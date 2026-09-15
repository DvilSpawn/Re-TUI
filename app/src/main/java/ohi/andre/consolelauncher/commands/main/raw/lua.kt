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
import ohi.andre.consolelauncher.managers.lua.LuaWidgetEngine
import ohi.andre.consolelauncher.managers.lua.LuaWidgetManager
import ohi.andre.consolelauncher.managers.lua.LuaWidgetManager.TrustStatus
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
            return listApps(pack.context)
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
            if (TextUtils.isEmpty(id)) return pack.context.getString(R.string.command_lua_invalid_lua_app_id_7476c)
            if (!LuaWidgetManager.exists(id)) {
                LuaWidgetManager.save(id, id, LuaWidgetManager.newAppTemplate(id))
            }
            openEditor(pack, id)
            return pack.context.getString(R.string.command_lua_opening_lua_app_editor_d1612, formatApp(id))
        }
        if ("config" == option || "prefs" == option) {
            if (args.size < 2) return pack.context.getString(R.string.help_lua)
            val id = LuaWidgetManager.normalizeId(args.get(1))
            val blocked = validateApp(pack.context, id)
            if (blocked != null) return blocked
            if (!LuaWidgetManager.hasConfig(id)) return pack.context.getString(R.string.command_lua_no_config_surface_92c6f, formatApp(id))
            openConfig(pack, id)
            return pack.context.getString(R.string.command_lua_opening_lua_app_config_301f2, formatApp(id))
        }
        if ("check" == option) {
            if (args.size < 2) return pack.context.getString(R.string.help_lua)
            return checkApp(pack, LuaWidgetManager.normalizeId(args.get(1)))
        }
        if ("info" == option || "app-info" == option) {
            if (args.size < 2) return pack.context.getString(R.string.help_lua)
            return appInfo(pack.context, LuaWidgetManager.normalizeId(args.get(1)))
        }
        if ("approve" == option || "trust" == option) {
            if (args.size < 2) return pack.context.getString(R.string.help_lua)
            val id = LuaWidgetManager.normalizeId(args.get(1))
            if (!LuaWidgetManager.exists(id)) return pack.context.getString(R.string.command_lua_unknown_lua_app_e937e, id)
            LuaWidgetManager.approve(id)
            return (pack.context.getString(R.string.command_lua_lua_app_approved_permissions_446e4, formatApp(id), LuaWidgetManager.describeRequiredPermissions(LuaWidgetManager.readScript(id))))
        }
        if ("disable" == option || "enable" == option) {
            if (args.size < 2) return pack.context.getString(R.string.help_lua)
            val id = LuaWidgetManager.normalizeId(args.get(1))
            if (!LuaWidgetManager.exists(id)) return pack.context.getString(R.string.command_lua_unknown_lua_app_e937e, id)
            val enabled = "enable" == option
            LuaWidgetManager.setEnabled(id, enabled)
            return pack.context.getString(R.string.command_lua_lua_app_76bea, (if (enabled) pack.context.getString(R.string.command_lua_enabled_2d378) else pack.context.getString(R.string.command_lua_disabled_de50b)), formatApp(id))
        }
        if ("export" == option) {
            if (args.size < 2) return pack.context.getString(R.string.help_lua)
            val id = LuaWidgetManager.normalizeId(args.get(1))
            if (!LuaWidgetManager.exists(id)) return pack.context.getString(R.string.command_lua_unknown_lua_app_e937e, id)
            copyToClipboard(pack.context, LuaWidgetManager.exportPackage(id))
            return pack.context.getString(R.string.command_lua_lua_app_package_copied_to_clipboard_6c6dc, formatApp(id))
        }
        if ("rm" == option || "remove" == option) {
            if (args.size < 2) return pack.context.getString(R.string.help_lua)
            val id = LuaWidgetManager.normalizeId(args.get(1))
            val label = formatApp(id)
            LuaWidgetManager.delete(id)
            ModuleManager.removeScriptModule(pack.context, id)
            return pack.context.getString(R.string.command_lua_lua_app_removed_f973e, label)
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
            return pack.context.getString(R.string.command_lua_invalid_lua_app_id_7476c)
        }
        if (!LuaWidgetManager.exists(id)) {
            LuaWidgetManager.save(id, requestedName, LuaWidgetManager.newAppTemplate(id))
        }
        openEditor(pack, id)
        return pack.context.getString(R.string.command_lua_lua_app_created_64c8a, formatApp(id))
    }

    private fun openLuaApp(pack: ExecutePack, args: MutableList<String?>): String? {
        if (args.size < 2) return pack.context.getString(R.string.help_lua)
        val id = LuaWidgetManager.normalizeId(args.get(1))
        val blocked = validateApp(pack.context, id)
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
        val blocked = validateApp(pack.context, id)
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
            return (pack.context.getString(R.string.command_lua_lua_app_check_failed_use_lua_edit_to_updat_4ae1d, formatApp(id), (if (TextUtils.isEmpty(result.errorStage)) "" else pack.context.getString(R.string.command_lua_stage_76cf8, result.errorStage)), result.error, id))
        }
        return (pack.context.getString(R.string.command_lua_lua_app_check_ok_capabilities_permissions_1a3e2, formatApp(id), LuaWidgetManager.describeCapabilities(LuaWidgetManager.readScript(id)), LuaWidgetManager.describeRequiredPermissions(LuaWidgetManager.readScript(id)), LuaWidgetManager.apiVersion(id), (if (TextUtils.isEmpty(result.title)) LuaWidgetManager.getName(id) else result.title), (result.buttons.size + result.valueActions.size + result.commands.size)))
    }

    private fun appInfo(context: Context, id: String?): String {
        if (!LuaWidgetManager.exists(id)) return context.getString(R.string.lua_detail_lua_unknown_lua_app_e937e, id)
        val trust = LuaWidgetManager.trustStatus(id)
        val meta = LuaWidgetManager.metadata(LuaWidgetManager.readScript(id))
        return (context.getString(R.string.lua_detail_lua_lua_app_type_capabilities_permissions_trus_09614, formatApp(id), LuaWidgetManager.getScriptType(id), LuaWidgetManager.describeCapabilities(LuaWidgetManager.readScript(id)), LuaWidgetManager.describeRequiredPermissions(LuaWidgetManager.readScript(id)), (if (trust.trusted) context.getString(R.string.lua_detail_lua_approved_c9560) else context.getString(R.string.lua_detail_lua_needs_approval_e0c3e)), LuaWidgetManager.apiVersion(id), (if (LuaWidgetManager.isEnabled(id)) context.getString(R.string.lua_detail_lua_enabled_3ea3f) else context.getString(R.string.lua_detail_lua_disabled_07596)), valueOr(meta.get("description"), context.getString(R.string.lua_detail_lua_none_71f8e)), valueOr(meta.get("author"), context.getString(R.string.lua_detail_lua_unknown_50d8b)), valueOr(meta.get("version"), context.getString(R.string.lua_detail_lua_none_71f8e))))
    }

    private fun listApps(context: Context): String {
        val ids = ArrayList<String?>()
        for (id in LuaWidgetManager.listIds()) {
            if ("app" == LuaWidgetManager.getScriptType(id)) {
                ids.add(id)
            }
        }
        return (context.getString(R.string.lua_detail_lua_lua_apps_use_lua_new_app_name_lua_app_id_l_55ff9, (if (ids.isEmpty()) context.getString(R.string.lua_detail_lua_none_71f8e) else formatApps(ids))))
    }

    private fun validateApp(context: Context, id: String?): String? {
        if (TextUtils.isEmpty(id)) return context.getString(R.string.lua_detail_lua_invalid_lua_app_id_7476c)
        if (!LuaWidgetManager.exists(id)) return context.getString(R.string.lua_detail_lua_unknown_lua_app_e937e, id)
        if ("app" != LuaWidgetManager.getScriptType(id)) {
            return context.getString(R.string.lua_detail_lua_script_is_not_a_lua_app_use_lua_new_app_na_1623c, formatApp(id))
        }
        if (!LuaWidgetManager.isEnabled(id)) return context.getString(R.string.lua_detail_lua_lua_app_disabled_use_lua_enable_8bf60, formatApp(id), id)
        val trust = LuaWidgetManager.trustStatus(id)
        if (!trust.trusted) {
            return trustSummary(context, context.getString(R.string.lua_detail_lua_lua_app_blocked_c1521), id, trust)
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

    private fun trustSummary(context: Context, prefix: String, id: String?, trust: TrustStatus): String {
        val out = StringBuilder(prefix).append(": ").append(formatApp(id))
        out.append(context.getString(R.string.lua_detail_lua_permissions_f56cd))
            .append(if (trust.requiredPermissions.isEmpty()) context.getString(R.string.lua_detail_lua_none_71f8e) else TextUtils.join(", ", trust.requiredPermissions))
        if (!trust.missingDeclarations.isEmpty()) {
            out.append(context.getString(R.string.lua_detail_lua_declare_first_9cbcd)).append(TextUtils.join(", ", trust.missingDeclarations))
        }
        if (!trust.unsupportedPermissions.isEmpty()) {
            out.append(context.getString(R.string.lua_detail_lua_unsupported_50a59)).append(TextUtils.join(", ", trust.unsupportedPermissions))
        }
        if (trust.canApprove()) {
            out.append(context.getString(R.string.lua_approve_app_instruction, LuaWidgetManager.normalizeId(id)))
        } else {
            out.append(context.getString(R.string.lua_detail_lua_edit_the_script_metadata_before_approval_bb65e))
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
        manager?.setPrimaryClip(ClipData.newPlainText(context.getString(R.string.command_lua_lua_app_d0263), text))
    }

    private fun cleanOption(value: String?): String {
        return if (value == null) "" else value.trim { it <= ' ' }
            .removePrefix("-")
            .lowercase(Locale.ROOT)
    }

    override fun argType(): IntArray = intArrayOf(CommandAbstraction.PLAIN_TEXT)

    override fun priority(): Int = 3

    override fun helpRes(): Int = R.string.help_lua

    override fun onArgNotFound(pack: ExecutePack, indexNotFound: Int): String = pack.context.getString(R.string.help_lua)

    override fun onNotArgEnough(pack: ExecutePack, nArgs: Int): String = listApps(pack.context)
}
