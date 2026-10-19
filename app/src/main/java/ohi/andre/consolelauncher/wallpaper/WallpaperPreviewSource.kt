package ohi.andre.consolelauncher.wallpaper

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import java.lang.ref.WeakReference

/** Reads the already-running wallpaper renderer; never binds a new engine or requests permissions. */
internal object WallpaperPreviewSource {
    interface Source { fun capturePreview(result: (Bitmap?)->Unit) }
    private var source=WeakReference<Source>(null)
    fun register(value: Source) { source=WeakReference(value) }
    fun unregister(value: Source) { if (source.get()===value) source.clear() }
    fun capture(context: Context,result: (Bitmap?)->Unit) {
        val main=Handler(Looper.getMainLooper())
        main.post {
            var completed=false
            var delivered: Bitmap?=null
            lateinit var timeout: Runnable
            fun finish(bitmap: Bitmap?) {
                if (completed) { if (bitmap!==delivered) bitmap?.recycle();return }
                completed=true
                delivered=bitmap
                main.removeCallbacks(timeout)
                result(bitmap)
            }
            timeout=Runnable { finish(null) }
            val own=runCatching { WallpaperManager.getInstance(context).wallpaperInfo?.packageName==context.packageName }.getOrDefault(false)
            val current=if (own) source.get() else null
            if (current==null) finish(null) else {
                main.postDelayed(timeout,1500)
                try {
                    current.capturePreview { bitmap ->
                        if (Looper.myLooper()==Looper.getMainLooper()) finish(bitmap) else main.post { finish(bitmap) }
                    }
                } catch (_: Exception) { finish(null) }
            }
        }
    }

    /** A thumbnail never needs a full-resolution display buffer. */
    fun bitmap(width: Int,height: Int): Bitmap? {
        if (width<=0 || height<=0) return null
        val scale=minOf(1f,1280f/maxOf(width,height))
        return runCatching {
            Bitmap.createBitmap((width*scale).toInt().coerceAtLeast(1),(height*scale).toInt().coerceAtLeast(1),Bitmap.Config.ARGB_8888)
        }.getOrNull()
    }
}
