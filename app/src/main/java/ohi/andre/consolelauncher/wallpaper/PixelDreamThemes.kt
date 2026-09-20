package ohi.andre.consolelauncher.wallpaper

import android.graphics.Color

data class PixelDreamTheme(
    val id: String,
    val name: String,
    val light: Boolean,
    val fieldBg: Int,
    val fieldDim: Int,
    val fieldMid: Int,
    val fieldLit: Int,
    val fieldHover: Int,
    val fieldCrest: Int
)

object PixelDreamThemes {
    const val DEFAULT_ID = "tokyo-night"
    const val CUSTOM_ID = "custom"

    val themes = listOf(
        theme("catppuccin", "Catppuccin", false, "#101019", "#34415c", "#5b76a4", "#89b4fa", "#accafc", "#d2e2fd"),
        theme("catppuccin-latte", "Catppuccin Latte", true, "#d7d8dc", "#a0b6e4", "#6491ec", "#1e66f5", "#1750bf", "#103887"),
        theme("ethereal", "Ethereal", false, "#030610", "#282b4c", "#4f538d", "#7d82d9", "#a4a8e4", "#ced0f1"),
        theme("everforest", "Everforest", false, "#181d20", "#374c4c", "#587f7b", "#7fbbb3", "#a5cfca", "#cee5e2"),
        theme("flexoki-light", "Flexoki Light", true, "#e5e2d8", "#aabac9", "#6b90b9", "#205ea6", "#194981", "#12345b"),
        theme("gruvbox", "Gruvbox", false, "#161616", "#354440", "#56746d", "#7daea3", "#a4c6bf", "#cee0dc"),
        theme("hackerman", "Hackerman", false, "#06060c", "#2b5037", "#539e65", "#82fb9c", "#a8fcba", "#d0fdd9"),
        theme("kanagawa", "Kanagawa", false, "#111116", "#4e4c47", "#8f8c7c", "#dcd7ba", "#e6e3cf", "#f2f0e5"),
        theme("last-horizon", "Last Horizon", false, "#060606", "#3a322f", "#72605c", "#b59790", "#cbb6b1", "#e3d7d5"),
        theme("lumon", "Lumon", false, "#0b1216", "#314956", "#5a839a", "#8bc9eb", "#aed9f1", "#d3eaf7"),
        theme("lupine", "Lupine", true, "#dedede", "#aab9e2", "#7392e6", "#3264eb", "#274eb7", "#1c3781"),
        theme("matte-black", "Matte Black", false, "#090909", "#4b310a", "#925b0b", "#e68e0d", "#eeb056", "#f6d4a3"),
        theme("miasma", "Miasma", false, "#121212", "#313423", "#515735", "#78824b", "#a0a881", "#ccd0bb"),
        theme("nord", "Nord", false, "#191c23", "#384452", "#596e85", "#81a1c1", "#a7bdd4", "#cfdbe7"),
        theme("osaka-jade", "Osaka Jade", false, "#090f0d", "#1e372c", "#35614d", "#509475", "#84b49e", "#bcd6cb"),
        theme("retro-82", "Retro 82", false, "#020c17", "#4c3b2f", "#9c6d49", "#faa968", "#fcc395", "#fddec6"),
        theme("ristretto", "Ristretto", false, "#181414", "#5a3830", "#a05f4d", "#f38d70", "#f7af9b", "#fad4c9"),
        theme("rose-pine", "Rosé Pine", true, "#e1dbd5", "#b7c6c5", "#8bafb4", "#56949f", "#43737c", "#2f5157"),
        theme("solitude", "Solitude", false, "#080a0b", "#2a2e30", "#4e5457", "#798186", "#a1a7aa", "#cccfd1"),
        theme("tokyo-night", "Tokyo Night", false, "#0e0e14", "#39482e", "#678549", "#9ece6a", "#bbdd97", "#daecc6"),
        theme("vantablack", "Vantablack", false, "#070707", "#2f2f2f", "#5a5a5a", "#8d8d8d", "#afafaf", "#d4d4d4"),
        theme("white", "White", true, "#e8e8e8", "#c3c3c3", "#9c9c9c", "#6e6e6e", "#565656", "#3c3c3c")
    )

    val ids: List<String> = themes.map { it.id } + CUSTOM_ID
    val names: List<String> = themes.map { it.name } + "Custom"

    fun find(id: String): PixelDreamTheme? = themes.firstOrNull { it.id == id }

    fun indexOf(id: String): Int = when {
        id == CUSTOM_ID -> ids.lastIndex
        else -> themes.indexOfFirst { it.id == id }.takeIf { it >= 0 } ?: 0
    }

    fun idAt(index: Int): String = ids[index.coerceIn(0, ids.lastIndex)]

    private fun theme(
        id: String,
        name: String,
        light: Boolean,
        bg: String,
        dim: String,
        mid: String,
        lit: String,
        hover: String,
        crest: String
    ) = PixelDreamTheme(
        id, name, light,
        Color.parseColor(bg), Color.parseColor(dim), Color.parseColor(mid),
        Color.parseColor(lit), Color.parseColor(hover), Color.parseColor(crest)
    )
}
