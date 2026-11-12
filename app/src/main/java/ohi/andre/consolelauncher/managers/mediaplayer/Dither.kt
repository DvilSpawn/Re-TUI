package ohi.andre.consolelauncher.managers.mediaplayer

import android.graphics.Bitmap
import android.graphics.Color

/** Turns cover art into the launcher's one-colour dot pattern. */
internal object Dither {
    private val BAYER = intArrayOf(0, 8, 2, 10, 12, 4, 14, 6, 3, 11, 1, 9, 15, 7, 13, 5)

    /** Whether a pixel of brightness [luma] (0 to 255) at [x], [y] gets a dot. */
    fun lit(luma: Int, x: Int, y: Int) = luma * 16 / 256 > BAYER[(y and 3) * 4 + (x and 3)]

    fun apply(source: Bitmap, size: Int, color: Int): Bitmap {
        val scaled = Bitmap.createScaledBitmap(source, size, size, true)
        val pixels = IntArray(size * size)
        scaled.getPixels(pixels, 0, size, 0, 0, size, size)
        for (i in pixels.indices) {
            val p = pixels[i]
            val luma = (Color.red(p) * 30 + Color.green(p) * 59 + Color.blue(p) * 11) / 100
            pixels[i] = if (lit(luma, i % size, i / size)) color else Color.TRANSPARENT
        }
        return Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888)
    }
}
