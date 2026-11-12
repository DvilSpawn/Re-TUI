package ohi.andre.consolelauncher.wallpaper

import android.content.Context
import java.io.File
import kotlin.random.Random
import ohi.andre.consolelauncher.tuils.Tuils

object RetuiWallpaperSettings {
    private const val PREFS = "retui_wallpaper"
    private const val OFFSET_X = "offset_x"
    private const val OFFSET_Y = "offset_y"
    private const val SCALE = "scale"
    private const val HEIGHT = "height"
    private const val CANOPY_WIDTH = "canopy_width"
    private const val PETAL_DENSITY = "petal_density"
    private const val TREE_SEED = "tree_seed"
    private const val PALETTE = "palette"
    private const val SCENE = "scene"
    private const val CLOUD_SCENE = "cloud_scene"
    private const val ASTRO_SEED = "astro_seed"
    private const val ASTRO_MONO = "astro_mono"
    private const val PIXEL_LOOK = "pixel_look_"
    private const val DOOM_FIRE_COLORS = "doom_fire_colors"
    private const val PHOTO_RIPPLES = "photo_ripples"
    private const val PHOTO_OFFSET = "photo_offset"
    private const val SHORE_OFFSET_X = "shore_offset_x"
    private const val SHORE_OFFSET_Y = "shore_offset_y"
    private const val SHORE_MODE = "shore_mode"
    private const val SHORE_TEXT = "shore_text"
    private const val MATRIX_STYLE = "matrix_style"
    private const val MATRIX_GLYPHS = "matrix_glyphs"
    private const val BLACK_HOLE_PALETTE = "black_hole_palette"
    private const val SOLID_COLOR = "solid_color"
    private const val TOPO_OFFSET_X = "topo_offset_x"
    private const val TOPO_OFFSET_Y = "topo_offset_y"
    private const val TOPO_SCALE = "topo_scale"
    private const val TOPO_RELIEF = "topo_relief"
    private const val TOPO_DENSITY = "topo_density"
    private const val TOPO_SEED = "topo_seed"
    private const val TOPO_PALETTE = "topo_palette"
    private const val TOPO_BACKGROUND = "topo_background"
    private const val TOPO_LINE = "topo_line"
    private const val TOPO_INDEX = "topo_index"
    private const val PIXEL_PALETTE = "pixel_palette"
    private const val PIXEL_SEED = "pixel_seed"
    private const val PIXEL_BACKGROUND = "pixel_background"
    private const val PIXEL_DIM = "pixel_dim"
    private const val PIXEL_MID = "pixel_mid"
    private const val PIXEL_LIT = "pixel_lit"
    private const val PIXEL_HOVER = "pixel_hover"
    private const val PIXEL_CREST = "pixel_crest"
    private const val LEGACY_PIXEL_ACCENT = "pixel_accent"
    private const val LEGACY_PIXEL_HIGHLIGHT = "pixel_highlight"

    fun offsetX(context: Context): Float = prefs(context).getFloat(OFFSET_X, 0f)
    fun offsetY(context: Context): Float = prefs(context).getFloat(OFFSET_Y, 0f)
    fun scale(context: Context): Float = prefs(context).getFloat(SCALE, 1f)
    fun height(context: Context): Float = prefs(context).getFloat(HEIGHT, 0.72f)
    fun treeWidth(context: Context): Float = prefs(context).getFloat(CANOPY_WIDTH, 1f)
    fun petalDensity(context: Context): Int = prefs(context).getInt(PETAL_DENSITY, 5)
    fun treeSeed(context: Context): Int {
        val prefs = prefs(context)
        if (prefs.contains(TREE_SEED)) return prefs.getInt(TREE_SEED, 0)
        return Random.nextInt().also { prefs.edit().putInt(TREE_SEED, it).apply() }
    }
    fun palette(context: Context): String = prefs(context).getString(PALETTE, "sakura") ?: "sakura"
    fun scene(context: Context): String = when (val saved = prefs(context).getString(SCENE, "csakura") ?: "csakura") {
        "waterfall" -> "black hole"
        "clouds 8" -> CloudsView.SCENE
        else -> saved
    }
    fun cloudScene(context: Context): Int = prefs(context).getInt(CLOUD_SCENE, 8).coerceIn(1, 8)

    fun astroSeed(context: Context): Int {
        val prefs = prefs(context)
        if (prefs.contains(ASTRO_SEED)) return prefs.getInt(ASTRO_SEED, 0)
        return Random.nextInt(0, 1 shl 20).also { prefs.edit().putInt(ASTRO_SEED, it).apply() }
    }

    fun saveAstroSeed(context: Context, seed: Int, mono: Boolean = astroMono(context)) {
        prefs(context).edit().putInt(ASTRO_SEED, seed).putBoolean(ASTRO_MONO, mono).apply()
    }

    fun astroMono(context: Context): Boolean = prefs(context).getBoolean(ASTRO_MONO, false)

    /** The look (palette) a pixel wallpaper is dithered to, per scene; null keeps the scene's default. */
    fun pixelLook(context: Context, scene: String): String? = prefs(context).getString(PIXEL_LOOK + scene, null)
    fun photoRipples(context: Context): Boolean = prefs(context).getBoolean(PHOTO_RIPPLES, true)
    fun photoOffset(context: Context): Float = prefs(context).getFloat(PHOTO_OFFSET, 0f)
    fun shoreOffsetX(context: Context): Float = prefs(context).getFloat(SHORE_OFFSET_X, 0f)
    fun shoreOffsetY(context: Context): Float = prefs(context).getFloat(SHORE_OFFSET_Y, 0f)
    fun shoreMode(context: Context): String = prefs(context).getString(SHORE_MODE, "time") ?: "time"
    fun shoreText(context: Context): String = prefs(context).getString(SHORE_TEXT, "") ?: ""
    fun saveShoreline(context: Context, look: String, offsetX: Float, offsetY: Float, mode: String, text: String) {
        prefs(context).edit().putString(PIXEL_LOOK + "shoreline", look).putFloat(SHORE_OFFSET_X, offsetX).putFloat(SHORE_OFFSET_Y, offsetY)
            .putString(SHORE_MODE, mode).putString(SHORE_TEXT, text).apply()
    }
    fun savePixelWallpaper(context: Context, scene: String, look: String, ripples: Boolean? = null, photoOffset: Float? = null) {
        prefs(context).edit().putString(PIXEL_LOOK + scene, look).apply {
            if (ripples != null) putBoolean(PHOTO_RIPPLES, ripples)
            if (photoOffset != null) putFloat(PHOTO_OFFSET, photoOffset)
        }.apply()
    }

    /** Doom fire's own colours (background, embers, flame, tips), or null before any were picked. */
    fun doomFireColors(context: Context): IntArray? = prefs(context).getString(DOOM_FIRE_COLORS, null)
        ?.split(',')?.mapNotNull { it.trim().toLongOrNull(16)?.toInt() }?.takeIf { it.size == 4 }?.toIntArray()

    fun saveDoomFireColors(context: Context, colors: IntArray) {
        prefs(context).edit().putString(DOOM_FIRE_COLORS, colors.joinToString(",") { "%08X".format(it) }).apply()
    }

    fun matrixStyle(context: Context): String = prefs(context).getString(MATRIX_STYLE, "classic") ?: "classic"
    fun matrixGlyphs(context: Context): String = prefs(context).getString(MATRIX_GLYPHS, "katakana") ?: "katakana"

    fun saveMatrix(context: Context, style: String, glyphs: String) {
        prefs(context).edit().putString(MATRIX_STYLE, style).putString(MATRIX_GLYPHS, glyphs.lowercase()).apply()
    }

    fun saveCloudScene(context: Context, number: Int) {
        prefs(context).edit().putInt(CLOUD_SCENE, number.coerceIn(1, 8)).apply()
    }
    fun blackHolePalette(context: Context): String =
        prefs(context).getString(BLACK_HOLE_PALETTE, "amber") ?: "amber"
    fun solidColor(context: Context): String =
        prefs(context).getString(SOLID_COLOR, "#FF000000") ?: "#FF000000"
    fun topoOffsetX(context: Context): Float = prefs(context).getFloat(TOPO_OFFSET_X, 0f)
    fun topoOffsetY(context: Context): Float = prefs(context).getFloat(TOPO_OFFSET_Y, 0f)
    fun topoScale(context: Context): Float = prefs(context).getFloat(TOPO_SCALE, 1f)
    fun topoRelief(context: Context): Float = prefs(context).getFloat(TOPO_RELIEF, 0.72f)
    fun topoDensity(context: Context): Int = prefs(context).getInt(TOPO_DENSITY, 5)
    fun topoSeed(context: Context): Int {
        val prefs = prefs(context)
        if (prefs.contains(TOPO_SEED)) return prefs.getInt(TOPO_SEED, 0)
        return Random.nextInt().also { prefs.edit().putInt(TOPO_SEED, it).apply() }
    }
    fun topoPalette(context: Context): String = prefs(context).getString(TOPO_PALETTE, "graphite") ?: "graphite"
    fun topoBackground(context: Context): Int = prefs(context).getInt(TOPO_BACKGROUND, 0xFF0A0B0F.toInt())
    fun topoLine(context: Context): Int = prefs(context).getInt(TOPO_LINE, 0xFF5C5C5C.toInt())
    fun topoIndex(context: Context): Int = prefs(context).getInt(TOPO_INDEX, 0xFFDCDCDC.toInt())
    fun pixelDreamPalette(context: Context): String {
        val saved = prefs(context).getString(PIXEL_PALETTE, PixelDreamThemes.DEFAULT_ID)
        return when {
            saved == PixelDreamView.CUSTOM -> saved
            saved != null && PixelDreamThemes.find(saved) != null -> saved
            else -> PixelDreamThemes.DEFAULT_ID
        }
    }
    fun pixelDreamSeed(context: Context): Int {
        val prefs = prefs(context)
        if (prefs.contains(PIXEL_SEED)) return prefs.getInt(PIXEL_SEED, 0)
        return Random.nextInt().also { prefs.edit().putInt(PIXEL_SEED, it).apply() }
    }
    fun pixelDreamBackground(context: Context): Int = prefs(context).getInt(PIXEL_BACKGROUND, defaultPixelTheme().fieldBg)
    fun pixelDreamDim(context: Context): Int = prefs(context).getInt(PIXEL_DIM, defaultPixelTheme().fieldDim)
    fun pixelDreamMid(context: Context): Int = prefs(context).getInt(PIXEL_MID, legacyPixelColor(context, LEGACY_PIXEL_ACCENT, defaultPixelTheme().fieldMid))
    fun pixelDreamLit(context: Context): Int = prefs(context).getInt(PIXEL_LIT, legacyPixelColor(context, LEGACY_PIXEL_ACCENT, defaultPixelTheme().fieldLit))
    fun pixelDreamHover(context: Context): Int = prefs(context).getInt(PIXEL_HOVER, legacyPixelColor(context, LEGACY_PIXEL_HIGHLIGHT, defaultPixelTheme().fieldHover))
    fun pixelDreamCrest(context: Context): Int = prefs(context).getInt(PIXEL_CREST, legacyPixelColor(context, LEGACY_PIXEL_HIGHLIGHT, defaultPixelTheme().fieldCrest))

    fun themeColors(): List<String> = runCatching {
        uniqueThemeColors(File(Tuils.getFolder(), "theme.xml").readText())
    }.getOrDefault(emptyList())

    fun saveScene(context: Context, scene: String) {
        prefs(context).edit().putString(SCENE, scene).apply()
    }

    fun saveBlackHolePalette(context: Context, palette: String) {
        prefs(context).edit().putString(BLACK_HOLE_PALETTE, palette).apply()
    }

    fun saveSolidColor(context: Context, color: String) {
        prefs(context).edit().putString(SOLID_COLOR, color).apply()
    }

    fun saveTopo(
        context: Context,
        offsetX: Float,
        offsetY: Float,
        scale: Float,
        relief: Float,
        density: Int,
        seed: Int,
        palette: String,
        background: Int,
        line: Int,
        index: Int
    ) {
        prefs(context).edit()
            .putFloat(TOPO_OFFSET_X, offsetX)
            .putFloat(TOPO_OFFSET_Y, offsetY)
            .putFloat(TOPO_SCALE, scale.coerceIn(0.5f, 2f))
            .putFloat(TOPO_RELIEF, relief.coerceIn(0.45f, 1f))
            .putInt(TOPO_DENSITY, density.coerceIn(1, 10))
            .putInt(TOPO_SEED, seed)
            .putString(TOPO_PALETTE, palette)
            .putInt(TOPO_BACKGROUND, background)
            .putInt(TOPO_LINE, line)
            .putInt(TOPO_INDEX, index)
            .apply()
    }

    fun savePixelDream(
        context: Context,
        palette: String,
        seed: Int,
        background: Int,
        dim: Int,
        mid: Int,
        lit: Int,
        hover: Int,
        crest: Int
    ) {
        prefs(context).edit()
            .putString(PIXEL_PALETTE, palette)
            .putInt(PIXEL_SEED, seed)
            .putInt(PIXEL_BACKGROUND, background)
            .putInt(PIXEL_DIM, dim)
            .putInt(PIXEL_MID, mid)
            .putInt(PIXEL_LIT, lit)
            .putInt(PIXEL_HOVER, hover)
            .putInt(PIXEL_CREST, crest)
            .apply()
    }

    fun save(context: Context, offsetX: Float, offsetY: Float, scale: Float, height: Float,
             treeWidth: Float, petalDensity: Int, treeSeed: Int, palette: String) {
        prefs(context).edit()
            .putFloat(OFFSET_X, offsetX)
            .putFloat(OFFSET_Y, offsetY)
            .putFloat(SCALE, scale.coerceIn(0.5f, 2f))
            .putFloat(HEIGHT, height.coerceIn(0.45f, 1f))
            .putFloat(CANOPY_WIDTH, treeWidth.coerceIn(0.75f, 2.5f))
            .putInt(PETAL_DENSITY, petalDensity.coerceIn(1, 10))
            .putInt(TREE_SEED, treeSeed)
            .putString(PALETTE, palette)
            .apply()
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** The applied live wallpaper settings, for a preset to keep. Values carry their type so [restore] is exact. */
    fun snapshot(context: Context): org.json.JSONObject {
        val out = org.json.JSONObject()
        for ((key, value) in prefs(context).all) {
            val typed = when (value) {
                is Boolean -> "b"; is Int -> "i"; is Long -> "l"; is Float -> "f"; is String -> "s"
                is Set<*> -> "S"; else -> continue
            }
            out.put(key, org.json.JSONObject().put("t", typed).put("v",
                if (value is Set<*>) org.json.JSONArray(value.filterIsInstance<String>()) else value))
        }
        return out
    }

    /** Replaces the applied settings with a [snapshot] and drops any choice staged in the picker. */
    fun restore(context: Context, saved: org.json.JSONObject) {
        val values = linkedMapOf<String, Any>()
        for (key in saved.keys()) {
            val entry = saved.optJSONObject(key) ?: continue
            values[key] = when (entry.optString("t")) {
                "b" -> entry.optBoolean("v"); "i" -> entry.optInt("v"); "l" -> entry.optLong("v")
                "f" -> entry.optDouble("v").toFloat(); "s" -> entry.optString("v")
                "S" -> entry.optJSONArray("v")?.let { a -> (0 until a.length()).map(a::optString).toSet() } ?: continue
                else -> continue
            }
        }
        val editor = prefs(context).edit().clear()
        copy(values, editor)
        editor.commit()
        discardPending(context)
    }

    // Use on phone stages the picker's choices here. Android's apply page previews them (its engine reads through
    // previewContext); the running wallpaper keeps the committed settings until the user really applies.
    private const val PENDING = "retui_wallpaper_pending"
    private const val PENDING_MARK = "__pending"
    private const val APPLY_STARTED = "__apply_started"
    private const val PREVIEWED = "__previewed"
    /** How long after Use on phone a wallpaper change still counts as the user applying the staged choice. */
    private const val APPLY_WINDOW_MS = 10 * 60_000L

    /** Settings as the apply page should preview them: the staged ones while a choice is pending. */
    class PreviewContext(base: Context) : android.content.ContextWrapper(base) {
        override fun getSharedPreferences(name: String?, mode: Int): android.content.SharedPreferences {
            val pending = baseContext.getSharedPreferences(PENDING, Context.MODE_PRIVATE)
            return if (name == PREFS && pending.contains(PENDING_MARK)) pending else super.getSharedPreferences(name, mode)
        }
    }

    fun previewContext(context: Context): Context = context as? PreviewContext ?: PreviewContext(context)

    /** Starts a staged copy of the current settings; save into the returned context. */
    fun beginPending(context: Context): Context {
        val pending = context.getSharedPreferences(PENDING, Context.MODE_PRIVATE).edit().clear()
        copy(prefs(context).all, pending)
        pending.putBoolean(PENDING_MARK, true).commit()
        return PreviewContext(context)
    }

    fun hasPending(context: Context): Boolean = context.getSharedPreferences(PENDING, Context.MODE_PRIVATE).contains(PENDING_MARK)

    /** Android's apply page is opening for the staged choice. */
    fun markApplyStarted(context: Context) {
        context.getSharedPreferences(PENDING, Context.MODE_PRIVATE).edit().putLong(APPLY_STARTED, System.currentTimeMillis()).commit()
    }

    /** The apply page is showing the staged choice. */
    fun markPreviewed(context: Context) {
        if (applyInProgress(context)) context.getSharedPreferences(PENDING, Context.MODE_PRIVATE).edit().putBoolean(PREVIEWED, true).commit()
    }

    /** The staged choice was shown on the apply page, which has since closed somewhere other than the picker. */
    fun previewedForApply(context: Context): Boolean =
        applyInProgress(context) && context.getSharedPreferences(PENDING, Context.MODE_PRIVATE).getBoolean(PREVIEWED, false)

    /** A staged choice whose apply page opened in the last few minutes. */
    fun applyInProgress(context: Context): Boolean {
        val staged = context.getSharedPreferences(PENDING, Context.MODE_PRIVATE)
        val started = staged.getLong(APPLY_STARTED, 0L)
        return staged.contains(PENDING_MARK) && System.currentTimeMillis() - started in 0..APPLY_WINDOW_MS
    }

    /** The user applied: the staged settings become the wallpaper's. */
    fun commitPending(context: Context) {
        val staged = context.getSharedPreferences(PENDING, Context.MODE_PRIVATE)
        if (!staged.contains(PENDING_MARK)) return
        val committed = prefs(context).edit().clear()
        copy(staged.all.filterKeys { it != PENDING_MARK && it != APPLY_STARTED && it != PREVIEWED }, committed)
        committed.commit()
        staged.edit().clear().commit()
    }

    fun discardPending(context: Context) {
        context.getSharedPreferences(PENDING, Context.MODE_PRIVATE).edit().clear().commit()
    }

    private fun copy(values: Map<String, *>, editor: android.content.SharedPreferences.Editor) {
        for ((key, value) in values) when (value) {
            is Boolean -> editor.putBoolean(key, value)
            is Int -> editor.putInt(key, value)
            is Long -> editor.putLong(key, value)
            is Float -> editor.putFloat(key, value)
            is String -> editor.putString(key, value)
            is Set<*> -> editor.putStringSet(key, value.filterIsInstance<String>().toSet())
        }
    }

    private fun legacyPixelColor(context: Context, key: String, fallback: Int): Int =
        prefs(context).getInt(key, fallback)

    private fun defaultPixelTheme(): PixelDreamTheme =
        PixelDreamThemes.find(PixelDreamThemes.DEFAULT_ID)!!

    internal fun uniqueThemeColors(xml: String): List<String> =
        Regex("value=\"(#[0-9a-fA-F]{6}|#[0-9a-fA-F]{8})\"")
            .findAll(xml)
            .map { it.groupValues[1].uppercase() }
            .distinct()
            .toList()

    internal fun parseColorValue(value: String): Int? {
        val hex = value.trim().removePrefix("#").removePrefix("#")
        if ((hex.length != 6 && hex.length != 8) || hex.any { it !in "0123456789abcdefABCDEF" }) return null
        val parsed = hex.toLongOrNull(16) ?: return null
        return if (hex.length == 6) (0xFF000000L or parsed).toInt() else parsed.toInt()
    }
}
