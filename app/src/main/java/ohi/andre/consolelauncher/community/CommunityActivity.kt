package ohi.andre.consolelauncher.community

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.graphics.Typeface
import android.view.KeyEvent
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.*
import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.localization.LocalizedComponentActivity
import ohi.andre.consolelauncher.managers.settings.ThemeColorResolver
import ohi.andre.consolelauncher.managers.xml.XMLPrefsManager
import ohi.andre.consolelauncher.managers.xml.options.Theme
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
    private var visible = false
    private var generation = 0
    private var job: java.util.concurrent.Future<*>? = null
    private var pollDelay = 15_000L
    private val poll = Runnable { if (visible && !busy && state.account != null) refresh() }
    private val expireCache = object : Runnable {
        override fun run() {
            if (!visible) return
            val changed = state.expire()
            if (changed || displayed.keys.any { id -> state.messages.none { it.optString("id") == id } }) {
                val previousStatus = status.text
                render(); if (storageReady) persist()
                if (storageReady) status.text = previousStatus
            } else markDisplayed()
            ui.removeCallbacks(this); ui.postDelayed(this, state.expiryDelay())
        }
    }
    private val saveDraft = Runnable { if (::store.isInitialized && storageReady) persist() }
    private var leaveDialog: android.app.AlertDialog? = null
    private var authDialog: android.app.AlertDialog? = null
    private val worker = java.util.concurrent.Executors.newSingleThreadExecutor()
    private val ui = android.os.Handler(android.os.Looper.getMainLooper())
    private lateinit var input: EditText
    private lateinit var status: TextView
    private lateinit var unread: TextView
    private val displayed = mutableMapOf<String, IntRange>()
    private lateinit var output: TextView
    private lateinit var scroll: ScrollView
    override fun onCreate(saved: Bundle?) {
        LauncherSystemUi.requestNoTitleIfFullscreen(this)
        super.onCreate(saved)
        client = CommunityClient(if (ohi.andre.consolelauncher.BuildConfig.DEBUG) debugBackend ?: CommunityClient.PRODUCTION else CommunityClient.PRODUCTION)
        store = CommunityStore(this)
        try { state = CommunityState(store.load()) } catch (_: Exception) { storageReady = false }
        Tuils.init(this); XMLPrefsManager.loadCommons(this); LauncherSystemUi.applyFullscreen(this)
        val color = ThemeColorResolver.color(Theme.output_text_color)
        fun label(tagName: String, text: String = "") = TextView(this).apply {
            tag = tagName; this.text = text; typeface = Typeface.MONOSPACE; textSize = 16f; setTextColor(color)
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(Tuils.dpToPx(this@CommunityActivity, 12), 12, Tuils.dpToPx(this@CommunityActivity, 12), 12)
            setBackgroundColor(ThemeColorResolver.color(Theme.workspace_panel_background_color))
        }
        root.addView(label("community_header", getString(R.string.community_title)))
        status = label("community_status", getString(R.string.community_signed_out)); root.addView(status)
        unread = label("community_unread"); root.addView(unread)
        output = label("community_output", getString(R.string.community_empty)).apply { setTextIsSelectable(true); autoLinkMask = 0 }
        scroll = ScrollView(this).apply { tag = "community_scroll"; addView(output); setOnScrollChangeListener { _, _, _, _, _ -> markDisplayed() } }
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        val prompt = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        prompt.addView(label("community_prompt", "> "))
        input = EditText(this).apply {
            tag = "community_input"; background = null; typeface = Typeface.MONOSPACE; setTextColor(ThemeColorResolver.color(Theme.input_text_color))
            textSize = 16f; setPadding(0, 0, 0, 0); imeOptions = EditorInfo.IME_ACTION_SEND or EditorInfo.IME_FLAG_NO_EXTRACT_UI
            setSingleLine(false); maxLines = 4
            setOnKeyListener { _, key, event ->
                if (key == KeyEvent.KEYCODE_ENTER && !event.isShiftPressed) {
                    if (event.action == KeyEvent.ACTION_UP) submit()
                    true
                } else false
            }
            setOnEditorActionListener { _, action, _ ->
                if (action == EditorInfo.IME_ACTION_SEND) { submit(); true } else false
            }
        }
        prompt.addView(input, LinearLayout.LayoutParams(0, -2, 1f)); root.addView(prompt)
        root.addView(Button(this).apply { tag = "community_submit"; text = getString(R.string.community_submit); setOnClickListener { submit() } })
        setContentView(root)
        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = leave()
        })
        input.setText(state.draft)
        input.isSaveEnabled = false
        input.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val text = s.toString()
                if (!text.startsWith("/") || text.startsWith("//")) {
                    state.draft = text; ui.removeCallbacks(saveDraft); ui.postDelayed(saveDraft, 250)
                }
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })
        render()
        if (storageReady) persist()
    }
    override fun onResume() {
        super.onResume(); visible = true; generation++
        expireCache.run()
        if (state.account != null && storageReady) refresh()
    }
    override fun onPause() {
        visible = false; generation++; ui.removeCallbacks(poll); ui.removeCallbacks(expireCache); ui.removeCallbacks(saveDraft)
        job?.cancel(true); client.cancel(); busy = false
        if (storageReady) persist()
        super.onPause()
    }
    override fun onDestroy() {
        ui.removeCallbacksAndMessages(null); authDialog?.dismiss(); worker.shutdownNow(); client.cancel()
        super.onDestroy()
    }
    private fun persist(): Boolean = try {
        store.save(state.toJson()); storageReady = true; true
    } catch (_: Exception) { storageReady = false; status.setText(R.string.community_storage_error); false }
    private fun render(current: Boolean = false, latestViewport: Boolean = false) {
        state.expire()
        if (visible) { ui.removeCallbacks(expireCache); ui.postDelayed(expireCache, state.expiryDelay()) }
        window.decorView.findViewWithTag<TextView>("community_header").text = getString(R.string.community_title) +
            (state.account?.let { " · ${it.handle}" } ?: "")
        status.text = state.account?.let { getString(if (current) R.string.community_current else R.string.community_stale, it.handle) }
            ?: getString(R.string.community_signed_out)
        val y = scroll.scrollY
        val text = StringBuilder(); displayed.clear()
        var divider = false
        for (message in state.messages.filterNot { it.optString("author_handle") in state.blocked }) {
            val id = message.getString("id")
            if (!divider && id !in state.seen) { text.append(getString(R.string.community_unread_divider)).append("\n"); divider = true }
            val start = text.length
            text.append("[$id] ${message.optString("author_handle")} ${message.optString("created")}\n${message.optString("body")}\n\n")
            displayed[id] = start until text.length
        }
        output.text = text.toString().ifEmpty { getString(R.string.community_empty) }
        scroll.post { if (latestViewport) scroll.fullScroll(android.view.View.FOCUS_DOWN) else scroll.scrollTo(0, y); markDisplayed() }
    }
    private fun markDisplayed() {
        if (!visible || isFinishing) return
        val layout = output.layout ?: return
        var changed = false
        for ((id, range) in displayed) {
            val top = layout.getLineTop(layout.getLineForOffset(range.first))
            val bottom = layout.getLineBottom(layout.getLineForOffset(range.last.coerceAtMost(output.length() - 1)))
            if (top >= scroll.scrollY && bottom <= scroll.scrollY + scroll.height) changed = state.seen.add(id) || changed
        }
        val count = (displayed.keys.filter { it !in state.seen }.toSet() + state.newArrivalIds).size
        unread.text = if (count == 0) "" else getString(R.string.community_new_messages, count)
        if (changed && storageReady) { ui.removeCallbacks(saveDraft); ui.postDelayed(saveDraft, 250) }
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
                    failed(error)
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
                    authDialog?.window?.decorView?.findViewWithTag<TextView>("community_auth_error")?.text = status.text
                }
                if (visible && !busy && state.account != null) ui.postDelayed(poll, pollDelay)
            }
        }
    }
    private fun login(registering: Boolean = false) {
        authDialog?.dismiss()
        val form = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24, 12, 24, 12) }
        val username = EditText(this).apply { tag = "community_username"; setHint(R.string.community_username); isSaveEnabled = false; inputType = android.text.InputType.TYPE_CLASS_TEXT }
        val password = EditText(this).apply {
            tag = "community_password"; setHint(R.string.community_password); isSaveEnabled = false
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
            if (android.os.Build.VERSION.SDK_INT >= 26) importantForAutofill = android.view.View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
        }
        val confirmation = EditText(this).apply {
            tag = "community_confirmation"; setHint(R.string.community_confirm_password); isSaveEnabled = false
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
            if (android.os.Build.VERSION.SDK_INT >= 26) importantForAutofill = android.view.View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
        }
        val formError = TextView(this).apply { tag = "community_auth_error" }
        form.addView(username); form.addView(password)
        if (registering) form.addView(confirmation)
        form.addView(TextView(this).apply { setText(R.string.community_account_policy) }); form.addView(formError)
        form.addView(Button(this).apply {
            tag = "community_auth_submit"; setText(if (registering) R.string.community_register else R.string.community_login)
            setOnClickListener {
                if (busy) return@setOnClickListener
                val name = username.text.toString(); val secret = password.text.toString(); val confirm = confirmation.text.toString()
                if (registering && (!name.matches(Regex("[-_a-zA-Z0-9]{3,24}")) || secret.length < 8 || secret != confirm)) {
                    formError.setText(R.string.community_registration_error); return@setOnClickListener
                }
                password.setText(""); confirmation.setText("")
                request({ if (registering) client.register(name, secret, confirm) else client.login(name, secret) }) { account ->
                    state.signIn(account); input.setText(state.draft)
                    if (persist()) { authDialog?.dismiss(); render(); refresh() }
                }
            }
        })
        if (!registering) form.addView(Button(this).apply {
            tag = "community_register"; setText(R.string.community_register); setOnClickListener { if (!busy) login(true) }
        })
        authDialog = android.app.AlertDialog.Builder(this).setTitle(if (registering) R.string.community_register else R.string.community_login).setView(form)
            .setNegativeButton(R.string.community_cancel, null).create().also { it.show(); it.window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE) }
    }
    private fun leave() {
        if (state.draft.isEmpty() && state.pendingId == null) { finish(); return }
        leaveDialog = android.app.AlertDialog.Builder(this).setTitle(R.string.community_leave_title)
            .setMessage(R.string.community_leave_body)
            .setPositiveButton(R.string.community_keep) { _, _ -> if (persist()) finish() }
            .setNegativeButton(R.string.community_discard) { _, _ ->
                generation++; job?.cancel(true); client.cancel(); state.completePost(); input.setText("")
                if (persist()) finish()
            }.setNeutralButton(R.string.community_cancel, null).show()
    }
    private fun logout() {
        generation++; job?.cancel(true); client.cancel(); busy = false; ui.removeCallbacks(poll)
        state.clear(); input.setText(""); ui.removeCallbacks(saveDraft)
        try { store.clear(); storageReady = true } catch (_: Exception) { storageReady = false }
        render(); status.setText(if (storageReady) R.string.community_logout else R.string.community_storage_error)
    }
    private fun refresh(older: Boolean = false, returnLatest: Boolean = false) {
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
        val text = input.text.toString()
        when (val result = CommunityInput.parse(text)) {
            is CommunityInput.Rejected -> input.error = getString(R.string.community_invalid)
            is CommunityInput.Command -> when (result.name) {
                "login", "register" -> { if (busy) { input.error = getString(R.string.community_busy); return }; input.setText(state.draft); login(result.name == "register") }
                "help" -> { input.setText(state.draft); displayed.clear(); unread.text = ""; output.setText(R.string.community_help) }
                "refresh", "older" -> { input.setText(state.draft); refresh(result.name == "older", result.name == "refresh") }
                "logout" -> logout()
                "block" -> {
                    if (state.account == null) { input.error = getString(R.string.community_login_required); return }
                    if (!result.argument.matches(Regex("[-_a-zA-Z0-9]{3,24}"))) { input.error = getString(R.string.community_invalid); return }
                    val handle = result.argument.lowercase(java.util.Locale.ROOT)
                    if (!state.blocked.add(handle)) state.blocked.remove(handle)
                    input.setText(state.draft); persist(); render(); status.text = getString(R.string.community_blocked, handle)
                }
                "report" -> {
                    val account = state.account ?: run { input.error = getString(R.string.community_login_required); return }
                    if (!result.argument.matches(Regex("[a-zA-Z0-9]{15}"))) { input.error = getString(R.string.community_invalid); return }
                    input.setText(state.draft)
                    request({ client.report(account, result.argument) }) { status.setText(R.string.community_reported) }
                }
                "exit" -> leave()
                else -> input.error = getString(R.string.community_invalid)
            }
            is CommunityInput.Message -> {
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
