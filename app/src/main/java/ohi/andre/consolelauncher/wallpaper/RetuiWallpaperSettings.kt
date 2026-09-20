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
        else -> saved
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
