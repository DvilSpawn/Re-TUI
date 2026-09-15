package ohi.andre.consolelauncher.commands.main.raw

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import ohi.andre.consolelauncher.BuildConfig
import ohi.andre.consolelauncher.LauncherActivity
import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.UIManager
import ohi.andre.consolelauncher.commands.CommandAbstraction
import ohi.andre.consolelauncher.commands.CommandsPreferences
import ohi.andre.consolelauncher.commands.ExecutePack
import ohi.andre.consolelauncher.commands.main.MainPack
import ohi.andre.consolelauncher.commands.main.specific.ParamCommand
import ohi.andre.consolelauncher.managers.xml.XMLPrefsManager
import ohi.andre.consolelauncher.tuils.Tuils
import ohi.andre.consolelauncher.tuils.interfaces.Reloadable
import ohi.andre.consolelauncher.tuils.stuff.PolicyReceiver
import java.io.File
import java.util.Locale

/**
 * Created by francescoandreuzzi on 10/06/2017.
 */
class tui : ParamCommand() {
    private enum class Param : ohi.andre.consolelauncher.commands.main.Param {
        rm {
            override fun exec(pack: ExecutePack): String? {
                val info: MainPack = pack as MainPack

                val policy: DevicePolicyManager =
                    info.context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
                val name: ComponentName = ComponentName(info.context, PolicyReceiver::class.java)
                policy.removeActiveAdmin(name)

                val packageURI = Uri.fromParts("package", BuildConfig.APPLICATION_ID, null)
                val uninstallIntent: Intent = Intent(Intent.ACTION_DELETE, packageURI)
                uninstallIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                info.context.startActivity(uninstallIntent)

                return null
            }
        },
        about {
            override fun exec(pack: ExecutePack): String? {
                val info: MainPack = pack as MainPack
                return pack.context.getString(R.string.command_tui_version_code_a8b49, Tuils.SPACE, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE, (if (BuildConfig.DEBUG) Tuils.NEWLINE + BuildConfig.BUILD_TYPE else Tuils.EMPTYSTRING), Tuils.NEWLINE, Tuils.NEWLINE, info.res.getString(R.string.output_about))
            }
        },
        log {
            override fun args(): IntArray? {
                return intArrayOf(CommandAbstraction.PLAIN_TEXT)
            }

            override fun exec(pack: ExecutePack): String? {
                val i: Intent = Intent(UIManager.ACTION_LOGTOFILE)
                i.putExtra(UIManager.FILE_NAME, pack.getString())
                LocalBroadcastManager.getInstance(pack.context.getApplicationContext())
                    .sendBroadcast(i)

                return null
            }

            override fun onNotArgEnough(pack: ExecutePack, n: Int): String? {
                return pack.context.getString(R.string.help_tui)
            }
        },
        priority {
            override fun args(): IntArray? {
                return intArrayOf(CommandAbstraction.COMMAND, CommandAbstraction.INT)
            }

            override fun exec(pack: ExecutePack): String? {
                val file: File = File(Tuils.getFolder(), "cmd.xml")
                return XMLPrefsManager.set(
                    file,
                    pack.get()!!.javaClass.getSimpleName() + CommandsPreferences.PRIORITY_SUFFIX,
                    arrayOf<String>(XMLPrefsManager.VALUE_ATTRIBUTE),
                    arrayOf<String>(pack.getInt().toString())
                )
            }

            override fun onNotArgEnough(pack: ExecutePack, n: Int): String? {
                return pack.context.getString(R.string.help_tui)
            }

            override fun onArgNotFound(pack: ExecutePack, index: Int): String? {
                return pack.context.getString(R.string.output_invalidarg)
            }
        },
        sourcecode {
            override fun exec(pack: ExecutePack): String? {
                pack.context.startActivity(Tuils.webPage("https://github.com/DvilSpawn/Re-TUI"))
                return null
            }
        },
        reset {
            override fun exec(pack: ExecutePack): String? {
                Tuils.deleteContentOnly(Tuils.getFolder())

                (pack.context as LauncherActivity).addMessage(
                    pack.context.getString(R.string.tui_reset),
                    null
                )
                (pack.context as Reloadable).reload()
                return null
            }
        },
        folder {
            override fun exec(pack: ExecutePack): String? {
                val selectedUri = Uri.parse(Tuils.getFolder().getAbsolutePath())
                val intent: Intent = Intent(Intent.ACTION_VIEW)
                intent.setDataAndType(selectedUri, "resource/folder")

                if (intent.resolveActivityInfo(pack.context.getPackageManager(), 0) != null) {
                    pack.context.startActivity(intent)
                } else {
                    return Tuils.getFolder().getAbsolutePath()
                }

                return null
            }
        }; //        ,
        //        exclude_message {
        //            @Override
        //            public int[] args() {
        //                return new int[] {CommandAbstraction.INT};
        //            }
        //
        //            @Override
        //            public String exec(ExecutePack pack) {
        //                return null;
        //            }
        //
        //            @Override
        //            public String onNotArgEnough(ExecutePack pack, int n) {
        //
        //            }
        //        }

        override fun args(): IntArray? {
            return IntArray(0)
        }

        override fun label(): String? {
            return Tuils.MINUS + name
        }

        override fun onArgNotFound(pack: ExecutePack, index: Int): String? {
            return null
        }

        override fun onNotArgEnough(pack: ExecutePack, n: Int): String? {
            return null
        }

        companion object {
            fun get(p: String): Param? {
                var p = p
                p = p.lowercase(Locale.ROOT)
                val ps = entries.toTypedArray()
                for (p1 in ps) if (p.endsWith(p1.label()!!)) return p1
                return null
            }

            fun labels(): Array<String?> {
                val ps = entries.toTypedArray()
                val ss = arrayOfNulls<String>(ps.size)

                for (count in ps.indices) {
                    ss[count] = ps[count].label()
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
        return null
    }

    public override fun params(): Array<String?> {
        return Param.Companion.labels()
    }

    override fun priority(): Int {
        return 4
    }

    override fun helpRes(): Int {
        return R.string.help_tui
    }
}
