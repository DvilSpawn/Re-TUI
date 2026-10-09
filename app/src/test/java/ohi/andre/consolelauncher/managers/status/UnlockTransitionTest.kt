package ohi.andre.consolelauncher.managers.status

import org.junit.Assert.*
import org.junit.Test

class UnlockTransitionTest {
    @Test fun unlockedEventsWithoutObservedCycleNeverCount() {
        val transition = UnlockTransition()
        assertFalse(transition.onScreenEvent(false, false))
    }

    @Test fun wakeAndUserPresentCountOneUnlock() {
        val transition = UnlockTransition(true)
        assertTrue(transition.onScreenEvent(false, false))
        assertFalse(transition.onScreenEvent(false, false))
    }

    @Test fun lockedEventsWaitForUserPresent() {
        val transition = UnlockTransition()
        assertFalse(transition.onScreenEvent(false, true))
        assertFalse(transition.onScreenEvent(false, true))
        assertTrue(transition.onScreenEvent(false, false))
        assertFalse(transition.onScreenEvent(false, false))
    }

    @Test fun twoRapidLockCyclesBothCount() {
        val transition = UnlockTransition()
        repeat(2) {
            assertFalse(transition.onScreenEvent(true, false))
            assertFalse(transition.onScreenEvent(true, true))
            assertFalse(transition.onScreenEvent(false, true))
            assertTrue(transition.onScreenEvent(false, false))
            assertFalse(transition.onScreenEvent(false, false))
        }
    }

    @Test fun noSecureKeyguardCountsWakeOncePerScreenCycle() {
        val transition = UnlockTransition()
        repeat(2) {
            assertFalse(transition.onScreenEvent(true, false))
            assertTrue(transition.onScreenEvent(false, false))
            assertFalse(transition.onScreenEvent(false, false))
        }
    }

    @Test fun screenOffArmsButNeverCounts() {
        val transition = UnlockTransition()
        assertFalse(transition.onScreenEvent(true, false))
        assertTrue(transition.onScreenEvent(false, false))
    }
}
