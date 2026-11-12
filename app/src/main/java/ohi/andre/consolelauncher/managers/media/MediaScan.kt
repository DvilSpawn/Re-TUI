package ohi.andre.consolelauncher.managers.media

import android.content.Context
import ohi.andre.consolelauncher.LauncherActivity
import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.tuils.Tuils

/** Scans the library off the main thread, reloads the music list, and reports what it found. */
object MediaScan {
    @Volatile private var running = false

    fun start(context: Context, prefix: String? = null) {
        if (running) return
        running = true
        Thread {
            try {
                val items = MediaLibrary.scan(context)
                val audio = items.count { !it.video }
                val video = items.size - audio
                LauncherActivity.instance?.let { launcher ->
                    launcher.runOnUiThread { runCatching { launcher.uiManager?.pack?.player?.updateSongs() } }
                }
                val found = context.resources.getQuantityString(R.plurals.media_tracks, audio, audio) + " · " +
                    context.resources.getQuantityString(R.plurals.media_videos, video, video)
                Tuils.sendOutput(context, listOfNotNull(prefix, found).joinToString("\n"))
            } finally {
                running = false
            }
        }.start()
    }
}
