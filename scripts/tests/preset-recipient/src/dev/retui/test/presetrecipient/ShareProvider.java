package dev.retui.test.presetrecipient;
import android.content.*;
import android.database.Cursor;
import android.net.Uri;
import android.os.*;
import java.io.*;
/** Private provider owned by the standalone sender APK. */
public final class ShareProvider extends ContentProvider {
 public boolean onCreate(){return true;}
 public ParcelFileDescriptor openFile(Uri uri,String mode)throws FileNotFoundException {
  if(!"r".equals(mode)||!"/cache_files/preset-share/revoke.retui".equals(uri.getPath()))throw new FileNotFoundException();
  return ParcelFileDescriptor.open(new File(getContext().getCacheDir(),"revoke.retui"),ParcelFileDescriptor.MODE_READ_ONLY);
 }
 public String getType(Uri uri){return "application/vnd.retui+zip";}
 public Cursor query(Uri u,String[]p,String s,String[]a,String o){return null;}
 public Uri insert(Uri u,ContentValues v){throw new UnsupportedOperationException();}
 public int delete(Uri u,String s,String[]a){throw new UnsupportedOperationException();}
 public int update(Uri u,ContentValues v,String s,String[]a){throw new UnsupportedOperationException();}
}
