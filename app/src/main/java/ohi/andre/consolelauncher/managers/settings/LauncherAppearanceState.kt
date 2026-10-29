package ohi.andre.consolelauncher.managers.settings

import ohi.andre.consolelauncher.UIManager
import ohi.andre.consolelauncher.managers.TerminalManager

/** Read-only copies of already-rendered launcher content; contains no actions or managers. */
data class LauncherAppearanceState(
    val labels: Map<UIManager.Label, CharSequence> = emptyMap(),
    val terminal: TerminalManager.State? = null,
    val moduleTitle: String = "",
    val moduleText: CharSequence = "",
    val song: String? = null,
    val singer: String? = null,
    val playing: Boolean = false,
    val musicApp: String = "",
    val suggestions: List<Pair<String, Int>> = emptyList(),
    val unlock: ohi.andre.consolelauncher.managers.status.UnlockManager.State? = null,
    val luaModule: String? = null,
    val luaResult: ohi.andre.consolelauncher.managers.lua.LuaWidgetEngine.RenderResult? = null
)
