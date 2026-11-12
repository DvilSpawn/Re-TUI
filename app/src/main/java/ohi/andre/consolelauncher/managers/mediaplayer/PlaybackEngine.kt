package ohi.andre.consolelauncher.managers.mediaplayer

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import ohi.andre.consolelauncher.tuils.Tuils

/**
 * How the launcher plays anything: music, podcasts and video all run on this. The player lives on the main thread;
 * calls from elsewhere go through [onMain], and state read from elsewhere is the last the main thread saw.
 *
 * Video gets its own instance so that watching something does not lose the place in what was playing.
 */
@OptIn(UnstableApi::class)
internal class PlaybackEngine(
    context: Context,
    kind: Kind,
    private val onChanged: () -> Unit = {},
    private val onEnded: () -> Unit = {},
    private val onTick: () -> Unit = {},
    private val onError: () -> Unit = {},
) {
    enum class Kind(val contentType: Int, val keepsNetworkAwake: Boolean) {
        AUDIO(C.AUDIO_CONTENT_TYPE_MUSIC, true),
        VIDEO(C.AUDIO_CONTENT_TYPE_MOVIE, false),
    }

    private val main = Handler(Looper.getMainLooper())

    val player: ExoPlayer = ExoPlayer.Builder(context)
        .setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(kind.contentType).build(), /* handleAudioFocus = */ true)
        .setHandleAudioBecomingNoisy(true)
        .apply { if (kind.keepsNetworkAwake) setWakeMode(C.WAKE_MODE_NETWORK) }
        .apply { if (kind == Kind.AUDIO) setMediaSourceFactory(StreamCache.mediaSources(context)) }
        .build()

    @Volatile var preparing = false

    @Volatile private var cachedPosition = 0
    @Volatile private var cachedDuration = 0
    @Volatile private var cachedPlaying = false

    private val progressTick = object : Runnable {
        override fun run() {
            refreshState()
            onTick()
            if (player.isPlaying) main.postDelayed(this, 1000L)
        }
    }

    init {
        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) preparing = false
                if (state == Player.STATE_ENDED) onEnded()
                refreshState()
                onChanged()
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                refreshState()
                if (isPlaying) { main.removeCallbacks(progressTick); main.post(progressTick) }
                onChanged()
            }

            override fun onPlayerError(error: PlaybackException) {
                Tuils.log(error)
                preparing = false
                refreshState()
                onChanged()
                onError()
            }
        })
    }

    private val onMainThread get() = Looper.myLooper() == Looper.getMainLooper()

    private fun currentDuration() = player.duration.let { if (it == C.TIME_UNSET || it < 0) 0 else it.toInt() }

    val positionMs: Int get() = if (onMainThread) player.currentPosition.coerceAtLeast(0L).toInt() else cachedPosition
    val durationMs: Int get() = if (onMainThread) currentDuration() else cachedDuration
    val isPlaying: Boolean get() = if (onMainThread) player.isPlaying else cachedPlaying

    fun onMain(action: () -> Unit) {
        if (onMainThread) action() else main.post(action)
    }

    fun refreshState() {
        cachedPosition = player.currentPosition.coerceAtLeast(0L).toInt()
        cachedDuration = currentDuration()
        cachedPlaying = player.isPlaying
    }

    fun release() {
        main.removeCallbacks(progressTick)
        player.release()
    }
}
