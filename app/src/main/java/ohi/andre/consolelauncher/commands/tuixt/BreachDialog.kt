package ohi.andre.consolelauncher.commands.tuixt

import android.animation.ObjectAnimator
import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import ohi.andre.consolelauncher.commands.tuixt.TuixtDialog.ConfirmAction
import ohi.andre.consolelauncher.commands.tuixt.TuixtDialog.ContentFactory
import ohi.andre.consolelauncher.managers.BreachManager
import ohi.andre.consolelauncher.managers.FocusFrictionStyle
import ohi.andre.consolelauncher.managers.RetuiCreditManager
import ohi.andre.consolelauncher.tuils.Tuils
import java.util.Locale

object BreachDialog {
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
        onComplete: ((Boolean) -> Unit)?
    ) {
        TuixtDialog.showCustom(
            context,
            if (session.mode == BreachManager.Mode.EMERGENCY) "EMERGENCY BREACH" else "BREACH",
            ContentFactory { dialog: Dialog? ->
                content(context, dialog, session, reward, onComplete)
            }
        )
    }

    private fun content(
        context: Context,
        dialog: Dialog?,
        session: BreachManager.Session,
        reward: Boolean,
        onComplete: ((Boolean) -> Unit)?
    ): View {
        val root = LinearLayout(context)
        root.orientation = LinearLayout.VERTICAL
        root.gravity = Gravity.CENTER
        var closing = false

        addText(context, root, "TARGET: " + session.targets.joinToString("  ") { it.joinToString(" ") }, 12f)
        addText(context, root, "BUFFER: " + if (session.buffer.isEmpty()) "-" else session.buffer.joinToString(" "), 12f)
        addText(
            context,
            root,
            "SELECT " + session.activeAxis.name.lowercase(Locale.US) + " " + (session.activeIndex + 1),
            11f
        )

        for (row in session.grid.indices) {
            val rowView = LinearLayout(context)
            rowView.orientation = LinearLayout.HORIZONTAL
            rowView.gravity = Gravity.CENTER
            rowView.setPadding(0, FocusFrictionStyle.dp(context, 4f), 0, 0)
            root.addView(rowView)

            for (col in session.grid[row].indices) {
                val cell = BreachManager.Cell(row, col)
                val token = TextView(context)
                token.text = session.grid[row][col]
                FocusFrictionStyle.styleSticker(context, token, session.isAvailable(cell))
                token.alpha = when {
                    session.isUsed(cell) -> 0.30f
                    session.isAvailable(cell) -> 1f
                    else -> 0.50f
                }
                token.minWidth = FocusFrictionStyle.dp(context, 58f)
                val params = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                params.setMargins(
                    FocusFrictionStyle.dp(context, 3f),
                    0,
                    FocusFrictionStyle.dp(context, 3f),
                    0
                )
                rowView.addView(token, params)

                if (session.isAvailable(cell)) {
                    token.setOnClickListener {
                        if (closing) return@setOnClickListener
                        val result = session.pick(cell)
                        when {
                            result.won -> {
                                closing = true
                                dialog?.dismiss()
                                finish(context, session, true, reward, onComplete)
                            }
                            result.lost -> {
                                closing = true
                                failFeedback(context, root) {
                                    dialog?.dismiss()
                                    finish(context, session, false, reward, onComplete)
                                }
                            }
                            else -> {
                                dialog?.dismiss()
                                showSession(context, session, reward, onComplete)
                            }
                        }
                    }
                }
            }
        }

        return root
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
            "Breach failed.\n-1 credit\nCredits: ${wallet.credits}"
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

    private fun addText(context: Context, root: LinearLayout, text: String, size: Float) {
        val view = TextView(context)
        view.text = text
        view.setTextColor(FocusFrictionStyle.bodyText())
        view.setTypeface(Tuils.getTypeface(context))
        view.textSize = size
        view.gravity = Gravity.CENTER
        view.setPadding(0, FocusFrictionStyle.dp(context, 3f), 0, FocusFrictionStyle.dp(context, 3f))
        root.addView(
            view,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )
    }
}
