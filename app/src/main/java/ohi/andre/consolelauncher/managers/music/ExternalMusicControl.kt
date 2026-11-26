package ohi.andre.consolelauncher.managers.music

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.SystemClock
import android.text.TextUtils
import android.view.KeyEvent
import ohi.andre.consolelauncher.managers.notifications.NotificationService
import ohi.andre.consolelauncher.managers.settings.MusicSettings

/**
 * Drives another music app when the built-in player is off. Prefers a live media session (the preferred_music_app one
 * first), then a media button aimed at the preferred app, then a system media key that goes to whichever app last played.
 */
object ExternalMusicControl {
    enum class Action(val keyCode: Int) {
        PLAY_PAUSE(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE),
        NEXT(KeyEvent.KEYCODE_MEDIA_NEXT),
        PREVIOUS(KeyEvent.KEYCODE_MEDIA_PREVIOUS),
        STOP(KeyEvent.KEYCODE_MEDIA_STOP)
    }

    @JvmStatic
    fun send(context: Context, action: Action) {
        val preferred = MusicSettings.preferredPackage()?.takeUnless { TextUtils.isEmpty(it) }
        val controller = pickController(context, preferred)
        if (controller != null) {
            val controls = controller.transportControls
            when (action) {
                Action.PLAY_PAUSE ->
                    if (controller.playbackState?.state == PlaybackState.STATE_PLAYING) controls.pause() else controls.play()
                Action.NEXT -> controls.skipToNext()
                Action.PREVIOUS -> controls.skipToPrevious()
                Action.STOP -> controls.stop()
            }
            return
        }

        if (preferred != null) {
            for (keyAction in intArrayOf(KeyEvent.ACTION_DOWN, KeyEvent.ACTION_UP)) {
                val event = KeyEvent(SystemClock.uptimeMillis(), SystemClock.uptimeMillis(), keyAction, action.keyCode, 0)
                context.sendBroadcast(Intent(Intent.ACTION_MEDIA_BUTTON).setPackage(preferred).putExtra(Intent.EXTRA_KEY_EVENT, event))
            }
            return
        }

        val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, action.keyCode))
        audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, action.keyCode))
    }

    /**
     * Opens the preferred music app, or else the app whose session is playing (or last held one). False when there is
     * neither, or the app has no launcher entry.
     */
    @JvmStatic
    fun open(context: Context): Boolean {
        val preferred = MusicSettings.preferredPackage()?.takeUnless { TextUtils.isEmpty(it) }
        val pkg = preferred ?: pickController(context, null)?.packageName ?: return false
        val launch = context.packageManager.getLaunchIntentForPackage(pkg) ?: return false
        context.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        return true
    }

    /** What another music app is playing; positions in ms, 0 when the app gives no length. */
    data class NowPlaying(val title: String?, val artist: String?, val position: Long, val duration: Long)

    /** The preferred app's session, else the one playing; null without notification access or a session (#36). */
    @JvmStatic
    fun nowPlaying(context: Context): NowPlaying? {
        val controller = pickController(context, MusicSettings.preferredPackage()?.takeUnless { TextUtils.isEmpty(it) }) ?: return null
        val metadata = controller.metadata
        val state = controller.playbackState
        // The session reports where it was at its last update; run that on while it plays.
        val position = when {
            state == null -> 0L
            state.state == PlaybackState.STATE_PLAYING ->
                state.position + ((SystemClock.elapsedRealtime() - state.lastPositionUpdateTime) * state.playbackSpeed).toLong()
            else -> state.position
        }
        return NowPlaying(
            metadata?.getString(MediaMetadata.METADATA_KEY_TITLE),
            metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST),
            position.coerceAtLeast(0L),
            metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION)?.coerceAtLeast(0L) ?: 0L
        )
    }

    /** Needs notification access; without it there are no sessions to pick from and send falls back to key events. */
    private fun pickController(context: Context, preferred: String?): MediaController? {
        val sessions = try {
            val manager = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
            manager.getActiveSessions(ComponentName(context, NotificationService::class.java))
        } catch (e: SecurityException) {
            return null
        }.filter { it.packageName != context.packageName }

        if (preferred != null) return sessions.firstOrNull { it.packageName == preferred }
        return sessions.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING } ?: sessions.firstOrNull()
    }
}
