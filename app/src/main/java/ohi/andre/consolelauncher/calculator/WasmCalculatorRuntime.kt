package ohi.andre.consolelauncher.calculator

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebMessage
import android.webkit.WebMessagePort
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import ohi.andre.consolelauncher.R
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.File
import java.util.ArrayDeque
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** A calculator-only message channel; downloaded code never receives Android objects. */
object WasmCalculatorRuntime {
    private const val ORIGIN = "https://retui-calculator.invalid"
    private const val MAX_REPLY = 262144
    private val main = Handler(Looper.getMainLooper())
    private val waiting = ArrayDeque<Job>()
    private var current: Job? = null
    private var view: WebView? = null
    private var port: WebMessagePort? = null
    private var ready = false
    private var generation = 0
    private var packKey: String? = null
    private var packDirectory: File? = null
    @Volatile private var unavailablePackKey: String? = null

    fun available(context: Context): Boolean {
        val directory = CalculatorPackStore.activeDirectory(context) ?: return false
        val key = directory.absolutePath + ":" + CalculatorPackStore.installedVersion(context)
        return Build.VERSION.SDK_INT >= 24 && key != unavailablePackKey
    }

    private class Job(val context: Context, val expression: String, val options: CalculatorEngine.Options,
        val request: CalculatorEngine.Request, val preview: Boolean) {
        val done = CountDownLatch(1)
        @Volatile var result = CalculatorEngine.Result("", true, failure = "unavailable")
        var deadline: Runnable? = null
        var retriedPrevious = false
    }

    fun evaluate(context: Context, expression: String, options: CalculatorEngine.Options,
        request: CalculatorEngine.Request, preview: Boolean): CalculatorEngine.Result {
        check(Looper.myLooper() != Looper.getMainLooper())
        val job = Job(context.applicationContext, expression, options, request, preview)
        if (!request.attachCancellation { main.post { cancel(request.id, "cancelled") } })
            return failure(job, "cancelled")
        main.post {
            if (request.cancelled) finish(job, failure(job, "cancelled"))
            else { waiting.add(job); startNext() }
        }
        try {
            if (!job.done.await(8, TimeUnit.SECONDS)) {
                main.post { cancel(request.id, "timeout") }
                return failure(job, "timeout")
            }
            return job.result
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            main.post { cancel(request.id, "cancelled") }
            return failure(job, "cancelled")
        } finally { request.finished() }
    }

    /** Call after replacing/removing a pack. No running worker retains the previous engine. */
    fun reset() {
        main.post {
            unavailablePackKey = null
            current?.let { finish(it, failure(it, "stopped")) }
            current = null
            while (waiting.isNotEmpty()) waiting.removeFirst().let { finish(it, failure(it, "stopped")) }
            closeView()
        }
    }

    private fun failure(job: Job, reason: String): CalculatorEngine.Result {
        val resource = when (reason) {
            "cancelled" -> R.string.calculator_cancelled
            "timeout" -> R.string.calculator_timeout
            else -> R.string.calculator_engine_stopped
        }
        return CalculatorEngine.Result(job.context.getString(resource), true, failure = reason)
    }

    private fun finish(job: Job, result: CalculatorEngine.Result) {
        job.deadline?.let { main.removeCallbacks(it) }
        job.result = result
        job.done.countDown()
    }

    private fun cancel(id: String, reason: String) {
        val active = current
        if (active?.request?.id == id) {
            if (reason == "timeout" && !ready) {
                packDirectory?.let { directory ->
                    runCatching { CalculatorPackStore.rollback(active.context, directory) }
                }
            }
            finish(active, failure(active, reason))
            current = null
            closeView()
            startNext()
        } else {
            val iterator = waiting.iterator()
            while (iterator.hasNext()) {
                val job = iterator.next()
                if (job.request.id == id) {
                    iterator.remove()
                    finish(job, failure(job, reason))
                    break
                }
            }
        }
    }

    private fun startNext() {
        if (current == null) current = waiting.pollFirst()
        val job = current ?: return
        if (job.request.cancelled) { cancel(job.request.id, "cancelled"); return }
        val directory = CalculatorPackStore.activeDirectory(job.context)
        if (directory == null) { failRuntime("unavailable"); return }
        val key = directory.absolutePath + ":" + CalculatorPackStore.installedVersion(job.context)
        if (packKey != key) closeView()
        if (view == null) {
            packKey = key
            packDirectory = directory
            try { createView(job.context, directory) } catch (_: Exception) { failRuntime("unavailable") }
        } else if (ready && job.deadline == null) dispatch(job)
    }

    private fun dispatch(job: Job) {
        val message = JSONObject().put("type", "evaluate").put("id", job.request.id)
            .put("expression", job.expression).put("degrees", job.options.degrees)
            .put("exact", job.options.exact).put("preview", job.preview)
        job.deadline = Runnable { cancel(job.request.id, "timeout") }.also { main.postDelayed(it, 4000) }
        try { port?.postMessage(WebMessage(message.toString())) ?: failRuntime("unavailable") }
        catch (_: Exception) { failRuntime("unavailable") }
    }

    private fun failRuntime(reason: String) {
        val active = current
        val failedDirectory = packDirectory
        if (reason == "unavailable" && active != null && failedDirectory != null && !active.retriedPrevious) {
            active.retriedPrevious = true
            if (runCatching { CalculatorPackStore.rollback(active.context, failedDirectory) }.getOrDefault(false)) {
                active.deadline?.let { main.removeCallbacks(it) }
                active.deadline = null
                unavailablePackKey = null
                closeView()
                startNext()
                return
            }
        }
        if (reason == "unavailable") unavailablePackKey = packKey
        current?.let { finish(it, failure(it, reason)) }
        current = null
        while (waiting.isNotEmpty()) waiting.removeFirst().let { finish(it, failure(it, reason)) }
        closeView()
    }

    private fun closeView() {
        generation++
        ready = false
        packKey = null
        packDirectory = null
        try { port?.close() } catch (_: Exception) { }
        port = null
        try { view?.stopLoading(); view?.destroy() } catch (_: Exception) { }
        view = null
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun createView(context: Context, directory: File) {
        val token = ++generation
        val web = WebView(context)
        view = web
        web.settings.apply {
            javaScriptEnabled = true
            allowFileAccess = false
            allowContentAccess = false
            domStorageEnabled = false
            databaseEnabled = false
            blockNetworkLoads = true
            cacheMode = WebSettings.LOAD_NO_CACHE
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            setSupportMultipleWindows(false)
        }
        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?) = true
            @Suppress("DEPRECATION")
            override fun shouldOverrideUrlLoading(view: WebView?, url: String?) = true
            override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest): WebResourceResponse =
                asset(context, directory, request.url)
            override fun onPageFinished(view: WebView, url: String?) {
                if (token != generation || url != "$ORIGIN/index.html" || port != null) return
                try {
                    val pair = view.createWebMessageChannel()
                    port = pair[0]
                    pair[0].setWebMessageCallback(object : WebMessagePort.WebMessageCallback() {
                        override fun onMessage(port: WebMessagePort, message: WebMessage?) {
                            if (token == generation) receive(message?.data)
                        }
                    }, main)
                    view.postWebMessage(WebMessage("connect", arrayOf(pair[1])), Uri.parse(ORIGIN))
                } catch (_: Exception) { failRuntime("unavailable") }
            }
            override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                if (token == generation) failRuntime("unavailable")
                return true
            }
        }
        web.loadUrl("$ORIGIN/index.html")
    }

    private fun receive(data: String?) {
        try {
            require(data != null && data.length <= MAX_REPLY)
            val reply = JSONObject(data)
            when (reply.getString("type")) {
                "ready" -> { require(!ready); ready = true; current?.let { dispatch(it) } }
                "unavailable" -> failRuntime("unavailable")
                "result" -> {
                    val job = current ?: return
                    require(reply.getString("id") == job.request.id)
                    val value = reply.getJSONObject("result")
                    val status = value.getInt("status")
                    require(status in 0..3)
                    val text = value.getString("text")
                    val parsed = value.optString("parsed").takeIf { it.isNotBlank() }
                    finish(job, CalculatorEngine.Result(text, status == 1 || status == 3,
                        approximate = status == 2, reusable = if (!job.preview && (status == 0 || status == 2)) text else null,
                        failure = value.optString("failure").takeIf { it.isNotBlank() } ?: when (status) { 1 -> nativeFailure(text); 3 -> "preview_unavailable"; else -> null }, parsed = parsed))
                    current = null
                    startNext()
                }
                else -> failRuntime("protocol")
            }
        } catch (_: Exception) { failRuntime("protocol") }
    }

    private fun nativeFailure(text: String): String = when {
        text.contains("zero", true) && (text.contains("div", true) || text.contains("denominator", true)) -> "division_by_zero"
        text.contains("domain", true) || text.contains("real number", true) || text.contains("positive", true) -> "domain"
        text.contains("not supported", true) || text.contains("unable to", true) -> "unsupported"
        else -> "syntax"
    }

    private fun asset(context: Context, directory: File, uri: Uri): WebResourceResponse {
        val allowed = uri.scheme == "https" && uri.authority == "retui-calculator.invalid" && uri.query == null && uri.fragment == null
        val name = if (allowed) uri.path.orEmpty().removePrefix("/") else ""
        return try {
            val mime = when (name) { "index.html" -> "text/html"; "engine.wasm" -> "application/wasm"; else -> "application/javascript" }
            val stream = when (name) {
                "index.html", "bootstrap.js", "worker.js" -> context.assets.open("calculator-runtime/$name")
                "engine.js", "engine.wasm" -> File(directory, name).inputStream()
                else -> return blocked()
            }
            WebResourceResponse(mime, if (name == "engine.wasm") null else "UTF-8", 200, "OK", mapOf(
                "Cache-Control" to "no-store", "X-Content-Type-Options" to "nosniff",
                "Content-Security-Policy" to "default-src 'none'; script-src 'self' blob: 'unsafe-eval' 'wasm-unsafe-eval'; connect-src 'self'; worker-src blob:; child-src blob:; frame-src 'none'; base-uri 'none'"
            ), stream)
        } catch (_: Exception) { blocked() }
    }

    private fun blocked() = WebResourceResponse("text/plain", "UTF-8", 403, "Forbidden", emptyMap(), ByteArrayInputStream(ByteArray(0)))
}
