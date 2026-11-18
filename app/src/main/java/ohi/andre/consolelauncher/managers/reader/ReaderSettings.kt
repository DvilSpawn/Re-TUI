package ohi.andre.consolelauncher.managers.reader

import android.content.Context

/** How pages look; one set for every book. */
class ReaderSettings(context: Context) {
    enum class Font { LITERATA, MONO, SANS }
    enum class Paper(val paper: Int, val ink: Int) {
        GREY(0xFFE3E1DA.toInt(), 0xFF1E1E1C.toInt()),
        WARM(0xFFEDE6D6.toInt(), 0xFF22201C.toInt()),
        COOL(0xFFDDE0E0.toInt(), 0xFF1C1E1E.toInt()),
        NIGHT(0xFF2A2A28.toInt(), 0xFFD8D5CC.toInt())
    }
    enum class Level { OFF, FAINT, STRONG }
    /** How often a turn does a full refresh: the grey wash that also clears the ghosts. */
    enum class Refresh(val every: Int) { OFF(0), SIX(6), EVERY(1) }
    /** What the footer's right side shows; a tap on the footer moves to the next. */
    enum class Footer { CHAPTER_TIME, BOOK_TIME, CHAPTER_PAGES, PERCENT }
    /** Which way pages go: left to right, right to left (manga), or one scrolling column. */
    enum class Direction { LTR, RTL, SCROLL }

    private val prefs = context.getSharedPreferences("reader", Context.MODE_PRIVATE)

    var font: Font by option("font", Font.LITERATA, Font::valueOf)
    var paper: Paper by option("paper", Paper.GREY, Paper::valueOf)
    var grain: Level by option("grain", Level.FAINT, Level::valueOf)
    var ghosting: Level by option("ghosting", Level.FAINT, Level::valueOf)
    var refresh: Refresh by option("refresh", Refresh.SIX, Refresh::valueOf)
    /** Text books and picture books keep their own direction, so manga can read right to left alone. */
    var directionText: Direction by option("direction_text", Direction.LTR, Direction::valueOf)
    var directionPictures: Direction by option("direction_pictures", Direction.LTR, Direction::valueOf)
    var footer: Footer by option("footer", Footer.CHAPTER_TIME, Footer::valueOf)
    /** The reader's own speed in characters a minute, learned from page turns; starts at an average reader's. */
    var speed: Float
        get() = prefs.getFloat("speed_cpm", DEFAULT_SPEED)
        set(value) = prefs.edit().putFloat("speed_cpm", value).apply()
    var swipe: Boolean
        get() = prefs.getBoolean("swipe", true)
        set(value) = prefs.edit().putBoolean("swipe", value).apply()
    /** Paper and ink of an e-ink screen, or the launcher theme's own colours. */
    var themeLook: Boolean
        get() = prefs.getBoolean("theme_look", false)
        set(value) = prefs.edit().putBoolean("theme_look", value).apply()
    var sizeSp: Int
        get() = prefs.getInt("size", 17)
        set(value) = prefs.edit().putInt("size", value.coerceIn(MIN_SIZE, MAX_SIZE)).apply()
    /** 0, 1 or 2: small, medium and large margins. */
    var margin: Int
        get() = prefs.getInt("margin", 1)
        set(value) = prefs.edit().putInt("margin", value).apply()
    var spacing: Float
        get() = prefs.getFloat("spacing", 1.55f)
        set(value) = prefs.edit().putFloat("spacing", value).apply()
    var justify: Boolean
        get() = prefs.getBoolean("justify", true)
        set(value) = prefs.edit().putBoolean("justify", value).apply()
    var volumeKeys: Boolean
        get() = prefs.getBoolean("volume_keys", true)
        set(value) = prefs.edit().putBoolean("volume_keys", value).apply()

    fun marginDp() = MARGINS[margin.coerceIn(0, MARGINS.lastIndex)]

    /** What changes where the text breaks into pages; a new value means laying the book out again. */
    fun layoutKey() = listOf(font, sizeSp, margin, spacing, justify).joinToString("|")

    private fun <T : Enum<T>> option(key: String, default: T, parse: (String) -> T) = object : kotlin.properties.ReadWriteProperty<Any, T> {
        override fun getValue(thisRef: Any, property: kotlin.reflect.KProperty<*>): T =
            prefs.getString(key, null)?.let { runCatching { parse(it) }.getOrNull() } ?: default
        override fun setValue(thisRef: Any, property: kotlin.reflect.KProperty<*>, value: T) = prefs.edit().putString(key, value.name).apply()
    }

    companion object {
        /** About 220 words a minute. */
        const val DEFAULT_SPEED = 1100f
        const val MIN_SIZE = 12
        const val MAX_SIZE = 32
        val MARGINS = intArrayOf(14, 28, 44)
        val SPACINGS = floatArrayOf(1.3f, 1.55f, 1.8f)
        /** How much of the last page shows through, for faint and strong ghosting. */
        fun ghost(level: Level) = when (level) { Level.OFF -> 0f; Level.FAINT -> 0.10f; Level.STRONG -> 0.2f }
        fun grainAlpha(level: Level) = when (level) { Level.OFF -> 0; Level.FAINT -> 18; Level.STRONG -> 38 }
    }
}
