package ohi.andre.consolelauncher.managers.mediaplayer

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.BitmapDrawable
import android.media.MediaMetadataRetriever
import android.graphics.BitmapFactory
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.text.TextUtils
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.SeekBar
import android.widget.Space
import android.widget.TextView
import android.widget.Toast
import androidx.activity.addCallback
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.doAfterTextChanged
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import ohi.andre.consolelauncher.LauncherActivity
import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.commands.tuixt.TuixtDialog
import ohi.andre.consolelauncher.commands.tuixt.TuixtLayout
import ohi.andre.consolelauncher.help.PanelBackdrop
import ohi.andre.consolelauncher.managers.media.VideoActivity
import ohi.andre.consolelauncher.managers.media.MediaLibrary
import ohi.andre.consolelauncher.managers.media.VideoMath
import ohi.andre.consolelauncher.managers.media.VideoPlayerActivity
import ohi.andre.consolelauncher.managers.mediaplayer.LibraryBrowse.By
import ohi.andre.consolelauncher.managers.music.MusicService
import ohi.andre.consolelauncher.managers.music.Song
import ohi.andre.consolelauncher.managers.podcast.PodcastEpisode
import ohi.andre.consolelauncher.managers.podcast.PodcastManager
import ohi.andre.consolelauncher.managers.podcast.PodcastShow
import ohi.andre.consolelauncher.managers.settings.AppearanceSettings
import ohi.andre.consolelauncher.managers.settings.ThemeColorResolver
import ohi.andre.consolelauncher.managers.xml.options.Theme
import ohi.andre.consolelauncher.tuils.LauncherFontScale
import ohi.andre.consolelauncher.tuils.LauncherSystemUi
import ohi.andre.consolelauncher.tuils.Tuils
import ohi.andre.consolelauncher.tuils.frame.FrameTarget
import ohi.andre.consolelauncher.tuils.frame.TerminalBorderRuntime
import java.util.Collections
import java.util.Locale

/**
 * The media player: music, video and podcast libraries, what is playing, and the queue. It steers the playback the
 * launcher already runs. Videos play in [VideoPlayerActivity], which needs a task of its own for picture-in-picture.
 */
class MediaPlayerActivity : VideoActivity() {
    private enum class Screen { LIBRARY, PLAYER, QUEUE }
    enum class Tab { MUSIC, VIDEO, PODCASTS }
    private enum class Shelf { NEW, SHOWS, RECENT, OFFLINE, EPISODES }
    private enum class Panel { UP_NEXT, LYRICS, EQ, SLEEP, NOTES, CHAPTERS }

    private class Line(val title: String, val sub: String = "", val meta: String = "", val on: Boolean = false,
        val tap: (() -> Unit)? = null, val hold: (() -> Unit)? = null, val header: Boolean = false, val wrap: Boolean = false)

    override val font: Typeface by lazy { Tuils.getTypeface(this) ?: Typeface.DEFAULT }
    override val border by lazy { ThemeColorResolver.color(Theme.workspace_panel_border_color) }
    override val ink by lazy { ThemeColorResolver.color(Theme.workspace_panel_text_color) }
    override val sel by lazy { ThemeColorResolver.color(Theme.selection_background_color) }
    override val head by lazy { ThemeColorResolver.color(Theme.workspace_panel_text_color) }
    override val dim by lazy { ThemeColorResolver.color(Theme.muted_text_color) }
    override val ground by lazy { ThemeColorResolver.color(Theme.workspace_panel_background_color) }
    override val onSelected by lazy { ThemeColorResolver.color(Theme.selection_text_color) }
    override val control by lazy { ThemeColorResolver.color(Theme.workspace_control_text_color) }

    /** Controls follow the launcher's dashed-border and corner settings, but a custom frame image is for whole panels. */
    override fun outline(color: Int, fill: Int, radius: Int): Drawable = TerminalBorderRuntime.panelDrawable(this, fill, color, 1f,
        AppearanceSettings.outputCornerRadius(), AppearanceSettings.dashedBorders(), cyberdeckNotch = false, frameEnabled = false)

    private val handler = Handler(Looper.getMainLooper())
    private val pack get() = LauncherActivity.instance?.uiManager?.pack
    private val podcasts get() = pack?.podcastManager
    /** With music off, podcasts still have a playback manager of their own. */
    private val manager get() = pack?.player ?: podcasts?.playback
    private val service get() = manager?.takeIf { it.musicBound }?.musicSrv

    private var screen = Screen.LIBRARY
    private var tab = Tab.MUSIC
    private var shelf = Shelf.SHOWS
    private var tagFilter: String? = null
    private var status: String? = null
    private var by = By.ALBUMS
    private var playlistsOpen = false
    private var unwatchedOnly = false
    private var panel = Panel.UP_NEXT
    private var lyricsFor: Song? = null
    private var lyrics: List<LyricLine>? = null
    private var chaptersFor: Song? = null
    private var feedChapters: List<Chapter> = emptyList()
    private var highlighted = -2
    private val playlists by lazy { PlaylistStore(this) }
    private var group: LibraryBrowse.Group? = null
    private var query = ""
    private var dragging = false
    private var artFor: Song? = null
    private var artUrl: String? = null

    private lateinit var library: View
    private lateinit var player: View
    private lateinit var queue: View
    private val browseButtons = HashMap<By, TextView>()
    private val tabButtons = HashMap<Tab, TextView>()
    private val panelButtons = HashMap<Panel, TextView>()
    private val videoButtons = HashMap<Boolean, TextView>()
    private lateinit var listsButton: TextView
    private lateinit var videoRow: View
    private lateinit var refreshButton: TextView
    private lateinit var silenceButton: TextView
    private lateinit var sleepButton: TextView
    private lateinit var panelList: RecyclerView
    private val track = MarkedLine()
    private val shelfButtons = HashMap<Shelf, TextView>()
    private lateinit var filterBox: EditText
    private lateinit var browseRow: View
    private lateinit var podcastRow: View
    private lateinit var tagScroll: HorizontalScrollView
    private lateinit var tagRow: LinearLayout
    private lateinit var addOrSort: TextView
    private lateinit var libraryTitle: TextView
    private val libraryLines = Lines()
    private val upNextLines = Lines()
    private val queueLines = Lines(editable = true)
    private lateinit var stripText: TextView
    private lateinit var strip: View
    private lateinit var askBlock: LinearLayout
    private lateinit var askText: TextView
    private lateinit var askButtons: List<TextView>
    private lateinit var art: ImageView
    private lateinit var title: TextView
    private lateinit var sub: TextView
    private lateinit var seek: SeekBar
    private lateinit var position: TextView
    private lateinit var remaining: TextView
    private lateinit var playButton: TextView
    private lateinit var shuffleButton: TextView
    private lateinit var backButton: TextView
    private lateinit var forwardButton: TextView
    private lateinit var repeatButton: TextView
    private lateinit var upNextTitle: TextView
    private lateinit var queueTitle: TextView
    private var seeking = false
    private var connected = false

    private val changes = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = refresh()
    }

    private val ticker = object : Runnable {
        override fun run() {
            if (!connected && service != null) { connected = true; refresh() }
            showTime()
            handler.postDelayed(this, TICK)
        }
    }

    /** The same window as help and the Termux console: one framed panel with a title tab, the launcher dimmed behind. */
    private fun buildWindow(content: View): View {
        val panel = FrameLayout(this).apply {
            clipChildren = false
            background = TerminalBorderRuntime.panelDrawable(context, ground, border, 1.5f,
                AppearanceSettings.outputCornerRadius(), AppearanceSettings.dashedBorders(), target = FrameTarget.OVERLAYS)
            setPadding(dp(8), dp(22), dp(8), dp(8))
            addView(content, FrameLayout.LayoutParams(-1, -1))
        }
        val header = label(getString(R.string.player_title), ThemeColorResolver.color(Theme.workspace_header_text_color), LauncherFontScale.terminalTextSizeSp()).apply {
            gravity = Gravity.CENTER_VERTICAL
            isSingleLine = true
            setPadding(dp(10), 0, dp(10), 0)
            background = TerminalBorderRuntime.tabDrawable(context, ThemeColorResolver.color(Theme.workspace_header_background_color), FrameTarget.OVERLAYS)
        }
        val root = FrameLayout(this).apply {
            clipChildren = false
            setPadding(dp(8), 0, dp(8), dp(4))
            addView(panel, FrameLayout.LayoutParams(-1, -1).apply { topMargin = dp(18); bottomMargin = dp(2) })
            addView(header, FrameLayout.LayoutParams(-2, dp(20)).apply { leftMargin = dp(48); topMargin = dp(8) })
        }
        val screen = FrameLayout(this).apply {
            isFocusableInTouchMode = true
            // It only holds focus to keep the keyboard away from the search box, so it must not look focused.
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) defaultFocusHighlightEnabled = false
        }
        val viewport = FrameLayout(this)
        TuixtLayout.addFoldAwareHost(this, screen, ViewGroup.LayoutParams.MATCH_PARENT).addView(viewport, ViewGroup.LayoutParams(-1, -1))
        PanelBackdrop.install(viewport)
        viewport.addView(root, FrameLayout.LayoutParams(-1, -1))
        TerminalBorderRuntime.bind(panel, header)
        ViewCompat.setOnApplyWindowInsetsListener(screen) { view, insets ->
            val safe = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout() or WindowInsetsCompat.Type.ime())
            view.setPadding(safe.left, safe.top, safe.right, safe.bottom)
            WindowInsetsCompat.CONSUMED
        }
        screen.post { screen.requestFocus() }
        return screen
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        LauncherSystemUi.requestNoTitleIfFullscreen(this)
        super.onCreate(savedInstanceState)
        LauncherSystemUi.applyFullscreen(this)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        savedInstanceState?.let { state ->
            screen = Screen.entries[state.getInt(KEY_SCREEN)]
            by = By.entries[state.getInt(KEY_BY)]
            query = state.getString(KEY_QUERY).orEmpty()
            tab = Tab.entries[state.getInt(KEY_TAB)]
            shelf = Shelf.entries[state.getInt(KEY_SHELF)]
            tagFilter = state.getString(KEY_TAG)
            playlistsOpen = state.getBoolean(KEY_LISTS)
            unwatchedOnly = state.getBoolean(KEY_UNWATCHED)
            panel = Panel.entries[state.getInt(KEY_PANEL)]
        }
        library = buildLibrary()
        player = buildPlayer()
        queue = buildQueue()
        val content = FrameLayout(this).apply { clipChildren = false; listOf(library, player, queue).forEach { addView(it, FrameLayout.LayoutParams(-1, -1)) } }
        setContentView(buildWindow(content))
        onBackPressedDispatcher.addCallback(this) { goBack() }
        if (savedInstanceState == null) follow(intent)
        show(screen)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        follow(intent)
        show(screen)
    }

    /** Where a command, the dock or an edge pill asked to land. */
    private fun follow(request: Intent) {
        request.getStringExtra(EXTRA_TAB)?.let { name -> pickTab(Tab.valueOf(name)); screen = Screen.LIBRARY }
        if (request.getBooleanExtra(EXTRA_NOW_PLAYING, false)) {
            if (podcasts?.currentSong() != null) pickTab(Tab.PODCASTS)
            screen = Screen.PLAYER
        }
        request.getStringExtra(EXTRA_PODCAST_COMMAND)?.takeIf { it.isNotBlank() }?.let(::runPodcastCommand)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(KEY_SCREEN, screen.ordinal)
        outState.putInt(KEY_BY, by.ordinal)
        outState.putString(KEY_QUERY, query)
        outState.putInt(KEY_TAB, tab.ordinal)
        outState.putInt(KEY_SHELF, shelf.ordinal)
        outState.putString(KEY_TAG, tagFilter)
        outState.putBoolean(KEY_LISTS, playlistsOpen)
        outState.putBoolean(KEY_UNWATCHED, unwatchedOnly)
        outState.putInt(KEY_PANEL, panel.ordinal)
    }

    override fun onStart() {
        super.onStart()
        visible = true
        // With music off nothing has connected to playback yet, and the saved queue lives in the service.
        manager?.takeIf { !it.musicBound }?.init()
        LauncherActivity.instance?.uiManager?.syncPodcastPill()
        podcasts?.downloads?.onChanged = { message -> handler.post { message?.let { status = it }; refresh() } }
        LocalBroadcastManager.getInstance(this).registerReceiver(changes, IntentFilter(MusicService.ACTION_MUSIC_CHANGED))
        handler.post(ticker)
        refresh()
    }

    override fun onStop() {
        LocalBroadcastManager.getInstance(this).unregisterReceiver(changes)
        podcasts?.downloads?.onChanged = null
        handler.removeCallbacks(ticker)
        visible = false
        LauncherActivity.instance?.uiManager?.syncPodcastPill()
        super.onStop()
    }

    private fun goBack() {
        when {
            askBlock.visibility == View.VISIBLE -> hideAsk()
            screen == Screen.QUEUE -> show(Screen.PLAYER)
            screen == Screen.PLAYER && panel != Panel.UP_NEXT -> { panel = Panel.UP_NEXT; refresh() }
            screen == Screen.PLAYER -> show(Screen.LIBRARY)
            tab == Tab.PODCASTS && shelf == Shelf.EPISODES -> openShelf(Shelf.SHOWS)
            tab == Tab.MUSIC && group != null -> { group = null; refresh() }
            else -> finish()
        }
    }

    private fun show(next: Screen) {
        screen = next
        library.visibility = if (next == Screen.LIBRARY) View.VISIBLE else View.GONE
        player.visibility = if (next == Screen.PLAYER) View.VISIBLE else View.GONE
        queue.visibility = if (next == Screen.QUEUE) View.VISIBLE else View.GONE
        refresh()
    }

    // ---- building blocks ----

    /** Highlighted when [on]; otherwise an ordinary control. */
    private fun mark(b: TextView, on: Boolean) = paint(b, on, if (on) sel else control)

    private fun row(vararg views: View) = LinearLayout(this).apply {
        gravity = Gravity.CENTER_VERTICAL
        views.forEach { addView(it) }
    }

    /** Equal-width buttons with the player's 6dp gaps. */
    private fun evenRow(buttons: List<TextView>) = LinearLayout(this).apply {
        gravity = Gravity.CENTER_VERTICAL
        buttons.forEachIndexed { i, b ->
            addView(b, LinearLayout.LayoutParams(0, dp(TOUCH), 1f).apply { if (i > 0) leftMargin = dp(GAP) })
        }
    }

    /** Landscape on a phone: rows move up beside the top controls so the list keeps some room. */
    private fun isShort() = resources.displayMetrics.run { heightPixels / density } < SHORT_HEIGHT_DP

    private fun spring() = Space(this).apply { layoutParams = LinearLayout.LayoutParams(0, 1, 1f) }

    private fun section(heading: TextView, list: View) = FrameLayout(this).apply {
        background = outline(border, ThemeColorResolver.color(Theme.workspace_output_background_color))
        clipChildren = false
        addView(FrameLayout(context).apply { addView(list, FrameLayout.LayoutParams(-1, -1)) }, FrameLayout.LayoutParams(-1, -1))
        addView(heading, FrameLayout.LayoutParams(-2, -2).apply { leftMargin = dp(14) })
    }

    private fun lineList(adapter: Lines) = RecyclerView(this).apply {
        layoutManager = LinearLayoutManager(context)
        this.adapter = adapter
        setPadding(0, dp(12), 0, dp(4))
        clipToPadding = false
        itemAnimator = null
    }

    private fun clock(ms: Int) = VideoMath.clock(ms.toLong())

    // ---- library ----

    private fun buildLibrary(): View {
        filterBox = EditText(this).apply {
            hint = getString(R.string.player_filter)
            contentDescription = hint
            setText(query)
            typeface = font
            textSize = 13f
            setTextColor(ThemeColorResolver.color(Theme.workspace_input_text_color))
            setHintTextColor(ThemeColorResolver.color(Theme.field_hint_color))
            Tuils.setCursorDrawableColor(this, ThemeColorResolver.color(Theme.cursor_color))
            isSingleLine = true
            background = outline(border)
            setPadding(dp(10), dp(6), dp(10), dp(6))
            doAfterTextChanged { query = it?.toString().orEmpty(); refresh() }
        }
        val close = button("X", getString(R.string.player_close), 44, BAR) { finish() }
        val tabNames = mapOf(Tab.MUSIC to R.string.player_tab_music, Tab.VIDEO to R.string.player_tab_video, Tab.PODCASTS to R.string.player_tab_podcasts)
        val tabs = evenRow(Tab.entries.map { option ->
            button(getString(tabNames.getValue(option)), getString(tabNames.getValue(option)), 52, BAR) { pickTab(option); hideAskQuietly(); refresh() }
                .also { tabButtons[option] = it }
        })
        val names = mapOf(By.ALBUMS to R.string.player_albums, By.ARTISTS to R.string.player_artists,
            By.SONGS to R.string.player_songs, By.FOLDERS to R.string.player_folders)
        val browseButtonsInOrder = By.entries.map { option ->
            button(getString(names.getValue(option)), getString(names.getValue(option)), 52, BAR) {
                by = option
                playlistsOpen = false
                group = null
                refresh()
            }.also { browseButtons[option] = it }
        }.toMutableList()
        listsButton = button(getString(R.string.player_lists), getString(R.string.player_lists_desc), 52, BAR) {
            playlistsOpen = true
            group = null
            refresh()
        }
        browseButtonsInOrder.add(3, listsButton)
        browseRow = evenRow(browseButtonsInOrder)
        videoRow = evenRow(listOf(false to R.string.player_all, true to R.string.player_unwatched).map { (only, name) ->
            button(getString(name), getString(name), 52, BAR) { unwatchedOnly = only; refresh() }.also { videoButtons[only] = it }
        })
        addOrSort = button(getString(R.string.player_add), getString(R.string.surface_add_podcast_24ba4), 52, BAR) {}
        refreshButton = button("", getString(R.string.surface_refresh_selected_podcast_4b302), 52, BAR) { refreshPodcast() }
        val shelfNames = listOf(Shelf.NEW to R.string.player_new, Shelf.SHOWS to R.string.player_shows, Shelf.RECENT to R.string.player_recent, Shelf.OFFLINE to R.string.player_offline)
        podcastRow = evenRow(shelfNames.map { (option, name) ->
            button(getString(name), getString(name), 52, BAR) { openShelf(option) }.also { shelfButtons[option] = it }
        } + listOf(addOrSort, refreshButton))
        tagRow = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        tagScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            addView(tagRow)
        }
        libraryTitle = titled("")
        stripText = label().apply { isSingleLine = true; ellipsize = TextUtils.TruncateAt.END }
        strip = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(TOUCH)
            setPadding(dp(2), 0, dp(2), 0)
            addView(stripText, LinearLayout.LayoutParams(-1, -2))
        }
        askText = label(color = head).apply { isSingleLine = true; ellipsize = TextUtils.TruncateAt.END }
        askButtons = List(4) { button("", "", 52, BAR) {} }
        askBlock = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            setPadding(dp(2), dp(8), dp(2), 0)
            addView(askText)
            addView(evenRow(askButtons))
        }
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            clipChildren = false
            addView(LinearLayout(context).apply {
                gravity = Gravity.CENTER_VERTICAL
                addView(filterBox, LinearLayout.LayoutParams(0, -2, 1f).apply { rightMargin = dp(GAP) })
                if (isShort()) addView(tabs, LinearLayout.LayoutParams(0, -2, SHORT_TABS_WEIGHT).apply { rightMargin = dp(GAP) })
                addView(close)
            })
            if (!isShort()) addView(tabs)
            addView(browseRow)
            addView(videoRow)
            addView(podcastRow)
            addView(tagScroll)
            addView(Space(context), LinearLayout.LayoutParams(1, dp(10)))
            addView(section(libraryTitle, lineList(libraryLines)), LinearLayout.LayoutParams(-1, 0, 1f))
            addView(strip)
            addView(askBlock)
        }
    }

    private fun songLine(song: Song, context: List<Song>, current: Song?) = Line(song.getTitle(), song.getSinger(),
        on = song.getTitle() == current?.getTitle() && song.getPath() == current?.getPath() && song.getID() == current?.getID(),
        tap = { playFrom(context, song) }, hold = { askQueue(song.getTitle(), listOf(song)) })

    private fun pickTab(next: Tab) {
        if (next != tab) clearFilter()
        tab = next
    }

    private fun clearFilter() {
        if (query.isNotEmpty()) filterBox.setText("")
    }

    private fun refreshLibrary() {
        tabButtons.forEach { (option, b) -> mark(b, option == tab) }
        val searching = query.isNotBlank() && !(tab == Tab.PODCASTS && shelf == Shelf.EPISODES) && !(tab == Tab.MUSIC && group != null)
        browseRow.visibility = if (tab == Tab.MUSIC && !searching) View.VISIBLE else View.GONE
        videoRow.visibility = if (tab == Tab.VIDEO && !searching) View.VISIBLE else View.GONE
        podcastRow.visibility = if (tab == Tab.PODCASTS && !searching) View.VISIBLE else View.GONE
        tagScroll.visibility = View.GONE
        showNowPlayingStrip()
        if (searching) refreshSearch() else when (tab) {
            Tab.MUSIC -> refreshMusic()
            Tab.VIDEO -> refreshVideo()
            Tab.PODCASTS -> refreshPodcasts()
        }
        if (askBlock.visibility == View.VISIBLE) strip.visibility = View.GONE
    }

    private fun showNowPlayingStrip() {
        val current = service?.currentSong()
        val length = service?.dur ?: 0
        stripText.text = current?.let { if (length > 0) getString(R.string.player_continue, it.getTitle(), clock(service?.posn ?: 0), clock(length)) else it.getTitle() }.orEmpty()
        strip.visibility = if (current == null) View.GONE else View.VISIBLE
        strip.setOnClickListener { show(Screen.PLAYER) }
    }

    /** One search across everything, grouped by kind. */
    private fun refreshSearch() {
        libraryTitle.text = getString(R.string.player_search)
        val lines = ArrayList<Line>()
        val current = service?.currentSong()
        pack?.player?.getSongs()?.filter { LibraryBrowse.matches(it, query) }?.take(SEARCH_ROWS)?.takeIf { it.isNotEmpty() }?.let { found ->
            lines += Line(getString(R.string.player_tab_music), header = true)
            found.mapTo(lines) { songLine(it, found, current) }
        }
        videos().filter { it.name.contains(query, ignoreCase = true) }.take(SEARCH_ROWS).takeIf { it.isNotEmpty() }?.let { found ->
            lines += Line(getString(R.string.player_tab_video), header = true)
            found.forEachIndexed { i, item -> lines += videoLine(i + 1, item) }
        }
        podcasts?.let { p ->
            val shows = p.shows().filter { it.title.contains(query, ignoreCase = true) }
            val episodes = p.shows().flatMap { show -> p.episodesFor(show, query).map { show to it } }.take(SEARCH_ROWS)
            if (shows.isNotEmpty() || episodes.isNotEmpty()) lines += Line(getString(R.string.player_tab_podcasts), header = true)
            shows.mapTo(lines) { showLine(p, it) }
            episodes.mapTo(lines) { (show, episode) -> episodeLine(p, show, episode, namingShow = true) }
        }
        if (lines.isEmpty()) lines += Line(getString(R.string.player_no_results))
        libraryLines.set(lines)
    }

    private fun playlistLines(): List<Line> {
        val names = playlists.names()
        if (names.isEmpty()) return listOf(Line(getString(R.string.player_no_playlists)))
        return names.map { name ->
            val songs = playlists.songs(name)
            Line(name, meta = songs.size.toString(), tap = { group = LibraryBrowse.Group(name, songs); refresh() }, hold = {
                askWith(name, getString(R.string.player_play_next) to { service?.editQueue { songs.asReversed().forEach(::addNext) } },
                    getString(R.string.player_add_to_queue) to { service?.editQueue { songs.forEach(::addLast) } },
                    getString(R.string.player_delete) to { playlists.delete(name) })
            })
        }
    }

    private fun refreshMusic() {
        libraryTitle.text = getString(if (playlistsOpen) R.string.player_lists else R.string.player_tab_music)
        by.let { chosen -> browseButtons.forEach { (option, b) -> mark(b, option == chosen && !playlistsOpen) } }
        mark(listsButton, playlistsOpen)
        val current = service?.currentSong()
        val open = group
        if (playlistsOpen && open == null) {
            libraryLines.set(playlistLines())
            return
        }
        val all = pack?.player?.getSongs()
        if (all == null && open == null) {
            libraryLines.set(listOf(Line(getString(R.string.player_music_off))))
            return
        }
        libraryLines.set(when {
            open != null -> listOf(Line("< ${open.name}", tap = { group = null; refresh() })) +
                open.songs.filter { LibraryBrowse.matches(it, query) }.let { inside -> inside.map { songLine(it, inside, current) } }
            by == By.SONGS -> all.orEmpty().sortedBy { it.getTitle().lowercase() }.let { sorted -> sorted.map { songLine(it, sorted, current) } }
            else -> LibraryBrowse.groups(all.orEmpty(), by).map { g ->
                Line(g.name, meta = g.songs.size.toString(), tap = { group = g; refresh() }, hold = { askQueue(g.name, g.songs) })
            }
        })
    }

    // ---- video ----

    private fun openVideo(item: MediaLibrary.Item, startAt: Long, forget: Boolean = false) {
        startActivity(Intent(this, VideoPlayerActivity::class.java)
            .putExtra(EXTRA_URI, item.uri).putExtra(EXTRA_START_AT, startAt).putExtra(EXTRA_FORGET, forget))
    }

    /** Brings the floating player back to full screen. */
    private fun expandVideo() = startActivity(Intent(this, VideoPlayerActivity::class.java))

    private fun refreshVideo() {
        libraryTitle.text = getString(R.string.player_tab_video)
        videoButtons.forEach { (only, b) -> mark(b, only == unwatchedOnly) }
        val all = videos()
        val lines = ArrayList<Line>()
        all.groupBy { it.folder }.forEach { (folder, items) ->
            val states = items.map { saved(it.uri) }
            val left = states.count { (p, d) -> !VideoMath.isDone(p, d) }
            val shown = items.filter { !unwatchedOnly || !saved(it.uri).let { (p, d) -> VideoMath.isDone(p, d) } }
            if (shown.isEmpty()) return@forEach
            val start = VideoMath.showStart(states)
            lines += Line(folder.substringAfterLast('/'), getString(R.string.player_unwatched_count, left, items.size), header = true, tap = { tapShow(folder) },
                meta = getString(if (start.started) R.string.media_video_resume_show else R.string.media_video_start_show))
            shown.forEach { item -> lines += videoLine(items.indexOf(item) + 1, item) }
        }
        if (lines.isEmpty()) lines += Line(getString(when {
            MediaLibrary.folders(this).isEmpty() -> R.string.media_no_folders
            all.isEmpty() -> R.string.media_no_videos
            else -> R.string.player_all_watched
        }))
        libraryLines.set(lines)
        val last = all.firstOrNull { it.uri == resume.getString(KEY_LAST, null) } ?: return
        val (pos, dur) = saved(last.uri)
        stripText.text = getString(R.string.player_continue, last.name, VideoMath.clock(pos), VideoMath.clock(dur))
        strip.visibility = View.VISIBLE
        strip.setOnClickListener { if (last.uri == nowPlaying) expandVideo() else openVideo(last, VideoMath.resumeAt(pos, dur)) }
    }

    private fun videoLine(n: Int, item: MediaLibrary.Item): Line {
        val (pos, dur) = saved(item.uri)
        val length = (if (dur > MARKED_DONE) dur else item.durationMs).let { if (it > 0) VideoMath.clock(it) else "" }
        val state = when {
            VideoMath.isDone(pos, dur) -> getString(R.string.media_video_done)
            dur > 0 && pos > 0 -> "${pos * 100 / dur}%"
            else -> ""
        }
        return Line("$n  ${item.name}", meta = listOf(length, state).filter(String::isNotEmpty).joinToString("  "),
            on = item.uri == nowPlaying, tap = { tapVideo(item) }, hold = { toggleWatched(item) })
    }

    /** A first watch just starts; a half-watched video asks. */
    private fun tapVideo(item: MediaLibrary.Item) {
        if (item.uri == nowPlaying) { expandVideo(); return }
        val (pos, dur) = saved(item.uri)
        if (VideoMath.canResume(pos, dur)) askResume(item, pos, listOf(item)) else openVideo(item, 0)
    }

    /** A folder is a show: start it if it is new, otherwise offer to carry on from where it was left. */
    private fun tapShow(folder: String) {
        val episodes = videos().filter { it.folder == folder }
        val start = VideoMath.showStart(episodes.map { saved(it.uri) })
        val item = episodes.getOrNull(start.index) ?: return
        if (start.started) askResume(item, saved(item.uri).first, episodes) else openVideo(item, 0)
    }

    private fun askResume(item: MediaLibrary.Item, pos: Long, clearing: List<MediaLibrary.Item>) = askWith(item.name,
        getString(R.string.media_video_resume_at, VideoMath.clock(pos)) to { openVideo(item, pos) },
        getString(R.string.media_video_start_over) to {
            resume.edit().apply { clearing.forEach { remove(it.uri) } }.apply()
            openVideo(clearing.first(), 0, forget = true)
        })

    /** Long-press marks a video watched, or clears that; the same as the podcast list's played flag. */
    private fun toggleWatched(item: MediaLibrary.Item) {
        if (item.uri == nowPlaying) return
        val (pos, dur) = saved(item.uri)
        if (VideoMath.isDone(pos, dur)) resume.edit().remove(item.uri).apply()
        else resume.edit().putString(item.uri, if (dur > MARKED_DONE) "$dur|$dur" else "$MARKED_DONE|$MARKED_DONE").apply()
        refresh()
    }

    // ---- podcasts ----

    private fun openShelf(next: Shelf) {
        shelf = next
        status = null
        clearFilter()
        refresh()
    }

    private fun say(message: String?) = runOnUiThread { status = message; refresh() }

    private fun refreshPodcast() {
        val p = podcasts ?: return
        say(getString(R.string.surface_refreshing_selected_podcast_7f1bc))
        p.refreshSelectedShow { say(it ?: getString(R.string.surface_podcast_refreshed_3f4e4)) }
    }

    private fun subscribe(url: String) {
        val p = podcasts ?: return
        say(getString(R.string.surface_adding_podcast_98f33))
        p.subscribe(url) { message ->
            shelf = Shelf.EPISODES
            say(message ?: getString(R.string.surface_podcast_added_49c22))
        }
    }

    private fun askFeedUrl() = TuixtDialog.showValidatedForm(this, getString(R.string.surface_add_podcast_24c78),
        listOf(TuixtDialog.FormField("url", getString(R.string.surface_feed_url_c2311), "https://example.com/feed.xml",
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI)),
        getString(R.string.surface_add_f9460), getString(R.string.surface_cancel_1507c),
        { values -> if (PodcastManager.isSecureFeedUrl(values["url"].orEmpty())) null else getString(R.string.surface_podcast_feed_url_must_be_a_valid_https_address_75717) },
    ) { values -> subscribe(values["url"].orEmpty()) }

    private fun editTags(show: PodcastShow) = TuixtDialog.showValidatedForm(this, getString(R.string.surface_podcast_tags_cac94),
        listOf(TuixtDialog.FormField("tags", getString(R.string.surface_tags_848ee), getString(R.string.surface_workout_travel_calming_5a511),
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS, show.tags.joinToString(", "))),
        getString(R.string.surface_save_50815), getString(R.string.surface_cancel_1507c), { null },
    ) { values ->
        val message = podcasts?.setTags(show, values["tags"].orEmpty())
        shelf = Shelf.SHOWS
        say(message)
    }

    private fun confirmRemove(show: PodcastShow) = TuixtDialog.showConfirm(this, getString(R.string.surface_remove_podcast_5cba3),
        getString(R.string.surface_remove_from_podcasts_e8074, show.title), getString(R.string.surface_remove_f9662), getString(R.string.surface_cancel_1507c),
        TuixtDialog.ConfirmAction {
            shelf = Shelf.SHOWS
            say(podcasts?.removeShow(show))
        })

    private fun refreshPodcasts() {
        val p = podcasts ?: return
        shelfButtons.forEach { (option, b) -> mark(b, option == shelf) }
        val inShow = shelf == Shelf.EPISODES
        val selected = p.selectedShow()
        listOf(Shelf.NEW, Shelf.RECENT, Shelf.OFFLINE).forEach { shelfButtons[it]?.visibility = if (inShow) View.GONE else View.VISIBLE }
        fun widen(b: TextView, weight: Float) { b.layoutParams = (b.layoutParams as LinearLayout.LayoutParams).apply { this.weight = weight } }
        shelfButtons[Shelf.SHOWS]?.let { b -> b.layoutParams = (b.layoutParams as LinearLayout.LayoutParams).apply { leftMargin = if (inShow) 0 else dp(GAP) } }
        widen(addOrSort, if (inShow) 1f else NARROW)
        widen(refreshButton, if (inShow) 1f else NARROW)
        refreshButton.text = if (inShow) getString(R.string.player_refresh) else "R"
        addOrSort.text = when {
            !inShow || selected == null -> "+"
            p.isNewestFirst(selected) -> getString(R.string.player_newest)
            else -> getString(R.string.player_oldest)
        }
        addOrSort.contentDescription = if (!inShow) getString(R.string.surface_add_podcast_24ba4) else addOrSort.text
        addOrSort.setOnClickListener {
            if (inShow && selected != null) { p.toggleNewestFirst(selected); refresh() } else askFeedUrl()
        }
        libraryTitle.text = when {
            inShow -> selected?.title ?: getString(R.string.player_tab_podcasts)
            shelf == Shelf.RECENT -> getString(R.string.player_recent)
            shelf == Shelf.NEW -> getString(R.string.player_new)
            shelf == Shelf.OFFLINE -> getString(R.string.player_offline)
            else -> getString(R.string.player_tab_podcasts)
        }
        val lines = ArrayList<Line>()
        status?.takeIf { it.isNotEmpty() }?.let { lines += Line(it) }
        if (p.shows().isEmpty()) {
            lines += Line(getString(if (p.feeds().isEmpty()) R.string.surface_no_subscriptions_yet_use_add_or_type_podcast_add_feed_url_d4398 else R.string.surface_subscriptions_are_saved_tap_a_show_to_refresh_it_539d1))
            libraryLines.set(lines)
            return
        }
        when (shelf) {
            Shelf.SHOWS -> { showTags(p); lines += showLines(p) }
            Shelf.RECENT -> { showTags(p); lines += recentLines(p) }
            Shelf.EPISODES -> lines += episodeLines(p, selected)
            Shelf.NEW -> lines += acrossShows(p, R.string.player_no_new) { !p.isPlayed(it) }
            Shelf.OFFLINE -> lines += acrossShows(p, R.string.player_no_offline) { p.downloads.isDownloaded(it) || p.downloads.percent(it) != null }
        }
        libraryLines.set(lines)
    }

    private fun matchesTag(show: PodcastShow) = tagFilter?.let { wanted -> show.tags.any { it.equals(wanted, ignoreCase = true) } } ?: true

    private fun showTags(p: PodcastManager) {
        val tags = p.tags()
        if (tagFilter != null && tags.none { it.equals(tagFilter, ignoreCase = true) }) tagFilter = null
        tagRow.removeAllViews()
        if (tags.isEmpty()) return
        tagScroll.visibility = View.VISIBLE
        fun chip(name: String, wanted: String?) {
            val text = name.uppercase(Locale.getDefault())
            val b = button(text, text, text.length * 8 + 18, BAR) { tagFilter = wanted; refresh() }
            mark(b, wanted.equals(tagFilter, ignoreCase = true))
            tagRow.addView(b, LinearLayout.LayoutParams(dp(text.length * 8 + 18), dp(TOUCH)).apply { rightMargin = dp(GAP) })
        }
        chip(getString(R.string.surface_all_6b428), null)
        tags.forEach { chip(it, it) }
    }

    private fun showLines(p: PodcastManager) = p.shows().filter(::matchesTag).map { showLine(p, it) }

    private fun showLine(p: PodcastManager, show: PodcastShow) =
        Line(show.title, show.tags.joinToString("  ") { "#$it" }, show.episodes.size.toString(), on = show.id == p.selectedShow()?.id, tap = {
            p.selectShow(show.id)
            clearFilter()
            shelf = Shelf.EPISODES
            say(getString(R.string.surface_refreshing_09866, show.title))
            p.refreshShow(show) { say(it ?: getString(R.string.surface_podcast_refreshed_3f4e4)) }
        }, hold = {
            askWith(show.title, getString(R.string.surface_edit_tags_d8a5f).uppercase(Locale.getDefault()) to { editTags(show) },
                getString(R.string.surface_remove_e9639).uppercase(Locale.getDefault()) to { confirmRemove(show) })
        })

    /** Episodes from every show, newest first. */
    private fun acrossShows(p: PodcastManager, empty: Int, wanted: (PodcastEpisode) -> Boolean): List<Line> {
        val found = p.shows().filter(::matchesTag).flatMap { show -> show.episodes.filter(wanted).map { show to it } }
            .sortedByDescending { (_, episode) -> episode.publishedAt ?: Long.MIN_VALUE }.take(ACROSS_ROWS)
        if (found.isEmpty()) return listOf(Line(getString(empty)))
        return found.map { (show, episode) -> episodeLine(p, show, episode, namingShow = true) }
    }

    private fun recentLines(p: PodcastManager): List<Line> {
        val recents = p.recents().filter { matchesTag(it.show) }
        if (recents.isEmpty()) return listOf(Line(getString(R.string.surface_no_recent_podcast_episodes_yet_b17af)))
        return recents.map { recent ->
            Line(recent.episode.title, recent.show.title,
                if (recent.progressMs > 0) PodcastManager.formatMillis(recent.progressMs) else getString(R.string.surface_not_started_db2c4),
                tap = { playEpisode(recent.show, recent.episode) })
        }
    }

    private fun episodeLines(p: PodcastManager, show: PodcastShow?): List<Line> {
        if (show == null) return listOf(Line(getString(R.string.surface_no_show_selected_7bad6)))
        val episodes = p.episodesFor(show, query)
        if (episodes.isEmpty()) return listOf(Line(
            if (query.isBlank()) getString(R.string.surface_no_playable_episodes_found_in_this_feed_fc157)
            else getString(R.string.surface_no_episodes_match_07d9b, query)))
        return episodes.map { episodeLine(p, show, it) }
    }

    private fun episodeLine(p: PodcastManager, show: PodcastShow, episode: PodcastEpisode, namingShow: Boolean = false): Line {
        val played = p.isPlayed(episode)
        val progress = p.progress(episode)
        val date = episode.publishedAt?.let { android.text.format.DateFormat.format("yyyy-MM-dd", it).toString() }
        val length = episode.duration?.let { d -> d.toIntOrNull()?.let { clock(it * 1000) } ?: d }
        val state = listOfNotNull(
            p.downloads.percent(episode)?.let { "$it%" } ?: getString(R.string.player_saved).takeIf { p.downloads.isDownloaded(episode) },
            when {
                played -> getString(R.string.media_video_done)
                progress > 0 -> PodcastManager.formatMillis(progress)
                else -> null
            })
        return Line(episode.title, listOfNotNull(show.title.takeIf { namingShow }, date, length).joinToString(" / "), state.joinToString("  "),
            on = episode.key == p.activeEpisode()?.key && p.currentSong() != null,
            tap = { playEpisode(show, episode) },
            hold = { askEpisode(p, show, episode, played) })
    }

    private fun playEpisode(show: PodcastShow, episode: PodcastEpisode) {
        podcasts?.play(show, episode)
        status = null
        show(Screen.PLAYER)
    }

    private fun askEpisode(p: PodcastManager, show: PodcastShow, episode: PodcastEpisode, played: Boolean) {
        val song = p.songFor(show, episode)
        fun queue(edit: PlayQueue<Song>.() -> Unit) { service?.editQueue(edit) }
        val downloads = p.downloads
        val keep = when {
            downloads.percent(episode) != null -> getString(R.string.player_cancel) to { downloads.delete(episode) }
            downloads.isDownloaded(episode) -> getString(R.string.player_delete) to { downloads.delete(episode) }
            else -> getString(R.string.player_download) to { downloads.start(episode, getString(R.string.player_download_failed)) }
        }
        askWith(episode.title,
            getString(R.string.player_ask_next) to { queue { addNext(song) } },
            getString(R.string.player_ask_queue) to { queue { addLast(song) } },
            getString(if (played) R.string.player_mark_unplayed else R.string.player_mark_played) to { p.markPlayed(episode, !played) },
            keep)
    }

    private fun runPodcastCommand(raw: String) {
        val p = podcasts ?: return
        val command = raw.trim()
        val lower = command.lowercase(Locale.getDefault())
        pickTab(Tab.PODCASTS)
        screen = Screen.LIBRARY
        when {
            lower == "help" -> status = getString(R.string.surface_commands_add_https_feed_url_refresh_play_next_prev_rewind_8ce06)
            lower == "refresh" || lower == "reload" -> refreshPodcast()
            lower == "play" -> status = p.play()
            lower == "next" -> status = p.next()
            lower == "prev" || lower == "previous" -> status = p.previous()
            lower == "rewind" || lower == "back" -> status = getString(R.string.surface_30s_1c6a4, p.seekBy(-LONG_SKIP))
            lower == "forward" || lower == "fwd" -> status = getString(R.string.surface_30s_ffb4b, p.seekBy(LONG_SKIP))
            lower.startsWith("speed ") -> status = command.substringAfter(' ').trim().removeSuffix("x").toFloatOrNull()?.let(p::setPlaybackSpeed)
                ?: getString(R.string.surface_playback_speed_must_be_a_number_from_0_5_to_2_0_eca60)
            lower.startsWith("add ") -> subscribe(command.substring(4).trim())
            else -> status = getString(R.string.surface_unknown_podcast_command_try_add_https_feed_url_refresh_pla_74479)
        }
    }

    private fun playFrom(list: List<Song>, song: Song) {
        manager?.playList(ArrayList(list), list.indexOf(song), manager?.podcastListener)
        show(Screen.PLAYER)
    }

    private fun askQueue(name: String, songs: List<Song>) = askWith(name,
        getString(R.string.player_play_next) to { service?.editQueue { songs.asReversed().forEach(::addNext) } },
        getString(R.string.player_add_to_queue) to { service?.editQueue { songs.forEach(::addLast) } })

    /** A question about one row, in the library's bottom strip rather than a box over it. */
    private fun askWith(name: String, vararg answers: Pair<String, () -> Any?>) {
        askText.text = name
        askButtons.forEachIndexed { i, b ->
            val answer = answers.getOrNull(i)
            b.visibility = if (answer == null) View.GONE else View.VISIBLE
            b.text = answer?.first
            b.contentDescription = answer?.first
            b.setOnClickListener { hideAskQuietly(); answer?.second?.invoke(); refresh() }
        }
        askBlock.visibility = View.VISIBLE
        strip.visibility = View.GONE
    }

    private fun hideAskQuietly() {
        askBlock.visibility = View.GONE
    }

    private fun hideAsk() {
        hideAskQuietly()
        refresh()
    }

    // ---- now playing ----

    private fun buildPlayer(): View {
        val metrics = resources.displayMetrics
        val heightDp = metrics.heightPixels / metrics.density
        val widthDp = metrics.widthPixels / metrics.density
        art = ImageView(this).apply { visibility = View.GONE }
        val artSize = minOf(widthDp * 0.6f, heightDp * 0.3f).toInt()
        title = label(size = 16f, color = head).apply { isSingleLine = true; ellipsize = TextUtils.TruncateAt.END }
        sub = label(color = dim).apply { isSingleLine = true; ellipsize = TextUtils.TruncateAt.END }
        position = label().apply { setPadding(dp(4), 0, dp(4), 0) }
        remaining = label()
        seek = slider(getString(R.string.player_seek)).apply {
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(bar: SeekBar, p: Int, user: Boolean) {
                    if (user) position.text = clock((service?.dur ?: 0) * p / SLIDER_MAX)
                }
                override fun onStartTrackingTouch(bar: SeekBar) { seeking = true }
                override fun onStopTrackingTouch(bar: SeekBar) {
                    seeking = false
                    service?.let { srv ->
                        val target = srv.dur * bar.progress / SLIDER_MAX
                        if (podcastNow()) podcasts?.seekTo(target) else srv.seek(target)
                    }
                }
            })
        }
        shuffleButton = button(getString(R.string.player_shuffle), getString(R.string.player_shuffle), 52, BAR) {
            if (podcastNow()) { askSpeed(shuffleButton); return@button }
            service?.editQueue { if (shuffled) unshuffle() else shuffle() }
            refresh()
        }
        repeatButton = button("", getString(R.string.player_repeat), 52, BAR) {
            if (podcastNow()) { manager?.playNext(); return@button }
            service?.editQueue { repeat = PlayQueue.Repeat.entries[(repeat.ordinal + 1) % PlayQueue.Repeat.entries.size] }
            refresh()
        }
        backButton = button("<<", getString(R.string.player_previous), 44, CENTER) { if (podcastNow()) skip(-SKIP_BACK) else manager?.playPrev() }
        forwardButton = button(">>", getString(R.string.player_next), 44, CENTER) { if (podcastNow()) skip(SKIP_FORWARD) else manager?.playNext() }
        playButton = button("", "", 76, CENTER) { togglePlay() }
        val transport = LinearLayout(this).apply {
            gravity = Gravity.CENTER
            fun gap() = Space(context).apply { layoutParams = LinearLayout.LayoutParams(dp(GAP), 1) }
            addView(shuffleButton); addView(gap())
            addView(backButton); addView(gap())
            addView(playButton); addView(gap())
            addView(forwardButton); addView(gap())
            addView(repeatButton)
        }
        upNextTitle = titled("")
        seek.progressDrawable = track
        fun tool(option: Panel, name: Int) = button(getString(name), getString(name), 52, BAR) {
            panel = if (panel == option) Panel.UP_NEXT else option
            refresh()
        }.also { panelButtons[option] = it }
        sleepButton = tool(Panel.SLEEP, R.string.player_sleep)
        silenceButton = button(getString(R.string.player_silence), getString(R.string.player_silence_desc), 52, BAR) {
            podcasts?.let { it.setSkipsSilence(!it.skipsSilence()) }
            refresh()
        }
        val tools = evenRow(listOf(sleepButton, silenceButton, tool(Panel.NOTES, R.string.player_notes), tool(Panel.CHAPTERS, R.string.player_chapters),
            tool(Panel.EQ, R.string.player_eq), tool(Panel.LYRICS, R.string.player_lyrics)))
        panelList = lineList(upNextLines)
        if (isShort()) tools.layoutParams = LinearLayout.LayoutParams(0, dp(TOUCH), 1f).apply { leftMargin = dp(GAP); rightMargin = dp(GAP) }
        val top = row(
            button("<", getString(R.string.player_library), 44, BAR) { show(Screen.LIBRARY) }, if (isShort()) tools else spring(),
            button(getString(R.string.player_queue), getString(R.string.player_queue), 72, BAR) { show(Screen.QUEUE) },
        )
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            clipChildren = false
            addView(top)
            if (heightDp >= MIN_HEIGHT_FOR_ART) addView(art, LinearLayout.LayoutParams(dp(artSize), dp(artSize)).apply { gravity = Gravity.CENTER_HORIZONTAL; bottomMargin = dp(8) })
            addView(title)
            addView(sub, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(2) })
            addView(LinearLayout(context).apply {
                gravity = Gravity.CENTER_VERTICAL
                addView(position)
                addView(seek, LinearLayout.LayoutParams(0, dp(32), 1f))
                addView(remaining)
            })
            addView(transport, LinearLayout.LayoutParams(-1, -2).apply { if (isShort()) bottomMargin = dp(10) })
            if (!isShort()) addView(tools, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) })
            addView(section(upNextTitle, panelList), LinearLayout.LayoutParams(-1, 0, 1f))
        }
    }

    private fun podcastNow() = service?.currentSong()?.getSource() == MusicService.SOURCE_PODCAST

    private fun skip(ms: Int) {
        podcasts?.seekBy(ms)
        showTime()
    }

    private fun askSpeed(anchor: View) {
        val p = podcasts ?: return
        val menu = PopupMenu(anchor.context, anchor)
        PodcastManager.PLAYBACK_SPEEDS.forEachIndexed { i, speed -> menu.menu.add(0, i + 1, i, PodcastManager.formatSpeed(speed)) }
        menu.setOnMenuItemClickListener { item ->
            PodcastManager.PLAYBACK_SPEEDS.getOrNull(item.itemId - 1)?.let { p.setPlaybackSpeed(it) }
            refresh()
            true
        }
        menu.show()
    }

    private fun togglePlay() {
        if (podcastNow()) podcasts?.toggle() else manager?.play()
        handler.postDelayed({ refresh() }, 150)
    }

    private fun refreshPlayer() {
        val srv = service
        val song = srv?.currentSong()
        title.text = song?.getTitle() ?: getString(R.string.player_nothing)
        sub.text = song?.let {
            if (srv.trouble != null) getString(if (srv.trouble == MusicService.Trouble.OFFLINE) R.string.player_waiting_network else R.string.player_reconnecting)
            else if (it.getSource() == MusicService.SOURCE_PODCAST) it.getSinger()
            else listOf(it.getSinger(), LibraryBrowse.albumOf(it)).filter(String::isNotBlank).distinct().joinToString(" / ")
        }.orEmpty()
        val playing = srv?.isPlaying == true
        playButton.text = if (srv?.isPreparing == true) "..." else getString(if (playing) R.string.player_pause else R.string.player_play)
        playButton.contentDescription = if (srv?.isPreparing == true) getString(R.string.player_buffering) else playButton.text
        if (song?.getSource() == MusicService.SOURCE_PODCAST) showPodcastTransport() else showMusicTransport(srv)
        showTime()
        refreshPanel(song, srv)
        loadArt(song)
    }

    private fun showMusicTransport(srv: MusicService?) {
        fun labelled(b: TextView, text: String, desc: String) { b.text = text; b.contentDescription = desc }
        labelled(backButton, "<<", getString(R.string.player_previous))
        labelled(forwardButton, ">>", getString(R.string.player_next))
        labelled(shuffleButton, getString(R.string.player_shuffle), getString(R.string.player_shuffle))
        mark(shuffleButton, srv?.queueShuffled == true)
        val repeat = srv?.queueRepeat ?: PlayQueue.Repeat.ALL
        labelled(repeatButton, getString(when (repeat) {
            PlayQueue.Repeat.OFF -> R.string.player_repeat_off
            PlayQueue.Repeat.ALL -> R.string.player_repeat_all
            PlayQueue.Repeat.ONE -> R.string.player_repeat_one
        }), getString(R.string.player_repeat))
        mark(repeatButton, repeat != PlayQueue.Repeat.OFF)
    }

    /** A podcast keeps the same five buttons: speed, short skips, and on to the next item. */
    private fun showPodcastTransport() {
        val speed = PodcastManager.formatSpeed(podcasts?.playbackSpeed() ?: 1f)
        shuffleButton.text = speed
        shuffleButton.contentDescription = getString(R.string.surface_podcast_playback_speed_7a955, speed)
        paint(shuffleButton, false)
        backButton.text = "-15"
        backButton.contentDescription = getString(R.string.player_back_15)
        forwardButton.text = "+30"
        forwardButton.contentDescription = getString(R.string.player_forward_30)
        repeatButton.text = ">>"
        repeatButton.contentDescription = getString(R.string.player_next)
        paint(repeatButton, false)
    }

    private fun showTime() {
        if (screen != Screen.PLAYER || seeking) return
        val srv = service ?: return
        val pos = srv.posn
        val dur = srv.dur
        position.text = clock(pos)
        remaining.text = "-" + clock((dur - pos).coerceAtLeast(0))
        seek.progress = if (dur > 0) (pos.toLong() * SLIDER_MAX / dur).toInt() else 0
        showSleep(srv)
        markChapters(dur)
        val now = when (panel) {
            Panel.LYRICS -> lyrics?.let { Lyrics.indexAt(it, pos) }
            Panel.CHAPTERS -> Chapters.indexAt(chapters(srv), pos)
            else -> null
        }
        if (now != null && now != highlighted) refreshPanel(srv.currentSong(), srv)
    }

    private fun showSleep(srv: MusicService?) {
        val left = srv?.sleepLeftMs ?: 0
        val atEnd = srv?.sleepAtTrackEnd == true
        val text = when {
            atEnd -> getString(R.string.player_sleep_end_short)
            left > 0 -> getString(R.string.player_sleep_left, (left + 59_999) / 60_000)
            else -> getString(R.string.player_sleep)
        }
        if (sleepButton.text != text) {
            sleepButton.text = text
            mark(sleepButton, atEnd || left > 0 || panel == Panel.SLEEP)
        }
    }

    private fun chapters(srv: MusicService?): List<Chapter> = feedChapters.ifEmpty { srv?.taggedChapters.orEmpty() }

    private fun markChapters(durationMs: Int) {
        track.marks = if (durationMs > 0) chapters(service).map { it.startMs.toFloat() / durationMs }.filter { it > 0f } else emptyList()
    }

    private fun loadSongExtras(song: Song?) {
        if (song !== chaptersFor) {
            chaptersFor = song
            feedChapters = emptyList()
            song?.let { podcasts?.episodeOf(it) }?.let { episode ->
                podcasts?.feedChapters(episode) { found -> if (chaptersFor === song) { feedChapters = found; refresh() } }
            }
        }
        if (song !== lyricsFor) {
            lyricsFor = song
            lyrics = null
            if (song == null || song.getSource() == MusicService.SOURCE_PODCAST) return
            Thread {
                val found = Lyrics.load(this, song)
                handler.post { if (lyricsFor === song) { lyrics = found; refresh() } }
            }.start()
        }
    }

    /** The space under the controls: what is next, or whichever tool is open. */
    private fun refreshPanel(song: Song?, srv: MusicService?) {
        loadSongExtras(song)
        val podcast = song?.getSource() == MusicService.SOURCE_PODCAST
        if ((podcast && panel in MUSIC_PANELS) || (!podcast && panel in PODCAST_PANELS)) panel = Panel.UP_NEXT
        silenceButton.visibility = if (podcast) View.VISIBLE else View.GONE
        panelButtons.forEach { (option, b) ->
            b.visibility = if ((podcast && option in MUSIC_PANELS) || (!podcast && option in PODCAST_PANELS)) View.GONE else View.VISIBLE
            mark(b, option == panel)
        }
        mark(silenceButton, podcasts?.skipsSilence() == true)
        mark(sleepButton, srv?.sleepAtTrackEnd == true || (srv?.sleepLeftMs ?: 0) > 0 || panel == Panel.SLEEP)
        showSleep(srv)
        highlighted = -2
        when (panel) {
            Panel.UP_NEXT -> showUpNext(srv)
            Panel.SLEEP -> showSleepChoices(srv)
            Panel.EQ -> showEqualizer(srv)
            Panel.LYRICS -> showLyrics(srv)
            Panel.NOTES -> showNotes(song)
            Panel.CHAPTERS -> showChapters(srv)
        }
    }

    private fun showUpNext(srv: MusicService?) {
        val items = srv?.queueSnapshot().orEmpty()
        val at = srv?.songIndex ?: 0
        val after = items.drop(at + 1)
        upNextTitle.text = getString(R.string.player_up_next, after.size)
        upNextLines.set(after.take(UP_NEXT_ROWS).mapIndexed { i, s ->
            Line(s.getTitle(), s.getSinger(), tap = { srv?.editQueue { jumpTo(at + 1 + i) }; refresh() })
        })
    }

    private fun showSleepChoices(srv: MusicService?) {
        upNextTitle.text = getString(R.string.player_sleep)
        val set = (srv?.sleepLeftMs ?: 0) > 0 || srv?.sleepAtTrackEnd == true
        upNextLines.set(listOf(Line(getString(R.string.player_sleep_off), on = !set, tap = { srv?.sleepIn(0); refresh() })) +
            SLEEP_MINUTES.map { minutes -> Line(getString(R.string.player_sleep_minutes, minutes), tap = { srv?.sleepIn(minutes); refresh() }) } +
            Line(getString(R.string.player_sleep_end), on = srv?.sleepAtTrackEnd == true, tap = { srv?.sleepAfterThisTrack(); refresh() }))
    }

    private fun showEqualizer(srv: MusicService?) {
        upNextTitle.text = getString(R.string.player_eq)
        val effects = srv?.effects
        if (effects == null || effects.presets.isEmpty()) {
            upNextLines.set(listOf(Line(getString(R.string.player_eq_none), wrap = true)))
            return
        }
        upNextLines.set(listOf(Line(getString(R.string.player_sleep_off), on = effects.preset !in effects.presets.indices, tap = { effects.select(AudioEffects.OFF); refresh() })) +
            effects.presets.mapIndexed { i, name -> Line(name, on = i == effects.preset, tap = { effects.select(i); refresh() }) })
    }

    private fun showLyrics(srv: MusicService?) {
        upNextTitle.text = getString(R.string.player_lyrics)
        val lines = lyrics
        if (lines.isNullOrEmpty()) {
            highlighted = -1
            upNextLines.set(listOf(Line(getString(if (lines == null) R.string.player_looking else R.string.player_lyrics_none), wrap = true)))
            return
        }
        val now = Lyrics.indexAt(lines, srv?.posn ?: 0)
        highlighted = now
        upNextLines.set(lines.mapIndexed { i, line ->
            Line(line.text.ifEmpty { " " }, on = i == now, wrap = true, tap = line.ms?.let { ms -> { srv?.seek(ms); Unit } })
        })
        if (now >= 0) (panelList.layoutManager as LinearLayoutManager).scrollToPositionWithOffset(now, panelList.height / 3)
    }

    private fun showNotes(song: Song?) {
        upNextTitle.text = getString(R.string.player_notes)
        val notes = song?.let { podcasts?.episodeOf(it) }?.let { podcasts?.notes(it) }.orEmpty()
        upNextLines.set(listOf(Line(notes.ifEmpty { getString(R.string.player_notes_none) }, wrap = true)))
    }

    private fun showChapters(srv: MusicService?) {
        upNextTitle.text = getString(R.string.player_chapters)
        val list = chapters(srv)
        if (list.isEmpty()) {
            highlighted = -1
            upNextLines.set(listOf(Line(getString(R.string.player_chapters_none), wrap = true)))
            return
        }
        val now = Chapters.indexAt(list, srv?.posn ?: 0)
        highlighted = now
        upNextLines.set(list.mapIndexed { i, chapter ->
            Line(chapter.title.ifEmpty { getString(R.string.player_chapter_n, i + 1) }, meta = clock(chapter.startMs), on = i == now, tap = { podcasts?.seekTo(chapter.startMs); showTime(); Unit })
        })
    }

    private fun loadArt(song: Song?) {
        if (song === artFor) return
        artFor = song
        val srv = service
        val p = podcasts
        if (song?.getSource() == MusicService.SOURCE_PODCAST) {
            val url = p?.artworkUrl(song)
            if (url != null && url == artUrl && art.visibility == View.VISIBLE) return
            artUrl = url
            art.visibility = View.GONE
            if (url != null) drawArt(song) { p.artwork(url) }
            return
        }
        artUrl = null
        art.visibility = View.GONE
        val uri = song?.let { srv?.uriOf(it) } ?: return
        drawArt(song) {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(this, uri)
                retriever.embeddedPicture?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
            } finally {
                retriever.release()
            }
        }
    }

    private fun drawArt(song: Song, source: () -> android.graphics.Bitmap?) {
        Thread {
            val dithered = runCatching { source()?.let { Dither.apply(it, ART_DOTS, ink) } }.getOrNull()
            handler.post {
                if (artFor === song && dithered != null) {
                    art.setImageDrawable(BitmapDrawable(resources, dithered).apply { paint.isFilterBitmap = false })
                    art.visibility = View.VISIBLE
                }
            }
        }.start()
    }

    // ---- queue ----

    private fun buildQueue(): View {
        queueTitle = titled("")
        val list = lineList(queueLines)
        val helper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0) {
            private var from = -1
            private var to = -1
            override fun isLongPressDragEnabled() = false
            override fun onSwiped(holder: RecyclerView.ViewHolder, direction: Int) {}
            override fun onMove(view: RecyclerView, source: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean {
                if (from == -1) from = source.bindingAdapterPosition
                to = target.bindingAdapterPosition
                queueLines.swap(source.bindingAdapterPosition, to)
                return true
            }
            override fun clearView(view: RecyclerView, holder: RecyclerView.ViewHolder) {
                super.clearView(view, holder)
                dragging = false
                val a = from
                val b = to
                from = -1
                to = -1
                if (a != -1 && b != -1 && a != b) service?.editQueue { move(a, b) }
                refresh()
            }
        })
        helper.attachToRecyclerView(list)
        queueLines.startDrag = { holder -> dragging = true; helper.startDrag(holder) }
        queueLines.remove = { i -> service?.editQueue { removeAt(i) }; refresh() }
        val top = row(
            button("<", getString(R.string.player_now_playing), 44, BAR) { show(Screen.PLAYER) }, spring(),
            button(getString(R.string.player_save_list), getString(R.string.player_save_list), 72, BAR) { askListName() },
            Space(this).apply { layoutParams = LinearLayout.LayoutParams(dp(GAP), 1) },
            button(getString(R.string.player_add), getString(R.string.player_add_desc), 48, BAR) { show(Screen.LIBRARY) },
            Space(this).apply { layoutParams = LinearLayout.LayoutParams(dp(GAP), 1) },
            button(getString(R.string.player_clear), getString(R.string.player_clear), 60, BAR) { service?.editQueue { clear() }; refresh() },
        )
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            clipChildren = false
            addView(top, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) })
            addView(section(queueTitle, list), LinearLayout.LayoutParams(-1, 0, 1f))
        }
    }

    private fun askListName() {
        val songs = service?.queueSnapshot().orEmpty()
        if (songs.isEmpty()) return
        TuixtDialog.showValidatedForm(this, getString(R.string.player_save_list),
            listOf(TuixtDialog.FormField("name", getString(R.string.player_list_name))),
            getString(R.string.surface_save_50815), getString(R.string.surface_cancel_1507c),
            { values -> if (values["name"].isNullOrBlank()) getString(R.string.player_list_name_needed) else null },
        ) { values ->
            val name = values["name"].orEmpty().trim()
            playlists.save(name, songs)
            Toast.makeText(this, getString(R.string.player_list_saved, name), Toast.LENGTH_SHORT).show()
        }
    }

    private fun refreshQueue() {
        if (dragging) return
        val srv = service
        val items = srv?.queueSnapshot().orEmpty()
        val at = srv?.songIndex ?: 0
        queueTitle.text = getString(R.string.player_queue_count, items.size)
        queueLines.set(items.mapIndexed { i, s ->
            Line(s.getTitle(), s.getSinger(), on = i == at, tap = { srv?.editQueue { jumpTo(i) }; refresh() })
        })
    }

    private fun refresh() {
        when (screen) {
            Screen.LIBRARY -> refreshLibrary()
            Screen.PLAYER -> refreshPlayer()
            Screen.QUEUE -> refreshQueue()
        }
    }

    // ---- rows ----

    private inner class Lines(private val editable: Boolean = false) : RecyclerView.Adapter<Lines.Holder>() {
        private var lines: List<Line> = emptyList()
        var startDrag: (RecyclerView.ViewHolder) -> Unit = {}
        var remove: (Int) -> Unit = {}

        @SuppressLint("NotifyDataSetChanged")
        fun set(next: List<Line>) {
            lines = next
            notifyDataSetChanged()
        }

        fun swap(a: Int, b: Int) {
            lines = ArrayList(lines).also { Collections.swap(it, a, b) }
            notifyItemMoved(a, b)
        }

        inner class Holder(val root: LinearLayout, val handle: TextView, val title: TextView, val sub: TextView, val meta: TextView, val x: TextView) : RecyclerView.ViewHolder(root)

        @SuppressLint("ClickableViewAccessibility")
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            val handle = label("=").apply {
                gravity = Gravity.CENTER
                contentDescription = getString(R.string.player_reorder)
                visibility = if (editable) View.VISIBLE else View.GONE
            }
            val title = label().apply { isSingleLine = true; ellipsize = TextUtils.TruncateAt.END }
            val sub = label(size = 11f).apply { isSingleLine = true; ellipsize = TextUtils.TruncateAt.END }
            val meta = label().apply { setPadding(dp(10), 0, 0, 0) }
            val x = label("x").apply {
                gravity = Gravity.CENTER
                contentDescription = getString(R.string.player_remove)
                visibility = if (editable) View.VISIBLE else View.GONE
            }
            val texts = LinearLayout(parent.context).apply {
                orientation = LinearLayout.VERTICAL
                addView(title)
                addView(sub, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(2) })
            }
            val root = LinearLayout(parent.context).apply {
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = RecyclerView.LayoutParams(-1, dp(ROW))
                setPadding(dp(8), 0, if (editable) 0 else dp(8), 0)
                addView(handle, LinearLayout.LayoutParams(dp(32), -1))
                addView(texts, LinearLayout.LayoutParams(0, -2, 1f))
                addView(meta)
                addView(x, LinearLayout.LayoutParams(dp(TOUCH), -1))
            }
            return Holder(root, handle, title, sub, meta, x).also { holder ->
                handle.setOnTouchListener { _, event ->
                    if (event.actionMasked == MotionEvent.ACTION_DOWN) startDrag(holder)
                    false
                }
                x.setOnClickListener { holder.bindingAdapterPosition.takeIf { it != RecyclerView.NO_POSITION }?.let(remove) }
            }
        }

        override fun getItemCount() = lines.size

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val line = lines[position]
            val fg = if (line.on) onSelected else if (line.header) head else ink
            holder.root.setBackgroundColor(if (line.on) sel else Color.TRANSPARENT)
            holder.root.layoutParams = holder.root.layoutParams.apply { height = if (line.wrap) -2 else dp(ROW) }
            holder.root.setPadding(holder.root.paddingLeft, if (line.wrap) dp(WRAP_PADDING) else 0, holder.root.paddingRight, if (line.wrap) dp(WRAP_PADDING) else 0)
            holder.title.isSingleLine = !line.wrap
            holder.title.ellipsize = if (line.wrap) null else TextUtils.TruncateAt.END
            holder.title.text = line.title
            holder.sub.text = line.sub
            holder.sub.visibility = if (line.sub.isEmpty()) View.GONE else View.VISIBLE
            holder.meta.text = line.meta
            holder.title.setTextColor(fg)
            holder.handle.setTextColor(fg)
            holder.x.setTextColor(fg)
            holder.meta.setTextColor(fg)
            holder.sub.setTextColor(if (line.on) onSelected else dim)
            holder.root.setOnClickListener(line.tap?.let { tap -> View.OnClickListener { tap() } })
            holder.root.isClickable = line.tap != null
            holder.root.setOnLongClickListener(line.hold?.let { hold -> View.OnLongClickListener { hold(); true } })
        }
    }

    /** The seek bar's hairline, with a tick at each chapter. */
    private inner class MarkedLine : Drawable() {
        var marks: List<Float> = emptyList()
            set(value) { if (field != value) { field = value; invalidateSelf() } }
        private val paint = Paint().apply { color = ink }

        override fun draw(canvas: Canvas) {
            val y = bounds.exactCenterY()
            val thin = maxOf(1, dp(1)).toFloat()
            canvas.drawRect(bounds.left.toFloat(), y, bounds.right.toFloat(), y + thin, paint)
            marks.forEach { mark ->
                val x = bounds.left + bounds.width() * mark
                canvas.drawRect(x, y - dp(3), x + thin, y + dp(3) + thin, paint)
            }
        }

        override fun setAlpha(alpha: Int) {}
        override fun setColorFilter(filter: ColorFilter?) {}
        @Deprecated("Deprecated in Java") override fun getOpacity() = PixelFormat.TRANSLUCENT
    }

    companion object {
        const val EXTRA_NOW_PLAYING = "now_playing"
        const val EXTRA_TAB = "tab"
        const val EXTRA_PODCAST_COMMAND = "podcast_command"
        /** True while the player is on screen, so the launcher can hide the podcast pill. */
        @Volatile var visible = false
        private const val KEY_TAB = "tab"
        private const val KEY_SHELF = "shelf"
        private const val KEY_TAG = "tag"
        private const val SHORT_HEIGHT_DP = 420f
        private const val SHORT_TABS_WEIGHT = 1.6f
        private const val KEY_LISTS = "lists"
        private const val KEY_UNWATCHED = "unwatched"
        private const val KEY_PANEL = "panel"
        private const val SEARCH_ROWS = 40
        private const val ACROSS_ROWS = 60
        private const val NARROW = 0.65f
        private const val WRAP_PADDING = 6
        private val SLEEP_MINUTES = listOf(15, 30, 45, 60)
        private val MUSIC_PANELS = setOf(Panel.EQ, Panel.LYRICS)
        private val PODCAST_PANELS = setOf(Panel.NOTES, Panel.CHAPTERS)
        private const val SKIP_BACK = 15_000
        private const val SKIP_FORWARD = 30_000
        private const val LONG_SKIP = 30_000
        private const val KEY_SCREEN = "screen"
        private const val KEY_BY = "by"
        private const val KEY_QUERY = "query"
        private const val TICK = 500L
        private const val GAP = 6
        private const val ROW = 44
        private const val UP_NEXT_ROWS = 20
        private const val ART_DOTS = 96
        private const val MIN_HEIGHT_FOR_ART = 560f
    }
}
