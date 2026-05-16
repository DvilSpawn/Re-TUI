package ohi.andre.consolelauncher.commands.main.raw;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.text.TextUtils;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import java.util.Arrays;
import java.util.List;

import ohi.andre.consolelauncher.R;
import ohi.andre.consolelauncher.UIManager;
import ohi.andre.consolelauncher.commands.CommandAbstraction;
import ohi.andre.consolelauncher.commands.ExecutePack;
import ohi.andre.consolelauncher.commands.tuixt.WidgetEditorActivity;
import ohi.andre.consolelauncher.managers.modules.ModuleManager;
import ohi.andre.consolelauncher.managers.widgets.LuaWidgetEngine;
import ohi.andre.consolelauncher.managers.widgets.LuaWidgetManager;
import ohi.andre.consolelauncher.tuils.Tuils;

public class widget implements CommandAbstraction {

    @Override
    public String exec(ExecutePack pack) throws Exception {
        Object arg = pack.get(Object.class, 0);
        String input = arg == null ? "" : arg.toString().trim();
        if (input.length() == 0 || "-ls".equalsIgnoreCase(input)) {
            return listWidgets(pack);
        }

        List<String> args = Tuils.splitArgs(input);
        String option = args.get(0).toLowerCase();

        if ("-new".equals(option) || "-add".equals(option)) {
            if (args.size() < 2) return pack.context.getString(R.string.help_widget);
            String requestedName = args.get(1);
            String id = LuaWidgetManager.idFromName(requestedName);
            if (TextUtils.isEmpty(id)) return "Invalid widget id.";
            if (!LuaWidgetManager.exists(id)) {
                LuaWidgetManager.save(id, args.size() > 2 ? args.get(2) : requestedName, LuaWidgetManager.newWidgetTemplate(id));
            }
            openEditor(pack, id);
            return "Widget created: " + formatWidget(id);
        }

        if ("-edit".equals(option)) {
            if (args.size() < 2) return pack.context.getString(R.string.help_widget);
            String id = LuaWidgetManager.normalizeId(args.get(1));
            if (TextUtils.isEmpty(id)) return "Invalid widget id.";
            if (!LuaWidgetManager.exists(id)) {
                LuaWidgetManager.save(id, id, LuaWidgetManager.newWidgetTemplate(id));
            }
            openEditor(pack, id);
            return "Opening widget editor: " + formatWidget(id);
        }

        if ("-show".equals(option)) {
            if (args.size() < 2) return pack.context.getString(R.string.help_widget);
            String id = LuaWidgetManager.normalizeId(args.get(1));
            if (!LuaWidgetManager.exists(id)) return "Unknown widget: " + id;
            if (!LuaWidgetManager.isDockable(id)) return "Script is not a dock widget: " + formatWidget(id);
            if (!LuaWidgetManager.isEnabled(id)) return "Widget disabled: " + formatWidget(id) + "\nUse widget -enable " + id + ".";
            ModuleManager.setScriptModule(pack.context, id, LuaWidgetManager.SOURCE_PREFIX + id);
            ModuleManager.addToDock(pack.context, Arrays.asList(id));
            send(pack, "show", id, 0);
            return "Widget opened: " + formatWidget(id);
        }

        if ("-refresh".equals(option)) {
            if (args.size() < 2) return pack.context.getString(R.string.help_widget);
            String id = LuaWidgetManager.normalizeId(args.get(1));
            if (!LuaWidgetManager.exists(id)) return "Unknown widget: " + id;
            if (!LuaWidgetManager.isDockable(id)) return "Script is not a dock widget: " + formatWidget(id);
            if (!LuaWidgetManager.isEnabled(id)) return "Widget disabled: " + formatWidget(id) + "\nUse widget -enable " + id + ".";
            ModuleManager.setScriptModule(pack.context, id, LuaWidgetManager.SOURCE_PREFIX + id);
            send(pack, "refresh", id, 0);
            return "Widget refresh dispatched: " + formatWidget(id);
        }

        if ("-check".equals(option)) {
            if (args.size() < 2) return pack.context.getString(R.string.help_widget);
            String id = LuaWidgetManager.normalizeId(args.get(1));
            if (!LuaWidgetManager.exists(id)) return "Unknown widget: " + id;
            LuaWidgetManager.TrustStatus trust = LuaWidgetManager.trustStatus(id);
            if (!trust.trusted) {
                return trustSummary("Widget check blocked", id, trust);
            }
            LuaWidgetEngine engine = new LuaWidgetEngine(pack.context, id, LuaWidgetManager.readScript(id), LuaWidgetManager.version(id), null);
            LuaWidgetEngine.RenderResult result = engine.render(true);
            if (!TextUtils.isEmpty(result.error)) {
                return "Widget check failed: " + formatWidget(id)
                        + (TextUtils.isEmpty(result.errorStage) ? "" : "\nStage: " + result.errorStage)
                        + "\n" + result.error
                        + "\nUse widget -copy-error " + id + " or widget -edit " + id + ".";
            }
            return "Widget check OK: " + formatWidget(id)
                    + "\nType: " + LuaWidgetManager.getScriptType(id)
                    + "\nCapabilities: " + LuaWidgetManager.describeCapabilities(LuaWidgetManager.readScript(id))
                    + "\nPermissions: " + LuaWidgetManager.describeRequiredPermissions(LuaWidgetManager.readScript(id))
                    + "\nTrust: approved"
                    + "\nRuntime: API " + LuaWidgetManager.apiVersion(id)
                    + "\nTitle: " + (TextUtils.isEmpty(result.title) ? LuaWidgetManager.getName(id) : result.title)
                    + "\nActions: " + result.buttons.size()
                    + "\nCommands: " + result.commands.size();
        }

        if ("-info".equals(option)) {
            if (args.size() < 2) return pack.context.getString(R.string.help_widget);
            String id = LuaWidgetManager.normalizeId(args.get(1));
            if (!LuaWidgetManager.exists(id)) return "Unknown widget: " + id;
            java.util.Map<String, String> meta = LuaWidgetManager.metadata(LuaWidgetManager.readScript(id));
            LuaWidgetManager.TrustStatus trust = LuaWidgetManager.trustStatus(id);
            return "Widget: " + formatWidget(id)
                    + "\nType: " + LuaWidgetManager.getScriptType(id)
                    + "\nCapabilities: " + LuaWidgetManager.describeCapabilities(LuaWidgetManager.readScript(id))
                    + "\nPermissions: " + LuaWidgetManager.describeRequiredPermissions(LuaWidgetManager.readScript(id))
                    + "\nTrust: " + (trust.trusted ? "approved" : "needs approval")
                    + "\nRuntime: API " + LuaWidgetManager.apiVersion(id)
                    + "\nState: " + (LuaWidgetManager.isEnabled(id) ? "enabled" : "disabled")
                    + "\nDescription: " + valueOr(meta.get("description"), "none")
                    + "\nAuthor: " + valueOr(meta.get("author"), "unknown")
                    + "\nVersion: " + valueOr(meta.get("version"), "none");
        }

        if ("-approve".equals(option) || "-trust".equals(option)) {
            if (args.size() < 2) return pack.context.getString(R.string.help_widget);
            String id = LuaWidgetManager.normalizeId(args.get(1));
            LuaWidgetManager.approve(id);
            if (LuaWidgetManager.isDockable(id)) {
                ModuleManager.setScriptModule(pack.context, id, LuaWidgetManager.SOURCE_PREFIX + id);
                send(pack, "update", id, 0);
            }
            return "Lua widget approved: " + formatWidget(id)
                    + "\nPermissions: " + LuaWidgetManager.describeRequiredPermissions(LuaWidgetManager.readScript(id));
        }

        if ("-copy-error".equals(option)) {
            if (args.size() < 2) return pack.context.getString(R.string.help_widget);
            String id = LuaWidgetManager.normalizeId(args.get(1));
            if (!LuaWidgetManager.exists(id)) return "Unknown widget: " + id;
            String error = LuaWidgetManager.lastError(id);
            if (TextUtils.isEmpty(error)) return "No saved Lua error: " + formatWidget(id);
            copyToClipboard(pack.context, error);
            return "Lua error copied: " + formatWidget(id);
        }

        if ("-disable".equals(option)) {
            if (args.size() < 2) return pack.context.getString(R.string.help_widget);
            String id = LuaWidgetManager.normalizeId(args.get(1));
            LuaWidgetManager.setEnabled(id, false);
            ModuleManager.removeFromDock(pack.context, Arrays.asList(id));
            send(pack, "rebuild", null, 0);
            return "Widget disabled: " + formatWidget(id);
        }

        if ("-enable".equals(option)) {
            if (args.size() < 2) return pack.context.getString(R.string.help_widget);
            String id = LuaWidgetManager.normalizeId(args.get(1));
            LuaWidgetManager.setEnabled(id, true);
            if (LuaWidgetManager.isDockable(id)) {
                ModuleManager.setScriptModule(pack.context, id, LuaWidgetManager.SOURCE_PREFIX + id);
            }
            send(pack, "rebuild", null, 0);
            return "Widget enabled: " + formatWidget(id);
        }

        if ("-export".equals(option)) {
            if (args.size() < 2) return pack.context.getString(R.string.help_widget);
            String id = LuaWidgetManager.normalizeId(args.get(1));
            String exported = LuaWidgetManager.exportPackage(id);
            copyToClipboard(pack.context, exported);
            return "Widget package copied to clipboard: " + formatWidget(id);
        }

        if ("-rename".equals(option) || "-mv".equals(option)) {
            if (args.size() < 3) return pack.context.getString(R.string.help_widget);
            String oldId = LuaWidgetManager.normalizeId(args.get(1));
            String newId = LuaWidgetManager.idFromName(args.get(2));
            if (TextUtils.isEmpty(newId)) return "Invalid widget id.";
            if (!LuaWidgetManager.exists(oldId)) return "Unknown widget: " + oldId;
            if (TextUtils.equals(oldId, newId)) return "Widget id unchanged: " + formatWidget(oldId);
            if (ModuleManager.isKnown(pack.context, newId) || LuaWidgetManager.exists(newId)) {
                return "Widget id already exists: " + newId;
            }

            String oldLabel = formatWidget(oldId);
            LuaWidgetManager.rename(oldId, newId);
            ModuleManager.renameScriptModule(pack.context, oldId, newId, LuaWidgetManager.SOURCE_PREFIX + newId);
            if (LuaWidgetManager.isDockable(newId)) {
                ModuleManager.setScriptModule(pack.context, newId, LuaWidgetManager.SOURCE_PREFIX + newId);
            } else {
                ModuleManager.removeScriptModule(pack.context, newId);
            }
            send(pack, "rebuild", null, 0);
            return "Widget id changed: " + oldLabel + " -> " + formatWidget(newId);
        }

        if ("-click".equals(option)) {
            if (args.size() < 3) return pack.context.getString(R.string.help_widget);
            String id = LuaWidgetManager.normalizeId(args.get(1));
            if (!LuaWidgetManager.exists(id)) return "Unknown widget: " + id;
            if (!LuaWidgetManager.isDockable(id)) return "Script is not a dock widget: " + formatWidget(id);
            if (!LuaWidgetManager.isEnabled(id)) return "Widget disabled: " + formatWidget(id);
            int index;
            try {
                index = Integer.parseInt(args.get(2));
            } catch (Exception e) {
                return "Invalid widget action index: " + args.get(2);
            }
            send(pack, "lua_click", id, index);
            return null;
        }

        if ("-action".equals(option) || "-send".equals(option) || "-input".equals(option)) {
            if (args.size() < 3) return pack.context.getString(R.string.help_widget);
            String id = LuaWidgetManager.normalizeId(args.get(1));
            if (!LuaWidgetManager.exists(id)) return "Unknown widget: " + id;
            if (!LuaWidgetManager.isDockable(id)) return "Script is not a dock widget: " + formatWidget(id);
            if (!LuaWidgetManager.isEnabled(id)) return "Widget disabled: " + formatWidget(id);
            send(pack, "lua_action", id, 0, TextUtils.join(" ", args.subList(2, args.size())));
            return null;
        }

        if ("-dialog".equals(option)) {
            if (args.size() < 3) return pack.context.getString(R.string.help_widget);
            String id = LuaWidgetManager.normalizeId(args.get(1));
            if (!LuaWidgetManager.exists(id)) return "Unknown widget: " + id;
            if (!LuaWidgetManager.isDockable(id)) return "Script is not a dock widget: " + formatWidget(id);
            if (!LuaWidgetManager.isEnabled(id)) return "Widget disabled: " + formatWidget(id);
            int index;
            try {
                index = Integer.parseInt(args.get(2));
            } catch (Exception e) {
                return "Invalid widget dialog index: " + args.get(2);
            }
            send(pack, "lua_dialog", id, index);
            return null;
        }

        if ("-expand".equals(option) || "-collapse".equals(option) || "-toggle".equals(option)) {
            if (args.size() < 2) return pack.context.getString(R.string.help_widget);
            String id = LuaWidgetManager.normalizeId(args.get(1));
            if (!LuaWidgetManager.exists(id)) return "Unknown widget: " + id;
            if (!LuaWidgetManager.isDockable(id)) return "Script is not a dock widget: " + formatWidget(id);
            if (!LuaWidgetManager.isEnabled(id)) return "Widget disabled: " + formatWidget(id);
            String command = "-expand".equals(option) ? "lua_expand"
                    : "-collapse".equals(option) ? "lua_collapse" : "lua_toggle";
            send(pack, command, id, 0);
            return null;
        }

        if ("-rm".equals(option) || "-remove".equals(option)) {
            if (args.size() < 2) return pack.context.getString(R.string.help_widget);
            String id = LuaWidgetManager.normalizeId(args.get(1));
            String label = formatWidget(id);
            LuaWidgetManager.delete(id);
            ModuleManager.removeScriptModule(pack.context, id);
            send(pack, "rebuild", null, 0);
            return "Widget removed: " + label;
        }

        return pack.context.getString(R.string.output_invalid_param) + " " + args.get(0);
    }

    private String listWidgets(ExecutePack pack) {
        List<String> ids = LuaWidgetManager.listIds();
        return "Lua widgets: " + (ids.isEmpty() ? "none" : formatWidgets(ids))
                + "\nUse widget -add [name], widget -new [name], widget -edit [id], widget -rename [old] [new], widget -show [id], widget -refresh [id], widget -check [id], widget -info [id], widget -approve [id], widget -copy-error [id], widget -disable|-enable [id], widget -export [id], widget -expand|-collapse|-toggle [id], widget -rm [id].";
    }

    private String formatWidgets(List<String> ids) {
        StringBuilder out = new StringBuilder();
        for (String id : ids) {
            if (out.length() > 0) out.append(", ");
            out.append(formatWidget(id));
        }
        return out.toString();
    }

    private String formatWidget(String id) {
        String label = LuaWidgetManager.getName(id);
        return TextUtils.equals(label, id) ? id : label + " (" + id + ")";
    }

    private String valueOr(String value, String fallback) {
        return TextUtils.isEmpty(value) ? fallback : value;
    }

    private String trustSummary(String prefix, String id, LuaWidgetManager.TrustStatus trust) {
        StringBuilder out = new StringBuilder(prefix).append(": ").append(formatWidget(id));
        out.append("\nPermissions: ")
                .append(trust.requiredPermissions.isEmpty() ? "none" : TextUtils.join(", ", trust.requiredPermissions));
        if (!trust.missingDeclarations.isEmpty()) {
            out.append("\nDeclare first: ").append(TextUtils.join(", ", trust.missingDeclarations));
        }
        if (!trust.unsupportedPermissions.isEmpty()) {
            out.append("\nUnsupported: ").append(TextUtils.join(", ", trust.unsupportedPermissions));
        }
        if (trust.canApprove()) {
            out.append("\nUse widget -approve ").append(LuaWidgetManager.normalizeId(id)).append(" to allow this script.");
        } else {
            out.append("\nEdit the script metadata before approval.");
        }
        return out.toString();
    }

    private void openEditor(ExecutePack pack, String id) {
        Intent intent = new Intent(pack.context, WidgetEditorActivity.class);
        intent.putExtra(WidgetEditorActivity.EXTRA_WIDGET_ID, id);
        ((Activity) pack.context).startActivity(intent);
    }

    private void send(ExecutePack pack, String command, String module, int actionIndex) {
        send(pack, command, module, actionIndex, null);
    }

    private void send(ExecutePack pack, String command, String module, int actionIndex, String actionValue) {
        Intent intent = new Intent(UIManager.ACTION_MODULE_COMMAND);
        intent.putExtra(UIManager.EXTRA_MODULE_COMMAND, command);
        if (module != null) {
            intent.putExtra(UIManager.EXTRA_MODULE_NAME, module);
        }
        if (actionIndex != 0) {
            intent.putExtra(UIManager.EXTRA_WIDGET_ACTION_INDEX, actionIndex);
        }
        if (actionValue != null) {
            intent.putExtra(UIManager.EXTRA_WIDGET_ACTION_VALUE, actionValue);
        }
        LocalBroadcastManager.getInstance(pack.context.getApplicationContext()).sendBroadcast(intent);
    }

    private void copyToClipboard(Context context, String text) {
        ClipboardManager manager = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        if (manager != null) {
            manager.setPrimaryClip(ClipData.newPlainText("Re:TUI widget package", text == null ? "" : text));
        }
    }

    @Override
    public int[] argType() {
        return new int[] {CommandAbstraction.PLAIN_TEXT};
    }

    @Override
    public int priority() {
        return 3;
    }

    @Override
    public int helpRes() {
        return R.string.help_widget;
    }

    @Override
    public String onArgNotFound(ExecutePack pack, int indexNotFound) {
        return pack.context.getString(R.string.help_widget);
    }

    @Override
    public String onNotArgEnough(ExecutePack pack, int nArgs) {
        return listWidgets(pack);
    }
}
