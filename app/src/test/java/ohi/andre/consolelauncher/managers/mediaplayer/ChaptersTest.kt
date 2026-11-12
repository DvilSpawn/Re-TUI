package ohi.andre.consolelauncher.managers.mediaplayer

import org.junit.Assert.assertEquals
import org.junit.Test

class ChaptersTest {
    @Test fun readsPodcastingChaptersAndSkipsHiddenOnes() {
        val json = """{"chapters":[{"startTime":61.5,"title":"B"},{"startTime":0,"title":"A"},{"startTime":30,"title":"ad","toc":false}]}"""
        assertEquals(listOf(Chapter(0, "A"), Chapter(61500, "B")), Chapters.parseJson(json))
    }

    @Test fun aBadFileIsNoChapters() {
        assertEquals(emptyList<Chapter>(), Chapters.parseJson("not json"))
    }

    @Test fun findsTheCurrentChapter() {
        val list = listOf(Chapter(0, "A"), Chapter(1000, "B"))
        assertEquals(0, Chapters.indexAt(list, 999))
        assertEquals(1, Chapters.indexAt(list, 1000))
    }
}
