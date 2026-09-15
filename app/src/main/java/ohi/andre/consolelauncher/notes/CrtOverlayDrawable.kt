package ohi.andre.consolelauncher.notes


import android.content.Context
import android.graphics.*
import android.graphics.drawable.Drawable
import kotlin.math.max

class CrtOverlayDrawable(context: Context, private val vignette: Boolean) : Drawable() {
    private val d = context.resources.displayMetrics.density
    private val tint = Paint().apply { color = Color.argb(10,120,255,190) }
    private val line = Paint().apply { color = Color.argb(44,0,0,0) }
    private val beam = Paint().apply { color = Color.argb(10,255,255,255) }
    private val mask = Paint().apply { color = Color.argb(18,0,0,0); strokeWidth = 1f }
    private val shade = Paint(Paint.ANTI_ALIAS_FLAG)
    fun setAccentColor(c: Int) { tint.color = Color.argb(10, Color.red(c), Color.green(c), Color.blue(c)) }
    override fun onBoundsChange(b: Rect) { shade.shader = RadialGradient(b.exactCenterX(),b.exactCenterY(),max(b.width(),b.height())*.72f,intArrayOf(Color.TRANSPARENT,Color.argb(116,0,0,0)),floatArrayOf(.58f,1f),Shader.TileMode.CLAMP) }
    override fun draw(c: Canvas) { val b=bounds; c.drawRect(b,tint); var y=b.top.toFloat(); while(y<b.bottom){c.drawRect(b.left.toFloat(),y,b.right.toFloat(),y+max(1f,d),line);c.drawRect(b.left.toFloat(),y+max(1f,d),b.right.toFloat(),y+max(1f,d)+max(1f,d*.5f),beam);y+=max(3f,d*3)}; var x=b.left.toFloat();while(x<b.right){c.drawLine(x,b.top.toFloat(),x,b.bottom.toFloat(),mask);x+=max(4f,d*4)};if(vignette)c.drawRect(b,shade) }
    override fun setAlpha(a:Int){ listOf(tint,line,beam,mask,shade).forEach{it.alpha=a} }; override fun setColorFilter(f:ColorFilter?){ listOf(tint,line,beam,mask,shade).forEach{it.colorFilter=f} }; @Deprecated("Deprecated in Java") override fun getOpacity()=PixelFormat.TRANSLUCENT
}
