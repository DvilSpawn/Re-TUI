package ohi.andre.consolelauncher.managers.modes

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.os.SystemClock
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import ohi.andre.consolelauncher.BuildConfig
import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.managers.settings.AppearanceSettings
import ohi.andre.consolelauncher.managers.settings.ThemeColorResolver
import ohi.andre.consolelauncher.managers.status.AsciiAnimationManager
import ohi.andre.consolelauncher.managers.xml.XMLPrefsManager
import ohi.andre.consolelauncher.managers.xml.options.Theme
import ohi.andre.consolelauncher.managers.xml.options.Ui
import ohi.andre.consolelauncher.tuils.AsciiArtTextView
import ohi.andre.consolelauncher.tuils.LauncherFontScale
import ohi.andre.consolelauncher.tuils.Tuils
import java.io.File
import kotlin.math.roundToInt

/**
 * The top of the terminal pane: a fetch the launcher prints itself (no Termux), with the user's ascii.txt above the
 * details and the theme's colours below, then the live lines ([now]) that update in place.
 */
internal class TerminalFetchView(context: Context, private val apps: () -> Int, private val now: () -> List<Pair<String, String>>) :
    LinearLayout(context) {
    private val art = AsciiArtTextView(context)
    private val details = LinearLayout(context).apply { orientation = VERTICAL }
    private val swatches = LinearLayout(context).apply { orientation = VERTICAL }
    private val live = LinearLayout(context).apply { orientation = VERTICAL }
    private val tick = object : Runnable {
        override fun run() { refreshLive(); postDelayed(this, 30_000L) }
    }

    init {
        orientation = VERTICAL
        setPadding(dp(4), dp(8), dp(4), dp(12))
        addView(art, LayoutParams(-1, -2))
        addView(details, LayoutParams(-1, -2).apply { topMargin = dp(8) })
        addView(swatches, LayoutParams(-2, -2).apply { topMargin = dp(10) })
        addView(live, LayoutParams(-1, -2).apply { topMargin = dp(12) })
        refresh()
    }

    private val key get() = ThemeColorResolver.color(Theme.input_text_color)
    private val value get() = ThemeColorResolver.color(Theme.output_text_color)

    /** Everything again: the art, the details and the colours (theme or settings changed, or the pane came back). */
    private var appsValue: TextView? = null

    fun refresh() {
        val asciiColor = ThemeColorResolver.color(Theme.ascii_text_color)
        val file = File(Tuils.getFolder(), "ascii.txt")
        val text = if (file.exists()) runCatching { AsciiAnimationManager(0, asciiColor, null).load(file, false) }.getOrNull() else null
        art.visibility = if (text.isNullOrBlank()) GONE else VISIBLE
        art.setAsciiFrame(text?.toString(), (text as? AsciiAnimationManager.AsciiFrameText)?.color() ?: asciiColor, -1,
            XMLPrefsManager.getInt(Ui.ascii_pane_height_rows), (text as? AsciiAnimationManager.AsciiFrameText)?.colors())

        details.removeAllViews()
        val appsLabel = context.getString(R.string.term_fetch_apps)
        val user = XMLPrefsManager.get(Ui.username).ifBlank { "user" }
        val device = XMLPrefsManager.get(Ui.deviceName).ifBlank { Build.MODEL }
        details.addView(line("$user@$device", key))
        details.addView(line("─".repeat((user.length + device.length + 1).coerceIn(8, 28)), dim(value)))
        val font = if (AppearanceSettings.useSystemFont()) "system" else AppearanceSettings.fontFile()?.substringAfterLast('/')?.substringBeforeLast('.').orEmpty().ifBlank { "system" }
        listOf(
            R.string.term_fetch_launcher to "Re:TUI ${BuildConfig.VERSION_NAME} · ${BuildConfig.VERSION_CODE}",
            R.string.term_fetch_android to Build.VERSION.RELEASE,
            R.string.term_fetch_device to "${Build.MANUFACTURER} ${Build.MODEL}".trim(),
            R.string.term_fetch_uptime to uptime(),
            R.string.term_fetch_battery to battery(),
            R.string.term_fetch_memory to memory(),
            R.string.term_fetch_storage to storage(),
            R.string.term_fetch_apps to apps().toString(),
            R.string.term_fetch_font to font
        ).map { (label, text) -> context.getString(label) to text }.chunked(2).forEach { pair ->
            // Two pairs to a line, so the fetch leaves the terminals more of the pane.
            details.addView(LinearLayout(context).apply {
                orientation = HORIZONTAL
                pair.forEach { (label, text) ->
                    val row = row(label, text, width = 7)
                    if (label == appsLabel) appsValue = row.getChildAt(1) as? TextView
                    addView(row, LayoutParams(0, -2, 1f))
                }
                if (pair.size == 1) addView(View(context), LayoutParams(0, 0, 1f))
            })
        }

        swatches.removeAllViews()
        listOf(
            listOf(Theme.device_text_color, Theme.battery_text_high, Theme.ascii_text_color, Theme.time_text_color,
                Theme.storage_text_color, Theme.ram_text_color, Theme.network_info_text_color, Theme.weather_text_color),
            listOf(Theme.input_text_color, Theme.output_text_color, Theme.cursor_color, Theme.enter_icon_color,
                Theme.unlock_counter_text_color, Theme.battery_text_medium, Theme.battery_text_low, Theme.session_info_text_color)
        ).forEach { roles ->
            swatches.addView(LinearLayout(context).apply {
                roles.forEach { role -> addView(View(context).apply { setBackgroundColor(ThemeColorResolver.color(role)) }, LayoutParams(dp(22), dp(11))) }
            })
        }
        refreshLive()
    }

    /** The live lines only: time, weather, music, the next alarm. */
    fun refreshLive() {
        live.removeAllViews()
        now().forEach { (label, text) -> live.addView(row(label, text, width = 6)) }
    }

    override fun onAttachedToWindow() { super.onAttachedToWindow(); removeCallbacks(tick); post(tick) }
    override fun onDetachedFromWindow() { removeCallbacks(tick); super.onDetachedFromWindow() }

    /** The app list loads after the pane is built, and changes on installs, so its count updates on its own. */
    fun refreshApps() {
        appsValue?.text = apps().toString()
    }

    private fun row(label: String, text: String, width: Int = 9) = LinearLayout(context).apply {
        orientation = HORIZONTAL
        gravity = Gravity.TOP
        addView(line(label, key).apply { minEms = width / 2 + 2 }, LayoutParams(-2, -2).apply { marginEnd = dp(10) })
        addView(line(text, value), LayoutParams(0, -2, 1f))
    }

    private fun line(text: String, color: Int) = TextView(context).apply {
        this.text = text
        setTextColor(color)
        typeface = Tuils.getTypeface(context)
        LauncherFontScale.setBaseSize(this, 12f, LauncherFontScale.offsetSp())
        includeFontPadding = false
        setPadding(0, dp(1), 0, dp(1))
        ellipsize = TextUtils.TruncateAt.END
        maxLines = 2
    }

    private fun dim(color: Int) = (color and 0x00FFFFFF) or (((color ushr 24) * 0.55f).roundToInt() shl 24)

    private fun uptime(): String {
        val minutes = SystemClock.elapsedRealtime() / 60_000
        val days = minutes / 1440; val hours = minutes / 60 % 24; val mins = minutes % 60
        return listOfNotNull(days.takeIf { it > 0 }?.let { "${it}d" }, hours.takeIf { it > 0 || days > 0 }?.let { "${it}h" }, "${mins}m").joinToString(" ")
    }

    private fun battery(): String = runCatching {
        val status = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return ""
        val level = status.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) * 100 / status.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
        val charging = status.getIntExtra(BatteryManager.EXTRA_STATUS, -1).let { it == BatteryManager.BATTERY_STATUS_CHARGING || it == BatteryManager.BATTERY_STATUS_FULL }
        "$level%" + if (charging) " · " + context.getString(R.string.term_fetch_charging) else ""
    }.getOrDefault("")

    private fun memory(): String = runCatching {
        val info = ActivityManager.MemoryInfo()
        (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(info)
        "%.1f / %.1f GiB".format((info.totalMem - info.availMem) / GIB, info.totalMem / GIB)
    }.getOrDefault("")

    private fun storage(): String = runCatching {
        val stat = StatFs(Environment.getDataDirectory().path)
        "%.0f / %.0f GB".format((stat.totalBytes - stat.availableBytes) / 1e9, stat.totalBytes / 1e9)
    }.getOrDefault("")

    private fun dp(value: Int) = (value * resources.displayMetrics.density).roundToInt()

    private companion object { const val GIB = 1024.0 * 1024.0 * 1024.0 }
}
