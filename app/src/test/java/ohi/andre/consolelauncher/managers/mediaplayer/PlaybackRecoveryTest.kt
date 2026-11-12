package ohi.andre.consolelauncher.managers.mediaplayer

import ohi.andre.consolelauncher.managers.mediaplayer.PlaybackRecovery.GiveUp
import ohi.andre.consolelauncher.managers.mediaplayer.PlaybackRecovery.Retry
import ohi.andre.consolelauncher.managers.mediaplayer.PlaybackRecovery.Skip
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackRecoveryTest {
    private val recovery = PlaybackRecovery(listOf(1, 2), 9)

    @Test fun aStreamRetriesThenSkips() {
        assertEquals(Retry(1, false), recovery.next(true, true, 5))
        assertEquals(Retry(2, false), recovery.next(true, true, 5))
        assertEquals(Skip, recovery.next(true, true, 5))
    }

    @Test fun offlineWaitsWithoutUsingUpRetries() {
        repeat(10) { assertEquals(Retry(9, true), recovery.next(true, false, 5)) }
        assertEquals(Retry(1, false), recovery.next(true, true, 5))
    }

    @Test fun aLocalFileIsSkippedAtOnce() = assertEquals(Skip, recovery.next(false, false, 5))

    @Test fun givesUpOnceTheWholeQueueHasFailed() {
        assertEquals(Skip, recovery.next(false, true, 2))
        assertEquals(GiveUp, recovery.next(false, true, 2))
    }

    @Test fun playingClearsTheCount() {
        recovery.next(false, true, 2)
        recovery.played()
        assertEquals(Skip, recovery.next(false, true, 2))
    }

    @Test fun aNewTrackGetsFreshRetries() {
        recovery.next(true, true, 5)
        recovery.trackChanged()
        assertEquals(Retry(1, false), recovery.next(true, true, 5))
    }
}
