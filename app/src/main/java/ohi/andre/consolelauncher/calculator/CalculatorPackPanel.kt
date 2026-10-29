package ohi.andre.consolelauncher.calculator

import android.app.Activity
import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import ohi.andre.consolelauncher.BuildConfig
import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.commands.tuixt.TuixtDialog
import ohi.andre.consolelauncher.commands.tuixt.TuixtTheme
import ohi.andre.consolelauncher.managers.settings.LauncherSettings
import ohi.andre.consolelauncher.managers.settings.ThemeColorResolver
import ohi.andre.consolelauncher.managers.xml.options.Behavior
import ohi.andre.consolelauncher.managers.xml.options.Theme
import ohi.andre.consolelauncher.tuils.LauncherFontScale
import ohi.andre.consolelauncher.tuils.Tuils
import java.io.Closeable
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference

/** One installer UI for the calculator card and Android's exported file-opening activity. */
internal class CalculatorPackPanel(
    private val activity: Activity,
    private val inline: Boolean = false,
    private val onChanged: () -> Unit = {},
    private val onDismiss: () -> Unit = { activity.finish() }
) {
    private val context = activity.applicationContext
    private val worker = Executors.newSingleThreadExecutor()
    private val source = AtomicReference<Closeable?>()
    @Volatile private var cancelled = false
    @Volatile private var leaving = false
    @Volatile private var probeRequest: CalculatorEngine.Request? = null
    private var busy = false
    val isBusy get() = busy
    private var message: String? = null
    val view = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        tag = "calculator-enhancement-panel"
        val inset = if (inline) Tuils.dpToPx(activity, 8) else 0
        setPadding(inset, inset, inset, inset)
    }
    private val title = TextView(activity).apply {
        setText(if (inline) R.string.optional_calculator_inline_title else R.string.optional_calculator_title)
        setTypeface(Tuils.getTypeface(activity), Typeface.BOLD)
    }
    private val status = TextView(activity).apply { setTextIsSelectable(true) }
    private val progress = ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal).apply {
        tag = "calculator-enhancement-progress"
        visibility = View.GONE
    }
    private val actions = LinearLayout(activity).apply { orientation = if (inline) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL }
    private val dismiss = TextView(activity).apply {
        text = "×"
        contentDescription = activity.getString(R.string.optional_calculator_not_now)
        gravity = Gravity.CENTER
        minWidth = Tuils.dpToPx(activity, 48)
        minHeight = Tuils.dpToPx(activity, 48)
        isClickable = true; isFocusable = true
        setOnClickListener { if (!busy) onDismiss() }
    }

    init {
        if (inline) view.addView(LinearLayout(activity).apply {
            gravity = Gravity.CENTER_VERTICAL
            addView(title, LinearLayout.LayoutParams(0, -2, 1f))
            addView(dismiss, LinearLayout.LayoutParams(-2, -2))
        }) else view.addView(title)
        view.addView(status)
        view.addView(progress, LinearLayout.LayoutParams(-1, Tuils.dpToPx(activity, 8)))
        view.addView(actions)
        refresh()
    }

    private fun button(text: Int, click: () -> Unit) {
        actions.addView(TextView(activity).apply {
            setText(text); setAllCaps(true)
            tag = "calculator-enhancement-action-$text"
            minHeight = Tuils.dpToPx(activity, 48)
            isClickable = true; isFocusable = true
            TuixtTheme.styleButton(activity, this, text == R.string.optional_calculator_download)
            if (inline) setPadding(Tuils.dpToPx(activity, 6), 0, Tuils.dpToPx(activity, 6), 0)
            setOnClickListener { click() }
        }, if (inline) LinearLayout.LayoutParams(0, -2, 1f) else LinearLayout.LayoutParams(-1, -2))
    }

    fun refresh() {
        if (leaving) return
        actions.removeAllViews()
        val version = CalculatorPackStore.installedVersion(context)
        status.text = message ?: if (version != null) activity.getString(R.string.optional_calculator_pack_installed, version)
            else activity.getString(if (inline) R.string.optional_calculator_inline_description else R.string.optional_calculator_missing_description)
        dismiss.visibility = if (busy) View.GONE else View.VISIBLE
        if (busy) {
            button(R.string.optional_calculator_cancel) { cancelOperation() }
            refreshTheme()
            return
        }
        progress.visibility = View.GONE
        if (CalculatorEngine.supported()) {
            button(if (version == null) R.string.optional_calculator_download else R.string.optional_calculator_update) {
                if (BuildConfig.CALCULATOR_ENGINE_CATALOG_URL.isBlank()) {
                    showError(activity.getString(R.string.optional_calculator_unpublished_description))
                } else operate {
                    CalculatorPackStore.downloadAndInstall(context, BuildConfig.CALCULATOR_ENGINE_CATALOG_URL,
                        { cancelled }, ::reportProgress)
                    verifyInstalledEngine()
                }
            }
            button(R.string.optional_calculator_import) {
                try {
                    @Suppress("DEPRECATION")
                    activity.startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                        addCategory(Intent.CATEGORY_OPENABLE); type = "*/*"
                        putExtra(Intent.EXTRA_MIME_TYPES, arrayOf(CalculatorPackStore.MIME, "application/zip", "application/octet-stream"))
                    }, PICK_ENGINE)
                } catch (_: Exception) { showError(activity.getString(R.string.optional_calculator_picker_unavailable)) }
            }
            if (version != null && !inline) {
                button(if (LauncherSettings.getBoolean(Behavior.qalculate)) R.string.optional_calculator_use_basic else R.string.optional_calculator_enable) {
                    LauncherSettings.set(context, Behavior.qalculate, (!LauncherSettings.getBoolean(Behavior.qalculate)).toString())
                    WasmCalculatorRuntime.reset(); message = null; refresh(); onChanged()
                }
                button(R.string.optional_calculator_remove) {
                    TuixtDialog.showConfirm(activity, activity.getString(R.string.optional_calculator_remove),
                        activity.getString(R.string.optional_calculator_remove_description), activity.getString(R.string.optional_calculator_remove),
                        activity.getString(R.string.optional_calculator_cancel), TuixtDialog.ConfirmAction {
                            operate(R.string.optional_calculator_removed) { WasmCalculatorRuntime.reset(); CalculatorPackStore.remove(context) }
                        })
                }
            }
        } else status.setText(R.string.optional_calculator_unsupported_description)
        if (!inline) button(R.string.optional_calculator_close) { onDismiss() }
        refreshTheme()
    }

    fun refreshTheme() {
        val color = if (inline) ThemeColorResolver.color(Theme.workspace_panel_text_color) else TuixtTheme.textColor()
        title.setTextColor(color); status.setTextColor(color); dismiss.setTextColor(color)
        title.typeface = Typeface.create(Tuils.getTypeface(activity), Typeface.BOLD)
        status.typeface = Tuils.getTypeface(activity)
        title.textSize = if (inline) LauncherFontScale.terminalTextSizeSp() else 20f
        status.textSize = if (inline) LauncherFontScale.terminalTextSizeSp() * 0.9f else 14f
        dismiss.textSize = LauncherFontScale.terminalTextSizeSp() * 1.4f
        if (inline) for (index in 0 until actions.childCount) {
            val action = actions.getChildAt(index) as TextView
            TuixtTheme.styleButton(activity, action, action.tag == "calculator-enhancement-action-${R.string.optional_calculator_download}")
            action.textSize = LauncherFontScale.terminalTextSizeSp() * 0.9f
            action.setPadding(Tuils.dpToPx(activity, 6), 0, Tuils.dpToPx(activity, 6), 0)
        }
    }

    fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?): Boolean {
        if (requestCode != PICK_ENGINE) return false
        if (resultCode == Activity.RESULT_OK) try { confirmImport(requireNotNull(data?.data)) }
        catch (error: Exception) { showError(error.message ?: activity.getString(R.string.optional_calculator_invalid_file)) }
        return true
    }

    fun confirmImport(uri: Uri) {
        require(CalculatorEngine.supported()) { activity.getString(R.string.optional_calculator_unsupported_description) }
        require(uri.scheme == "content" && !uri.authority.isNullOrBlank() && !uri.authority!!.contains('@')) {
            activity.getString(R.string.optional_calculator_invalid_file)
        }
        // The exported importer must never lend its own private provider access to a caller.
        val provider = activity.packageManager.resolveContentProvider(uri.authority!!, 0)
        require(provider != null && provider.applicationInfo.uid != activity.applicationInfo.uid) {
            activity.getString(R.string.optional_calculator_invalid_file)
        }
        TuixtDialog.showConfirm(activity, activity.getString(R.string.optional_calculator_import),
            activity.getString(R.string.optional_calculator_import_description), activity.getString(R.string.optional_calculator_install),
            activity.getString(R.string.optional_calculator_cancel), TuixtDialog.ConfirmAction {
                operate {
                    val input = requireNotNull(activity.contentResolver.openInputStream(uri))
                    source.set(input)
                    try { CalculatorPackStore.install(context, input, { cancelled }, ::reportProgress) }
                    finally { source.compareAndSet(input, null); input.close() }
                    verifyInstalledEngine()
                }
            })
    }

    fun showError(text: String) { message = text; refresh() }

    private fun verifyInstalledEngine() {
        val installed = requireNotNull(CalculatorPackStore.activeDirectory(context))
        WasmCalculatorRuntime.reset()
        val request = CalculatorEngine.Request()
        probeRequest = request
        if (cancelled) request.cancel()
        val probe = try { WasmCalculatorRuntime.evaluate(context, "2+2", CalculatorEngine.Options(true, true), request, false) }
            finally { probeRequest = null }
        if (cancelled || probe.error || probe.text != "4" || CalculatorPackStore.activeDirectory(context) != installed) {
            CalculatorPackStore.rollback(context, installed)
            WasmCalculatorRuntime.reset()
            throw IllegalArgumentException(activity.getString(R.string.calculator_engine_stopped))
        }
        LauncherSettings.set(context, Behavior.qalculate, "true")
    }

    private fun reportProgress(done: Long, total: Long) {
        activity.runOnUiThread {
            if (!leaving && busy) {
                progress.isIndeterminate = total <= 0
                if (total > 0) progress.progress = ((done * 100) / total).coerceIn(0, 100).toInt()
            }
        }
    }

    private fun operate(success: Int = R.string.optional_calculator_complete, action: () -> Unit) {
        if (busy || leaving) return
        busy = true; cancelled = false; message = activity.getString(R.string.optional_calculator_working)
        refresh()
        progress.visibility = View.VISIBLE; progress.isIndeterminate = true
        worker.execute {
            val result = try { action(); activity.getString(success) }
            catch (error: Exception) { if (cancelled) activity.getString(R.string.calculator_cancelled)
                else activity.getString(R.string.optional_calculator_failed, error.message ?: error.javaClass.simpleName) }
            activity.runOnUiThread {
                busy = false
                if (!leaving) { message = result; refresh(); onChanged() }
            }
        }
    }

    private fun cancelOperation() {
        cancelled = true
        probeRequest?.cancel()
        runCatching { source.getAndSet(null)?.close() }
    }

    fun destroy() { leaving = true; cancelOperation(); worker.shutdown() }

    companion object { private const val PICK_ENGINE = 0x51c }
}
