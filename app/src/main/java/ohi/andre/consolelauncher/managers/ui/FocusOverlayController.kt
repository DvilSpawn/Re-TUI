package ohi.andre.consolelauncher.managers.ui

import android.content.Context
import android.content.Intent
import android.graphics.PorterDuff
import android.graphics.Typeface
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.commands.tuixt.BreachDialog
import ohi.andre.consolelauncher.commands.tuixt.TuixtDialog
import ohi.andre.consolelauncher.commands.tuixt.TuixtDialog.ConfirmAction
import ohi.andre.consolelauncher.managers.net.BreachManager
import ohi.andre.consolelauncher.managers.time.ClockManager
import ohi.andre.consolelauncher.managers.apps.FocusFrictionStyle
import ohi.andre.consolelauncher.managers.apps.LockdownManager
import ohi.andre.consolelauncher.managers.time.PomodoroManager
import ohi.andre.consolelauncher.managers.time.PomodoroManager.SessionType
import ohi.andre.consolelauncher.managers.RetuiCreditManager
import ohi.andre.consolelauncher.managers.settings.AppearanceSettings.dashedBorders
import ohi.andre.consolelauncher.managers.settings.AppearanceSettings.moduleCornerRadius
import ohi.andre.consolelauncher.managers.settings.ThemeColorResolver
import ohi.andre.consolelauncher.managers.xml.XMLPrefsManager
import ohi.andre.consolelauncher.managers.xml.options.Theme
import ohi.andre.consolelauncher.managers.xml.options.Ui
import ohi.andre.consolelauncher.tuils.frame.FrameTarget
import ohi.andre.consolelauncher.tuils.frame.TerminalBorderRuntime
import ohi.andre.consolelauncher.tuils.Tuils

/** Pomodoro and lockdown full-screen overlays; each hides the launcher chrome unless the other holds it. */
class FocusOverlayController(
    private val context: Context,
    private val rootView: View,
    private val closeKeyboard: () -> Unit,
    private val launcherWindowFocused: () -> Boolean,
    private val pauseAsciiAnimation: () -> Unit,
    private val resumeAsciiAnimation: () -> Unit
) {
    var isPomodoroOverlayVisible: Boolean = false
        private set
    var isLockdownOverlayVisible: Boolean = false
        private set

    fun updatePomodoroOverlay(intent: Intent) {
        val running = intent.getBooleanExtra(PomodoroManager.EXTRA_POMODORO_RUNNING, false)
        val remaining = intent.getLongExtra(PomodoroManager.EXTRA_POMODORO_REMAINING, 0L)
        val total = intent.getLongExtra(PomodoroManager.EXTRA_POMODORO_TOTAL, 0L)
        val task = intent.getStringExtra(PomodoroManager.EXTRA_POMODORO_TASK)
        val typeStr = intent.getStringExtra(PomodoroManager.EXTRA_POMODORO_TYPE)
        val message = intent.getStringExtra(PomodoroManager.EXTRA_MESSAGE)

        var overlay = rootView.findViewById<View?>(R.id.pomodoro_root)
        if (overlay == null) {
            if (!running) return
            overlay = View.inflate(context, R.layout.pomodoro_overlay, rootView as ViewGroup)
            setupPomodoroOverlay(overlay)
        }

        if (!running) {
            (rootView as ViewGroup).removeView(overlay)
            this.isPomodoroOverlayVisible = false
            if (!isLockdownOverlayVisible) {
                rootView.findViewById<View?>(R.id.main_container)?.setVisibility(View.VISIBLE)
                val terminalTray = rootView.findViewById<View?>(R.id.terminal_tray_container)
                if (terminalTray != null) {
                    terminalTray.setVisibility(View.VISIBLE)
                }
            }
            if (message != null) {
                Tuils.sendOutput(context, message)
            }
            return
        }

        this.isPomodoroOverlayVisible = true
        closeKeyboard()
        rootView.findViewById<View?>(R.id.main_container)?.setVisibility(View.GONE)
        val terminalTray = rootView.findViewById<View?>(R.id.terminal_tray_container)
        if (terminalTray != null) {
            terminalTray.setVisibility(View.GONE)
        }
        overlay.bringToFront()
        overlay.setElevation(Tuils.dpToPx(context, 128).toFloat())

        val title = overlay.findViewById<TextView>(R.id.pomodoro_title)
        val countdown = overlay.findViewById<TextView>(R.id.pomodoro_countdown)
        val taskDisplay = overlay.findViewById<TextView>(R.id.pomodoro_task_display)
        val terminateBtn = overlay.findViewById<Button>(R.id.pomodoro_terminate)

        val type = SessionType.valueOf(typeStr!!)

        overlay.setKeepScreenOn(running && type == SessionType.FOCUS)

        if (type == SessionType.FINISHED) {
            title.setText(context.getString(R.string.surface_mission_accomplished_5f337))
            title.setTextColor(XMLPrefsManager.getColor(Theme.input_text_color))
            taskDisplay.setText(context.getString(R.string.surface_good_job_you_did_great_c4fb2))
            countdown.setVisibility(View.GONE)
            terminateBtn.setText(context.getString(R.string.surface_exit_session_52cc0))
        } else {
            countdown.setVisibility(View.VISIBLE)
            if (!RetuiCreditManager.isDystopiaEnabled(context)) {
                terminateBtn.setText(context.getString(R.string.surface_terminate_session_cd886))
            } else if (RetuiCreditManager.wallet(context).credits >= RetuiCreditManager.ESCAPE_COST) {
                terminateBtn.setText(context.getString(R.string.surface_skip_pomodoro_credits_b7376, RetuiCreditManager.ESCAPE_COST))
            } else {
                terminateBtn.setText(context.getString(R.string.surface_emergency_breach_ce727))
            }
            if (type == SessionType.BREAK) {
                title.setText(context.getString(R.string.surface_take_a_break_849f9))
                title.setTextColor(XMLPrefsManager.getColor(Theme.input_text_color))
            } else {
                title.setText(context.getString(R.string.surface_focus_mode_active_f4757))
                title.setTextColor(ThemeColorResolver.color(Theme.error_text_color))
            }
            taskDisplay.setText(context.getString(R.string.surface_task_c6f9a, task))
            countdown.setText(ClockManager.formatDuration(remaining))
        }
    }

    private fun setupPomodoroOverlay(overlay: View) {
        val title = overlay.findViewById<TextView>(R.id.pomodoro_title)
        val countdown = overlay.findViewById<TextView>(R.id.pomodoro_countdown)
        val taskDisplay = overlay.findViewById<TextView>(R.id.pomodoro_task_display)
        val terminateBtn = overlay.findViewById<Button>(R.id.pomodoro_terminate)

        val color = ThemeColorResolver.color(Theme.overlay_panel_text_color)
        val bgColor = if (XMLPrefsManager.getBoolean(Ui.system_wallpaper)) {
            XMLPrefsManager.getColor(Theme.wallpaper_overlay_color)
        } else {
            XMLPrefsManager.getColor(Theme.background_color)
        }
        val textBgColor = ThemeColorResolver.color(Theme.overlay_panel_background_color)
        overlay.setBackgroundColor(bgColor)

        title.setTypeface(Tuils.getTypeface(context), Typeface.BOLD)
        countdown.setTypeface(Tuils.getTypeface(context), Typeface.BOLD)
        taskDisplay.setTypeface(Tuils.getTypeface(context))
        terminateBtn.setTypeface(Tuils.getTypeface(context), Typeface.BOLD)

        countdown.setTextColor(color)
        taskDisplay.setTextColor(color)
        terminateBtn.setTextColor(color)

        title.setBackgroundColor(textBgColor)
        countdown.setBackgroundColor(textBgColor)
        taskDisplay.setBackgroundColor(textBgColor)

        terminateBtn.setBackground(
            TerminalBorderRuntime.panelDrawable(
                context,
                textBgColor,
                color,
                1.4f,
                moduleCornerRadius(),
                dashedBorders(),
                target = FrameTarget.MODULES
            )
        )

        terminateBtn.setOnClickListener(View.OnClickListener { v: View? ->
            val manager = PomodoroManager.getInstance(context)
            if (manager.currentType == SessionType.FINISHED) {
                manager.stopSession()
            } else {
                TuixtDialog.showConfirm(
                    context,
                    context.getString(R.string.surface_terminate_7acd3),
                    pomodoroStopConfirmText(),
                    context.getString(R.string.surface_yes_8fff0),
                    context.getString(R.string.surface_no_a0509),
                    ConfirmAction {
                        terminatePomodoroEarly(manager)
                    })
            }
        })
    }

    private fun pomodoroStopConfirmText(): String {
        if (!RetuiCreditManager.isDystopiaEnabled(context)) {
            return context.getString(R.string.surface_do_you_really_want_to_stop_the_focus_session_8ed84)
        }
        return if (RetuiCreditManager.wallet(context).credits >= RetuiCreditManager.ESCAPE_COST) {
            context.getString(R.string.surface_stop_this_pomodoro_for_credits_ad296, RetuiCreditManager.ESCAPE_COST)
        } else {
            context.getString(R.string.surface_not_enough_credits_try_an_emergency_breach_75d79)
        }
    }

    private fun terminatePomodoroEarly(manager: PomodoroManager) {
        if (!RetuiCreditManager.isDystopiaEnabled(context)) {
            manager.stopSession()
            return
        }

        if (RetuiCreditManager.spendCredits(context)) {
            manager.stopSession()
            Tuils.sendOutput(context, context.getString(R.string.surface_pomodoro_terminated_credits_d6ff8, RetuiCreditManager.ESCAPE_COST))
            return
        }

        BreachDialog.show(context, BreachManager.Mode.EMERGENCY) { won ->
            if (won) {
                PomodoroManager.getInstance(context).stopSession()
            }
        }
    }

    fun updateLockdownOverlay(intent: Intent) {
        val running = intent.getBooleanExtra(LockdownManager.EXTRA_LOCKDOWN_RUNNING, false)
        val remaining = intent.getLongExtra(LockdownManager.EXTRA_LOCKDOWN_REMAINING, 0L)
        val reason = intent.getStringExtra(LockdownManager.EXTRA_LOCKDOWN_REASON)
        val message = intent.getStringExtra(LockdownManager.EXTRA_MESSAGE)

        var overlay = rootView.findViewById<View?>(R.id.lockdown_root)
        if (overlay == null) {
            if (!running) return
            overlay = View.inflate(context, R.layout.lockdown_overlay, rootView as ViewGroup)
            setupLockdownOverlay(overlay)
        }

        if (!running) {
            (rootView as ViewGroup).removeView(overlay)
            isLockdownOverlayVisible = false
            if (!isPomodoroOverlayVisible) {
                rootView.findViewById<View?>(R.id.main_container)?.setVisibility(View.VISIBLE)
                val terminalTray = rootView.findViewById<View?>(R.id.terminal_tray_container)
                if (terminalTray != null) {
                    terminalTray.setVisibility(View.VISIBLE)
                }
            }
            if (message != null) {
                Tuils.sendOutput(context, message)
            }
            if (launcherWindowFocused()) {
                resumeAsciiAnimation()
            }
            return
        }

        isLockdownOverlayVisible = true
        pauseAsciiAnimation()
        closeKeyboard()
        rootView.findViewById<View?>(R.id.main_container)?.setVisibility(View.GONE)
        val terminalTray = rootView.findViewById<View?>(R.id.terminal_tray_container)
        if (terminalTray != null) {
            terminalTray.setVisibility(View.GONE)
        }
        overlay.bringToFront()
        overlay.setElevation(Tuils.dpToPx(context, 160).toFloat())
        overlay.setKeepScreenOn(true)

        val countdown = overlay.findViewById<TextView>(R.id.lockdown_countdown)
        val reasonView = overlay.findViewById<TextView>(R.id.lockdown_reason)
        countdown.setText(ClockManager.formatDuration(remaining))
        reasonView.setText(if (reason.isNullOrBlank()) context.getString(R.string.surface_focus_mode_9a2af) else reason)
        refreshLockdownActions(overlay)
    }

    private fun setupLockdownOverlay(overlay: View) {
        overlay.setBackgroundColor(FocusFrictionStyle.overlayBackground())

        val title = overlay.findViewById<ImageView>(R.id.lockdown_title)
        val countdown = overlay.findViewById<TextView>(R.id.lockdown_countdown)
        val reason = overlay.findViewById<TextView>(R.id.lockdown_reason)
        val wallet = overlay.findViewById<TextView>(R.id.lockdown_wallet)
        val skip = overlay.findViewById<TextView>(R.id.lockdown_skip)
        val key = overlay.findViewById<TextView>(R.id.lockdown_key)
        val breach = overlay.findViewById<TextView>(R.id.lockdown_breach)

        title.setColorFilter(FocusFrictionStyle.buttonFill(), PorterDuff.Mode.SRC_IN)
        countdown.setTextColor(FocusFrictionStyle.bodyText())
        reason.setTextColor(FocusFrictionStyle.bodyText())
        wallet.setTextColor(FocusFrictionStyle.bodyText())

        countdown.setTypeface(Tuils.getTypeface(context), Typeface.BOLD)
        reason.setTypeface(Tuils.getTypeface(context))
        wallet.setTypeface(Tuils.getTypeface(context))

        FocusFrictionStyle.styleSticker(context, skip)
        FocusFrictionStyle.styleSticker(context, key)
        FocusFrictionStyle.styleSticker(context, breach)

        skip.setOnClickListener {
            if (RetuiCreditManager.spendCredits(context)) {
                LockdownManager.getInstance(context).stop(context.getString(R.string.surface_lockdown_skipped_credits_29c2d, RetuiCreditManager.ESCAPE_COST))
            } else {
                refreshLockdownActions(overlay)
            }
        }
        key.setOnClickListener {
            if (RetuiCreditManager.spendKey(context)) {
                LockdownManager.getInstance(context).stop(context.getString(R.string.surface_lockdown_skipped_with_breach_key_19c9d))
            } else {
                refreshLockdownActions(overlay)
            }
        }
        breach.setOnClickListener {
            BreachDialog.show(context, BreachManager.Mode.EMERGENCY) { won ->
                if (won) {
                    LockdownManager.getInstance(context).stop(context.getString(R.string.surface_emergency_breach_accepted_fed42))
                } else {
                    refreshLockdownActions(overlay)
                }
            }
        }

        refreshLockdownActions(overlay)
    }

    private fun refreshLockdownActions(overlay: View) {
        val current = RetuiCreditManager.wallet(context)
        val wallet = overlay.findViewById<TextView>(R.id.lockdown_wallet)
        val skip = overlay.findViewById<TextView>(R.id.lockdown_skip)
        val key = overlay.findViewById<TextView>(R.id.lockdown_key)
        val breach = overlay.findViewById<TextView>(R.id.lockdown_breach)

        wallet.setText(context.getString(R.string.surface_credits_keys_bb9c3, current.credits, current.keys))
        skip.setVisibility(if (current.credits >= RetuiCreditManager.ESCAPE_COST) View.VISIBLE else View.GONE)
        key.setText(context.getString(R.string.surface_use_breach_key_8d028, current.keys))
        key.setVisibility(if (current.keys > 0) View.VISIBLE else View.GONE)
        breach.setVisibility(
            if (current.credits < RetuiCreditManager.ESCAPE_COST && current.keys <= 0) View.VISIBLE else View.GONE
        )
    }
}
