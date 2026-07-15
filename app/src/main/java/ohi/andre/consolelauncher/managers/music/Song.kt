package ohi.andre.consolelauncher.managers.music

import it.andreuzzi.comparestring2.StringableObject
import java.io.File

class Song : StringableObject {
    private var id: Long
    private var title: String
    private var path: String? = null
    private var lowercaseTitle: String
    private var singer: String
    private var source: String = MusicService.SOURCE_INTERNAL
    private var podcastShowId: String? = null
    private var podcastEpisodeKey: String? = null
    private var startPositionMs: Int = 0

    constructor(songID: Long, songTitle: String, singer: String) {
        id = songID
        title = songTitle
        this.singer = singer
        lowercaseTitle = title.lowercase()
    }

    constructor(file: File) {
        var name = file.name
        val dot = name.lastIndexOf(".")
        if (dot != -1) {
            name = name.substring(0, dot)
        }

        title = name
        path = file.absolutePath
        id = -1
        singer = "Unknown"
        lowercaseTitle = title.lowercase()
    }

    constructor(
        title: String,
        singer: String,
        path: String,
        source: String,
        podcastShowId: String?,
        podcastEpisodeKey: String?,
        startPositionMs: Int
    ) {
        id = -1
        this.title = title
        this.singer = singer
        this.path = path
        this.source = source
        this.podcastShowId = podcastShowId
        this.podcastEpisodeKey = podcastEpisodeKey
        this.startPositionMs = startPositionMs
        lowercaseTitle = title.lowercase()
    }

    fun getID(): Long = id

    fun getTitle(): String = title

    fun getSinger(): String = singer

    fun getPath(): String? = path

    fun getSource(): String = source

    fun getPodcastShowId(): String? = podcastShowId

    fun getPodcastEpisodeKey(): String? = podcastEpisodeKey

    fun getStartPositionMs(): Int = startPositionMs

    override fun getLowercaseString(): String = lowercaseTitle

    override fun getString(): String = title
}
