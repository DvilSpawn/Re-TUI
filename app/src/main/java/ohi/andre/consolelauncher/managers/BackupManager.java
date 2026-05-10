package ohi.andre.consolelauncher.managers;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.util.Base64;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import javax.crypto.Cipher;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

import ohi.andre.consolelauncher.BuildConfig;
import ohi.andre.consolelauncher.managers.xml.XMLPrefsManager;
import ohi.andre.consolelauncher.tuils.Tuils;

public final class BackupManager {

    private static final String BACKUP_SUFFIX = ".retui-backup";
    private static final String MANIFEST_FILE = "manifest.txt";
    private static final String SHARED_PREFS_DIR = "shared_prefs/";
    private static final long MAX_BACKUP_BYTES = 32L * 1024L * 1024L;
    private static final byte[] ENCRYPTED_MAGIC = new byte[] {'R', 'E', 'T', 'U', 'I', 'E', 'N', 'C', '1'};
    private static final int SALT_BYTES = 16;
    private static final int IV_BYTES = 12;
    private static final int KEY_BITS = 256;
    private static final int KDF_ITERATIONS = 120000;
    private static final int GCM_TAG_BITS = 128;
    private static final String TYPE_BACKUP = "retui-backup";
    private static final String TYPE_SHAREABLE = "retui-shareable-config";
    private static final String[] SHAREABLE_FILES = {
            XMLPrefsManager.XMLPrefsRoot.THEME.path,
            XMLPrefsManager.XMLPrefsRoot.SUGGESTIONS.path
    };
    private static final String[] PERSONAL_PREFS = {
            "ui",
            "apps",
            "retui_modules",
            "retui_reminders",
            "retui_module_prompt",
            "retui_callback_auth",
            "pomodoro_state",
            "busybox_prefs",
            "changelogPrefs"
    };

    private BackupManager() {}

    public static String defaultBackupName() {
        String stamp = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(new Date());
        return "retui-backup-" + stamp + BACKUP_SUFFIX;
    }

    public static String defaultShareableConfigurationName() {
        return defaultShareableConfigurationName(null);
    }

    public static String defaultShareableConfigurationName(String sourceName) {
        String stamp = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(new Date());
        String source = fileSafeName(sourceName);
        if (source.length() > 0) {
            return "retui-config-" + source + "-" + stamp + BACKUP_SUFFIX;
        }
        return "retui-config-" + stamp + BACKUP_SUFFIX;
    }

    public static void exportBackup(Context context, Uri uri) throws Exception {
        exportBackup(context, uri, null);
    }

    public static void exportBackup(Context context, Uri uri, String password) throws Exception {
        if (context == null || uri == null) {
            throw new IllegalArgumentException("Backup destination is required");
        }

        OutputStream out = new BufferedOutputStream(context.getContentResolver().openOutputStream(uri, "w"));
        if (out == null) {
            throw new IllegalArgumentException("Unable to open backup destination");
        }

        byte[] backup = createPersonalBackup(context);
        try {
            if (password != null && password.length() > 0) {
                out.write(encrypt(backup, password));
            } else {
                out.write(backup);
            }
        } finally {
            out.close();
        }
    }

    public static void exportShareableConfiguration(Context context, Uri uri) throws Exception {
        exportShareableConfiguration(context, uri, null);
    }

    public static void exportShareableConfiguration(Context context, Uri uri, String presetName) throws Exception {
        if (context == null || uri == null) {
            throw new IllegalArgumentException("Configuration destination is required");
        }

        String preset = presetName == null ? null : presetName.trim();
        File sourceRoot = Tuils.getFolder();
        String sourceType = "current";
        String sourceLabel = null;
        if (preset != null && preset.length() > 0) {
            sourceRoot = PresetManager.getSavedPresetFolder(preset);
            if (!sourceRoot.isDirectory()) {
                throw new IllegalArgumentException("Preset not found");
            }
            sourceType = "preset";
            sourceLabel = sourceRoot.getName();
        }

        OutputStream out = new BufferedOutputStream(context.getContentResolver().openOutputStream(uri, "w"));
        if (out == null) {
            throw new IllegalArgumentException("Unable to open configuration destination");
        }

        ZipOutputStream zip = new ZipOutputStream(out);
        try {
            addTextEntry(zip, MANIFEST_FILE,
                    "type=" + TYPE_SHAREABLE + "\n"
                            + "schema=1\n"
                            + "profile=shareable\n"
                            + "appVersion=" + BuildConfig.VERSION_NAME + "\n"
                            + "source=" + sourceType + "\n"
                            + (sourceLabel == null ? "" : "presetName=" + manifestSafeValue(sourceLabel) + "\n")
                            + "sections=theme,suggestions\n");
            for (String name : SHAREABLE_FILES) {
                File file = new File(sourceRoot, name);
                if (!file.isFile()) {
                    throw new IllegalArgumentException(sourceLabel == null ? "Configuration is incomplete" : "Preset is incomplete");
                }
                addFileEntry(zip, name, file);
            }
        } finally {
            zip.close();
        }
    }

    public static void importBackup(Context context, Uri uri) throws Exception {
        importBackup(context, uri, null);
    }

    public static void importBackup(Context context, Uri uri, String password) throws Exception {
        if (context == null || uri == null) {
            throw new IllegalArgumentException("Backup package is required");
        }

        File tempDir = new File(Tuils.getFolder(), ".restore-importing");
        if (tempDir.exists()) {
            Tuils.delete(tempDir);
        }
        if (!tempDir.mkdirs()) {
            throw new IllegalStateException("Unable to create restore folder");
        }

        boolean hasManifest = false;
        String manifest = null;
        long totalBytes = 0;
        InputStream packageStream = backupInputStream(context, uri, password);
        ZipInputStream zip = new ZipInputStream(new BufferedInputStream(packageStream));
        byte[] buffer = new byte[8192];
        try {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                String name = entry.getName();
                if (entry.isDirectory()) continue;
                if (!isSafeEntry(name)) {
                    throw new IllegalArgumentException("Unsafe backup package");
                }

                if (MANIFEST_FILE.equals(name)) {
                    hasManifest = true;
                }

                File out = new File(tempDir, name);
                File parent = out.getParentFile();
                if (parent != null && !parent.exists() && !parent.mkdirs()) {
                    throw new IllegalStateException("Unable to restore backup folder");
                }

                FileOutputStream stream = new FileOutputStream(out, false);
                try {
                    int read;
                    ByteArrayOutputStream manifestOut = MANIFEST_FILE.equals(name) ? new ByteArrayOutputStream() : null;
                    while ((read = zip.read(buffer)) != -1) {
                        totalBytes += read;
                        if (totalBytes > MAX_BACKUP_BYTES) {
                            throw new IllegalArgumentException("Backup package is too large");
                        }
                        stream.write(buffer, 0, read);
                        if (manifestOut != null) {
                            manifestOut.write(buffer, 0, read);
                        }
                    }
                    if (manifestOut != null) {
                        manifest = manifestOut.toString("UTF-8");
                    }
                } finally {
                    stream.close();
                }
            }
        } finally {
            zip.close();
        }

        if (!hasManifest) {
            throw new IllegalArgumentException("Backup package is incomplete");
        }

        String type = manifestValue(manifest, "type");
        if (!TYPE_BACKUP.equals(type) && !TYPE_SHAREABLE.equals(type)) {
            throw new IllegalArgumentException("Unsupported backup package");
        }
        boolean personal = TYPE_BACKUP.equals(type);
        if (personal) {
            clearForPersonalRestore(Tuils.getFolder());
        }
        restoreDirectory(tempDir, Tuils.getFolder(), personal);
        if (personal) {
            restoreSharedPreferences(context, tempDir);
        }
        Tuils.delete(tempDir);
    }

    public static boolean isEncryptedBackup(Context context, Uri uri) throws Exception {
        if (context == null || uri == null) return false;
        InputStream in = new BufferedInputStream(context.getContentResolver().openInputStream(uri));
        if (in == null) return false;
        try {
            byte[] header = new byte[ENCRYPTED_MAGIC.length];
            int read = in.read(header);
            return read == ENCRYPTED_MAGIC.length && matchesMagic(header);
        } finally {
            in.close();
        }
    }

    private static byte[] createPersonalBackup(Context context) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ZipOutputStream zip = new ZipOutputStream(out);
        try {
            addTextEntry(zip, MANIFEST_FILE,
                    "type=" + TYPE_BACKUP + "\n"
                            + "schema=1\n"
                            + "profile=personal\n"
                            + "appVersion=" + BuildConfig.VERSION_NAME + "\n");
            File root = Tuils.getFolder();
            addDirectory(zip, root, root);
            addSharedPreferences(zip, context);
        } finally {
            zip.close();
        }
        return out.toByteArray();
    }

    private static void addDirectory(ZipOutputStream zip, File root, File dir) throws Exception {
        File[] files = dir.listFiles();
        if (files == null) return;

        for (File file : files) {
            String name = relativeName(root, file);
            if (!isBackupCandidate(file, name)) continue;

            if (file.isDirectory()) {
                addDirectory(zip, root, file);
            } else if (file.isFile()) {
                addFileEntry(zip, name, file);
            }
        }
    }

    private static boolean isBackupCandidate(File file, String name) {
        if (name == null || name.length() == 0) return false;
        if (name.startsWith(SHARED_PREFS_DIR)) return false;
        if (name.startsWith(".restore-importing") || name.endsWith(BACKUP_SUFFIX)) return false;
        if (name.startsWith("crash.txt")) return false;
        return file.isDirectory() || file.isFile();
    }

    private static String fileSafeName(String value) {
        if (value == null) return "";
        String trimmed = value.trim().toLowerCase(Locale.US);
        if (trimmed.length() == 0) return "";
        return trimmed.replaceAll("[^a-z0-9._-]+", "-").replaceAll("^-+|-+$", "");
    }

    private static String manifestSafeValue(String value) {
        if (value == null) return "";
        return value.replace('\r', ' ').replace('\n', ' ').trim();
    }

    private static void addSharedPreferences(ZipOutputStream zip, Context context) throws Exception {
        for (String name : PERSONAL_PREFS) {
            SharedPreferences prefs = context.getApplicationContext().getSharedPreferences(name, Context.MODE_PRIVATE);
            Map<String, ?> values = prefs.getAll();
            addTextEntry(zip, SHARED_PREFS_DIR + name + ".properties", serializePrefs(values));
        }
    }

    private static String serializePrefs(Map<String, ?> values) {
        StringBuilder out = new StringBuilder();
        List<String> keys = new ArrayList<>(values.keySet());
        Collections.sort(keys);
        for (String key : keys) {
            Object value = values.get(key);
            if (value == null) continue;
            if (value instanceof Set) {
                List<String> setValues = new ArrayList<>();
                for (Object item : (Set<?>) value) {
                    if (item != null) setValues.add(String.valueOf(item));
                }
                Collections.sort(setValues);
                out.append("set|").append(encode(key)).append("|").append(encode(joinSet(setValues))).append('\n');
            } else if (value instanceof Boolean) {
                out.append("boolean|").append(encode(key)).append("|").append(encode(String.valueOf(value))).append('\n');
            } else if (value instanceof Integer) {
                out.append("int|").append(encode(key)).append("|").append(encode(String.valueOf(value))).append('\n');
            } else if (value instanceof Long) {
                out.append("long|").append(encode(key)).append("|").append(encode(String.valueOf(value))).append('\n');
            } else if (value instanceof Float) {
                out.append("float|").append(encode(key)).append("|").append(encode(String.valueOf(value))).append('\n');
            } else {
                out.append("string|").append(encode(key)).append("|").append(encode(String.valueOf(value))).append('\n');
            }
        }
        return out.toString();
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
        byte[] buffer = new byte[8192];
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

    private static void restoreDirectory(File source, File target, boolean replaceExisting) throws Exception {
        File[] files = source.listFiles();
        if (files == null) return;

        for (File file : files) {
            if (MANIFEST_FILE.equals(file.getName())) continue;
            String relative = relativeName(source, file);
            if (relative.startsWith(SHARED_PREFS_DIR)) continue;
            File dest = new File(target, relativeName(source, file));
            if (file.isDirectory()) {
                if (!dest.exists() && !dest.mkdirs()) {
                    throw new IllegalStateException("Unable to restore folder: " + dest.getName());
                }
                restoreDirectory(file, dest, replaceExisting);
            } else if (file.isFile()) {
                File parent = dest.getParentFile();
                if (parent != null && !parent.exists() && !parent.mkdirs()) {
                    throw new IllegalStateException("Unable to restore folder: " + parent.getName());
                }
                if (!replaceExisting && dest.exists()) {
                    Tuils.insertOld(dest);
                }
                copyFile(file, dest);
            }
        }
    }

    private static void restoreSharedPreferences(Context context, File tempDir) throws Exception {
        File prefsDir = new File(tempDir, SHARED_PREFS_DIR);
        if (!prefsDir.isDirectory()) return;
        for (String name : PERSONAL_PREFS) {
            File file = new File(prefsDir, name + ".properties");
            if (!file.isFile()) continue;
            SharedPreferences.Editor editor = context.getApplicationContext()
                    .getSharedPreferences(name, Context.MODE_PRIVATE)
                    .edit()
                    .clear();
            applyPrefs(editor, readText(file));
            editor.apply();
        }
    }

    private static void applyPrefs(SharedPreferences.Editor editor, String text) {
        String[] lines = text.split("\\n");
        for (String line : lines) {
            if (line.length() == 0) continue;
            String[] parts = line.split("\\|", 3);
            if (parts.length != 3) continue;
            String type = parts[0];
            String key = decode(parts[1]);
            String value = decode(parts[2]);
            try {
                if ("boolean".equals(type)) {
                    editor.putBoolean(key, Boolean.parseBoolean(value));
                } else if ("int".equals(type)) {
                    editor.putInt(key, Integer.parseInt(value));
                } else if ("long".equals(type)) {
                    editor.putLong(key, Long.parseLong(value));
                } else if ("float".equals(type)) {
                    editor.putFloat(key, Float.parseFloat(value));
                } else if ("set".equals(type)) {
                    editor.putStringSet(key, splitSet(value));
                } else {
                    editor.putString(key, value);
                }
            } catch (Exception ignored) {
            }
        }
    }

    private static void clearForPersonalRestore(File root) {
        File[] files = root.listFiles();
        if (files == null) return;
        for (File file : files) {
            String name = file.getName();
            if (name.startsWith(".restore-importing") || name.endsWith(BACKUP_SUFFIX)) continue;
            Tuils.delete(file);
        }
    }

    private static InputStream backupInputStream(Context context, Uri uri, String password) throws Exception {
        byte[] bytes = readUri(context, uri);
        if (!startsWithMagic(bytes)) {
            return new ByteArrayInputStream(bytes);
        }
        if (password == null || password.length() == 0) {
            throw new IllegalArgumentException("Backup password is required");
        }
        return new ByteArrayInputStream(decrypt(bytes, password));
    }

    private static byte[] encrypt(byte[] plain, String password) throws Exception {
        byte[] salt = randomBytes(SALT_BYTES);
        byte[] iv = randomBytes(IV_BYTES);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key(password, salt), new GCMParameterSpec(GCM_TAG_BITS, iv));
        byte[] encrypted = cipher.doFinal(plain);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(ENCRYPTED_MAGIC);
        out.write(salt);
        out.write(iv);
        out.write(encrypted);
        return out.toByteArray();
    }

    private static byte[] decrypt(byte[] encrypted, String password) throws Exception {
        int headerBytes = ENCRYPTED_MAGIC.length + SALT_BYTES + IV_BYTES;
        if (encrypted.length <= headerBytes || !startsWithMagic(encrypted)) {
            throw new IllegalArgumentException("Encrypted backup is incomplete");
        }

        byte[] salt = new byte[SALT_BYTES];
        byte[] iv = new byte[IV_BYTES];
        System.arraycopy(encrypted, ENCRYPTED_MAGIC.length, salt, 0, SALT_BYTES);
        System.arraycopy(encrypted, ENCRYPTED_MAGIC.length + SALT_BYTES, iv, 0, IV_BYTES);

        int payloadStart = headerBytes;
        byte[] payload = new byte[encrypted.length - payloadStart];
        System.arraycopy(encrypted, payloadStart, payload, 0, payload.length);

        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key(password, salt), new GCMParameterSpec(GCM_TAG_BITS, iv));
            return cipher.doFinal(payload);
        } catch (Exception e) {
            throw new IllegalArgumentException("Backup password is incorrect or the file was changed");
        }
    }

    private static SecretKeySpec key(String password, byte[] salt) throws Exception {
        PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, KDF_ITERATIONS, KEY_BITS);
        SecretKeyFactory factory;
        try {
            factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        } catch (Exception e) {
            factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1");
        }
        return new SecretKeySpec(factory.generateSecret(spec).getEncoded(), "AES");
    }

    private static byte[] randomBytes(int count) {
        byte[] bytes = new byte[count];
        new java.security.SecureRandom().nextBytes(bytes);
        return bytes;
    }

    private static byte[] readUri(Context context, Uri uri) throws Exception {
        InputStream in = new BufferedInputStream(context.getContentResolver().openInputStream(uri));
        if (in == null) {
            throw new IllegalArgumentException("Unable to open backup package");
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        long total = 0;
        try {
            int read;
            while ((read = in.read(buffer)) != -1) {
                total += read;
                if (total > MAX_BACKUP_BYTES) {
                    throw new IllegalArgumentException("Backup package is too large");
                }
                out.write(buffer, 0, read);
            }
        } finally {
            in.close();
        }
        return out.toByteArray();
    }

    private static boolean startsWithMagic(byte[] bytes) {
        return bytes != null && bytes.length >= ENCRYPTED_MAGIC.length && matchesMagic(bytes);
    }

    private static boolean matchesMagic(byte[] bytes) {
        if (bytes == null || bytes.length < ENCRYPTED_MAGIC.length) return false;
        for (int i = 0; i < ENCRYPTED_MAGIC.length; i++) {
            if (bytes[i] != ENCRYPTED_MAGIC[i]) return false;
        }
        return true;
    }

    private static String manifestValue(String manifest, String key) {
        if (manifest == null || key == null) return "";
        String prefix = key + "=";
        String[] lines = manifest.split("\\n");
        for (String line : lines) {
            if (line.startsWith(prefix)) {
                return line.substring(prefix.length()).trim();
            }
        }
        return "";
    }

    private static String readText(File file) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        InputStream in = new BufferedInputStream(new FileInputStream(file));
        byte[] buffer = new byte[4096];
        try {
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
        } finally {
            in.close();
        }
        return out.toString("UTF-8");
    }

    private static String joinSet(List<String> values) {
        StringBuilder out = new StringBuilder();
        for (String value : values) {
            if (out.length() > 0) out.append('\u001F');
            out.append(value);
        }
        return out.toString();
    }

    private static Set<String> splitSet(String value) {
        Set<String> set = new HashSet<>();
        if (value == null || value.length() == 0) return set;
        String[] parts = value.split(String.valueOf('\u001F'), -1);
        Collections.addAll(set, parts);
        return set;
    }

    private static String encode(String value) {
        if (value == null) value = "";
        return Base64.encodeToString(value.getBytes(), Base64.NO_WRAP);
    }

    private static String decode(String value) {
        try {
            return new String(Base64.decode(value, Base64.NO_WRAP));
        } catch (Exception e) {
            return "";
        }
    }

    private static void copyFile(File source, File dest) throws Exception {
        InputStream in = new BufferedInputStream(new FileInputStream(source));
        OutputStream out = new BufferedOutputStream(new FileOutputStream(dest, false));
        byte[] buffer = new byte[8192];
        try {
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
        } finally {
            in.close();
            out.close();
        }
    }

    private static String relativeName(File root, File file) {
        String rootPath = root.getAbsolutePath();
        String filePath = file.getAbsolutePath();
        if (!filePath.startsWith(rootPath)) return file.getName();
        String name = filePath.substring(rootPath.length());
        if (name.startsWith(File.separator)) name = name.substring(1);
        return name.replace(File.separatorChar, '/');
    }

    private static boolean isSafeEntry(String name) {
        return name != null
                && name.length() > 0
                && !name.startsWith("/")
                && !name.contains("\\")
                && !name.contains("..");
    }
}
