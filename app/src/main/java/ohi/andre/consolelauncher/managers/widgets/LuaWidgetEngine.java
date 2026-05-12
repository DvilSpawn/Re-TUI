package ohi.andre.consolelauncher.managers.widgets;

import android.text.TextUtils;

import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;
import org.luaj.vm2.lib.jse.JsePlatform;

import java.util.ArrayList;
import java.util.List;

public final class LuaWidgetEngine {

    private final String id;
    private final String script;
    private final long version;

    private Globals globals;
    private boolean loaded;
    private RenderResult lastResult = new RenderResult();

    public LuaWidgetEngine(String id, String script, long version) {
        this.id = LuaWidgetManager.normalizeId(id);
        this.script = script == null ? "" : script;
        this.version = version;
    }

    public long version() {
        return version;
    }

    public RenderResult render() {
        try {
            ensureLoaded();
            RenderResult result = newResult();
            if (callIfPresent("on_resume")) {
                lastResult = result;
            } else if (TextUtils.isEmpty(lastResult.body) && lastResult.buttons.isEmpty()) {
                callIfPresent("on_load");
                lastResult = result;
            }
        } catch (Exception e) {
            lastResult = RenderResult.error(errorMessage(e));
        }
        return lastResult.copy();
    }

    public RenderResult click(int index) {
        try {
            ensureLoaded();
            RenderResult result = newResult();
            if (!callIfPresent("on_click", LuaValue.valueOf(index))) {
                result.body = "No on_click handler in " + id + ".";
            }
            lastResult = result;
        } catch (Exception e) {
            lastResult = RenderResult.error(errorMessage(e));
        }
        return lastResult.copy();
    }

    private void ensureLoaded() {
        if (loaded) {
            return;
        }

        globals = JsePlatform.standardGlobals();
        globals.set("io", LuaValue.NIL);
        globals.set("package", LuaValue.NIL);
        globals.set("luajava", LuaValue.NIL);
        globals.set("os", safeOsTable(globals.get("os")));
        globals.set("ui", buildUiTable());
        globals.load(script, id).call();
        loaded = true;

        RenderResult result = newResult();
        callIfPresent("on_load");
        lastResult = result;
    }

    private RenderResult newResult() {
        RenderResult result = new RenderResult();
        lastResult = result;
        return result;
    }

    private boolean callIfPresent(String name, LuaValue... args) {
        LuaValue function = globals.get(name);
        if (!function.isfunction()) {
            return false;
        }
        if (args == null || args.length == 0) {
            function.call();
        } else if (args.length == 1) {
            function.call(args[0]);
        } else {
            LuaValue[] values = new LuaValue[args.length];
            System.arraycopy(args, 0, values, 0, args.length);
            function.invoke(values);
        }
        return true;
    }

    private LuaTable buildUiTable() {
        LuaTable ui = new LuaTable();
        ui.set("set_title", new UiFunction(args -> lastResult.title = stringArg(args)));
        ui.set("title", new UiFunction(args -> lastResult.title = stringArg(args)));
        ui.set("show_text", new UiFunction(args -> lastResult.body = stringArg(args)));
        ui.set("text", new UiFunction(args -> lastResult.body = appendLine(lastResult.body, stringArg(args))));
        ui.set("body", new UiFunction(args -> lastResult.body = appendLine(lastResult.body, stringArg(args))));
        ui.set("show_lines", new UiFunction(args -> lastResult.body = tableLines(tableArg(args))));
        ui.set("lines", new UiFunction(args -> lastResult.body = tableLines(tableArg(args))));
        ui.set("show_buttons", new UiFunction(args -> lastResult.buttons = tableStrings(tableArg(args))));
        ui.set("buttons", new UiFunction(args -> lastResult.buttons = tableStrings(tableArg(args))));
        ui.set("show_progress_bar", new UiFunction(args -> {
            String label = stringAt(args, 1, "Progress");
            double current = numberAt(args, 2, 0);
            double max = Math.max(1, numberAt(args, 3, 100));
            int pct = (int) Math.round(Math.max(0, Math.min(100, (current * 100d) / max)));
            lastResult.body = appendLine(lastResult.body, label + ": " + pct + "%");
        }));
        ui.set("show_toast", new UiFunction(args ->
                lastResult.body = appendLine(lastResult.body, "[toast] " + stringArg(args))));
        return ui;
    }

    private LuaTable safeOsTable(LuaValue os) {
        LuaTable safe = new LuaTable();
        if (os != null && os.istable()) {
            safe.set("clock", os.get("clock"));
            safe.set("date", os.get("date"));
            safe.set("difftime", os.get("difftime"));
            safe.set("time", os.get("time"));
        }
        return safe;
    }

    private static String stringArg(Varargs args) {
        return stringAt(args, 1, "");
    }

    private static String stringAt(Varargs args, int logicalIndex, String fallback) {
        int rawIndex = rawIndex(args, logicalIndex);
        LuaValue value = args.arg(rawIndex);
        return value.isnil() ? fallback : value.tojstring();
    }

    private static double numberAt(Varargs args, int logicalIndex, double fallback) {
        int rawIndex = rawIndex(args, logicalIndex);
        LuaValue value = args.arg(rawIndex);
        return value.isnumber() ? value.todouble() : fallback;
    }

    private static LuaValue tableArg(Varargs args) {
        return args.arg(rawIndex(args, 1));
    }

    private static int rawIndex(Varargs args, int logicalIndex) {
        if (args.narg() > logicalIndex && args.arg1().istable()) {
            return logicalIndex + 1;
        }
        return logicalIndex;
    }

    private static String tableLines(LuaValue table) {
        StringBuilder out = new StringBuilder();
        if (!table.istable()) {
            return table.isnil() ? "" : table.tojstring();
        }
        int length = table.length();
        for (int i = 1; i <= length; i++) {
            String value = table.get(i).tojstring();
            if (out.length() > 0) out.append('\n');
            out.append(value);
        }
        return out.toString();
    }

    private static List<String> tableStrings(LuaValue table) {
        ArrayList<String> values = new ArrayList<>();
        if (!table.istable()) {
            if (!table.isnil()) values.add(table.tojstring());
            return values;
        }
        int length = table.length();
        for (int i = 1; i <= length; i++) {
            LuaValue value = table.get(i);
            if (!value.isnil()) {
                values.add(value.tojstring());
            }
        }
        return values;
    }

    private static String appendLine(String body, String line) {
        if (TextUtils.isEmpty(body)) {
            return line == null ? "" : line;
        }
        return body + "\n" + (line == null ? "" : line);
    }

    private static String errorMessage(Exception e) {
        if (e instanceof LuaError && e.getMessage() != null) {
            return e.getMessage();
        }
        return e.getClass().getSimpleName() + (e.getMessage() == null ? "" : ": " + e.getMessage());
    }

    public static final class RenderResult {
        public String title = "";
        public String body = "";
        public String error = "";
        public List<String> buttons = new ArrayList<>();

        static RenderResult error(String message) {
            RenderResult result = new RenderResult();
            result.error = message == null ? "unknown error" : message;
            return result;
        }

        RenderResult copy() {
            RenderResult copy = new RenderResult();
            copy.title = title;
            copy.body = body;
            copy.error = error;
            copy.buttons = new ArrayList<>(buttons);
            return copy;
        }
    }

    private interface UiAction {
        void run(Varargs args);
    }

    private static final class UiFunction extends VarArgFunction {
        private final UiAction action;

        UiFunction(UiAction action) {
            this.action = action;
        }

        @Override
        public Varargs invoke(Varargs args) {
            action.run(args);
            return LuaValue.NONE;
        }
    }
}
