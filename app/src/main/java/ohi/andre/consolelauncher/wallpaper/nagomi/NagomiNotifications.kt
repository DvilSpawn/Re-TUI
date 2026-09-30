package ohi.andre.consolelauncher.wallpaper.nagomi

import android.app.Notification
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.os.SystemClock
import android.service.notification.StatusBarNotification
import android.util.LruCache
import ohi.andre.consolelauncher.wallpaper.RetuiWallpaperSettings

/** Notification identity only; no message content or notification actions enter the pond. */
internal class NagomiNotificationKeys {
    private val active = LinkedHashSet<String>()
    fun seed(keys: Collection<String>) { active.clear(); active.addAll(keys.toList().takeLast(4096)) }
    fun posted(key: String): Boolean {
        if (!active.add(key)) return false
        if (active.size > 4096) active.remove(active.first())
        return true
    }
    fun removed(key: String) { active.remove(key) }
}

internal object NagomiNotifications {
    data class Event(val id: Long, val time: Long, val accent: Int, val marking: Int)
    val keys = NagomiNotificationKeys()
    private var serial = 0L
    private val events = ArrayList<Event>()
    private val palettes = LruCache<String, IntArray>(64)
    var previews = 0
    var engines = 0

    fun posted(context: Context, sbn: StatusBarNotification) {
        if (!keys.posted(sbn.key)) return
        val n = sbn.notification
        if (n.flags and (Notification.FLAG_GROUP_SUMMARY or Notification.FLAG_ONGOING_EVENT) != 0) return
        if (previews == 0 && engines == 0 && RetuiWallpaperSettings.scene(context) != NagomiView.SCENE) return
        val palette = palettes.get(sbn.packageName) ?: palette(context, sbn.packageName, n.color).also { palettes.put(sbn.packageName, it) }
        enqueue(palette[0], palette[1])
    }

    fun enqueue(accent: Int, marking: Int) {
        if (events.size == NagomiSchool.MAX_FISH) events.removeAt(0)
        events += Event(++serial, SystemClock.elapsedRealtime(), accent, marking)
    }

    fun after(id: Long): List<Event> {
        val now = SystemClock.elapsedRealtime()
        events.removeAll { now - it.time > 5 * 60_000 }
        return events.filter { it.id > id }
    }

    private fun palette(context: Context, pkg: String, fallback: Int): IntArray {
        val default = if (fallback != 0) fallback else 0xff5bada2.toInt()
        val bitmap = Bitmap.createBitmap(24, 24, Bitmap.Config.ARGB_8888)
        try {
            val icon = context.packageManager.getApplicationIcon(pkg)
            icon.setBounds(0, 0, 24, 24)
            icon.draw(Canvas(bitmap))
            val counts = HashMap<Int, Int>()
            val hsv = FloatArray(3)
            for (y in 0 until 24) for (x in 0 until 24) {
                val pixel = bitmap.getPixel(x, y)
                Color.colorToHSV(pixel, hsv)
                if (Color.alpha(pixel) < 180 || hsv[1] < .25 || hsv[2] < .25) continue
                val quantized = Color.rgb((Color.red(pixel) / 32) * 32 + 16, (Color.green(pixel) / 32) * 32 + 16, (Color.blue(pixel) / 32) * 32 + 16)
                counts[quantized] = (counts[quantized] ?: 0) + 1
            }
            val ordered = counts.entries.sortedByDescending { it.value }
            val accent = ordered.firstOrNull()?.key ?: default
            Color.colorToHSV(accent, hsv)
            val hue = hsv[0]
            val second = ordered.firstOrNull {
                Color.colorToHSV(it.key, hsv)
                val difference = kotlin.math.abs(hsv[0] - hue)
                minOf(difference, 360 - difference) > 45
            }?.key ?: Color.rgb(Color.red(accent) / 3, Color.green(accent) / 3, Color.blue(accent) / 3)
            return intArrayOf(accent, second)
        } catch (_: Exception) {
            return intArrayOf(default, 0xff253b39.toInt())
        } finally { bitmap.recycle() }
    }
}
