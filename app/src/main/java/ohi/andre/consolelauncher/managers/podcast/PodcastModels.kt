package ohi.andre.consolelauncher.managers.podcast

data class PodcastShow(
    val id: String,
    val feedUrl: String,
    val title: String,
    val description: String,
    val imageUrl: String?,
    val episodes: List<PodcastEpisode>,
    val tags: List<String> = emptyList()
)

data class PodcastRecent(
    val show: PodcastShow,
    val episode: PodcastEpisode,
    val progressMs: Int,
    val updatedAtMs: Long
)

data class PodcastEpisode(
    val showId: String,
    val key: String,
    val title: String,
    val audioUrl: String,
    val link: String?,
    val description: String,
    val duration: String?,
    val publishedAt: Long?,
    val feedOrder: Int
)
