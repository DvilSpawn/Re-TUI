package ohi.andre.consolelauncher.managers.suggestions

/**
 * Runs a CompareString2 search. Its internal sort can throw "Comparison method violates its
 * general contract!" on some inputs, and a suggestion is never worth crashing over, so that
 * search then yields no fuzzy results.
 */
internal inline fun <reified T> fuzzyMatchesOrNone(search: () -> Array<T>): Array<T> =
    try {
        search()
    } catch (_: IllegalArgumentException) {
        emptyArray()
    }
