package ohi.andre.consolelauncher.managers.mediaplayer

/**
 * What to do after a playback error. A stream gets a few retries, and waits as long as it takes while there is no
 * network. A local file cannot get better by waiting, so it is skipped at once.
 */
internal class PlaybackRecovery(
    private val retryDelaysMs: List<Long> = listOf(1_000, 3_000, 8_000),
    private val offlineEveryMs: Long = 15_000,
) {
    sealed interface Step
    data class Retry(val delayMs: Long, val offline: Boolean) : Step
    data object Skip : Step
    data object GiveUp : Step

    private var attempts = 0
    private var skippedInARow = 0

    fun next(streaming: Boolean, online: Boolean, queueSize: Int): Step {
        if (streaming && !online) return Retry(offlineEveryMs, offline = true)
        if (streaming && attempts < retryDelaysMs.size) return Retry(retryDelaysMs[attempts++], offline = false)
        attempts = 0
        if (++skippedInARow < queueSize) return Skip
        skippedInARow = 0
        return GiveUp
    }

    /** A different item is starting, so it gets its own retries. */
    fun trackChanged() {
        attempts = 0
    }

    /** Audio is coming out, so whatever was wrong has passed. */
    fun played() {
        attempts = 0
        skippedInARow = 0
    }
}
