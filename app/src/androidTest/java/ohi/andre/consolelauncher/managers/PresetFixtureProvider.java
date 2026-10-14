package ohi.andre.consolelauncher.managers;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Read-only fixtures served by the test APK without depending on target-app classes. */
public class PresetFixtureProvider extends ContentProvider {
    @Override public boolean onCreate() { return true; }
    @Override public String getType(Uri uri) { return "legacy.retui-backup".equals(uri.getLastPathSegment()) ? "application/octet-stream" : "application/vnd.retui+zip"; }
    @Override public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) { return null; }
    @Override public Uri insert(Uri uri, ContentValues values) { throw new UnsupportedOperationException(); }
    @Override public int delete(Uri uri, String selection, String[] selectionArgs) { return 0; }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) { return 0; }
    @Override public android.os.Bundle call(String method, String argument, android.os.Bundle extras) {
        if (!"read-share".equals(method)) return null;
        android.os.Bundle result = new android.os.Bundle();
        result.putInt("uid", android.os.Process.myUid());
        Uri uri = Uri.parse(argument);
        // Read as the recipient process, rather than inheriting the sender's Binder identity.
        long identity = android.os.Binder.clearCallingIdentity();
        try (java.io.InputStream input = getContext().getContentResolver().openInputStream(uri)) {
            java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1) bytes.write(buffer, 0, count);
            result.putByteArray("bytes", bytes.toByteArray());
            result.putString("mime", getContext().getContentResolver().getType(uri));
        } catch (Exception error) { result.putString("error", error.toString()); }
        finally { android.os.Binder.restoreCallingIdentity(identity); }
        return result;
    }
    @Override public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        String variant = uri.getLastPathSegment();
        if (!"r".equals(mode) || !("legacy.retui-backup".equals(variant) || "selected.retui".equals(variant) || "personal.retui".equals(variant)))
            throw new FileNotFoundException("Unknown fixture");
        boolean personal = "personal.retui".equals(variant);
        boolean selected = "selected.retui".equals(variant);
        String manifest = personal ? "type=retui-backup\nschema=1\nprofile=personal\n" :
            "type=retui-shareable-config\nschema=" + (selected ? "3" : "1") + "\nprofile=shareable\n" +
            "sections=theme,suggestions" + (selected ? ",behavior" : "") + "\npresetName=Portable fixture\nappVersion=fixture\n" +
            (selected ? "privacy=user-selected\nbehaviorFields=double_tap_cmd\n" : "");
        File file = new File(getContext().getCacheDir(), variant);
        Map<String, String> entries = new LinkedHashMap<>();
        entries.put("manifest.txt", manifest);
        entries.put("theme.xml", "<THEME/>");
        entries.put("suggestions.xml", "<SUGGESTIONS/>");
        if (selected) entries.put("behavior.xml", "<BEHAVIOR><double_tap_cmd value=\"echo inspected-not-run\"/></BEHAVIOR>");
        try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(file))) {
            for (Map.Entry<String, String> entry : entries.entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey()));
                zip.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        } catch (IOException error) { throw new FileNotFoundException(error.getMessage()); }
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
    }
}
