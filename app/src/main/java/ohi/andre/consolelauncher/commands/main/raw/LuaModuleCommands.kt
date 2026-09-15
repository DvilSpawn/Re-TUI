package ohi.andre.consolelauncher.commands.main.raw

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.text.TextUtils
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.UIManager
import ohi.andre.consolelauncher.commands.ExecutePack
import ohi.andre.consolelauncher.commands.tuixt.WidgetConfigActivity
import ohi.andre.consolelauncher.commands.tuixt.WidgetEditorActivity
import ohi.andre.consolelauncher.managers.modules.ModuleManager
import ohi.andre.consolelauncher.managers.lua.LuaWidgetEngine
import ohi.andre.consolelauncher.managers.lua.LuaWidgetManager
import ohi.andre.consolelauncher.managers.lua.LuaWidgetManager.TrustStatus
import ohi.andre.consolelauncher.tuils.Tuils
import java.util.Arrays
import java.util.Locale

/**
 * Lua dock-module command body used by `module`.
 * The `widget` command is retired and must not call this.
 */
internal object LuaModuleCommands {
    fun exec(pack: ExecutePack): String? {
        val arg = pack.get(Any::class.java, 0)
        val input = if (arg == null) "" else arg.toString().trim { it <= ' ' }
        if (input.length == 0 || "-ls".equals(input, ignoreCase = true)) {
            return listWidgets(pack)
        }

        val args = Tuils.splitArgs(input)
        val option = args.get(0)!!.lowercase(Locale.ROOT)

        if ("-new" == option || "-add" == option) {
            if (args.size < 2) return pack.context.getString(R.string.help_module)
            val requestedName = args.get(1)
            val id = LuaWidgetManager.idFromName(requestedName)
            if (TextUtils.isEmpty(id)) return pack.context.getString(R.string.command_luamodulecommands_invalid_lua_module_id_123ed)
            if (!LuaWidgetManager.exists(id)) {
                LuaWidgetManager.save(
                    id,
                    if (args.size > 2) args.get(2) else requestedName,
                    LuaWidgetManager.newWidgetTemplate(id)
                )
            }
            openEditor(pack, id)
            return pack.context.getString(R.string.command_luamodulecommands_lua_module_created_31196, formatWidget(id))
        }

        if ("-edit" == option) {
            if (args.size < 2) return pack.context.getString(R.string.help_module)
            val id = LuaWidgetManager.normalizeId(args.get(1))
            if (TextUtils.isEmpty(id)) return pack.context.getString(R.string.command_luamodulecommands_invalid_lua_module_id_123ed)
            if (!LuaWidgetManager.exists(id)) {
                LuaWidgetManager.save(id, id, LuaWidgetManager.newWidgetTemplate(id))
            }
            openEditor(pack, id)
            return pack.context.getString(R.string.command_luamodulecommands_opening_lua_module_editor_e9fb2, formatWidget(id))
        }

        if ("-config" == option || "-prefs" == option) {
            if (args.size < 2) return pack.context.getString(R.string.help_module)
            val id = LuaWidgetManager.normalizeId(args.get(1))
            if (TextUtils.isEmpty(id)) return pack.context.getString(R.string.command_luamodulecommands_invalid_lua_module_id_123ed)
            if (!LuaWidgetManager.exists(id)) return pack.context.getString(R.string.command_luamodulecommands_unknown_lua_module_ef4ca, id)
            if (!LuaWidgetManager.hasConfig(id)) return pack.context.getString(R.string.command_luamodulecommands_no_config_surface_92c6f, formatWidget(id))
            if (!LuaWidgetManager.isEnabled(id)) return pack.context.getString(R.string.command_luamodulecommands_lua_module_disabled_use_module_enable_3ad4f, formatWidget(id), id)
            val trust = LuaWidgetManager.trustStatus(id)
            if (!trust.trusted) {
                return trustSummary(pack.context, pack.context.getString(R.string.command_luamodulecommands_lua_module_config_blocked_41b80), id, trust)
            }
            openConfig(pack, id)
            return pack.context.getString(R.string.command_luamodulecommands_opening_lua_module_config_bf40a, formatWidget(id))
        }

        if ("-show" == option) {
            if (args.size < 2) return pack.context.getString(R.string.help_module)
            val id = LuaWidgetManager.normalizeId(args.get(1))
            if (!LuaWidgetManager.exists(id)) return pack.context.getString(R.string.command_luamodulecommands_unknown_lua_module_ef4ca, id)
            if (!LuaWidgetManager.isDockable(id)) return pack.context.getString(R.string.command_luamodulecommands_script_is_not_a_dock_module_a030a, formatWidget(
                id
            ))
            if (!LuaWidgetManager.isEnabled(id)) return pack.context.getString(R.string.command_luamodulecommands_lua_module_disabled_use_module_enable_3ad4f, formatWidget(id), id)
            ModuleManager.setScriptModule(pack.context, id, LuaWidgetManager.SOURCE_PREFIX + id)
            ModuleManager.addToDock(pack.context, Arrays.asList<String?>(id))
            send(pack, "show", id, 0)
            return pack.context.getString(R.string.command_luamodulecommands_lua_module_opened_bd9be, formatWidget(id))
        }

        if ("-refresh" == option) {
            if (args.size < 2) return pack.context.getString(R.string.help_module)
            val id = LuaWidgetManager.normalizeId(args.get(1))
            if (!LuaWidgetManager.exists(id)) return pack.context.getString(R.string.command_luamodulecommands_unknown_lua_module_ef4ca, id)
            if (!LuaWidgetManager.isDockable(id)) return pack.context.getString(R.string.command_luamodulecommands_script_is_not_a_dock_module_a030a, formatWidget(
                id
            ))
            if (!LuaWidgetManager.isEnabled(id)) return pack.context.getString(R.string.command_luamodulecommands_lua_module_disabled_use_module_enable_3ad4f, formatWidget(id), id)
            ModuleManager.setScriptModule(pack.context, id, LuaWidgetManager.SOURCE_PREFIX + id)
            send(pack, "refresh", id, 0)
            return pack.context.getString(R.string.command_luamodulecommands_lua_module_refresh_dispatched_fc516, formatWidget(id))
        }

        if ("-check" == option) {
            if (args.size < 2) return pack.context.getString(R.string.help_module)
            val id = LuaWidgetManager.normalizeId(args.get(1))
            if (!LuaWidgetManager.exists(id)) return pack.context.getString(R.string.command_luamodulecommands_unknown_lua_module_ef4ca, id)
            val trust = LuaWidgetManager.trustStatus(id)
            if (!trust.trusted) {
                return trustSummary(pack.context, pack.context.getString(R.string.command_luamodulecommands_lua_module_check_blocked_dfce4), id, trust)
            }
            val engine = LuaWidgetEngine(
                pack.context,
                id,
                LuaWidgetManager.readScript(id),
                LuaWidgetManager.version(id),
                null
            )
            val result = engine.render(true)
            if (!TextUtils.isEmpty(result.error)) {
                return (pack.context.getString(R.string.command_luamodulecommands_lua_module_check_failed_use_module_copy_er_e3e83, formatWidget(id), (if (TextUtils.isEmpty(result.errorStage)) "" else pack.context.getString(R.string.command_luamodulecommands_stage_76cf8, result.errorStage)), result.error, id, id))
            }
            return (pack.context.getString(R.string.command_luamodulecommands_lua_module_check_ok_type_capabilities_perm_a9277, formatWidget(id), LuaWidgetManager.getScriptType(id), LuaWidgetManager.describeCapabilities(
                LuaWidgetManager.readScript(
                    id
                )
            ), LuaWidgetManager.describeRequiredPermissions(
                LuaWidgetManager.readScript(id)
            ), LuaWidgetManager.apiVersion(id), (if (TextUtils.isEmpty(result.title)) LuaWidgetManager.getName(
                id
            ) else result.title), (result.buttons.size + result.valueActions.size + result.commands.size), result.suggestions.size))
        }

        if ("-info" == option) {
            if (args.size < 2) return pack.context.getString(R.string.help_module)
            val id = LuaWidgetManager.normalizeId(args.get(1))
            if (!LuaWidgetManager.exists(id)) return pack.context.getString(R.string.command_luamodulecommands_unknown_lua_module_ef4ca, id)
            val meta = LuaWidgetManager.metadata(LuaWidgetManager.readScript(id))
            val trust = LuaWidgetManager.trustStatus(id)
            return (pack.context.getString(R.string.command_luamodulecommands_lua_module_type_capabilities_permissions_t_97c7d, formatWidget(id), LuaWidgetManager.getScriptType(id), LuaWidgetManager.describeCapabilities(
                LuaWidgetManager.readScript(
                    id
                )
            ), LuaWidgetManager.describeRequiredPermissions(
                LuaWidgetManager.readScript(id)
            ), (if (trust.trusted) "approved" else pack.context.getString(R.string.command_luamodulecommands_needs_approval_e0c3e)), LuaWidgetManager.apiVersion(id), (if (LuaWidgetManager.isEnabled(id)) "enabled" else "disabled"), valueOr(meta.get("description"), "none"), valueOr(meta.get("author"), "unknown"), valueOr(meta.get("version"), "none")))
        }

        if ("-approve" == option || "-trust" == option) {
            if (args.size < 2) return pack.context.getString(R.string.help_module)
            val id = LuaWidgetManager.normalizeId(args.get(1))
            LuaWidgetManager.approve(id)
            if (LuaWidgetManager.isDockable(id)) {
                ModuleManager.setScriptModule(pack.context, id, LuaWidgetManager.SOURCE_PREFIX + id)
                send(pack, "update", id, 0)
            }
            return (pack.context.getString(R.string.command_luamodulecommands_lua_module_approved_permissions_2f15f, formatWidget(id), LuaWidgetManager.describeRequiredPermissions(
                LuaWidgetManager.readScript(id)
            )))
        }

        if ("-copy-error" == option) {
            if (args.size < 2) return pack.context.getString(R.string.help_module)
            val id = LuaWidgetManager.normalizeId(args.get(1))
            if (!LuaWidgetManager.exists(id)) return pack.context.getString(R.string.command_luamodulecommands_unknown_lua_module_ef4ca, id)
            val error = LuaWidgetManager.lastError(id)
            if (TextUtils.isEmpty(error)) return pack.context.getString(R.string.command_luamodulecommands_no_saved_lua_error_34d62, formatWidget(id))
            copyToClipboard(pack.context, error)
            return pack.context.getString(R.string.command_luamodulecommands_lua_error_copied_daae5, formatWidget(id))
        }

        if ("-disable" == option) {
            if (args.size < 2) return pack.context.getString(R.string.help_module)
            val id = LuaWidgetManager.normalizeId(args.get(1))
            LuaWidgetManager.setEnabled(id, false)
            ModuleManager.removeFromDock(pack.context, Arrays.asList<String?>(id))
            send(pack, "rebuild", null, 0)
            return pack.context.getString(R.string.command_luamodulecommands_lua_module_disabled_407cc, formatWidget(id))
        }

        if ("-enable" == option) {
            if (args.size < 2) return pack.context.getString(R.string.help_module)
            val id = LuaWidgetManager.normalizeId(args.get(1))
            LuaWidgetManager.setEnabled(id, true)
            if (LuaWidgetManager.isDockable(id)) {
                ModuleManager.setScriptModule(pack.context, id, LuaWidgetManager.SOURCE_PREFIX + id)
            }
            send(pack, "rebuild", null, 0)
            return pack.context.getString(R.string.command_luamodulecommands_lua_module_enabled_a1b01, formatWidget(id))
        }

        if ("-export" == option) {
            if (args.size < 2) return pack.context.getString(R.string.help_module)
            val id = LuaWidgetManager.normalizeId(args.get(1))
            val exported = LuaWidgetManager.exportPackage(id)
            copyToClipboard(pack.context, exported)
            return pack.context.getString(R.string.command_luamodulecommands_lua_module_package_copied_to_clipboard_334d8, formatWidget(id))
        }

        if ("-rename" == option || "-mv" == option) {
            if (args.size < 3) return pack.context.getString(R.string.help_module)
            val oldId = LuaWidgetManager.normalizeId(args.get(1))
            val newId = LuaWidgetManager.idFromName(args.get(2))
            if (TextUtils.isEmpty(newId)) return pack.context.getString(R.string.command_luamodulecommands_invalid_lua_module_id_123ed)
            if (!LuaWidgetManager.exists(oldId)) return pack.context.getString(R.string.command_luamodulecommands_unknown_lua_module_ef4ca, oldId)
            if (TextUtils.equals(oldId, newId)) return pack.context.getString(R.string.command_luamodulecommands_lua_module_id_unchanged_f0ada, formatWidget(oldId))
            if (ModuleManager.isKnown(pack.context, newId) || LuaWidgetManager.exists(newId)) {
                return pack.context.getString(R.string.command_luamodulecommands_lua_module_id_already_exists_7eb87, newId)
            }

            val oldLabel = formatWidget(oldId)
            LuaWidgetManager.rename(oldId, newId)
            ModuleManager.renameScriptModule(
                pack.context,
                oldId,
                newId,
                LuaWidgetManager.SOURCE_PREFIX + newId
            )
            if (LuaWidgetManager.isDockable(newId)) {
                ModuleManager.setScriptModule(
                    pack.context,
                    newId,
                    LuaWidgetManager.SOURCE_PREFIX + newId
                )
            } else {
                ModuleManager.removeScriptModule(pack.context, newId)
            }
            send(pack, "rebuild", null, 0)
            return pack.context.getString(R.string.command_luamodulecommands_lua_module_id_changed_ac703, oldLabel, formatWidget(newId))
        }

        if ("-click" == option) {
            if (args.size < 3) return pack.context.getString(R.string.help_module)
            val id = LuaWidgetManager.normalizeId(args.get(1))
            if (!LuaWidgetManager.exists(id)) return pack.context.getString(R.string.command_luamodulecommands_unknown_lua_module_ef4ca, id)
            if (!LuaWidgetManager.isDockable(id)) return pack.context.getString(R.string.command_luamodulecommands_script_is_not_a_dock_module_a030a, formatWidget(
                id
            ))
            if (!LuaWidgetManager.isEnabled(id)) return pack.context.getString(R.string.command_luamodulecommands_lua_module_disabled_407cc, formatWidget(id))
            val index: Int
            try {
                index = args.get(2)!!.toInt()
            } catch (e: Exception) {
                return pack.context.getString(R.string.command_luamodulecommands_invalid_lua_module_action_index_d4245, args.get(2))
            }
            send(pack, "lua_click", id, index)
            return null
        }

        if ("-action" == option || "-send" == option || "-input" == option) {
            if (args.size < 3) return pack.context.getString(R.string.help_module)
            val id = LuaWidgetManager.normalizeId(args.get(1))
            if (!LuaWidgetManager.exists(id)) return pack.context.getString(R.string.command_luamodulecommands_unknown_lua_module_ef4ca, id)
            if (!LuaWidgetManager.isDockable(id)) return pack.context.getString(R.string.command_luamodulecommands_script_is_not_a_dock_module_a030a, formatWidget(
                id
            ))
            if (!LuaWidgetManager.isEnabled(id)) return pack.context.getString(R.string.command_luamodulecommands_lua_module_disabled_407cc, formatWidget(id))
            send(pack, "lua_action", id, 0, TextUtils.join(" ", args.subList(2, args.size)))
            return null
        }

        if ("-dialog" == option) {
            if (args.size < 3) return pack.context.getString(R.string.help_module)
            val id = LuaWidgetManager.normalizeId(args.get(1))
            if (!LuaWidgetManager.exists(id)) return pack.context.getString(R.string.command_luamodulecommands_unknown_lua_module_ef4ca, id)
            if (!LuaWidgetManager.isDockable(id)) return pack.context.getString(R.string.command_luamodulecommands_script_is_not_a_dock_module_a030a, formatWidget(
                id
            ))
            if (!LuaWidgetManager.isEnabled(id)) return pack.context.getString(R.string.command_luamodulecommands_lua_module_disabled_407cc, formatWidget(id))
            val index: Int
            try {
                index = args.get(2)!!.toInt()
            } catch (e: Exception) {
                return pack.context.getString(R.string.command_luamodulecommands_invalid_lua_module_dialog_index_a7b13, args.get(2))
            }
            send(pack, "lua_dialog", id, index)
            return null
        }

        if ("-expand" == option || "-collapse" == option || "-toggle" == option) {
            if (args.size < 2) return pack.context.getString(R.string.help_module)
            val id = LuaWidgetManager.normalizeId(args.get(1))
            if (!LuaWidgetManager.exists(id)) return pack.context.getString(R.string.command_luamodulecommands_unknown_lua_module_ef4ca, id)
            if (!LuaWidgetManager.isDockable(id)) return pack.context.getString(R.string.command_luamodulecommands_script_is_not_a_dock_module_a030a, formatWidget(
                id
            ))
            if (!LuaWidgetManager.isEnabled(id)) return pack.context.getString(R.string.command_luamodulecommands_lua_module_disabled_407cc, formatWidget(id))
            val command = if ("-expand" == option)
                "lua_expand"
            else
                if ("-collapse" == option) "lua_collapse" else "lua_toggle"
            send(pack, command, id, 0)
            return null
        }

        if ("-rm" == option || "-remove" == option) {
            if (args.size < 2) return pack.context.getString(R.string.help_module)
            val id = LuaWidgetManager.normalizeId(args.get(1))
            val label = formatWidget(id)
            LuaWidgetManager.delete(id)
            ModuleManager.removeScriptModule(pack.context, id)
            send(pack, "rebuild", null, 0)
            return pack.context.getString(R.string.command_luamodulecommands_lua_module_removed_5769f, label)
        }

        return pack.context.getString(R.string.output_invalid_param) + " " + args.get(0)
    }

    private fun listWidgets(pack: ExecutePack): String {
        val ids = LuaWidgetManager.listIds()
        return (pack.context.getString(R.string.command_luamodulecommands_lua_modules_use_module_new_lua_name_module_b0bc0, (if (ids.isEmpty()) "none" else formatWidgets(ids))))
    }

    private fun formatWidgets(ids: MutableList<String?>): String {
        val out = StringBuilder()
        for (id in ids) {
            if (out.length > 0) out.append(", ")
            out.append(formatWidget(id))
        }
        return out.toString()
    }

    private fun formatWidget(id: String?): String? {
        val label = LuaWidgetManager.getName(id)
        return if (TextUtils.equals(label, id)) id else label + " (" + id + ")"
    }

    private fun valueOr(value: String?, fallback: String?): String? {
        return if (TextUtils.isEmpty(value)) fallback else value
    }

    private fun trustSummary(context: Context, prefix: String, id: String?, trust: TrustStatus): String {
        val out = StringBuilder(prefix).append(": ").append(formatWidget(id))
        out.append(context.getString(R.string.lua_detail_luamodulecommands_permissions_f56cd))
            .append(
                if (trust.requiredPermissions.isEmpty()) context.getString(R.string.lua_detail_luamodulecommands_none_71f8e) else TextUtils.join(
                    ", ",
                    trust.requiredPermissions
                )
            )
        if (!trust.missingDeclarations.isEmpty()) {
            out.append(context.getString(R.string.lua_detail_luamodulecommands_declare_first_9cbcd)).append(TextUtils.join(", ", trust.missingDeclarations))
        }
        if (!trust.unsupportedPermissions.isEmpty()) {
            out.append(context.getString(R.string.lua_detail_luamodulecommands_unsupported_50a59)).append(TextUtils.join(", ", trust.unsupportedPermissions))
        }
        if (trust.canApprove()) {
            out.append(context.getString(R.string.lua_approve_module_instruction, LuaWidgetManager.normalizeId(id)))
        } else {
            out.append(context.getString(R.string.lua_detail_luamodulecommands_edit_the_script_metadata_before_approval_bb65e))
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

    private fun send(
        pack: ExecutePack,
        command: String?,
        module: String?,
        actionIndex: Int,
        actionValue: String? = null
    ) {
        val intent = Intent(UIManager.ACTION_MODULE_COMMAND)
        intent.putExtra(UIManager.EXTRA_MODULE_COMMAND, command)
        if (module != null) {
            intent.putExtra(UIManager.EXTRA_MODULE_NAME, module)
        }
        if (actionIndex != 0) {
            intent.putExtra(UIManager.EXTRA_WIDGET_ACTION_INDEX, actionIndex)
        }
        if (actionValue != null) {
            intent.putExtra(UIManager.EXTRA_WIDGET_ACTION_VALUE, actionValue)
        }
        LocalBroadcastManager.getInstance(pack.context.getApplicationContext())
            .sendBroadcast(intent)
    }

    private fun copyToClipboard(context: Context, text: String?) {
        val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager?
        if (manager != null) {
            manager.setPrimaryClip(
                ClipData.newPlainText(
                    context.getString(R.string.command_luamodulecommands_re_tui_lua_module_package_c1350),
                    if (text == null) "" else text
                )
            )
        }
    }

}
