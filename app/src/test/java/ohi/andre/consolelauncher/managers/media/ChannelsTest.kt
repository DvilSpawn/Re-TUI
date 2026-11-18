package ohi.andre.consolelauncher.managers.media

import ohi.andre.consolelauncher.managers.media.Channels.Channel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChannelsTest {
    @Test
    fun extinfNamesTheNextStreamWithItsGroup() {
        val text = """
            #EXTM3U
            #EXTINF:-1 tvg-id="news.ch" tvg-logo="https://x/logo.png" group-title="News",City News
            https://example.com/news/index.m3u8
            #EXTINF:-1 group-title="Music, live",Radio One, HD
            #EXTVLCOPT:http-user-agent=Player
            http://example.com/radio.m3u8
        """.trimIndent()
        assertEquals(listOf(
            Channel("City News", "https://example.com/news/index.m3u8", "News"),
            Channel("Radio One, HD", "http://example.com/radio.m3u8", "Music, live"),
        ), Channels.parse(text))
    }

    @Test
    fun extgrpSetsTheGroupAndBareAddressesAreNamedFromTheirPath() {
        val text = "﻿#EXTM3U\n#EXTINF:0,One\n#EXTGRP:Sports\nhttps://a.tv/one.m3u8\nhttps://a.tv/live/two.m3u8?token=1\n"
        assertEquals(listOf(Channel("One", "https://a.tv/one.m3u8", "Sports"), Channel("two", "https://a.tv/live/two.m3u8?token=1", "")),
            Channels.parse(text))
    }

    @Test
    fun repeatsAndJunkAreDropped() {
        val text = "#EXTINF:-1,A\nhttps://a.tv/a\nnot a url\n#EXTINF:-1,A again\nhttps://a.tv/a\n"
        assertEquals(listOf(Channel("A", "https://a.tv/a")), Channels.parse(text))
    }

    @Test
    fun onlyNetworkAddressesAreStreams() {
        assertTrue(Channels.isStream("https://a.tv/x.m3u8"))
        assertTrue(Channels.isStream("rtsp://cam.local/live"))
        assertFalse(Channels.isStream("content://com.android.externalstorage.documents/tree/x"))
        assertFalse(Channels.isStream("file:///sdcard/x.mp4"))
        assertFalse(Channels.isStream(null))
    }
}
