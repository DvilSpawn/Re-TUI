package ohi.andre.consolelauncher.commands.main.raw

import android.Manifest
import android.app.Activity
import android.content.Intent
import androidx.core.app.ActivityCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import java.util.Locale
import ohi.andre.consolelauncher.LauncherActivity
import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.UIManager
import ohi.andre.consolelauncher.commands.CommandAbstraction
import ohi.andre.consolelauncher.commands.ExecutePack
import ohi.andre.consolelauncher.managers.modules.ModuleManager
import ohi.andre.consolelauncher.managers.modules.UpcomingEventsManager
import ohi.andre.consolelauncher.managers.settings.LauncherSettings
import ohi.andre.consolelauncher.managers.xml.options.Behavior

class events : CommandAbstraction {
    override fun exec(pack: ExecutePack): String {
        val arg = pack.get(Any::class.java, 0)
        val input = arg?.toString()?.trim()?.lowercase(Locale.ROOT) ?: ""

        if (input == "-access" || input == "access") {
            if (UpcomingEventsManager.hasCalendarPermission(pack.context)) {
                return pack.context.getString(R.string.command_events_calendar_access_already_granted_e615f)
            }
            if (pack.context is Activity) {
                ActivityCompat.requestPermissions(
                    pack.context as Activity,
                    arrayOf(Manifest.permission.READ_CALENDAR),
                    LauncherActivity.COMMAND_REQUEST_PERMISSION
                )
                return pack.context.getString(R.string.command_events_calendar_access_requested_62085)
            }
            return pack.context.getString(R.string.command_events_calendar_access_must_be_granted_from_andro_48275)
        }

        val parts = input.split("\\s+".toRegex()).toTypedArray()
        if (parts.isNotEmpty() && (parts[0] == "-lookahead" || parts[0] == "lookahead")) {
            if (parts.size < 2 || parts[1].isEmpty()) {
                return pack.context.getString(R.string.command_events_events_lookahead_days_after_today_usage_ev_98fd1, UpcomingEventsManager.lookaheadDays)
            }

            var days: Int
            try {
                days = parts[1].toInt()
            } catch (e: Exception) {
                return pack.context.getString(R.string.command_events_invalid_lookahead_7cbc8, parts[1])
            }
            days = UpcomingEventsManager.sanitizeLookaheadDays(days)
            LauncherSettings.set(pack.context, Behavior.events_lookahead_days, days.toString())
            refreshLauncherEventsIfKnown(pack)
            if (days == 0) {
                return pack.context.getString(R.string.command_events_launcher_events_lookahead_set_today_only_9b01c)
            }
            return pack.context.getString(R.string.command_events_launcher_events_lookahead_set_today_days_8cdfd, days)
        }

        if (input == "-module" || input == "module" || input == "-print") {
            return exampleScript()
        }

        if (input == "-install" || input == "-add" || input == "install") {
            return pack.context.getString(R.string.command_events_events_is_an_editable_termux_module_now_cr_e09d6)
        }

        if (!ModuleManager.isKnown(pack.context, ModuleManager.EVENTS)) {
            return pack.context.getString(R.string.command_events_events_is_an_editable_termux_module_run_ev_734cb)
        }
        send(pack, "show")
        return pack.context.getString(R.string.command_events_module_opened_events_4a38b)
    }

    private fun refreshLauncherEventsIfKnown(pack: ExecutePack) {
        if (!ModuleManager.isKnown(pack.context, ModuleManager.EVENTS)) {
            return
        }
        val source = ModuleManager.getModuleSource(pack.context, ModuleManager.EVENTS)
        if (ModuleManager.isLauncherSource(source)) {
            send(pack, "refresh")
        }
    }

    private fun send(pack: ExecutePack, command: String) {
        val intent = Intent(UIManager.ACTION_MODULE_COMMAND)
        intent.putExtra(UIManager.EXTRA_MODULE_COMMAND, command)
        intent.putExtra(UIManager.EXTRA_MODULE_NAME, ModuleManager.EVENTS)
        LocalBroadcastManager.getInstance(pack.context.applicationContext).sendBroadcast(intent)
    }

    private fun exampleScript(): String =
        "#!/data/data/com.termux/files/usr/bin/sh\n" +
            "\n" +
            "echo \"::title Events\"\n" +
            "\n" +
            "EVENTS_FILE=\"%RETUI_CALENDAR_UPCOMING_MONTH\"\n" +
            "if [ ! -s \"\$EVENTS_FILE\" ]; then\n" +
            "  echo \"::body No upcoming events this month.\"\n" +
            "else\n" +
            "  while IFS='\t' read -r date time title location; do\n" +
            "    if [ -n \"\$time\" ]; then\n" +
            "      line=\"\$date \$time - \$title\"\n" +
            "    else\n" +
            "      line=\"\$date - \$title\"\n" +
            "    fi\n" +
            "    [ -n \"\$location\" ] && line=\"\$line @ \$location\"\n" +
            "    echo \"::body \$line\"\n" +
            "  done < \"\$EVENTS_FILE\"\n" +
            "fi\n" +
            "\n" +
            "echo \"::suggest refresh | command | module -refresh events\"\n" +
            "echo \"::suggest access | command | events -access\"\n" +
            "echo \"::suggest calendar | command | intent -view content://com.android.calendar/time/%RETUI_NOW\""

    override fun argType(): IntArray = intArrayOf(CommandAbstraction.PLAIN_TEXT)

    override fun priority(): Int = 4

    override fun helpRes(): Int = R.string.help_events

    override fun onArgNotFound(info: ExecutePack, indexNotFound: Int): String =
        info.context.getString(R.string.help_events)

    override fun onNotArgEnough(info: ExecutePack, nArgs: Int): String =
        info.context.getString(R.string.help_events)
}
