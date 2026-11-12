package ohi.andre.consolelauncher.managers.media

import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Typeface
import android.graphics.drawable.Icon
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Rational
import android.view.Gravity
import android.view.TextureView
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.SeekBar
import android.widget.Space
import android.widget.TextView
import androidx.activity.addCallback
import androidx.annotation.OptIn
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.VideoSize
import androidx.media3.common.text.CueGroup
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.session.MediaSession
import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.managers.mediaplayer.MediaPlayerActivity
import ohi.andre.consolelauncher.managers.mediaplayer.PlaybackEngine
import ohi.andre.consolelauncher.tuils.Tuils
import java.util.Locale

/**
 * The video player: full screen, or native picture-in-picture over the library and other apps, without playback
 * restarting. It owns its player; taking audio focus is what pauses any music playing in MusicService.
 */
@OptIn(UnstableApi::class)
class VideoPlayerActivity : VideoActivity() {
    private enum class Mode { FULL, PIP }

    private lateinit var engine: PlaybackEngine
    private val player get() = engine.player
    private lateinit var audio: AudioManager
    private var session: MediaSession? = null
    private val main = Handler(Looper.getMainLooper())

    private var mode = Mode.FULL
    private var safe = Insets.NONE
    private var playlist: List<MediaLibrary.Item> = emptyList()
    private var playlistOpen = false
    private var crop = false
    private var videoW = 0f
    private var videoH = 0f
    private val speedKey = "__speed"
    private var speed = 1f
    private var ccIndex = -1
    private var dragging = false
    private var holding = false
    private var ticks = 0
    private var trackedUri: String? = null
    private var trackedPos = 0L
    private var trackedDur = 0L

    private lateinit var root: FrameLayout
    private lateinit var pane: FrameLayout
    private lateinit var video: TextureView
    private lateinit var subtitle: TextView
    private lateinit var controls: FrameLayout
    private lateinit var topRow: LinearLayout
    private lateinit var strip: LinearLayout
    private lateinit var title: TextView
    private lateinit var nextUp: TextView
    /** Autoplay stopped on a finished episode; sitting at its start must not undo its watched state. */
    private var parkedOnWatched = false
    private lateinit var listTitle: TextView
    private lateinit var listColumn: LinearLayout
    private lateinit var back10: TextView
    private lateinit var pause: TextView
    private lateinit var forward10: TextView
    private lateinit var gapLeft: Space
    private lateinit var gapRight: Space
    private lateinit var cc: TextView
    private lateinit var audioButton: TextView
    private lateinit var speedButton: TextView
    private lateinit var fit: TextView
    private lateinit var pip: TextView
    private lateinit var list: TextView
    private lateinit var volGroup: LinearLayout
    private lateinit var volBar: SeekBar
    private lateinit var volText: TextView
    private lateinit var seeking: TextView
    private lateinit var position: TextView
    private lateinit var seekBar: SeekBar
    private lateinit var duration: TextView
    private val playlistRows = Rows()

    private val autoHide = Runnable { hideControls() }
    private val tick = object : Runnable {
        override fun run() {
            track()
            if (!holding && !dragging) showTime(player.currentPosition)
            showNextUp()
            if (++ticks % 10 == 0) saveTracked()
            main.postDelayed(this, 500L)
        }
    }
    private val pipReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.getIntExtra(EXTRA_PIP, 0)) {
                PIP_BACK -> jump(-TEN_SECONDS)
                PIP_TOGGLE -> togglePlay()
                PIP_FORWARD -> jump(TEN_SECONDS)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        audio = getSystemService(AUDIO_SERVICE) as AudioManager
        engine = PlaybackEngine(this, PlaybackEngine.Kind.VIDEO)
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true).build()
        player.addListener(playerListener)
        // Headset buttons, Bluetooth and anything reading the system's media sessions can see and steer the video.
        session = MediaSession.Builder(this, player).setId("video").build()

        root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK); isFocusableInTouchMode = true; clipChildren = false }
        pane = buildPane()
        root.addView(pane)
        setContentView(root)
        root.requestFocus()
        player.setVideoTextureView(video)

        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            safe = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout() or WindowInsetsCompat.Type.ime())
            layoutPane()
            WindowInsetsCompat.CONSUMED
        }
        onBackPressedDispatcher.addCallback(this) { goBack() }
        ContextCompat.registerReceiver(this, pipReceiver, IntentFilter(ACTION_PIP), ContextCompat.RECEIVER_NOT_EXPORTED)
        speed = resume.getFloat(speedKey, 1f)
        speedButton.text = String.format(Locale.ROOT, "%sx", speed)
        setMode(Mode.FULL)
        handleIntent(intent)
        // Nothing to show if the system brought this back without a video.
        if (player.mediaItemCount == 0) finish()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        val uri = intent.getStringExtra(EXTRA_URI) ?: return
        val item = videos().firstOrNull { it.uri == uri } ?: return
        // After start over the library has cleared these positions; the old video must not write its own back.
        if (intent.getBooleanExtra(EXTRA_FORGET, false)) trackedUri = null
        open(item, intent.getLongExtra(EXTRA_START_AT, 0))
    }

    // ---- views ----

    private fun buildPane(): FrameLayout {
        video = TextureView(this).apply { addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> applyScale() } }
        subtitle = label(size = 16f).apply {
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setShadowLayer(4f, 0f, 1f, Color.BLACK)
        }
        back10 = button(getString(R.string.media_video_back10), getString(R.string.media_video_back10_desc), 76, CENTER, 18f) { jump(-TEN_SECONDS) }
        forward10 = button(getString(R.string.media_video_forward10), getString(R.string.media_video_forward10_desc), 76, CENTER, 18f) { jump(TEN_SECONDS) }
        back10.setOnLongClickListener { startHold(back10, -1); true }
        forward10.setOnLongClickListener { startHold(forward10, 1); true }
        pause = button(getString(R.string.media_video_pause), getString(R.string.media_video_pause), 108, CENTER, 18f) { togglePlay() }
        gapLeft = Space(this)
        gapRight = Space(this)
        val center = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            addView(back10); addView(gapLeft); addView(pause); addView(gapRight); addView(forward10)
        }

        val goBackButton = button("<", getString(R.string.media_video_back), 44, BAR) { goBack() }
        cc = button(getString(R.string.media_video_cc), getString(R.string.media_video_cc_desc), 52, BAR) { cycleSubtitles() }
        audioButton = button(getString(R.string.media_video_audio), getString(R.string.media_video_audio_desc), 52, BAR) { cycleAudio() }
        speedButton = button("1.0x", getString(R.string.media_video_speed_desc), 52, BAR) { cycleSpeed() }
        fit = button(getString(R.string.media_video_fit), getString(R.string.media_video_fit_desc), 52, BAR) { toggleCrop() }
        pip = button(getString(R.string.media_video_pip), getString(R.string.media_video_pip_desc), 52, BAR) { enterPip() }
        list = button(getString(R.string.media_video_list), getString(R.string.media_video_list_desc), 52, BAR) { togglePlaylist() }
        if (!pipSupported()) pip.visibility = View.GONE
        nextUp = label(color = dim).apply { isSingleLine = true; ellipsize = android.text.TextUtils.TruncateAt.END }
        topRow = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            addView(goBackButton)
            addView(nextUp, LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = dp(10) })
            listOf(cc, audioButton, speedButton, fit, pip, list).forEach { b ->
                addView(b, LinearLayout.LayoutParams(dp(52), dp(TOUCH)).apply { leftMargin = dp(6) })
            }
        }

        volBar = slider(getString(R.string.media_video_volume_desc)).apply {
            setOnSeekBarChangeListener(sliderListener { p, user ->
                if (user) audio.setStreamVolume(AudioManager.STREAM_MUSIC, p * audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC) / SLIDER_MAX, 0)
                volText.text = "${p * 100 / SLIDER_MAX}%"
            })
        }
        volText = label()
        volGroup = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            addView(label(getString(R.string.media_video_vol)))
            addView(volBar, LinearLayout.LayoutParams(dp(110), -1))
            addView(volText)
        }
        seeking = label(color = sel).apply { visibility = View.GONE }
        position = label().apply { setPadding(dp(4), 0, dp(4), 0) }
        seekBar = slider(getString(R.string.media_video_seek_desc)).apply {
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(bar: SeekBar, p: Int, user: Boolean) {
                    if (user) position.text = VideoMath.clock(knownDuration() * p / SLIDER_MAX)
                    if (user) touched()
                }
                override fun onStartTrackingTouch(bar: SeekBar) { dragging = true }
                override fun onStopTrackingTouch(bar: SeekBar) {
                    dragging = false
                    player.seekTo(knownDuration() * bar.progress / SLIDER_MAX)
                    touched()
                }
            })
        }
        duration = label()
        strip = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(View(context).apply { setBackgroundColor(border) }, LinearLayout.LayoutParams(-1, maxOf(1, dp(1))))
            addView(LinearLayout(context).apply {
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(12), 0, dp(12), 0)
                addView(volGroup, LinearLayout.LayoutParams(-2, -1).apply { rightMargin = dp(10) })
                addView(seeking, LinearLayout.LayoutParams(-2, -2).apply { rightMargin = dp(10) })
                addView(position)
                addView(seekBar, LinearLayout.LayoutParams(0, -1, 1f))
                addView(duration)
            }, LinearLayout.LayoutParams(-1, dp(47)))
        }

        controls = FrameLayout(this).apply {
            setBackgroundColor(SCRIM)
            setOnClickListener { hideControls() }
            addView(topRow, FrameLayout.LayoutParams(-1, -2, Gravity.TOP).apply { leftMargin = dp(10); rightMargin = dp(10); topMargin = dp(4) })
            addView(center, FrameLayout.LayoutParams(-2, -2, Gravity.CENTER))
            addView(strip, FrameLayout.LayoutParams(-1, dp(48), Gravity.BOTTOM))
        }
        val box = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
            setOnClickListener { showControls() }
            addView(video, FrameLayout.LayoutParams(-1, -1))
            addView(subtitle, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM).apply { leftMargin = dp(24); rightMargin = dp(24); bottomMargin = dp(16) })
            addView(controls, FrameLayout.LayoutParams(-1, -1))
        }
        val playlistView = list().apply {
            adapter = playlistRows
            setBackgroundColor(Color.BLACK)
            setPadding(0, dp(14), 0, 0)
            clipToPadding = false
            setOnItemClickListener { _, _, position, _ -> playlistRows.rows[position].n.let { playIndex(it - 1) }; touched() }
        }
        listColumn = LinearLayout(this).apply {
            visibility = View.GONE
            addView(View(context).apply { setBackgroundColor(border) }, LinearLayout.LayoutParams(maxOf(1, dp(1)), -1))
            addView(playlistView, LinearLayout.LayoutParams(0, -1, 1f))
        }
        title = titled("")
        listTitle = titled("").apply { visibility = View.GONE }
        return FrameLayout(this).apply {
            clipChildren = false
            addView(LinearLayout(context).apply {
                addView(box, LinearLayout.LayoutParams(0, -1, 1f))
                addView(listColumn, LinearLayout.LayoutParams(dp(270), -1))
            }, FrameLayout.LayoutParams(-1, -1))
            addView(title, FrameLayout.LayoutParams(-2, -2).apply { leftMargin = dp(14) })
            addView(listTitle, FrameLayout.LayoutParams(-2, -2, Gravity.END).apply { rightMargin = dp(14) })
        }
    }

    private fun sliderListener(changed: (Int, Boolean) -> Unit) = object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(bar: SeekBar, p: Int, user: Boolean) { changed(p, user); if (user) touched() }
        override fun onStartTrackingTouch(bar: SeekBar) {}
        override fun onStopTrackingTouch(bar: SeekBar) {}
    }

    private fun track() {
        if (parkedOnWatched) { if (player.isPlaying) parkedOnWatched = false else return }
        val uri = currentUri() ?: return
        if (uri != trackedUri) return
        trackedPos = player.currentPosition
        trackedDur = knownDuration().takeIf { it > 0 } ?: trackedDur
    }

    private fun saveTracked() {
        val uri = trackedUri ?: return
        resume.edit().putString(uri, "$trackedPos|$trackedDur").putString(KEY_LAST, uri).apply()
    }

    private fun currentUri() = player.currentMediaItem?.mediaId
    private fun knownDuration() = engine.durationMs.toLong()

    private fun open(item: MediaLibrary.Item, startAt: Long) {
        track()
        saveTracked()
        playlist = videos().filter { it.folder == item.folder }
        playlistRows.rows = playlist.mapIndexed { i, v -> Row(null, v, i + 1) }
        val dur = saved(item.uri).second
        player.setMediaItems(playlist.map(::mediaItem),
            playlist.indexOf(item).coerceAtLeast(0), startAt)
        trackedUri = item.uri
        nowPlaying = item.uri
        trackedPos = startAt
        trackedDur = dur
        listTitle.text = getString(R.string.media_video_playlist, item.folder.substringAfterLast('/'))
        title.text = item.name
        player.setPlaybackParameters(PlaybackParameters(speed))
        player.prepare()
        player.play()
        setMode(Mode.FULL)
    }

    private fun mediaItem(video: MediaLibrary.Item) = MediaItem.Builder().setUri(video.uri).setMediaId(video.uri)
        .setMediaMetadata(androidx.media3.common.MediaMetadata.Builder().setTitle(video.name).build())
        .setSubtitleConfigurations(video.subs.map {
            MediaItem.SubtitleConfiguration.Builder(Uri.parse(it)).setMimeType(VideoMath.subtitleMime(it)).build()
        }).build()

    /** Autoplay goes on to the next unfinished episode, picking up where that one was left. */
    private fun skipWatched(uri: String) {
        val at = playlist.indexOfFirst { it.uri == uri }
        if (at < 0) return
        val states = playlist.map { saved(it.uri) }
        val target = VideoMath.nextUnwatched(states, at)
        when {
            target < 0 -> { parkedOnWatched = true; player.pause(); if (mode == Mode.FULL) showControls() }
            target != at -> player.seekTo(target, VideoMath.resumeAt(states[target].first, states[target].second))
            VideoMath.canResume(states[at].first, states[at].second) -> player.seekTo(at, states[at].first)
        }
    }

    private fun showNextUp() {
        val at = playlist.indexOfFirst { it.uri == trackedUri }
        val next = if (at < 0) -1 else VideoMath.nextUnwatched(playlist.map { saved(it.uri) }, at + 1)
        val left = (knownDuration() - player.currentPosition).coerceAtLeast(0)
        nextUp.text = playlist.getOrNull(next)?.let { getString(R.string.media_video_next_up, it.name, (left + 59_999) / 60_000) }.orEmpty()
    }

    private fun playIndex(index: Int) {
        val item = playlist.getOrNull(index) ?: return
        if (item.uri == currentUri()) return
        val (pos, dur) = saved(item.uri)
        player.seekTo(index, VideoMath.resumeAt(pos, dur))
        player.prepare()
        player.play()
    }

    // ---- playback ----

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            showPlayState()
            root.keepScreenOn = isPlaying
            updatePip()
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) = showPlayState()

        override fun onVideoSizeChanged(size: VideoSize) {
            if (size.width == 0 || size.height == 0) return
            videoW = size.width * size.pixelWidthHeightRatio
            videoH = size.height.toFloat()
            applyScale()
            if (mode == Mode.FULL) requestedOrientation = fullOrientation()
            updatePip()
        }

        override fun onCues(cueGroup: CueGroup) {
            subtitle.text = cueGroup.cues.mapNotNull { it.text }.joinToString("\n")
        }

        override fun onMediaItemTransition(item: MediaItem?, reason: Int) {
            if (item == null || item.mediaId == trackedUri) return
            if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) trackedPos = trackedDur
            saveTracked()
            trackedUri = item.mediaId
            nowPlaying = item.mediaId
            val (pos, dur) = saved(item.mediaId)
            trackedPos = pos
            trackedDur = dur
            subtitle.text = ""
            ccIndex = -1
            applySubtitles()
            title.text = playlist.firstOrNull { it.uri == item.mediaId }?.name.orEmpty()
            playlistRows.notifyDataSetChanged()
            if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) skipWatched(item.mediaId)
        }

        override fun onPlaybackStateChanged(state: Int) {
            showPlayState()
            if (state == Player.STATE_ENDED) {
                trackedPos = trackedDur
                saveTracked()
                if (mode == Mode.FULL) showControls()
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            Tuils.log(error)
            subtitle.setText(R.string.media_video_cannot_play)
            if (mode == Mode.FULL) showControls()
        }

        override fun onTracksChanged(tracks: androidx.media3.common.Tracks) {
            cc.alpha = if (textGroups().isEmpty()) 0.4f else 1f
            layoutPane()
        }
    }

    /** True through buffering too, so the button does not flicker to PLAY during a seek. */
    private fun wantsPlay() = player.playWhenReady && player.playbackState != Player.STATE_ENDED

    private fun showPlayState() {
        pause.setText(if (wantsPlay()) R.string.media_video_pause else R.string.media_video_play)
        pause.contentDescription = pause.text
    }

    private fun togglePlay() {
        if (player.playbackState == Player.STATE_ENDED) player.seekToDefaultPosition(player.currentMediaItemIndex)
        if (player.playbackState == Player.STATE_IDLE) player.prepare()
        if (wantsPlay()) player.pause() else player.play()
    }

    private fun jump(ms: Long) {
        val target = (player.currentPosition + ms).coerceAtLeast(0)
        player.seekTo(knownDuration().takeIf { it > 0 }?.let { target.coerceAtMost(it) } ?: target)
        showTime(target)
    }

    private fun startHold(button: TextView, direction: Int) {
        if (knownDuration() <= 0) return
        holding = true
        val start = SystemClock.uptimeMillis()
        val origin = player.currentPosition
        var target = origin
        player.setSeekParameters(SeekParameters.CLOSEST_SYNC)
        main.removeCallbacks(autoHide)
        showSeeking(true)
        paint(button, true, sel)
        main.post(object : Runnable {
            override fun run() {
                // Pressed clears on lift, and also ends a long-press that came from TalkBack rather than a finger.
                if (!button.isPressed) {
                    holding = false
                    player.setSeekParameters(SeekParameters.DEFAULT)
                    player.seekTo(target)
                    showSeeking(false)
                    paintCenter()
                    touched()
                    return
                }
                target = (target + direction * VideoMath.holdStepMs(SystemClock.uptimeMillis() - start)).coerceIn(0, knownDuration())
                player.seekTo(target)
                showTime(target)
                val offset = target - origin
                seeking.text = getString(R.string.media_video_seeking, (if (offset < 0) "-" else "+") + VideoMath.clock(offset))
                main.postDelayed(this, HOLD_TICK)
            }
        })
    }

    private fun showSeeking(on: Boolean) {
        volGroup.visibility = if (on) View.GONE else View.VISIBLE
        seeking.visibility = if (on) View.VISIBLE else View.GONE
        position.setBackgroundColor(if (on) sel else Color.TRANSPARENT)
        position.setTextColor(if (on) Color.BLACK else ink)
        style(seekBar, if (on) sel else ink)
    }

    private fun showTime(ms: Long) {
        val dur = knownDuration()
        position.text = VideoMath.clock(ms)
        duration.text = VideoMath.clock(dur)
        seekBar.progress = if (dur > 0) (ms.coerceIn(0, dur) * SLIDER_MAX / dur).toInt() else 0
    }

    private fun textGroups() = player.currentTracks.groups.filter { it.type == C.TRACK_TYPE_TEXT }

    private fun cycleSubtitles() {
        val count = textGroups().size
        if (count == 0) return
        ccIndex = if (ccIndex + 1 >= count) -1 else ccIndex + 1
        applySubtitles()
    }

    private fun applySubtitles() {
        val builder = player.trackSelectionParameters.buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_TEXT, ccIndex < 0)
        textGroups().getOrNull(ccIndex)?.let { builder.setOverrideForType(TrackSelectionOverride(it.mediaTrackGroup, 0)) }
        player.trackSelectionParameters = builder.build()
        paint(cc, ccIndex >= 0, ink)
    }

    private fun audioGroups() = player.currentTracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }

    private fun cycleAudio() {
        val groups = audioGroups()
        if (groups.size < 2) return
        val next = groups[(groups.indexOfFirst { it.isSelected } + 1) % groups.size]
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setOverrideForType(TrackSelectionOverride(next.mediaTrackGroup, 0)).build()
    }

    private fun cycleSpeed() {
        speed = SPEEDS[(SPEEDS.indexOf(speed) + 1) % SPEEDS.size]
        player.setPlaybackParameters(PlaybackParameters(speed))
        speedButton.text = String.format(Locale.ROOT, "%sx", speed)
        resume.edit().putFloat(speedKey, speed).apply()
    }

    private fun toggleCrop() {
        crop = !crop
        fit.setText(if (crop) R.string.media_video_fill else R.string.media_video_fit)
        applyScale()
    }

    private fun applyScale() {
        val w = video.width
        val h = video.height
        if (w == 0 || h == 0 || videoW == 0f) return
        val (sx, sy) = VideoMath.scale(w, h, videoW, videoH, crop && mode == Mode.FULL)
        video.setTransform(Matrix().apply { setScale(sx, sy, w / 2f, h / 2f) })
        video.invalidate()
    }

    // ---- modes and controls ----

    private fun fullOrientation() =
        if (videoH > videoW) ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT else ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE

    private fun setMode(next: Mode) {
        mode = next
        if (next == Mode.FULL) requestedOrientation = fullOrientation()
        WindowInsetsControllerCompat(window, root).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
        // Subtitles sit above the controls in full screen and shrink to suit PiP.
        subtitle.animate().cancel()
        subtitle.translationY = 0f
        subtitle.textSize = if (next == Mode.PIP) 10f else 16f
        layoutPane()
        paintCenter()
        main.removeCallbacks(autoHide)
        when (next) {
            Mode.FULL -> showControls()
            Mode.PIP -> controls.visibility = View.GONE
        }
        topRow.visibility = if (next == Mode.FULL) View.VISIBLE else View.GONE
        strip.visibility = if (next == Mode.FULL) View.VISIBLE else View.GONE
        applyScale()
    }

    private fun layoutPane() {
        val params = FrameLayout.LayoutParams(-1, -1)
        when (mode) {
            Mode.FULL -> params.setMargins(dp(8) + safe.left, dp(12) + safe.top, dp(8) + safe.right, dp(8) + safe.bottom)
            Mode.PIP -> Unit
        }
        pane.layoutParams = params
        pane.background = if (mode == Mode.FULL) outline(border) else null
        title.visibility = if (mode == Mode.FULL) View.VISIBLE else View.GONE
        val showList = mode == Mode.FULL && playlistOpen
        listColumn.visibility = if (showList) View.VISIBLE else View.GONE
        listTitle.visibility = listColumn.visibility
        val extras = if (showList) View.GONE else View.VISIBLE
        cc.visibility = extras
        audioButton.visibility = if (showList || audioGroups().size < 2) View.GONE else View.VISIBLE
        speedButton.visibility = extras
        fit.visibility = extras
        paint(list, showList, if (showList) sel else ink)
    }

    private fun paintCenter() {
        val gap = dp(if (playlistOpen) 32 else 96)
        gapLeft.layoutParams = LinearLayout.LayoutParams(gap, 1)
        gapRight.layoutParams = LinearLayout.LayoutParams(gap, 1)
        paint(back10, false)
        paint(pause, false, sel)
        paint(forward10, false)
        pause.setTypeface(font, Typeface.BOLD)
    }

    private fun showControls() {
        if (mode != Mode.FULL) return
        if (controls.visibility != View.VISIBLE || controls.alpha < 1f) {
            controls.setBackgroundColor(SCRIM)
            controls.visibility = View.VISIBLE
            controls.animate().cancel()
            controls.alpha = 0f
            controls.animate().alpha(1f).setDuration(FADE).withEndAction(null).start()
            strip.translationY = dp(48).toFloat()
            strip.animate().translationY(0f).setDuration(FADE).start()
            subtitle.animate().translationY(-dp(48).toFloat()).setDuration(FADE).start()
        }
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        volBar.progress = audio.getStreamVolume(AudioManager.STREAM_MUSIC) * SLIDER_MAX / max
        showTime(player.currentPosition)
        touched()
    }

    override fun touched() {
        main.removeCallbacks(autoHide)
        if (mode == Mode.FULL && !playlistOpen && !holding) main.postDelayed(autoHide, AUTO_HIDE)
    }

    private fun hideControls() {
        if (mode != Mode.FULL || holding || dragging) return
        strip.animate().translationY(dp(48).toFloat()).setDuration(FADE).start()
        subtitle.animate().translationY(0f).setDuration(FADE).start()
        controls.animate().alpha(0f).setDuration(FADE).withEndAction { controls.visibility = View.INVISIBLE }.start()
    }

    private fun togglePlaylist() {
        playlistOpen = !playlistOpen
        playlistRows.notifyDataSetChanged()
        layoutPane()
        paintCenter()
    }

    /** Back floats a playing video over the library; a paused one is simply put away. */
    private fun goBack() {
        when {
            playlistOpen -> togglePlaylist()
            player.isPlaying && enterPip() -> showLibrary()
            else -> { showLibrary(); finish() }
        }
    }

    private fun showLibrary() = startActivity(Intent(this, MediaPlayerActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        .putExtra(MediaPlayerActivity.EXTRA_TAB, MediaPlayerActivity.Tab.VIDEO.name))

    // ---- picture-in-picture ----

    private fun pipSupported() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)

    private fun enterPip(): Boolean {
        if (!pipSupported() || player.mediaItemCount == 0) return false
        return runCatching { enterPictureInPictureMode(pipParams()) }.getOrDefault(false)
    }

    private fun updatePip() {
        if (pipSupported()) runCatching { setPictureInPictureParams(pipParams()) }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun pipParams(): PictureInPictureParams {
        val (w, h) = VideoMath.pipRatio(videoW.toInt(), videoH.toInt())
        val playing = player.isPlaying
        val builder = PictureInPictureParams.Builder()
            .setAspectRatio(Rational(w, h))
            .setActions(listOf(
                pipAction(androidx.media3.session.R.drawable.media3_icon_skip_back_10, R.string.media_video_back10_desc, PIP_BACK),
                pipAction(if (playing) androidx.media3.session.R.drawable.media3_icon_pause else androidx.media3.session.R.drawable.media3_icon_play,
                    if (playing) R.string.media_video_pause else R.string.media_video_play, PIP_TOGGLE),
                pipAction(androidx.media3.session.R.drawable.media3_icon_skip_forward_10, R.string.media_video_forward10_desc, PIP_FORWARD)
            ))
        // Going home mid-video slides straight into the window on Android 12 and up.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) builder.setAutoEnterEnabled(playing)
        return builder.build()
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun pipAction(icon: Int, label: Int, command: Int): RemoteAction {
        val intent = Intent(ACTION_PIP).setPackage(packageName).putExtra(EXTRA_PIP, command)
        return RemoteAction(Icon.createWithResource(this, icon), getString(label), getString(label),
            PendingIntent.getBroadcast(this, command, intent, Tuils.pendingIntentFlags(PendingIntent.FLAG_UPDATE_CURRENT)))
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S && player.isPlaying) enterPip()
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        if (isInPictureInPictureMode) {
            setMode(Mode.PIP)
        } else if (lifecycle.currentState == Lifecycle.State.CREATED) {
            // Stopped as it left PiP: the window was closed rather than expanded.
            finish()
        } else {
            setMode(Mode.FULL)
        }
    }

    override fun onStart() {
        super.onStart()
        main.post(tick)
    }

    override fun onStop() {
        super.onStop()
        main.removeCallbacks(tick)
        track()
        saveTracked()
        if (isFinishing) nowPlaying = null // The library redraws on resume, before this screen's onDestroy.
        if (!isPipNow()) player.pause()
    }

    private fun isPipNow() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && isInPictureInPictureMode

    override fun onDestroy() {
        nowPlaying = null
        main.removeCallbacksAndMessages(null)
        track()
        saveTracked()
        unregisterReceiver(pipReceiver)
        session?.release()
        engine.release()
        super.onDestroy()
    }

    private companion object {
        const val ACTION_PIP = "ohi.andre.consolelauncher.video_pip"
        const val EXTRA_PIP = "command"
        const val PIP_BACK = 1
        const val PIP_TOGGLE = 2
        const val PIP_FORWARD = 3
        const val TEN_SECONDS = 10_000L
        const val HOLD_TICK = 200L
        const val AUTO_HIDE = 3000L
        const val FADE = 180L
        const val SCRIM = 0x8C000000.toInt()
        val SPEEDS = listOf(1f, 1.25f, 1.5f, 2f, 0.75f)
    }
}
