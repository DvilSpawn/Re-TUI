package ohi.andre.consolelauncher.commands.main.raw

import java.util.Locale
import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.commands.ExecutePack
import ohi.andre.consolelauncher.commands.main.MainPack
import ohi.andre.consolelauncher.commands.main.specific.ParamCommand
import ohi.andre.consolelauncher.managers.PresetManager
import ohi.andre.consolelauncher.managers.settings.AppearanceSettings
import ohi.andre.consolelauncher.managers.settings.LauncherSettings
import ohi.andre.consolelauncher.managers.settings.MusicSettings
import ohi.andre.consolelauncher.managers.settings.NotificationSettings
import ohi.andre.consolelauncher.managers.xml.classes.XMLPrefsSave
import ohi.andre.consolelauncher.managers.xml.options.Suggestions
import ohi.andre.consolelauncher.managers.xml.options.Theme
import ohi.andre.consolelauncher.managers.xml.options.Ui
import ohi.andre.consolelauncher.tuils.Tuils
import java.io.File

class debug : ParamCommand() {
    private enum class Param : ohi.andre.consolelauncher.commands.main.Param {
        theme {
            override fun exec(pack: ExecutePack): String = buildSettingsOutput(pack.context).toString()
        },
        settings {
            override fun exec(pack: ExecutePack): String = buildSettingsOutput(pack.context).toString()
        },
        presets {
            override fun exec(pack: ExecutePack): String {
                val output = StringBuilder()
                val dir = PresetManager.presetsDir
                output.append(pack.context.getString(R.string.command_debug_preset_dir_cb906)).append(dir.absolutePath).append(Tuils.NEWLINE)
                output.append(pack.context.getString(R.string.command_debug_saved_built_in_presets_69f98)).append(Tuils.NEWLINE)
                output.append(Tuils.toPlanString(PresetManager.listAllPresetNames(), Tuils.NEWLINE))
                return output.toString()
            }
        };

        override fun args(): IntArray = IntArray(0)

        override fun label(): String = Tuils.MINUS + name

        override fun onNotArgEnough(pack: ExecutePack, n: Int): String =
            pack.context.getString(R.string.help_debug)

        override fun onArgNotFound(pack: ExecutePack, index: Int): String =
            pack.context.getString(R.string.help_debug)

        companion object {
            fun get(p: String): Param? {
                val value = p.lowercase(Locale.ROOT)
                for (param in entries) {
                    if (value.endsWith(param.label())) {
                        return param
                    }
                }
                return null
            }

            fun labels(): Array<String> {
                val params = entries
                val labels = Array(params.size) { "" }
                for (i in params.indices) {
                    labels[i] = params[i].label()
                }
                return labels
            }
        }
    }

    override fun paramForString(pack: MainPack, param: String): ohi.andre.consolelauncher.commands.main.Param? =
        Param.get(param)

    override fun params(): Array<String> = Param.labels()

    override fun priority(): Int = 2

    override fun helpRes(): Int = R.string.help_debug

    override fun doThings(pack: ExecutePack): String? {
        if (pack.get(ohi.andre.consolelauncher.commands.main.Param::class.java, 0) != null) {
            return null
        }
        return pack.context.getString(R.string.help_debug)
    }

    companion object {
        private fun buildSettingsOutput(context: android.content.Context): StringBuilder {
            val output = StringBuilder()
            output.append(context.getString(R.string.command_detail_debug_runtime_settings_982a4)).append(Tuils.NEWLINE)
            output.append(LauncherSettings.debugSummary()).append(Tuils.NEWLINE)
            output.append(context.getString(R.string.command_detail_debug_auto_color_pick_7a5c0)).append(AppearanceSettings.autoColorPick()).append(Tuils.NEWLINE)
            output.append(context.getString(R.string.command_detail_debug_system_wallpaper_ace9c)).append(LauncherSettings.getBoolean(Ui.system_wallpaper)).append(Tuils.NEWLINE)
            output.append(context.getString(R.string.command_detail_debug_system_font_ee2d6)).append(AppearanceSettings.useSystemFont()).append(Tuils.NEWLINE)
            output.append(context.getString(R.string.command_detail_debug_font_file_c5936)).append(AppearanceSettings.fontFile()).append(Tuils.NEWLINE)
            appendValue(output, Ui.enable_dashed_border)
            appendValue(output, Ui.dashed_border_dash_length)
            appendValue(output, Ui.dashed_border_gap_length)
            appendValue(output, Ui.dashed_border_stroke_width)
            appendValue(output, Ui.dashed_border_corner_radius)
            output.append(context.getString(R.string.command_detail_debug_notification_terminal_c4355)).append(NotificationSettings.showTerminal()).append(Tuils.NEWLINE)
            output.append(context.getString(R.string.command_detail_debug_notification_output_16b6e)).append(NotificationSettings.printToOutput()).append(Tuils.NEWLINE)
            output.append(context.getString(R.string.command_detail_debug_music_enabled_0fb24)).append(MusicSettings.enabled()).append(Tuils.NEWLINE)
            output.append(context.getString(R.string.command_detail_debug_music_widget_802f6)).append(MusicSettings.showWidget()).append(Tuils.NEWLINE)
            output.append(context.getString(R.string.command_detail_debug_music_widget_auto_show_b8ed4)).append(MusicSettings.autoShowWidget()).append(Tuils.NEWLINE)
            output.append(context.getString(R.string.command_detail_debug_music_preferred_package_477d5)).append(MusicSettings.preferredPackage()).append(Tuils.NEWLINE)
            output.append(Tuils.NEWLINE)
            output.append(context.getString(R.string.command_detail_debug_effective_colors_f4064)).append(Tuils.NEWLINE)
            appendValue(output, Theme.background_color)
            appendValue(output, Theme.wallpaper_overlay_color)
            appendValue(output, Theme.input_text_color)
            appendValue(output, Theme.output_text_color)
            appendValue(output, Theme.apps_drawer_text_color)
            appendValue(output, Theme.terminal_window_background_color)
            appendValue(output, Theme.terminal_header_background_color)
            appendValue(output, Theme.terminal_header_border_color)
            appendValue(output, Theme.terminal_border_color)
            appendValue(output, Theme.module_text_color)
            appendValue(output, Suggestions.apps_background_color)
            appendValue(output, Suggestions.alias_background_color)
            appendValue(output, Suggestions.cmd_background_color)
            appendValue(output, Suggestions.contact_background_color)
            return output
        }

        private fun appendValue(output: StringBuilder, value: XMLPrefsSave) {
            output.append(value.label())
                .append(": ")
                .append(LauncherSettings.getEffective(value))
                .append(Tuils.NEWLINE)
        }
    }
}
