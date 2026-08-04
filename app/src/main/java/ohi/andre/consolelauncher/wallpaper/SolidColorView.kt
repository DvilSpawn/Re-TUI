package ohi.andre.consolelauncher.wallpaper

import android.app.WallpaperColors
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.view.View

class SolidColorView(context: Context) : View(context) {
    var color: Int = Color.parseColor(RetuiWallpaperSettings.solidColor(context)) or Color.BLACK
        set(value) {
            field = value
            invalidate()
        }

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(color)
    }

    fun wallpaperColors(): WallpaperColors = WallpaperColors(Color.valueOf(color), null, null)
}
