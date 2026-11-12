package ohi.andre.consolelauncher.managers.ui

import android.content.Context
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.UIManager.Companion.MODULE_TEXT_FONT_MONO
import ohi.andre.consolelauncher.managers.modules.home.HomeModuleHost
import ohi.andre.consolelauncher.tuils.Tuils
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

internal class LuaSurfaceAction(val label: String, val run: Runnable)

/** Builds Lua output into views, for both Home modules and Lua apps. */
internal class LuaLayoutRenderer(
    private val host: HomeModuleHost,
    private val actionLuaApp: (String?) -> Unit,
    private val actionLuaWidget: (String?, String?) -> Unit
) {
    private val context get() = host.context

    fun renderLuaLayout(
        parent: LinearLayout,
        module: String?,
        rawJson: String?,
        appSurface: Boolean = false
    ): Boolean {
        if (TextUtils.isEmpty(rawJson)) {
            return false
        }
        try {
            val trimmed = rawJson!!.trim { it <= ' ' }
            if (trimmed.startsWith("[")) {
                renderLuaLayoutArray(parent, module, JSONArray(trimmed), appSurface)
            } else {
                renderLuaLayoutObject(parent, module, JSONObject(trimmed), appSurface)
            }
            return true
        } catch (e: Exception) {
            addLuaText(parent, context.getString(R.string.surface_layout_error_b5bcf, e.message), module)
            return true
        }
    }

    private fun renderLuaLayoutArray(
        parent: LinearLayout,
        module: String?,
        array: JSONArray,
        appSurface: Boolean
    ) {
        for (i in 0..<array.length()) {
            val item = array.opt(i)
            if (item is JSONObject) {
                renderLuaLayoutObject(parent, module, item, appSurface)
            } else if (item is JSONArray) {
                renderLuaLayoutCompact(parent, module, item, appSurface)
            } else if (item != null) {
                addLuaText(parent, item.toString(), module)
            }
        }
    }

    private fun renderLuaLayoutCompact(
        parent: LinearLayout,
        module: String?,
        array: JSONArray,
        appSurface: Boolean
    ) {
        if (array.length() == 0) {
            return
        }
        val type = array.optString(0, "text")
        val obj = JSONObject()
        obj.put("type", type)
        if ("button" == type) {
            obj.put("label", array.optString(1, ""))
            if (array.length() > 2) obj.put("command", array.optString(2, ""))
        } else {
            obj.put("text", array.optString(1, ""))
        }
        renderLuaLayoutObject(parent, module, obj, appSurface)
    }

    private fun renderLuaLayoutObject(
        parent: LinearLayout,
        module: String?,
        obj: JSONObject,
        appSurface: Boolean
    ) {
        val type = obj.optString("type", obj.optString("kind", "text")).lowercase(Locale.US)
        if ("column" == type || "container" == type) {
            val column = ohi.andre.consolelauncher.presentation.ModuleBodyPresentation.group(context,ohi.andre.consolelauncher.presentation.ModuleBodyPresentation.GroupKind.COLUMN)
            column.setOrientation(LinearLayout.VERTICAL)
            column.setLayoutParams(
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )
            parent.addView(column)
            renderLuaLayoutChildren(column, module, obj.optJSONArray("children"), appSurface)
            return
        }
        if ("row" == type) {
            val row = ohi.andre.consolelauncher.presentation.ModuleBodyPresentation.group(context,ohi.andre.consolelauncher.presentation.ModuleBodyPresentation.GroupKind.ROW)
            row.setOrientation(LinearLayout.HORIZONTAL)
            row.setLayoutParams(
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )
            parent.addView(row)
            val children = obj.optJSONArray("children")
            if (children != null) {
                for (i in 0..<children.length()) {
                    val child = children.optJSONObject(i) ?: continue
                    val childBox = ohi.andre.consolelauncher.presentation.ModuleBodyPresentation.group(context,ohi.andre.consolelauncher.presentation.ModuleBodyPresentation.GroupKind.WEIGHTED_COLUMN)
                    childBox.setOrientation(LinearLayout.VERTICAL)
                    val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                    val margin = Tuils.dpToPx(context, 3)
                    lp.setMargins(margin, margin, margin, margin)
                    childBox.setLayoutParams(lp)
                    row.addView(childBox)
                    renderLuaLayoutObject(childBox, module, child, appSurface)
                }
            }
            return
        }
        if ("button" == type || "action" == type || "command" == type || "module" == type) {
            addLuaButtonGrid(
                parent,
                mutableListOf(luaLayoutSurfaceAction(module, obj, appSurface))
            )
            return
        }
        if ("progress" == type) {
            addLuaText(parent, formatLuaLayoutProgress(obj), module, MODULE_TEXT_FONT_MONO)
            return
        }
        if ("divider" == type) {
            addLuaText(parent, "----------------", module, MODULE_TEXT_FONT_MONO)
            return
        }
        if ("pre" == type || "ascii" == type || "code" == type) {
            addLuaText(parent, obj.optString("text", obj.optString("label", "")), module, MODULE_TEXT_FONT_MONO)
            return
        }
        if ("spacer" == type) {
            val spacer = ohi.andre.consolelauncher.presentation.ModuleBodyPresentation.space(context,max(1,obj.optInt("size",8)))
            spacer.setLayoutParams(
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    Tuils.dpToPx(context, max(1, obj.optInt("size", 8)))
                )
            )
            parent.addView(spacer)
            return
        }
        val text = obj.optString("text", obj.optString("label", ""))
        val view = addLuaText(parent, text, module)
        val command = commandFromLuaLayoutObject(obj)
        if (command.isNotEmpty()) {
            view.paintFlags = view.paintFlags or android.graphics.Paint.UNDERLINE_TEXT_FLAG
            view.setOnClickListener { host.executeCommand(command) }
        }
    }

    private fun renderLuaLayoutChildren(
        parent: LinearLayout,
        module: String?,
        children: JSONArray?,
        appSurface: Boolean
    ) {
        if (children == null) {
            return
        }
        for (i in 0..<children.length()) {
            val child = children.opt(i)
            if (child is JSONObject) {
                renderLuaLayoutObject(parent, module, child, appSurface)
            } else if (child is JSONArray) {
                renderLuaLayoutCompact(parent, module, child, appSurface)
            }
        }
    }

    private fun luaLayoutSurfaceAction(
        module: String?,
        obj: JSONObject,
        appSurface: Boolean
    ): LuaSurfaceAction {
        val label = obj.optString("label", obj.optString("text", "button"))
        val localValue = obj.optString("action", obj.optString("value", label))
        val hasLocalAction = obj.has("action") || obj.has("value") || "action" == obj.optString(
            "type",
            obj.optString("kind", "")
        ).lowercase(Locale.US)
        val command = commandFromLuaLayoutObject(obj)
        return LuaSurfaceAction(label) {
            if (!TextUtils.isEmpty(command)) {
                host.executeCommand(command)
            } else if (appSurface) {
                actionLuaApp(localValue)
            } else if (hasLocalAction) {
                actionLuaWidget(module, localValue)
            }
        }
    }

    private fun commandFromLuaLayoutObject(obj: JSONObject): String {
        if (!TextUtils.isEmpty(obj.optString("command", ""))) {
            return obj.optString("command")
        }
        if (!TextUtils.isEmpty(obj.optString("module", ""))) {
            return "module -show " + obj.optString("module")
        }
        return ""
    }

    private fun formatLuaLayoutProgress(obj: JSONObject): String {
        val label = obj.optString("label", obj.optString("text", context.getString(R.string.surface_progress_1b902)))
        val value = obj.optDouble("value", obj.optDouble("progress", 0.0))
        val maxValue = obj.optDouble("max", 1.0)
        val pct = if (maxValue <= 0.0) 0.0 else min(1.0, max(0.0, value / maxValue))
        val width = max(4, min(32, obj.optInt("width", 12)))
        val filled = Math.round(pct * width).toInt()
        val out = StringBuilder(label).append(" [")
        for (i in 0..<width) {
            out.append(if (i < filled) '█' else '░')
        }
        out.append("] ").append(Math.round(pct * 100.0)).append('%')
        return out.toString()
    }

    fun addLuaBodyText(parent: LinearLayout, text: String?, module: String? = null) {
        if (TextUtils.isEmpty(text)) {
            addLuaText(parent, "", module)
            return
        }
        addLuaText(parent, text, module)
    }

    fun addLuaText(
        parent: LinearLayout,
        text: String?,
        module: String? = null,
        fontMode: String? = null
    ): TextView {
        val view = ohi.andre.consolelauncher.presentation.ModuleBodyPresentation.paragraph(context)
        view.setText(text)
        view.setTextColor(host.bodyTextColor())
        view.setTextSize(host.bodyTextSize().toFloat())
        view.setIncludeFontPadding(true)
        view.setLineSpacing(Tuils.dpToPx(context, 2).toFloat(), 1f)
        host.applyBodyTypeface(view, module, fontMode)
        view.setLayoutParams(
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )
        parent.addView(view)
        return view
    }

    fun addLuaButtonGrid(parent: LinearLayout, actions: MutableList<LuaSurfaceAction>) {
        if (actions.isEmpty()) {
            return
        }
        val topSpacer = ohi.andre.consolelauncher.presentation.ModuleBodyPresentation.space(context,8)
        topSpacer.setLayoutParams(
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                Tuils.dpToPx(context, 8)
            )
        )
        parent.addView(topSpacer)

        var row: LinearLayout? = null
        for (i in actions.indices) {
            if (i % 2 == 0) {
                row = ohi.andre.consolelauncher.presentation.ModuleBodyPresentation.group(context,ohi.andre.consolelauncher.presentation.ModuleBodyPresentation.GroupKind.ROW)
                row.setOrientation(LinearLayout.HORIZONTAL)
                row.setLayoutParams(
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                )
                parent.addView(row)
            }
            val button = luaSurfaceButton(actions[i])
            row!!.addView(button)
        }
    }

    private fun luaSurfaceButton(action: LuaSurfaceAction): TextView {
        val button = ohi.andre.consolelauncher.presentation.ModuleBodyPresentation.action(context)
        button.setText(action.label)
        button.setSingleLine(false)
        button.setGravity(Gravity.CENTER)
        button.setMinHeight(Tuils.dpToPx(context, 36))
        button.setPadding(
            Tuils.dpToPx(context, 10),
            Tuils.dpToPx(context, 7),
            Tuils.dpToPx(context, 10),
            Tuils.dpToPx(context, 7)
        )
        ohi.andre.consolelauncher.presentation.ModuleContentPresentation.action(button,
            host.appearance())
        val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        val margin = Tuils.dpToPx(context, 4)
        lp.setMargins(margin, margin, margin, margin)
        button.setLayoutParams(lp)
        button.setOnClickListener(View.OnClickListener { action.run.run() })
        return button
    }
}
