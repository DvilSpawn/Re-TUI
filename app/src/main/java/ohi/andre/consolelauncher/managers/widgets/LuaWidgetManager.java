package ohi.andre.consolelauncher.managers.widgets;

import android.content.Context;
import android.text.TextUtils;

import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import ohi.andre.consolelauncher.tuils.Tuils;

public final class LuaWidgetManager {

    public static final String ENGINE = "lua";
    public static final String SOURCE_PREFIX = "lua:";
    public static final String WIDGETS_DIR = "widgets";
    public static final String SCRIPT_FILE = "main.lua";
    public static final String MANIFEST_FILE = "manifest.json";

    private LuaWidgetManager() {}

    public static File widgetsDir() {
        File dir = new File(Tuils.getFolder(), WIDGETS_DIR);
        if (!dir.exists()) {
            //noinspection ResultOfMethodCallIgnored
            dir.mkdirs();
        }
        return dir;
    }

    public static File widgetDir(String id) {
        return new File(widgetsDir(), normalizeId(id));
    }

    public static File scriptFile(String id) {
        return new File(widgetDir(id), SCRIPT_FILE);
    }

    public static File manifestFile(String id) {
        return new File(widgetDir(id), MANIFEST_FILE);
    }

    public static boolean exists(String id) {
        return scriptFile(id).isFile();
    }

    public static List<String> listIds() {
        ArrayList<String> ids = new ArrayList<>();
        File[] files = widgetsDir().listFiles();
        if (files == null) {
            return ids;
        }
        for (File file : files) {
            if (file.isDirectory() && new File(file, SCRIPT_FILE).isFile()) {
                ids.add(file.getName());
            }
        }
        Collections.sort(ids, String.CASE_INSENSITIVE_ORDER);
        return ids;
    }

    public static String readScript(String id) {
        File file = scriptFile(id);
        if (!file.isFile()) {
            return "";
        }
        try (FileInputStream in = new FileInputStream(file)) {
            return Tuils.convertStreamToString(in);
        } catch (Exception e) {
            return "";
        }
    }

    public static String getName(String id) {
        String normalized = normalizeId(id);
        File manifest = manifestFile(normalized);
        if (manifest.isFile()) {
            try (FileInputStream in = new FileInputStream(manifest)) {
                JSONObject object = new JSONObject(Tuils.convertStreamToString(in));
                String name = object.optString("name", "");
                if (!TextUtils.isEmpty(name)) {
                    return name;
                }
            } catch (Exception ignored) {
            }
        }
        return displayName(normalized);
    }

    public static long version(String id) {
        File file = scriptFile(id);
        return file.isFile() ? file.lastModified() : 0L;
    }

    public static void save(String id, String name, String script) throws Exception {
        String normalized = normalizeId(id);
        if (TextUtils.isEmpty(normalized)) {
            throw new IllegalArgumentException("Widget id is required");
        }
        File dir = widgetDir(normalized);
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IllegalStateException("Unable to create widget folder");
        }
        write(scriptFile(normalized), script == null ? "" : script);

        JSONObject manifest = new JSONObject();
        manifest.put("type", "retui-widget");
        manifest.put("schema", 1);
        manifest.put("id", normalized);
        manifest.put("name", TextUtils.isEmpty(name) ? displayName(normalized) : name.trim());
        manifest.put("engine", ENGINE);
        manifest.put("devOnly", true);
        write(manifestFile(normalized), manifest.toString(2) + "\n");
    }

    public static void delete(String id) {
        File dir = widgetDir(id);
        if (dir.exists()) {
            Tuils.delete(dir);
        }
    }

    public static String newWidgetTemplate(String id) {
        String normalized = normalizeId(id);
        String title = displayName(normalized);
        return "-- name = \"" + title + "\"\n"
                + "-- type = \"widget\"\n"
                + "\n"
                + "local count = 0\n"
                + "\n"
                + "local function render()\n"
                + "    ui:set_title(\"" + title + "\")\n"
                + "    ui:show_text(\"Hello from \" .. \"" + normalized + "\" .. \"\\nCount: \" .. count)\n"
                + "    ui:show_buttons({\"Increase\", \"Reset\"})\n"
                + "end\n"
                + "\n"
                + "function on_load()\n"
                + "    render()\n"
                + "end\n"
                + "\n"
                + "function on_click(index)\n"
                + "    if index == 1 then count = count + 1 end\n"
                + "    if index == 2 then count = 0 end\n"
                + "    render()\n"
                + "end\n";
    }

    public static List<String> installSamples() throws Exception {
        ArrayList<String> ids = new ArrayList<>();
        for (Map.Entry<String, Sample> entry : samples().entrySet()) {
            String id = entry.getKey();
            Sample sample = entry.getValue();
            save(id, sample.name, sample.script);
            ids.add(id);
        }
        return ids;
    }

    public static String modulePayload(String id, LuaWidgetEngine.RenderResult result) {
        StringBuilder out = new StringBuilder();
        String title = result.title;
        if (TextUtils.isEmpty(title)) {
            title = getName(id);
        }
        appendDirective(out, "title", title);

        if (!TextUtils.isEmpty(result.error)) {
            appendDirective(out, "body", "Lua error: " + result.error);
        } else if (!TextUtils.isEmpty(result.body)) {
            for (String line : result.body.split("\\r?\\n", -1)) {
                appendDirective(out, "body", line);
            }
        } else {
            appendDirective(out, "body", "No widget output yet.");
        }

        int index = 1;
        for (String button : result.buttons) {
            appendSuggest(out, button, "widget -click " + normalizeId(id) + " " + index);
            index += 1;
        }
        appendSuggest(out, "refresh", "module -refresh " + normalizeId(id));
        appendSuggest(out, "edit", "widget -edit " + normalizeId(id));
        return out.toString().trim();
    }

    public static String normalizeId(String value) {
        if (value == null) {
            return "";
        }
        return value.trim()
                .toLowerCase(Locale.US)
                .replaceAll("[^a-z0-9_-]", "");
    }

    private static String displayName(String id) {
        if (TextUtils.isEmpty(id)) {
            return "Lua Widget";
        }
        StringBuilder out = new StringBuilder();
        for (String part : id.split("[_-]+")) {
            if (part.length() == 0) continue;
            if (out.length() > 0) out.append(' ');
            out.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) out.append(part.substring(1));
        }
        return out.length() == 0 ? id : out.toString();
    }

    private static void appendDirective(StringBuilder out, String key, String value) {
        if (out.length() > 0) out.append('\n');
        out.append("::").append(key).append(' ').append(value == null ? "" : value);
    }

    private static void appendSuggest(StringBuilder out, String label, String command) {
        if (TextUtils.isEmpty(label) || TextUtils.isEmpty(command)) {
            return;
        }
        if (out.length() > 0) out.append('\n');
        out.append("::suggest ")
                .append(label.replace("|", "/"))
                .append(" | command | ")
                .append(command);
    }

    private static void write(File file, String text) throws Exception {
        File parent = file.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IllegalStateException("Unable to create widget folder");
        }
        try (FileOutputStream out = new FileOutputStream(file, false)) {
            out.write((text == null ? "" : text).getBytes(StandardCharsets.UTF_8));
        }
    }

    private static Map<String, Sample> samples() {
        LinkedHashMap<String, Sample> samples = new LinkedHashMap<>();
        samples.put("aio_counter", new Sample("AIO Counter", ""
                + "-- Adapted from AIO Launcher samples/interactive_counter.lua\n"
                + "-- type = \"widget\"\n"
                + "\n"
                + "local counter = 0\n"
                + "\n"
                + "local function update_display()\n"
                + "    ui:set_title(\"AIO Counter\")\n"
                + "    ui:show_text(\"Counter: \" .. counter)\n"
                + "    ui:show_buttons({\"Increase (\" .. counter .. \")\", \"Reset\"})\n"
                + "end\n"
                + "\n"
                + "function on_load()\n"
                + "    update_display()\n"
                + "end\n"
                + "\n"
                + "function on_click(index)\n"
                + "    if index == 1 then counter = counter + 1 end\n"
                + "    if index == 2 then counter = 0 end\n"
                + "    update_display()\n"
                + "end\n"));
        samples.put("aio_progress", new Sample("AIO Progress", ""
                + "-- Inspired by AIO Launcher rich GUI/progress samples\n"
                + "-- type = \"widget\"\n"
                + "\n"
                + "local progress = 30\n"
                + "\n"
                + "local function render()\n"
                + "    ui:set_title(\"AIO Progress\")\n"
                + "    ui:show_text(\"Progress API sketch\\nCurrent: \" .. progress .. \"%\")\n"
                + "    ui:show_progress_bar(\"Progress\", progress, 100)\n"
                + "    ui:show_buttons({\"+10\", \"-10\", \"Reset\"})\n"
                + "end\n"
                + "\n"
                + "function on_load() render() end\n"
                + "\n"
                + "function on_click(index)\n"
                + "    if index == 1 then progress = math.min(100, progress + 10) end\n"
                + "    if index == 2 then progress = math.max(0, progress - 10) end\n"
                + "    if index == 3 then progress = 30 end\n"
                + "    render()\n"
                + "end\n"));
        samples.put("aio_clock", new Sample("AIO Clock", ""
                + "-- Inspired by AIO clock/widget lifecycle samples\n"
                + "-- type = \"widget\"\n"
                + "\n"
                + "function on_resume()\n"
                + "    ui:set_title(\"AIO Clock\")\n"
                + "    ui:show_text(\"Local time\\n\" .. os.date(\"%Y-%m-%d %H:%M:%S\"))\n"
                + "    ui:show_buttons({\"Refresh\"})\n"
                + "end\n"
                + "\n"
                + "function on_click(index)\n"
                + "    on_resume()\n"
                + "end\n"));
        return samples;
    }

    private static final class Sample {
        final String name;
        final String script;

        Sample(String name, String script) {
            this.name = name;
            this.script = script;
        }
    }
}
