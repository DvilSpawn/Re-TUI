package ohi.andre.consolelauncher.commands.main.raw

import java.util.Locale
import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.LauncherActivity
import ohi.andre.consolelauncher.commands.CommandAbstraction
import ohi.andre.consolelauncher.commands.ExecutePack
import ohi.andre.consolelauncher.commands.main.MainPack
import ohi.andre.consolelauncher.commands.main.specific.ParamCommand
import ohi.andre.consolelauncher.commands.tuixt.TuixtDialog
import ohi.andre.consolelauncher.managers.preset.PresetManager
import ohi.andre.consolelauncher.managers.preset.PresetWallpaper
import ohi.andre.consolelauncher.managers.preset.PresetWallpaperPickerActivity
import ohi.andre.consolelauncher.tuils.Tuils
import ohi.andre.consolelauncher.tuils.interfaces.Reloadable

class preset : ParamCommand() {
    private enum class Param : ohi.andre.consolelauncher.commands.main.Param {
        save {
            override fun exec(pack: ExecutePack): String {
                val name = pack.getString()!!
                val context = pack.context
                // First, what the preset should hold: everything is ticked, untick what it should leave alone.
                val parts = LinkedHashSet(PresetManager.Part.entries)
                val list = android.widget.LinearLayout(context).apply {
                    orientation = android.widget.LinearLayout.VERTICAL
                    addView(android.widget.TextView(context).apply {
                        text = context.getString(R.string.command_preset_parts_note)
                        typeface = Tuils.getTypeface(context); textSize = 12f
                        setTextColor(ohi.andre.consolelauncher.commands.tuixt.TuixtTheme.textColor())
                        setPadding(0, 0, 0, Tuils.dpToPx(context, 8))
                    })
                    PresetManager.Part.entries.forEach { part ->
                        addView(android.widget.CheckBox(context).apply {
                            text = context.getString(Parts.partLabel(part))
                            isChecked = true
                            typeface = Tuils.getTypeface(context); textSize = 13f
                            setTextColor(ohi.andre.consolelauncher.commands.tuixt.TuixtTheme.textColor())
                            buttonTintList = android.content.res.ColorStateList.valueOf(ohi.andre.consolelauncher.commands.tuixt.TuixtTheme.accentColor())
                            minHeight = Tuils.dpToPx(context, 44)
                            setOnCheckedChangeListener { _, on -> if (on) parts.add(part) else parts.remove(part) }
                        })
                    }
                }
                TuixtDialog.showContent(context, context.getString(R.string.command_preset_parts_title, name.trim()), list,
                    context.getString(R.string.command_preset_save), context.getString(R.string.command_preset_cancel_77dfd),
                    TuixtDialog.ConfirmAction {
                        val message = try {
                            if (PresetManager.save(context, name, parts) == PresetWallpaper.Saved.UNKNOWN)
                                PresetWallpaperPickerActivity.ask(context, name.trim())
                            context.getString(R.string.command_preset_saved_preset_4a339, name.trim())
                        } catch (e: Exception) {
                            e.message ?: context.getString(R.string.output_error)
                        }
                        if (context is Reloadable) context.addMessage("preset", message)
                    })
                return context.getString(R.string.command_preset_choose_parts, name.trim())
            }

            override fun args(): IntArray = intArrayOf(CommandAbstraction.PRESET_NAME)
        },
        apply {
            override fun exec(pack: ExecutePack): String {
                val name = pack.getString()!!
                try {
                    PresetManager.apply(name)
                    runCatching { PresetManager.applyWallpaper(pack.context, name) }

                    if (pack.context is Reloadable) {
                        (pack.context as Reloadable).addMessage("preset", pack.context.getString(R.string.command_preset_applied_preset_c132b, name.trim()))
                    }

                    return pack.context.getString(R.string.command_preset_preset_applied_0611f, name.trim())
                } catch (e: IllegalArgumentException) {
                    return e.message!!
                } catch (e: Exception) {
                    return e.message ?: pack.context.getString(R.string.output_error)
                }
            }

            override fun args(): IntArray = intArrayOf(CommandAbstraction.PRESET_NAME)
        },
        duplicate {
            override fun exec(pack: ExecutePack): String {
                val sourceName = pack.getString()!!
                TuixtDialog.showInput(
                    pack.context,
                    pack.context.getString(R.string.command_preset_duplicate_preset_76c16),
                    pack.context.getString(R.string.command_preset_new_preset_name_0ee29),
                    pack.context.getString(R.string.command_preset_duplicate_972d5),
                    pack.context.getString(R.string.command_preset_cancel_77dfd),
                    TuixtDialog.InputAction { value ->
                        val message = try {
                            val duplicated = PresetManager.duplicate(sourceName, value.orEmpty())
                            pack.context.getString(R.string.command_preset_duplicated_as_0b74a, sourceName, duplicated)
                        } catch (e: Exception) {
                            e.message ?: pack.context.getString(R.string.output_error)
                        }
                        if (pack.context is Reloadable) {
                            (pack.context as Reloadable).addMessage("preset", message)
                        }
                    }
                )
                return pack.context.getString(R.string.command_preset_enter_a_name_for_the_duplicate_e815f)
            }

            override fun args(): IntArray = intArrayOf(CommandAbstraction.SAVED_PRESET_NAME)
        },
        ls {
            override fun exec(pack: ExecutePack): String {
                val list = PresetManager.listAllPresetNames()
                if (list.isEmpty()) return pack.context.getString(R.string.command_preset_no_presets_found_a6efc)
                return Tuils.toPlanString(list, "\n")
            }

            override fun args(): IntArray = IntArray(0)
        },
        market {
            override fun exec(pack: ExecutePack): String? {
                pack.context.startActivity(android.content.Intent(pack.context,
                    ohi.andre.consolelauncher.commands.tuixt.PresetMarketplaceActivity::class.java)
                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
                return null
            }

            override fun args(): IntArray = IntArray(0)
        },
        rm {
            override fun exec(pack: ExecutePack): String {
                val name = pack.getString()!!
                return try {
                    PresetManager.remove(name)
                    pack.context.getString(R.string.command_preset_preset_removed_0fa34, name.trim())
                } catch (e: Exception) {
                    e.message ?: pack.context.getString(R.string.output_error)
                }
            }

            override fun args(): IntArray = intArrayOf(CommandAbstraction.PRESET_NAME)
        };

        override fun label(): String = Tuils.MINUS + name.replace("_", "")

        override fun onNotArgEnough(pack: ExecutePack, n: Int): String =
            pack.context.getString(R.string.help_preset)

        override fun onArgNotFound(pack: ExecutePack, index: Int): String =
            pack.context.getString(R.string.help_preset)

        companion object {
            fun get(p: String): Param? {
                val value = p.lowercase(Locale.ROOT)
                val ps = entries
                for (p1 in ps) {
                    if (value.endsWith(p1.label())) {
                        return p1
                    }
                }
                return null
            }

            fun labels(): Array<String> {
                val ps = entries
                val ss = Array(ps.size) { "" }
                for (count in ps.indices) {
                    ss[count] = ps[count].label()
                }
                return ss
            }
        }
    }

    override fun params(): Array<String> = Param.labels()

    private object Parts {
        fun partLabel(part: PresetManager.Part): Int = when (part) {
            PresetManager.Part.COLOURS -> R.string.command_preset_part_colours
            PresetManager.Part.LOOK -> R.string.command_preset_part_look
            PresetManager.Part.MODE -> R.string.command_preset_part_mode
            PresetManager.Part.MONO -> R.string.command_preset_part_mono
            PresetManager.Part.BEHAVIOUR -> R.string.command_preset_part_behaviour
            PresetManager.Part.WALLPAPER -> R.string.command_preset_part_wallpaper
        }
    }

    override fun paramForString(pack: MainPack, param: String): ohi.andre.consolelauncher.commands.main.Param? =
        Param.get(param)

    override fun priority(): Int = 4

    override fun helpRes(): Int = R.string.help_preset

    override fun doThings(pack: ExecutePack): String? = null

    companion object {
        internal const val MARKETPLACE_URL = "https://re-tui.pages.dev/marketplace"
    }
}
