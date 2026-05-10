package ohi.andre.consolelauncher.managers;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.DocumentsContract;
import android.provider.OpenableColumns;

import org.w3c.dom.Document;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import javax.xml.parsers.DocumentBuilderFactory;

import ohi.andre.consolelauncher.BuildConfig;
import ohi.andre.consolelauncher.managers.settings.LauncherSettings;
import ohi.andre.consolelauncher.managers.xml.XMLPrefsManager;
import ohi.andre.consolelauncher.managers.xml.classes.XMLPrefsSave;
import ohi.andre.consolelauncher.managers.xml.options.Suggestions;
import ohi.andre.consolelauncher.managers.xml.options.Theme;
import ohi.andre.consolelauncher.managers.xml.options.Ui;
import ohi.andre.consolelauncher.tuils.Tuils;

public final class PresetManager {

    private static final String PRESETS_FOLDER = "presets";
    private static final String PRESET_PACKAGE_SUFFIX = ".retui-preset";
    private static final String MANIFEST_FILE = "manifest.json";
    private static final int MAX_ENTRY_BYTES = 256 * 1024;
    private static final String[] BUILT_IN_PRESETS = {"blue", "red", "green", "pink", "bw", "cyberpunk"};
    private static final String[] PRESET_XML_FILES = {
            XMLPrefsManager.XMLPrefsRoot.THEME.path,
            XMLPrefsManager.XMLPrefsRoot.SUGGESTIONS.path
    };

    private PresetManager() {}

    public static File getPresetsDir() {
        return new File(Tuils.getFolder(), PRESETS_FOLDER);
    }

    public static List<String> listPresets() {
        File[] files = getPresetsDir().listFiles();
        List<String> presets = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        if (files != null) {
            for (File file : files) {
                String name = null;
                if (file.isDirectory()) {
                    name = file.getName();
                } else if (file.isFile() && file.getName().toLowerCase().endsWith(PRESET_PACKAGE_SUFFIX)) {
                    name = file.getName().substring(0, file.getName().length() - PRESET_PACKAGE_SUFFIX.length());
                }
                if (name != null && seen.add(name.toLowerCase())) {
                    presets.add(name);
                }
            }
        }
        Collections.sort(presets, String.CASE_INSENSITIVE_ORDER);
        return presets;
    }

    public static List<String> listSavedPresetFolders() {
        File[] files = getPresetsDir().listFiles();
        List<String> presets = new ArrayList<>();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    presets.add(file.getName());
                }
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

    public static File getSavedPresetFolder(String name) {
        return new File(getPresetsDir(), cleanName(name));
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
        String cleanName = cleanPresetPackageName(name);
        File presetFolder = new File(getPresetsDir(), cleanName);
        if (!presetFolder.isDirectory()) {
            File packageFile = packageFile(cleanName);
            if (packageFile.isFile()) {
                importPackage(cleanName);
                presetFolder = new File(getPresetsDir(), cleanName);
            }
        }

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

    public static File exportPackage(String name) throws Exception {
        String cleanName = cleanPresetPackageName(name);
        File presetFolder = new File(getPresetsDir(), cleanName);
        if (!presetFolder.isDirectory()) {
            throw new IllegalArgumentException("Preset not found");
        }

        File presetTheme = new File(presetFolder, XMLPrefsManager.XMLPrefsRoot.THEME.path);
        File presetSuggestions = new File(presetFolder, XMLPrefsManager.XMLPrefsRoot.SUGGESTIONS.path);
        if (!presetTheme.isFile() || !presetSuggestions.isFile()) {
            throw new IllegalArgumentException("Preset is incomplete");
        }

        File out = packageFile(cleanName);
        ZipOutputStream zip = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(out, false)));
        try {
            addTextEntry(zip, MANIFEST_FILE, manifest(cleanName));
            addFileEntry(zip, XMLPrefsManager.XMLPrefsRoot.THEME.path, presetTheme);
            addFileEntry(zip, XMLPrefsManager.XMLPrefsRoot.SUGGESTIONS.path, presetSuggestions);
        } finally {
            zip.close();
        }
        return out;
    }

    public static void importPackage(String name) throws Exception {
        String cleanName = cleanPresetPackageName(name);
        File packageFile = packageFile(cleanName);
        if (!packageFile.isFile()) {
            throw new IllegalArgumentException("Preset package not found");
        }
        importPackageFile(cleanName, packageFile);
    }

    public static String importPackage(Context context, Uri uri) throws Exception {
        if (context == null || uri == null) {
            throw new IllegalArgumentException("Preset package is required");
        }

        String displayName = displayName(context, uri);
        String cleanName = cleanPresetPackageName(displayName);
        File packageFile = packageFile(cleanName);
        copyUriToFile(context, uri, packageFile);
        importPackageFile(cleanName, packageFile);
        return cleanName;
    }

    public static String importFolder(Context context, Uri treeUri) throws Exception {
        if (context == null || treeUri == null) {
            throw new IllegalArgumentException("Preset folder is required");
        }

        String cleanName = cleanName(treeName(treeUri));
        File tempFolder = new File(getPresetsDir(), "." + cleanName + ".importing");
        if (tempFolder.exists()) {
            Tuils.delete(tempFolder);
        }
        if (!tempFolder.mkdirs()) {
            throw new IllegalStateException("Unable to create import folder");
        }

        try {
            Map<String, Uri> children = folderChildren(context, treeUri);
            for (String fileName : PRESET_XML_FILES) {
                Uri child = children.get(fileName.toLowerCase());
                if (child == null) {
                    throw new IllegalArgumentException("Preset folder is incomplete");
                }
                copyUriToFile(context, child, new File(tempFolder, fileName));
            }
            validatePresetFolder(tempFolder);

            File presetFolder = new File(getPresetsDir(), cleanName);
            if (!presetFolder.exists() && !presetFolder.mkdirs()) {
                throw new IllegalStateException("Unable to create preset folder");
            }

            for (String fileName : PRESET_XML_FILES) {
                File dest = new File(presetFolder, fileName);
                if (dest.exists()) {
                    Tuils.insertOld(dest);
                }
                Tuils.copy(new File(tempFolder, fileName), dest);
            }
            return cleanName;
        } finally {
            Tuils.delete(tempFolder);
        }
    }

    public static void exportPackage(Context context, File packageFile, Uri uri) throws Exception {
        if (context == null || packageFile == null || uri == null || !packageFile.isFile()) {
            throw new IllegalArgumentException("Preset package not found");
        }

        InputStream in = new BufferedInputStream(new FileInputStream(packageFile));
        OutputStream out = new BufferedOutputStream(context.getContentResolver().openOutputStream(uri, "w"));
        if (out == null) {
            in.close();
            throw new IllegalArgumentException("Unable to open export destination");
        }
        try {
            copyStream(in, out);
        } finally {
            in.close();
            out.close();
        }
    }

    public static String packageFileName(File packageFile) {
        return packageFile == null ? "preset" + PRESET_PACKAGE_SUFFIX : packageFile.getName();
    }

    private static void importPackageFile(String cleanName, File packageFile) throws Exception {

        File tempFolder = new File(getPresetsDir(), "." + cleanName + ".importing");
        if (tempFolder.exists()) {
            Tuils.delete(tempFolder);
        }
        if (!tempFolder.mkdirs()) {
            throw new IllegalStateException("Unable to create import folder");
        }

        try {
            extractPackage(packageFile, tempFolder);
            validatePresetFolder(tempFolder);

            File presetFolder = new File(getPresetsDir(), cleanName);
            if (!presetFolder.exists() && !presetFolder.mkdirs()) {
                throw new IllegalStateException("Unable to create preset folder");
            }

            for (String fileName : PRESET_XML_FILES) {
                File dest = new File(presetFolder, fileName);
                if (dest.exists()) {
                    Tuils.insertOld(dest);
                }
                Tuils.copy(new File(tempFolder, fileName), dest);
            }
        } finally {
            Tuils.delete(tempFolder);
        }
    }

    private static String displayName(Context context, Uri uri) {
        String name = null;
        Cursor cursor = null;
        try {
            cursor = context.getContentResolver().query(uri, null, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (index >= 0) {
                    name = cursor.getString(index);
                }
            }
        } catch (Exception ignored) {
        } finally {
            if (cursor != null) cursor.close();
        }

        if (name == null || name.trim().length() == 0) {
            String path = uri.getLastPathSegment();
            name = path == null ? "imported-preset" : new File(path).getName();
        }
        return name;
    }

    private static String treeName(Uri treeUri) {
        String id = DocumentsContract.getTreeDocumentId(treeUri);
        if (id == null || id.trim().length() == 0) {
            return "imported-preset";
        }
        int slash = id.lastIndexOf('/');
        int colon = id.lastIndexOf(':');
        int cut = Math.max(slash, colon);
        return cut >= 0 && cut < id.length() - 1 ? id.substring(cut + 1) : id;
    }

    private static Map<String, Uri> folderChildren(Context context, Uri treeUri) throws Exception {
        Map<String, Uri> children = new HashMap<>();
        Uri childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
                treeUri,
                DocumentsContract.getTreeDocumentId(treeUri)
        );
        Cursor cursor = null;
        try {
            cursor = context.getContentResolver().query(
                    childrenUri,
                    new String[] {
                            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                            DocumentsContract.Document.COLUMN_DISPLAY_NAME
                    },
                    null,
                    null,
                    null
            );
            if (cursor == null) {
                throw new IllegalArgumentException("Unable to read preset folder");
            }
            while (cursor.moveToNext()) {
                String documentId = cursor.getString(0);
                String name = cursor.getString(1);
                if (documentId != null && name != null) {
                    children.put(name.toLowerCase(), DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId));
                }
            }
        } finally {
            if (cursor != null) cursor.close();
        }
        return children;
    }

    private static void copyUriToFile(Context context, Uri uri, File file) throws Exception {
        File parent = file.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IllegalStateException("Unable to create preset folder");
        }

        InputStream in = new BufferedInputStream(context.getContentResolver().openInputStream(uri));
        if (in == null) {
            throw new IllegalArgumentException("Unable to open preset package");
        }
        OutputStream out = new BufferedOutputStream(new FileOutputStream(file, false));
        try {
            copyStream(in, out);
        } finally {
            in.close();
            out.close();
        }
    }

    private static void copyStream(InputStream in, OutputStream out) throws Exception {
        byte[] buffer = new byte[4096];
        int read;
        int total = 0;
        while ((read = in.read(buffer)) != -1) {
            total += read;
            if (total > MAX_ENTRY_BYTES * PRESET_XML_FILES.length + 64 * 1024) {
                throw new IllegalArgumentException("Preset package file too large");
            }
            out.write(buffer, 0, read);
        }
        out.flush();
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

    private static String cleanPresetPackageName(String name) {
        String cleanName = cleanName(name);
        if (cleanName.toLowerCase().endsWith(PRESET_PACKAGE_SUFFIX)) {
            cleanName = cleanName.substring(0, cleanName.length() - PRESET_PACKAGE_SUFFIX.length());
        }
        if (cleanName.length() == 0) {
            throw new IllegalArgumentException("Invalid preset name");
        }
        return cleanName;
    }

    private static File packageFile(String cleanName) {
        return new File(getPresetsDir(), cleanName + PRESET_PACKAGE_SUFFIX);
    }

    private static void addTextEntry(ZipOutputStream zip, String name, String text) throws Exception {
        ZipEntry entry = new ZipEntry(name);
        zip.putNextEntry(entry);
        zip.write(text.getBytes("UTF-8"));
        zip.closeEntry();
    }

    private static void addFileEntry(ZipOutputStream zip, String name, File file) throws Exception {
        ZipEntry entry = new ZipEntry(name);
        zip.putNextEntry(entry);
        FileInputStream in = new FileInputStream(file);
        byte[] buffer = new byte[4096];
        try {
            int read;
            while ((read = in.read(buffer)) != -1) {
                zip.write(buffer, 0, read);
            }
        } finally {
            in.close();
        }
        zip.closeEntry();
    }

    private static String manifest(String name) {
        return "{\n"
                + "  \"type\": \"retui-preset\",\n"
                + "  \"schema\": 1,\n"
                + "  \"name\": \"" + jsonEscape(name) + "\",\n"
                + "  \"appVersion\": \"" + jsonEscape(BuildConfig.VERSION_NAME) + "\"\n"
                + "}\n";
    }

    private static String jsonEscape(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static void extractPackage(File packageFile, File tempFolder) throws Exception {
        Set<String> required = new HashSet<>();
        Collections.addAll(required, PRESET_XML_FILES);
        boolean hasManifest = false;

        ZipInputStream zip = new ZipInputStream(new BufferedInputStream(new FileInputStream(packageFile)));
        byte[] buffer = new byte[4096];
        try {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }

                String name = entry.getName();
                if (name.contains("/") || name.contains("\\") || name.contains("..")) {
                    throw new IllegalArgumentException("Unsafe preset package");
                }

                boolean allowedXml = required.contains(name);
                if (!allowedXml && !MANIFEST_FILE.equals(name)) {
                    throw new IllegalArgumentException("Unsupported preset package file: " + name);
                }

                File out = new File(tempFolder, name);
                FileOutputStream stream = new FileOutputStream(out, false);
                int total = 0;
                try {
                    int read;
                    while ((read = zip.read(buffer)) != -1) {
                        total += read;
                        if (total > MAX_ENTRY_BYTES) {
                            throw new IllegalArgumentException("Preset package file too large: " + name);
                        }
                        stream.write(buffer, 0, read);
                    }
                } finally {
                    stream.close();
                }

                if (MANIFEST_FILE.equals(name)) {
                    hasManifest = true;
                } else {
                    required.remove(name);
                }
            }
        } finally {
            zip.close();
        }

        if (!hasManifest || !required.isEmpty()) {
            throw new IllegalArgumentException("Preset package is incomplete");
        }
    }

    private static void validatePresetFolder(File folder) throws Exception {
        validateXmlRoot(new File(folder, XMLPrefsManager.XMLPrefsRoot.THEME.path), XMLPrefsManager.XMLPrefsRoot.THEME.name());
        validateXmlRoot(new File(folder, XMLPrefsManager.XMLPrefsRoot.SUGGESTIONS.path), XMLPrefsManager.XMLPrefsRoot.SUGGESTIONS.name());
    }

    private static void validateXmlRoot(File file, String expectedRoot) throws Exception {
        if (!file.isFile() || file.length() > MAX_ENTRY_BYTES) {
            throw new IllegalArgumentException("Preset package is incomplete");
        }
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setExpandEntityReferences(false);
        Document doc = factory.newDocumentBuilder().parse(file);
        if (doc == null || doc.getDocumentElement() == null || !expectedRoot.equals(doc.getDocumentElement().getNodeName())) {
            throw new IllegalArgumentException("Invalid preset XML: " + file.getName());
        }
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
