package ohi.andre.consolelauncher.managers.podcast

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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
