package ohi.andre.consolelauncher.wallpaper

import android.content.Context
import kotlin.random.Random

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

    fun saveScene(context: Context, scene: String) {
        prefs(context).edit().putString(SCENE, scene).apply()
    }

    fun saveBlackHolePalette(context: Context, palette: String) {
        prefs(context).edit().putString(BLACK_HOLE_PALETTE, palette).apply()
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
}
