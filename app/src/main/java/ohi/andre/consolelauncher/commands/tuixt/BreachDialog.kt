package ohi.andre.consolelauncher.commands.tuixt

import android.animation.ObjectAnimator
import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import ohi.andre.consolelauncher.commands.tuixt.TuixtDialog.ConfirmAction
import ohi.andre.consolelauncher.commands.tuixt.TuixtDialog.ContentFactory
import ohi.andre.consolelauncher.managers.BreachManager
import ohi.andre.consolelauncher.managers.FocusFrictionStyle
import ohi.andre.consolelauncher.managers.RetuiCreditManager
import ohi.andre.consolelauncher.tuils.TerminalBorderRuntime
import ohi.andre.consolelauncher.tuils.Tuils
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

object BreachDialog {
    private const val ROUND_TIME_MS = 15000L
    private const val TIMER_BARS = 15
    private const val TIMER_FILLED = "█"
    private const val TIMER_EMPTY = "░"

    fun show(
        context: Context,
        mode: BreachManager.Mode = BreachManager.Mode.NORMAL,
        reward: Boolean = true,
        onComplete: ((Boolean) -> Unit)? = null
    ) {
        showSession(context, BreachManager.newSession(mode), reward, onComplete)
    }

    private fun showSession(
        context: Context,
        session: BreachManager.Session,
        reward: Boolean,
        onComplete: ((Boolean) -> Unit)?,
        remainingMs: Long = ROUND_TIME_MS
    ) {
        TuixtDialog.showCustomCompactDimmed(
            context,
            if (session.mode == BreachManager.Mode.EMERGENCY) "EMERGENCY BREACH" else "BREACH",
            ContentFactory { dialog: Dialog? ->
                BreachRound(context, dialog, session, reward, onComplete, remainingMs).build()
            }
        )
    }

    private class BreachRound(
        private val context: Context,
        private val dialog: Dialog?,
        initialSession: BreachManager.Session,
        private val reward: Boolean,
        private val onComplete: ((Boolean) -> Unit)?,
        initialRemainingMs: Long
    ) {
        private val handler = Handler(Looper.getMainLooper())
        private val root = LinearLayout(context)
        private val target = TextView(context)
        private val timer = TextView(context)
        private val buffer = TextView(context)
        private val status = TextView(context)
        private val keypad = GridLayout(context)
        private val mode = initialSession.mode
        private val ticker = object : Runnable {
            override fun run() {
                if (closing || resolvingFailure) return
                val remaining = deadline - SystemClock.elapsedRealtime()
                if (remaining <= 0L) {
                    failRound("TIME EXPIRED")
                    return
                }
                timer.text = timerText(remaining)
                handler.postDelayed(this, 250L)
            }
        }

        private var session = initialSession
        private var roundRemainingMs = initialRemainingMs.coerceIn(1L, ROUND_TIME_MS)
        private var deadline = 0L
        private var closing = false
        private var resolvingFailure = false
        private var disposed = false
        private var exitPromptShowing = false

        fun build(): View {
            root.orientation = LinearLayout.VERTICAL
            root.gravity = Gravity.CENTER_HORIZONTAL
            root.setPadding(
                0,
                FocusFrictionStyle.dp(context, 4f),
                0,
                FocusFrictionStyle.dp(context, 4f)
            )

            addLabel("TARGET")
            styleTarget(target)
            root.addView(
                target,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )

            styleTimer(timer)
            root.addView(
                timer,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )

            val keypadPanel = LinearLayout(context)
            keypadPanel.orientation = LinearLayout.VERTICAL
            keypadPanel.gravity = Gravity.CENTER
            keypadPanel.setPadding(
                FocusFrictionStyle.dp(context, 6f),
                FocusFrictionStyle.dp(context, 6f),
                FocusFrictionStyle.dp(context, 6f),
                FocusFrictionStyle.dp(context, 6f)
            )
            keypadPanel.background = TerminalBorderRuntime.panelDrawable(
                context,
                ColorUtils.setAlphaComponent(FocusFrictionStyle.buttonFill(), 45),
                FocusFrictionStyle.buttonText(),
                1.4f,
                0,
                false
            )
            keypadPanel.addView(keypad)
            val keypadParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            keypadParams.setMargins(0, FocusFrictionStyle.dp(context, 6f), 0, FocusFrictionStyle.dp(context, 6f))
            root.addView(keypadPanel, keypadParams)

            styleLine(buffer, 13f, Typeface.BOLD)
            root.addView(
                buffer,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )

            styleLine(status, 12f, Typeface.NORMAL)
            root.addView(
                status,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )

            dialog?.setCanceledOnTouchOutside(true)
            dialog?.setOnCancelListener { requestExit() }
            dialog?.setOnDismissListener { dispose() }
            startRound("BREACH ARMED")
            return root
        }

        private fun startRound(message: String) {
            root.setBackgroundColor(Color.TRANSPARENT)
            deadline = SystemClock.elapsedRealtime() + roundRemainingMs
            status.text = "$message\n${instruction()}"
            render()
            handler.removeCallbacks(ticker)
            handler.post(ticker)
        }

        private fun resetRound(message: String) {
            session = BreachManager.newSession(mode)
            roundRemainingMs = ROUND_TIME_MS
            resolvingFailure = false
            startRound(message)
        }

        private fun render() {
            target.text = session.targets.joinToString("   ") { it.joinToString("  ") }
            timer.text = timerText(max(0L, deadline - SystemClock.elapsedRealtime()))
            buffer.text = "BUFFER: " + if (session.buffer.isEmpty()) "-" else session.buffer.joinToString(" ")
            renderKeypad()
        }

        private fun renderKeypad() {
            keypad.removeAllViews()
            keypad.rowCount = session.grid.size
            keypad.columnCount = session.grid.size
            val cellSize = FocusFrictionStyle.dp(context, 58f)
            val gap = FocusFrictionStyle.dp(context, 3f)

            for (row in session.grid.indices) {
                for (col in session.grid[row].indices) {
                    val cell = BreachManager.Cell(row, col)
                    val token = TextView(context)
                    token.text = session.grid[row][col]
                    styleCell(token, session.isAvailable(cell), session.isUsed(cell))
                    if (session.isAvailable(cell)) {
                        token.setOnClickListener { pick(cell) }
                    }

                    val params = GridLayout.LayoutParams(GridLayout.spec(row), GridLayout.spec(col))
                    params.width = cellSize
                    params.height = cellSize
                    params.setMargins(gap, gap, gap, gap)
                    keypad.addView(token, params)
                }
            }
        }

        private fun pick(cell: BreachManager.Cell) {
            if (closing || resolvingFailure) return
            val result = session.pick(cell)
            render()
            when {
                result.won -> {
                    closing = true
                    dispose()
                    dialog?.dismiss()
                    finish(context, session, true, reward, onComplete)
                }
                result.lost -> failRound(result.message.uppercase(Locale.US))
                else -> status.text = instruction()
            }
        }

        private fun failRound(reason: String) {
            if (closing || resolvingFailure) return
            resolvingFailure = true
            handler.removeCallbacks(ticker)
            val wallet = RetuiCreditManager.recordBreachFailure(context)
            status.text = "$reason\nBREACH FAILED -${RetuiCreditManager.BREACH_FAILURE_COST} CREDITS\nCREDITS: ${wallet.credits}"
            failFeedback(context, root) {
                if (disposed) return@failFeedback
                resetRound("NEW TARGET LOADED")
            }
        }

        private fun requestExit() {
            if (closing || exitPromptShowing) return
            exitPromptShowing = true
            val promptSession = if (resolvingFailure || session.complete) BreachManager.newSession(mode) else session
            roundRemainingMs = if (promptSession === session) {
                max(1L, deadline - SystemClock.elapsedRealtime())
            } else {
                ROUND_TIME_MS
            }
            handler.removeCallbacks(ticker)
            showExitPrompt(context, promptSession, roundRemainingMs, reward, onComplete)
        }

        private fun dispose() {
            disposed = true
            handler.removeCallbacks(ticker)
        }

        private fun instruction(): String =
            "SELECT " + session.activeAxis.name.lowercase(Locale.US) + " " + (session.activeIndex + 1)

        private fun addLabel(text: String) {
            val label = TextView(context)
            label.text = text
            styleLine(label, 12f, Typeface.BOLD)
            root.addView(
                label,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )
        }

        private fun styleCell(view: TextView, available: Boolean, used: Boolean) {
            val fill = when {
                used -> ColorUtils.setAlphaComponent(FocusFrictionStyle.buttonFill(), 40)
                available -> FocusFrictionStyle.buttonFill()
                else -> ColorUtils.setAlphaComponent(FocusFrictionStyle.buttonFill(), 28)
            }
            val text = when {
                used -> ColorUtils.setAlphaComponent(FocusFrictionStyle.bodyText(), 90)
                available -> FocusFrictionStyle.buttonText()
                else -> ColorUtils.setAlphaComponent(FocusFrictionStyle.bodyText(), 150)
            }
            view.setTextColor(text)
            view.setTypeface(Tuils.getTypeface(context), Typeface.BOLD)
            view.textSize = 17f
            view.gravity = Gravity.CENTER
            view.background = TerminalBorderRuntime.panelDrawable(
                context,
                fill,
                if (available) FocusFrictionStyle.buttonText() else FocusFrictionStyle.buttonFill(),
                1.5f,
                0,
                false
            )
        }

        private fun styleTarget(view: TextView) {
            view.setTextColor(FocusFrictionStyle.buttonText())
            view.setTypeface(Tuils.getTypeface(context), Typeface.BOLD)
            view.textSize = 22f
            view.gravity = Gravity.CENTER
            view.setPadding(
                FocusFrictionStyle.dp(context, 12f),
                FocusFrictionStyle.dp(context, 8f),
                FocusFrictionStyle.dp(context, 12f),
                FocusFrictionStyle.dp(context, 8f)
            )
            view.background = TerminalBorderRuntime.panelDrawable(
                context,
                ColorUtils.setAlphaComponent(FocusFrictionStyle.buttonFill(), 185),
                FocusFrictionStyle.buttonText(),
                1.6f,
                0,
                false
            )
        }

        private fun styleTimer(view: TextView) {
            styleLine(view, 14f, Typeface.BOLD)
            view.setPadding(0, FocusFrictionStyle.dp(context, 8f), 0, FocusFrictionStyle.dp(context, 4f))
        }

        private fun styleLine(view: TextView, size: Float, style: Int) {
            view.setTextColor(FocusFrictionStyle.bodyText())
            view.setTypeface(Tuils.getTypeface(context), style)
            view.textSize = size
            view.gravity = Gravity.CENTER
            view.setPadding(0, FocusFrictionStyle.dp(context, 3f), 0, FocusFrictionStyle.dp(context, 3f))
        }
    }

    private fun showExitPrompt(
        context: Context,
        session: BreachManager.Session,
        remainingMs: Long,
        reward: Boolean,
        onComplete: ((Boolean) -> Unit)?
    ) {
        TuixtDialog.showCustomCompactDimmed(
            context,
            "EXIT BREACH?",
            ContentFactory { dialog: Dialog? ->
                dialog?.setCancelable(false)
                dialog?.setCanceledOnTouchOutside(false)
                exitPromptContent(context, dialog, session, remainingMs, reward, onComplete)
            }
        )
    }

    private fun exitPromptContent(
        context: Context,
        dialog: Dialog?,
        session: BreachManager.Session,
        remainingMs: Long,
        reward: Boolean,
        onComplete: ((Boolean) -> Unit)?
    ): View {
        val root = LinearLayout(context)
        root.orientation = LinearLayout.VERTICAL
        root.gravity = Gravity.CENTER

        val message = TextView(context)
        message.text = "Exit breach?\nCost: -${RetuiCreditManager.BREACH_EXIT_COST} credit\nTimer paused."
        message.setTextColor(FocusFrictionStyle.bodyText())
        message.setTypeface(Tuils.getTypeface(context), Typeface.BOLD)
        message.textSize = 13f
        message.gravity = Gravity.CENTER
        message.setPadding(
            0,
            FocusFrictionStyle.dp(context, 4f),
            0,
            FocusFrictionStyle.dp(context, 12f)
        )
        root.addView(
            message,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        val row = LinearLayout(context)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER

        val cancel = exitPromptButton(context, "CANCEL", false)
        cancel.setOnClickListener {
            dialog?.dismiss()
            showSession(context, session, reward, onComplete, remainingMs)
        }
        row.addView(cancel, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        val spacer = View(context)
        row.addView(spacer, LinearLayout.LayoutParams(FocusFrictionStyle.dp(context, 10f), 1))

        val exit = exitPromptButton(context, "EXIT -${RetuiCreditManager.BREACH_EXIT_COST}", true)
        exit.setOnClickListener {
            RetuiCreditManager.spendCredits(context, RetuiCreditManager.BREACH_EXIT_COST)
            dialog?.dismiss()
            onComplete?.invoke(false)
        }
        row.addView(exit, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        root.addView(
            row,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )
        return root
    }

    private fun exitPromptButton(context: Context, text: String, filled: Boolean): TextView {
        val button = TextView(context)
        button.text = text
        FocusFrictionStyle.styleSticker(context, button, filled)
        return button
    }

    private fun failFeedback(context: Context, root: View, after: () -> Unit) {
        FocusFrictionStyle.vibrate(context, longArrayOf(0L, 60L, 45L, 60L, 45L, 120L))
        root.setBackgroundColor(ColorUtils.setAlphaComponent(Color.RED, 95))
        ObjectAnimator.ofFloat(root, View.TRANSLATION_X, 0f, -14f, 14f, -9f, 9f, 0f)
            .setDuration(280L)
            .start()
        root.postDelayed(after, 340L)
    }

    private fun finish(
        context: Context,
        session: BreachManager.Session,
        won: Boolean,
        reward: Boolean,
        onComplete: ((Boolean) -> Unit)?
    ) {
        val message = if (won) {
            val result = if (reward) RetuiCreditManager.rewardBreach(context, session.mode.reward) else null
            "Breach successful." +
                (if (result != null) "\n+${result.credits} credits" else "") +
                (if (result?.keyAwarded == true) "\nBreach key acquired." else "")
        } else {
            val wallet = RetuiCreditManager.recordBreachFailure(context)
            "Breach failed.\n-${RetuiCreditManager.BREACH_FAILURE_COST} credits\nCredits: ${wallet.credits}"
        }

        onComplete?.invoke(won)
        TuixtDialog.showConfirm(
            context,
            if (won) "BREACH COMPLETE" else "BREACH FAILED",
            message,
            "OK",
            "CLOSE",
            ConfirmAction { }
        )
    }

    private fun timerText(remainingMs: Long): String {
        val totalSeconds = max(1, (ROUND_TIME_MS / 1000L).toInt())
        val seconds = min(totalSeconds, max(0, ((remainingMs + 999L) / 1000L).toInt()))
        val filled = (seconds * TIMER_BARS + totalSeconds - 1) / totalSeconds
        return "TIME " + TIMER_FILLED.repeat(filled) + TIMER_EMPTY.repeat(TIMER_BARS - filled) + " " + seconds + "s"
    }
}
