package ohi.andre.consolelauncher.wallpaper.nagomi

import android.graphics.*
import kotlin.math.*

/** Adapted from Nagomi fish-renderer.ts / settings/definition.ts, Copyright 2026 Mayank Kadam.
 * Original geometry and palettes rendered into a reusable Android bitmap for the water shader.
 */
internal class NagomiPainter {
    // Upstream disables geometry antialiasing; preserve the low-resolution pixel edges.
    private val paint = Paint().apply { isAntiAlias = false; isFilterBitmap = false }
    private val plants = NagomiPlants()
    private val body = Path()
    private val fins = Path()
    private val patchPath = Path()
    private val left = Array(14) { PondPoint() }
    private val right = Array(14) { PondPoint() }
    private val palettes = arrayOf(
        intArrayOf(0xf1eadb, 0xdc4b2f, 0x27251f, 0xe6ddca),
        intArrayOf(0xf2ebdc, 0xdf5032, 0x20211f, 0xe7dece),
        intArrayOf(0xeee6d5, 0xd9482e, 0x242622, 0xc9bfaa),
        intArrayOf(0xe7aa31, 0xcf7626, 0x78431f, 0xd9922a),
        intArrayOf(0xf2ebdc, 0xda4430, 0x292723, 0xe7dece),
        intArrayOf(0xeae5da, 0x252825, 0x4f5c5a, 0xd7d2c7)
    )
    // position, length, width, lateral offset, marking (1) or accent (0)
    private val patches = arrayOf(
        arrayOf(doubleArrayOf(.17,.09,.74,.04,0.0), doubleArrayOf(.48,.115,.69,-.12,0.0), doubleArrayOf(.76,.085,.62,.16,0.0)),
        arrayOf(doubleArrayOf(.19,.095,.7,.04,0.0), doubleArrayOf(.58,.105,.66,-.14,0.0), doubleArrayOf(.38,.045,.3,.38,1.0), doubleArrayOf(.79,.04,.28,-.36,1.0)),
        arrayOf(doubleArrayOf(.15,.082,.63,-.05,0.0), doubleArrayOf(.53,.092,.58,.17,0.0), doubleArrayOf(.32,.09,.72,.1,1.0), doubleArrayOf(.73,.105,.67,-.14,1.0)),
        emptyArray(), arrayOf(doubleArrayOf(.16,.07,.52,0.0,0.0)),
        arrayOf(doubleArrayOf(.22,.092,.68,.08,0.0), doubleArrayOf(.51,.09,.6,-.18,0.0), doubleArrayOf(.79,.074,.54,.22,0.0))
    )

    fun draw(canvas: Canvas, school: NagomiSchool, bed: Bitmap) {
        paint.color = Color.WHITE
        paint.alpha = 255
        canvas.drawBitmap(bed, null, Rect(0, 0, canvas.width, canvas.height), paint)
        plants.drawLotusShadows(canvas, school.time)
        drawTiny(canvas, school.tiny, true)
        school.fish.forEach { k ->
            build(k)
            val depth = visualDepth(k)
            canvas.save()
            canvas.translate((4.4 - 3 * depth).toFloat(), (10.4 - 7 * depth).toFloat())
            paint.color = 0xff0b211e.toInt()
            paint.alpha = ((.38 + .18 * depth) * k.opacity * 255).toInt()
            canvas.drawPath(body, paint)
            canvas.drawPath(fins, paint)
            canvas.restore()
        }
        school.fish.forEach { k ->
            build(k)
            val palette = palettes[k.id % palettes.size]
            val base = if (k.accent == null) palette[0] else 0xf2ebdc
            val accent = k.accent ?: palette[1]
            val marking = k.marking ?: palette[2]
            color(k, if (k.accent == null) palette[3] else mix(0xe7dece, accent, .25))
            canvas.drawPath(fins, paint)
            color(k, base)
            canvas.drawPath(body, paint)
            val pattern = if (k.accent == null) k.id % patches.size else 1
            for ((index, patch) in patches[pattern].withIndex()) {
                val position = patch[0] * 13
                val node = min(12, floor(position).toInt())
                val amount = position - node
                val spine = k.renderSpine
                val forward = (spine[max(0, node - 1)] - spine[min(13, node + 2)]).unit(direction(k.heading))
                val normal = forward.normal()
                val width = width(k, node) * (1 - amount) + width(k, node + 1) * amount
                val center = spine[node] + (spine[node + 1] - spine[node]) * amount + normal * (width * patch[3])
                patchPath.rewind()
                val phase = index * 1.73 + patch[0] * 5.1
                repeat(10) { p ->
                    val angle = p / 10.0 * PI * 2
                    val wobble = 1 + sin(angle * 3 + phase) * .08 + cos(angle * 2 - phase * .7) * .045
                    val point = center + forward * (cos(angle) * k.bodyLength * patch[1] * wobble) + normal * (sin(angle) * width * patch[2] * wobble)
                    if (p == 0) patchPath.moveTo(point.x.toFloat(), point.y.toFloat()) else patchPath.lineTo(point.x.toFloat(), point.y.toFloat())
                }
                patchPath.close()
                color(k, if (patch[4] == 1.0) marking else accent)
                canvas.drawPath(patchPath, paint)
            }
            val forward = (k.renderSpine[0] - k.renderSpine[1]).unit(direction(k.heading))
            val anchor = k.renderSpine[0] + forward * (k.bodyWidth * .08)
            val normal = forward.normal() * (width(k, 0) * .58)
            color(k, 0x171815)
            for (eye in arrayOf(anchor + normal, anchor - normal)) {
                canvas.drawCircle(eye.x.toFloat(), eye.y.toFloat(), max(.58, k.bodyWidth * .11).toFloat(), paint)
            }
        }
        drawTiny(canvas, school.tiny, false)
    }

    fun drawSurface(canvas: Canvas, time: Double) = plants.drawSurface(canvas, time)

    private val tinyPalettes = arrayOf(
        intArrayOf(0xffe66d,0xfff3a0,0xff8c42,0xffc857,0x203638),
        intArrayOf(0x56dffc,0xb2f2ff,0x3877ed,0x85edff,0x173b52),
        intArrayOf(0xff72ad,0xffbad2,0xffd05e,0xff9bc2,0x4d2940))

    /** Original tiny-fish-renderer.ts silhouette, stripe, paired fins and forked tail. */
    private fun drawTiny(canvas: Canvas, schools: NagomiTinyFish, shadow: Boolean) {
        for (f in schools.fish) {
            val p=tinyPalettes[f.school]; val l=f.length; val w=f.width
            canvas.save()
            canvas.translate((f.x+if(shadow)1.5 else 0.0).toFloat(),(f.y+if(shadow)2.8 else 0.0).toFloat())
            canvas.rotate(Math.toDegrees(atan2(f.vy,f.vx)).toFloat())
            fun shape(rgb: Int, vararg points: Double) {
                patchPath.rewind(); patchPath.moveTo(points[0].toFloat(),points[1].toFloat())
                for(i in 2 until points.size step 2) patchPath.lineTo(points[i].toFloat(),points[i+1].toFloat())
                patchPath.close()
                paint.color=if(shadow)0x4712352f else rgb or 0xff000000.toInt()
                canvas.drawPath(patchPath,paint)
            }
            shape(p[0],l*.16,w,-l*.34,w*.58,-l*.44,0.0,-l*.34,-w*.58,l*.16,-w)
            shape(p[1],l*.5,0.0,l*.16,w,0.0,0.0,l*.16,-w)
            val swing=sin(f.tail)*w*.44
            shape(p[3],-l*.44,0.0,-l*.78,swing+w*.92,-l*(.44+.34*.62),swing*.44)
            shape(p[3],-l*.44,0.0,-l*(.44+.34*.62),swing*.44,-l*.78,swing-w*.92)
            if(!shadow) {
                for(side in intArrayOf(-1,1)) shape(p[3],l*.02,0.0,l*.02,w*1.28*side,-l*.18,0.0)
                shape(p[2],l*.08,w*.78,l*.08,-w*.78,-l*.09,-w*.78,-l*.09,w*.78)
                paint.color=p[4] or 0xff000000.toInt()
                canvas.drawCircle((l*.31).toFloat(),(w*.58).toFloat(),.28f,paint)
                canvas.drawCircle((l*.31).toFloat(),(-w*.58).toFloat(),.28f,paint)
            }
            canvas.restore()
        }
    }

    private fun build(k: NagomiSchool.Koi) {
        val spine = k.renderSpine
        spine[0] = k.spine[0]
        for (node in 1..13) {
            val t = node / 13.0
            val normal = (k.spine[max(0, node - 1)] - k.spine[min(13, node + 1)]).unit(direction(k.heading)).normal()
            val wave = sin(k.swimPhase - t * 6.1) * k.bodyWidth * 1.15 * t.pow(1.72) * (.08 + k.tailEffort * .92)
            spine[node] = k.spine[node] + normal * wave
        }
        for (node in 0..13) {
            val normal = (spine[max(0, node - 1)] - spine[min(13, node + 1)]).unit(direction(k.heading)).normal()
            left[node] = spine[node] + normal * width(k, node)
            right[node] = spine[node] - normal * width(k, node)
        }
        body.rewind()
        fins.rewind()
        val forward = (spine[0] - spine[1]).unit(direction(k.heading))
        val nose = spine[0] + forward * (k.bodyWidth * .43)
        val halfNose = width(k, 0) * .72
        val nl = nose + forward.normal() * halfNose
        val nr = nose - forward.normal() * halfNose
        body.moveTo(nl.x.toFloat(), nl.y.toFloat())
        left.forEach { body.lineTo(it.x.toFloat(), it.y.toFloat()) }
        right.reversedArray().forEach { body.lineTo(it.x.toFloat(), it.y.toFloat()) }
        body.lineTo(nr.x.toFloat(), nr.y.toFloat())
        body.close()
        body.addCircle(nose.x.toFloat(), nose.y.toFloat(), max(1.0, halfNose * .72).toFloat(), Path.Direction.CW)
        val activity = (if (k.state == 2) 1.0 else if (k.state == 4) .85 else .45) + sin(PI * k.gulpAnimation / .24) * .85
        val pulse = .82 + activity * .25 * sin(k.swimPhase * .64 + k.phaseOffset)
        val tangent = (spine[3] - spine[5]).unit(direction(k.heading))
        val reach = k.bodyWidth * (.55 + activity * .25) * pulse
        triangle(fins, left[3], left[4] + tangent.normal() * reach - tangent * (k.bodyWidth * .22), left[6])
        triangle(fins, right[3], right[6], right[4] - tangent.normal() * reach - tangent * (k.bodyWidth * .22))
        val pelvic = (spine[7] - spine[9]).unit(direction(k.heading)).normal() * (k.bodyWidth * (.28 + .05 * pulse))
        triangle(fins, left[7], left[8] + pelvic, left[9])
        triangle(fins, right[7], right[9], right[8] - pelvic)
        val tail = (spine[12] - spine[13]).unit(direction(k.heading))
        val spread = tail.normal() * (k.bodyWidth * (.58 + .08 * sin(k.swimPhase - .8)))
        val end = spine[13] - tail * (k.bodyWidth * 1.38)
        val notch = spine[13] - tail * (k.bodyWidth * .86)
        triangle(fins, spine[13], end + spread, notch)
        triangle(fins, spine[13], notch, end - spread)
    }

    private fun triangle(path: Path, a: PondPoint, b: PondPoint, c: PondPoint) {
        path.moveTo(a.x.toFloat(), a.y.toFloat())
        path.lineTo(b.x.toFloat(), b.y.toFloat())
        path.lineTo(c.x.toFloat(), c.y.toFloat())
        path.close()
    }

    private fun width(k: NagomiSchool.Koi, node: Int): Double {
        val t = node / 13.0
        val profile = if (t < .18) .73 + t / .18 * .27 else max(0.0, 1 - (t - .18) / .82).pow(.72)
        return max(.7, k.bodyWidth * profile)
    }

    private fun visualDepth(k: NagomiSchool.Koi): Double {
        val linear = ((k.depth - .1) / .62).coerceIn(0.0, 1.0)
        return linear * linear * (3 - 2 * linear)
    }

    private fun color(k: NagomiSchool.Koi, source: Int) {
        val depth = visualDepth(k)
        val brightness = 1 - .41 * depth
        val saturation = 1 - .24 * depth
        val r = source shr 16 and 255
        val g = source shr 8 and 255
        val b = source and 255
        val luminance = r * .2126 + g * .7152 + b * .0722
        fun channel(c: Int, tint: Double) = ((luminance + (c - luminance) * saturation) * brightness * (1 + (tint - 1) * depth)).toInt().coerceIn(0, 255)
        paint.color = Color.argb((k.opacity * 255).toInt(), channel(r, .66), channel(g, .84), channel(b, .8))
    }

    private fun mix(a: Int, b: Int, t: Double): Int = Color.rgb(
        ((a shr 16 and 255) * (1-t) + (b shr 16 and 255) * t).toInt(),
        ((a shr 8 and 255) * (1-t) + (b shr 8 and 255) * t).toInt(),
        ((a and 255) * (1-t) + (b and 255) * t).toInt())
}
