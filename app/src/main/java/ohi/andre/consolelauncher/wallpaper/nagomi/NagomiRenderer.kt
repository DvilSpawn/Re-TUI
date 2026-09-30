package ohi.andre.consolelauncher.wallpaper.nagomi

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.GLES20.*
import android.opengl.GLUtils
import android.os.SystemClock
import android.util.Log
import android.view.Surface
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.roundToInt

/** One EGL owner per surface. Called only on the main thread, including disposal. */
internal class NagomiRenderer(private val context: Context) {
    val school = NagomiSchool()
    var background: File? = NagomiBackground.saved(context).takeIf { it.isFile }
        set(value) { if (field != value) { field = value; bed?.recycle(); bed = null } }
    private val painter = NagomiPainter()
    private var bed: Bitmap? = null
    private var pixels: Bitmap? = null
    private var canvas: Canvas? = null
    private var lastEvent = 0L
    private var display = EGL14.EGL_NO_DISPLAY
    private var eglContext = EGL14.EGL_NO_CONTEXT
    private var window = EGL14.EGL_NO_SURFACE
    private var attached: Surface? = null
    private var waterProgram = 0
    private var copyProgram = 0
    private var copyFlip = 0
    private val textures = IntArray(2)
    private val frameBuffer = IntArray(1)
    private val locations = HashMap<String, Int>()
    private var renderWidth = 0
    private var renderHeight = 0
    private var loggedFailure = false
    var frames = 0L
        private set
    private val vertices = ByteBuffer.allocateDirect(8 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply {
        put(floatArrayOf(-1f,-1f, 1f,-1f, -1f,1f, 1f,1f)); position(0)
    }

    fun resetClock() = school.resetClock()

    fun draw(surface: Surface, width: Int, height: Int): Boolean {
        if (!surface.isValid || width <= 0 || height <= 0) return false
        try {
            if (attached !== surface || display == EGL14.EGL_NO_DISPLAY) {
                releaseSurface()
                attach(surface)
            }
            check(EGL14.eglMakeCurrent(display, window, window, eglContext)) { "Cannot activate pond surface" }
            // Fixed short edge, bounded long edge: expensive water shading never runs at phone resolution.
            val scale = minOf(270f / minOf(width, height), 640f / maxOf(width, height))
            val w = (width * scale).roundToInt().coerceAtLeast(100)
            val h = (height * scale).roundToInt().coerceAtLeast(100)
            if (w != renderWidth || h != renderHeight) resize(w, h)
            if (bed == null) bed = NagomiBackground.load(context, background, w, h)
            for (event in NagomiNotifications.after(lastEvent)) {
                school.spawn(event.accent, event.marking)
                lastEvent = event.id
            }
            school.advance(SystemClock.uptimeMillis())
            painter.draw(canvas!!, school, bed!!)
            glActiveTexture(GL_TEXTURE0)
            glBindTexture(GL_TEXTURE_2D, textures[0])
            GLUtils.texSubImage2D(GL_TEXTURE_2D, 0, 0, 0, pixels)
            glBindFramebuffer(GL_FRAMEBUFFER, frameBuffer[0])
            glViewport(0, 0, w, h)
            glUseProgram(waterProgram)
            uniform("uTime", school.time.toFloat())
            uniform("uLargeCurrentTime", school.time.toFloat())
            uniform("uSecondaryLargeCurrentTime", school.time.toFloat())
            uniform("uDetailCurrentTime", school.time.toFloat())
            val rippleData = FloatArray(8 * 4)
            school.ripples.forEachIndexed { i, ripple ->
                rippleData[i*4] = ripple.point.x.toFloat()
                rippleData[i*4+1] = ripple.point.y.toFloat()
                rippleData[i*4+2] = ripple.age.toFloat()
                rippleData[i*4+3] = if (ripple.mouth) 2.5f else 1f
            }
            glUniform1i(location("uRippleCount"), school.ripples.size)
            glUniform4fv(location("uRipples[0]"), 8, rippleData, 0)
            quad()
            // Floating plants belong above the refracted water. Reuse the scene upload
            // bitmap/texture after the underwater pass has finished reading it.
            pixels!!.eraseColor(android.graphics.Color.TRANSPARENT)
            painter.drawSurface(canvas!!, school.time)
            glBindTexture(GL_TEXTURE_2D, textures[0])
            GLUtils.texSubImage2D(GL_TEXTURE_2D, 0, 0, 0, pixels)
            glUseProgram(copyProgram)
            glUniform1f(copyFlip, 1f)
            glEnable(GL_BLEND)
            glBlendFunc(GL_ONE, GL_ONE_MINUS_SRC_ALPHA)
            quad()
            glDisable(GL_BLEND)
            glBindFramebuffer(GL_FRAMEBUFFER, 0)
            glViewport(0, 0, width, height)
            glUseProgram(copyProgram)
            glUniform1f(copyFlip, 0f)
            glBindTexture(GL_TEXTURE_2D, textures[1])
            quad()
            check(glGetError() == GL_NO_ERROR) { "Pond drawing failed" }
            check(EGL14.eglSwapBuffers(display, window)) { "Pond surface lost" }
            frames++
            loggedFailure = false
            return true
        } catch (e: Exception) {
            if (!loggedFailure) Log.e("Nagomi", "Unable to render pond; will retry", e)
            loggedFailure = true
            releaseSurface()
            resetClock()
            return false
        }
    }

    private fun attach(surface: Surface) {
        display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        check(display != EGL14.EGL_NO_DISPLAY)
        check(EGL14.eglInitialize(display, IntArray(2), 0, IntArray(2), 0))
        val configs = arrayOfNulls<EGLConfig>(1)
        val count = IntArray(1)
        check(EGL14.eglChooseConfig(display, intArrayOf(
            EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
            EGL14.EGL_SURFACE_TYPE, EGL14.EGL_WINDOW_BIT,
            EGL14.EGL_RED_SIZE,8, EGL14.EGL_GREEN_SIZE,8, EGL14.EGL_BLUE_SIZE,8,
            EGL14.EGL_ALPHA_SIZE,8, EGL14.EGL_NONE), 0, configs, 0, 1, count, 0) && count[0] > 0)
        eglContext = EGL14.eglCreateContext(display, configs[0], EGL14.EGL_NO_CONTEXT,
            intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION,2,EGL14.EGL_NONE), 0)
        check(eglContext != EGL14.EGL_NO_CONTEXT)
        window = EGL14.eglCreateWindowSurface(display, configs[0], surface, intArrayOf(EGL14.EGL_NONE), 0)
        check(window != EGL14.EGL_NO_SURFACE)
        check(EGL14.eglMakeCurrent(display, window, window, eglContext))
        waterProgram = program(NAGOMI_WATER_SHADER)
        copyProgram = program("precision mediump float; varying vec2 vUv; uniform sampler2D uImage; uniform float uFlipY; void main(){gl_FragColor=texture2D(uImage,vec2(vUv.x,mix(vUv.y,1.0-vUv.y,uFlipY)));}")
        copyFlip = glGetUniformLocation(copyProgram, "uFlipY")
        glGenTextures(2, textures, 0)
        textures.forEach {
            glBindTexture(GL_TEXTURE_2D, it)
            // Keep texels crisp during water refraction, plant compositing and enlargement.
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST)
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST)
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE)
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE)
        }
        glGenFramebuffers(1, frameBuffer, 0)
        glUseProgram(waterProgram)
        glUniform1i(location("uUnderwater"), 0)
        glUniform4fv(location("uRipplePhysics[0]"), 2, floatArrayOf(2.2f,4f,62f,5.55f, .9f,1.2f,17f,10.4f), 0)
        glUniform4fv(location("uRippleCurves[0]"), 2, floatArrayOf(.3f,.58f,.18f,0f, .7f,.25f,.45f,0f), 0)
        vector3("uColorTint", .98f, 1f, 1f)
        uniform("uClarity", .35f)
        uniform("uShowCurrentEffect", 1f)
        uniform("uLargeCellSize", 908f)
        uniform("uLargeCurrentOpacity", .99f)
        uniform("uSecondaryLargeCellSize", 10f)
        uniform("uSecondaryLargeCurrentOpacity", .15f)
        uniform("uDetailCellSize", 20f)
        uniform("uDetailCurrentOpacity", .9f)
        vector3("uLargeCurrentColor", .022f,.068f,.047f)
        vector3("uLargeCurrentCoreColor", .052f,.155f,.108f)
        vector3("uSecondaryLargeCurrentColor", .022f,.068f,.047f)
        vector3("uSecondaryLargeCurrentCoreColor", .052f,.155f,.108f)
        vector3("uDetailCurrentColor", .01f,.034f,.023f)
        vector3("uDetailCurrentCoreColor", .028f,.09f,.061f)
        uniform("uCurrentAmplitude", .015f)
        glUniform2f(location("uWaveDirectionA"), .94f,.34f)
        glUniform2f(location("uWaveDirectionB"), -.38f,.92f)
        glUniform2f(location("uWaveDirectionC"), .71f,.71f)
        vector3("uWaveFrequency",22f,31f,59f)
        vector3("uWaveSpeed",.92f,.51f,-6.38f)
        vector3("uWaveStrength",1.2f,.55f,.28f)
        attached = surface
        renderWidth = 0
        resetClock()
    }

    private fun resize(width: Int, height: Int) {
        pixels?.recycle()
        bed?.recycle()
        bed = null
        pixels = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        canvas = Canvas(pixels!!)
        school.resize(width.toDouble(), height.toDouble())
        textures.forEach { texture ->
            glBindTexture(GL_TEXTURE_2D, texture)
            glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, width, height, 0, GL_RGBA, GL_UNSIGNED_BYTE, null)
        }
        glBindFramebuffer(GL_FRAMEBUFFER, frameBuffer[0])
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, textures[1], 0)
        check(glCheckFramebufferStatus(GL_FRAMEBUFFER) == GL_FRAMEBUFFER_COMPLETE)
        glUseProgram(waterProgram)
        glUniform2f(location("uResolution"), width.toFloat(), height.toFloat())
        renderWidth = width
        renderHeight = height
    }

    private fun quad() {
        vertices.position(0)
        glEnableVertexAttribArray(0)
        glVertexAttribPointer(0, 2, GL_FLOAT, false, 0, vertices)
        glDrawArrays(GL_TRIANGLE_STRIP, 0, 4)
    }

    private fun program(fragment: String): Int {
        val vertex = shader(GL_VERTEX_SHADER, "attribute vec2 aPosition; varying vec2 vUv; void main(){vUv=aPosition*0.5+0.5;gl_Position=vec4(aPosition,0.0,1.0);}")
        val pixel = shader(GL_FRAGMENT_SHADER, fragment)
        val program = glCreateProgram()
        glAttachShader(program, vertex)
        glAttachShader(program, pixel)
        glBindAttribLocation(program, 0, "aPosition")
        glLinkProgram(program)
        glDeleteShader(vertex)
        glDeleteShader(pixel)
        val status = IntArray(1)
        glGetProgramiv(program, GL_LINK_STATUS, status, 0)
        check(status[0] != 0) { glGetProgramInfoLog(program) }
        return program
    }

    private fun shader(type: Int, source: String): Int {
        val shader = glCreateShader(type)
        glShaderSource(shader, source)
        glCompileShader(shader)
        val status = IntArray(1)
        glGetShaderiv(shader, GL_COMPILE_STATUS, status, 0)
        check(status[0] != 0) { glGetShaderInfoLog(shader) }
        return shader
    }

    private fun location(name: String) = locations.getOrPut(name) { glGetUniformLocation(waterProgram, name) }
    private fun uniform(name: String, value: Float) = glUniform1f(location(name), value)
    private fun vector3(name: String, a: Float, b: Float, c: Float) = glUniform3f(location(name), a,b,c)

    fun releaseSurface() {
        if (display != EGL14.EGL_NO_DISPLAY) {
            EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
            if (window != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(display, window)
            if (eglContext != EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(display, eglContext)
            EGL14.eglTerminate(display)
        }
        display = EGL14.EGL_NO_DISPLAY
        window = EGL14.EGL_NO_SURFACE
        eglContext = EGL14.EGL_NO_CONTEXT
        attached = null
        locations.clear()
        pixels?.recycle(); pixels = null; canvas = null
        bed?.recycle(); bed = null
        resetClock()
    }
}
