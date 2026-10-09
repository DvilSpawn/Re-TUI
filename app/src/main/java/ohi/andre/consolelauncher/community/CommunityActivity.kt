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
import ohi.andre.consolelauncher.managers.xml.options.Suggestions
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
    private val pane get() = state.pane
    private lateinit var root: View
    private lateinit var windowPanel: HelpWindowPanel
    private lateinit var titleInput: EditText
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
    override fun onCreate(saved: Bundle?) {
        LauncherSystemUi.requestNoTitleIfFullscreen(this)
        super.onCreate(saved)
        val backend = if (ohi.andre.consolelauncher.BuildConfig.DEBUG) debugBackend ?: CommunityClient.PRODUCTION else CommunityClient.PRODUCTION
        client = CommunityClient(backend)
        presenceClient = CommunityClient(backend); lookupClient = CommunityClient(backend); notificationClient = CommunityClient(backend)
        store = CommunityStore(this)
        try { state = CommunityState(store.load()) } catch (_: Exception) { storageReady = false }
        Tuils.init(this); XMLPrefsManager.loadCommons(this); LauncherSystemUi.applyFullscreen(this)
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        val screen = FrameLayout(this)
        val host = TuixtLayout.addFoldAwareHost(this, screen, ViewGroup.LayoutParams.MATCH_PARENT)
        val viewport = FrameLayout(this); host.addView(viewport, ViewGroup.LayoutParams(-1, -1))
        root = layoutInflater.inflate(R.layout.community_surface, viewport, false)
        windowPanel = HelpWindowPanel(viewport, root, saved, "community",
            root.findViewById(R.id.community_header), root.findViewById(R.id.community_header_row))
        status = root.findViewById(R.id.community_status)
        unread = root.findViewById(R.id.community_unread)
        output = root.findViewById(R.id.community_output)
        scroll = root.findViewById(R.id.community_scroll)
        records = root.findViewById(R.id.community_records)
        titleInput = root.findViewById(R.id.community_thread_title)
        input = root.findViewById(R.id.community_input)
        input.selectionChanged = { scheduleLookup() }
        input.setOnKeyListener { _, key, event ->
            if (key == KeyEvent.KEYCODE_ENTER && !event.isShiftPressed) {
                if (event.action == KeyEvent.ACTION_UP) submit()
                true
            } else false
        }
        input.setOnEditorActionListener { _, action, _ -> if (action == EditorInfo.IME_ACTION_SEND) { submit(); true } else false }
        scroll.setOnScrollChangeListener { _, _, _, _, _ -> markDisplayed() }
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
        input.isSaveEnabled = false; titleInput.isSaveEnabled = false
        input.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (switching || auth.active) return
                saveCurrentDraft(); scheduleLookup()
                ui.removeCallbacks(saveDraft); ui.postDelayed(saveDraft, 250)
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })
        titleInput.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (!switching && !auth.active) { saveCurrentDraft(); ui.removeCallbacks(saveDraft); ui.postDelayed(saveDraft, 250) }
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
        if (state.account != null && storageReady) { refresh(); startForeground() }
    }
    override fun onPause() {
        cancelAuth()
        visible = false; stopForeground(); generation++; ui.removeCallbacks(poll); ui.removeCallbacks(expireCache); ui.removeCallbacks(saveDraft)
        job?.cancel(true); client.cancel(); busy = false
        if (storageReady) persist()
        super.onPause()
    }
    override fun onDestroy() {
        cancelAuth(); stopForeground(); ui.removeCallbacksAndMessages(null); worker.shutdownNow(); client.cancel()
        presenceWorker.shutdownNow(); lookupWorker.shutdownNow(); notificationWorker.shutdownNow()
        super.onDestroy()
    }
    private fun startForeground() {
        if (!visible || state.account == null || !storageReady) return
        renewPresence(); pollMentions(); scheduleLookup()
    }
    private fun stopForeground() {
        foregroundGeneration++; lookupGeneration++; lookupKey = ""
        ui.removeCallbacks(presencePoll); ui.removeCallbacks(notificationPoll); ui.removeCallbacks(mentionLookup)
        presenceJob?.cancel(true); presenceClient.cancel()
        lookupJob?.cancel(true); lookupClient.cancel()
        notificationJob?.cancel(true); notificationClient.cancel()
        if (::root.isInitialized) root.findViewById<View>(R.id.community_completion_scroll).visibility = View.INVISIBLE
        // The server lease expires within 90 seconds. Never chain a release after cancellation.
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
                if (error is CommunityClient.Failure && error.status in setOf(401, 403)) { logout(); return@post }
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
                if (error is CommunityClient.Failure && error.status in setOf(401, 403)) { logout(); return@post }
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
        if (!visible || state.account == null || input.selectionStart != input.selectionEnd || mentionsView || (pane.channel != "general" && !pane.composing && pane.thread.isEmpty())) return
        if (CommunityMentionInput.fragment(input.text.toString(), input.selectionStart) != null) ui.postDelayed(mentionLookup, 250)
    }
    private fun lookupUsers(page: Int = 1) {
        val account = state.account ?: return
        if (!visible || !storageReady || auth.active || input.selectionStart != input.selectionEnd) return
        val text = input.text.toString(); val caret = input.selectionStart
        val fragment = CommunityMentionInput.fragment(text, caret) ?: return
        val epoch = foregroundGeneration; val query = lookupGeneration
        lookupJob = lookupWorker.submit {
            val result = runCatching { lookupClient.operation { lookupClient.users(account, fragment, page) } }
            ui.post {
                if (!visible || epoch != foregroundGeneration || query != lookupGeneration || state.account?.id != account.id || input.text.toString() != text || input.selectionStart != caret || input.selectionEnd != caret) return@post
                val error = result.exceptionOrNull()
                if (error is CommunityClient.Failure && error.status in setOf(401, 403)) { logout(); return@post }
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
    private fun suggestion(label: String, tagName: String, command: Boolean, action: () -> Unit): TextView = TextView(this).apply {
        tag = tagName; text = label; typeface = Tuils.getTypeface(this@CommunityActivity)
        textSize = XMLPrefsManager.getInt(Suggestions.suggestions_size).toFloat()
        setSingleLine(); gravity = android.view.Gravity.CENTER; minWidth = dp(48); minHeight = dp(48)
        setPadding(dp(12), 0, dp(12), 0)
        val foreground = XMLPrefsManager.getColor(if (command) Suggestions.cmd_text_color else Suggestions.contact_text_color)
        setTextColor(if (foreground == Int.MAX_VALUE) XMLPrefsManager.getColor(Suggestions.default_text_color) else foreground)
        ohi.andre.consolelauncher.managers.settings.TextShadows.apply(this, Theme.suggestions_text_shadow_color)
        val color = XMLPrefsManager.getColor(if (command) Suggestions.cmd_background_color else Suggestions.contact_background_color)
        background = if (XMLPrefsManager.getBoolean(Suggestions.transparent_suggestions)) android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT)
            else if (AppearanceSettings.cyberdeckMode() || TerminalBorderRuntime.customFrameActive(this@CommunityActivity, FrameTarget.SUGGESTIONS))
                TerminalBorderRuntime.panelDrawable(this@CommunityActivity, color, AppearanceSettings.terminalBorderColor(), 1f, 0, true, target = FrameTarget.SUGGESTIONS)
            else android.graphics.drawable.GradientDrawable().apply { cornerRadius = dp(AppearanceSettings.moduleCornerRadius()).toFloat(); setColor(color) }
        layoutParams = LinearLayout.LayoutParams(-2, -1).apply { setMargins(dp(4), 0, dp(4), 0) }
        isClickable = true; isFocusable = true; setOnClickListener { action() }
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
        // Reserve identical dock and status rows across contexts. Only typography changes their budget.
        fun reserve(view: View, height: Int) { view.layoutParams = view.layoutParams.apply { this.height = height } }
        val line = kotlin.math.ceil(input.paint.fontSpacing.toDouble()).toInt()
        reserve(root.findViewById(R.id.community_prompt_row), maxOf(dp(48), line * 3 + dp(8)))
        reserve(root.findViewById(R.id.community_guidance_row), maxOf(dp(48), line * 2 + dp(4)))
        val suggestionPaint = android.text.TextPaint(input.paint).apply {
            textSize = android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_SP,
                XMLPrefsManager.getInt(Suggestions.suggestions_size).toFloat(), resources.displayMetrics)
        }
        val suggestionMetrics = suggestionPaint.fontMetricsInt
        reserve(root.findViewById(R.id.community_completion_scroll), maxOf(dp(48), suggestionMetrics.bottom - suggestionMetrics.top + dp(12)))
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
    }
    private fun saveCurrentDraft() {
        if (auth.active) return
        val text = input.text.toString()
        if (text.startsWith("/") && !text.startsWith("//")) return
        if (pane.channel == "general") state.draft = text
        else if (pane.composing || pane.thread.isNotEmpty()) pane.saveDraft(pane.context, if (pane.composing) titleInput.text.toString() else "", text)
    }
    private fun restoreDraft() {
        if (auth.active) return
        switching = true
        input.setText(if (pane.channel == "general") state.draft else pane.draft(pane.context).optString("body"))
        titleInput.setText(pane.draft(pane.context).optString("title")); input.setSelection(input.length())
        switching = false; scheduleLookup()
    }
    private fun cancelPageRequest() {
        generation++; job?.cancel(true); client.cancel(); busy = false; ui.removeCallbacks(poll)
        lookupGeneration++; lookupKey = ""; ui.removeCallbacks(mentionLookup); lookupJob?.cancel(true); lookupClient.cancel()
        root.findViewById<View>(R.id.community_completion_scroll).visibility = View.INVISIBLE
    }
    private fun selectChannel(channel: String) {
        cancelAuth()
        saveCurrentDraft(); cancelPageRequest(); mentionsView = false
        pane.channel = channel; pane.thread = ""; pane.composing = false
        threads = emptyList(); replies = emptyList(); threadRoot = null; forumCursor = null; pane.history = false
        restoreDraft(); persist(); render(); if (state.account != null) refresh()
    }
    private fun openThread(id: String, focus: JSONObject? = null, channel: String = pane.channel, loadedRoot: JSONObject? = null) {
        cancelAuth()
        saveCurrentDraft(); cancelPageRequest(); mentionsView = false
        pane.channel = channel; pane.thread = id; pane.composing = false; replies = emptyList(); threadRoot = null; forumCursor = null
        if (focus != null) pane.focusReply(focus)
        threadRoot = loadedRoot
        restoreDraft(); persist(); render(); if (loadedRoot == null) refresh()
    }
    private fun back() {
        if (auth.active) { cancelAuth(); return }
        if (mentionsView) { mentionsView = false; render(); return }
        if (pane.thread.isNotEmpty() || pane.composing) {
            saveCurrentDraft(); cancelPageRequest(); pane.thread = ""; pane.composing = false
            replies = emptyList(); threadRoot = null
            restoreDraft(); persist(); render(); if (state.account != null) refresh()
        } else leave()
    }
    private fun controls() {
        val rows = root.findViewById<LinearLayout>(R.id.community_channel_rows); rows.removeAllViews()
        for (channel in CommunityPaneState.CHANNELS) {
            val count = state.mentions.unread(channel)
            rows.addView(button((if (channel == pane.channel) "> " else "") + channelLabel(channel) + if (count > 0) " @$count" else "", "community_channel_$channel") { selectChannel(channel) }, LinearLayout.LayoutParams(-1, -2))
        }
        val actions = root.findViewById<LinearLayout>(R.id.community_actions); actions.removeAllViews()
        val mentionCount = CommunityPaneState.CHANNELS.sumOf { state.mentions.unread(it) }
        val badge = if (mentionCount > 0) " @$mentionCount" else ""
        actions.addView(button(getString(R.string.community_channels) + badge, "community_channels") {
            android.app.AlertDialog.Builder(this).setTitle(R.string.community_channels)
                .setItems(CommunityPaneState.CHANNELS.map { channelLabel(it) + state.mentions.unread(it).let { n -> if (n > 0) " @$n" else "" } }.toTypedArray()) { _, i -> selectChannel(CommunityPaneState.CHANNELS[i]) }.show()
        })
        if (pane.thread.isNotEmpty() || pane.composing || mentionsView) actions.addView(button(R.string.community_back, "community_back") { back() })
        if (pane.channel != "general" && pane.thread.isEmpty() && !pane.composing && !mentionsView) actions.addView(button(R.string.community_new_thread, "community_new_thread") {
            cancelAuth()
            saveCurrentDraft(); pane.composing = true; restoreDraft(); render(); input.requestFocus(); persist()
        })
        actions.addView(button(R.string.community_refresh, "community_latest") { refresh(returnLatest = true) })
        if (!pane.composing && !mentionsView) actions.addView(button(if (pane.thread.isEmpty()) R.string.community_older else R.string.community_more, "community_older") { refresh(older = true) })
        actions.addView(button(getString(R.string.community_mentions) + badge, "community_mentions") { showMentions() })
        if (pane.pendingId != null) actions.addView(button(R.string.community_retry, "community_retry") { retryForum() })
        actions.addView(button(R.string.community_menu, "community_menu") { menu() })
        val editable = auth.active || (!mentionsView && (state.account == null || pane.channel == "general" || pane.composing || pane.thread.isNotEmpty()))
        val prompt = root.findViewById<View>(R.id.community_prompt_row)
        prompt.visibility = if (editable) View.VISIBLE else View.INVISIBLE
        prompt.importantForAccessibility = if (editable) View.IMPORTANT_FOR_ACCESSIBILITY_AUTO else View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        input.isEnabled = editable && auth.stage != CommunityAuthInput.Stage.SUBMITTING
        root.findViewById<View>(R.id.community_send).isEnabled = input.isEnabled
        if (!editable) { input.clearFocus(); titleInput.clearFocus(); (getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager).hideSoftInputFromWindow(input.windowToken, 0) }
        titleInput.visibility = if (!auth.active && !mentionsView && pane.composing) View.VISIBLE else View.INVISIBLE
        titleInput.isEnabled = titleInput.visibility == View.VISIBLE
        titleInput.importantForAccessibility = if (titleInput.isEnabled) View.IMPORTANT_FOR_ACCESSIBILITY_AUTO else View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        updateAuthGuidance()
        lookupKey = ""; scheduleLookup()
        adaptRail()
    }
    private fun menu() {
        val reportMessages = state.messages.toList()
        val labels = if (state.account == null) listOf(R.string.community_login, R.string.community_register, R.string.community_help) else
            listOf(R.string.community_report, R.string.community_moderate, R.string.community_logout, R.string.community_help)
        android.app.AlertDialog.Builder(this).setTitle(R.string.community_menu).setItems(labels.map { getString(it) }.toTypedArray()) { _, i ->
            when (labels[i]) {
                R.string.community_login -> login(); R.string.community_register -> login(true); R.string.community_logout -> logout()
                R.string.community_report -> android.app.AlertDialog.Builder(this).setTitle(R.string.community_report)
                    .setItems(reportMessages.map { it.optString("id") + " · " + it.optString("author_handle") }.toTypedArray()) { _, index ->
                        val account = state.account ?: return@setItems
                        val id = reportMessages.getOrNull(index)?.optString("id") ?: return@setItems
                        request({ client.report(account, id) }) { status.setText(R.string.community_reported) }
                    }.show()
                R.string.community_moderate -> privateRequest()
                else -> { displayed.clear(); records.removeAllViews(); output.setText(R.string.community_help) }
            }
        }.show()
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
        request({ client.moderate(account, body) }, failed = { status.setText(R.string.community_private_uncertain) }) { status.setText(R.string.community_private_sent) }
    }
    private fun renderForum() {
        displayed.clear(); unread.text = ""; records.removeAllViews()
        if (pane.composing) { output.text = getString(R.string.community_new_thread); return }
        if (pane.thread.isEmpty()) {
            output.text = if (threads.isEmpty()) getString(R.string.community_threads_empty) else ""
            for (thread in threads.filterNot { it.optString("author_handle") in state.blocked }) records.addView(button(
                thread.optString("title") + "\n@" + thread.optString("author_handle") + " · " + thread.optString("created"), "community_thread_${thread.optString("id")}") { openThread(thread.getString("id")) }, LinearLayout.LayoutParams(-1, -2))
        } else {
            val text = android.text.SpannableStringBuilder()
            fun appendRecord(record: JSONObject) {
                val start = text.length
                if (record.has("title")) text.append(record.optString("title")).append("\n")
                text.append("@${record.optString("author_handle")} ${record.optString("created")}\n")
                appendBody(text, record.optString("body")); text.append("\n\n")
                displayed[record.getString("id")] = start until text.length
            }
            threadRoot?.takeUnless { it.optString("author_handle") in state.blocked }?.let { appendRecord(it) }
            replies.filterNot { it.optString("author_handle") in state.blocked }.forEach { appendRecord(it) }
            output.text = text
            scroll.post { markDisplayed() }
        }
    }
    private fun refreshForum(older: Boolean, latest: Boolean) {
        val account = state.account ?: run { input.error = getString(R.string.community_login_required); return }
        val channel = pane.channel; val id = pane.thread
        if (pane.composing) return
        if (id.isEmpty()) {
            val ids = if (older) emptyList() else threads.map { it.getString("id") }
            request({ client.threads(account, channel, if (older) forumCursor else null) to client.checked(account, ids, "threads") }) { (page, checked) ->
                if (!older) threads = checked.filter { it.optString("channel") == channel }.sortedWith(compareByDescending<JSONObject> { it.getString("created") }.thenByDescending { it.getString("id") })
                pane.mergeThreads(page, older, latest)
                persist(); render(true); if (latest) scroll.post { scroll.scrollTo(0, 0) }
            }
        } else {
            val ids = if (older) emptyList() else replies.map { it.getString("id") }
            request({
                Triple(client.source(account, "threads", id), client.replies(account, id, if (older || !latest) forumCursor else null, latest), client.checked(account, ids, "thread_replies"))
            }, failed = { error ->
                if (error is CommunityClient.Failure && error.status == 404) { threadRoot = null; replies = emptyList(); persist(); render(); status.setText(R.string.community_source_gone) }
            }) { (thread, page, checked) ->
                threadRoot = thread; if (page.isNotEmpty()) forumCursor = page.last()
                if (latest) { replies = page; forumCursor = page.lastOrNull() }
                else replies = ((if (older) replies else checked.filter { it.optString("thread") == id }) + page)
                    .distinctBy { it.getString("id") }.sortedWith(compareBy({ it.getString("created") }, { it.getString("id") })).takeLast(200)
                persist(); render(true)
            }
        }
    }
    private fun submitForum(body: String) {
        val account = state.account ?: run { input.error = getString(R.string.community_login_required); return }
        if (busy) { input.error = getString(R.string.community_busy); return }
        val payload = if (pane.pendingId != null) {
            if (pane.pendingContext != pane.context) { input.error = getString(R.string.community_pending_locked); return }
            pane.pending
        } else JSONObject().put("body", body).apply {
            if (pane.thread.isEmpty()) { put("channel", pane.channel); put("title", titleInput.text.toString()) }
            else put("thread", pane.thread)
        }
        if (payload.has("title") && (payload.getString("title").isBlank() || payload.getString("title").codePointCount(0, payload.getString("title").length) > 120 || payload.getString("title").toByteArray(Charsets.UTF_8).size > 480)) {
            titleInput.error = getString(R.string.community_invalid); return
        }
        val uncertain = pane.pendingId != null; val context = pane.context
        val uuid = pane.begin(context, payload); saveCurrentDraft(); if (!storageReady || !persist()) return
        status.setText(R.string.community_pending)
        request({ if (payload.has("thread")) client.postReply(account, payload.getString("thread"), payload.getString("body"), uuid, uncertain)
            else client.postThread(account, payload.getString("channel"), payload.getString("title"), payload.getString("body"), uuid, uncertain)
        }, failed = { error -> if (error is CommunityClient.Failure && error.status in setOf(400, 429) && !uncertain) { pane.reject(); persist() } }) { record ->
            pane.complete(); restoreDraft(); persist()
            if (payload.has("thread")) { replies = (replies + record).distinctBy { it.getString("id") }.takeLast(200); render(true); status.setText(R.string.community_posted) }
            else { pane.composing = false; openThread(record.getString("id")) }
        }
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
                openThread(id, channel = channel)
            }
        } else if (context in CommunityPaneState.CHANNELS && context != "general") {
            selectChannel(context); pane.composing = true; restoreDraft(); render()
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
        displayedMentions = state.mentions.items.map { it.optString("id") }.toSet()
        displayed.clear(); unread.text = ""; records.removeAllViews()
        output.text = if (state.mentions.items.isEmpty()) getString(R.string.community_mentions_empty) else getString(R.string.community_mentions)
        for (mention in state.mentions.items) records.addView(button(
            channelLabel(mention.optString("channel")) + " · @" + mention.optString("author_handle") + " · " + mention.optString("created"),
            "community_mention_${mention.optString("id")}") { openMention(mention) }, LinearLayout.LayoutParams(-1, -2))
        if (mentionMore && state.mentions.items.size < 200) records.addView(button(R.string.community_more, "community_mentions_more") { fetchMentions(true) })
    }
    private fun openMention(mention: JSONObject) {
        val account = state.account ?: return
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
                scroll.post { displayed[source.getString("id")]?.let { range -> output.layout?.let { layout -> scroll.scrollTo(0, layout.getLineTop(layout.getLineForOffset(range.first))); markDisplayed() } } }
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
    private fun render(current: Boolean = false, latestViewport: Boolean = false) {
        state.expire()
        if (visible) { ui.removeCallbacks(expireCache); ui.postDelayed(expireCache, minOf(state.expiryDelay(), state.mentions.expiryDelay())) }
        window.decorView.findViewWithTag<TextView>("community_header").text = getString(R.string.community_title) +
            (state.account?.let { " · ${it.handle}" } ?: "")
        status.text = state.account?.let { getString(if (current) R.string.community_current else R.string.community_stale, it.handle) }
            ?: getString(R.string.community_signed_out)
        controls()
        if (mentionsView) { renderMentions(); return }
        if (pane.channel != "general") { renderForum(); return }
        records.removeAllViews()
        val y = scroll.scrollY
        val text = android.text.SpannableStringBuilder(); displayed.clear()
        var divider = false
        for (message in state.messages.filterNot { it.optString("author_handle") in state.blocked }) {
            val id = message.getString("id")
            if (!divider && id !in state.seen) { text.append(getString(R.string.community_unread_divider)).append("\n"); divider = true }
            val start = text.length
            text.append("[$id] ${message.optString("author_handle")} ${message.optString("created")}\n")
            appendBody(text, message.optString("body")); text.append("\n\n")
            displayed[id] = start until text.length
        }
        output.text = if (text.isEmpty()) getString(R.string.community_empty) else text
        scroll.post { if (latestViewport) scroll.fullScroll(android.view.View.FOCUS_DOWN) else scroll.scrollTo(0, y); markDisplayed() }
    }
    private fun markDisplayed() {
        if (!visible || isFinishing || mentionsView) return
        val layout = output.layout ?: return
        var changed = false
        for ((id, range) in displayed) {
            val top = layout.getLineTop(layout.getLineForOffset(range.first))
            val bottom = layout.getLineBottom(layout.getLineForOffset(range.last.coerceAtMost(output.length() - 1)))
            if (top >= scroll.scrollY && bottom <= scroll.scrollY + scroll.height) {
                if (pane.channel == "general") changed = state.seen.add(id) || changed
                for (mention in state.mentions.items.filter { mention -> mention.optString("source_id") == id }) {
                    changed = state.mentions.read(mention.optString("id")) || changed
                }
            }
        }
        val count = if (pane.channel == "general") (displayed.keys.filter { it !in state.seen }.toSet() + state.newArrivalIds).size else 0
        unread.text = if (count == 0) "" else getString(R.string.community_new_messages, count)
        if (changed && storageReady) { controls(); ui.removeCallbacks(saveDraft); ui.postDelayed(saveDraft, 250) }
    }
    private fun <T> request(task: () -> T, failed: (Throwable) -> Unit = {}, complete: (T) -> Unit) {
        if (busy || !visible) { input.error = getString(R.string.community_busy); return }
        busy = true; ui.removeCallbacks(poll)
        val epoch = generation
        job = worker.submit {
            val result = runCatching { client.operation(task) }
            ui.post {
                if (!visible || epoch != generation || isFinishing || isDestroyed) return@post
                busy = false
                result.fold({ value -> pollDelay = 15_000L; complete(value) }) { error ->
                    pollDelay = if (error is CommunityClient.Failure && error.status == 429) error.retrySeconds * 1000 else (pollDelay * 2).coerceAtMost(300_000L)
                    if (error is CommunityClient.Failure && error.status in setOf(401, 403) && state.account != null) logout()
                    status.text = when (error) {
                        is CommunityClient.Uncertain -> getString(R.string.community_uncertain)
                        is CommunityClient.Failure -> when (error.status) {
                            429 -> getString(R.string.community_rate_error, error.retrySeconds)
                            401, 403 -> getString(R.string.community_session_error)
                            400 -> if (error.message == "Handle is already taken.") getString(R.string.community_duplicate_username) else getString(R.string.community_request_error, error.status)
                            else -> getString(R.string.community_request_error, error.status)
                        }
                        else -> getString(R.string.community_network_error)
                    }
                    if (error !is CommunityClient.Failure || error.status !in setOf(401, 403)) failed(error)
                    if (auth.active) { authError = status.text.toString(); auth.restart(); restoreDraft(); configureAuthInput() }
                }
                if (visible && !busy && state.account != null) ui.postDelayed(poll, pollDelay)
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
        generation++; job?.cancel(true); job = null; client.cancel(); busy = false
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
            state.signIn(account); state.draft = draft
            pane.channel = channel; pane.thread = thread; pane.composing = composing
            if (body.isNotEmpty() || title.isNotEmpty()) pane.saveDraft(context, title, body)
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
                generation++; job?.cancel(true); client.cancel(); state.completePost(); state.pane = CommunityPaneState(JSONObject()); restoreDraft()
                if (persist()) finish()
            }.setNeutralButton(R.string.community_cancel, null).show()
    }
    private fun logout() {
        cancelAuth()
        stopForeground(); generation++; job?.cancel(true); client.cancel(); busy = false; ui.removeCallbacks(poll)
        state.clear(); threads = emptyList(); replies = emptyList(); threadRoot = null; forumCursor = null; mentionsView = false
        restoreDraft(); ui.removeCallbacks(saveDraft)
        try { store.clear(); storageReady = true } catch (_: Exception) { storageReady = false }
        render(); status.setText(if (storageReady) R.string.community_logout else R.string.community_storage_error)
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
        }) { (history, checked) ->
            if (!older) state.observeLatest(history)
            state.merge(history + checked, ids, returnLatest); persist(); render(true, returnLatest)
        }
    }
    private fun submit() {
        input.error = null
        if (auth.active) { submitAuth(); return }
        val text = input.text.toString()
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
                val account = state.account ?: run { input.error = getString(R.string.community_login_required); return }
                if (busy) { input.error = getString(R.string.community_busy); return }
                val uncertainAttempt = state.pendingId != null
                val body = if (uncertainAttempt) state.pendingBody else result.body
                val id = state.beginPost(body)
                state.draft = text
                if (!storageReady || !persist()) return
                status.setText(R.string.community_pending)
                request({
                    val message = client.post(account, body, id, uncertainAttempt)
                    message to runCatching { client.history(account, null) }
                }, failed = { error ->
                    if (error is CommunityClient.Failure && error.status in setOf(400, 429)) { state.rejectPost(); persist() }
                }) { (message, latest) ->
                    latest.getOrNull()?.let { state.observeLatest(it) }
                    state.merge(latest.getOrDefault(emptyList()) + message, emptySet(), true)
                    state.seen.add(message.getString("id")); state.completePost()
                    if ((CommunityInput.parse(input.text.toString()) as? CommunityInput.Message)?.body == body) input.setText("")
                    persist(); render(latest.isSuccess, true)
                    status.text = getString(R.string.community_posted) + if (latest.isFailure) " · " + getString(R.string.community_stale, account.handle) else ""
                    val error = latest.exceptionOrNull()
                    if (error is CommunityClient.Failure && error.status in setOf(401, 403)) logout()
                }
            }
        }
    }
}
