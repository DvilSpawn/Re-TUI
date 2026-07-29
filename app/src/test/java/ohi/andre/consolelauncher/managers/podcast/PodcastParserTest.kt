package ohi.andre.consolelauncher.managers.podcast

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream

class PodcastParserTest {
    @Test
    fun parsesPodcastRssOldestFirst() {
        val rss = """
            <rss version="2.0" xmlns:itunes="http://www.itunes.com/dtds/podcast-1.0.dtd">
              <channel>
                <title>Sample Show</title>
                <description>Sample description</description>
                <itunes:image href="https://example.com/show.jpg" />
                <item>
                  <title>Episode 2</title>
                  <guid>ep-2</guid>
                  <pubDate>Tue, 02 Jan 2024 00:00:00 +0000</pubDate>
                  <enclosure url="https://example.com/ep2.mp3" type="audio/mpeg" />
                </item>
                <item>
                  <title>Episode 1</title>
                  <guid>ep-1</guid>
                  <pubDate>Mon, 01 Jan 2024 00:00:00 +0000</pubDate>
                  <enclosure url="https://example.com/ep1.mp3" type="audio/mpeg" />
                </item>
                <item>
                  <title>Bonus</title>
                  <guid>bonus</guid>
                  <enclosure url="https://example.com/bonus.mp3" type="audio/mpeg" />
                </item>
              </channel>
            </rss>
        """.trimIndent()

        val show = PodcastParser.parse(ByteArrayInputStream(rss.toByteArray()), "https://example.com/feed.xml")

        assertEquals("Sample Show", show.title)
        assertEquals("https://example.com/show.jpg", show.imageUrl)
        assertEquals(3, show.episodes.size)
        assertEquals("Episode 1", show.episodes[0].title)
        assertEquals("ep-1", show.episodes[0].key)
        assertEquals("https://example.com/ep1.mp3", show.episodes[0].audioUrl)
        assertEquals("Episode 2", show.episodes[1].title)
        assertEquals("Bonus", show.episodes[2].title)
        assertNotNull(show.episodes[0].publishedAt)
    }

    @Test
    fun newestFirstKeepsUndatedEpisodesLast() {
        val episodes = listOf(
            episode("Old", 1000L, 0),
            episode("New", 3000L, 1),
            episode("Undated", null, 2)
        )

        val ordered = PodcastManager.orderedEpisodes(episodes, newestFirst = true)

        assertEquals(listOf("New", "Old", "Undated"), ordered.map { it.title })
    }

    @Test
    fun normalizesPodcastTags() {
        assertEquals(
            listOf("workout", "travel", "calming"),
            PodcastManager.parseTags("Workout, travel #Calming, workout")
        )
    }

    @Test
    fun rejectsDocumentTypesAndExternalEntities() {
        val unsafe = """
            <!DOCTYPE rss [<!ENTITY leak SYSTEM "file:///etc/passwd">]>
            <rss><channel><title>&leak;</title></channel></rss>
        """.trimIndent()

        var rejected = false
        try {
            PodcastParser.parse(ByteArrayInputStream(unsafe.toByteArray()), "https://example.com/feed.xml")
        } catch (_: Exception) {
            rejected = true
        }

        assertTrue(rejected)
    }

    @Test
    fun capsVeryLargeEpisodeLists() {
        val items = (0 until PodcastParser.MAX_EPISODES + 5).joinToString("") {
            "<item><title>Episode $it</title><enclosure url=\"https://example.com/$it.mp3\" /></item>"
        }
        val rss = "<rss><channel><title>Large show</title>$items</channel></rss>"

        val show = PodcastParser.parse(ByteArrayInputStream(rss.toByteArray()), "https://example.com/feed.xml")

        assertEquals(PodcastParser.MAX_EPISODES, show.episodes.size)
    }

    @Test
    fun validatesSecureFeedUrls() {
        assertTrue(PodcastManager.isSecureFeedUrl("https://example.com/feed.xml"))
        assertFalse(PodcastManager.isSecureFeedUrl("http://example.com/feed.xml"))
        assertFalse(PodcastManager.isSecureFeedUrl("https://"))
        assertFalse(PodcastManager.isSecureFeedUrl("not a url"))
    }

    @Test
    fun filtersEpisodesByTitleOrDescription() {
        val episodes = listOf(
            episode("Android news", 1000L, 0).copy(description = "Weekly roundup"),
            episode("Design chat", 2000L, 1).copy(description = "Launcher accessibility")
        )

        assertEquals(listOf("Android news"), PodcastManager.filterEpisodes(episodes, "android").map { it.title })
        assertEquals(listOf("Design chat"), PodcastManager.filterEpisodes(episodes, "ACCESS").map { it.title })
        assertEquals(episodes, PodcastManager.filterEpisodes(episodes, " "))
    }

    private fun episode(title: String, publishedAt: Long?, feedOrder: Int): PodcastEpisode =
        PodcastEpisode(
            showId = "show",
            key = title,
            title = title,
            audioUrl = "https://example.com/$title.mp3",
            link = null,
            description = "",
            duration = null,
            publishedAt = publishedAt,
            feedOrder = feedOrder
        )
}
