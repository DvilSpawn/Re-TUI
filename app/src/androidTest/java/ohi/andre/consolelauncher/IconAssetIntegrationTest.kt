package ohi.andre.consolelauncher

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.VectorDrawable
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/** Read-only resource/render check; no accounts, settings, navigation or network. */
class IconAssetIntegrationTest {
    @Test fun licensedReplacementIconsInflateAndRenderWithExistingResourceNames() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val sources = context.assets.open("licenses/third_party/material-icons.json").bufferedReader().use { JSONObject(it.readText()) }
        val own = context.assets.open("licenses/third_party/project-vector-icons.json").bufferedReader().use { JSONObject(it.readText()) }
        val files = sources.getJSONArray("files")
        assertEquals(66, files.length())
        val names = (0 until files.length()).map { files.getJSONObject(it).getString("path").substringAfterLast('/').removeSuffix(".xml") } +
            (0 until own.getJSONArray("files").length()).map { own.getJSONArray("files").getJSONObject(it).getString("path").substringAfterLast('/').removeSuffix(".xml") }
        val preview = Bitmap.createBitmap(800, ((names.size + 7) / 8) * 112, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(preview)
        canvas.drawColor(Color.rgb(18, 23, 27))
        val label = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.LTGRAY; textSize = 8f }
        try {
            for ((index, name) in names.withIndex()) {
                val id = context.resources.getIdentifier(name, "drawable", context.packageName)
                assertTrue("Resource preserved: $name", id != 0)
                val icon = context.resources.getDrawable(id, context.theme).mutate()
                if (name.startsWith("remember_ic_format_")) assertTrue("Formatting PNG replaced by vector", icon is VectorDrawable)
                val bitmap = Bitmap.createBitmap(80, 80, Bitmap.Config.ARGB_8888)
                try {
                    icon.setTint(Color.WHITE)
                    val scale = 80f / maxOf(icon.intrinsicWidth, icon.intrinsicHeight)
                    val width = (icon.intrinsicWidth * scale).toInt().coerceAtLeast(1)
                    val height = (icon.intrinsicHeight * scale).toInt().coerceAtLeast(1)
                    icon.setBounds((80 - width) / 2, (80 - height) / 2, (80 + width) / 2, (80 + height) / 2)
                    icon.draw(Canvas(bitmap))
                    val pixels = IntArray(6400)
                    bitmap.getPixels(pixels, 0, 80, 0, 0, 80, 80)
                    assertTrue("Nonempty visible vector: $name", pixels.any { Color.alpha(it) > 0 })
                    val x = (index % 8) * 100f + 10
                    val y = (index / 8) * 112f
                    canvas.drawBitmap(bitmap, x, y, null)
                    canvas.drawText(name.removePrefix("remember_ic_").removePrefix("ic_"), x - 8, y + 94, label)
                } finally { bitmap.recycle() }
            }
            File(context.getExternalFilesDir(null), "licensed-icon-preview.png").outputStream().use {
                assertTrue(preview.compress(Bitmap.CompressFormat.PNG, 100, it))
            }
        } finally { preview.recycle() }
    }
}
