package ohi.andre.consolelauncher.reliability

import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.Canvas
import android.media.session.MediaSession
import android.view.View
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import ohi.andre.consolelauncher.managers.notifications.KeeperService
import ohi.andre.consolelauncher.managers.notifications.NotificationService
import ohi.andre.consolelauncher.managers.xml.XMLPrefsManager
import ohi.andre.consolelauncher.tuils.OutlineTextView
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReliabilityTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private fun attach(service: ContextWrapper) {
        ContextWrapper::class.java.getDeclaredMethod("attachBaseContext", Context::class.java).apply {
            isAccessible = true
            invoke(service, context)
        }
    }
    @Test fun nullRestartDoesNotReadCommandExtras() {
        instrumentation.runOnMainSync {
            XMLPrefsManager.loadCommons(context)
            val service = KeeperService()
            attach(service)
            // Exercise an already initialized service receiving a restart without extras.
            KeeperService::class.java.getDeclaredField("initialized").apply { isAccessible = true; setBoolean(service, true) }
            KeeperService::class.java.getDeclaredField("lastCommands").apply { isAccessible = true; set(service, arrayOfNulls<CharSequence>(2)) }
            assertEquals(android.app.Service.START_STICKY, service.onStartCommand(null, 0, 5))
        }
    }
    @Test fun mediaSessionsAcceptAbsentStateAndTeardown() {
        instrumentation.runOnMainSync {
            XMLPrefsManager.loadCommons(context)
            val service = NotificationService()
            attach(service)
            val update = NotificationService::class.java.getDeclaredMethod("updateActiveSessions", java.util.List::class.java).apply { isAccessible = true }
            val session = MediaSession(context, "reliability-test")
            try {
                update.invoke(service, null)
                update.invoke(service, arrayListOf(null, session.controller))
                session.release()
                update.invoke(service, arrayListOf<android.media.session.MediaController?>())
                val field = NotificationService::class.java.getDeclaredField("activeControllers").apply { isAccessible = true }
                assertTrue((field.get(service) as List<*>).isEmpty())
            } finally { session.release(); update.invoke(service, null) }
        }
    }
    @Test fun redrawCountIsBounded() {
        val old = OutlineTextView.redrawTimes
        try {
            for (configured in listOf(Int.MIN_VALUE, 0, 1, 4, 8, Int.MAX_VALUE)) {
                OutlineTextView.redrawTimes = configured
                assertEquals(1, OutlineTextView.drawPasses(null))
                assertEquals(configured.coerceIn(1, 8), OutlineTextView.drawPasses(OutlineTextView.SHADOW_TAG))
            }
        } finally { OutlineTextView.redrawTimes = old }
    }
    @Test fun profileTextDrawing() {
        instrumentation.runOnMainSync {
            val old = OutlineTextView.redrawTimes
            val bitmap = Bitmap.createBitmap(1080, 1920, Bitmap.Config.ARGB_8888)
            try {
                for (size in listOf(2000, 20000, 100000)) for (passes in listOf(1, 8)) {
                    OutlineTextView.redrawTimes = passes
                    val view = OutlineTextView(context).apply {
                        tag = OutlineTextView.SHADOW_TAG
                        text = android.text.SpannableStringBuilder("Terminal output text 0123456789\n".repeat(size / 32))
                        textSize = 14f
                    }
                    val start = android.os.SystemClock.elapsedRealtimeNanos()
                    view.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
                    view.layout(0, 0, 1080, view.measuredHeight)
                    val canvas = Canvas(bitmap)
                    view.draw(canvas)
                    val ms = (android.os.SystemClock.elapsedRealtimeNanos() - start) / 1000000
                    println("TEXT_PROFILE chars=$size passes=$passes layoutAndDrawMs=$ms height=${view.measuredHeight}")
                }
            } finally { OutlineTextView.redrawTimes = old; bitmap.recycle() }
        }
    }
}
