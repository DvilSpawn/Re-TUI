package ohi.andre.consolelauncher.managers.lua

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.FileProvider
import ohi.andre.consolelauncher.LauncherActivity
import ohi.andre.consolelauncher.commands.tuixt.TuixtDialog
import ohi.andre.consolelauncher.commands.tuixt.TuixtTheme
import org.json.JSONObject
import java.io.File
import java.util.concurrent.Executors
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** A single render with no persistent writes or external actions. */
object LuaWidgetPreview {
    private val worker = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    internal fun render(context: Context, id: String, source: String, emptyData: Boolean): LuaWidgetEngine.RenderResult {
        LuaWidgetManager.compatibilityError(source)?.let { return LuaWidgetEngine.RenderResult.error(it, "compatibility") }
        val permissions = LuaWidgetManager.requiredPermissions(source)
        if (permissions.isNotEmpty() && (source != LuaWidgetManager.readScript(id) || !LuaWidgetManager.isTrusted(id))) {
            return LuaWidgetEngine.RenderResult.error("Save and approve this module before previewing its required capabilities.", "approval")
        }
        val engine = LuaWidgetEngine(context, id, source, 0, null, preview = true, emptyPreviewData = emptyData)
        return try { engine.render() } finally { engine.dispose() }
    }

    fun show(context: Context, id: String, source: String) {
        TuixtDialog.showCustom(context, "Module preview", TuixtDialog.ContentFactory { dialog ->
            val content = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
            val pad = TuixtTheme.dp(context, 10f)
            fun label(text: String) = TextView(context).apply {
                this.text = text; textSize = 14f; setTextColor(TuixtTheme.textColor()); setPadding(pad, pad, pad, pad)
            }
            content.addView(label("Buttons are inactive in this preview. Your saved data stays unchanged. Network, files and phone actions are unavailable."))
            val controls = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
            content.addView(controls)
            val host = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
            content.addView(host, LinearLayout.LayoutParams(-1, -2))
            val diagnostics = label("Rendering…")
            content.addView(diagnostics)
            var narrow = false
            var emptyData = false
            var generation = 0
            var renderedPanel: View? = null
            var report = ""
            var ready = false
            fun refresh() {
                ready = false
                diagnostics.text = "Rendering…"
                val current = ++generation
                val useEmptyData = emptyData
                worker.execute {
                    val result = runCatching { render(context, id, source, useEmptyData) }
                        .getOrElse { LuaWidgetEngine.RenderResult.error(it.message, "preview") }
                    main.post {
                        if (current != generation || dialog?.isShowing != true) return@post
                        host.removeAllViews()
                        val ui = LauncherActivity.instance?.uiManager
                        val panel = ui?.buildLuaPreview(id, result) ?: label(result.body.orEmpty())
                        host.addView(panel, LinearLayout.LayoutParams(-1, -2))
                        renderedPanel = panel
                        val state = if (result.error.isNullOrBlank()) "Check passed" else "${result.errorStage}: ${result.error}"
                        diagnostics.text = state + if (result.debug.isEmpty()) "" else "\n" + result.debug.joinToString("\n")
                        report = JSONObject().put("module", id).put("api", LuaWidgetManager.apiVersionFromScript(source))
                            .put("emptyData", useEmptyData).put("width", if (narrow) "240dp" else "device")
                            .put("title", result.title).put("body", result.body).put("layout", result.layoutJson)
                            .put("error", result.error).put("stage", result.errorStage)
                            .put("debug", result.debug.joinToString("\n")).toString(2)
                        ready = true
                    }
                }
            }
            fun control(title: String, action: (TextView) -> Unit) {
                controls.addView(label(title).apply {
                    TuixtTheme.styleButton(context, this, false)
                    setOnClickListener { action(this) }
                }, LinearLayout.LayoutParams(0, -2, 1f))
            }
            control("Width: device") { button ->
                narrow = !narrow
                button.text = if (narrow) "Width: 240dp" else "Width: device"
                host.layoutParams = LinearLayout.LayoutParams(if (narrow) TuixtTheme.dp(context, 240f) else -1, -2)
                refresh()
            }
            control("Data: saved") { button ->
                emptyData = !emptyData
                button.text = if (emptyData) "Data: empty" else "Data: saved"
                refresh()
            }
            content.addView(label("Export preview + diagnostics").apply {
                TuixtTheme.styleButton(context, this, false)
                setOnClickListener {
                    if (!ready) return@setOnClickListener
                    val panel = renderedPanel ?: return@setOnClickListener
                    try {
                        val files = export(context, panel, report)
                        val uri = FileProvider.getUriForFile(context, context.packageName + ".FILE_PROVIDER", files)
                        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                            type = "application/zip"; putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            clipData = android.content.ClipData.newUri(context.contentResolver, "Module preview", uri)
                        }, "Share module preview"))
                    } catch (error: Exception) {
                        Toast.makeText(context, "Could not export preview: ${error.message}", Toast.LENGTH_LONG).show()
                    }
                }
            })
            content.addView(label("Close").apply {
                TuixtTheme.styleButton(context, this, false)
                setOnClickListener { generation++; dialog?.dismiss() }
            })
            // showCustom attaches and shows the dialog after this factory returns.
            main.post { refresh() }
            content
        })
    }

    internal fun export(context: Context, panel: View, diagnostics: String): File {
        check(panel.width > 0 && panel.height > 0) { "Preview is not ready" }
        require(panel.width.toLong() * panel.height <= 8_000_000) { "Preview is too large to export" }
        val bitmap = Bitmap.createBitmap(panel.width, panel.height, Bitmap.Config.ARGB_8888)
        panel.draw(Canvas(bitmap))
        val dir = File(context.cacheDir, "module-previews").apply { mkdirs() }
        val output = File(dir, "preview-${System.currentTimeMillis()}.zip")
        try {
            ZipOutputStream(output.outputStream()).use { zip ->
                zip.putNextEntry(ZipEntry("preview.png")); bitmap.compress(Bitmap.CompressFormat.PNG, 100, zip); zip.closeEntry()
                zip.putNextEntry(ZipEntry("diagnostics.json")); zip.write(diagnostics.toByteArray(Charsets.UTF_8)); zip.closeEntry()
            }
        } finally { bitmap.recycle() }
        return output
    }
}
