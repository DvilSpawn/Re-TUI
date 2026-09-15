package ohi.andre.consolelauncher.commands.main.raw

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.ContactsContract
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import ohi.andre.consolelauncher.LauncherActivity
import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.commands.CommandAbstraction
import ohi.andre.consolelauncher.commands.ExecutePack
import ohi.andre.consolelauncher.commands.main.MainPack
import ohi.andre.consolelauncher.commands.main.specific.ParamCommand
import ohi.andre.consolelauncher.managers.ContactManager
import ohi.andre.consolelauncher.tuils.Tuils
import java.util.Locale

/**
 * Created by francescoandreuzzi on 11/05/2017.
 */
open class cntcts : ParamCommand() {
    private enum class Param : ohi.andre.consolelauncher.commands.main.Param {
        ls {
            override fun exec(pack: ExecutePack): String? {
                val list = (pack as MainPack).contacts.listNamesAndNumbers()
                Tuils.insertHeaders(list, false)
                return Tuils.toPlanString(list)
            }

            override fun args(): IntArray? {
                return IntArray(0)
            }
        },
        add {
            override fun exec(pack: ExecutePack): String? {
                val intent = Intent(ContactsContract.Intents.Insert.ACTION)
                intent.setType(ContactsContract.RawContacts.CONTENT_TYPE)
                pack.context.startActivity(intent)

                return null
            }

            override fun args(): IntArray? {
                return IntArray(0)
            }
        },
        rm {
            override fun exec(pack: ExecutePack): String? {
                if (ContextCompat.checkSelfPermission(
                        pack.context,
                        Manifest.permission.WRITE_CONTACTS
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    ActivityCompat.requestPermissions(
                        pack.context as Activity, arrayOf<String>(
                            Manifest.permission.WRITE_CONTACTS
                        ), LauncherActivity.COMMAND_REQUEST_PERMISSION
                    )
                    return pack.context.getString(R.string.output_waitingpermission)
                }

                (pack as MainPack).contacts.delete(pack.getString())
                return null
            }

            override fun args(): IntArray? {
                return intArrayOf(CommandAbstraction.CONTACTNUMBER)
            }

            override fun onArgNotFound(pack: ExecutePack, index: Int): String? {
                return pack.context.getString(R.string.output_numbernotfound)
            }
        },
        edit {
            override fun exec(pack: ExecutePack): String? {
                val editIntent = Intent(Intent.ACTION_EDIT)
                editIntent.setDataAndType(
                    (pack as MainPack).contacts.fromPhone(pack.getString()),
                    ContactsContract.Contacts.CONTENT_ITEM_TYPE
                )
                pack.context.startActivity(editIntent)

                return null
            }

            override fun args(): IntArray? {
                return intArrayOf(CommandAbstraction.CONTACTNUMBER)
            }

            override fun onArgNotFound(pack: ExecutePack, index: Int): String? {
                return pack.context.getString(R.string.output_numbernotfound)
            }
        },
        l {
            override fun exec(pack: ExecutePack): String? {
                val about = (pack as MainPack).contacts.about(pack.getString())
                val builder = StringBuilder()

                val values = about ?: return pack.context.getString(R.string.output_numbernotfound)

                builder.append(values[ContactManager.NAME]).append(Tuils.NEWLINE)
                builder.append("\t\t").append(
                    values[ContactManager.NUMBERS]!!.replace(
                        Tuils.NEWLINE.toRegex(),
                        Tuils.NEWLINE + "\t\t"
                    )
                ).append(Tuils.NEWLINE)
                builder.append(pack.context.getString(R.string.command_cntcts_id_f63a6)).append(values[ContactManager.CONTACT_ID])
                    .append(Tuils.NEWLINE)
                val contacted = values[ContactManager.TIME_CONTACTED]?.toIntOrNull() ?: 0
                builder.append(pack.context.resources.getQuantityString(R.plurals.contact_times, contacted, contacted)).append(Tuils.NEWLINE)

                return builder.toString()
            }

            override fun args(): IntArray? {
                return intArrayOf(CommandAbstraction.CONTACTNUMBER)
            }

            override fun onArgNotFound(pack: ExecutePack, index: Int): String? {
                return pack.context.getString(R.string.output_numbernotfound)
            }
        };

        override fun label(): String? {
            return Tuils.MINUS + name
        }

        override fun onArgNotFound(pack: ExecutePack, index: Int): String? {
            return null
        }

        override fun onNotArgEnough(pack: ExecutePack, n: Int): String? {
            return pack.context.getString(R.string.help_cntcts)
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

    public override fun params(): Array<String?> {
        return Param.Companion.labels()
    }

    override fun doThings(pack: ExecutePack): String? {
        if (ContextCompat.checkSelfPermission(
                pack.context,
                Manifest.permission.READ_CONTACTS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                pack.context as Activity,
                arrayOf<String>(Manifest.permission.READ_CONTACTS),
                LauncherActivity.COMMAND_REQUEST_PERMISSION
            )
            return pack.context.getString(R.string.output_waitingpermission)
        }
        return null
    }

    override fun priority(): Int {
        return 3
    }

    override fun helpRes(): Int {
        return R.string.help_cntcts
    }
}
