package ohi.andre.consolelauncher.managers.termux

import ohi.andre.consolelauncher.UIManager

/** Builds the tmux shell scripts for Termux apps. Holds no state; geometry comes from the caller. */
internal object TermuxAppScripts {
    fun buildTermuxAppResizeScript(session: String, geometry: () -> UIManager.TermuxWorkspaceGeometry): String {
        val resolved = geometry()
        return ("tmux resize-window -t " + session + " -x " + resolved.cols + " -y " + resolved.rows + " 2>/dev/null || true\n"
                + "tmux resize-pane -t " + session + " -x " + resolved.cols + " -y " + resolved.rows + " 2>/dev/null || true")
    }

    fun buildTermuxAppStartScript(app: TermuxAppManager.TermuxApp, geometry: () -> UIManager.TermuxWorkspaceGeometry): String {
        val session = shellQuote(TermuxAppManager.tmuxSessionName(app.id))
        val workDir = shellQuote(app.workDir)
        val command = shellQuote(app.command)
        val sh = shellQuote(TermuxBridgeManager.TERMUX_SH)
        val appId = shellQuote(app.id)
        val appHome = shellQuote(app.homeDir)
        val state = shellQuote(app.statePath)
        val manifest = shellQuote(app.manifestPath)
        return (buildTermuxAppPrepareScript(app, false) + "\n"
                + "if ! command -v tmux >/dev/null 2>&1; then\n"
                + "  printf '%s\\n' 'tmux missing: pkg install tmux'\n"
                + "  exit 127\n"
                + "fi\n"
                + "if ! tmux has-session -t " + session + " 2>/dev/null; then\n"
                + "  mkdir -p " + workDir + " 2>/dev/null || true\n"
                + "  tmux new-session -d -s " + session + " -c " + workDir
                + " env RETUI_APP_ID=" + appId
                + " RETUI_APP_HOME=" + appHome
                + " RETUI_APP_STATE=" + state
                + " RETUI_APP_MANIFEST=" + manifest
                + " " + sh + " -lc " + command + "\n"
                + "  sleep 0.35\n"
                + "fi\n"
                + buildTermuxAppResizeScript(session, geometry) + "\n"
                + "tmux capture-pane -t " + session + " -p -e -N")
    }

    fun buildTermuxAppPrepareScript(app: TermuxAppManager.TermuxApp, echo: Boolean): String {
        val appHome = shellQuote(app.homeDir)
        val memoryDir = shellQuote(app.memoryDir)
        val logsDir = shellQuote(app.logsDir)
        val manifest = shellQuote(app.manifestPath)
        val state = shellQuote(app.statePath)
        val json = shellQuote(TermuxAppManager.manifestJson(app))
        val script = ("mkdir -p " + appHome + " " + memoryDir + " " + logsDir + "\n"
                + "if [ ! -f " + state + " ]; then printf '%s\\n' '{}' > " + state + "; fi\n"
                + "printf '%s\\n' " + json + " > " + manifest)
        if (!echo) {
            return script
        }
        return script + "\nprintf '%s\\n' 'manifest: " + app.manifestPath.replace("'", "'\"'\"'") + "'"
    }

    fun buildTermuxAppSendScript(app: TermuxAppManager.TermuxApp, input: String, geometry: () -> UIManager.TermuxWorkspaceGeometry): String {
        val session = shellQuote(TermuxAppManager.tmuxSessionName(app.id))
        val send = if (input.length == 0) {
            "tmux send-keys -t " + session + " C-m"
        } else {
            "tmux send-keys -t " + session + " -- " + shellQuote(input) + "\n" +
                    "tmux send-keys -t " + session + " C-m"
        }
        return buildTermuxAppEnsureScript(app) + "\n" +
                buildTermuxAppResizeScript(session, geometry) + "\n" +
                send + "\nsleep 0.25\n" +
                buildTermuxAppPostInputCaptureScript(session)
    }

    fun buildTermuxAppControlScript(app: TermuxAppManager.TermuxApp, key: String, geometry: () -> UIManager.TermuxWorkspaceGeometry): String {
        val session = shellQuote(TermuxAppManager.tmuxSessionName(app.id))
        return buildTermuxAppEnsureScript(app) + "\n" +
                buildTermuxAppResizeScript(session, geometry) + "\n" +
                "tmux send-keys -t " + session + " -- " + shellQuote(key) + "\n" +
                "sleep 0.25\n" +
                buildTermuxAppPostInputCaptureScript(session)
    }

    fun buildTermuxAppCaptureScript(app: TermuxAppManager.TermuxApp, geometry: () -> UIManager.TermuxWorkspaceGeometry): String {
        val session = shellQuote(TermuxAppManager.tmuxSessionName(app.id))
        return buildTermuxAppEnsureScript(app) + "\n" +
                buildTermuxAppResizeScript(session, geometry) + "\n" +
                "tmux capture-pane -t " + session + " -p -e -N"
    }

    fun buildTermuxAppPostInputCaptureScript(session: String): String {
        return ("if ! tmux has-session -t " + session + " 2>/dev/null; then\n"
                + "  printf '%s\\n' 'session ended. Type :restart to start it.'\n"
                + "  exit 0\n"
                + "fi\n"
                + "tmux capture-pane -t " + session + " -p -e -N")
    }

    fun buildTermuxAppKillScript(app: TermuxAppManager.TermuxApp): String {
        val session = shellQuote(TermuxAppManager.tmuxSessionName(app.id))
        return ("if command -v tmux >/dev/null 2>&1; then\n"
                + "  tmux kill-session -t " + session + " 2>/dev/null || true\n"
                + "  printf '%s\\n' 'session stopped.'\n"
                + "else\n"
                + "  printf '%s\\n' 'tmux missing: pkg install tmux'\n"
                + "fi")
    }

    fun buildTermuxAppEnsureScript(app: TermuxAppManager.TermuxApp): String {
        val session = shellQuote(TermuxAppManager.tmuxSessionName(app.id))
        return ("if ! command -v tmux >/dev/null 2>&1; then\n"
                + "  printf '%s\\n' 'tmux missing: pkg install tmux'\n"
                + "  exit 127\n"
                + "fi\n"
                + "if ! tmux has-session -t " + session + " 2>/dev/null; then\n"
                + "  printf '%s\\n' 'session not running. Type :restart to start it.'\n"
                + "  exit 1\n"
                + "fi")
    }

    fun shellQuote(value: String?): String {
        if (value == null) {
            return "''"
        }
        return "'" + value.replace("'", "'\"'\"'") + "'"
    }
}
