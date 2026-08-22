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
        val option = parts[0].lowercase(Locale.getDefault())

        if (option == "-status" || option == "-doctor") {
            return localStatus(info)
        }

        if (option == "-setup") {
            return setupText()
        }

        if (option == "-dirs" || option == "-files" || option == "-ls") {
            return retiredFileListingMessage()
        }

        if (!ensureReady(info)) {
            return null
        }

        if (option == "-probe") {
            TermuxBridgeManager.dispatchShell(info.context, "probe", STATUS_SCRIPT, TermuxBridgeManager.TERMUX_HOME)
            return "Termux bridge probe dispatched."
        }

        return info.res.getString(helpRes())
    }

    private fun localStatus(info: MainPack): String {
        val status = TermuxBridgeManager.status(info.context)
        val builder = StringBuilder()
        builder.append("Re:T-UI Termux Bridge").append('\n')
        builder.append("role: scripts, modules, callbacks, automation").append('\n')
        builder.append("termux: ").append(if (status.termuxInstalled) "installed" else "missing").append('\n')
        builder.append("RUN_COMMAND declared: ").append(if (status.runCommandDeclared) "yes" else "no").append('\n')
        builder.append("RUN_COMMAND granted: ").append(if (status.runCommandGranted) "yes" else "no").append('\n')
        builder.append("files: use the files command / Re:T-UI Files app").append('\n')
        builder.append("current path: ").append(info.currentDirectory.absolutePath).append('\n')
        builder.append("probe: tbridge -probe").append('\n')
        builder.append("setup: tbridge -setup")
        return builder.toString()
    }

    private fun ensureReady(info: MainPack): Boolean {
        val status = TermuxBridgeManager.status(info.context)
        if (!status.termuxInstalled) {
            Tuils.sendOutput(info.context, "Termux is not installed.")
            return false
        }
        if (!status.runCommandDeclared) {
            Tuils.sendOutput(info.context, "This Termux build does not expose RUN_COMMAND.")
            return false
        }
        if (!status.runCommandGranted) {
            TermuxBridgeManager.requestRunCommandPermissionIfPossible(info.context)
            Tuils.sendOutput(info.context, "Grant Re:T-UI the Termux RUN_COMMAND permission, then retry.")
            Tuils.sendOutput(info.context, "Termux must also set allow-external-apps=true.")
            return false
        }
        return true
    }

    private fun setupText(): String =
        "Termux bridge setup for scripts, modules, and automation:\n" +
            "1. Install Termux.\n" +
            "2. In Termux run: termux-setup-storage\n" +
            "3. In Termux run: mkdir -p ~/.termux && echo allow-external-apps=true >> ~/.termux/termux.properties\n" +
            "4. Restart Termux.\n" +
            "5. Grant Re:T-UI the RUN_COMMAND permission from Android app settings.\n" +
            "6. Run: tbridge -doctor\n" +
            "\nUse files for file navigation. TBridge is now the Termux runtime for scripts and modules."

    private fun retiredFileListingMessage(): String =
        "TBridge file listing is retired from the public command surface.\n" +
            "Use files, ls, open, or share through Re:T-UI Files.\n" +
            "TBridge now focuses on Termux runtime checks, scripts, modules, callbacks, and automation."

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
