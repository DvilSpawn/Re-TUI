package ohi.andre.consolelauncher.commands.main.raw;

import android.app.Activity;
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

        if ("-samples".equals(option)) {
            List<String> ids = LuaWidgetManager.installSamples();
            for (String id : ids) {
                ModuleManager.setScriptModule(pack.context, id, LuaWidgetManager.SOURCE_PREFIX + id);
            }
            ModuleManager.addToDock(pack.context, ids);
            send(pack, "rebuild", null, 0);
            return "Installed Lua widget samples: " + TextUtils.join(", ", ids)
                    + "\nUse widget -edit [name], module -show [name], or module -refresh [name].";
        }

        if ("-new".equals(option)) {
            if (args.size() < 2) return pack.context.getString(R.string.help_widget);
            String id = LuaWidgetManager.normalizeId(args.get(1));
            if (TextUtils.isEmpty(id)) return "Invalid widget id.";
            if (!LuaWidgetManager.exists(id)) {
                LuaWidgetManager.save(id, args.size() > 2 ? args.get(2) : id, LuaWidgetManager.newWidgetTemplate(id));
            }
            ModuleManager.setScriptModule(pack.context, id, LuaWidgetManager.SOURCE_PREFIX + id);
            ModuleManager.addToDock(pack.context, Arrays.asList(id));
            send(pack, "rebuild", null, 0);
            openEditor(pack, id);
            return "Widget created: " + id;
        }

        if ("-edit".equals(option)) {
            if (args.size() < 2) return pack.context.getString(R.string.help_widget);
            String id = LuaWidgetManager.normalizeId(args.get(1));
            if (TextUtils.isEmpty(id)) return "Invalid widget id.";
            if (!LuaWidgetManager.exists(id)) {
                LuaWidgetManager.save(id, id, LuaWidgetManager.newWidgetTemplate(id));
                ModuleManager.setScriptModule(pack.context, id, LuaWidgetManager.SOURCE_PREFIX + id);
                ModuleManager.addToDock(pack.context, Arrays.asList(id));
                send(pack, "rebuild", null, 0);
            }
            openEditor(pack, id);
            return "Opening widget editor: " + id;
        }

        if ("-show".equals(option)) {
            if (args.size() < 2) return pack.context.getString(R.string.help_widget);
            String id = LuaWidgetManager.normalizeId(args.get(1));
            if (!LuaWidgetManager.exists(id)) return "Unknown widget: " + id;
            ModuleManager.setScriptModule(pack.context, id, LuaWidgetManager.SOURCE_PREFIX + id);
            ModuleManager.addToDock(pack.context, Arrays.asList(id));
            send(pack, "show", id, 0);
            return "Widget opened: " + id;
        }

        if ("-refresh".equals(option)) {
            if (args.size() < 2) return pack.context.getString(R.string.help_widget);
            String id = LuaWidgetManager.normalizeId(args.get(1));
            if (!LuaWidgetManager.exists(id)) return "Unknown widget: " + id;
            ModuleManager.setScriptModule(pack.context, id, LuaWidgetManager.SOURCE_PREFIX + id);
            send(pack, "refresh", id, 0);
            return "Widget refresh dispatched: " + id;
        }

        if ("-click".equals(option)) {
            if (args.size() < 3) return pack.context.getString(R.string.help_widget);
            String id = LuaWidgetManager.normalizeId(args.get(1));
            int index;
            try {
                index = Integer.parseInt(args.get(2));
            } catch (Exception e) {
                return "Invalid widget action index: " + args.get(2);
            }
            send(pack, "lua_click", id, index);
            return "Widget action dispatched: " + id + " #" + index;
        }

        if ("-rm".equals(option) || "-remove".equals(option)) {
            if (args.size() < 2) return pack.context.getString(R.string.help_widget);
            String id = LuaWidgetManager.normalizeId(args.get(1));
            LuaWidgetManager.delete(id);
            ModuleManager.removeScriptModule(pack.context, id);
            send(pack, "rebuild", null, 0);
            return "Widget removed: " + id;
        }

        return pack.context.getString(R.string.output_invalid_param) + " " + args.get(0);
    }

    private String listWidgets(ExecutePack pack) {
        List<String> ids = LuaWidgetManager.listIds();
        return "Lua widgets: " + (ids.isEmpty() ? "none" : TextUtils.join(", ", ids))
                + "\nUse widget -samples, widget -new [name], widget -edit [name], widget -show [name], widget -refresh [name], widget -rm [name].";
    }

    private void openEditor(ExecutePack pack, String id) {
        Intent intent = new Intent(pack.context, WidgetEditorActivity.class);
        intent.putExtra(WidgetEditorActivity.EXTRA_WIDGET_ID, id);
        ((Activity) pack.context).startActivity(intent);
    }

    private void send(ExecutePack pack, String command, String module, int actionIndex) {
        Intent intent = new Intent(UIManager.ACTION_MODULE_COMMAND);
        intent.putExtra(UIManager.EXTRA_MODULE_COMMAND, command);
        if (module != null) {
            intent.putExtra(UIManager.EXTRA_MODULE_NAME, module);
        }
        if (actionIndex > 0) {
            intent.putExtra(UIManager.EXTRA_WIDGET_ACTION_INDEX, actionIndex);
        }
        LocalBroadcastManager.getInstance(pack.context.getApplicationContext()).sendBroadcast(intent);
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
