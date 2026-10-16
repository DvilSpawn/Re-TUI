package dev.retui.test.presetrecipient;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

/** Independent APK: deliberately not the instrumentation source package. */
public final class ReadProvider extends ContentProvider {
    @Override public boolean onCreate() { return true; }
    @Override public Bundle call(String method, String argument, Bundle extras) {
        if (!"read-share".equals(method)) throw new IllegalArgumentException("Unknown probe");
        Uri uri = Uri.parse(argument);
        if (!"content".equals(uri.getScheme()) || uri.getAuthority() == null ||
                !uri.getAuthority().endsWith(".FILE_PROVIDER") || uri.getPath() == null ||
                !uri.getPath().startsWith("/cache_files/preset-share/"))
            throw new IllegalArgumentException("Only cached preset shares may be probed");
        Bundle result = new Bundle();
        result.putInt("uid", android.os.Process.myUid());
        // Read outside the caller's Binder transaction, using only this APK's permissions.
        FutureTask<Void> read = new FutureTask<>(() -> {
            result.putInt("readPermission", getContext().checkUriPermission(uri,
                    android.os.Process.myPid(), android.os.Process.myUid(),
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION));
            if (android.os.Build.VERSION.SDK_INT >= 35) result.putInt("fullReadPermission", getContext().checkContentUriPermissionFull(uri,
                    android.os.Process.myPid(), android.os.Process.myUid(), android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION));
            try (InputStream input = getContext().getContentResolver().openInputStream(uri)) {
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                byte[] buffer = new byte[8192];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    if (output.size() + count > 4 * 1024 * 1024) throw new IllegalArgumentException("Probe exceeds preset limit");
                    output.write(buffer, 0, count);
                }
                result.putByteArray("bytes", output.toByteArray());
                result.putString("mime", getContext().getContentResolver().getType(uri));
            } catch (Exception error) { result.putString("error", error.toString()); }
            return null;
        });
        new Thread(read, "independent-preset-reader").start();
        try { read.get(10, TimeUnit.SECONDS); }
        catch (Exception error) { throw new IllegalStateException("Probe did not complete", error); }
        return result;
    }
    @Override public String getType(Uri uri) { return null; }
    @Override public Cursor query(Uri uri, String[] projection, String selection, String[] args, String sort) { return null; }
    @Override public Uri insert(Uri uri, ContentValues values) { throw new UnsupportedOperationException(); }
    @Override public int delete(Uri uri, String selection, String[] args) { throw new UnsupportedOperationException(); }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] args) { throw new UnsupportedOperationException(); }
}
