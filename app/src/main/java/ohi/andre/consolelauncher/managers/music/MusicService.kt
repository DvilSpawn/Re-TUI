package ohi.andre.consolelauncher.managers.music

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.provider.MediaStore
import androidx.annotation.OptIn
import androidx.core.app.NotificationCompat
import androidx.core.app.RemoteInput
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaNotification
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.MediaStyleNotificationHelper
import com.google.common.collect.ImmutableList
import ohi.andre.consolelauncher.LauncherActivity
import ohi.andre.consolelauncher.MainManager
import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.managers.mediaplayer.AudioEffects
import ohi.andre.consolelauncher.managers.mediaplayer.Chapter
import ohi.andre.consolelauncher.managers.mediaplayer.Chapters
import ohi.andre.consolelauncher.managers.mediaplayer.ForwardCache
import ohi.andre.consolelauncher.managers.mediaplayer.PlaybackRecovery
import ohi.andre.consolelauncher.managers.mediaplayer.StreamCache
import ohi.andre.consolelauncher.managers.mediaplayer.PlaybackEngine
import ohi.andre.consolelauncher.managers.mediaplayer.PlayQueue
import ohi.andre.consolelauncher.managers.mediaplayer.QueueStore
import ohi.andre.consolelauncher.tuils.PrivateIOReceiver
import ohi.andre.consolelauncher.tuils.PublicIOReceiver
import ohi.andre.consolelauncher.tuils.Tuils

/**
 * Music and podcast playback on Media3: ExoPlayer under a media session, so the system's media controls, the
 * lock screen, headsets and other apps' now-playing views all see what plays. The launcher still drives it through
 * the same calls as before (a list, an index, play, pause, seek) and hears back through [ACTION_MUSIC_CHANGED].
 *
 * The player lives on the main thread. Calls from elsewhere are posted there, and state read from elsewhere comes
 * from the last values the main thread saw.
 */
@OptIn(UnstableApi::class)
class MusicService : MediaSessionService() {
    override fun getResources(): android.content.res.Resources =
        ohi.andre.consolelauncher.localization.LanguagePacks.resources(super.getResources())

    private lateinit var engine: PlaybackEngine
    private val player get() = engine.player
    private var session: MediaSession? = null
    private val queue = PlayQueue<Song>()
    private lateinit var store: QueueStore
    private var resumeSong: Song? = null
    private var resumeAtMs = 0
    private var placeFrozen = false
    private var ticksSinceSave = 0
    val songIndex: Int get() = queue.index
    private val musicBind: IBinder = MusicBinder()
    private var songTitle: String? = Tuils.EMPTYSTRING
    private var shuffle = false
    private var playbackListener: PlaybackListener? = null
    private var podcastSpeed = 1f
    private var skipSilence = false
    private val main = Handler(Looper.getMainLooper())
    private val sleepNow = Runnable { sleepEndsAt = 0L; pausePlayer() }
    private var sleepEndsAt = 0L
    private val recovery = PlaybackRecovery()
    private val retryNow = Runnable {
        engine.preparing = true
        player.prepare()
        broadcastMusicState()
    }
    private lateinit var forward: ForwardCache
    private var forwardDue = false
    private var ticksSinceForward = 0

    enum class Trouble { RECONNECTING, OFFLINE }

    /** Why nothing is playing while the player works its way back; null when all is well. */
    var trouble: Trouble? = null
        private set

    /** True when playback should stop as the current item finishes. */
    var sleepAtTrackEnd = false
        private set

    /** Time left on the sleep timer, or 0 when none is set. */
    val sleepLeftMs: Long get() = if (sleepEndsAt == 0L) 0 else (sleepEndsAt - SystemClock.elapsedRealtime()).coerceAtLeast(0)

    /** Chapters read from the tags of what is playing; a feed's own chapters take priority over these. */
    internal var taggedChapters: List<Chapter> = emptyList()
        private set

    internal lateinit var effects: AudioEffects
        private set

    override fun onCreate() {
        super.onCreate()
        engine = PlaybackEngine(this, PlaybackEngine.Kind.AUDIO, ::onStateChanged, ::onCompletion, ::onSecondPlayed, ::onPlaybackError)
        forward = ForwardCache(this)
        // The player only picks a session once audio starts; the equaliser needs one to attach to from the start.
        val audioSession = (getSystemService(AUDIO_SERVICE) as AudioManager).generateAudioSessionId()
        player.audioSessionId = audioSession
        effects = AudioEffects(this, audioSession)
        player.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO && player.currentMediaItemIndex > 0) onGaplessAdvance()
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (!isPlaying) return
                recovery.played()
                if (trouble != null) clearTrouble()
            }

            override fun onTracksChanged(tracks: Tracks) {
                val found = tracks.groups.flatMap { group -> (0 until group.length).mapNotNull { group.getTrackFormat(it).metadata } }
                    .flatMap(Chapters::fromMetadata).distinct()
                if (found != taggedChapters) { taggedChapters = found; broadcastMusicState() }
            }
        })
        store = QueueStore(this)
        store.load()?.let { saved ->
            queue.replace(saved.songs, saved.index)
            queue.repeat = saved.repeat
            resumeSong = queue.current
            resumeAtMs = saved.positionMs
        }
        session = MediaSession.Builder(this, QueueControls(player))
            .setSessionActivity(PendingIntent.getActivity(this, 0, Intent(this, LauncherActivity::class.java),
                Tuils.pendingIntentFlags(PendingIntent.FLAG_UPDATE_CURRENT)))
            .build()
        setMediaNotificationProvider(LauncherNotificationProvider())
        // The launcher plays without a media controller of its own, so register the session here; that is what
        // gives it the media notification and keeps the service in the foreground while playing.
        session?.let(::addSession)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    /** The launcher binds to [MusicBinder]; media controllers get the session. */
    override fun onBind(intent: Intent?): IBinder? =
        if (intent?.action == SERVICE_INTERFACE) super.onBind(intent) else musicBind

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent != null && intent.hasExtra(EXTRA_CONTROL_CMD)) {
            when (intent.getIntExtra(EXTRA_CONTROL_CMD, -1)) {
                CONTROL_NEXT_INT -> { savePodcastProgress(); playNext() }
                CONTROL_PREV_INT -> { savePodcastProgress(); playPrev() }
                CONTROL_PLAY_PAUSE_INT -> if (isPlaying) pausePlayer() else playPlayer()
            }
            return START_NOT_STICKY
        }
        return super.onStartCommand(intent, flags, startId)
    }

    private fun onMain(action: () -> Unit) = engine.onMain(action)

    private fun cacheState() = engine.refreshState()

    private fun onStateChanged() {
        savePlace()
        broadcastMusicState()
    }

    private fun onSecondPlayed() {
        if (++ticksSinceSave >= PLACE_SAVE_SECONDS) savePlace()
        if (forwardDue || ++ticksSinceForward >= FORWARD_REFRESH_SECONDS) refreshForwardCache()
    }

    private fun networkState(): Pair<Boolean, Boolean> {
        val manager = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
        val online = manager.getNetworkCapabilities(manager.activeNetwork)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        return online to manager.isActiveNetworkMetered
    }

    /** Keeps the next stretch of streamed audio on disk: less on mobile data, where it costs the user something. */
    private fun refreshForwardCache() {
        forwardDue = false
        ticksSinceForward = 0
        val (online, metered) = networkState()
        if (!online) return
        val at = queue.index
        val ahead = queue.snapshot().drop(at).take(FORWARD_ITEMS).map(::uriOf)
        val bitrate = player.audioFormat?.bitrate ?: ForwardCache.FALLBACK_BITRATE
        val firstIsStream = StreamCache.isStream(ahead.firstOrNull())
        forward.refresh(ahead.filterNotNull().filter(StreamCache::isStream),
            if (firstIsStream) ForwardCache.startOffset(player.currentPosition, bitrate) else 0,
            ForwardCache.budgetBytes(bitrate, if (metered) FORWARD_MINUTES_METERED else FORWARD_MINUTES))
    }

    private fun clearTrouble() {
        main.removeCallbacks(retryNow)
        trouble = null
        broadcastMusicState()
    }

    private fun onPlaybackError() {
        if (player.currentMediaItemIndex > 0) onGaplessAdvance()
        val song = currentSong() ?: return
        main.removeCallbacks(retryNow)
        when (val step = recovery.next(StreamCache.isStream(uriOf(song)), networkState().first, queue.size)) {
            is PlaybackRecovery.Retry -> {
                trouble = if (step.offline) Trouble.OFFLINE else Trouble.RECONNECTING
                main.postDelayed(retryNow, step.delayMs)
                broadcastMusicState()
            }
            PlaybackRecovery.Skip -> {
                trouble = null
                Tuils.sendOutput(this, getString(R.string.player_cant_play, song.getTitle()))
                playNext()
            }
            PlaybackRecovery.GiveUp -> {
                trouble = null
                Tuils.sendOutput(this, getString(R.string.player_gave_up))
                broadcastMusicState()
            }
        }
    }

    private fun savePlace() {
        // A restored place stays until its song is loaded: before that the player is empty and reports 0.
        if (placeFrozen || queue.size == 0 || resumeSong != null) return
        ticksSinceSave = 0
        store.savePlace(queue.index, posn, queue.repeat)
    }

    private fun broadcastMusicState() {
        val intent = Intent(ACTION_MUSIC_CHANGED)
        intent.putExtra(SONG_TITLE, songTitle)
        intent.putExtra(MUSIC_PLAYING, isPlaying)
        intent.putExtra(MUSIC_PREPARING, isPreparing)
        intent.putExtra(SONG_POSITION, posn)
        intent.putExtra(SONG_DURATION, dur)
        val song = currentSong()
        intent.putExtra(MUSIC_SOURCE, song?.getSource() ?: SOURCE_INTERNAL)
        if (song != null) {
            intent.putExtra(SONG_SINGER, song.getSinger())
            intent.putExtra(PODCAST_SHOW_ID, song.getPodcastShowId())
            intent.putExtra(PODCAST_EPISODE_KEY, song.getPodcastEpisodeKey())
        }
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }

    fun setList(theSongs: MutableList<Song>?) {
        queue.replace(theSongs.orEmpty())
        if (shuffle) queue.shuffle(keepCurrent = false)
        store.saveList(queue.snapshot())
        onMain { queueNextInPlayer() }
    }

    /** Fills the queue from the song library unless it already holds songs, so play never wipes a saved queue. */
    fun offerLibrary(library: MutableList<Song>?) {
        if (queue.snapshot().none { it.getSource() == SOURCE_INTERNAL }) setList(library)
    }

    /** On connecting, a restored queue stays, even one holding only podcast episodes. */
    fun fillIfEmpty(library: MutableList<Song>?) {
        if (queue.size == 0) setList(library)
    }

    fun selectSong(song: Song): Boolean = queue.jumpTo(song)

    fun queueSnapshot(): List<Song> = queue.snapshot()
    val queueShuffled: Boolean get() = queue.shuffled
    internal val queueRepeat: PlayQueue.Repeat get() = queue.repeat

    /** Changes the queue and saves it. If that changes the current song, the new one starts. */
    internal fun editQueue(edit: PlayQueue<Song>.() -> Unit) {
        val before = queue.current
        queue.edit()
        store.saveList(queue.snapshot())
        savePlace()
        val after = queue.current
        when {
            after === before -> onMain { queueNextInPlayer(); forwardDue = true; broadcastMusicState() }
            after != null -> playSong()
            else -> {
                engine.preparing = false
                songTitle = null
                onMain { player.stop(); player.clearMediaItems(); cacheState(); broadcastMusicState() }
            }
        }
    }

    fun setPlaybackListener(listener: PlaybackListener?) {
        playbackListener = listener
    }

    /** The podcast listening speed. Music always plays at normal speed. */
    fun setPlaybackSpeed(speed: Float) {
        podcastSpeed = speed.coerceIn(0.5f, 2f)
        if (currentSong()?.getSource() == SOURCE_PODCAST) onMain { player.playbackParameters = PlaybackParameters(podcastSpeed) }
    }

    /** Podcasts only: music keeps its silences. */
    fun setSkipSilence(enabled: Boolean) {
        skipSilence = enabled
        onMain { player.skipSilenceEnabled = enabled && currentSong()?.getSource() == SOURCE_PODCAST }
    }

    /** Pauses after [minutes]; 0 cancels. */
    fun sleepIn(minutes: Int) {
        main.removeCallbacks(sleepNow)
        sleepAtTrackEnd = false
        sleepEndsAt = if (minutes > 0) SystemClock.elapsedRealtime() + minutes * 60_000L else 0L
        if (minutes > 0) main.postDelayed(sleepNow, minutes * 60_000L)
        onMain { queueNextInPlayer(); broadcastMusicState() }
    }

    fun sleepAfterThisTrack() {
        sleepIn(0)
        sleepAtTrackEnd = true
        onMain { queueNextInPlayer(); broadcastMusicState() }
    }

    private fun speedFor(song: Song) = if (song.getSource() == SOURCE_PODCAST) podcastSpeed else 1f

    private fun savePodcastProgress() {
        val song = currentSong()
        if (song?.getSource() == SOURCE_PODCAST) playbackListener?.onProgress(song, posn, dur)
    }

    inner class MusicBinder : Binder() {
        val service: MusicService
            get() = this@MusicService
    }

    fun uriOf(song: Song): Uri? {
        playbackListener?.localFile(song)?.let { return Uri.fromFile(it) }
        if (song.getID() != -1L) return ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, song.getID())
        val path = song.getPath() ?: return null
        // Podcasts stream from a URL; local songs are file paths.
        return if (path.contains("://")) Uri.parse(path) else Uri.fromFile(java.io.File(path))
    }

    /** Starts the current song from its saved position; returns its title, or null when there is nothing to play. */
    fun playSong(): String? = startSong(autoPlay = true)

    private fun mediaItemFor(song: Song): MediaItem? = uriOf(song)?.let { uri ->
        MediaItem.Builder()
            .setUri(uri)
            .setMediaMetadata(MediaMetadata.Builder().setTitle(song.getTitle()).setArtist(song.getSinger()).build())
            .build()
    }

    private fun startSong(autoPlay: Boolean): String? {
        val playSong = currentSong() ?: return null
        val startAt = if (playSong === resumeSong) resumeAtMs else playSong.getStartPositionMs()
        // Marks the episode as the one in progress. The player still holds the previous track, so report where this one starts.
        playbackListener?.onProgress(playSong, startAt, 0)
        songTitle = playSong.getTitle()
        val item = mediaItemFor(playSong) ?: return null
        recovery.trackChanged()
        main.removeCallbacks(retryNow)
        trouble = null
        forwardDue = true
        resumeSong = null
        engine.preparing = true
        onMain {
            player.setMediaItem(item, startAt.toLong().coerceAtLeast(0L))
            player.playbackParameters = PlaybackParameters(speedFor(playSong))
            player.skipSilenceEnabled = skipSilence && playSong.getSource() == SOURCE_PODCAST
            taggedChapters = emptyList()
            queueNextInPlayer()
            player.prepare()
            player.playWhenReady = autoPlay
            cacheState()
            playbackListener?.onTrackChanged(playSong)
            broadcastMusicState()
        }
        return playSong.getTitle()
    }

    fun setSong(songIndex: Int) {
        queue.jumpTo(songIndex)
    }

    /** Hands the player the next song ahead of time, so music runs on without a gap. Podcasts start fresh instead. */
    private fun queueNextInPlayer() {
        if (player.mediaItemCount == 0) return
        while (player.mediaItemCount > player.currentMediaItemIndex + 1) player.removeMediaItem(player.mediaItemCount - 1)
        if (sleepAtTrackEnd || currentSong()?.getSource() != SOURCE_INTERNAL) return
        val next = queue.peekNext()?.takeIf { it.getSource() == SOURCE_INTERNAL } ?: return
        mediaItemFor(next)?.let(player::addMediaItem)
    }

    private fun onGaplessAdvance() {
        player.removeMediaItem(0)
        playbackListener?.onTrackCompleted(currentSong(), posn, dur)
        queue.next()
        songTitle = currentSong()?.getTitle()
        recovery.trackChanged()
        queueNextInPlayer()
        savePlace()
        playbackListener?.onTrackChanged(currentSong())
        broadcastMusicState()
    }

    private fun onCompletion() {
        if (player.currentPosition <= 0) return
        playbackListener?.onTrackCompleted(currentSong(), posn, dur)
        if (sleepAtTrackEnd) {
            sleepAtTrackEnd = false
            if (queue.next()) startSong(autoPlay = false)
            return
        }
        if (queue.repeat == PlayQueue.Repeat.ONE) { player.seekTo(0); player.play() } else playNext()
    }

    val posn: Int get() = engine.positionMs

    val dur: Int get() = engine.durationMs

    val isPlaying: Boolean get() = engine.isPlaying

    val isPreparing: Boolean get() = engine.preparing

    fun pausePlayer() {
        currentSong()?.let { playbackListener?.onProgress(it, posn, dur) }
        onMain { clearTrouble(); player.pause(); broadcastMusicState() }
    }

    fun stop() {
        savePlace()
        forward.cancel()
        main.removeCallbacks(retryNow)
        trouble = null
        placeFrozen = true // Stopping rewinds the player; what was saved above is the place to come back to.
        engine.preparing = false
        songTitle = null
        onMain {
            player.stop()
            player.clearMediaItems()
            cacheState()
            broadcastMusicState()
        }
        setSong(0)
    }

    fun playPlayer() {
        onMain {
            // After an error the player sits idle and play alone will not wake it.
            if (player.playbackState == Player.STATE_IDLE && player.mediaItemCount > 0) player.prepare()
            player.play()
            broadcastMusicState()
        }
    }

    fun seek(posn: Int) {
        onMain {
            player.seekTo(posn.toLong())
            cacheState()
            currentSong()?.let { playbackListener?.onProgress(it, this.posn, dur) }
        }
    }

    fun go() = playPlayer()

    fun playPrev(): String? {
        if (queue.size == 0) return getString(R.string.no_songs)
        // Well into a song, Previous means "from the top". Podcasts keep their place in an episode instead.
        if (currentSong()?.getSource() == SOURCE_INTERNAL && posn > RESTART_THRESHOLD_MS) {
            seek(0)
            playPlayer()
            return currentSong()?.getTitle()
        }
        if (currentSong()?.getSource() == SOURCE_PODCAST && queue.index <= 0) {
            seek(0)
            return currentSong()?.getTitle()
        }
        if (!queue.previous()) { seek(0); return currentSong()?.getTitle() }
        return playSong()
    }

    fun playNext(): String? {
        if (queue.size == 0) return getString(R.string.no_songs)
        val endsHere = queue.isLast && (currentSong()?.getSource() == SOURCE_PODCAST || queue.repeat == PlayQueue.Repeat.OFF)
        if (endsHere) {
            songTitle = null
            onMain { player.stop(); broadcastMusicState() }
            return "End of show."
        }
        queue.next()
        return playSong()
    }

    override fun onDestroy() {
        savePlace()
        main.removeCallbacks(sleepNow)
        main.removeCallbacks(retryNow)
        forward.cancel()
        effects.release()
        session?.release()
        session = null
        engine.release()
        super.onDestroy()
    }

    fun setShuffle(shuffle: Boolean) {
        this.shuffle = shuffle
    }

    fun currentSong(): Song? = queue.current

    /** Lets headsets, the lock screen and other apps skip through the queue, which the player itself never holds. */
    private inner class QueueControls(player: Player) : ForwardingPlayer(player) {
        private fun skipCommands() = setOf(Player.COMMAND_SEEK_TO_NEXT, Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
            Player.COMMAND_SEEK_TO_PREVIOUS, Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)

        // The spread array holds Player.COMMAND_ values; lint just cannot see through it.
        @android.annotation.SuppressLint("WrongConstant")
        override fun getAvailableCommands(): Player.Commands =
            super.getAvailableCommands().buildUpon().addAll(*skipCommands().toIntArray()).build()

        override fun isCommandAvailable(command: Int) = command in skipCommands() || super.isCommandAvailable(command)

        private fun skipForward() { savePodcastProgress(); playNext() }
        private fun skipBack() { savePodcastProgress(); playPrev() }

        override fun seekToNext() = skipForward()
        override fun seekToNextMediaItem() = skipForward()
        override fun seekToPrevious() = skipBack()
        override fun seekToPreviousMediaItem() = skipBack()
    }

    interface PlaybackListener {
        fun onTrackChanged(song: Song?) {}
        fun onTrackCompleted(song: Song?, positionMs: Int, durationMs: Int) {}
        fun onProgress(song: Song?, positionMs: Int, durationMs: Int) {}
        fun localFile(song: Song): java.io.File? = null
    }

    /**
     * The launcher's own media notification: the system's media style (lock screen, quick settings player) with the
     * same previous / play-pause / next buttons, plus the reply field that runs a launcher command.
     */
    private inner class LauncherNotificationProvider : MediaNotification.Provider {
        override fun createNotification(
            mediaSession: MediaSession,
            mediaButtonPreferences: ImmutableList<CommandButton>,
            actionFactory: MediaNotification.ActionFactory,
            onNotificationChangedCallback: MediaNotification.Provider.Callback
        ): MediaNotification = MediaNotification(NOTIFY_ID, buildNotification(this@MusicService, songTitle, mediaSession))

        override fun handleCustomCommand(session: MediaSession, action: String, extras: android.os.Bundle): Boolean = false

        override fun getNotificationChannelInfo() = MediaNotification.Provider.NotificationChannelInfo(
            "music_channel", getString(R.string.manager_musicservice_music_playback_e100b))
    }

    companion object {
        const val ACTION_MUSIC_CHANGED: String = "ohi.andre.consolelauncher.music_changed"
        const val ACTION_MUSIC_CONTROL: String = "ohi.andre.consolelauncher.music_control"

        const val SONG_TITLE: String = "song_title"
        const val SONG_SINGER: String = "song_singer"
        const val SONG_DURATION: String = "song_duration"
        const val SONG_POSITION: String = "song_position"
        const val MUSIC_PLAYING: String = "music_playing"
        const val MUSIC_PREPARING: String = "music_preparing"
        const val MUSIC_SOURCE: String = "music_source"
        const val SOURCE_INTERNAL: String = "internal"
        const val SOURCE_EXTERNAL: String = "external"
        const val SOURCE_PODCAST: String = "podcast"
        const val PODCAST_SHOW_ID: String = "podcast_show_id"
        const val PODCAST_EPISODE_KEY: String = "podcast_episode_key"
        const val MUSIC_CONTROL: String = "music_control"
        const val CONTROL_PREVIOUS: String = "previous"
        const val CONTROL_PLAY_PAUSE: String = "play_pause"
        const val CONTROL_NEXT: String = "next"

        const val EXTRA_CONTROL_CMD: String = "control_cmd"
        const val CONTROL_NEXT_INT: Int = 1
        const val CONTROL_PREV_INT: Int = 2
        const val CONTROL_PLAY_PAUSE_INT: Int = 3

        const val NOTIFY_ID: Int = 100001
        private const val PLACE_SAVE_SECONDS = 10
        private const val RESTART_THRESHOLD_MS = 3_000
        private const val FORWARD_MINUTES = 30
        private const val FORWARD_MINUTES_METERED = 10
        private const val FORWARD_ITEMS = 8
        private const val FORWARD_REFRESH_SECONDS = 120

        private fun buildNotification(context: Context, songTitle: String?, session: MediaSession): Notification {
            val channelId = "music_channel"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    channelId,
                    context.getString(R.string.manager_musicservice_music_playback_e100b),
                    NotificationManager.IMPORTANCE_LOW
                )
                channel.setSound(null, null)
                (context.getSystemService(NOTIFICATION_SERVICE) as NotificationManager?)?.createNotificationChannel(channel)
            }

            val pendInt = PendingIntent.getActivity(context, 0, Intent(context, LauncherActivity::class.java),
                Tuils.pendingIntentFlags(PendingIntent.FLAG_UPDATE_CURRENT))

            val builder = NotificationCompat.Builder(context, channelId)
                .setContentIntent(pendInt)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setTicker(songTitle)
                .setOngoing(true)
                .setContentTitle(context.getString(R.string.manager_musicservice_playing_298c3))
                .setContentText(songTitle)
                .setStyle(MediaStyleNotificationHelper.MediaStyle(session).setShowActionsInCompactView(0, 1, 2))

            builder.addAction(androidx.media3.session.R.drawable.media3_icon_previous,
                context.getString(R.string.manager_musicservice_prev_b231d), controlIntent(context, CONTROL_PREV_INT, 21))
            builder.addAction(
                if (session.player.isPlaying) androidx.media3.session.R.drawable.media3_icon_pause else androidx.media3.session.R.drawable.media3_icon_play,
                context.getString(R.string.manager_musicservice_play_pause_884d0), controlIntent(context, CONTROL_PLAY_PAUSE_INT, 22))
            builder.addAction(androidx.media3.session.R.drawable.media3_icon_next,
                context.getString(R.string.manager_musicservice_next_1992d), controlIntent(context, CONTROL_NEXT_INT, 23))

            // Reply field: type a launcher command straight from the notification.
            val label = "cmd"
            val remoteInput = RemoteInput.Builder(PrivateIOReceiver.TEXT).setLabel(label).build()
            val i = Intent(context, PublicIOReceiver::class.java)
            i.setAction(PublicIOReceiver.ACTION_CMD)
            i.putExtra(MainManager.MUSIC_SERVICE, true)
            var flags = Tuils.pendingIntentFlags(PendingIntent.FLAG_UPDATE_CURRENT)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                flags = (flags and PendingIntent.FLAG_IMMUTABLE.inv()) or PendingIntent.FLAG_MUTABLE
            }
            builder.addAction(NotificationCompat.Action.Builder(R.mipmap.ic_launcher, label,
                PendingIntent.getBroadcast(context.applicationContext, 10, i, flags))
                .addRemoteInput(remoteInput)
                .build())
            return builder.build()
        }

        private fun controlIntent(context: Context, cmd: Int, requestCode: Int): PendingIntent {
            val intent = Intent(context, MusicService::class.java)
            intent.putExtra(EXTRA_CONTROL_CMD, cmd)
            return PendingIntent.getService(context, requestCode, intent,
                Tuils.pendingIntentFlags(PendingIntent.FLAG_UPDATE_CURRENT))
        }
    }
}
