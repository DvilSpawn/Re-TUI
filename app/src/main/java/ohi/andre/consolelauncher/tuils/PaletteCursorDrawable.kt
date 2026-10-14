package ohi.andre.consolelauncher.tuils

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import ohi.andre.consolelauncher.managers.settings.ThemeColorResolver
import ohi.andre.consolelauncher.managers.xml.options.Theme

/** The editor caches this drawable; read the palette when drawing, including after switches. */
class PaletteCursorDrawable(private val width: Int) : Drawable() {
    var role: Theme = Theme.cursor_color
    private val paint = Paint()
    private var opacity = 255

    override fun draw(canvas: Canvas) {
        paint.color = ThemeColorResolver.color(role)
        paint.alpha = android.graphics.Color.alpha(paint.color) * opacity / 255
        canvas.drawRect(bounds, paint)
    }

    override fun getIntrinsicWidth() = width
    override fun setAlpha(alpha: Int) { opacity = alpha.coerceIn(0, 255); invalidateSelf() }
    // OEM accent filters must not override the user's cursor color.
    override fun setColorFilter(colorFilter: ColorFilter?) = Unit
    @Deprecated("Deprecated in Java")
    override fun getOpacity() = PixelFormat.TRANSLUCENT
}
