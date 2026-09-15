package ohi.andre.consolelauncher.commands.main.raw

import android.app.WallpaperManager
import android.content.Intent
import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.LauncherActivity
import ohi.andre.consolelauncher.commands.ExecutePack
import ohi.andre.consolelauncher.commands.main.MainPack
import ohi.andre.consolelauncher.commands.main.specific.ParamCommand
import ohi.andre.consolelauncher.commands.main.specific.RedirectCommand
import ohi.andre.consolelauncher.managers.settings.LauncherSettings.enableWallpaperAuto
import ohi.andre.consolelauncher.tuils.Tuils
import ohi.andre.consolelauncher.tuils.interfaces.Reloadable
import java.util.Locale

class wallpaper : ParamCommand() {
    private enum class Param : ohi.andre.consolelauncher.commands.main.Param {
        static_wallpaper {
            override fun exec(pack: ExecutePack): String {
                return openStaticWallpaperPicker(pack)
            }

            override fun label(): String? {
                return Tuils.MINUS + "static"
            }
        },
        live {
            override fun exec(pack: ExecutePack): String {
                try {
                    pack.context.startActivity(Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER))
                    return Tuils.EMPTYSTRING
                } catch (e: Exception) {
                    return pack.context.getString(R.string.output_error)
                }
            }
        },
        auto {
            override fun exec(pack: ExecutePack): String? {
                if (pack is MainPack) {
                    pack.redirectator!!.prepareRedirection(WallpaperAutoConfirmation())
                    return pack.context.getString(R.string.command_wallpaper_please_confirm_if_you_have_saved_your_pres_87e90)
                }
                return enableWallpaperAuto(pack)
            }
        };

        override fun args(): IntArray? {
            return IntArray(0)
        }

        override fun label(): String? {
            return Tuils.MINUS + name
        }

        fun matches(value: String?): Boolean {
            if (value == null) {
                return false
            }

            val label = label()!!.lowercase(Locale.ROOT)
            if (value == label) {
                return true
            }

            if (label.startsWith(Tuils.MINUS)) {
                return value == label.substring(1)
            }

            return false
        }

        override fun onNotArgEnough(pack: ExecutePack, n: Int): String {
            return pack.context.getString(R.string.help_wallpaper)
        }

        override fun onArgNotFound(pack: ExecutePack, index: Int): String {
            return pack.context.getString(R.string.help_wallpaper)
        }

        companion object {
            fun get(p: String): Param? {
                var p = p
                p = p.lowercase(Locale.ROOT)
                for (param in entries) {
                    if (param.matches(p)) {
                        return param
                    }
                }
                return null
            }

            fun labels(): Array<String?> {
                val ps: Array<Param?> = entries.toTypedArray()
                val ss = arrayOfNulls<String>(ps.size)
                for (count in ps.indices) {
                    ss[count] = ps[count]!!.label()
                }
                return ss
            }
        }
    }

    override fun paramForString(
        pack: MainPack,
        param: String
    ): ohi.andre.consolelauncher.commands.main.Param? {
        return Param.Companion.get(param)
    }

    override fun doThings(pack: ExecutePack): String? {
        if (pack.get(
                ohi.andre.consolelauncher.commands.main.Param::class.java,
                0
            ) != null
        ) {
            return null
        }
        return openStaticWallpaperPicker(pack)
    }

    private class WallpaperAutoConfirmation : RedirectCommand() {
        public override fun onRedirect(pack: ExecutePack): String? {
            val mainPack = pack as MainPack
            var answer = Tuils.EMPTYSTRING
            if (!afterObjects.isEmpty() && afterObjects.get(0) != null) {
                answer = afterObjects.get(0).toString().trim { it <= ' ' }
            }

            if ("yes".equals(answer, ignoreCase = true) || "y".equals(answer, ignoreCase = true)) {
                mainPack.redirectator!!.cleanup()
                markCompletedCommand("wallpaper -auto")
                return enableWallpaperAuto(pack)
            }
            if ("no".equals(answer, ignoreCase = true) || "n".equals(answer, ignoreCase = true)) {
                mainPack.redirectator!!.cleanup()
                return pack.context.getString(R.string.command_wallpaper_wallpaper_auto_cancelled_4d625)
            }

            afterObjects.clear()
            return pack.context.getString(R.string.command_wallpaper_please_answer_yes_or_no_867a9)
        }

        public override fun getHint(): Int {
            return R.string.hint_wallpaper_auto_confirm
        }

        public override fun isWaitingPermission(): Boolean {
            return false
        }

        override fun argType(): IntArray {
            return IntArray(0)
        }

        override fun priority(): Int {
            return 0
        }

        override fun helpRes(): Int {
            return R.string.help_wallpaper
        }

        override fun onArgNotFound(pack: ExecutePack, indexNotFound: Int): String? {
            return null
        }

        override fun onNotArgEnough(pack: ExecutePack, nArgs: Int): String? {
            return null
        }

        override fun exec(pack: ExecutePack): String? {
            return null
        }
    }

    public override fun params(): Array<String?> {
        return Param.Companion.labels()
    }

    override fun priority(): Int {
        return 3
    }

    override fun helpRes(): Int {
        return R.string.help_wallpaper
    }

    companion object {
        private fun openStaticWallpaperPicker(pack: ExecutePack): String {
            try {
                pack.context.startActivity(
                    Intent.createChooser(
                        Intent(Intent.ACTION_SET_WALLPAPER),
                        pack.context.getString(R.string.app_name)
                    )
                )
                return Tuils.EMPTYSTRING
            } catch (e: Exception) {
                return pack.context.getString(R.string.output_error)
            }
        }

        private fun enableWallpaperAuto(pack: ExecutePack): String {
            if (!enableWallpaperAuto(pack.context)) {
                return pack.context.getString(R.string.command_wallpaper_unable_to_read_wallpaper_colors_259c7)
            }

            if (pack.context is Reloadable) {
                (pack.context as Reloadable).addMessage(
                    "wallpaper",
                    pack.context.getString(R.string.command_wallpaper_applied_wallpaper_colors_once_387a7)
                )
                LauncherActivity.preview(pack.context)
                return Tuils.EMPTYSTRING
            }

            return pack.context.getString(R.string.command_wallpaper_wallpaper_colors_applied_804d5)
        }
    }
}
