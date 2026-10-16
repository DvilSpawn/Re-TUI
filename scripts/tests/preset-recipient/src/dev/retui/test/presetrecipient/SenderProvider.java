package dev.retui.test.presetrecipient;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

/** Independent sender. Each phase returns before the recipient performs its next read. */
public final class SenderProvider extends ContentProvider {
    private static final String RECIPIENT = "dev.retui.test.presetrecipient";
    @Override public boolean onCreate() { return true; }
    @Override public Bundle call(String method, String argument, Bundle extras) {
        Uri uri = Uri.parse("content://" + getContext().getPackageName() +
                ".FILE_PROVIDER/cache_files/preset-share/revoke.retui");
        if ("prepare".equals(method)) {
            try (FileOutputStream output = new FileOutputStream(new File(getContext().getCacheDir(), "revoke.retui"))) {
                output.write("independent-sender-preset".getBytes("UTF-8"));
            } catch (IOException error) { throw new IllegalStateException(error); }
            getContext().revokeUriPermission(RECIPIENT, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } else if ("grant".equals(method)) {
            getContext().grantUriPermission(RECIPIENT, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } else if ("revoke".equals(method)) {
            getContext().revokeUriPermission(RECIPIENT, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } else throw new IllegalArgumentException("Unknown sender phase");
        Bundle result = new Bundle();
        result.putString("phase", method);
        result.putInt("senderUid", android.os.Process.myUid());
        return result;
    }
    @Override public String getType(Uri uri) { return null; }
    @Override public Cursor query(Uri uri, String[] projection, String selection, String[] args, String sort) { return null; }
    @Override public Uri insert(Uri uri, ContentValues values) { throw new UnsupportedOperationException(); }
    @Override public int delete(Uri uri, String selection, String[] args) { throw new UnsupportedOperationException(); }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] args) { throw new UnsupportedOperationException(); }
}
