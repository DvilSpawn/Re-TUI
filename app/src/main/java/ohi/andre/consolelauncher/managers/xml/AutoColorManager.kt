package ohi.andre.consolelauncher.managers.xml

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Color
import android.os.Build
import androidx.annotation.WorkerThread
import ohi.andre.consolelauncher.R
import androidx.core.graphics.ColorUtils
import ohi.andre.consolelauncher.managers.xml.classes.XMLPrefsSave
import ohi.andre.consolelauncher.managers.xml.options.Suggestions
import ohi.andre.consolelauncher.managers.xml.options.Theme
import kotlin.math.max
import kotlin.math.min

object AutoColorManager {

    private const val NO_COLOR = Int.MAX_VALUE
    private var appContext: Context? = null
    enum class Source { WALLPAPER, SYSTEM, UNAVAILABLE }

    data class Snapshot(
        val colors: Map<XMLPrefsSave, Int>,
        val source: Source,
        val failureReason: String? = null
    ) {
        val succeeded: Boolean get() = colors.isNotEmpty()
        fun message(context: Context): String = context.getString(when (source) {
            Source.WALLPAPER -> R.string.command_wallpaper_wallpaper_colors_applied_804d5
            Source.SYSTEM -> R.string.command_wallpaper_system_palette_applied
            Source.UNAVAILABLE -> R.string.command_wallpaper_unable_to_read_wallpaper_colors_259c7
        })
    }

    internal data class SeedColors(val colors: IntArray?, val source: Source, val failureReason: String?)
    @Volatile private var lastSource = Source.UNAVAILABLE
    @Volatile private var lastFailure: String? = "not_requested"

    fun init(context: Context?) {
        if (context != null) appContext = context.applicationContext
    }

    fun debugSummary(): String = "auto_theme_source: ${lastSource.name.lowercase(java.util.Locale.ROOT)}\n" +
        "auto_theme_failure: ${lastFailure ?: "none"}"

    // Shared by production extraction and tests; failure of one source must not skip the fallback.
    internal fun selectSeedColors(
        sdk: Int,
        wallpaper: () -> IntArray?,
        system: () -> IntArray?
    ): SeedColors {
        var reason = if (sdk < 27) "wallpaper_api_unsupported" else "wallpaper_colors_unavailable"
        if (sdk >= 27) try {
            wallpaper()?.takeIf { it.size == 3 }?.let { return SeedColors(it, Source.WALLPAPER, null) }
        } catch (e: Exception) {
            reason = "wallpaper_${e.javaClass.simpleName}"
        }
        if (sdk >= 31) try {
            system()?.takeIf { it.size == 3 }?.let { return SeedColors(it, Source.SYSTEM, reason) }
            reason += ";system_palette_unavailable"
        } catch (e: Exception) {
            reason += ";system_${e.javaClass.simpleName}"
        }
        return SeedColors(null, Source.UNAVAILABLE, reason)
    }

    @android.annotation.SuppressLint("NewApi") // API checks are in selectSeedColors.
    @WorkerThread
    fun currentWallpaperColorSnapshot(context: Context? = null): Snapshot {
        val owner = context ?: appContext
        val seeds = selectSeedColors(Build.VERSION.SDK_INT, {
            val manager = WallpaperManager.getInstance(requireNotNull(owner))
            manager.getWallpaperColors(WallpaperManager.FLAG_SYSTEM)?.let {
                intArrayOf(it.primaryColor.toArgb(),
                    (it.secondaryColor ?: it.primaryColor).toArgb(),
                    (it.tertiaryColor ?: it.secondaryColor ?: it.primaryColor).toArgb())
            }
        }, {
            requireNotNull(owner).let { ctx ->
                intArrayOf(ctx.getColor(android.R.color.system_accent1_500),
                    ctx.getColor(android.R.color.system_accent2_500),
                    ctx.getColor(android.R.color.system_neutral1_900))
            }
        })
        return snapshotFromSeeds(seeds)
    }

    internal fun snapshotFromSeeds(seeds: SeedColors): Snapshot {
        lastSource = seeds.source
        lastFailure = seeds.failureReason
        val current = seeds.colors?.let { buildPalette(it[0], it[1], it[2]) }
            ?: return Snapshot(emptyMap(), Source.UNAVAILABLE, seeds.failureReason)
        val colors = buildMap<XMLPrefsSave, Int> {
            Theme.entries.filterNot { it.advanced }.forEach { value ->
                resolveThemeColor(value, current, NO_COLOR)
                    .takeUnless { it == NO_COLOR }?.let { put(value, it) }
            }
            Suggestions.entries.forEach { value ->
                resolveSuggestionsColor(value, current, NO_COLOR)
                    .takeUnless { it == NO_COLOR }?.let { put(value, it) }
            }
        }
        return Snapshot(colors, seeds.source, seeds.failureReason)
    }

    private fun buildPalette(primary: Int, secondary: Int, tertiary: Int): Palette {
        val base = darkestOf(primary, secondary, tertiary)
        val accentSeed = mostSaturatedOf(primary, secondary, tertiary)
        val background = clampLuminance(base, 0.14f)
        val surface = ColorUtils.blendARGB(background, accentSeed, 0.18f)
        val surfaceStrong = ColorUtils.blendARGB(background, accentSeed, 0.28f)
        val accent = ensureReadable(accentSeed, background, 4.2)
        val text = readableTextFor(background)
        val mutedText = ColorUtils.blendARGB(text, accent, 0.28f)
        return Palette(
            background, surface, surfaceStrong, accent, text, mutedText,
            buildSuggestionStyle(accent, background, 0f, 0.96f, 0.04f),
            buildSuggestionStyle(accent, background, 18f, 0.82f, 0.10f),
            buildSuggestionStyle(accent, background, -18f, 1.12f, -0.02f),
            buildSuggestionStyle(accent, background, 34f, 0.78f, 0.18f),
            buildSuggestionStyle(accent, background, -34f, 0.88f, 0.20f),
            buildSuggestionStyle(accent, background, 52f, 0.74f, 0.08f),
            buildSuggestionStyle(accent, background, -8f, 0.60f, 0.14f)
        )
    }

    private fun resolveThemeColor(theme: Theme, palette: Palette, fallbackColor: Int): Int {
        when (theme) {
            Theme.background_color -> return palette.background
            Theme.wallpaper_overlay_color -> return Color.TRANSPARENT
            Theme.terminal_window_background_color, Theme.terminal_header_background_color, Theme.toolbar_background_color -> return palette.surface
            Theme.input_background_color, Theme.output_background_color, Theme.suggestions_background_color,
            Theme.ram_status_background_color, Theme.device_status_background_color, Theme.time_status_background_color,
            Theme.battery_status_background_color, Theme.storage_status_background_color, Theme.network_status_background_color,
            Theme.notes_status_background_color, Theme.weather_status_background_color, Theme.unlock_status_background_color,
            Theme.ascii_status_background_color, Theme.unified_status_background_color -> return ColorUtils.setAlphaComponent(palette.surface, 208)

            Theme.input_text_color, Theme.device_text_color, Theme.ascii_text_color, Theme.time_text_color,
            Theme.storage_text_color, Theme.ram_text_color, Theme.network_info_text_color, Theme.alias_content_text_color,
            Theme.notes_text_color, Theme.locked_notes_text_color, Theme.weather_text_color, Theme.unlock_counter_text_color,
            Theme.terminal_border_color, Theme.terminal_header_border_color, Theme.module_text_color -> return palette.accent
            Theme.module_button_background_color -> return ColorUtils.setAlphaComponent(
                palette.surface,
                208
            )

            Theme.output_text_color, Theme.toolbar_icon_color, Theme.enter_icon_color, Theme.cursor_color,
            Theme.restart_message_text_color, Theme.session_info_text_color, Theme.link_text_color,
            Theme.app_installed_text_color, Theme.app_uninstalled_text_color, Theme.apps_drawer_text_color -> return palette.text
            Theme.regex_match_background_color -> return palette.accent
            Theme.battery_text_high -> return palette.accent
            Theme.battery_text_medium -> return palette.mutedText
            Theme.battery_text_low -> return palette.surfaceStrong
            Theme.ram_status_text_shadow_color,
            Theme.device_status_text_shadow_color, Theme.time_status_text_shadow_color,
            Theme.battery_status_text_shadow_color, Theme.storage_status_text_shadow_color,
            Theme.network_status_text_shadow_color, Theme.notes_status_text_shadow_color,
            Theme.weather_status_text_shadow_color, Theme.unlock_status_text_shadow_color,
            Theme.ascii_status_text_shadow_color, Theme.input_text_shadow_color, Theme.output_text_shadow_color,
            Theme.suggestions_text_shadow_color, Theme.module_text_shadow_color -> return fallbackColor
            else -> return fallbackColor
        }
    }

    private fun resolveSuggestionsColor(
        suggestion: Suggestions,
        palette: Palette,
        fallbackColor: Int
    ): Int {
        when (suggestion) {
            Suggestions.default_text_color -> return palette.defaultSuggestionStyle.text
            Suggestions.apps_text_color -> return palette.appSuggestionStyle.text
            Suggestions.alias_text_color -> return palette.aliasSuggestionStyle.text
            Suggestions.cmd_text_color -> return palette.commandSuggestionStyle.text
            Suggestions.song_text_color -> return palette.songSuggestionStyle.text
            Suggestions.contact_text_color -> return palette.contactSuggestionStyle.text
            Suggestions.file_text_color -> return palette.fileSuggestionStyle.text
            Suggestions.default_background_color -> return palette.defaultSuggestionStyle.background
            Suggestions.apps_background_color -> return palette.appSuggestionStyle.background
            Suggestions.alias_background_color -> return palette.aliasSuggestionStyle.background
            Suggestions.cmd_background_color -> return palette.commandSuggestionStyle.background
            Suggestions.song_background_color -> return palette.songSuggestionStyle.background
            Suggestions.contact_background_color -> return palette.contactSuggestionStyle.background
            Suggestions.file_background_color -> return palette.fileSuggestionStyle.background
            else -> return fallbackColor
        }
    }

    private fun darkestOf(vararg colors: Int): Int {
        var darkest = colors[0]
        var luminance = ColorUtils.calculateLuminance(darkest)
        for (i in 1..<colors.size) {
            val candidate = ColorUtils.calculateLuminance(colors[i])
            if (candidate < luminance) {
                darkest = colors[i]
                luminance = candidate
            }
        }
        return darkest
    }

    private fun mostSaturatedOf(vararg colors: Int): Int {
        var selected = colors[0]
        var bestSaturation = -1f
        val hsl = FloatArray(3)
        for (color in colors) {
            ColorUtils.colorToHSL(color, hsl)
            if (hsl[1] > bestSaturation) {
                bestSaturation = hsl[1]
                selected = color
            }
        }
        return selected
    }

    private fun clampLuminance(color: Int, maxLuminance: Float): Int {
        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(color, hsl)
        hsl[2] = min(hsl[2], maxLuminance)
        if (hsl[1] < 0.18f) {
            hsl[1] = 0.18f
        }
        return ColorUtils.HSLToColor(hsl)
    }

    private fun readableTextFor(background: Int): Int {
        val preferred = ColorUtils.blendARGB(Color.WHITE, background, 0.10f)
        if (ColorUtils.calculateContrast(preferred, background) >= 4.5) {
            return preferred
        }
        return Color.WHITE
    }

    private fun ensureReadable(seed: Int, background: Int, minContrast: Double): Int {
        if (ColorUtils.calculateContrast(seed, background) >= minContrast) {
            return seed
        }

        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(seed, hsl)
        hsl[1] = max(hsl[1], 0.28f)

        for (i in 0..7) {
            hsl[2] = min(0.92f, hsl[2] + 0.08f)
            val adjusted = ColorUtils.HSLToColor(hsl)
            if (ColorUtils.calculateContrast(adjusted, background) >= minContrast) {
                return adjusted
            }
        }

        return readableTextFor(background)
    }

    private fun buildSuggestionStyle(
        accent: Int,
        background: Int,
        hueShiftDegrees: Float,
        saturationScale: Float,
        lightnessDelta: Float
    ): SuggestionStyle {
        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(accent, hsl)

        hsl[0] = (hsl[0] + hueShiftDegrees + 360f) % 360f
        hsl[1] = clamp(hsl[1] * saturationScale, 0.18f, 0.92f)
        hsl[2] = clamp(hsl[2] + lightnessDelta, 0.34f, 0.78f)

        val tone = ColorUtils.HSLToColor(hsl)
        var backgroundTone = ColorUtils.blendARGB(tone, background, 0.20f)
        var textTone = readableTextFor(backgroundTone)

        if (ColorUtils.calculateContrast(textTone, backgroundTone) < 4.5) {
            backgroundTone = ensureReadable(backgroundTone, background, 3.0)
            textTone = readableTextFor(backgroundTone)
        }

        return SuggestionStyle(backgroundTone, textTone)
    }

    private fun clamp(value: Float, min: Float, max: Float): Float {
        return max(min, min(max, value))
    }

    private class SuggestionStyle(val background: Int, val text: Int)

    private class Palette(
        val background: Int,
        val surface: Int,
        val surfaceStrong: Int,
        val accent: Int,
        val text: Int,
        val mutedText: Int,
        val appSuggestionStyle: SuggestionStyle,
        val aliasSuggestionStyle: SuggestionStyle,
        val commandSuggestionStyle: SuggestionStyle,
        val songSuggestionStyle: SuggestionStyle,
        val contactSuggestionStyle: SuggestionStyle,
        val fileSuggestionStyle: SuggestionStyle,
        val defaultSuggestionStyle: SuggestionStyle
    )
}
