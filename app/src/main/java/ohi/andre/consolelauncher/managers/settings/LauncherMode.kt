package ohi.andre.consolelauncher.managers.settings

import android.content.Context
import java.util.Locale
import ohi.andre.consolelauncher.managers.xml.options.Behavior

object LauncherMode {
    const val CLASSIC = "classic"
    const val SEARCH = "search"

    val values = listOf(CLASSIC, SEARCH)

    fun current(): String = resolve(
        LauncherSettings.get(Behavior.launcher_mode),
        LauncherSettings.getBoolean(Behavior.search_only_mode)
    )

    fun isClassic(): Boolean = current() == CLASSIC
    fun isSearch(): Boolean = current() == SEARCH

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
