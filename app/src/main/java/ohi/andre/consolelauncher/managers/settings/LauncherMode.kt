package ohi.andre.consolelauncher.managers.settings

import android.content.Context
import java.util.Locale
import ohi.andre.consolelauncher.managers.xml.options.Behavior

object LauncherMode {
    const val CLASSIC = "classic"
    const val SEARCH = "search"
    const val TERMINAL = "terminal"
    /** A black screen with one dot; hold for app rings, double-tap the dot to search. */
    const val DOT = "dot"
    /** The classic layout in two colours with a pixel font; see [MonoMode]. */
    const val MONO = "mono"

    val values = listOf(CLASSIC, SEARCH, TERMINAL, DOT, MONO)

    fun current(): String = resolve(
        LauncherSettings.get(Behavior.launcher_mode),
        LauncherSettings.getBoolean(Behavior.search_only_mode)
    )

    fun isSearch(mode: String = current()): Boolean = mode == SEARCH
    fun usesSearch(mode: String = current()): Boolean = mode in listOf(SEARCH, TERMINAL, DOT)
    fun isTerminal(mode: String = current()): Boolean = mode == TERMINAL
    fun isDot(mode: String = current()): Boolean = mode == DOT
    fun isMono(mode: String = current()): Boolean = mode == MONO

    /** This home keeps the keyboard away until its own gesture reveals it, instead of opening it on focus. */
    fun managesOwnKeyboard(mode: String = current()): Boolean = mode in listOf(TERMINAL, DOT, MONO)

    fun set(context: Context?, mode: String?): Boolean {
        val normalized = normalize(mode) ?: return false
        LauncherSettings.set(context, Behavior.launcher_mode, normalized)
        return true
    }

    fun resolve(rawMode: String?, legacySearchOnly: Boolean): String {
        val normalized = normalize(rawMode)
        if (normalized != null) return normalized
        return if (rawMode.isNullOrBlank() && legacySearchOnly) SEARCH else CLASSIC
    }

    fun normalize(mode: String?): String? {
        val normalized = mode?.trim()?.lowercase(Locale.US).orEmpty()
        return values.firstOrNull { it == normalized }
    }

    fun nextTarget(current: String = current()): String = if (current == CLASSIC) SEARCH else CLASSIC
}
