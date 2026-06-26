package ohi.andre.consolelauncher.commands.main.raw

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Build
import android.provider.Settings
import java.util.Locale
import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.commands.CommandAbstraction
import ohi.andre.consolelauncher.commands.ExecutePack
import ohi.andre.consolelauncher.commands.main.MainPack
import ohi.andre.consolelauncher.commands.main.specific.ParamCommand
import ohi.andre.consolelauncher.tuils.Tuils

class volume : ParamCommand() {
    private enum class Param : ohi.andre.consolelauncher.commands.main.Param {
        set {
            override fun args(): IntArray = intArrayOf(CommandAbstraction.TEXTLIST)

            override fun exec(pack: ExecutePack): String? {
                if (!ensureNotificationPolicyAccess(pack)) {
                    return pack.context.getString(R.string.output_waitingpermission)
                }

                val args = pack.getList<String>()
                if (args.size < 2) {
                    return pack.context.getString(R.string.help_volume)
                }

                val type = parseStream(args.dropLast(1).joinToString(Tuils.SPACE))
                    ?: return pack.context.getString(R.string.help_volume)
                val volume = args.last().toIntOrNull()
                    ?: return pack.context.getString(R.string.invalid_integer)

                val manager = pack.context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                val minIndex = streamMinVolume(manager, type)
                val maxIndex = manager.getStreamMaxVolume(type)
                if (volume < minIndex || volume > maxIndex) {
                    return STREAM_LABELS[type] + " can only be " + minIndex + "-" + maxIndex + "."
                }

                manager.setStreamVolume(type, volume, 0)

                return streamInfo(manager, type)
            }
        },
        profile {
            override fun args(): IntArray = intArrayOf(CommandAbstraction.INT)

            override fun exec(pack: ExecutePack): String? {
                if (!ensureNotificationPolicyAccess(pack)) {
                    return pack.context.getString(R.string.output_waitingpermission)
                }

                val manager = pack.context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                manager.ringerMode = pack.getInt()

                return null
            }
        },
        get {
            override fun args(): IntArray = intArrayOf(CommandAbstraction.TEXTLIST)

            override fun exec(pack: ExecutePack): String {
                val manager = pack.context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

                val stream = parseStream(pack.getList<String>().joinToString(Tuils.SPACE))
                    ?: return pack.context.getString(R.string.help_volume)

                val builder = StringBuilder()
                builder.append(streamInfo(manager, stream))

                return builder.toString().trim()
            }

            override fun onNotArgEnough(pack: ExecutePack, n: Int): String {
                val manager = pack.context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

                val builder = StringBuilder()
                for (c in STREAM_LABELS.indices) {
                    builder.append(streamInfo(manager, c)).append(Tuils.NEWLINE)
                }

                return builder.toString().trim()
            }
        };

        override fun label(): String = Tuils.MINUS + name

        override fun onNotArgEnough(pack: ExecutePack, n: Int): String =
            pack.context.getString(R.string.help_volume)

        override fun onArgNotFound(pack: ExecutePack, index: Int): String =
            pack.context.getString(R.string.invalid_integer)

        companion object {
            private val STREAM_LABELS = arrayOf("Voice call", "System", "Ring", "Media", "Alarm", "Notification")

            private fun ensureNotificationPolicyAccess(pack: ExecutePack): Boolean {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    val notificationManager = pack.context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    if (!notificationManager.isNotificationPolicyAccessGranted) {
                        val intent = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
                        pack.context.startActivity(intent)
                        return false
                    }
                }
                return true
            }

            fun get(p: String): Param? {
                val value = p.lowercase(Locale.getDefault())
                for (p1 in entries) {
                    if (value.endsWith(p1.label())) return p1
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

            private fun parseStream(value: String): Int? {
                val normalized = value.trim().lowercase(Locale.getDefault()).replace('-', ' ')
                    .replace('_', ' ')
                val number = normalized.toIntOrNull()
                if (number != null && number in 0..5) {
                    return number
                }

                return when (normalized) {
                    "voice", "voice call" -> 0
                    "system" -> 1
                    "ring", "ringer" -> 2
                    "media", "music" -> 3
                    "alarm" -> 4
                    "notification", "notifications" -> 5
                    else -> null
                }
            }

            private fun streamInfo(manager: AudioManager, stream: Int): String {
                val current = manager.getStreamVolume(stream)
                val max = manager.getStreamMaxVolume(stream)
                return STREAM_LABELS[stream] + ":" + Tuils.SPACE + current + "/" + max
            }

            private fun streamMinVolume(manager: AudioManager, stream: Int): Int {
                return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    manager.getStreamMinVolume(stream)
                } else {
                    0
                }
            }

        }
    }

    override fun params(): Array<String> = Param.labels()

    override fun paramForString(pack: MainPack, param: String): ohi.andre.consolelauncher.commands.main.Param? =
        Param.get(param)

    override fun priority(): Int = 3

    override fun helpRes(): Int = R.string.help_volume

    override fun doThings(pack: ExecutePack): String? = null
}
