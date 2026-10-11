package ohi.andre.consolelauncher.community

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.graphics.Typeface
import android.view.View
import org.json.JSONObject
import ohi.andre.consolelauncher.help.HelpWindowPanel
import ohi.andre.consolelauncher.commands.tuixt.TuixtLayout
import ohi.andre.consolelauncher.tuils.TerminalBorderRuntime
import ohi.andre.consolelauncher.tuils.FrameTarget
import ohi.andre.consolelauncher.tuils.LauncherFontScale
import ohi.andre.consolelauncher.managers.settings.AppearanceSettings
import android.view.KeyEvent
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.*
import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.localization.LocalizedComponentActivity
import ohi.andre.consolelauncher.managers.settings.ThemeColorResolver
import ohi.andre.consolelauncher.managers.xml.XMLPrefsManager
import ohi.andre.consolelauncher.managers.xml.options.Theme
import ohi.andre.consolelauncher.managers.xml.options.SurfaceBorder
import ohi.andre.consolelauncher.managers.suggestions.SuggestionChipStyler
import ohi.andre.consolelauncher.managers.suggestions.SuggestionsManager
import ohi.andre.consolelauncher.tuils.LauncherSystemUi
import ohi.andre.consolelauncher.tuils.Tuils

class CommunityActivity : LocalizedComponentActivity() {
    companion object {
        @JvmField internal var debugBackend: String? = null
        fun open(context: Context) {
            val intent = Intent(context, CommunityActivity::class.java)
            if (context !is android.app.Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
    }
    private lateinit var client: CommunityClient
    private lateinit var store: CommunityStore
    private var state = CommunityState()
    private var storageReady = true
    private var busy = false
    private var refreshing = false
    private var pendingLabel: Int? = null
    @Volatile private var visible = false
    private var generation = 0
    private var job: java.util.concurrent.Future<*>? = null
    private var pollDelay = 15_000L
    private val poll = Runnable { if (visible && !busy && state.account != null) refresh() }
    private val expireCache = object : Runnable {
        override fun run() {
            if (!visible) return
            val changed = state.expire() or state.mentions.expire()
            if (changed || (mentionsView && displayedMentions.any { id -> state.mentions.items.none { it.optString("id") == id } }) || (pane.channel == "general" && !mentionsView && displayed.keys.any { id -> state.messages.none { it.optString("id") == id } })) {
                val previousStatus = status.text
                render(); if (storageReady) persist()
                if (storageReady) status.text = previousStatus
            } else markDisplayed()
            ui.removeCallbacks(this); ui.postDelayed(this, minOf(state.expiryDelay(), state.mentions.expiryDelay()))
        }
    }
    private val saveDraft = Runnable { if (::store.isInitialized && storageReady) persist() }
    private var leaveDialog: android.app.AlertDialog? = null
    private var deletionDialog: android.app.AlertDialog? = null
    private var moderationDialog: android.app.AlertDialog? = null
    private var rulesDialog: android.app.AlertDialog? = null
    private val auth = CommunityAuthInput()
    private var secureBeforeAuth = false
    private var authError = ""
    private val worker = java.util.concurrent.Executors.newSingleThreadExecutor()
    private val ui = android.os.Handler(android.os.Looper.getMainLooper())
    private lateinit var input: CommunityEditText
    private lateinit var status: TextView
    private lateinit var unread: TextView
    private val displayed = mutableMapOf<String, IntRange>()
    private var displayedMentions = emptySet<String>()
    private lateinit var output: TextView
    private lateinit var scroll: ScrollView
    private var restoringViewport = false
    private var renderGeneration = 0
    private var readCurrent = false
    private val pane get() = state.pane
    private lateinit var root: View
    private lateinit var windowPanel: HelpWindowPanel
    private val editingTitle get() = pane.composing && pane.editingTitle
    private lateinit var records: LinearLayout
    private var switching = false
    private var threads: List<JSONObject>
        get() = pane.threads
        set(value) { pane.threads = value }
    private var replies: List<JSONObject>
        get() = pane.replies
        set(value) { pane.replies = value }
    private var threadRoot: JSONObject?
        get() = pane.root
        set(value) { pane.root = value }
    private var forumCursor: JSONObject?
        get() = pane.cursor
        set(value) { pane.cursor = value }
    private var mentionsView = false
    private var mentionPage = 1
    private var mentionMore = false
    private lateinit var presenceClient: CommunityClient
    private lateinit var lookupClient: CommunityClient
    private lateinit var notificationClient: CommunityClient
    private val presenceWorker = java.util.concurrent.Executors.newSingleThreadExecutor()
    private val lookupWorker = java.util.concurrent.Executors.newSingleThreadExecutor()
    private val notificationWorker = java.util.concurrent.Executors.newSingleThreadExecutor()
    private var presenceJob: java.util.concurrent.Future<*>? = null
    private var lookupJob: java.util.concurrent.Future<*>? = null
    private var notificationJob: java.util.concurrent.Future<*>? = null
    private var foregroundGeneration = 0
    private var lookupGeneration = 0
    private var lookupKey = ""
    private val presencePoll = Runnable { renewPresence() }
    private val notificationPoll = Runnable { pollMentions() }
    private val mentionLookup = Runnable { lookupUsers() }
    private lateinit var liveClient: CommunityClient
    private val liveWorker = java.util.concurrent.Executors.newSingleThreadExecutor()
    private var liveJob: java.util.concurrent.Future<*>? = null
    private var liveConnected = false
    private var liveRetry = 1000L
    private val liveChanges = linkedMapOf<String, String>()
    private var liveInvalidated = false
    private var liveLatestViewport = false
    private val reconnectLive = Runnable { startLive() }
    private val receiveLive = Runnable { drainLiveChanges() }
    override fun onCreate(saved: Bundle?) {
        LauncherSystemUi.requestNoTitleIfFullscreen(this)
        super.onCreate(saved)
        val backend = if (ohi.andre.consolelauncher.BuildConfig.DEBUG) debugBackend ?: CommunityClient.PRODUCTION else CommunityClient.PRODUCTION
        client = CommunityClient(backend)
        presenceClient = CommunityClient(backend); lookupClient = CommunityClient(backend); notificationClient = CommunityClient(backend)
        liveClient = CommunityClient(backend)
        store = CommunityStore(this)
        try { state = CommunityState(store.load(), client::now) } catch (_: Exception) { storageReady = false }
        Tuils.init(this); XMLPrefsManager.loadCommons(this); LauncherSystemUi.applyFullscreen(this)
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        val screen = FrameLayout(this)
        val host = TuixtLayout.addFoldAwareHost(this, screen, ViewGroup.LayoutParams.MATCH_PARENT)
        val viewport = FrameLayout(this); host.addView(viewport, ViewGroup.LayoutParams(-1, -1))
        root = layoutInflater.inflate(R.layout.community_surface, viewport, false)
        windowPanel = HelpWindowPanel(viewport, root, saved, "community",
            root.findViewById(R.id.community_header), root.findViewById(R.id.community_header_row), initiallyMaximized = true, showHeaderReset = false)
        windowPanel.reserveBottomCorners(root.findViewById(R.id.community_completion_scroll))
        status = root.findViewById(R.id.community_status)
        unread = root.findViewById(R.id.community_unread)
        output = root.findViewById(R.id.community_output)
        scroll = root.findViewById(R.id.community_scroll)
        unread.setOnClickListener { refresh(returnLatest = true) }
        status.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val normal = state.account?.let { account -> listOf(R.string.community_live, R.string.community_current, R.string.community_stale).map { getString(it, account.handle) } }
                    ?: listOf(getString(R.string.community_signed_out))
                status.visibility = if (s.isNullOrEmpty() || s.toString() in normal) View.GONE else View.VISIBLE
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })
        records = root.findViewById(R.id.community_records)
        input = root.findViewById(R.id.community_input)
        root.findViewById<View>(R.id.community_thread_summary).setOnClickListener {
            if (!auth.active && pane.composing && !editingTitle) {
                saveCurrentDraft(); pane.editingTitle = true; restoreDraft(); controls(); persist(); input.requestFocus()
            }
        }
        input.selectionChanged = { scheduleLookup() }
        input.setOnKeyListener { _, key, event ->
            if (key == KeyEvent.KEYCODE_ENTER && !event.isShiftPressed) {
                if (event.action == KeyEvent.ACTION_UP) submit()
                true
            } else false
        }
        input.setOnEditorActionListener { _, action, _ -> if (action == EditorInfo.IME_ACTION_SEND || action == EditorInfo.IME_ACTION_NEXT) { submit(); true } else false }
        scroll.setOnScrollChangeListener { _, _, _, _, _ -> if (!restoringViewport) { rememberViewport(); markDisplayed() } }
        root.findViewById<View>(R.id.community_history).setOnClickListener { refresh(older = true) }
        output.customSelectionActionModeCallback = object : android.view.ActionMode.Callback {
            override fun onCreateActionMode(mode: android.view.ActionMode, menu: android.view.Menu) = true
            override fun onPrepareActionMode(mode: android.view.ActionMode, menu: android.view.Menu): Boolean {
                menu.removeItem(R.id.community_output)
                if (selectedMessage() != null && !auth.active) menu.add(0, R.id.community_output, 0, R.string.community_message_actions)
                return true
            }
            override fun onActionItemClicked(mode: android.view.ActionMode, item: android.view.MenuItem): Boolean {
                if (item.itemId != R.id.community_output) return false
                selectedMessage()?.let { messageActions(it) }; mode.finish(); return true
            }
            override fun onDestroyActionMode(mode: android.view.ActionMode) {}
        }
        root.findViewById<Button>(R.id.community_send).setOnClickListener { submit() }
        root.findViewById<View>(R.id.community_body).addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> adaptRail() }
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(screen) { view, insets ->
            val bars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars() or androidx.core.view.WindowInsetsCompat.Type.displayCutout() or androidx.core.view.WindowInsetsCompat.Type.ime())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom); insets
        }
        stylePane()
        setContentView(screen)
        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = back()
        })
        restoreDraft()
        input.isSaveEnabled = false
        input.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (switching || auth.active) return
                saveCurrentDraft(); if (editingTitle) updateAuthGuidance(); scheduleLookup()
                ui.removeCallbacks(saveDraft); ui.postDelayed(saveDraft, 250)
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })
        render()
        saved?.let { input.setSelection(it.getInt("selection_start").coerceIn(0, input.length()), it.getInt("selection_end").coerceIn(0, input.length())) }
        if (storageReady) persist()
    }
    override fun onSaveInstanceState(out: Bundle) {
        cancelAuth()
        windowPanel.save(out); out.putInt("selection_start", input.selectionStart); out.putInt("selection_end", input.selectionEnd)
        super.onSaveInstanceState(out)
    }
    override fun onResume() {
        super.onResume(); visible = true; generation++; stylePane()
        expireCache.run()
        if (state.account != null && storageReady) {
            refresh(returnLatest = pane.channel != "general" && pane.thread.isNotEmpty() && replies.isEmpty() && !pane.replyHistory)
            startForeground()
        }
    }
    override fun onPause() {
        deletionDialog?.dismiss(); moderationDialog?.dismiss(); rulesDialog?.dismiss()
        cancelAuth()
        rememberViewport(); windowPanel.persist()
        visible = false; stopForeground(); generation++; ui.removeCallbacks(poll); ui.removeCallbacks(expireCache); ui.removeCallbacks(saveDraft)
        job?.cancel(true); client.cancel(); busy = false; pendingLabel = null
        if (storageReady) persist()
        super.onPause()
    }
    override fun onDestroy() {
        cancelAuth(); stopForeground(); ui.removeCallbacksAndMessages(null); worker.shutdownNow(); client.cancel()
        presenceWorker.shutdownNow(); lookupWorker.shutdownNow(); notificationWorker.shutdownNow()
        liveWorker.shutdownNow()
        super.onDestroy()
    }
    private fun startForeground() {
        if (!visible || state.account == null || !storageReady) return
        renewPresence(); pollMentions(); scheduleLookup(); startLive()
    }
    private fun stopForeground() {
        foregroundGeneration++; lookupGeneration++; lookupKey = ""
        ui.removeCallbacks(presencePoll); ui.removeCallbacks(notificationPoll); ui.removeCallbacks(mentionLookup)
        presenceJob?.cancel(true); presenceClient.cancel()
        lookupJob?.cancel(true); lookupClient.cancel()
        notificationJob?.cancel(true); notificationClient.cancel()
        ui.removeCallbacks(reconnectLive); ui.removeCallbacks(receiveLive)
        liveJob?.cancel(true); liveClient.cancel(); liveConnected = false
        liveChanges.clear(); liveInvalidated = false; liveLatestViewport = false
        if (::root.isInitialized) root.findViewById<View>(R.id.community_completion_scroll).visibility = View.INVISIBLE
        // The server lease expires within 90 seconds. Never chain a release after cancellation.
    }
    private fun startLive() {
        val account = state.account ?: return
        if (!visible || !storageReady || liveJob?.isDone == false) return
        val epoch = foregroundGeneration
        liveJob = liveWorker.submit {
            val result = runCatching { liveClient.live(account, connected = {
                ui.post {
                    if (!visible || epoch != foregroundGeneration || state.account?.id != account.id) return@post
                    liveConnected = true; liveRetry = 1000L
                    updateConnection()
                    // Close the gap between the initial/resumed history read and subscribing.
                    liveInvalidated = true; ui.removeCallbacks(receiveLive); ui.post(receiveLive)
                }
            }, changed = { event ->
                ui.post {
                    if (!visible || epoch != foregroundGeneration || state.account?.id != account.id) return@post
                    val collection = event.optString("collection"); val id = event.optString("id")
                    if (event.optString("action") == "invalidate") liveInvalidated = true
                    else if (collection in setOf("messages", "threads", "thread_replies") && id.matches(Regex("[a-zA-Z0-9]{15}"))) {
                        if (liveChanges.size >= 50) { liveChanges.clear(); liveInvalidated = true }
                        else liveChanges["$collection/$id"] = collection
                    }
                    ui.removeCallbacks(receiveLive); ui.postDelayed(receiveLive, 100)
                }
            }) }
            ui.post {
                if (!visible || epoch != foregroundGeneration || state.account?.id != account.id) return@post
                val wasLive = liveConnected
                liveConnected = false
                updateConnection()
                val error = result.exceptionOrNull()
                if (error is CommunityClient.Failure && error.status in setOf(401, 403)) { logout(true); return@post }
                val delay = if (error is CommunityClient.Failure && error.status == 429) error.retrySeconds * 1000 else liveRetry
                liveRetry = (liveRetry * 2).coerceAtMost(30_000L)
                ui.removeCallbacks(reconnectLive); ui.postDelayed(reconnectLive, delay)
                if (wasLive) { ui.removeCallbacks(poll); ui.postDelayed(poll, 15_000L) }
            }
        }
    }
    private fun drainLiveChanges() {
        val account = state.account ?: return
        if (!visible || !storageReady) return
        if (busy) { ui.postDelayed(receiveLive, 100); return }
        if (liveInvalidated) {
            val latest = liveLatestViewport
            liveInvalidated = false; liveLatestViewport = false; liveChanges.clear(); refresh(returnLatest = latest); pollMentions(); return
        }
        val next = liveChanges.entries.firstOrNull() ?: return
        val collection = next.value; val id = next.key.substringAfter('/')
        liveChanges.remove(next.key)
        request({ try { client.source(account, collection, id) } catch (error: CommunityClient.Failure) {
            if (error.status == 404) null else throw error
        } }, background = true, failed = { liveInvalidated = true }) { record ->
            when (collection) {
                "messages" -> {
                    record?.let { state.observeRealtime(it) }
                    state.merge(listOfNotNull(record), setOf(id))
                }
                "threads" -> {
                    threads = threads.filterNot { it.optString("id") == id }
                    if (record?.optString("channel") == pane.channel && !pane.history)
                        threads = (threads + record).sortedWith(compareByDescending<JSONObject> { it.optString("created") }.thenByDescending { it.optString("id") }).take(200)
                    if (pane.thread == id) { threadRoot = record; if (record == null) replies = emptyList() }
                }
                "thread_replies" -> {
                    replies = replies.filterNot { it.optString("id") == id }
                    if (record?.optString("thread") == pane.thread) {
                        val merged = (replies + record).sortedWith(compareBy<JSONObject>({ it.optString("created") }, { it.optString("id") }))
                        replies = if (pane.replyHistory) merged.take(200) else merged.takeLast(200)
                    }
                }
            }
            persist(); render(true); pollMentions()
        }
    }
    private fun preparePost(): Boolean {
        if (busy && refreshing) { cancelPageRequest(); liveInvalidated = true }
        if (!busy) return true
        input.error = getString(R.string.community_busy); return false
    }
    private fun redrawPending(latest: Boolean = false) {
        val previous = status.text; render(latestViewport = latest); status.text = previous
    }
    private fun appendPending(text: android.text.SpannableStringBuilder, body: String) {
        val handle = state.account?.handle ?: return
        text.append("@$handle · ").append(getString(pendingLabel ?: R.string.community_not_confirmed)).append("\n")
        appendBody(text, body); text.append("\n\n")
    }
    private fun renewPresence() {
        val account = state.account ?: return
        if (!visible || !storageReady || presenceJob?.isDone == false) return
        val epoch = foregroundGeneration
        presenceJob = presenceWorker.submit {
            val result = runCatching { presenceClient.operation { presenceClient.presence(account, true) } }
            ui.post {
                if (!visible || epoch != foregroundGeneration || state.account?.id != account.id) return@post
                val error = result.exceptionOrNull()
                if (error is CommunityClient.Failure && error.status in setOf(401, 403)) { logout(true); return@post }
                ui.removeCallbacks(presencePoll)
                if (error is CommunityClient.Failure && error.status == 429) ui.postDelayed(presencePoll, error.retrySeconds * 1000)
                else ui.postDelayed(presencePoll, 30_000L)
            }
        }
    }
    private fun pollMentions() {
        val account = state.account ?: return
        if (!visible || !storageReady || notificationJob?.isDone == false) return
        if (mentionsView) { ui.postDelayed(notificationPoll, 30_000L); return }
        val epoch = foregroundGeneration
        val cached = state.mentions.items.toList()
        notificationJob = notificationWorker.submit {
            val result = runCatching { notificationClient.operation { notificationClient.mentionWindow(account, cached) } }
            ui.post {
                if (!visible || epoch != foregroundGeneration || state.account?.id != account.id) return@post
                result.getOrNull()?.let { response ->
                    val array = response.optJSONArray("items")
                    if (array != null && !mentionsView) {
                        state.mentions.replace((0 until array.length()).map { array.getJSONObject(it) }, keepUnread = true)
                        controls(); persist()
                    }
                }
                val error = result.exceptionOrNull()
                if (error is CommunityClient.Failure && error.status in setOf(401, 403)) { logout(true); return@post }
                ui.removeCallbacks(notificationPoll)
                ui.postDelayed(notificationPoll, if (error is CommunityClient.Failure && error.status == 429) error.retrySeconds * 1000 else 30_000L)
            }
        }
    }
    private fun scheduleLookup() {
        if (!::input.isInitialized || switching || !::root.isInitialized || auth.active) return
        val key = "${input.text}|${input.selectionStart}|${input.selectionEnd}|${pane.context}"
        if (key == lookupKey) return
        lookupKey = key; lookupGeneration++
        ui.removeCallbacks(mentionLookup); lookupJob?.cancel(true); lookupClient.cancel()
        root.findViewById<View>(R.id.community_completion_scroll).visibility = View.INVISIBLE
        renderCommandSuggestions()
        if (!visible || state.account == null || editingTitle || input.selectionStart != input.selectionEnd || mentionsView || (pane.channel != "general" && !pane.composing && pane.thread.isEmpty())) return
        if (CommunityMentionInput.fragment(input.text.toString(), input.selectionStart) != null) ui.postDelayed(mentionLookup, 250)
    }
    private fun lookupUsers(page: Int = 1) {
        val account = state.account ?: return
        if (!visible || !storageReady || auth.active || editingTitle || input.selectionStart != input.selectionEnd) return
        val text = input.text.toString(); val caret = input.selectionStart
        val fragment = CommunityMentionInput.fragment(text, caret) ?: return
        val epoch = foregroundGeneration; val query = lookupGeneration
        lookupJob = lookupWorker.submit {
            val result = runCatching { lookupClient.operation { lookupClient.users(account, fragment, page) } }
            ui.post {
                if (!visible || epoch != foregroundGeneration || query != lookupGeneration || state.account?.id != account.id || input.text.toString() != text || input.selectionStart != caret || input.selectionEnd != caret) return@post
                val error = result.exceptionOrNull()
                if (error is CommunityClient.Failure && error.status in setOf(401, 403)) { logout(true); return@post }
                val response = result.getOrNull() ?: return@post
                val rows = root.findViewById<LinearLayout>(R.id.community_completions); rows.removeAllViews()
                val items = response.optJSONArray("items") ?: return@post
                for (i in 0 until minOf(items.length(), 20)) {
                    val user = items.getJSONObject(i); val handle = user.optString("handle")
                    if (!handle.matches(Regex("[-_a-z0-9]{3,24}"))) continue
                    rows.addView(suggestion(getString(if (user.optBoolean("online")) R.string.community_online else R.string.community_offline, handle), "community_complete_$handle", false) {
                        if (input.text.toString() != text || input.selectionStart != caret || input.selectionEnd != caret) { scheduleLookup(); return@suggestion }
                        val replacement = CommunityMentionInput.insert(text, caret, handle)
                        input.setText(replacement); input.setSelection((caret - fragment.length + handle.length).coerceIn(0, input.length()))
                        ui.removeCallbacks(mentionLookup); lookupGeneration++; root.findViewById<View>(R.id.community_completion_scroll).visibility = View.INVISIBLE
                        input.requestFocus()
                    })
                }
                if (response.optBoolean("hasMore")) rows.addView(suggestion(getString(R.string.community_more), "community_users_more", false) { lookupUsers(page + 1) })
                root.findViewById<View>(R.id.community_completion_scroll).visibility = if (rows.childCount > 0) View.VISIBLE else View.INVISIBLE
            }
        }
    }
    private fun renderCommandSuggestions() {
        val rows = root.findViewById<LinearLayout>(R.id.community_completions); rows.removeAllViews()
        val strip = root.findViewById<View>(R.id.community_completion_scroll)
        strip.visibility = View.INVISIBLE
        if (auth.active || mentionsView || root.findViewById<View>(R.id.community_prompt_row).visibility != View.VISIBLE) return
        val text = input.text.toString()
        for (command in CommunityInput.suggestions(text, state.account != null)) rows.addView(suggestion(command, "community_command_$command", true) {
            // Insertion only: a tap never sends a message or submits credentials.
            input.setText(command); input.setSelection(input.length()); input.requestFocus()
        })
        if (state.account == null && CommunityMentionInput.fragment(text, input.selectionStart) != null) rows.addView(suggestion(getString(R.string.community_login_required), "community_mention_login", true) {
            input.setText("/login"); input.setSelection(input.length()); input.requestFocus()
        })
        strip.visibility = if (rows.childCount > 0) View.VISIBLE else View.INVISIBLE
    }
    private fun suggestion(label: String, tagName: String, command: Boolean, action: () -> Unit): View {
        val spaces = SuggestionChipStyler.spacing()
        val chip = SuggestionChipStyler.createView(this, spaces).apply {
            text = label
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        SuggestionChipStyler(this).apply(chip,
            if (command) SuggestionsManager.Suggestion.TYPE_COMMAND else SuggestionsManager.Suggestion.TYPE_CONTACT, null)
        // Match the home-screen chip's visible geometry without shrinking its touch target.
        return FrameLayout(this).apply {
            tag = tagName; contentDescription = label
            minimumWidth = dp(48); minimumHeight = dp(48)
            layoutParams = LinearLayout.LayoutParams(-2, -1).apply { setMargins(spaces[0], spaces[1], spaces[0], spaces[1]) }
            addView(chip, FrameLayout.LayoutParams(-2, -2, android.view.Gravity.CENTER))
            isClickable = true; isFocusable = true; setOnClickListener { action() }
        }
    }
    private fun dp(value: Int) = Tuils.dpToPx(this, value)
    private fun button(label: Int, tagName: String, action: () -> Unit) = button(getString(label), tagName, action)
    private fun button(label: String, tagName: String, action: () -> Unit) = Button(this).apply {
        tag = tagName; text = label; isAllCaps = false; gravity = android.view.Gravity.START or android.view.Gravity.CENTER_VERTICAL
        minWidth = dp(48); minimumWidth = dp(48); minHeight = dp(48); minimumHeight = dp(48)
        setPadding(dp(6), 0, dp(6), 0); background = null
        typeface = Tuils.getTypeface(this@CommunityActivity); textSize = LauncherFontScale.terminalTextSizeSp()
        setTextColor(ThemeColorResolver.color(Theme.workspace_control_text_color)); setOnClickListener { action() }
    }
    private fun channelLabel(channel: String): String = getString(when (channel) {
        "bugs" -> R.string.community_bugs; "help" -> R.string.community_help_channel
        "features" -> R.string.community_suggestions; "presets" -> R.string.community_presets
        else -> R.string.community_general
    })
    private fun stylePane() {
        fun style(view: View) {
            if (view.id == R.id.community_completions) return
            if (view is TextView) { view.typeface = Tuils.getTypeface(this); view.textSize = LauncherFontScale.terminalTextSizeSp()
                view.setTextColor(ThemeColorResolver.color(if (view is Button) Theme.workspace_control_text_color else Theme.workspace_panel_text_color)) }
            if (view is ViewGroup) for (i in 0 until view.childCount) style(view.getChildAt(i))
        }
        style(root); root.setBackgroundColor(android.graphics.Color.TRANSPARENT)
        // Keep status/suggestion budgets stable; the composer grows only when its text needs more lines.
        fun reserve(view: View, height: Int) { view.layoutParams = view.layoutParams.apply { this.height = height } }
        val line = kotlin.math.ceil(input.paint.fontSpacing.toDouble()).toInt()
        root.findViewById<View>(R.id.community_prompt_row).minimumHeight = maxOf(dp(48), line + dp(8))
        reserve(root.findViewById(R.id.community_guidance_row), maxOf(dp(48), line * 2 + dp(4)))
        val spaces = SuggestionChipStyler.spacing()
        val suggestionChip = SuggestionChipStyler.createView(this, spaces).apply { text = "/" }
        val unspecified = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        suggestionChip.measure(unspecified, unspecified)
        reserve(root.findViewById(R.id.community_completion_scroll), maxOf(dp(48), suggestionChip.measuredHeight) + spaces[1] * 2)
        reserve(root.findViewById<View>(R.id.community_actions).parent as View, maxOf(dp(48), line + dp(12)))
        status.setLines(2); unread.setLines(1)
        root.findViewById<View>(R.id.community_panel).background = TerminalBorderRuntime.panelDrawable(this,
            ThemeColorResolver.color(Theme.workspace_panel_background_color), ThemeColorResolver.color(Theme.workspace_panel_border_color),
            1.5f, AppearanceSettings.outputCornerRadius(), AppearanceSettings.dashedBorders(), target = FrameTarget.OVERLAYS)
        root.findViewById<View>(R.id.community_main_pane).background = TerminalBorderRuntime.panelDrawable(this,
            ThemeColorResolver.color(Theme.workspace_output_background_color), ThemeColorResolver.withMaxAlpha(ThemeColorResolver.color(Theme.workspace_panel_border_color), 210),
            1.2f, AppearanceSettings.outputCornerRadius(), AppearanceSettings.dashedBorders(), target = FrameTarget.OVERLAYS)
        root.findViewById<TextView>(R.id.community_header).apply {
            includeFontPadding = false; setTypeface(Tuils.getTypeface(this@CommunityActivity), Typeface.BOLD)
            textSize = AppearanceSettings.outputHeaderTextSize().toFloat()
            background = TerminalBorderRuntime.tabDrawable(this@CommunityActivity, ThemeColorResolver.color(Theme.workspace_header_background_color), FrameTarget.OVERLAYS)
        }
        TerminalBorderRuntime.bind(root.findViewById(R.id.community_panel), root.findViewById(R.id.community_header))
        root.findViewById<View>(R.id.community_divider).setBackgroundColor(ThemeColorResolver.color(Theme.workspace_panel_border_color))
        input.background = TerminalBorderRuntime.panelDrawable(this,
            ThemeColorResolver.color(Theme.workspace_input_background_color), AppearanceSettings.surfaceBorderColor(SurfaceBorder.INPUT),
            1.5f, AppearanceSettings.dashedBorderCornerRadius(), AppearanceSettings.dashedBorders(),
            borderEnabled = AppearanceSettings.surfaceBorderEnabled(SurfaceBorder.INPUT), target = FrameTarget.INPUT)
        input.setPadding(dp(8), dp(4), dp(8), dp(4))
        input.setTextColor(ThemeColorResolver.color(Theme.workspace_input_text_color))
        root.findViewById<Button>(R.id.community_send).background = null
        windowPanel.refreshTheme(); adaptRail()
    }
    private fun adaptRail() {
        val body = root.findViewById<View>(R.id.community_body)
        if (body.width == 0) return
        val rows = root.findViewById<LinearLayout>(R.id.community_channel_rows)
        val required = (0 until rows.childCount).maxOfOrNull { i ->
            val row = rows.getChildAt(i) as TextView
            row.paint.measureText(row.text.toString()).toInt() + dp(16)
        } ?: dp(48)
        val wide = body.width * .2f >= required.coerceAtLeast(dp(48)) && body.width * .8f >= dp(240)
        val visibility = if (wide) View.VISIBLE else View.GONE
        val rail = root.findViewById<View>(R.id.community_rail)
        if (rail.visibility != visibility) { rail.visibility = visibility; root.findViewById<View>(R.id.community_divider).visibility = visibility }
        root.findViewWithTag<View>("community_channels")?.visibility = if (wide) View.GONE else View.VISIBLE
    }
    private fun saveCurrentDraft() {
        if (auth.active) return
        val text = input.text.toString()
        if (text.startsWith("/") && !text.startsWith("//")) return
        if (pane.channel == "general") state.draft = text
        else if (pane.composing || pane.thread.isNotEmpty()) {
            val draft = pane.draft(pane.context)
            val titleStep = editingTitle
            pane.saveDraft(pane.context, if (titleStep) text else if (pane.composing) draft.optString("title") else "",
                if (titleStep) draft.optString("body") else text)
            if (pane.composing) pane.editingTitle = titleStep
        }
    }
    private fun restoreDraft() {
        if (auth.active) return
        switching = true
        val inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or
            if (editingTitle) 0 else android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
        if (input.inputType != inputType) input.inputType = inputType
        input.maxLines = if (editingTitle) 1 else 3
        input.imeOptions = (if (editingTitle) EditorInfo.IME_ACTION_NEXT else EditorInfo.IME_ACTION_SEND) or EditorInfo.IME_FLAG_NO_EXTRACT_UI
        input.setText(if (pane.channel == "general") state.draft else pane.draft(pane.context).optString(if (editingTitle) "title" else "body"))
        input.setSelection(input.length())
        switching = false; scheduleLookup()
    }
    private fun cancelPageRequest() {
        generation++; job?.cancel(true); client.cancel(); busy = false; pendingLabel = null; ui.removeCallbacks(poll)
        lookupGeneration++; lookupKey = ""; ui.removeCallbacks(mentionLookup); lookupJob?.cancel(true); lookupClient.cancel()
        root.findViewById<View>(R.id.community_completion_scroll).visibility = View.INVISIBLE
    }
    private fun selectChannel(channel: String) {
        cancelAuth()
        saveCurrentDraft(); cancelPageRequest(); mentionsView = false
        pane.channel = channel; pane.thread = ""; pane.composing = false
        threads = emptyList(); replies = emptyList(); threadRoot = null; forumCursor = null; pane.history = false
        if (channel == "general") { state.following = true; state.viewAnchor = "" }
        restoreDraft(); persist(); render(latestViewport = channel == "general"); if (state.account != null) refresh(returnLatest = channel == "general")
    }
    private fun openThread(id: String, focus: JSONObject? = null, channel: String = pane.channel, loadedRoot: JSONObject? = null) {
        cancelAuth()
        saveCurrentDraft(); cancelPageRequest(); mentionsView = false
        pane.channel = channel; pane.thread = id; pane.composing = false; replies = emptyList(); threadRoot = null; forumCursor = null
        pane.replyHistory = focus != null; pane.replyFollowing = focus == null; pane.replyAnchor = ""; pane.replyOffset = 0
        if (focus != null) pane.focusReply(focus)
        threadRoot = loadedRoot
        restoreDraft(); persist(); render(latestViewport = true); if (loadedRoot == null) refresh(returnLatest = focus == null)
    }
    private fun back() {
        if (auth.active) { cancelAuth(); return }
        if (mentionsView) { mentionsView = false; render(); return }
        if (pane.composing && !editingTitle) {
            saveCurrentDraft(); pane.editingTitle = true; restoreDraft(); controls(); persist(); return
        }
        if (pane.thread.isNotEmpty() || pane.composing) {
            saveCurrentDraft(); cancelPageRequest(); pane.thread = ""; pane.composing = false
            replies = emptyList(); threadRoot = null
            restoreDraft(); persist(); render(); if (state.account != null) refresh()
        } else leave()
    }
    private fun controls() {
        val rows = root.findViewById<LinearLayout>(R.id.community_channel_rows); rows.removeAllViews()
        for (channel in CommunityPaneState.CHANNELS) {
            val count = state.mentions.unread(channel, state.blocked)
            rows.addView(button((if (channel == pane.channel) "> " else "") + channelLabel(channel) + if (count > 0) " @$count" else "", "community_channel_$channel") { selectChannel(channel) }, LinearLayout.LayoutParams(-1, -2))
        }
        val actions = root.findViewById<LinearLayout>(R.id.community_actions); actions.removeAllViews()
        val mentionCount = CommunityPaneState.CHANNELS.sumOf { state.mentions.unread(it, state.blocked) }
        val badge = if (mentionCount > 0) " @$mentionCount" else ""
        actions.addView(button(getString(R.string.community_channels) + badge, "community_channels") {
            android.app.AlertDialog.Builder(this).setTitle(R.string.community_channels)
                .setItems(CommunityPaneState.CHANNELS.map { channelLabel(it) + state.mentions.unread(it, state.blocked).let { n -> if (n > 0) " @$n" else "" } }.toTypedArray()) { _, i -> selectChannel(CommunityPaneState.CHANNELS[i]) }.show()
        })
        if (pane.thread.isNotEmpty() || pane.composing || mentionsView) actions.addView(button(if (pane.composing && !editingTitle) R.string.community_edit_title else R.string.community_back, "community_back") { back() })
        if (pane.channel != "general" && pane.thread.isEmpty() && !pane.composing && !mentionsView) actions.addView(button(R.string.community_new_thread, "community_new_thread") {
            cancelAuth()
            saveCurrentDraft(); pane.composing = true; restoreDraft(); render(); input.requestFocus(); persist()
        })
        if (state.account == null && !auth.active) {
            actions.addView(button(R.string.community_join, "community_join") { login(true) })
            actions.addView(button(R.string.community_sign_in, "community_sign_in") { login() })
        }
        if (state.account != null) {
            actions.addView(button(R.string.community_refresh, "community_latest") { refresh(returnLatest = true) })
            actions.addView(button(getString(R.string.community_mentions) + badge, "community_mentions") { showMentions() })
        }
        if (state.pendingId != null || pane.pendingId != null) actions.addView(button(R.string.community_retry, "community_retry") { checkDelivery() })
        actions.addView(button(R.string.community_menu, "community_menu") { menu() })
        root.findViewById<Button>(R.id.community_history).apply {
            visibility = if (state.account != null && !pane.composing && !mentionsView) View.VISIBLE else View.GONE
            setText(R.string.community_older)
        }
        val editable = auth.active || (!mentionsView && (state.account == null || pane.channel == "general" || pane.composing || pane.thread.isNotEmpty()))
        val prompt = root.findViewById<View>(R.id.community_prompt_row)
        prompt.visibility = if (editable) View.VISIBLE else View.INVISIBLE
        prompt.importantForAccessibility = if (editable) View.IMPORTANT_FOR_ACCESSIBILITY_AUTO else View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        input.isEnabled = editable && auth.stage != CommunityAuthInput.Stage.SUBMITTING
        root.findViewById<View>(R.id.community_send).isEnabled = input.isEnabled
        input.hint = getString(when {
            auth.active -> when (auth.stage) {
                CommunityAuthInput.Stage.USERNAME -> R.string.community_username
                CommunityAuthInput.Stage.PASSWORD -> R.string.community_password
                CommunityAuthInput.Stage.CONFIRMATION -> R.string.community_confirm_password
                else -> R.string.community_busy
            }
            editingTitle -> R.string.community_thread_title
            state.account == null -> R.string.community_signed_out_hint
            pane.composing -> R.string.community_discussion_hint
            pane.thread.isNotEmpty() -> R.string.community_reply_hint
            else -> R.string.community_message_hint
        })
        root.findViewById<Button>(R.id.community_send).setText(when {
            auth.active && auth.stage in setOf(CommunityAuthInput.Stage.USERNAME, CommunityAuthInput.Stage.PASSWORD) && !(auth.stage == CommunityAuthInput.Stage.PASSWORD && !auth.registering) -> R.string.community_next
            auth.active && auth.registering -> R.string.community_create_account
            auth.active -> R.string.community_sign_in
            editingTitle -> R.string.community_next
            pane.composing -> R.string.community_post_discussion
            else -> R.string.community_submit
        })
        if (!editable) { input.clearFocus(); (getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager).hideSoftInputFromWindow(input.windowToken, 0) }
        updateAuthGuidance()
        lookupKey = ""; scheduleLookup()
        adaptRail()
    }
    private fun menu() {
        val labels = (if (state.account == null) listOf(R.string.community_login, R.string.community_register) else
            listOf(R.string.community_message_actions, R.string.community_moderate, R.string.community_delete_account, R.string.community_logout) +
                if (state.account?.handle == "dvil") listOf(R.string.community_review_queue) else emptyList()) +
            listOf(R.string.community_rules_title, R.string.community_reset_window, R.string.community_help_action)
        android.app.AlertDialog.Builder(this).setTitle(R.string.community_menu).setItems(labels.map { getString(it) }.toTypedArray()) { _, i ->
            when (labels[i]) {
                R.string.community_login -> login(); R.string.community_register -> login(true); R.string.community_logout -> logout()
                R.string.community_message_actions -> chooseMessage()
                R.string.community_reset_window -> windowPanel.resetWindow()
                R.string.community_moderate -> privateRequest()
                R.string.community_review_queue -> reviewQueue()
                R.string.community_delete_account -> deleteAccount()
                R.string.community_rules_title -> showRules()
                else -> { displayed.clear(); records.removeAllViews(); output.setText(R.string.community_help) }
            }
        }.show()
    }
    private fun conversationMessages(): List<JSONObject> {
        state.expire()
        return (if (pane.channel == "general") state.messages else listOfNotNull(threadRoot) + replies)
            .filterNot { it.optString("author_handle") in state.blocked }
    }
    private fun selectedMessage(): JSONObject? {
        if (mentionsView || pane.composing) return null
        val start = minOf(output.selectionStart, output.selectionEnd)
        val end = maxOf(output.selectionStart, output.selectionEnd)
        if (start < 0 || start == end) return null
        val id = displayed.filterValues { start <= it.last && end > it.first }.keys.singleOrNull() ?: return null
        return conversationMessages().firstOrNull { it.optString("id") == id }
    }
    private fun chooseMessage() {
        if (mentionsView || pane.composing) return
        val messages = conversationMessages().takeLast(200)
        android.app.AlertDialog.Builder(this).setTitle(R.string.community_message_actions)
            .setItems(messages.map { CommunityMessagePresentation.header(it) + "\n" + CommunityMessagePresentation.preview(it) }.toTypedArray()) { _, i ->
                messages.getOrNull(i)?.let { messageActions(it) }
            }.show()
    }
    private fun messageActions(record: JSONObject) {
        val id = record.optString("id"); val context = pane.context; val accountId = state.account?.id
        val handle = record.optString("author_handle")
        val labels = mutableListOf(R.string.community_copy_message)
        if (handle.matches(Regex("[-_a-zA-Z0-9]{3,24}"))) labels.add(R.string.community_mention_author)
        if (state.account != null) labels.add(R.string.community_report)
        if (handle != state.account?.handle) labels.add(R.string.community_block_author)
        android.app.AlertDialog.Builder(this).setTitle(CommunityMessagePresentation.header(record))
            .setItems(labels.map { getString(it) }.toTypedArray()) { _, i ->
                if (state.account?.id != accountId || pane.context != context || auth.active) return@setItems
                val current = conversationMessages().firstOrNull { it.optString("id") == id }
                    ?: run { status.setText(R.string.community_source_gone); return@setItems }
                when (labels[i]) {
                    R.string.community_copy_message -> (getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager)
                        .setPrimaryClip(android.content.ClipData.newPlainText(getString(R.string.community_copy_message), current.optString("body")))
                    R.string.community_mention_author -> {
                        val caret = input.selectionStart.coerceIn(0, input.length())
                        val prefix = if (caret > 0 && !input.text[caret - 1].isWhitespace()) " " else ""
                        input.text.insert(caret, "$prefix@$handle "); input.requestFocus()
                    }
                    R.string.community_block_author -> {
                        if (!state.blocked.add(handle)) state.blocked.remove(handle)
                        persist(); render(); status.text = getString(R.string.community_blocked, handle)
                    }
                    R.string.community_report -> {
                        if (!ensurePolicy()) return@setItems
                        val reason = EditText(this).apply { setHint(R.string.community_report_reason); isSaveEnabled = false }
                        val collection = if (pane.channel == "general") "messages" else if (id == threadRoot?.optString("id")) "threads" else "thread_replies"
                        android.app.AlertDialog.Builder(this).setTitle(R.string.community_report)
                            .setMessage(CommunityMessagePresentation.header(current) + "\n" + CommunityMessagePresentation.preview(current)).setView(reason)
                            .setPositiveButton(R.string.community_report) { _, _ ->
                                val account = state.account?.takeIf { it.id == accountId } ?: return@setPositiveButton
                                val text = reason.text.toString().ifBlank { getString(R.string.community_report_default) }
                                request({ client.reportSource(account, collection, id, text) }) { status.setText(R.string.community_reported) }
                            }.setNegativeButton(R.string.community_cancel, null).show()
                    }
                }
            }.show()
    }
    private fun checkDelivery() {
        if (auth.active || state.account == null) return
        if (state.pendingId != null) {
            if (pane.channel != "general" || mentionsView) selectChannel("general")
            sendGeneral(state.pendingBody)
        } else if (pane.pendingId != null) {
            if (pane.pendingContext == pane.context && !mentionsView) submitForum(pane.pending.optString("body"))
            else retryForum()
        }
    }
    private fun privateRequest() {
        val body = EditText(this).apply { isSaveEnabled = false; minHeight = dp(48); setHint(R.string.community_moderate) }
        val dialog = android.app.AlertDialog.Builder(this).setTitle(R.string.community_moderate).setView(body)
            .setPositiveButton(R.string.community_submit, null).setNegativeButton(R.string.community_cancel, null).create()
        dialog.setOnShowListener { dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            if (CommunityInput.message(body.text.toString()) !is CommunityInput.Message) { body.error = getString(R.string.community_invalid); return@setOnClickListener }
            sendPrivate(body.text.toString()); dialog.dismiss()
        } }; dialog.show()
    }
    private fun sendPrivate(body: String) {
        val account = state.account ?: run { input.error = getString(R.string.community_login_required); return }
        if (!ensurePolicy()) return
        request({ client.moderate(account, body) }, failed = { status.setText(R.string.community_private_uncertain) }) { status.setText(R.string.community_private_sent) }
    }
    private fun ensurePolicy(): Boolean {
        if (state.account?.policyVersion == CommunityClient.POLICY_VERSION) return true
        showRules(); return false
    }
    private fun showRules() {
        if (rulesDialog?.isShowing == true) return
        val account = state.account
        val builder = android.app.AlertDialog.Builder(this).setTitle(R.string.community_rules_title)
            .setMessage(R.string.community_rules_body).setNegativeButton(R.string.community_cancel, null)
        if (account != null && account.policyVersion != CommunityClient.POLICY_VERSION) {
            builder.setPositiveButton(R.string.community_accept_rules) { _, _ ->
                request({ client.acceptPolicy(account) }) { next ->
                    if (state.account?.id == next.id) { state.signIn(next); persist(); status.setText(R.string.community_rules_accepted) }
                }
            }
        } else builder.setPositiveButton(android.R.string.ok, null)
        rulesDialog = builder.show()
    }
    private fun reviewQueue(queue: String = "moderator_requests", page: Int = 1) {
        val account = state.account ?: return
        request({ client.moderation(account, queue, page) }) { result ->
            result.optJSONObject("operations")?.takeIf { !it.optBoolean("healthy") }?.let {
                status.text = getString(R.string.community_operations_warning, it.optJSONArray("alerts")?.join(", ").orEmpty())
            }
            val array = result.getJSONArray("items")
            val rows = (0 until array.length()).map { array.getJSONObject(it) }
            moderationDialog = android.app.AlertDialog.Builder(this).setTitle(getString(R.string.community_queue_page, queue, page))
                .setItems((rows.map { "@" + it.optString("author_handle") + "\n" + it.getString("body").take(160) } +
                    listOf(getString(R.string.community_queue_switch))).toTypedArray()) { _, position ->
                    if (position == rows.size) reviewQueue(if (queue == "reports") "moderator_requests" else "reports")
                    else reviewRequest(rows[position])
                }.setNegativeButton(R.string.community_cancel, null)
                .setNeutralButton(R.string.community_queue_previous) { _, _ -> reviewQueue(queue, (page - 1).coerceAtLeast(1)) }
                .setPositiveButton(if (result.optBoolean("hasMore")) R.string.community_queue_next else R.string.community_refresh_action) { _, _ ->
                    reviewQueue(queue, if (result.optBoolean("hasMore")) page + 1 else page)
                }.show()
        }
    }
    private fun reviewRequest(row: JSONObject) {
        val account = state.account ?: return
        val actions = if (row.optString("source_id").isEmpty()) listOf("dismiss") else listOf("dismiss", "remove", "ban")
        val labels = actions.map { getString(when (it) { "remove" -> R.string.community_remove_content; "ban" -> R.string.community_ban_author; else -> R.string.community_resolve_request }) }
        moderationDialog = android.app.AlertDialog.Builder(this).setTitle(R.string.community_review_queue)
            .setMessage(row.getString("body") + "\n\n@" + row.optString("author_handle") + "\n" + row.optString("snapshot_body"))
            .setPositiveButton(R.string.community_moderation_actions) { _, _ ->
                moderationDialog = android.app.AlertDialog.Builder(this).setTitle(R.string.community_moderation_actions)
                    .setItems(labels.toTypedArray()) { _, index ->
                        val action = actions[index]
                        moderationDialog = android.app.AlertDialog.Builder(this).setTitle(labels[index])
                            .setMessage(R.string.community_moderation_confirm)
                            .setPositiveButton(R.string.community_confirm_action) { _, _ ->
                                request({ client.resolve(account, row.getString("queue"), row.getString("id"), action) }) {
                                    status.setText(R.string.community_request_resolved); reviewQueue(row.getString("queue"))
                                }
                            }.setNegativeButton(R.string.community_cancel, null).show()
                    }.setNegativeButton(R.string.community_cancel, null).show()
            }.setNegativeButton(R.string.community_cancel, null).show()
    }
    private fun deleteAccount() {
        val account = state.account ?: return
        if (busy && refreshing) cancelPageRequest()
        if (busy) { input.error = getString(R.string.community_busy); return }
        cancelAuth()
        val secure = window.attributes.flags and android.view.WindowManager.LayoutParams.FLAG_SECURE != 0
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
        val password = EditText(this).apply {
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
            setHint(R.string.community_password); isSaveEnabled = false
            imeOptions = EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING or EditorInfo.IME_FLAG_NO_EXTRACT_UI
            if (android.os.Build.VERSION.SDK_INT >= 26) importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
        }
        val dialog = android.app.AlertDialog.Builder(this).setTitle(R.string.community_delete_account)
            .setMessage(getString(R.string.community_delete_explanation, account.handle)).setView(password)
            .setPositiveButton(R.string.community_delete_account, null).setNegativeButton(R.string.community_cancel, null).create()
        deletionDialog = dialog
        dialog.setOnDismissListener { password.text.clear(); if (!secure) window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE) }
        dialog.setOnShowListener { dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            if (busy && !refreshing) { password.error = getString(R.string.community_busy); return@setOnClickListener }
            if (password.text.isNullOrEmpty()) { password.error = getString(R.string.community_auth_password_error); return@setOnClickListener }
            val secret = password.text.toString(); password.text.clear()
            dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).isEnabled = false
            request({ client.deleteAccount(account, secret) }, failed = { dialog.dismiss() }) { deleted ->
                dialog.dismiss()
                if (deleted) { logout(); status.setText(R.string.community_account_deleted) }
                else status.setText(R.string.community_deletion_failed)
            }
        } }
        dialog.show()
    }
    private fun renderForum(latestViewport: Boolean) {
        val y = scroll.scrollY
        displayed.clear(); unread.text = ""; unread.visibility = View.GONE; records.removeAllViews()
        if (pane.composing) {
            val text = android.text.SpannableStringBuilder(getString(R.string.community_new_thread) + "\n\n")
            if (pane.pendingId != null && pane.pendingContext == pane.context) appendPending(text, pane.pending.optString("body"))
            output.text = text; return
        }
        if (pane.thread.isEmpty()) {
            output.text = if (threads.isEmpty()) getString(R.string.community_threads_empty) else ""
            for (thread in threads.filterNot { it.optString("author_handle") in state.blocked }) records.addView(button(
                thread.optString("title") + "\n" + CommunityMessagePresentation.header(thread) + "\n" + CommunityMessagePresentation.preview(thread), "community_thread_${thread.optString("id")}") { openThread(thread.getString("id")) }, LinearLayout.LayoutParams(-1, -2))
        } else {
            val text = android.text.SpannableStringBuilder()
            fun appendRecord(record: JSONObject) {
                val start = text.length
                if (record.has("title")) text.append(record.optString("title")).append("\n")
                appendMessageHeader(text, record)
                appendBody(text, record.optString("body")); text.append("\n\n")
                displayed[record.getString("id")] = start until text.length
            }
            threadRoot?.takeUnless { it.optString("author_handle") in state.blocked }?.let { appendRecord(it) }
            replies.filterNot { it.optString("author_handle") in state.blocked }.forEach { appendRecord(it) }
            if (pane.pendingId != null && pane.pendingContext == pane.context && replies.none { it.optString("client_id") == pane.pendingId }) appendPending(text, pane.pending.optString("body"))
            output.text = text
        }
        positionViewport(latestViewport && pane.thread.isNotEmpty(), y)
    }
    private fun refreshForum(older: Boolean, latest: Boolean) {
        val account = state.account ?: run { input.error = getString(R.string.community_login_required); return }
        val channel = pane.channel; val id = pane.thread
        if (pane.composing) return
        if (id.isEmpty()) {
            val ids = if (older) emptyList() else threads.map { it.getString("id") }
            request({ client.threads(account, channel, if (older) forumCursor else null) to client.checked(account, ids, "threads") }, background = true) { (page, checked) ->
                if (!older) threads = checked.filter { it.optString("channel") == channel }.sortedWith(compareByDescending<JSONObject> { it.getString("created") }.thenByDescending { it.getString("id") })
                pane.mergeThreads(page, older, latest)
                persist(); render(true); if (latest) scroll.post { scroll.scrollTo(0, 0) }
            }
        } else {
            val ids = if (older) emptyList() else replies.map { it.getString("id") }
            val cursor = if (older) replies.firstOrNull() else if (!latest) forumCursor else null
            request({
                Triple(client.source(account, "threads", id), client.replies(account, id, cursor, latest, older), client.checked(account, ids, "thread_replies"))
            }, background = true, failed = { error ->
                if (error is CommunityClient.Failure && error.status == 404) { threadRoot = null; replies = emptyList(); persist(); render(); status.setText(R.string.community_source_gone) }
            }) { (thread, page, checked) ->
                threadRoot = thread
                if (older) { pane.replyHistory = true; pane.replyFollowing = false }
                if (latest) { pane.replyHistory = false; pane.replyFollowing = true; replies = page }
                else {
                    val merged = ((if (older) replies else checked.filter { it.optString("thread") == id }) + page)
                        .distinctBy { it.getString("id") }.sortedWith(compareBy({ it.getString("created") }, { it.getString("id") }))
                    replies = if (pane.replyHistory) merged.take(200) else merged.takeLast(200)
                }
                forumCursor = replies.lastOrNull()
                persist(); render(true, latest)
            }
        }
    }
    private fun submitForum(body: String) {
        val account = state.account ?: run { input.error = getString(R.string.community_login_required); return }
        if (!ensurePolicy()) return
        if (!preparePost()) return
        val payload = if (pane.pendingId != null) {
            if (pane.pendingContext != pane.context) { input.error = getString(R.string.community_pending_locked); return }
            pane.pending
        } else JSONObject().put("body", body).apply {
            if (pane.thread.isEmpty()) { put("channel", pane.channel); put("title", pane.draft(pane.context).optString("title")) }
            else put("thread", pane.thread)
        }
        if (payload.has("title") && (payload.getString("title").isBlank() || payload.getString("title").codePointCount(0, payload.getString("title").length) > 120 || payload.getString("title").toByteArray(Charsets.UTF_8).size > 480)) {
            input.error = getString(R.string.community_title_error); return
        }
        val uncertain = pane.pendingId != null; val context = pane.context
        val uuid = pane.begin(context, payload); saveCurrentDraft(); if (!storageReady || !persist()) return
        status.setText(R.string.community_pending)
        request({ if (payload.has("thread")) client.postReply(account, payload.getString("thread"), payload.getString("body"), uuid, uncertain)
            else client.postThread(account, payload.getString("channel"), payload.getString("title"), payload.getString("body"), uuid, uncertain)
        }, pendingLabel = if (uncertain) R.string.community_checking_delivery else R.string.community_sending, failed = { error ->
            if (error is CommunityClient.Failure && error.status in setOf(400, 428, 429) && !uncertain) { pane.reject(); persist() }
            redrawPending()
        }) { record ->
            pane.complete(); restoreDraft(); persist()
            if (payload.has("thread")) {
                val returnFromHistory = pane.replyHistory
                pane.replyHistory = false; pane.replyFollowing = true
                replies = (replies + record).distinctBy { it.getString("id") }.takeLast(200); render(true, true); status.setText(R.string.community_posted)
                if (returnFromHistory) { liveInvalidated = true; liveLatestViewport = true }
            }
            else { pane.composing = false; openThread(record.getString("id"), loadedRoot = record); status.setText(R.string.community_posted) }
        }
        redrawPending(true)
    }
    private fun retryForum() {
        saveCurrentDraft(); val context = pane.pendingContext
        if (context.startsWith("reply:")) {
            val account = state.account ?: return
            val pendingId = pane.pendingId ?: return
            val id = context.removePrefix("reply:")
            request({ client.source(account, "threads", id) }) { parent ->
                if (pane.pendingId != pendingId || pane.pendingContext != context) return@request
                val channel = parent.getString("channel")
                if (channel !in CommunityPaneState.CHANNELS || channel == "general") return@request
                openThread(id, channel = channel, loadedRoot = parent)
                submitForum(pane.pending.optString("body"))
            }
        } else if (context in CommunityPaneState.CHANNELS && context != "general") {
            selectChannel(context); pane.composing = true; restoreDraft(); render(); submitForum(pane.pending.optString("body"))
        }
    }
    private fun showMentions() {
        cancelAuth()
        saveCurrentDraft(); cancelPageRequest(); mentionsView = true; mentionPage = 1; render(); fetchMentions()
    }
    private fun fetchMentions(more: Boolean = false) {
        val account = state.account ?: run { input.error = getString(R.string.community_login_required); return }
        val page = if (more) mentionPage + 1 else 1
        val cached = state.mentions.items.toList()
        request({ if (more) client.mentions(account, page) else client.mentionWindow(account, cached) }) { response ->
            val items = response.getJSONArray("items").let { a -> (0 until a.length()).map { a.getJSONObject(it) } }
            state.mentions.replace(if (more) state.mentions.items + items else items, keepUnread = true)
            mentionPage = response.getInt("page"); mentionMore = response.getBoolean("hasMore"); persist(); render(true)
        }
    }
    private fun renderMentions() {
        val visible = state.mentions.items.filterNot { it.optString("author_handle") in state.blocked }
        displayedMentions = visible.map { it.optString("id") }.toSet()
        displayed.clear(); unread.text = ""; unread.visibility = View.GONE; records.removeAllViews()
        output.text = if (visible.isEmpty()) getString(R.string.community_mentions_empty) else getString(R.string.community_mentions)
        for (mention in visible) records.addView(button(
            "#" + channelLabel(mention.optString("channel")) + " · " + CommunityMessagePresentation.header(mention),
            "community_mention_${mention.optString("id")}") { openMention(mention) }, LinearLayout.LayoutParams(-1, -2))
        if (mentionMore && state.mentions.items.size < 200) records.addView(button(R.string.community_more, "community_mentions_more") { fetchMentions(true) })
    }
    private fun openMention(mention: JSONObject) {
        val account = state.account ?: return
        if (mention.optString("author_handle") in state.blocked) return
        val collection = when (mention.optString("kind")) { "message" -> "messages"; "thread" -> "threads"; "thread_reply" -> "thread_replies"; else -> return }
        request({
            val source = client.source(account, collection, mention.getString("source_id"))
            source to if (collection == "thread_replies") client.source(account, "threads", source.getString("thread")) else source
        }, failed = { error ->
            if (error is CommunityClient.Failure && error.status == 404) {
                state.mentions.replace(state.mentions.items.filterNot { it.optString("id") == mention.optString("id") }); persist(); render()
                status.setText(R.string.community_source_gone)
            }
        }) { (source, parent) ->
            if (collection == "messages") {
                saveCurrentDraft(); mentionsView = false
                pane.channel = "general"; pane.thread = ""; pane.composing = false
                state.focusMessage(source); restoreDraft(); render(true)
                scroll.post { displayed[source.getString("id")]?.let { range -> output.layout?.let { layout -> scroll.scrollTo(0, output.top + layout.getLineTop(layout.getLineForOffset(range.first))); markDisplayed() } } }
            } else {
                val channel = parent.getString("channel")
                if (channel !in CommunityPaneState.CHANNELS || channel == "general") return@request
                openThread(parent.getString("id"), if (collection == "thread_replies") source else null, channel, parent)
            }
            persist()
        }
    }
    private fun persist(): Boolean = try {
        store.save(state.toJson()); storageReady = true; true
    } catch (_: Exception) { storageReady = false; status.setText(R.string.community_storage_error); false }
    private fun appendBody(text: android.text.SpannableStringBuilder, body: String) {
        val start = text.length; text.append(body)
        val handle = state.account?.handle ?: return
        for (range in CommunityMentionInput.ranges(body, handle)) {
            text.setSpan(android.text.style.StyleSpan(Typeface.BOLD), start + range.first, start + range.last + 1, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            text.setSpan(android.text.style.ForegroundColorSpan(ThemeColorResolver.color(Theme.input_text_color)), start + range.first, start + range.last + 1, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }
    private fun appendMessageHeader(text: android.text.SpannableStringBuilder, record: JSONObject, grouped: Boolean = false) {
        val start = text.length
        val author = CommunityMessagePresentation.header(record).substringBefore(" · ")
        text.append(if (grouped) "  " else author + " · ").append(CommunityMessagePresentation.time(record)).append("\n")
        if (!grouped) text.setSpan(android.text.style.StyleSpan(Typeface.BOLD), start, start + author.length, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        val metadataStart = if (grouped) start else start + author.length
        text.setSpan(android.text.style.RelativeSizeSpan(.85f), metadataStart, text.length, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        text.setSpan(android.text.style.ForegroundColorSpan(ThemeColorResolver.color(Theme.workspace_control_text_color)), metadataStart, text.length, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    }
    private fun atBottom(): Boolean = scroll.getChildAt(0)?.let { it.height - scroll.scrollY - scroll.height <= dp(24) } ?: true
    private fun rememberViewport() {
        if (!::scroll.isInitialized || restoringViewport || mentionsView || pane.composing || (pane.channel != "general" && pane.thread.isEmpty())) return
        if (pane.channel == "general") state.following = !state.readingOlder && atBottom()
        else pane.replyFollowing = !pane.replyHistory && atBottom()
        val layout = output.layout ?: return
        val anchor = displayed.entries.firstOrNull { (_, range) ->
            output.top + layout.getLineBottom(layout.getLineForOffset(range.last.coerceAtMost(output.length() - 1))) > scroll.scrollY
        } ?: return
        val offset = scroll.scrollY - output.top - layout.getLineTop(layout.getLineForOffset(anchor.value.first))
        if (pane.channel == "general") { state.viewAnchor = anchor.key; state.viewOffset = offset }
        else { pane.replyAnchor = anchor.key; pane.replyOffset = offset }
        if (storageReady) { ui.removeCallbacks(saveDraft); ui.postDelayed(saveDraft, 250) }
    }
    private fun positionViewport(latest: Boolean, fallback: Int) {
        restoringViewport = true
        val epoch = ++renderGeneration
        androidx.core.view.OneShotPreDrawListener.add(output) {
            if (epoch != renderGeneration || isFinishing || isDestroyed) return@add
            val anchor = displayed[if (pane.channel == "general") state.viewAnchor else pane.replyAnchor]
            val offset = if (pane.channel == "general") state.viewOffset else pane.replyOffset
            val y = if (latest) scroll.getChildAt(0).height else if (anchor != null && output.layout != null)
                output.top + output.layout.getLineTop(output.layout.getLineForOffset(anchor.first)) + offset else fallback
            scroll.scrollTo(0, y.coerceAtLeast(0))
            restoringViewport = false; rememberViewport(); markDisplayed()
        }
    }
    private fun updateConnection() {
        val connection = root.findViewById<TextView>(R.id.community_connection)
        connection.text = state.account?.let { account -> getString(when {
            liveConnected -> R.string.community_connection_live
            busy && refreshing -> R.string.community_connection_syncing
            readCurrent -> R.string.community_connection_ready
            else -> R.string.community_connection_offline
        }, account.handle) } ?: getString(R.string.community_connection_signed_out)
    }
    private fun render(current: Boolean = false, latestViewport: Boolean = false) {
        renderGeneration++; restoringViewport = false
        state.expire()
        if (visible) { ui.removeCallbacks(expireCache); ui.postDelayed(expireCache, minOf(state.expiryDelay(), state.mentions.expiryDelay())) }
        window.decorView.findViewWithTag<TextView>("community_header").apply {
            text = getString(R.string.community_window_title)
            contentDescription = getString(R.string.community_title) + (state.account?.let { " · ${it.handle}" } ?: "")
        }
        status.text = state.account?.let { getString(if (current && liveConnected) R.string.community_live else if (current) R.string.community_current else R.string.community_stale, it.handle) }
            ?: getString(R.string.community_signed_out)
        root.findViewById<TextView>(R.id.community_room).apply {
            text = when {
                mentionsView -> getString(R.string.community_mentions)
                pane.channel == "general" -> getString(R.string.community_general_context)
                pane.composing -> getString(R.string.community_thread_context, channelLabel(pane.channel), getString(R.string.community_new_thread))
                pane.thread.isNotEmpty() -> getString(R.string.community_thread_context, channelLabel(pane.channel), threadRoot?.optString("title") ?: getString(R.string.community_thread_title))
                else -> getString(R.string.community_forum_context, channelLabel(pane.channel))
            }
            setTypeface(Tuils.getTypeface(this@CommunityActivity), Typeface.BOLD)
        }
        updateConnection()
        controls()
        if (mentionsView) { renderMentions(); return }
        if (pane.channel != "general") { renderForum(latestViewport || (pane.replyFollowing && !pane.replyHistory)); return }
        records.removeAllViews()
        val y = scroll.scrollY
        val text = android.text.SpannableStringBuilder(); displayed.clear()
        var divider = false
        var previous: JSONObject? = null
        var groupSize = 0
        for (message in state.messages.filterNot { it.optString("author_handle") in state.blocked }) {
            val id = message.getString("id")
            val day = CommunityMessagePresentation.day(message)
            if (day.isNotEmpty() && (previous == null || CommunityMessagePresentation.day(previous!!) != day)) {
                text.append("— ").append(day).append(" —\n\n")
            }
            val firstUnread = !divider && id !in state.seen
            if (firstUnread) { text.append(getString(R.string.community_unread_divider)).append("\n"); divider = true }
            val start = text.length
            val grouped = !firstUnread && groupSize < 4 && CommunityMessagePresentation.grouped(previous, message)
            appendMessageHeader(text, message, grouped)
            groupSize = if (grouped) groupSize + 1 else 1
            appendBody(text, message.optString("body")); text.append("\n\n")
            displayed[id] = start until text.length
            previous = message
        }
        if (state.pendingId != null && state.messages.none { it.optString("client_id") == state.pendingId }) appendPending(text, state.pendingBody)
        output.text = if (state.account == null) getString(R.string.community_welcome) else if (text.isEmpty()) getString(R.string.community_empty) else text
        positionViewport(state.account != null && (latestViewport || (state.following && !state.readingOlder)), y)
    }
    private fun markDisplayed() {
        if (!visible || isFinishing || mentionsView) return
        val layout = output.layout ?: return
        var changed = false
        for ((id, range) in displayed) {
            val top = output.top + layout.getLineTop(layout.getLineForOffset(range.first))
            val bottom = output.top + layout.getLineBottom(layout.getLineForOffset(range.last.coerceAtMost(output.length() - 1)))
            if (top >= scroll.scrollY && bottom <= scroll.scrollY + scroll.height) {
                if (pane.channel == "general") changed = state.seen.add(id) || changed
                for (mention in state.mentions.items.filter { mention -> mention.optString("source_id") == id }) {
                    changed = state.mentions.read(mention.optString("id")) || changed
                }
            }
        }
        val count = if (pane.channel == "general") (displayed.filter { (id, range) ->
            id !in state.seen && output.top + layout.getLineBottom(layout.getLineForOffset(range.last.coerceAtMost(output.length() - 1))) > scroll.scrollY + scroll.height
        }.keys + state.newArrivalIds.filter { it !in displayed }).size else displayed.values.count { range ->
            output.top + layout.getLineBottom(layout.getLineForOffset(range.last.coerceAtMost(output.length() - 1))) > scroll.scrollY + scroll.height
        }
        unread.text = if (count == 0) "" else getString(if (pane.channel == "general") R.string.community_new_messages else R.string.community_messages_below, count)
        unread.visibility = if (count == 0) View.GONE else View.VISIBLE
        if (changed && storageReady) { controls(); ui.removeCallbacks(saveDraft); ui.postDelayed(saveDraft, 250) }
    }
    private fun <T> request(task: () -> T, background: Boolean = false, pendingLabel: Int? = null, failed: (Throwable) -> Unit = {}, complete: (T) -> Unit) {
        if (!background && busy && refreshing) cancelPageRequest()
        if (busy || !visible) { input.error = getString(R.string.community_busy); return }
        busy = true; refreshing = background; this.pendingLabel = pendingLabel; ui.removeCallbacks(poll); updateConnection()
        val epoch = generation
        job = worker.submit {
            val result = runCatching { client.operation(task) }
            ui.post {
                if (!visible || epoch != generation || isFinishing || isDestroyed) return@post
                busy = false; refreshing = false; this.pendingLabel = null
                result.fold({ value -> if (background) readCurrent = true; pollDelay = if (liveConnected) 60_000L else 15_000L; complete(value) }) { error ->
                    if (background) readCurrent = false
                    pollDelay = if (error is CommunityClient.Failure && error.status == 429) error.retrySeconds * 1000 else (pollDelay * 2).coerceAtMost(300_000L)
                    if (error is CommunityClient.Failure && error.status in setOf(401, 403) && state.account != null) logout(true)
                    status.text = when (error) {
                        is CommunityClient.Uncertain -> getString(R.string.community_uncertain)
                        is CommunityClient.Failure -> when (error.status) {
                            429 -> getString(R.string.community_rate_error, error.retrySeconds)
                            401, 403 -> getString(R.string.community_session_error)
                            400 -> if (error.message == "Handle is already taken.") getString(R.string.community_duplicate_username) else getString(R.string.community_request_error, error.status)
                            428 -> getString(R.string.community_rules_required)
                            else -> getString(R.string.community_request_error, error.status)
                        }
                        else -> getString(R.string.community_network_error)
                    }
                    if (error !is CommunityClient.Failure || error.status !in setOf(401, 403)) failed(error)
                    if (error is CommunityClient.Failure && error.status == 428) {
                        state.account?.let { state.signIn(it.copy(policyVersion = "")); persist() }; showRules()
                    }
                    if (auth.active) { authError = status.text.toString(); auth.restart(); restoreDraft(); configureAuthInput() }
                }
                updateConnection(); controls()
                if (visible && !busy && state.account != null) ui.postDelayed(poll, pollDelay)
                if (visible && !busy && (liveInvalidated || liveChanges.isNotEmpty())) { ui.removeCallbacks(receiveLive); ui.post(receiveLive) }
            }
        }
    }
    private fun login(registering: Boolean = false) {
        if (busy || state.account != null) return
        if (auth.active) cancelAuth()
        saveCurrentDraft(); ui.removeCallbacks(saveDraft)
        cancelPageRequest()
        secureBeforeAuth = window.attributes.flags and android.view.WindowManager.LayoutParams.FLAG_SECURE != 0
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
        auth.begin(registering); authError = ""; configureAuthInput(); input.requestFocus()
    }
    private fun configureAuthInput() {
        switching = true
        input.text?.clear()
        input.inputType = if (auth.masked) android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD or android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            else android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        input.transformationMethod = if (auth.masked) android.text.method.PasswordTransformationMethod.getInstance() else null
        input.maxLines = 1
        input.typeface = Tuils.getTypeface(this)
        input.imeOptions = EditorInfo.IME_ACTION_SEND or EditorInfo.IME_FLAG_NO_EXTRACT_UI or EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
        input.isSaveEnabled = false
        if (android.os.Build.VERSION.SDK_INT >= 26) input.importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
        switching = false
        root.findViewById<LinearLayout>(R.id.community_completions).removeAllViews()
        root.findViewById<View>(R.id.community_completion_scroll).visibility = View.INVISIBLE
        controls()
    }
    private fun updateAuthGuidance() {
        root.findViewById<View>(R.id.community_guidance_row).visibility =
            if (auth.active || (!mentionsView && pane.composing)) View.VISIBLE else View.GONE
        root.findViewById<TextView>(R.id.community_thread_summary).apply {
            visibility = if (!auth.active && !mentionsView && pane.composing) View.VISIBLE else View.INVISIBLE
            text = if (editingTitle) getString(R.string.community_title_guidance) else pane.draft(pane.context).optString("title")
            contentDescription = if (editingTitle) text else getString(R.string.community_edit_title_summary, text)
            isClickable = !editingTitle && visibility == View.VISIBLE
            isFocusable = isClickable
        }
        val guidance = root.findViewById<TextView>(R.id.community_auth_guidance)
        guidance.visibility = if (auth.active) View.VISIBLE else View.INVISIBLE
        guidance.text = if (!auth.active) "" else {
            val stage = getString(when (auth.stage) {
                CommunityAuthInput.Stage.USERNAME -> R.string.community_username
                CommunityAuthInput.Stage.PASSWORD -> R.string.community_password
                CommunityAuthInput.Stage.CONFIRMATION -> R.string.community_confirm_password
                else -> R.string.community_busy
            })
            getString(if (auth.registering) R.string.community_register else R.string.community_login) + " · " + stage +
                "\n" + if (authError.isNotEmpty()) authError else when (auth.stage) {
                    CommunityAuthInput.Stage.USERNAME -> getString(R.string.community_auth_username_guidance)
                    CommunityAuthInput.Stage.PASSWORD, CommunityAuthInput.Stage.CONFIRMATION -> getString(R.string.community_auth_password_guidance)
                    else -> ""
                }
        }
        val cancel = root.findViewById<Button>(R.id.community_auth_cancel)
        cancel.visibility = if (auth.active) View.VISIBLE else View.INVISIBLE
        cancel.isEnabled = auth.active; cancel.setOnClickListener { cancelAuth() }
    }
    private fun cancelAuth() {
        if (!auth.active || !::input.isInitialized) return
        generation++; job?.cancel(true); job = null; client.cancel(); busy = false; pendingLabel = null
        // Clear under the auth guard before the ordinary draft watcher is re-enabled.
        input.text?.clear(); auth.cancel(); authError = ""
        if (!secureBeforeAuth) window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
        switching = true
        input.inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE or android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        input.transformationMethod = null
        input.maxLines = 3; input.typeface = Tuils.getTypeface(this)
        input.imeOptions = EditorInfo.IME_ACTION_SEND or EditorInfo.IME_FLAG_NO_EXTRACT_UI
        switching = false
        restoreDraft(); controls()
    }
    private fun submitAuth() {
        if (busy || auth.stage == CommunityAuthInput.Stage.SUBMITTING) return
        if (auth.stage == CommunityAuthInput.Stage.USERNAME && input.text.toString() == "/cancel") { cancelAuth(); return }
        val previous = auth.stage
        val credentials = auth.submit(input.text.toString())
        authError = when (auth.error) {
            CommunityAuthInput.Error.USERNAME -> getString(R.string.community_auth_username_error)
            CommunityAuthInput.Error.PASSWORD -> getString(R.string.community_auth_password_error)
            CommunityAuthInput.Error.CONFIRMATION -> getString(R.string.community_auth_confirmation_error)
            else -> ""
        }
        if (previous != auth.stage || auth.masked) configureAuthInput() else updateAuthGuidance()
        if (credentials == null) return
        request({ if (credentials.registering) client.register(credentials.username, credentials.password, credentials.password)
            else client.login(credentials.username, credentials.password) }) { account ->
            cancelAuth(); stopForeground()
            // Preserve the draft/context that surrounded inline onboarding.
            val draft = state.draft; val context = pane.context; val channel = pane.channel; val thread = pane.thread; val composing = pane.composing
            val body = pane.draft(context).optString("body"); val title = pane.draft(context).optString("title")
            val titleStep = pane.editingTitle
            state.signIn(account); state.draft = draft
            pane.channel = channel; pane.thread = thread; pane.composing = composing
            if (body.isNotEmpty() || title.isNotEmpty()) pane.saveDraft(context, title, body)
            if (composing) pane.editingTitle = titleStep
            restoreDraft()
            if (persist()) { render(); refresh(); startForeground() }
        }
    }
    private fun leave() {
        saveCurrentDraft()
        if (state.draft.isEmpty() && state.pendingId == null && pane.toJson().getJSONObject("drafts").length() == 0 && pane.pendingId == null) { finish(); return }
        leaveDialog = android.app.AlertDialog.Builder(this).setTitle(R.string.community_leave_title)
            .setMessage(R.string.community_leave_body)
            .setPositiveButton(R.string.community_keep) { _, _ -> if (persist()) finish() }
            .setNegativeButton(R.string.community_discard) { _, _ ->
                generation++; job?.cancel(true); client.cancel(); state.completePost(); state.draft = ""; state.pane = CommunityPaneState(JSONObject()); restoreDraft()
                if (persist()) finish()
            }.setNeutralButton(R.string.community_cancel, null).show()
    }
    private fun logout(sessionExpired: Boolean = false) {
        cancelAuth()
        stopForeground(); generation++; job?.cancel(true); client.cancel(); busy = false; pendingLabel = null; ui.removeCallbacks(poll)
        state.clear(); threads = emptyList(); replies = emptyList(); threadRoot = null; forumCursor = null; mentionsView = false
        restoreDraft(); ui.removeCallbacks(saveDraft)
        try { store.clear(); storageReady = true } catch (_: Exception) { storageReady = false }
        render(); status.setText(if (!storageReady) R.string.community_storage_error else if (sessionExpired) R.string.community_session_error else R.string.community_logout)
    }
    private fun refresh(older: Boolean = false, returnLatest: Boolean = false) {
        if (mentionsView) { fetchMentions(); return }
        if (pane.channel != "general") { refreshForum(older, returnLatest); return }
        val account = state.account ?: run { input.error = getString(R.string.community_login_required); return }
        if (state.expire()) { persist(); render() }
        val cursor = if (older) state.messages.firstOrNull() else null
        val ids = if (older) emptySet() else state.messages.map { it.getString("id") }.toSet()
        request({
            val history = client.history(account, cursor)
            history to client.checked(account, ids.toList())
        }, background = true) { (history, checked) ->
            if (!older) state.observeLatest(history)
            state.merge(history + checked, ids, returnLatest); persist(); render(true, returnLatest)
        }
    }
    private fun submit() {
        input.error = null
        if (auth.active) { submitAuth(); return }
        val text = input.text.toString()
        if (editingTitle && (!text.startsWith("/") || text.startsWith("//"))) {
            val title = if (text.startsWith("//")) text.drop(1) else text
            if (title.isBlank() || title.contains('\n') || title.contains('\r') || title.codePointCount(0, title.length) > 120 || title.toByteArray(Charsets.UTF_8).size > 480) {
                input.error = getString(R.string.community_title_error); return
            }
            pane.saveDraft(pane.context, title, pane.draft(pane.context).optString("body"))
            pane.editingTitle = false; restoreDraft(); controls(); persist(); input.requestFocus(); return
        }
        when (val result = CommunityInput.parse(text)) {
            is CommunityInput.Rejected -> input.error = getString(R.string.community_invalid)
            is CommunityInput.Command -> when (result.name) {
                "login", "register" -> { if (busy) { input.error = getString(R.string.community_busy); return }; restoreDraft(); login(result.name == "register") }
                "help" -> { restoreDraft(); displayed.clear(); unread.text = ""; output.setText(R.string.community_help) }
                "refresh", "older" -> { restoreDraft(); refresh(result.name == "older", result.name == "refresh") }
                "logout" -> logout()
                "block" -> {
                    if (state.account == null) { input.error = getString(R.string.community_login_required); return }
                    if (!result.argument.matches(Regex("[-_a-zA-Z0-9]{3,24}"))) { input.error = getString(R.string.community_invalid); return }
                    val handle = result.argument.lowercase(java.util.Locale.ROOT)
                    if (!state.blocked.add(handle)) state.blocked.remove(handle)
                    restoreDraft(); persist(); render(); status.text = getString(R.string.community_blocked, handle)
                }
                "report" -> {
                    val account = state.account ?: run { input.error = getString(R.string.community_login_required); return }
                    if (!ensurePolicy()) return
                    if (!result.argument.matches(Regex("[a-zA-Z0-9]{15}"))) { input.error = getString(R.string.community_invalid); return }
                    restoreDraft()
                    request({ client.report(account, result.argument) }) { status.setText(R.string.community_reported) }
                }
                "moderate" -> { sendPrivate(result.argument); restoreDraft() }
                "exit" -> leave()
                else -> input.error = getString(R.string.community_invalid)
            }
            is CommunityInput.Message -> {
                if (pane.channel != "general") { submitForum(result.body); return }
                sendGeneral(result.body)
            }
        }
    }
    private fun sendGeneral(nextBody: String) {
        val account = state.account ?: run { input.error = getString(R.string.community_login_required); return }
        if (!ensurePolicy()) return
        if (!preparePost()) return
        val uncertainAttempt = state.pendingId != null
        val body = if (uncertainAttempt) state.pendingBody else nextBody
        val id = state.beginPost(body)
        saveCurrentDraft()
        if (!storageReady || !persist()) return
        status.setText(R.string.community_pending)
        request({ client.post(account, body, id, uncertainAttempt) }, pendingLabel = if (uncertainAttempt) R.string.community_checking_delivery else R.string.community_sending, failed = { error ->
            if (error is CommunityClient.Failure && error.status in setOf(400, 428, 429) && !uncertainAttempt) { state.rejectPost(); persist() }
            redrawPending()
        }) { message ->
            val returnFromHistory = state.readingOlder
            state.observeRealtime(message)
            state.merge(listOf(message), emptySet(), true)
            state.seen.add(message.getString("id")); state.completePost()
            if ((CommunityInput.parse(input.text.toString()) as? CommunityInput.Message)?.body == body) input.setText("")
            persist(); render(true, true)
            status.setText(R.string.community_posted)
            if (returnFromHistory) { liveInvalidated = true; liveLatestViewport = true }
        }
        redrawPending(true)
    }
}
