package ohi.andre.consolelauncher.managers.status

import android.content.Context
import android.os.Handler
import android.os.Looper

abstract class StatusManager(
    @JvmField protected val context: Context,
    @JvmField protected var delay: Long
) {
    @JvmField protected val handler: Handler = Handler(Looper.getMainLooper())
    @JvmField protected var running: Boolean = false
    private val updateRunnable: Runnable = object : Runnable {
        override fun run() {
            if (running) {
                update()
                handler.postDelayed(this, delay)
            }
        }
    }

    open fun start() {
        if (!running) {
            running = true
            handler.post(updateRunnable)
        }
    }

    open fun stop() {
        running = false
        handler.removeCallbacks(updateRunnable)
    }

    open fun refreshAppearance() { if (running) update() }

    fun refreshNow() = update()

    protected abstract fun update()
}
