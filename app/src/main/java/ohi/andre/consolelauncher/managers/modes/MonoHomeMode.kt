package ohi.andre.consolelauncher.managers.modes

import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.managers.terminal.TerminalManager
import ohi.andre.consolelauncher.managers.apps.AppsManager
import ohi.andre.consolelauncher.managers.modules.ReminderManager
import ohi.andre.consolelauncher.managers.suggestions.SuggestionTextWatcher
import ohi.andre.consolelauncher.managers.suggestions.SuggestionsManager
import ohi.andre.consolelauncher.managers.time.ClockManager
import ohi.andre.consolelauncher.mono.MonoHomeView
import ohi.andre.consolelauncher.tuils.interfaces.CommandExecuter
import ohi.andre.consolelauncher.tuils.interfaces.OnTextChanged

/** Mono mode: its own two-colour home over the (themed) classic layout, which stays underneath for drawers. */
internal class MonoHomeMode(private val host: HomeModeHost) : HomeMode {
    private var mono: MonoHomeView? = null
    private var suggestions: SuggestionsManager? = null

    override val managesOwnKeyboard get() = true

    override fun install() {
        val root = host.root ?: return
        val view = mono ?: MonoHomeView(host.context, source()).also { mono = it; wireSearch(it) }
        if (view.parent == null) root.addView(view, FrameLayout.LayoutParams(-1, -1))
        view.bringToFront()
        host.hideKeyboard()
    }

    override fun onStart() {
        mono?.closeInput()
        mono?.refresh()
        host.hideKeyboard()
    }

    override fun handleBack(): Boolean {
        val view = mono ?: return false
        view.back()
        return true
    }

    // Mono answers in its own session pane; nothing underneath needs to show.
    override fun beforeSearchCommand(command: String) {
        mono?.echo(command)
    }

    override fun afterSearchCommand() {
        val view = mono ?: return
        host.hideKeyboard()
        view.postDelayed({ host.hideKeyboard() }, 400L)
    }

    override fun onOutput(text: CharSequence?) {
        mono?.let { view -> host.onMain { view.output(text) } }
    }

    override fun onDataChanged() {
        mono?.let { view -> host.onMain { view.refresh() } }
    }

    private fun source(): MonoHomeView.Source {
        val apps = host.pack.appsManager
        val launch = { app: AppsManager.LaunchInfo ->
            MonoHomeView.Action(app.publicLabel?.lowercase(java.util.Locale.getDefault()).orEmpty()) {
                apps.launch(host.context, app)
            }
        }
        return MonoHomeView.Source(
            directory = {
                val home = android.os.Environment.getExternalStorageDirectory().absolutePath
                // VT323's tilde sits high and reads like an N on its own, so home always shows as ~/.
                val path = host.pack.currentDirectory.absolutePath
                if (path.startsWith(home)) "~/" + path.removePrefix(home).trimStart('/') else path
            },
            weather = {
                listOfNotNull(host.weatherDetails?.substringBefore(" · ")?.trim()?.takeIf { it.isNotEmpty() },
                    host.weatherCondition?.trim()?.takeIf { it.isNotEmpty() }).joinToString(" · ").ifEmpty { null }
            },
            weatherSymbol = { host.weatherSymbol },
            music = { host.music?.let { MonoHomeView.Music(it.title, it.artist, it.playing) } },
            notifications = {
                host.notifications.filter { !it.groupSummary }.sortedByDescending { it.time }.map { n ->
                    val time = java.text.SimpleDateFormat("HH:mm", java.util.Locale.US).format(java.util.Date(n.time))
                    val what = listOfNotNull(n.title?.takeIf { it.isNotBlank() }, n.body?.takeIf { it.isNotBlank() } ?: n.text)
                        .joinToString(": ").replace('\n', ' ')
                    MonoHomeView.Action(
                        "$time  ${(n.appName ?: n.pkg).orEmpty().lowercase(java.util.Locale.getDefault())}  $what") {
                        runCatching { n.pendingIntent?.send() }
                    }
                }
            },
            reminders = {
                runCatching { ReminderManager.list(host.context) }.getOrDefault(mutableListOf())
                    .filter { it.atMillis >= System.currentTimeMillis() }.sortedBy { it.atMillis }.map { reminder ->
                        ReminderManager.formatWhen(host.context, reminder.atMillis) + "  " + reminder.title.orEmpty()
                    }
            },
            clock = {
                val clock = ClockManager.getInstance(host.context)
                fun span(ms: Long): String {
                    val s = (ms / 1000).coerceAtLeast(0)
                    return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s / 60 % 60, s % 60) else "%d:%02d".format(s / 60, s % 60)
                }
                listOfNotNull(
                    when {
                        clock.isTimerRunning -> host.context.getString(R.string.mono_timer_running, span(clock.timerRemainingMillis))
                        clock.isTimerPaused -> host.context.getString(R.string.mono_timer_paused, span(clock.timerRemainingMillis))
                        else -> null
                    },
                    when {
                        clock.isStopwatchRunning -> host.context.getString(R.string.mono_stopwatch_running, span(clock.stopwatchElapsedMillis))
                        clock.isStopwatchPaused -> host.context.getString(R.string.mono_stopwatch_paused, span(clock.stopwatchElapsedMillis))
                        else -> null
                    }
                )
            },
            chips = { HomeApps.picks(host.pack.appsManager).map(launch) }
        )
    }

    /**
     * The mono input line runs on the same terminal and suggestion engine as the floating search: typing fills
     * the results above it, Enter or a result runs, and the answer lands in the session.
     */
    private fun wireSearch(mono: MonoHomeView) {
        // TerminalManager expects its output inside a ScrollView; mono shows answers in its session instead.
        val output = TextView(host.context)
        ScrollView(host.context).addView(output)
        // The suggestion engine also expects its chip row inside a LinearLayout inside a HorizontalScrollView.
        val chips = LinearLayout(host.context)
        android.widget.HorizontalScrollView(host.context).addView(LinearLayout(host.context).apply { addView(chips) })
        val run = CommandExecuter { command, obj ->
            host.onMain {
                if (obj is AppsManager.LaunchInfo) host.executeResolved(command, obj)
                else if (!command.isNullOrBlank()) host.executeSearchCommand(command)
                // The terminal takes the input back right after running; close it once it has.
                mono.closeInput()
                mono.post { mono.closeInput() }
            }
        }
        val terminal = TerminalManager(output, mono.input, mono.prefix, mono.submit, null, null, null, null, host.context, host.pack, run)
        suggestions = SuggestionsManager(chips, host.pack, terminal, mono.results, true).also { manager ->
            mono.input.addTextChangedListener(SuggestionTextWatcher(manager,
                OnTextChanged { _, _ -> }))
            manager.setSearchInputActive(true)
            manager.requestSuggestion("")
        }
        mono.prefix.text = "$ "
        mono.styleInput()
    }
}
