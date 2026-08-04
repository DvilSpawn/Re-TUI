package ohi.andre.consolelauncher.commands.main.raw

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.UIManager
import ohi.andre.consolelauncher.commands.CommandAbstraction
import ohi.andre.consolelauncher.commands.ExecutePack
import ohi.andre.consolelauncher.commands.tuixt.WidgetConfigActivity
import ohi.andre.consolelauncher.commands.tuixt.WidgetEditorActivity
import ohi.andre.consolelauncher.managers.modules.ModuleManager
import ohi.andre.consolelauncher.managers.widgets.LuaWidgetEngine
import ohi.andre.consolelauncher.managers.widgets.LuaWidgetManager
import ohi.andre.consolelauncher.managers.widgets.LuaWidgetManager.TrustStatus
import ohi.andre.consolelauncher.tuils.Tuils
import java.util.Locale

class lua : CommandAbstraction {
    override fun exec(pack: ExecutePack): String? {
        val arg = pack.get(Any::class.java, 0)
        val input = if (arg == null) "" else arg.toString().trim { it <= ' ' }
        if (input.length == 0
            || "-apps".equals(input, ignoreCase = true)
            || "apps".equals(input, ignoreCase = true)
            || "-ls".equals(input, ignoreCase = true)
            || "ls".equals(input, ignoreCase = true)
        ) {
            return listApps()
        }

        val args = Tuils.splitArgs(input)
        val option = cleanOption(args.get(0))
        if ("new" == option || "create" == option) {
            return createLuaApp(pack, args)
        }
        if ("app" == option || "open" == option || "show" == option) {
            return openLuaApp(pack, args)
        }
        if ("edit" == option) {
            if (args.size < 2) return pack.context.getString(R.string.help_lua)
            val id = LuaWidgetManager.normalizeId(args.get(1))
            if (TextUtils.isEmpty(id)) return "Invalid Lua app id."
            if (!LuaWidgetManager.exists(id)) {
                LuaWidgetManager.save(id, id, LuaWidgetManager.newAppTemplate(id))
            }
            openEditor(pack, id)
            return "Opening Lua app editor: " + formatApp(id)
        }
        if ("config" == option || "prefs" == option) {
            if (args.size < 2) return pack.context.getString(R.string.help_lua)
            val id = LuaWidgetManager.normalizeId(args.get(1))
            val blocked = validateApp(id)
            if (blocked != null) return blocked
            if (!LuaWidgetManager.hasConfig(id)) return "No config surface: " + formatApp(id)
            openConfig(pack, id)
            return "Opening Lua app config: " + formatApp(id)
        }
        if ("check" == option) {
            if (args.size < 2) return pack.context.getString(R.string.help_lua)
            return checkApp(pack, LuaWidgetManager.normalizeId(args.get(1)))
        }
        if ("info" == option || "app-info" == option) {
            if (args.size < 2) return pack.context.getString(R.string.help_lua)
            return appInfo(LuaWidgetManager.normalizeId(args.get(1)))
        }
        if ("approve" == option || "trust" == option) {
            if (args.size < 2) return pack.context.getString(R.string.help_lua)
            val id = LuaWidgetManager.normalizeId(args.get(1))
            if (!LuaWidgetManager.exists(id)) return "Unknown Lua app: " + id
            LuaWidgetManager.approve(id)
            return ("Lua app approved: " + formatApp(id)
                    + "\nPermissions: " + LuaWidgetManager.describeRequiredPermissions(LuaWidgetManager.readScript(id)))
        }
        if ("disable" == option || "enable" == option) {
            if (args.size < 2) return pack.context.getString(R.string.help_lua)
            val id = LuaWidgetManager.normalizeId(args.get(1))
            if (!LuaWidgetManager.exists(id)) return "Unknown Lua app: " + id
            val enabled = "enable" == option
            LuaWidgetManager.setEnabled(id, enabled)
            return "Lua app " + (if (enabled) "enabled: " else "disabled: ") + formatApp(id)
        }
        if ("export" == option) {
            if (args.size < 2) return pack.context.getString(R.string.help_lua)
            val id = LuaWidgetManager.normalizeId(args.get(1))
            if (!LuaWidgetManager.exists(id)) return "Unknown Lua app: " + id
            copyToClipboard(pack.context, LuaWidgetManager.exportPackage(id))
            return "Lua app package copied to clipboard: " + formatApp(id)
        }
        if ("rm" == option || "remove" == option) {
            if (args.size < 2) return pack.context.getString(R.string.help_lua)
            val id = LuaWidgetManager.normalizeId(args.get(1))
            val label = formatApp(id)
            LuaWidgetManager.delete(id)
            ModuleManager.removeScriptModule(pack.context, id)
            return "Lua app removed: " + label
        }
        return pack.context.getString(R.string.output_invalid_param) + " " + args.get(0)
    }

    private fun createLuaApp(pack: ExecutePack, args: MutableList<String?>): String? {
        if (args.size < 3 || !"app".equals(args.get(1), ignoreCase = true)) {
            return pack.context.getString(R.string.help_lua)
        }
        val requestedName = TextUtils.join(" ", args.subList(2, args.size)).trim { it <= ' ' }
        val id = LuaWidgetManager.idFromName(requestedName)
        if (TextUtils.isEmpty(id)) {
            return "Invalid Lua app id."
        }
        if (!LuaWidgetManager.exists(id)) {
            LuaWidgetManager.save(id, requestedName, LuaWidgetManager.newAppTemplate(id))
        }
        openEditor(pack, id)
        return "Lua app created: " + formatApp(id)
    }

    private fun openLuaApp(pack: ExecutePack, args: MutableList<String?>): String? {
        if (args.size < 2) return pack.context.getString(R.string.help_lua)
        val id = LuaWidgetManager.normalizeId(args.get(1))
        val blocked = validateApp(id)
        if (blocked != null) return blocked

        val intent = Intent(UIManager.ACTION_LUA_APP)
        intent.putExtra(UIManager.EXTRA_LUA_APP_ID, id)
        Handler(Looper.getMainLooper()).post {
            LocalBroadcastManager
                .getInstance(pack.context.applicationContext)
                .sendBroadcast(intent)
        }
        return null
    }

    private fun checkApp(pack: ExecutePack, id: String?): String {
        val blocked = validateApp(id)
        if (blocked != null) return blocked
        val engine = LuaWidgetEngine(
            pack.context,
            id,
            LuaWidgetManager.readScript(id),
            LuaWidgetManager.version(id),
            null
        )
        val result = engine.open()
        if (!TextUtils.isEmpty(result.error)) {
            return ("Lua app check failed: " + formatApp(id)
                    + (if (TextUtils.isEmpty(result.errorStage)) "" else "\nStage: " + result.errorStage)
                    + "\n" + result.error
                    + "\nUse lua -edit " + id + " to update it.")
        }
        return ("Lua app check OK: " + formatApp(id)
                + "\nCapabilities: " + LuaWidgetManager.describeCapabilities(LuaWidgetManager.readScript(id))
                + "\nPermissions: " + LuaWidgetManager.describeRequiredPermissions(LuaWidgetManager.readScript(id))
                + "\nTrust: approved"
                + "\nRuntime: API " + LuaWidgetManager.apiVersion(id)
                + "\nTitle: " + (if (TextUtils.isEmpty(result.title)) LuaWidgetManager.getName(id) else result.title)
                + "\nActions: " + (result.buttons.size + result.valueActions.size + result.commands.size))
    }

    private fun appInfo(id: String?): String {
        if (!LuaWidgetManager.exists(id)) return "Unknown Lua app: " + id
        val trust = LuaWidgetManager.trustStatus(id)
        val meta = LuaWidgetManager.metadata(LuaWidgetManager.readScript(id))
        return ("Lua app: " + formatApp(id)
                + "\nType: " + LuaWidgetManager.getScriptType(id)
                + "\nCapabilities: " + LuaWidgetManager.describeCapabilities(LuaWidgetManager.readScript(id))
                + "\nPermissions: " + LuaWidgetManager.describeRequiredPermissions(LuaWidgetManager.readScript(id))
                + "\nTrust: " + (if (trust.trusted) "approved" else "needs approval")
                + "\nRuntime: API " + LuaWidgetManager.apiVersion(id)
                + "\nState: " + (if (LuaWidgetManager.isEnabled(id)) "enabled" else "disabled")
                + "\nDescription: " + valueOr(meta.get("description"), "none")
                + "\nAuthor: " + valueOr(meta.get("author"), "unknown")
                + "\nVersion: " + valueOr(meta.get("version"), "none"))
    }

    private fun listApps(): String {
        val ids = ArrayList<String?>()
        for (id in LuaWidgetManager.listIds()) {
            if ("app" == LuaWidgetManager.getScriptType(id)) {
                ids.add(id)
            }
        }
        return ("Lua apps: " + (if (ids.isEmpty()) "none" else formatApps(ids))
                + "\nUse lua -new app [name], lua -app [id], lua -edit [id], lua -check [id], lua -info [id], lua -approve [id], lua -config [id], lua -export [id], lua -disable|-enable [id].")
    }

    private fun validateApp(id: String?): String? {
        if (TextUtils.isEmpty(id)) return "Invalid Lua app id."
        if (!LuaWidgetManager.exists(id)) return "Unknown Lua app: " + id
        if ("app" != LuaWidgetManager.getScriptType(id)) {
            return "Script is not a Lua app: " + formatApp(id) + "\nUse lua -new app [name] for the app surface."
        }
        if (!LuaWidgetManager.isEnabled(id)) return "Lua app disabled: " + formatApp(id) + "\nUse lua -enable " + id + "."
        val trust = LuaWidgetManager.trustStatus(id)
        if (!trust.trusted) {
            return trustSummary("Lua app blocked", id, trust)
        }
        return null
    }

    private fun formatApps(ids: MutableList<String?>): String {
        val out = StringBuilder()
        for (id in ids) {
            if (out.length > 0) out.append(", ")
            out.append(formatApp(id))
        }
        return out.toString()
    }

    private fun formatApp(id: String?): String? {
        val label = LuaWidgetManager.getName(id)
        return if (TextUtils.equals(label, id)) id else label + " (" + id + ")"
    }

    private fun valueOr(value: String?, fallback: String?): String? {
        return if (TextUtils.isEmpty(value)) fallback else value
    }

    private fun trustSummary(prefix: String, id: String?, trust: TrustStatus): String {
        val out = StringBuilder(prefix).append(": ").append(formatApp(id))
        out.append("\nPermissions: ")
            .append(if (trust.requiredPermissions.isEmpty()) "none" else TextUtils.join(", ", trust.requiredPermissions))
        if (!trust.missingDeclarations.isEmpty()) {
            out.append("\nDeclare first: ").append(TextUtils.join(", ", trust.missingDeclarations))
        }
        if (!trust.unsupportedPermissions.isEmpty()) {
            out.append("\nUnsupported: ").append(TextUtils.join(", ", trust.unsupportedPermissions))
        }
        if (trust.canApprove()) {
            out.append("\nUse lua -approve ").append(LuaWidgetManager.normalizeId(id)).append(" to allow this app.")
        } else {
            out.append("\nEdit the script metadata before approval.")
        }
        return out.toString()
    }

    private fun openEditor(pack: ExecutePack, id: String?) {
        WidgetEditorActivity.openWidget(pack.context, id)
    }

    private fun openConfig(pack: ExecutePack, id: String?) {
        val intent = Intent(pack.context, WidgetConfigActivity::class.java)
        intent.putExtra(WidgetConfigActivity.EXTRA_WIDGET_ID, id)
        (pack.context as Activity).startActivity(intent)
    }

    private fun copyToClipboard(context: Context, text: String?) {
        val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager?
        manager?.setPrimaryClip(ClipData.newPlainText("Lua app", text))
    }

    private fun cleanOption(value: String?): String {
        return if (value == null) "" else value.trim { it <= ' ' }
            .removePrefix("-")
            .lowercase(Locale.getDefault())
    }

    override fun argType(): IntArray = intArrayOf(CommandAbstraction.PLAIN_TEXT)

    override fun priority(): Int = 3

    override fun helpRes(): Int = R.string.help_lua

    override fun onArgNotFound(pack: ExecutePack, indexNotFound: Int): String = pack.context.getString(R.string.help_lua)

    override fun onNotArgEnough(pack: ExecutePack, nArgs: Int): String = listApps()
}
