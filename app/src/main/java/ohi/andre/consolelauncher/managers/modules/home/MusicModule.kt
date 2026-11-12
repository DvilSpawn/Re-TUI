package ohi.andre.consolelauncher.managers.modules.home

import android.app.Activity
import android.content.Intent
import android.text.TextUtils
import android.util.Log
import android.view.View
import android.widget.TextView
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.commands.main.MainPack
import ohi.andre.consolelauncher.managers.mediaplayer.MediaPlayerActivity
import ohi.andre.consolelauncher.managers.modules.ModuleManager
import ohi.andre.consolelauncher.managers.music.MusicService
import ohi.andre.consolelauncher.managers.settings.AppearanceSettings.musicWidgetBorderColor
import ohi.andre.consolelauncher.managers.settings.AppearanceSettings.musicWidgetTextColor
import ohi.andre.consolelauncher.managers.settings.MusicSettings.autoShowWidget
import ohi.andre.consolelauncher.managers.settings.MusicSettings.preferredPackage
import ohi.andre.consolelauncher.tuils.MusicVisualizerView
import ohi.andre.consolelauncher.tuils.Tuils
import ohi.andre.consolelauncher.tuils.frame.FrameTarget
import ohi.andre.consolelauncher.tuils.frame.TuiWidgetDecorator.decorateWidget
import java.util.Locale

/** The player panel, and the now-playing state the rest of the launcher reads. */
internal class MusicModule(
    private val host: HomeModuleHost,
    private val pack: () -> MainPack,
    private val onTrackChanged: () -> Unit,
    private val onPlaybackChanged: () -> Unit,
) : HomeModule {
    private val context get() = host.context
    private val mainPack get() = pack()

    private var lastMusicSong: String? = null
    private var lastMusicSinger: String? = null
    private var lastMusicPlaying = false
    private var lastMusicAppPackage: String? = null
    private var activeMusicSource: String? = "internal"

    // Ticker must not removeCallbacks+repost on every MUSIC_CHANGED, that cancels the 1s delay.
    private var musicTickerScheduled = false

    // A layout built mid-playback hears nothing until the next track or state change, so ask the player directly.
    private fun playingNow() = mainPack.podcastManager.takeIf { it.isPlaying() }?.currentSong()
        ?: mainPack.player?.takeIf { it.isPlaying() }?.currentSong()

    val song: String? get() = lastMusicSong ?: playingNow()?.getTitle()
    val singer: String? get() = lastMusicSinger ?: playingNow()?.getSinger()
    val playing: Boolean get() = lastMusicPlaying || (lastMusicSong == null && playingNow() != null)

    override val id = ModuleManager.MUSIC

    // Chips only while actually playing; pause/stop used to leave prev/play/next up.
    override val showsSuggestions: Boolean get() = lastMusicPlaying
    override val broadcasts = setOf(MusicService.ACTION_MUSIC_CHANGED)

    private val musicTimeRunnable: Runnable = object : Runnable {
        override fun run() {
            var shouldContinue = false
            var delayMs = 1000L
            val musicWidget = host.root!!.findViewById<View?>(R.id.music_module)
            if (MusicService.SOURCE_PODCAST == activeMusicSource) {
                val manager = mainPack.podcastManager
                val current = manager.currentSong()
                if (current != null && (manager.isPlaying() || manager.isPreparing())) {
                    if (manager.isPlaying()) manager.saveCurrentProgress()
                    shouldContinue = true
                    val preparing = manager.isPreparing()
                    delayMs = if (preparing) 120L else 1000L
                    lastMusicSong = current.getTitle()
                    lastMusicSinger = current.getSinger()
                    lastMusicPlaying = manager.isPlaying()
                    if (musicWidget != null && musicWidget.visibility == View.VISIBLE) {
                        val songTitleView = musicWidget.findViewById<TextView?>(R.id.music_song_title)
                        songTitleView?.text = musicTitlePrefix() + (current.getTitle() ?: "-").uppercase(Locale.getDefault())
                        val singerView = musicWidget.findViewById<TextView?>(R.id.music_singer)
                        singerView?.text = musicSubtitlePrefix() + (current.getSinger() ?: "-").uppercase(Locale.getDefault())
                        setMusicVisualizerPlaying(manager.isPlaying())
                    }
                }
            } else if (musicWidget != null && musicWidget.getVisibility() == View.VISIBLE) {
                if (MusicService.SOURCE_INTERNAL == activeMusicSource) {
                    if (mainPack.player != null && mainPack.player!!.isPlaying()) {
                        shouldContinue = true
                        val intent: Intent = Intent(MusicService.ACTION_MUSIC_CHANGED)
                        val song = mainPack.player!!.currentSong()
                        if (song != null) {
                            intent.putExtra(MusicService.SONG_TITLE, song.getTitle())
                            intent.putExtra(MusicService.SONG_SINGER, song.getSinger())
                        }
                        intent.putExtra(MusicService.SONG_DURATION, mainPack.player!!.getDuration())
                        intent.putExtra(MusicService.SONG_POSITION, mainPack.player!!.getCurrentPosition())
                        intent.putExtra(MusicService.MUSIC_PLAYING, mainPack.player!!.isPlaying())
                        intent.putExtra(MusicService.MUSIC_SOURCE, MusicService.SOURCE_INTERNAL)
                        LocalBroadcastManager.getInstance(context).sendBroadcast(intent)
                    }
                }
            }
            if (shouldContinue) {
                host.handler!!.postDelayed(this, delayMs)
            } else {
                musicTickerScheduled = false
            }
        }
    }

    override fun onLauncherPaused() {
        host.handler?.removeCallbacks(musicTimeRunnable)
        musicTickerScheduled = false
        setMusicVisualizerPlaying(false)
    }

    override fun onLauncherResumed() {
        setMusicVisualizerPlaying(lastMusicPlaying)
        scheduleInternalMusicTickerIfNeeded()
    }

    override fun onBroadcast(intent: Intent) {
            Log.d("TUI-Music", "UIManager received music change broadcast")
            var song = intent.getStringExtra(MusicService.SONG_TITLE)
            val singer = intent.getStringExtra(MusicService.SONG_SINGER)
            var isPlaying = intent.getBooleanExtra(MusicService.MUSIC_PLAYING, false)
            val source = intent.getStringExtra(MusicService.MUSIC_SOURCE)
            val pkg = intent.getStringExtra("package")
    
            val preferredPkg = preferredPackage()
            val isPreferred = TextUtils.isEmpty(preferredPkg) || preferredPkg == pkg
    
            // Source logic: external always wins if it is playing.
            // Internal only wins if it's playing and external is not.
            if (source != null) {
                if (MusicService.SOURCE_EXTERNAL == source) {
                    // Strictly filter external source if a preferred app is set
                    if (!isPreferred) {
                        isPlaying = false
                        song = null
                    }
                    activeMusicSource = source
                } else if (MusicService.SOURCE_INTERNAL == source && MusicService.SOURCE_EXTERNAL == activeMusicSource) {
                    // Don't let internal idle broadcast override external metadata
                    if (!isPlaying) return
                    activeMusicSource = source
                } else {
                    activeMusicSource = source
                }
            }
    
            Log.d(
                "TUI-Music",
                "UIManager update UI: " + song + ", isPlaying=" + isPlaying + " source=" + activeMusicSource + " pkg=" + pkg
            )
    
            val hasContent = (song != null && !song.isEmpty() && (song != "-"))
            val showMusicWidget = isPlaying || hasContent
            // Mono's music row is rebuilt from these; the broadcast repeats while playing, so only on a change.
            val monoMusicChanged = song != lastMusicSong || singer != lastMusicSinger || isPlaying != lastMusicPlaying
            lastMusicSong = song
            lastMusicSinger = singer
            lastMusicPlaying = isPlaying
            lastMusicAppPackage =
                if (MusicService.SOURCE_EXTERNAL == activeMusicSource
                    && isPreferred
                    && showMusicWidget
                    && !TextUtils.isEmpty(pkg)
                ) pkg else null
            if (monoMusicChanged) onTrackChanged()
    
            // Gone widget left host.activeModuleId=MUSIC, so prev/play/next chips stayed up.
            if (!showMusicWidget && ModuleManager.MUSIC == host.activeModuleId) {
                host.closeModule()
                scheduleInternalMusicTickerIfNeeded()
                return
            }
    
            if (isPlaying && autoShowWidget() && ModuleManager.MUSIC != host.activeModuleId) {
                host.presentModule(ModuleManager.MUSIC)
            }
    
            val musicWidget = host.root?.findViewById<View?>(R.id.music_module)
            if (musicWidget != null) {
                musicWidget.setVisibility(if (showMusicWidget) View.VISIBLE else View.GONE)
            }
            host.refreshContextVisibility()
    
            val widgetBorderColor = musicWidgetBorderColor()
            val widgetTextColor = musicWidgetTextColor()
    
            val visualizerView =
                host.root?.findViewById<MusicVisualizerView?>(R.id.music_visualizer)
            if (visualizerView != null) {
                visualizerView.setBarColor(widgetTextColor)
                visualizerView.setPlaying(isPlaying)
            }
    
            val songTitleView = host.root?.findViewById<TextView?>(R.id.music_song_title)
            if (songTitleView != null) {
                songTitleView.setText(if (song != null) musicTitlePrefix() + song.uppercase(Locale.getDefault()) else musicTitlePrefix() + "-")
                songTitleView.setTextColor(widgetTextColor)
            }
    
            val singerView = host.root?.findViewById<TextView?>(R.id.music_singer)
            if (singerView != null) {
                singerView.setText(
                    if (singer != null) musicSubtitlePrefix() + singer.uppercase(
                        Locale.getDefault()
                    ) else musicSubtitlePrefix() + "-"
                )
                singerView.setTextColor(widgetTextColor)
            }
    
            updateMusicAppRow(host.root, widgetTextColor)
    
            decorateWidget(
                host.root!!,
                R.id.music_module_border,
                R.id.music_module_label,
                R.id.music_module_close,
                widgetBorderColor,
                widgetTextColor,
                FrameTarget.MUSIC
            )
            host.styleCloseButton(host.root?.findViewById<TextView?>(R.id.music_module_close), FrameTarget.MUSIC)
            sizeMusicVisualizer(host.root!!)
            onPlaybackChanged()
            if (ModuleManager.MUSIC == host.activeModuleId) {
                host.refreshSuggestions()
            }
            scheduleInternalMusicTickerIfNeeded()
        
    }

    override fun show(host: HomeModuleHost) {
        val musicWidget = ohi.andre.consolelauncher.presentation.ModulePanelPresentation.inflate(context,host.moduleContainer,ohi.andre.consolelauncher.presentation.ModulePanelPresentation.Kind.MUSIC)
        host.moduleContainer!!.addView(musicWidget)
        musicWidget.setVisibility(View.VISIBLE)
        val close = musicWidget.findViewById<TextView?>(R.id.music_module_close)
        close?.let(host::bindCloseButton)
        musicWidget.findViewById<TextView?>(R.id.music_song_title)?.setOnClickListener {
            if (mainPack.player != null) host.launchActivity(Intent(context, MediaPlayerActivity::class.java).putExtra(MediaPlayerActivity.EXTRA_NOW_PLAYING, true))
        }
        styleMusicWidget(musicWidget)
        updateMusicModuleText(musicWidget)
        scheduleInternalMusicTickerIfNeeded()
    }

    private fun updateMusicModuleText(musicWidget: View) {
        val title = musicWidget.findViewById<TextView?>(R.id.music_song_title)
        val singer = musicWidget.findViewById<TextView?>(R.id.music_singer)
        val visualizer = musicWidget.findViewById<MusicVisualizerView?>(R.id.music_visualizer)
        val textColor = musicWidgetTextColor()
        if (title != null) {
            title.setText(
                if (!TextUtils.isEmpty(lastMusicSong)) musicTitlePrefix() + lastMusicSong!!.uppercase(
                    Locale.getDefault()
                ) else musicTitlePrefix() + "-"
            )
            title.setTextColor(textColor)
        }
        if (singer != null) {
            singer.setText(
                if (!TextUtils.isEmpty(lastMusicSinger)) musicSubtitlePrefix() + lastMusicSinger!!.uppercase(
                    Locale.getDefault()
                ) else musicSubtitlePrefix() + "-"
            )
            singer.setTextColor(textColor)
        }
        updateMusicAppRow(musicWidget, textColor)
        if (visualizer != null) {
            visualizer.setBarColor(textColor)
            visualizer.setPlaying(lastMusicPlaying)
        }
    }

    private fun musicTitlePrefix(): String =
        if (MusicService.SOURCE_PODCAST == activeMusicSource) context.getString(R.string.surface_episode_791bf) else context.getString(R.string.surface_title_edd45)

    private fun musicSubtitlePrefix(): String =
        if (MusicService.SOURCE_PODCAST == activeMusicSource) context.getString(R.string.surface_show_06f63) else context.getString(R.string.surface_singer_e58b5)

    private fun styleMusicWidget(musicWidget: View?) {
        if (musicWidget == null) return
        decorateWidget(
            musicWidget,
            R.id.music_module_border,
            R.id.music_module_label,
            R.id.music_module_close,
            musicWidgetBorderColor(),
            musicWidgetTextColor(),
            FrameTarget.MUSIC
        )
        host.styleCloseButton(musicWidget.findViewById<TextView?>(R.id.music_module_close), FrameTarget.MUSIC)

        val musicAppearance=host.appearance()
        ohi.andre.consolelauncher.presentation.ModulePanelPresentation.musicContent(musicWidget,musicAppearance)
        musicWidget.findViewById<TextView>(R.id.music_app_label)?.setOnClickListener { launchCurrentMusicApp() }
        updateMusicAppRow(musicWidget, musicWidgetTextColor())

        // Style control buttons
        val prevBtn = musicWidget.findViewById<TextView?>(R.id.music_prev)
        val nextBtn = musicWidget.findViewById<TextView?>(R.id.music_next)
        val playPauseBtn = musicWidget.findViewById<TextView?>(R.id.music_play_pause)

        if (prevBtn != null) {
            prevBtn.setOnClickListener(View.OnClickListener { v: View? ->
                if (MusicService.SOURCE_PODCAST == activeMusicSource) {
                    mainPack.podcastManager.previous()
                } else if (MusicService.SOURCE_INTERNAL == activeMusicSource) {
                    if (mainPack.player != null) mainPack.player!!.playPrev()
                } else {
                    val intent = Intent(MusicService.ACTION_MUSIC_CONTROL)
                    intent.putExtra(MusicService.EXTRA_CONTROL_CMD, MusicService.CONTROL_PREV_INT)
                    LocalBroadcastManager.getInstance(context).sendBroadcast(intent)
                }
            })
        }

        if (nextBtn != null) {
            nextBtn.setOnClickListener(View.OnClickListener { v: View? ->
                if (MusicService.SOURCE_PODCAST == activeMusicSource) {
                    mainPack.podcastManager.next()
                } else if (MusicService.SOURCE_INTERNAL == activeMusicSource) {
                    if (mainPack.player != null) mainPack.player!!.playNext()
                } else {
                    val intent = Intent(MusicService.ACTION_MUSIC_CONTROL)
                    intent.putExtra(MusicService.EXTRA_CONTROL_CMD, MusicService.CONTROL_NEXT_INT)
                    LocalBroadcastManager.getInstance(context).sendBroadcast(intent)
                }
            })
        }

        if (playPauseBtn != null) {
            playPauseBtn.setOnClickListener(View.OnClickListener { v: View? ->
                if (MusicService.SOURCE_PODCAST == activeMusicSource) {
                    mainPack.podcastManager.toggle()
                } else if (MusicService.SOURCE_INTERNAL == activeMusicSource) {
                    if (mainPack.player != null) {
                        if (mainPack.player!!.isPlaying()) mainPack.player!!.pause()
                        else mainPack.player!!.play()
                    }
                } else {
                    val intent = Intent(MusicService.ACTION_MUSIC_CONTROL)
                    intent.putExtra(
                        MusicService.EXTRA_CONTROL_CMD,
                        MusicService.CONTROL_PLAY_PAUSE_INT
                    )
                    LocalBroadcastManager.getInstance(context).sendBroadcast(intent)
                }
            })
        }
    }

    private fun updateMusicAppRow(rootView: View?, textColor: Int) {
        if (rootView == null) return
        val row = host.root?.findViewById<View?>(R.id.music_app_row)
        val prefix = host.root?.findViewById<TextView?>(R.id.music_app_prefix)
        val label = host.root?.findViewById<TextView?>(R.id.music_app_label)
        if (row == null || prefix == null || label == null) return

        val packageName = lastMusicAppPackage
        val launchIntent =
            if (!packageName.isNullOrEmpty()) context.getPackageManager()
                .getLaunchIntentForPackage(packageName)
            else null

        if (packageName.isNullOrEmpty() || launchIntent == null) {
            row.setVisibility(View.GONE)
            label.setText(Tuils.EMPTYSTRING)
            label.setOnClickListener(null)
            label.setEnabled(false)
            return
        }

        prefix.setTextColor(textColor)
        label.setTextColor(textColor)
        label.setText(resolveMusicAppLabel(packageName))
        label.setEnabled(true)
        label.setOnClickListener(View.OnClickListener { v: View? -> launchCurrentMusicApp() })
        row.setVisibility(View.VISIBLE)
    }

    private fun resolveMusicAppLabel(packageName: String?): String {
        val resolvedPackage = packageName ?: return Tuils.EMPTYSTRING
        if (TextUtils.isEmpty(resolvedPackage)) return Tuils.EMPTYSTRING
        return try {
            val packageManager = context.getPackageManager()
            val info = packageManager.getApplicationInfo(resolvedPackage, 0)
            val label = info.loadLabel(packageManager)
            if (label.length > 0) label.toString() else resolvedPackage
        } catch (e: Exception) {
            resolvedPackage
        }
    }

    private fun launchCurrentMusicApp() {
        val packageName = lastMusicAppPackage ?: return
        if (packageName.isEmpty()) return
        val intent = context.getPackageManager().getLaunchIntentForPackage(packageName)
            ?: return
        if (context !is Activity) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        host.launchActivity(intent)
    }

    private fun sizeMusicVisualizer(musicWidget: View) {
        ohi.andre.consolelauncher.presentation.ModulePanelPresentation.sizeVisualizer(musicWidget)
    }

    private fun setMusicVisualizerPlaying(playing: Boolean) {
        val visualizer =
            host.root?.findViewById<MusicVisualizerView?>(R.id.music_visualizer)
        if (visualizer != null) {
            visualizer.setPlaying(playing)
        }
    }

    private fun scheduleInternalMusicTickerIfNeeded() {
        if (host.handler == null) {
            return
        }
        if (!host.windowFocused) {
            host.handler!!.removeCallbacks(musicTimeRunnable)
            musicTickerScheduled = false
            return
        }
        val musicWidget =
            host.root?.findViewById<View?>(R.id.music_module)
        val internalPlaying = MusicService.SOURCE_INTERNAL == activeMusicSource
            && mainPack.player != null
            && mainPack.player!!.isPlaying()
            && musicWidget != null
            && musicWidget.getVisibility() == View.VISIBLE
        val podcastPlaying = MusicService.SOURCE_PODCAST == activeMusicSource
            && (mainPack.podcastManager.isPlaying() || mainPack.podcastManager.isPreparing())
        if (!internalPlaying && !podcastPlaying) {
            host.handler!!.removeCallbacks(musicTimeRunnable)
            musicTickerScheduled = false
            return
        }
        if (musicTickerScheduled) return
        musicTickerScheduled = true
        host.handler!!.post(musicTimeRunnable)
    }
}
