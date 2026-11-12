package ohi.andre.consolelauncher.managers.mediaplayer

import android.content.Context
import android.media.audiofx.Equalizer

/** The equaliser: the device's own presets, applied to the audio player and remembered. Some devices have none. */
internal class AudioEffects(context: Context, sessionId: Int) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val equalizer = runCatching { Equalizer(0, sessionId) }.getOrNull()

    val presets: List<String> = runCatching {
        equalizer?.let { eq -> (0 until eq.numberOfPresets).map { eq.getPresetName(it.toShort()) } }
    }.getOrNull().orEmpty()

    var preset = prefs.getInt(KEY_PRESET, OFF)
        private set

    init {
        apply()
    }

    fun select(next: Int) {
        preset = next
        prefs.edit().putInt(KEY_PRESET, next).apply()
        apply()
    }

    private fun apply() {
        runCatching {
            val eq = equalizer ?: return
            if (preset in presets.indices) eq.usePreset(preset.toShort())
            eq.enabled = preset in presets.indices
        }
    }

    fun release() {
        runCatching { equalizer?.release() }
    }

    companion object {
        const val PREFS = "retui_media_audio"
        const val OFF = -1
        private const val KEY_PRESET = "eq_preset"
    }
}
