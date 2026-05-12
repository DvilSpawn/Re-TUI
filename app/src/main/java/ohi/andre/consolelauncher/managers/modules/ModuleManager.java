package ohi.andre.consolelauncher.managers.modules;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import ohi.andre.consolelauncher.managers.widgets.LuaWidgetManager;

public final class ModuleManager {

    public static final String MUSIC = "music";
    public static final String NOTIFICATIONS = "notifications";
    public static final String TIMER = "timer";
    public static final String CALENDAR = "calendar";
    public static final String EVENTS = "events";
    public static final String REMINDER = "reminder";
    public static final String SOURCE_LAUNCHER_PREFIX = "launcher:";
    public static final String SOURCE_TERMUX_PREFIX = "termux:";
    public static final String SOURCE_LUA_PREFIX = "lua:";

    private static final String PREFS = "retui_modules";
    private static final String KEY_DOCK = "dock";
    private static final String KEY_ACTIVE_MODULE = "active_module";
    private static final String KEY_SCRIPT_IDS = "script_ids";
    private static final String KEY_SCRIPT_PREFIX = "script_";
    private static final String KEY_SCRIPT_PATH_PREFIX = "script_path_";
    private static final String KEY_SCRIPT_TITLE_PREFIX = "script_title_";
    private static final String KEY_SCRIPT_SUGGESTIONS_PREFIX = "script_suggestions_";
    private static final List<String> BUILT_INS = Arrays.asList(MUSIC, NOTIFICATIONS, TIMER, CALENDAR, REMINDER);

    private ModuleManager() {}

    public static List<String> getBuiltIns() {
        return new ArrayList<>(BUILT_INS);
    }

    public static List<String> getDock(Context context) {
        String raw = prefs(context).getString(KEY_DOCK, null);
        if (TextUtils.isEmpty(raw)) {
            return new ArrayList<>(BUILT_INS);
        }
        return parseList(raw);
    }

    public static void setDock(Context context, List<String> modules) {
        LinkedHashSet<String> valid = new LinkedHashSet<>();
        for (String module : modules) {
            String id = normalize(module);
            if (isKnown(context, id)) {
                valid.add(id);
            }
        }
        prefs(context).edit().putString(KEY_DOCK, TextUtils.join(",", valid)).apply();
    }

    public static void addToDock(Context context, List<String> modules) {
        LinkedHashSet<String> dock = new LinkedHashSet<>(getDock(context));
        for (String module : modules) {
            String id = normalize(module);
            if (isKnown(context, id)) {
                dock.add(id);
            }
        }
        setDock(context, new ArrayList<>(dock));
    }

    public static void removeFromDock(Context context, List<String> modules) {
        LinkedHashSet<String> dock = new LinkedHashSet<>(getDock(context));
        for (String module : modules) {
            dock.remove(normalize(module));
        }
        setDock(context, new ArrayList<>(dock));
    }

    public static void setActiveModule(Context context, String module) {
        prefs(context).edit().putString(KEY_ACTIVE_MODULE, normalize(module)).apply();
    }

    public static String getActiveModule(Context context) {
        return normalize(prefs(context).getString(KEY_ACTIVE_MODULE, ""));
    }

    public static List<ModuleSuggestion> getActiveSuggestions(Context context) {
        return getSuggestionsForModule(context, getActiveModule(context));
    }

    public static void hideFromDock(Context context, String module) {
        String id = normalize(module);
        List<String> dock = getDock(context);
        dock.remove(id);
        setDock(context, dock);
    }

    public static boolean isKnown(Context context, String module) {
        String id = normalize(module);
        return BUILT_INS.contains(id) || getScriptIds(context).contains(id);
    }

    public static List<String> listAll(Context context) {
        LinkedHashSet<String> all = new LinkedHashSet<>(BUILT_INS);
        all.addAll(getScriptIds(context));
        return new ArrayList<>(all);
    }

    public static void setScriptText(Context context, String module, String text) {
        String id = normalize(module);
        if (TextUtils.isEmpty(id)) {
            return;
        }
        ScriptPayload payload = parseScriptPayload(text);
        LinkedHashSet<String> ids = new LinkedHashSet<>(getScriptIds(context));
        ids.add(id);
        SharedPreferences.Editor editor = prefs(context).edit()
                .putStringSet(KEY_SCRIPT_IDS, ids)
                .putString(KEY_SCRIPT_PREFIX + id, payload.body);
        if (TextUtils.isEmpty(payload.title)) {
            editor.remove(KEY_SCRIPT_TITLE_PREFIX + id);
        } else {
            editor.putString(KEY_SCRIPT_TITLE_PREFIX + id, payload.title);
        }
        if (payload.suggestions.isEmpty()) {
            editor.remove(KEY_SCRIPT_SUGGESTIONS_PREFIX + id);
        } else {
            editor.putString(KEY_SCRIPT_SUGGESTIONS_PREFIX + id, serializeSuggestions(payload.suggestions));
        }
        editor.apply();
    }

    public static void setScriptModule(Context context, String module, String path) {
        String id = normalize(module);
        if (TextUtils.isEmpty(id) || TextUtils.isEmpty(path)) {
            return;
        }
        LinkedHashSet<String> ids = new LinkedHashSet<>(getScriptIds(context));
        ids.add(id);
        prefs(context).edit()
                .putStringSet(KEY_SCRIPT_IDS, ids)
                .putString(KEY_SCRIPT_PATH_PREFIX + id, normalizeModuleSource(path))
                .putString(KEY_SCRIPT_PREFIX + id, "No module output yet. Run module -refresh " + id)
                .remove(KEY_SCRIPT_TITLE_PREFIX + id)
                .remove(KEY_SCRIPT_SUGGESTIONS_PREFIX + id)
                .apply();
    }

    public static void removeScriptModule(Context context, String module) {
        String id = normalize(module);
        if (TextUtils.isEmpty(id) || BUILT_INS.contains(id)) {
            return;
        }
        LinkedHashSet<String> ids = new LinkedHashSet<>(getScriptIds(context));
        ids.remove(id);
        LinkedHashSet<String> dock = new LinkedHashSet<>(getDock(context));
        dock.remove(id);
        prefs(context).edit()
                .putStringSet(KEY_SCRIPT_IDS, ids)
                .putString(KEY_DOCK, TextUtils.join(",", dock))
                .remove(KEY_SCRIPT_PREFIX + id)
                .remove(KEY_SCRIPT_PATH_PREFIX + id)
                .remove(KEY_SCRIPT_TITLE_PREFIX + id)
                .remove(KEY_SCRIPT_SUGGESTIONS_PREFIX + id)
                .apply();
    }

    public static String getScriptText(Context context, String module) {
        String id = normalize(module);
        return prefs(context).getString(KEY_SCRIPT_PREFIX + id, null);
    }

    public static String getScriptTitle(Context context, String module) {
        String id = normalize(module);
        return prefs(context).getString(KEY_SCRIPT_TITLE_PREFIX + id, "");
    }

    public static String getScriptPath(Context context, String module) {
        String id = normalize(module);
        return prefs(context).getString(KEY_SCRIPT_PATH_PREFIX + id, "");
    }

    public static String getModuleSource(Context context, String module) {
        return getScriptPath(context, module);
    }

    public static boolean isLauncherSource(String source) {
        return source != null && source.trim().toLowerCase(Locale.US).startsWith(SOURCE_LAUNCHER_PREFIX);
    }

    public static boolean isTermuxSource(String source) {
        return !TextUtils.isEmpty(source) && !isLauncherSource(source) && !isLuaSource(source);
    }

    public static boolean isLuaSource(String source) {
        return source != null && source.trim().toLowerCase(Locale.US).startsWith(SOURCE_LUA_PREFIX);
    }

    public static String luaWidgetId(String source) {
        if (!isLuaSource(source)) {
            return "";
        }
        return LuaWidgetManager.normalizeId(source.trim().substring(SOURCE_LUA_PREFIX.length()));
    }

    public static String launcherProvider(String source) {
        if (!isLauncherSource(source)) {
            return "";
        }
        return source.trim().substring(SOURCE_LAUNCHER_PREFIX.length()).trim().toLowerCase(Locale.US);
    }

    public static String normalize(String value) {
        if (value == null) {
            return "";
        }
        String lower = value.trim().toLowerCase(Locale.US);
        String id = lower.replaceAll("[^a-z0-9_-]", "");
        if ("notif".equals(id) || "notification".equals(id)) {
            return NOTIFICATIONS;
        }
        if ("cal".equals(id)) {
            return CALENDAR;
        }
        if ("event".equals(id) || "next_event".equals(id) || "upcoming".equals(id) || "upcomingevents".equals(id)) {
            return EVENTS;
        }
        if ("reminders".equals(id)) {
            return REMINDER;
        }
        return id;
    }

    public static String displayName(String module) {
        String id = normalize(module);
        if (NOTIFICATIONS.equals(id)) {
            return "NOTIFICATIONS";
        }
        if (EVENTS.equals(id)) {
            return "EVENTS";
        }
        return id.toUpperCase(Locale.US);
    }

    public static String displayTitle(Context context, String module) {
        String id = normalize(module);
        String title = getScriptTitle(context, id);
        if (!TextUtils.isEmpty(title)) {
            return title;
        }
        String source = getModuleSource(context, id);
        if (isLuaSource(source)) {
            return LuaWidgetManager.getName(luaWidgetId(source));
        }
        return displayName(id);
    }

    private static List<ModuleSuggestion> getSuggestionsForModule(Context context, String module) {
        ArrayList<ModuleSuggestion> suggestions = new ArrayList<>();
        String id = normalize(module);
        if (TextUtils.isEmpty(id)) {
            return suggestions;
        }

        if (TIMER.equals(id)) {
            suggestions.add(ModuleSuggestion.command("+5m", "timer -add 5m"));
            suggestions.add(ModuleSuggestion.command("+15m", "timer -add 15m"));
            suggestions.add(ModuleSuggestion.command("25m", "timer 25m"));
            suggestions.add(ModuleSuggestion.command("stop", "timer -stop"));
            suggestions.add(ModuleSuggestion.command("status", "timer -status"));
            suggestions.add(ModuleSuggestion.command("pomodoro", "pomodoro"));
        } else if (MUSIC.equals(id)) {
            suggestions.add(ModuleSuggestion.command("prev", "music -previous"));
            suggestions.add(ModuleSuggestion.command("play", "music -play"));
            suggestions.add(ModuleSuggestion.command("next", "music -next"));
            suggestions.add(ModuleSuggestion.command("info", "music -info"));
            suggestions.add(ModuleSuggestion.command("stop", "music -stop"));
        } else if (NOTIFICATIONS.equals(id)) {
            if (ModulePromptManager.isActive(context)) {
                suggestions.addAll(ModulePromptManager.getSuggestions(context));
                return suggestions;
            }
            suggestions.add(ModuleSuggestion.command("prev", "notifications -prev"));
            suggestions.add(ModuleSuggestion.command("next", "notifications -next"));
            suggestions.add(ModuleSuggestion.command("reply", "notifications -reply"));
            suggestions.add(ModuleSuggestion.command("open", "notifications -open"));
            suggestions.add(ModuleSuggestion.command("access", "notifications -access"));
            suggestions.add(ModuleSuggestion.command("rules", "notifications -ls"));
            suggestions.add(ModuleSuggestion.command("filters", "notifications -file"));
        } else if (CALENDAR.equals(id)) {
            suggestions.add(ModuleSuggestion.command("today", "module -show calendar"));
            suggestions.add(ModuleSuggestion.command("timer", "module -show timer"));
        } else if (REMINDER.equals(id)) {
            if (ModulePromptManager.isActive(context)) {
                suggestions.addAll(ModulePromptManager.getSuggestions(context));
            } else {
                suggestions.add(ModuleSuggestion.command("-add", "module -prompt reminder add"));
                suggestions.add(ModuleSuggestion.command("-edit", "module -prompt reminder edit"));
                suggestions.add(ModuleSuggestion.command("-rm", "module -prompt reminder remove"));
                suggestions.add(ModuleSuggestion.command("-ls", "module -show reminder"));
            }
        } else {
            suggestions.addAll(getScriptSuggestions(context, id));
        }

        return suggestions;
    }

    public static final class ModuleSuggestion {
        public static final String MODE_COMMAND = "command";
        public static final String MODE_TERMUX_RUN = "termux-run";
        public static final String MODE_CALLBACK = "callback";

        public final String label;
        public final String action;
        public final String mode;

        private ModuleSuggestion(String label, String action, String mode) {
            this.label = label;
            this.action = action;
            this.mode = mode;
        }

        public static ModuleSuggestion command(String label, String command) {
            return new ModuleSuggestion(label, command, MODE_COMMAND);
        }
    }

    private static ScriptPayload parseScriptPayload(String text) {
        ScriptPayload payload = new ScriptPayload();
        if (text == null) {
            return payload;
        }

        StringBuilder body = new StringBuilder();
        for (String rawLine : text.split("\\r?\\n", -1)) {
            String line = rawLine.trim();
            if (line.startsWith("::title ")) {
                payload.title = line.substring("::title ".length()).trim();
            } else if (line.startsWith("::body ")) {
                appendBodyLine(body, line.substring("::body ".length()).trim());
            } else if (line.startsWith("::suggest ")) {
                ModuleSuggestion suggestion = parseSuggestion(line.substring("::suggest ".length()).trim());
                if (suggestion != null) {
                    payload.suggestions.add(suggestion);
                }
            } else if (!line.startsWith("::")) {
                appendBodyLine(body, rawLine);
            }
        }
        payload.body = body.toString().trim();
        return payload;
    }

    private static ModuleSuggestion parseSuggestion(String raw) {
        if (TextUtils.isEmpty(raw)) {
            return null;
        }

        String[] parts = raw.split("\\|", -1);
        String label;
        String mode;
        String action;
        if (parts.length >= 3) {
            label = parts[0].trim();
            mode = parts[1].trim().toLowerCase(Locale.US);
            action = parts[2].trim();
        } else if (parts.length == 2) {
            label = parts[0].trim();
            mode = ModuleSuggestion.MODE_COMMAND;
            action = parts[1].trim();
        } else {
            label = raw.trim();
            mode = ModuleSuggestion.MODE_COMMAND;
            action = label;
        }

        if (TextUtils.isEmpty(label) || TextUtils.isEmpty(action)) {
            return null;
        }
        return new ModuleSuggestion(label, action, normalizeMode(mode));
    }

    private static String normalizeMode(String mode) {
        String normalized = mode == null ? "" : mode.trim().toLowerCase(Locale.US);
        if (ModuleSuggestion.MODE_TERMUX_RUN.equals(normalized)) {
            return ModuleSuggestion.MODE_TERMUX_RUN;
        }
        if (ModuleSuggestion.MODE_CALLBACK.equals(normalized)) {
            return ModuleSuggestion.MODE_CALLBACK;
        }
        return ModuleSuggestion.MODE_COMMAND;
    }

    private static void appendBodyLine(StringBuilder body, String line) {
        if (body.length() > 0) {
            body.append('\n');
        }
        body.append(line);
    }

    private static String serializeSuggestions(List<ModuleSuggestion> suggestions) {
        ArrayList<String> lines = new ArrayList<>();
        for (ModuleSuggestion suggestion : suggestions) {
            lines.add(suggestion.label + "\t" + suggestion.mode + "\t" + suggestion.action);
        }
        return TextUtils.join("\n", lines);
    }

    private static List<ModuleSuggestion> getScriptSuggestions(Context context, String module) {
        ArrayList<ModuleSuggestion> suggestions = new ArrayList<>();
        String raw = prefs(context).getString(KEY_SCRIPT_SUGGESTIONS_PREFIX + normalize(module), "");
        if (TextUtils.isEmpty(raw)) {
            return suggestions;
        }

        for (String line : raw.split("\\n")) {
            String[] parts = line.split("\\t", 3);
            if (parts.length == 3 && !TextUtils.isEmpty(parts[0]) && !TextUtils.isEmpty(parts[2])) {
                suggestions.add(new ModuleSuggestion(parts[0], parts[2], normalizeMode(parts[1])));
            }
        }
        return suggestions;
    }

    private static final class ScriptPayload {
        String title = "";
        String body = "";
        final List<ModuleSuggestion> suggestions = new ArrayList<>();
    }

    private static List<String> parseList(String raw) {
        ArrayList<String> out = new ArrayList<>();
        for (String part : raw.split(",")) {
            String id = normalize(part);
            if (!TextUtils.isEmpty(id) && !out.contains(id)) {
                out.add(id);
            }
        }
        return out;
    }

    private static String normalizeModuleSource(String path) {
        String trimmed = path == null ? "" : path.trim();
        String lower = trimmed.toLowerCase(Locale.US);
        if (lower.startsWith(SOURCE_TERMUX_PREFIX)) {
            return trimmed.substring(SOURCE_TERMUX_PREFIX.length()).trim();
        }
        if (lower.startsWith(SOURCE_LAUNCHER_PREFIX)) {
            return SOURCE_LAUNCHER_PREFIX + trimmed.substring(SOURCE_LAUNCHER_PREFIX.length()).trim().toLowerCase(Locale.US);
        }
        if (lower.startsWith(SOURCE_LUA_PREFIX)) {
            return SOURCE_LUA_PREFIX + LuaWidgetManager.normalizeId(trimmed.substring(SOURCE_LUA_PREFIX.length()));
        }
        return trimmed;
    }

    private static Set<String> getScriptIds(Context context) {
        return prefs(context).getStringSet(KEY_SCRIPT_IDS, new LinkedHashSet<>());
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
