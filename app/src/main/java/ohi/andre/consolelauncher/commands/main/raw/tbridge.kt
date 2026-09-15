package ohi.andre.consolelauncher.commands.main.raw

import java.util.Locale
import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.commands.CommandAbstraction
import ohi.andre.consolelauncher.commands.ExecutePack
import ohi.andre.consolelauncher.commands.main.MainPack
import ohi.andre.consolelauncher.managers.termux.TermuxBridgeManager
import ohi.andre.consolelauncher.tuils.Tuils

class tbridge : CommandAbstraction {
    override fun exec(pack: ExecutePack): String? {
        val info = pack as MainPack
        val input = info.getString()
        if (input.trim().isEmpty()) {
            return info.res.getString(helpRes())
        }

        val parts = input.trim().split("\\s+".toRegex(), limit = 2).toTypedArray()
        val option = parts[0].lowercase(Locale.ROOT)

        if (option == "-status" || option == "-doctor") {
            return localStatus(info)
        }

        if (option == "-setup") {
            return setupText(pack.context)
        }

        if (option == "-dirs" || option == "-files" || option == "-ls") {
            return retiredFileListingMessage(pack.context)
        }

        if (!ensureReady(info)) {
            return null
        }

        if (option == "-probe") {
            TermuxBridgeManager.dispatchShell(info.context, "probe", STATUS_SCRIPT, TermuxBridgeManager.TERMUX_HOME)
            return pack.context.getString(R.string.command_tbridge_termux_bridge_probe_dispatched_9c2fe)
        }

        return info.res.getString(helpRes())
    }

    private fun localStatus(info: MainPack): String {
        val status = TermuxBridgeManager.status(info.context)
        val builder = StringBuilder()
        builder.append(info.context.getString(R.string.command_tbridge_re_t_ui_termux_bridge_ca56e)).append('\n')
        builder.append(info.context.getString(R.string.command_tbridge_role_scripts_modules_callbacks_automation_d1885)).append('\n')
        builder.append(info.context.getString(R.string.command_tbridge_termux_f248d)).append(if (status.termuxInstalled) "installed" else "missing").append('\n')
        builder.append(info.context.getString(R.string.command_tbridge_run_command_declared_3cdca)).append(if (status.runCommandDeclared) "yes" else "no").append('\n')
        builder.append(info.context.getString(R.string.command_tbridge_run_command_granted_b7cc8)).append(if (status.runCommandGranted) "yes" else "no").append('\n')
        builder.append(info.context.getString(R.string.command_tbridge_files_use_the_files_command_re_t_ui_files_4f43d)).append('\n')
        builder.append(info.context.getString(R.string.command_tbridge_current_path_c14e7)).append(info.currentDirectory.absolutePath).append('\n')
        builder.append(info.context.getString(R.string.command_tbridge_probe_tbridge_probe_b2fb9)).append('\n')
        builder.append(info.context.getString(R.string.command_tbridge_setup_tbridge_setup_74582))
        return builder.toString()
    }

    private fun ensureReady(info: MainPack): Boolean {
        val status = TermuxBridgeManager.status(info.context)
        if (!status.termuxInstalled) {
            Tuils.sendOutput(info.context, info.context.getString(R.string.command_tbridge_termux_is_not_installed_3aba0))
            return false
        }
        if (!status.runCommandDeclared) {
            Tuils.sendOutput(info.context, info.context.getString(R.string.command_tbridge_this_termux_build_does_not_expose_run_comm_b2ba8))
            return false
        }
        if (!status.runCommandGranted) {
            TermuxBridgeManager.requestRunCommandPermissionIfPossible(info.context)
            Tuils.sendOutput(info.context, info.context.getString(R.string.command_tbridge_grant_re_t_ui_the_termux_run_command_permi_44e22))
            Tuils.sendOutput(info.context, info.context.getString(R.string.command_tbridge_termux_must_also_set_allow_external_apps_t_b23b5))
            return false
        }
        return true
    }

    private fun setupText(context: android.content.Context): String =
        context.getString(R.string.command_detail_tbridge_termux_bridge_setup_for_scripts_modules_an_de1dc)

    private fun retiredFileListingMessage(context: android.content.Context): String =
        context.getString(R.string.command_detail_tbridge_tbridge_file_listing_is_retired_from_the_p_d319c)

    override fun helpRes(): Int = R.string.help_tbridge

    override fun argType(): IntArray = intArrayOf(CommandAbstraction.PLAIN_TEXT)

    override fun priority(): Int = 4

    override fun onNotArgEnough(pack: ExecutePack, nArgs: Int): String =
        pack.context.getString(helpRes())

    override fun onArgNotFound(pack: ExecutePack, indexNotFound: Int): String =
        pack.context.getString(helpRes())

    companion object {
        const val CD_SCRIPT =
            "target=\"\$1\"; [ -d \"\$target\" ] || { echo \"not a directory: \$target\" >&2; exit 2; }; cd \"\$target\" && pwd"
        const val LIST_DIRS_SCRIPT =
            "dir=\"\$1\"; [ -d \"\$dir\" ] || { echo \"not a directory: \$dir\" >&2; exit 2; }; find \"\$dir\" -mindepth 1 -maxdepth 1 -type d -printf '%f/\\n' 2>/dev/null | sort"
        const val LIST_FILES_SCRIPT =
            "dir=\"\$1\"; [ -d \"\$dir\" ] || { echo \"not a directory: \$dir\" >&2; exit 2; }; find \"\$dir\" -mindepth 1 -maxdepth 1 -type f -printf '%f\\n' 2>/dev/null | sort"
        const val LIST_ALL_SCRIPT =
            "dir=\"\$1\"; [ -d \"\$dir\" ] || { echo \"not a directory: \$dir\" >&2; exit 2; }; { find \"\$dir\" -mindepth 1 -maxdepth 1 -type d -printf '%f/\\n' 2>/dev/null; find \"\$dir\" -mindepth 1 -maxdepth 1 -type f -printf '%f\\n' 2>/dev/null; } | sort"
        const val OPEN_FILE_SCRIPT =
            "target=\"\$1\"; [ -e \"\$target\" ] || { echo \"not found: \$target\" >&2; exit 2; }; [ -f \"\$target\" ] || { echo \"is directory: \$target\" >&2; exit 3; }; command -v termux-open >/dev/null || { echo \"termux-open missing\" >&2; exit 4; }; termux-open \"\$target\" && printf 'opening %s\\n' \"\$target\""
        const val SHARE_FILE_SCRIPT =
            "target=\"\$1\"; [ -e \"\$target\" ] || { echo \"not found: \$target\" >&2; exit 2; }; [ -f \"\$target\" ] || { echo \"is directory: \$target\" >&2; exit 3; }; if command -v termux-share >/dev/null; then termux-share \"\$target\" && printf 'sharing %s\\n' \"\$target\"; else echo \"termux-share missing; install Termux:API for share support\" >&2; exit 4; fi"
        private const val STATUS_SCRIPT =
            "printf 'termux_home=%s\\n' \"\$HOME\"; printf 'pwd=%s\\n' \"\$PWD\"; command -v find >/dev/null && echo 'find=available' || echo 'find=missing'; [ -d /storage/emulated/0 ] && echo 'shared_storage=visible' || echo 'shared_storage=not_visible'"
    }
}
