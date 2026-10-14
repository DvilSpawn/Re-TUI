package ohi.andre.consolelauncher.managers.settings;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.IOException;

/** Java keeps this provider independent of the target APK's Kotlin runtime. */
public class PaletteFixtureProvider extends ContentProvider {
    @Override public boolean onCreate() { return true; }
    @Override public String getType(Uri uri) { return "application/vnd.retui+zip"; }
    @Override public Cursor query(Uri uri, String[] projection, String selection, String[] args, String sort) { return null; }
    @Override public Uri insert(Uri uri, ContentValues values) { throw new UnsupportedOperationException(); }
    @Override public int delete(Uri uri, String selection, String[] args) { throw new UnsupportedOperationException(); }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] args) { throw new UnsupportedOperationException(); }
    @Override public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        String name = uri.getLastPathSegment();
        if (!"r".equals(mode) || name == null || !name.matches("[a-z0-9-]+\\.retui")) throw new FileNotFoundException();
        File file = new File(getContext().getCacheDir(), name);
        try (InputStream input = getContext().getAssets().open("terminal-colors/downloads/" + name);
             FileOutputStream output = new FileOutputStream(file)) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
        } catch (IOException error) { throw new FileNotFoundException(error.getMessage()); }
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
    }
}
