package ohi.andre.consolelauncher.managers.mediaplayer

import org.junit.Assert.assertEquals
import org.junit.Test

class ForwardCacheTest {
    @Test fun budgetFollowsBitrateAndTime() = assertEquals(16_000L * 600, ForwardCache.budgetBytes(128_000, 10))
    @Test fun anUnknownBitrateUsesTheFallback() = assertEquals(ForwardCache.budgetBytes(128_000, 1), ForwardCache.budgetBytes(-1, 1))
    @Test fun startsPastThePlayersOwnBuffer() = assertEquals(16_000L * 70, ForwardCache.startOffset(10_000, 128_000))
}
