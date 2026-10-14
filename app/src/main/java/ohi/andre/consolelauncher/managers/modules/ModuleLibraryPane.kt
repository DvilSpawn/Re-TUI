package ohi.andre.consolelauncher.managers.modules

import android.app.Activity
import android.content.Context
import android.graphics.Typeface
import android.widget.LinearLayout
import android.widget.TextView
import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.commands.tuixt.TuixtDialog
import ohi.andre.consolelauncher.commands.tuixt.TuixtTheme
import ohi.andre.consolelauncher.managers.lua.LuaWidgetManager
import ohi.andre.consolelauncher.tuils.Tuils

/** Catalogue samples are inert text: browsing never runs or installs a downloaded script. */
object ModuleLibraryPane {
    fun show(context: Context, entry: ModuleLibrary.Entry, send: (String, String?) -> Unit) {
        TuixtDialog.showCustom(context, entry.title, TuixtDialog.ContentFactory { dialog ->
            val content = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
            val pad = (12 * context.resources.displayMetrics.density).toInt()
            fun label(value: String, preview: Boolean = false) {
                content.addView(TextView(context).apply {
                    text = value; setTextColor(TuixtTheme.textColor()); textSize = if (preview) 14f else 15f
                    setPadding(pad, pad, pad, pad)
                    if (preview) { typeface = Typeface.MONOSPACE; TuixtTheme.stylePanel(context, this) }
                })
            }
            if (entry.screenshot.isNotEmpty()) {
                label("Example screenshot · publisher theme")
                val image = android.widget.ImageView(context).apply { adjustViewBounds = true; contentDescription = "${entry.title} screenshot" }
                content.addView(image, LinearLayout.LayoutParams(-1, -2))
                ModuleLibrary.screenshot(entry) { result ->
                    result.onSuccess { if (dialog?.isShowing == true) image.setImageBitmap(it) else it.recycle() }
                        .onFailure { image.contentDescription = "Screenshot unavailable" }
                }
            }
            label("Preview · sample data")
            label(entry.preview.ifBlank { "Preview unavailable" }, true)
            label(entry.details.ifBlank { entry.description })
            label("Requirements: " + entry.requirements.ifBlank { "Re:TUI widget API ${entry.api}" })
            label("Version ${entry.version} · ${entry.license}\n${entry.author}\n${ModuleLibrary.status(context, entry)}")
            label("The sample uses example data. Your theme, settings and local edits may change its appearance. Module permissions are reviewed before first use.")
            val actions = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
            content.addView(actions)
            fun button(title: String, primary: Boolean = false, action: () -> Unit) {
                actions.addView(TextView(context).apply {
                    text = title; textSize = 15f; setPadding(pad, pad, pad, pad)
                    gravity = android.view.Gravity.CENTER
                    TuixtTheme.styleButton(context, this, primary)
                    setOnClickListener { dialog?.dismiss(); action() }
                }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = pad / 2 })
            }
            fun install(replace: Boolean) {
                Tuils.sendOutput(context, if (replace) "Updating module…" else "Installing module…")
                ModuleLibrary.installAsync(context, entry, replace) { result ->
                    val activity = context as? Activity
                    if (activity?.isFinishing == true || activity?.isDestroyed == true) return@installAsync
                    result.onSuccess { id ->
                        if (replace) send("lua_reload_widget", id)
                        send("rebuild", null); send("show", id)
                    }.onFailure { Tuils.sendOutput(context, context.getString(R.string.module_library_failed, it.message)) }
                }
            }
            if (!LuaWidgetManager.exists(entry.id)) {
                if (entry.api == 1) button("Install", true) { install(false) }
                else label("Requires a newer launcher")
                button("Cancel") {}
            } else {
                button("Open", true) { send("rebuild", null); send("show", entry.id) }
                if (ModuleLibrary.hasUpdate(context, entry)) button("Update") {
                    TuixtDialog.showConfirm(context, "Update ${entry.title}?", "Replace the module script with version ${entry.version}? Any local source edits will be replaced. Saved settings and data are kept.",
                        "Update", "Cancel", TuixtDialog.ConfirmAction { install(true) })
                }
                if (LuaWidgetManager.hasConfig(entry.id)) button("Configure") {
                    if (!LuaWidgetManager.isTrusted(entry.id)) {
                        Tuils.sendOutput(context, "Approve this module before configuring it: module -approve ${entry.id}")
                    } else {
                        context.startActivity(android.content.Intent(context, ohi.andre.consolelauncher.commands.tuixt.WidgetConfigActivity::class.java)
                            .putExtra(ohi.andre.consolelauncher.commands.tuixt.WidgetConfigActivity.EXTRA_WIDGET_ID, entry.id))
                    }
                }
                button("Preview") { ohi.andre.consolelauncher.managers.lua.LuaWidgetPreview.show(context, entry.id, LuaWidgetManager.readScript(entry.id).orEmpty()) }
                button("Remove") {
                    TuixtDialog.showConfirm(context, "Remove ${entry.title}?", "Delete this module, its saved settings and data, and remove it from the dock? You can install it again from the library.",
                        "Remove", "Cancel", TuixtDialog.ConfirmAction { send("library_remove", entry.id) })
                }
                button("Close") {}
            }
            content
        })
    }
}
