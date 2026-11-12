package ohi.andre.consolelauncher.managers.modes

/** Search mode: the search bar layout on its own. Everything it shows is the shared [SearchSurface]. */
internal class SearchHomeMode : HomeMode {
    override val usesSearchLayout get() = true
    override val focusesSearchInput get() = true
}
