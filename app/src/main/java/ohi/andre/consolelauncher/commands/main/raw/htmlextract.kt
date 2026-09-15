package ohi.andre.consolelauncher.commands.main.raw

import android.content.Intent
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.commands.CommandAbstraction
import ohi.andre.consolelauncher.commands.ExecutePack
import ohi.andre.consolelauncher.commands.main.MainPack
import ohi.andre.consolelauncher.commands.main.specific.ParamCommand
import ohi.andre.consolelauncher.managers.HTMLExtractManager
import ohi.andre.consolelauncher.managers.xml.XMLPrefsManager
import ohi.andre.consolelauncher.tuils.Tuils
import java.io.File
import java.util.Locale

/**
 * Created by francescoandreuzzi on 29/03/2018.
 */
class htmlextract : ParamCommand() {
    private enum class Param : ohi.andre.consolelauncher.commands.main.Param {
        query {
            override fun args(): IntArray? {
                return intArrayOf(
                    CommandAbstraction.INT,
                    CommandAbstraction.INT,
                    CommandAbstraction.PLAIN_TEXT
                )
            }

            override fun exec(pack: ExecutePack): String? {
                val i = Intent(HTMLExtractManager.ACTION_QUERY)

                i.putExtra(HTMLExtractManager.ID, pack.getInt())
                i.putExtra(HTMLExtractManager.FORMAT_ID, pack.getInt())
                i.putExtra(XMLPrefsManager.VALUE_ATTRIBUTE, pack.getString())
                i.putExtra(HTMLExtractManager.BROADCAST_COUNT, HTMLExtractManager.broadcastCount)
                LocalBroadcastManager.getInstance(pack.context.getApplicationContext())
                    .sendBroadcast(i)

                return null
            }

            override fun onArgNotFound(pack: ExecutePack, index: Int): String? {
                if (index == 1) return pack.context.getString(R.string.invalid_integer)

                if (index == 2) {
//                    the user was trying to use the default format

//                    waste the first

                    pack.get()

                    val i = Intent(HTMLExtractManager.ACTION_QUERY)
                    i.putExtra(HTMLExtractManager.ID, pack.getInt())
                    i.putExtra(XMLPrefsManager.VALUE_ATTRIBUTE, pack.getString())
                    i.putExtra(
                        HTMLExtractManager.BROADCAST_COUNT,
                        HTMLExtractManager.broadcastCount
                    )
                    LocalBroadcastManager.getInstance(pack.context.getApplicationContext())
                        .sendBroadcast(i)

                    return null
                }

                return pack.context.getString(R.string.help_htmlextract)
            }
        },
        add {
            override fun args(): IntArray? {
                return intArrayOf(
                    CommandAbstraction.DATASTORE_PATH_TYPE,
                    CommandAbstraction.INT,
                    CommandAbstraction.PLAIN_TEXT
                )
            }

            override fun exec(pack: ExecutePack): String? {
                val i = Intent(HTMLExtractManager.ACTION_ADD)
                i.putExtra(HTMLExtractManager.TAG_NAME, pack.getString())
                i.putExtra(HTMLExtractManager.ID, pack.getInt())
                i.putExtra(XMLPrefsManager.VALUE_ATTRIBUTE, pack.getString())
                i.putExtra(HTMLExtractManager.BROADCAST_COUNT, HTMLExtractManager.broadcastCount)
                LocalBroadcastManager.getInstance(pack.context.getApplicationContext())
                    .sendBroadcast(i)

                return null
            }

            override fun onArgNotFound(pack: ExecutePack, index: Int): String? {
                if (index == 1) return pack.context.getString(R.string.invalid_datastoretype)
                return super.onArgNotFound(pack, index)
            }
        },
        rm {
            override fun args(): IntArray? {
                return intArrayOf(CommandAbstraction.INT)
            }

            override fun exec(pack: ExecutePack): String? {
                val i = Intent(HTMLExtractManager.ACTION_RM)
                i.putExtra(HTMLExtractManager.ID, pack.getInt())
                i.putExtra(HTMLExtractManager.BROADCAST_COUNT, HTMLExtractManager.broadcastCount)
                LocalBroadcastManager.getInstance(pack.context.getApplicationContext())
                    .sendBroadcast(i)

                return null
            }
        },
        edit {
            override fun args(): IntArray? {
                return intArrayOf(CommandAbstraction.INT, CommandAbstraction.PLAIN_TEXT)
            }

            override fun exec(pack: ExecutePack): String? {
                val i = Intent(HTMLExtractManager.ACTION_EDIT)
                i.putExtra(HTMLExtractManager.ID, pack.getInt())
                i.putExtra(HTMLExtractManager.BROADCAST_COUNT, HTMLExtractManager.broadcastCount)
                i.putExtra(XMLPrefsManager.VALUE_ATTRIBUTE, pack.getString())
                LocalBroadcastManager.getInstance(pack.context.getApplicationContext())
                    .sendBroadcast(i)

                return null
            }
        },
        ls {
            override fun args(): IntArray? {
                return intArrayOf(CommandAbstraction.DATASTORE_PATH_TYPE)
            }

            override fun exec(pack: ExecutePack): String? {
                val i = Intent(HTMLExtractManager.ACTION_LS)
                i.putExtra(HTMLExtractManager.BROADCAST_COUNT, HTMLExtractManager.broadcastCount)
                i.putExtra(HTMLExtractManager.TAG_NAME, pack.getString())
                LocalBroadcastManager.getInstance(pack.context.getApplicationContext())
                    .sendBroadcast(i)

                return null
            }

            override fun onNotArgEnough(pack: ExecutePack, n: Int): String? {
                val i = Intent(HTMLExtractManager.ACTION_LS)
                i.putExtra(HTMLExtractManager.BROADCAST_COUNT, HTMLExtractManager.broadcastCount)
                LocalBroadcastManager.getInstance(pack.context.getApplicationContext())
                    .sendBroadcast(i)

                return null
            }

            override fun onArgNotFound(pack: ExecutePack, index: Int): String? {
                return pack.context.getString(R.string.invalid_datastoretype)
            }
        },
        tutorial {
            override fun args(): IntArray? {
                return IntArray(0)
            }

            override fun exec(pack: ExecutePack): String? {
                pack.context.startActivity(Tuils.webPage("https://github.com/DvilSpawn/Re-TUI/wiki/HTMLExtract"))
                return null
            }
        },
        file {
            override fun args(): IntArray? {
                return IntArray(0)
            }

            override fun exec(pack: ExecutePack): String? {
                val file = File(Tuils.getFolder(), HTMLExtractManager.PATH)
                pack.context.startActivity(Tuils.openFile(pack.context, file))
                return null
            }
        };

        override fun label(): String? {
            return Tuils.MINUS + name
        }

        override fun onNotArgEnough(pack: ExecutePack, n: Int): String? {
            return pack.context.getString(R.string.help_htmlextract)
        }

        override fun onArgNotFound(pack: ExecutePack, index: Int): String? {
            return pack.context.getString(R.string.invalid_integer)
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

    public override fun params(): Array<String?> {
        return Param.Companion.labels()
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

    override fun priority(): Int {
        return 3
    }

    override fun helpRes(): Int {
        return R.string.help_htmlextract
    }
}
