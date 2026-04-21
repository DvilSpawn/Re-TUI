package ohi.andre.consolelauncher.managers;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import ohi.andre.consolelauncher.managers.settings.LauncherSettings;
import ohi.andre.consolelauncher.managers.xml.XMLPrefsManager;
import ohi.andre.consolelauncher.managers.xml.classes.XMLPrefsSave;
import ohi.andre.consolelauncher.managers.xml.options.Suggestions;
import ohi.andre.consolelauncher.managers.xml.options.Theme;
import ohi.andre.consolelauncher.managers.xml.options.Ui;
import ohi.andre.consolelauncher.tuils.Tuils;

public final class PresetManager {

    private static final String PRESETS_FOLDER = "presets";
    private static final String[] BUILT_IN_PRESETS = {"blue", "red", "green", "pink", "bw", "cyberpunk"};

    private PresetManager() {}

    public static File getPresetsDir() {
        return new File(Tuils.getFolder(), PRESETS_FOLDER);
    }

    public static List<String> listPresets() {
        File[] files = getPresetsDir().listFiles(File::isDirectory);
        List<String> presets = new ArrayList<>();
        if (files != null) {
            for (File file : files) {
                presets.add(file.getName());
            }
        }
        Collections.sort(presets, String.CASE_INSENSITIVE_ORDER);
        return presets;
    }

    public static List<String> listBuiltInPresets() {
        List<String> presets = new ArrayList<>();
        Collections.addAll(presets, BUILT_IN_PRESETS);
        return presets;
    }

    public static List<String> listAllPresetNames() {
        List<String> presets = listPresets();
        for (String builtIn : BUILT_IN_PRESETS) {
            if (!containsIgnoreCase(presets, builtIn)) {
                presets.add(builtIn);
            }
        }
        Collections.sort(presets, String.CASE_INSENSITIVE_ORDER);
        return presets;
    }

    public static boolean isBuiltInPreset(String name) {
        return containsIgnoreCase(listBuiltInPresets(), name);
    }

    public static void save(String name) throws Exception {
        String cleanName = cleanName(name);
        File presetFolder = new File(getPresetsDir(), cleanName);
        if (!presetFolder.exists() && !presetFolder.mkdirs()) {
            throw new IllegalStateException("Unable to create preset folder");
        }

        writeXml(new File(presetFolder, XMLPrefsManager.XMLPrefsRoot.THEME.path),
                XMLPrefsManager.XMLPrefsRoot.THEME, Theme.values());
        writeXml(new File(presetFolder, XMLPrefsManager.XMLPrefsRoot.SUGGESTIONS.path),
                XMLPrefsManager.XMLPrefsRoot.SUGGESTIONS, Suggestions.values());

        apply(cleanName);
    }

    public static void apply(String name) throws Exception {
        String cleanName = cleanName(name);
        File presetFolder = new File(getPresetsDir(), cleanName);
        if (!presetFolder.isDirectory()) {
            if (applyBuiltIn(cleanName)) {
                return;
            }
            throw new IllegalArgumentException("Preset not found");
        }

        File presetTheme = new File(presetFolder, XMLPrefsManager.XMLPrefsRoot.THEME.path);
        File presetSuggestions = new File(presetFolder, XMLPrefsManager.XMLPrefsRoot.SUGGESTIONS.path);
        if (!presetTheme.isFile() || !presetSuggestions.isFile()) {
            throw new IllegalArgumentException("Preset is incomplete");
        }

        File currentTheme = new File(Tuils.getFolder(), XMLPrefsManager.XMLPrefsRoot.THEME.path);
        File currentSuggestions = new File(Tuils.getFolder(), XMLPrefsManager.XMLPrefsRoot.SUGGESTIONS.path);
        Tuils.insertOld(currentTheme);
        Tuils.insertOld(currentSuggestions);
        Tuils.copy(presetTheme, currentTheme);
        Tuils.copy(presetSuggestions, currentSuggestions);
        LauncherSettings.setAutoColorPick(false);
    }

    public static boolean applyBuiltIn(String name) {
        String cleanName = name == null ? null : name.trim().toLowerCase();
        if (!isBuiltInPreset(cleanName)) {
            return false;
        }

        Map<Theme, String> colors = new HashMap<>();
        Map<Suggestions, String> suggestionColors = new HashMap<>();

        boolean isTransparent = LauncherSettings.getBoolean(Ui.system_wallpaper);
        Theme backgroundTarget = isTransparent ? Theme.overlay_color : Theme.bg_color;
        String transPrefix = isTransparent ? "#00" : "#FF";

        switch(cleanName) {
            case "blue":
                colors.put(backgroundTarget, transPrefix + "001221");
                colors.put(Theme.input_color, "#00BFFF");
                colors.put(Theme.output_color, "#E0FFFF");
                colors.put(Theme.device_color, "#1E90FF");
                colors.put(Theme.enter_color, "#00BFFF");
                colors.put(Theme.toolbar_color, "#00BFFF");
                colors.put(Theme.time_color, "#87CEFA");

                suggestionColors.put(Suggestions.apps_bg_color, "#0000FF");
                suggestionColors.put(Suggestions.alias_bg_color, "#4169E1");
                suggestionColors.put(Suggestions.cmd_bg_color, "#00BFFF");
                suggestionColors.put(Suggestions.file_bg_color, "#87CEFA");
                suggestionColors.put(Suggestions.song_bg_color, "#1E90FF");
                break;
            case "red":
                colors.put(backgroundTarget, transPrefix + "210000");
                colors.put(Theme.input_color, "#FF4500");
                colors.put(Theme.output_color, "#FFEBEE");
                colors.put(Theme.device_color, "#B71C1C");
                colors.put(Theme.enter_color, "#FF0000");
                colors.put(Theme.toolbar_color, "#FF5252");
                colors.put(Theme.time_color, "#FF8A80");

                suggestionColors.put(Suggestions.apps_bg_color, "#FF0000");
                suggestionColors.put(Suggestions.alias_bg_color, "#DC143C");
                suggestionColors.put(Suggestions.cmd_bg_color, "#FF4500");
                suggestionColors.put(Suggestions.file_bg_color, "#FA8072");
                suggestionColors.put(Suggestions.song_bg_color, "#B22222");
                break;
            case "green":
                colors.put(backgroundTarget, transPrefix + "001B00");
                colors.put(Theme.input_color, "#00FF41");
                colors.put(Theme.output_color, "#D5F5E3");
                colors.put(Theme.device_color, "#2ECC71");
                colors.put(Theme.enter_color, "#00FF41");
                colors.put(Theme.toolbar_color, "#27AE60");
                colors.put(Theme.time_color, "#A9DFBF");

                suggestionColors.put(Suggestions.apps_bg_color, "#00FF00");
                suggestionColors.put(Suggestions.alias_bg_color, "#32CD32");
                suggestionColors.put(Suggestions.cmd_bg_color, "#00FF41");
                suggestionColors.put(Suggestions.file_bg_color, "#90EE90");
                suggestionColors.put(Suggestions.song_bg_color, "#228B22");
                break;
            case "pink":
                colors.put(backgroundTarget, transPrefix + "1A0010");
                colors.put(Theme.input_color, "#FF69B4");
                colors.put(Theme.output_color, "#FCE4EC");
                colors.put(Theme.device_color, "#AD1457");
                colors.put(Theme.enter_color, "#FF1493");
                colors.put(Theme.toolbar_color, "#F06292");
                colors.put(Theme.time_color, "#F8BBD0");

                suggestionColors.put(Suggestions.apps_bg_color, "#FF69B4");
                suggestionColors.put(Suggestions.alias_bg_color, "#FF1493");
                suggestionColors.put(Suggestions.cmd_bg_color, "#FFB6C1");
                suggestionColors.put(Suggestions.file_bg_color, "#FFC0CB");
                suggestionColors.put(Suggestions.song_bg_color, "#C71585");
                break;
            case "bw":
                colors.put(backgroundTarget, transPrefix + "000000");
                colors.put(Theme.input_color, "#FFFFFF");
                colors.put(Theme.output_color, "#CCCCCC");
                colors.put(Theme.device_color, "#AAAAAA");
                colors.put(Theme.enter_color, "#FFFFFF");
                colors.put(Theme.toolbar_color, "#FFFFFF");
                colors.put(Theme.time_color, "#FFFFFF");

                suggestionColors.put(Suggestions.apps_bg_color, "#FFFFFF");
                suggestionColors.put(Suggestions.alias_bg_color, "#EEEEEE");
                suggestionColors.put(Suggestions.cmd_bg_color, "#DDDDDD");
                suggestionColors.put(Suggestions.file_bg_color, "#CCCCCC");
                suggestionColors.put(Suggestions.song_bg_color, "#BBBBBB");

                suggestionColors.put(Suggestions.apps_text_color, "#000000");
                suggestionColors.put(Suggestions.alias_text_color, "#000000");
                suggestionColors.put(Suggestions.cmd_text_color, "#000000");
                suggestionColors.put(Suggestions.file_text_color, "#000000");
                suggestionColors.put(Suggestions.song_text_color, "#000000");
                break;
            case "cyberpunk":
                colors.put(backgroundTarget, transPrefix + "0D0615");
                colors.put(Theme.input_color, "#FCEE09");
                colors.put(Theme.output_color, "#00F0FF");
                colors.put(Theme.device_color, "#FF003C");
                colors.put(Theme.enter_color, "#FCEE09");
                colors.put(Theme.toolbar_color, "#39FF14");
                colors.put(Theme.time_color, "#00F0FF");

                suggestionColors.put(Suggestions.apps_bg_color, "#FF003C");
                suggestionColors.put(Suggestions.alias_bg_color, "#FCEE09");
                suggestionColors.put(Suggestions.cmd_bg_color, "#00F0FF");
                suggestionColors.put(Suggestions.file_bg_color, "#39FF14");
                suggestionColors.put(Suggestions.song_bg_color, "#BC00FF");

                suggestionColors.put(Suggestions.alias_text_color, "#000000");
                break;
            default:
                return false;
        }

        colors.put(Theme.toolbar_bg, "#00000000");
        for (Map.Entry<Theme, String> entry : colors.entrySet()) {
            LauncherSettings.setTheme(entry.getKey(), entry.getValue());
        }
        for (Map.Entry<Suggestions, String> entry : suggestionColors.entrySet()) {
            LauncherSettings.setSuggestion(entry.getKey(), entry.getValue());
        }
        LauncherSettings.setAutoColorPick(false);
        return true;
    }

    private static String cleanName(String name) {
        if (name == null) {
            throw new IllegalArgumentException("Preset name is required");
        }
        String cleanName = name.trim();
        if (cleanName.length() == 0 || cleanName.contains("/") || cleanName.contains("\\") || cleanName.contains("..")) {
            throw new IllegalArgumentException("Invalid preset name");
        }
        return cleanName;
    }

    private static boolean containsIgnoreCase(List<String> list, String value) {
        if (value == null) {
            return false;
        }
        for (String entry : list) {
            if (entry.equalsIgnoreCase(value)) {
                return true;
            }
        }
        return false;
    }

    private static void writeXml(File file, XMLPrefsManager.XMLPrefsRoot root, XMLPrefsSave[] values) throws Exception {
        StringBuilder xml = new StringBuilder(XMLPrefsManager.XML_DEFAULT);
        xml.append("<").append(root.name()).append(">\n");
        for (XMLPrefsSave value : values) {
            xml.append("\t<")
                    .append(value.label())
                    .append(" value=\"")
                    .append(LauncherSettings.getEffective(value))
                    .append("\" />\n");
        }
        xml.append("</").append(root.name()).append(">\n");

        FileOutputStream stream = new FileOutputStream(file, false);
        stream.write(xml.toString().getBytes());
        stream.flush();
        stream.close();
    }

}
