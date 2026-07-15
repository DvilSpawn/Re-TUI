package ohi.andre.consolelauncher.commands.tuixt

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import ohi.andre.consolelauncher.commands.tuixt.TuixtLayout.addFoldAwareHost
import ohi.andre.consolelauncher.commands.tuixt.TuixtTheme.dp
import ohi.andre.consolelauncher.commands.tuixt.TuixtTheme.styleButton
import ohi.andre.consolelauncher.commands.tuixt.TuixtTheme.styleHeader
import ohi.andre.consolelauncher.commands.tuixt.TuixtTheme.styleInput
import ohi.andre.consolelauncher.commands.tuixt.TuixtTheme.styleListItem
import ohi.andre.consolelauncher.commands.tuixt.TuixtTheme.stylePanel
import ohi.andre.consolelauncher.commands.tuixt.TuixtTheme.textColor
import ohi.andre.consolelauncher.tuils.LauncherSystemUi.applyFullscreen
import ohi.andre.consolelauncher.tuils.Tuils
import java.util.Locale
import ohi.andre.consolelauncher.tuils.LauncherSystemUi

object TuixtDialog {
    data class FormField(
        val key: String,
        val label: String,
        val hint: String? = null,
        val inputType: Int = InputType.TYPE_CLASS_TEXT,
        val value: String? = null
    )

    fun showOptions(
        context: Context,
        title: String,
        items: List<String?>,
        action: ItemAction
    ) {
        Handler(Looper.getMainLooper()).post(Runnable {
            val dialog = createDialog(context)
            val content = createContent(context)

            for (i in items.indices) {
                val index = i
                val row = TextView(context)
                row.setText(items.get(i)!!.uppercase(Locale.getDefault()))
                styleListItem(context, row, false)
                val rowParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                rowParams.bottomMargin = dp(context, 8f)
                content.addView(row, rowParams)
                row.setOnClickListener(View.OnClickListener { v: View? ->
                    dialog.dismiss()
                    action.onItemSelected(index)
                })
            }

            dialog.setContentView(wrap(context, title, content, null))
            show(dialog)
        })
    }

    fun showInput(
        context: Context,
        title: String,
        hint: String?,
        positive: String,
        negative: String,
        action: InputAction
    ) {
        Handler(Looper.getMainLooper()).post(Runnable {
            val dialog = createDialog(context)
            val content = createContent(context)

            val input = EditText(context)
            input.setHint(hint)
            styleInput(context, input)
            content.addView(
                input, LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )

            val buttons = buttons(
                context,
                dialog,
                positive,
                negative,
                ConfirmAction { action.onInput(input.getText().toString()) })
            dialog.setContentView(wrap(context, title, content, buttons))
            show(dialog)
            dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
            input.post {
                input.requestFocus()
                (context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager)
                    ?.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT)
            }
        })
    }

    fun showValidatedForm(
        context: Context,
        title: String,
        fields: List<FormField>,
        positive: String,
        negative: String,
        validator: FormValidator,
        action: FormAction
    ) {
        Handler(Looper.getMainLooper()).post(Runnable {
            val dialog = createDialog(context)
            val content = createContent(context)
            val inputs = LinkedHashMap<String, EditText>()

            fields.forEach { field ->
                val label = TextView(context)
                label.text = field.label.uppercase(Locale.getDefault())
                label.setTextColor(textColor())
                label.setTypeface(Tuils.getTypeface(context))
                label.textSize = 12f
                label.setPadding(0, dp(context, 6f), 0, dp(context, 4f))
                content.addView(label)

                val input = EditText(context)
                input.hint = field.hint
                input.inputType = field.inputType
                if (field.value != null) input.setText(field.value)
                styleInput(context, input)
                content.addView(
                    input,
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                )
                inputs[field.key] = input
            }

            val error = TextView(context)
            error.setTextColor(Color.RED)
            error.setTypeface(Tuils.getTypeface(context))
            error.textSize = 12f
            error.visibility = View.GONE
            error.setPadding(0, dp(context, 8f), 0, 0)
            content.addView(error)

            val buttons = buttons(
                context,
                dialog,
                positive,
                negative,
                ConfirmAction {
                    val values = inputs.mapValues { it.value.text.toString().trim() }
                    val message = validator.validate(values)
                    if (message != null) {
                        error.text = message
                        error.visibility = View.VISIBLE
                        return@ConfirmAction
                    }
                    dialog.dismiss()
                    action.onSubmit(values)
                },
                false
            )
            dialog.setContentView(wrap(context, title, content, buttons))
            show(dialog)
            dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
            inputs.values.firstOrNull()?.post {
                val input = inputs.values.firstOrNull() ?: return@post
                input.requestFocus()
                (context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager)
                    ?.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT)
            }
        })
    }

    fun showConfirm(
        context: Context,
        title: String,
        message: String?,
        positive: String,
        negative: String,
        action: ConfirmAction
    ) {
        Handler(Looper.getMainLooper()).post(Runnable {
            val dialog = createDialog(context)
            val content = createContent(context)

            val messageView = TextView(context)
            messageView.setText(message)
            messageView.setTextColor(textColor())
            messageView.setTypeface(Tuils.getTypeface(context))
            messageView.setTextSize(14f)
            messageView.setPadding(
                dp(context, 10f),
                dp(context, 12f),
                dp(context, 10f),
                dp(context, 12f)
            )
            content.addView(
                messageView, LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )

            val buttons = buttons(context, dialog, positive, negative, action)
            dialog.setContentView(wrap(context, title, content, buttons))
            show(dialog)
        })
    }

    fun showContent(
        context: Context,
        title: String,
        customContent: View?,
        positive: String,
        negative: String,
        action: ConfirmAction
    ) {
        Handler(Looper.getMainLooper()).post(Runnable {
            val dialog = createDialog(context)
            val content = createContent(context)
            content.addView(
                customContent, LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )

            val buttons = buttons(context, dialog, positive, negative, action)
            dialog.setContentView(wrap(context, title, content, buttons))
            show(dialog)
        })
    }

    fun showCustom(context: Context, title: String, factory: ContentFactory) {
        Handler(Looper.getMainLooper()).post(Runnable {
            val dialog = createDialog(context)
            val content = factory.create(dialog)
            dialog.setContentView(wrap(context, title, content, null))
            show(dialog)
        })
    }

    private fun createDialog(context: Context): Dialog {
        val dialog = Dialog(context)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        return dialog
    }

    private fun createContent(context: Context?): LinearLayout {
        val content = LinearLayout(context)
        content.setOrientation(LinearLayout.VERTICAL)
        content.setPadding(0, 0, 0, 0)
        return content
    }

    private fun wrap(context: Context, title: String, content: View?, buttons: View?): View {
        val root = FrameLayout(context)
        root.setPadding(
            dp(context, 18f),
            dp(context, 18f),
            dp(context, 18f),
            dp(context, 18f)
        )

        val container = FrameLayout(context)

        val panel = LinearLayout(context)
        panel.setOrientation(LinearLayout.VERTICAL)
        panel.setPadding(
            dp(context, 14f),
            dp(context, 36f),
            dp(context, 14f),
            dp(context, 14f)
        )
        stylePanel(context, panel)

        val scrollView = ScrollView(context)
        scrollView.setFillViewport(false)
        scrollView.addView(
            content, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )
        panel.addView(
            scrollView, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        if (buttons != null) {
            panel.addView(buttons)
        }

        val panelParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        panelParams.topMargin = dp(context, 12f)
        container.addView(panel, panelParams)

        val header = TextView(context)
        header.setText(title.uppercase(Locale.getDefault()))
        styleHeader(context, header)
        val headerParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        headerParams.gravity = Gravity.TOP or Gravity.START
        headerParams.leftMargin = dp(context, 42f)
        container.addView(header, headerParams)

        val contentHost = addFoldAwareHost(context, root, ViewGroup.LayoutParams.WRAP_CONTENT)
        contentHost.addView(
            container, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
            )
        )

        return root
    }

    private fun buttons(
        context: Context,
        dialog: Dialog,
        positive: String,
        negative: String,
        action: ConfirmAction
    ): LinearLayout = buttons(context, dialog, positive, negative, action, true)

    private fun buttons(
        context: Context,
        dialog: Dialog,
        positive: String,
        negative: String,
        action: ConfirmAction,
        dismissOnConfirm: Boolean
    ): LinearLayout {
        val row = LinearLayout(context)
        row.setOrientation(LinearLayout.HORIZONTAL)
        row.setGravity(Gravity.CENTER_VERTICAL)
        row.setPadding(0, dp(context, 12f), 0, 0)

        val cancel = TextView(context)
        cancel.setText(negative.uppercase(Locale.getDefault()))
        styleButton(context, cancel, false)
        cancel.setOnClickListener(View.OnClickListener { v: View? -> dialog.dismiss() })
        row.addView(
            cancel, LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
            )
        )

        val spacer = View(context)
        row.addView(spacer, LinearLayout.LayoutParams(dp(context, 12f), 1))

        val confirm = TextView(context)
        confirm.setText(positive.uppercase(Locale.getDefault()))
        styleButton(context, confirm, true)
        confirm.setOnClickListener(View.OnClickListener { v: View? ->
            if (dismissOnConfirm) {
                dialog.dismiss()
            }
            action.onConfirm()
        })
        row.addView(
            confirm, LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
            )
        )

        return row
    }

    private fun show(dialog: Dialog) {
        dialog.show()
        applyFullscreen(dialog)
        val window = dialog.getWindow()
        if (window != null) {
            window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            window.setDimAmount(0f)
            val params = WindowManager.LayoutParams()
            params.copyFrom(window.getAttributes())
            params.width = WindowManager.LayoutParams.MATCH_PARENT
            params.height = WindowManager.LayoutParams.WRAP_CONTENT
            window.setAttributes(params)
        }
    }

    fun interface ItemAction {
        fun onItemSelected(index: Int)
    }

    fun interface InputAction {
        fun onInput(value: String?)
    }

    fun interface ConfirmAction {
        fun onConfirm()
    }

    fun interface FormValidator {
        fun validate(values: Map<String, String>): String?
    }

    fun interface FormAction {
        fun onSubmit(values: Map<String, String>)
    }

    fun interface ContentFactory {
        fun create(dialog: Dialog?): View?
    }
}
