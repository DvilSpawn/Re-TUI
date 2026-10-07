package ohi.andre.consolelauncher.help

internal class HelpNavigation(private val ids: List<String>, initialPage: Int = 0) {
    var page = initialPage.coerceIn(0, (ids.size - 1).coerceAtLeast(0) / 7)
        private set
    val previous get() = page > 0
    val next get() = (page + 1) * 7 < ids.size
    fun visible(): List<String> = ids.drop(page * 7).take(7)
    fun move(direction: Int) {
        if (direction < 0 && previous) page--
        if (direction > 0 && next) page++
    }
}
