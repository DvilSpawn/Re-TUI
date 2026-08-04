package ohi.andre.consolelauncher.managers

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.util.Log
import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.managers.xml.XMLPrefsManager
import ohi.andre.consolelauncher.managers.xml.options.Behavior

object LauncherSoundManager {
    enum class Event(val resource: Int, val setting: Behavior? = null) {
        BOOT(R.raw.boot, Behavior.sound_boot),
        SHUTDOWN(R.raw.shutdown),
        CLICK(R.raw.click, Behavior.sound_click),
        CONFIRM(R.raw.confirm),
        CANCEL(R.raw.cancel),
        SUCCESS(R.raw.success, Behavior.sound_success),
        ERROR(R.raw.error, Behavior.sound_failure),
        NOTIFICATION(R.raw.notification, Behavior.sound_notification),
        REMINDER(R.raw.reminder, Behavior.sound_reminder),
        TIMER(R.raw.timer_loop, Behavior.sound_timer),
        ALARM(R.raw.alarm_loop),
        PROCESSING(R.raw.processing_loop)
    }

    private var pool: SoundPool? = null
    private val samples = HashMap<Event, Int>()
    private val loaded = HashSet<Int>()
    private val pending = ArrayList<Event>()
    private var bootPlayed = false

    @Synchronized
    fun play(context: Context, event: Event): Boolean {
        if (!enabled(event)) return false
        initialize(context.applicationContext)
        val sample = samples[event] ?: return false
        if (sample in loaded) {
            playLoaded(event, sample)
        } else {
            pending.add(event)
        }
        return true
    }

    @Synchronized
    fun playBoot(context: Context) {
        if (!bootPlayed && play(context, Event.BOOT)) bootPlayed = true
    }

    fun isEnabled(): Boolean = runCatching {
        XMLPrefsManager.getBoolean(Behavior.launcher_sounds)
    }.getOrDefault(false)

    private fun enabled(event: Event): Boolean = runCatching {
        isEnabled() &&
            (event.setting == null || XMLPrefsManager.getBoolean(event.setting))
    }.getOrDefault(false)

    private fun initialize(context: Context) {
        if (pool != null) return
        pool = SoundPool.Builder()
            .setMaxStreams(4)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .build()
            .also { soundPool ->
                soundPool.setOnLoadCompleteListener { _, sample, status ->
                    if (status != 0) return@setOnLoadCompleteListener
                    synchronized(this) {
                        loaded.add(sample)
                        val iterator = pending.iterator()
                        while (iterator.hasNext()) {
                            val event = iterator.next()
                            if (samples[event] == sample) {
                                if (soundPool.play(sample, 1f, 1f, 1, 0, 1f) != 0) {
                                    Log.d("TUI-SOUND", "Played: ${event.name}")
                                }
                                iterator.remove()
                            }
                        }
                    }
                }
                for (event in Event.entries) samples[event] = soundPool.load(context, event.resource, 1)
            }
    }

    private fun playLoaded(event: Event, sample: Int) {
        if (pool?.play(sample, 1f, 1f, 1, 0, 1f) != 0) {
            Log.d("TUI-SOUND", "Played: ${event.name}")
        }
    }
}
