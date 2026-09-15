package ohi.andre.consolelauncher.managers

import android.content.Context
import java.util.Arrays
import java.util.Locale
import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.managers.settings.LauncherSettings
import ohi.andre.consolelauncher.managers.xml.classes.XMLPrefsSave
import ohi.andre.consolelauncher.managers.xml.options.Toolbar

object ToolbarShortcutManager {
    const val MAX_SLOTS = 2
    const val DEFAULT_ICON = "star"

    private val ICONS = arrayOf(
        IconChoice("star", R.string.toolbar_icon_star, R.drawable.ic_toolbar_star_24),
        IconChoice("bell", R.string.toolbar_icon_bell, R.drawable.ic_toolbar_bell_24),
        IconChoice("chat", R.string.toolbar_icon_chat, R.drawable.ic_toolbar_chat_24),
        IconChoice("music", R.string.toolbar_icon_music, R.drawable.ic_toolbar_music_24),
        IconChoice("timer", R.string.toolbar_icon_timer, R.drawable.timer_24),
        IconChoice("note", R.string.toolbar_icon_note, R.drawable.ic_toolbar_note_24),
        IconChoice("search", R.string.toolbar_icon_search, R.drawable.ic_toolbar_search_24),
        IconChoice("refresh", R.string.toolbar_icon_refresh, R.drawable.ic_toolbar_refresh_24),
        IconChoice("home", R.string.toolbar_icon_home, R.drawable.ic_toolbar_home_24),
        IconChoice("terminal", R.string.toolbar_icon_terminal, R.drawable.ic_toolbar_terminal_24),
        IconChoice("lock", R.string.toolbar_icon_lock, R.drawable.ic_tuixt_lock_24),
        IconChoice("apps", R.string.toolbar_icon_apps, R.drawable.ic_menu)
    )

    @JvmStatic
    fun icons(): List<IconChoice> = Arrays.asList(*ICONS)

    @JvmStatic
    fun slot(slot: Int): Slot {
        validateSlot(slot)
        val command = clean(LauncherSettings.get(commandOption(slot)))
        val icon = normalizeIcon(LauncherSettings.get(iconOption(slot)))
        val enabled = LauncherSettings.getBoolean(enabledOption(slot)) && command.isNotEmpty()
        return Slot(slot, enabled, command, icon, iconRes(icon), iconLabel(icon))
    }

    @JvmStatic
    fun saveSlot(context: Context, slot: Int, enabled: Boolean, command: String?, icon: String?) {
        validateSlot(slot)
        val cleanCommand = clean(command)
        val cleanIcon = normalizeIcon(icon)
        LauncherSettings.set(context, enabledOption(slot), (enabled && cleanCommand.isNotEmpty()).toString())
        LauncherSettings.set(context, commandOption(slot), cleanCommand)
        LauncherSettings.set(context, iconOption(slot), cleanIcon)
    }

    @JvmStatic
    fun clearSlot(context: Context, slot: Int) {
        saveSlot(context, slot, false, "", DEFAULT_ICON)
    }

    @JvmStatic
    fun iconRes(icon: String?): Int {
        val normalized = normalizeIcon(icon)
        for (choice in ICONS) {
            if (choice.key == normalized) {
                return choice.drawableRes
            }
        }
        return R.drawable.ic_toolbar_star_24
    }

    @JvmStatic
    fun iconLabel(icon: String?): Int {
        val normalized = normalizeIcon(icon)
        for (choice in ICONS) {
            if (choice.key == normalized) {
                return choice.labelRes
            }
        }
        return R.string.toolbar_icon_star
    }

    @JvmStatic
    fun normalizeIcon(icon: String?): String {
        val value = clean(icon).lowercase(Locale.US)
        if (value.isEmpty()) {
            return DEFAULT_ICON
        }
        for (choice in ICONS) {
            if (choice.key == value) {
                return value
            }
        }
        return DEFAULT_ICON
    }

    private fun enabledOption(slot: Int): XMLPrefsSave =
        if (slot == 1) Toolbar.shortcut_button_1_enabled else Toolbar.shortcut_button_2_enabled

    private fun commandOption(slot: Int): XMLPrefsSave =
        if (slot == 1) Toolbar.shortcut_button_1_command else Toolbar.shortcut_button_2_command

    private fun iconOption(slot: Int): XMLPrefsSave =
        if (slot == 1) Toolbar.shortcut_button_1_icon else Toolbar.shortcut_button_2_icon

    private fun validateSlot(slot: Int) {
        if (slot < 1 || slot > MAX_SLOTS) {
            throw IllegalArgumentException("Invalid toolbar shortcut slot: $slot")
        }
    }

    private fun clean(value: String?): String = value?.trim() ?: ""

    class IconChoice(
        @JvmField val key: String,
        @JvmField val labelRes: Int,
        @JvmField val drawableRes: Int
    )

    class Slot(
        @JvmField val index: Int,
        @JvmField val enabled: Boolean,
        @JvmField val command: String,
        @JvmField val icon: String,
        @JvmField val iconRes: Int,
        @JvmField val iconLabel: Int
    )
}
