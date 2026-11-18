package ohi.andre.consolelauncher.managers.music

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.AudioManager
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
