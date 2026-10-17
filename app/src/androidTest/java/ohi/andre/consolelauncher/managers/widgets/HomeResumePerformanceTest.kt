package ohi.andre.consolelauncher.managers.widgets

import android.content.Intent
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.provider.Settings
import android.view.FrameMetrics
import android.view.ViewTreeObserver
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.math.ceil

class HomeResumePerformanceTest {
    @Test fun settingsToHomeKeepsTheWarmLauncherAndRecordsFrameTimings() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        WidgetPaneIntegrationTest().fixture(keyboardOnStart = InstrumentationRegistry.getArguments().getString("homePerfKeyboard") == "true") { home, drawer ->
            instrumentation.runOnMainSync { drawer.hide() }
            if (InstrumentationRegistry.getArguments().getString("homePerfFrames") == "true") {
                ohi.andre.consolelauncher.tuils.FrameManager.beginEdit(context).apply {
                    applyPack(ohi.andre.consolelauncher.tuils.FrameManager.SPROUT_LANDS_PACK_ID)
                    save()
                }
            }
            Thread.sleep(1200)
            val metricsThread = HandlerThread("home-frame-metrics").apply { start() }
            val frames = Collections.synchronizedList(mutableListOf<Double>())
            val frameListener = android.view.Window.OnFrameMetricsAvailableListener { _, metric, _ ->
                frames.add(metric.getMetric(FrameMetrics.TOTAL_DURATION) / 1_000_000.0)
            }
            val returns = mutableListOf<Double>()
            val originalUi = home.uiManager
            try {
                instrumentation.runOnMainSync { home.window.addOnFrameMetricsAvailableListener(frameListener, Handler(metricsThread.looper)) }
                repeat(10) {
                    context.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    Thread.sleep(400)
                    val drawn = CountDownLatch(1)
                    var started = 0L
                    lateinit var listener: ViewTreeObserver.OnDrawListener
                    instrumentation.runOnMainSync {
                        listener = ViewTreeObserver.OnDrawListener {
                            if (drawn.count > 0) {
                                returns.add((SystemClock.elapsedRealtimeNanos() - started) / 1_000_000.0)
                                drawn.countDown()
                                home.window.decorView.post { home.window.decorView.viewTreeObserver.removeOnDrawListener(listener) }
                            }
                        }
                        home.window.decorView.viewTreeObserver.addOnDrawListener(listener)
                        started = SystemClock.elapsedRealtimeNanos()
                        context.startActivity(Intent(context, home.javaClass).setAction(Intent.ACTION_MAIN)
                            .addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }
                    assertTrue("Home must draw after returning from Settings", drawn.await(8, TimeUnit.SECONDS))
                    assertSame("Home must reuse the existing launcher UI", originalUi, home.uiManager)
                    Thread.sleep(1050)
                }
                fun percentile(values: List<Double>, fraction: Double): Double = values.sorted()
                    .let { it[(ceil(it.size * fraction).toInt() - 1).coerceAtLeast(0)] }
                val captured = synchronized(frames) { frames.toList() }
                assertEquals(10, returns.size)
                assertTrue(captured.isNotEmpty())
                val label = InstrumentationRegistry.getArguments().getString("homePerfLabel", "current")
                    .filter { it.isLetterOrDigit() || it == '-' }.take(40)
                File(context.filesDir, "home-resume-$label.json").writeText(JSONObject()
                    .put("keyboardOnStart", InstrumentationRegistry.getArguments().getString("homePerfKeyboard") == "true")
                    .put("framesEnabled", InstrumentationRegistry.getArguments().getString("homePerfFrames") == "true")
                    .put("cycles", returns.size).put("firstDrawMs", JSONArray(returns))
                    .put("firstDrawMedianMs", percentile(returns, .5)).put("firstDrawP95Ms", percentile(returns, .95))
                    .put("frameCount", captured.size).put("frameP95Ms", percentile(captured, .95))
                    .put("framesOver32ms", captured.count { it > 32.0 }).put("framesOver100ms", captured.count { it > 100.0 })
                    .toString(2))
            } finally {
                instrumentation.runOnMainSync { home.window.removeOnFrameMetricsAvailableListener(frameListener) }
                metricsThread.quitSafely()
            }
        }
    }
}
