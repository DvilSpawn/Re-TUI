package ohi.andre.consolelauncher.managers.suggestions

import it.andreuzzi.comparestring2.AlgMap.Alg
import it.andreuzzi.comparestring2.CompareStrings
import it.andreuzzi.comparestring2.algs.interfaces.Algorithm
import ohi.andre.consolelauncher.commands.main.MainPack
import ohi.andre.consolelauncher.commands.main.raw.tbridge
import ohi.andre.consolelauncher.managers.file.FileManager.cd
import ohi.andre.consolelauncher.managers.file.RetuiFilesContract
import ohi.andre.consolelauncher.managers.suggestions.SuggestionsManager.Companion.DOUBLE_QUOTES
import ohi.andre.consolelauncher.managers.suggestions.SuggestionsManager.Companion.SINGLE_QUOTE
import ohi.andre.consolelauncher.managers.suggestions.SuggestionsManager.Suggestion
import ohi.andre.consolelauncher.managers.termux.TermuxBridgeCache.dirs
import ohi.andre.consolelauncher.managers.termux.TermuxBridgeCache.files
import ohi.andre.consolelauncher.managers.termux.TermuxBridgeCache.shouldRequest
import ohi.andre.consolelauncher.managers.termux.TermuxBridgeManager
import ohi.andre.consolelauncher.managers.termux.TermuxBridgeManager.dispatchShell
import ohi.andre.consolelauncher.tuils.Tuils
import java.io.File
import java.io.FilenameFilter
import java.util.Arrays
import java.util.Locale
import java.util.regex.Pattern
import kotlin.math.abs
import kotlin.math.max

/** File and directory suggestions for [SuggestionsManager]; the manager supplies its live settings. */
internal class SuggestionFileProvider(
    private val rmQuotes: Pattern,
    private val perCategory: () -> Int,
    private val deadline: () -> Float,
    private val algorithm: () -> Algorithm?,
    private val algorithmType: () -> Alg?,
    private val quickCompare: (String, Array<String>, MutableList<Suggestion?>, String?, Int, Boolean, Int, Any?) -> Int
) {
    private val FILE_SPLITTERS = arrayOf<String?>(Tuils.SPACE, "-", "_")
    private val suggestionsPerCategory: Int get() = perCategory()
    private val suggestionsDeadline: Float get() = deadline()
    private val algInstance: Algorithm? get() = algorithm()
    private val alg: Alg? get() = algorithmType()

    fun suggestFile(
        info: MainPack,
        suggestions: MutableList<Suggestion?>,
        afterLastSpace: String?,
        beforeLastSpace: String?
    ) {
        var afterLastSpace = afterLastSpace
        if (isCdCommand(beforeLastSpace)) {
            suggestDirectory(info, suggestions, afterLastSpace, beforeLastSpace)
            return
        }
        if (isFileOpenCommand(beforeLastSpace)) {
            suggestOpenableFile(info, suggestions, afterLastSpace, beforeLastSpace)
            return
        }

        val noAfterLastSpace = afterLastSpace == null || afterLastSpace.length == 0
        val afterLastSpaceNotEndsWithSeparator =
            noAfterLastSpace || !afterLastSpace.endsWith(File.separator)

        if (noAfterLastSpace || afterLastSpaceNotEndsWithSeparator) {
            suggestions.add(
                Suggestion(
                    beforeLastSpace,
                    File.separator,
                    false,
                    Suggestion.Companion.TYPE_FILE,
                    afterLastSpace
                )
            )
        }

        if (Suggestion.Companion.appendQuotesBeforeFile && !noAfterLastSpace && !afterLastSpace.endsWith(
                SINGLE_QUOTE
            ) && !afterLastSpace.endsWith(DOUBLE_QUOTES)
        ) suggestions.add(
            Suggestion(
                beforeLastSpace,
                SINGLE_QUOTE,
                false,
                Suggestion.Companion.TYPE_FILE,
                afterLastSpace
            )
        )

        if (noAfterLastSpace) {
            suggestFilesInDir(null, suggestions, info.currentDirectory, beforeLastSpace)
            return
        }

        if (!afterLastSpace.contains(File.separator)) {
            suggestFilesInDir(
                suggestions,
                info.currentDirectory,
                afterLastSpace,
                beforeLastSpace,
                null
            )
        } else {
            //            if it's ../../

            if (!afterLastSpaceNotEndsWithSeparator) {
                val total = beforeLastSpace + Tuils.SPACE + afterLastSpace
                val quotesCount: Int =
                    total.length - total.replace(DOUBLE_QUOTES, Tuils.EMPTYSTRING).replace(
                        SINGLE_QUOTE, Tuils.EMPTYSTRING
                    ).length

                if (quotesCount > 0) {
                    val singleQIndex: Int = total.lastIndexOf(SINGLE_QUOTE)
                    val doubleQIndex: Int = total.lastIndexOf(DOUBLE_QUOTES)

                    val lastQuote = max(singleQIndex, doubleQIndex)

                    val file = total.substring(lastQuote + abs(quotesCount % 2 - 2))
                    val dirInfo = cd(info.currentDirectory, file)
                    suggestFilesInDir(afterLastSpace, suggestions, dirInfo.file, beforeLastSpace)
                } else {
//                    removes the /
                    afterLastSpace = afterLastSpace.substring(0, afterLastSpace.length - 1)
                    val dirInfo = cd(info.currentDirectory, afterLastSpace)
                    suggestFilesInDir(
                        afterLastSpace + File.separator,
                        suggestions,
                        dirInfo.file,
                        beforeLastSpace
                    )
                }
            } else {
                val originalAfterLastSpace: String? = afterLastSpace
                afterLastSpace = rmQuotes.matcher(afterLastSpace).replaceAll(Tuils.EMPTYSTRING)

                val index = afterLastSpace.lastIndexOf(File.separator)
                val dirInfo = cd(info.currentDirectory, afterLastSpace.substring(0, index))

                val originalIndex = originalAfterLastSpace!!.lastIndexOf(File.separator)

                val alsals = originalAfterLastSpace.substring(0, originalIndex + 1)
                val als = originalAfterLastSpace.substring(originalIndex + 1)

                //                beforeLastSpace  = beforeLastSpace + Tuils.SPACE + hold;
                suggestFilesInDir(suggestions, dirInfo.file, als, beforeLastSpace, alsals)
            }
        }
    }

    fun isCdCommand(beforeLastSpace: String?): Boolean {
        if (beforeLastSpace == null) {
            return false
        }
        return "cd".equals(beforeLastSpace.trim { it <= ' ' }, ignoreCase = true)
    }

    fun isFileOpenCommand(beforeLastSpace: String?): Boolean {
        if (beforeLastSpace == null) {
            return false
        }
        val command = beforeLastSpace.trim { it <= ' ' }.lowercase(Locale.getDefault())
        return "open" == command || "termux-open" == command || "share" == command
    }

    private fun suggestOpenableFile(
        info: MainPack,
        suggestions: MutableList<Suggestion?>,
        afterLastSpace: String?,
        beforeLastSpace: String?
    ) {
        if (beforeLastSpace?.trim()?.equals("termux-open", ignoreCase = true) == true) {
            suggestTermuxFiles(info, suggestions, afterLastSpace, beforeLastSpace)
            return
        }
        suggestRetuiFiles(info, suggestions, afterLastSpace, beforeLastSpace, false)
    }

    private fun suggestDirectory(
        info: MainPack,
        suggestions: MutableList<Suggestion?>,
        afterLastSpace: String?,
        beforeLastSpace: String?
    ) {
        suggestRetuiFiles(info, suggestions, afterLastSpace, beforeLastSpace, true)
    }

    fun suggestRetuiFiles(
        info: MainPack,
        suggestions: MutableList<Suggestion?>,
        prefix: String?,
        command: String?,
        directoriesOnly: Boolean
    ) {
        RetuiFilesContract.entries(info.context, directoriesOnly, prefix).forEach { entry ->
            suggestions.add(
                Suggestion(command, entry.name, false, Suggestion.Companion.TYPE_FILE, prefix)
            )
        }
    }

    private fun suggestTermuxFiles(
        info: MainPack,
        suggestions: MutableList<Suggestion?>,
        afterLastSpace: String?,
        beforeLastSpace: String?
    ) {
        val target = termuxTarget(info.currentDirectory, afterLastSpace)
        requestTermuxListing(info, "files", target.dir)
        addTermuxMatches(
            files(target.dir),
            target.leaf,
            suggestions,
            beforeLastSpace,
            target.holder
        )
    }

    private fun requestTermuxListing(info: MainPack, type: String, path: String) {
        if (!shouldRequest(type, path)) {
            return
        }
        val script = if ("dirs" == type) tbridge.LIST_DIRS_SCRIPT else tbridge.LIST_FILES_SCRIPT
        dispatchShell(
            info.context,
            type + " " + path,
            script,
            TermuxBridgeManager.TERMUX_HOME,
            path
        )
    }

    private fun termuxTarget(
        currentDirectory: File,
        afterLastSpace: String?
    ): TermuxSuggestionTarget {
        if (afterLastSpace == null || afterLastSpace.trim { it <= ' ' }.length == 0) {
            return TermuxSuggestionTarget(
                currentDirectory.getAbsolutePath(),
                Tuils.EMPTYSTRING,
                null
            )
        }

        val original: String? = afterLastSpace
        val clean = rmQuotes.matcher(afterLastSpace).replaceAll(Tuils.EMPTYSTRING)
        if (clean.endsWith(File.separator)) {
            val dir =
                if (clean.startsWith(File.separator)) File(clean) else File(currentDirectory, clean)
            return TermuxSuggestionTarget(dir.getAbsolutePath(), Tuils.EMPTYSTRING, original)
        }

        val index = clean.lastIndexOf(File.separator)
        if (index < 0) {
            return TermuxSuggestionTarget(currentDirectory.getAbsolutePath(), clean, null)
        }

        val base = clean.substring(0, index)
        val holder = original!!.substring(0, original.lastIndexOf(File.separator) + 1)
        val leaf = clean.substring(index + 1)
        val dir = if (base.startsWith(File.separator)) File(base) else File(currentDirectory, base)
        return TermuxSuggestionTarget(dir.getAbsolutePath(), leaf, holder)
    }

    private fun addTermuxMatches(
        values: List<String>,
        leaf: String?,
        suggestions: MutableList<Suggestion?>,
        beforeLastSpace: String?,
        holder: String?
    ) {
        val temp = if (leaf == null) Tuils.EMPTYSTRING else leaf
        val counter = quickCompare(
            temp,
            values.toTypedArray(),
            suggestions,
            beforeLastSpace,
            suggestionsPerCategory,
            false,
            Suggestion.Companion.TYPE_FILE,
            false
        )
        if (suggestionsPerCategory - counter <= 0) {
            return
        }
        val matches = fuzzyMatchesOrNone { CompareStrings.topMatchesWithDeadline(
            temp,
            values.toTypedArray(),
            suggestionsPerCategory - counter,
            suggestionsDeadline,
            FILE_SPLITTERS,
            algInstance,
            alg
        ) }
        for (match in matches) {
            suggestions.add(
                Suggestion(
                    beforeLastSpace,
                    match,
                    false,
                    Suggestion.Companion.TYPE_FILE,
                    holder
                )
            )
        }
    }

    private class TermuxSuggestionTarget(val dir: String, val leaf: String?, val holder: String?)

    private fun suggestFilesInDir(
        suggestions: MutableList<Suggestion?>,
        dir: File?,
        afterLastSeparator: String?,
        beforeLastSpace: String?,
        afterLastSpaceWithoutALS: String?
    ) {
        if (dir == null || !dir.isDirectory()) return

        if (afterLastSeparator == null || afterLastSeparator.length == 0) {
            suggestFilesInDir(null, suggestions, dir, beforeLastSpace)
            return
        }

        val files = dir.list()
        if (files == null) {
            return
        }

        //        Tuils.log("bls", beforeLastSpace);
//        Tuils.log("als", afterLastSeparator);
//        Tuils.log("alsals", afterLastSpaceWithoutALS);
        val temp = rmQuotes.matcher(afterLastSeparator).replaceAll(Tuils.EMPTYSTRING)

        val counter = quickCompare(
            temp,
            files,
            suggestions,
            beforeLastSpace,
            suggestionsPerCategory,
            false,
            Suggestion.Companion.TYPE_FILE,
            false
        )
        if (suggestionsPerCategory - counter <= 0) return

        val fs = fuzzyMatchesOrNone { CompareStrings.topMatchesWithDeadline(
            temp,
            files,
            suggestionsPerCategory - counter,
            suggestionsDeadline,
            FILE_SPLITTERS,
            algInstance,
            alg
        ) }
        for (f in fs) {
            suggestions.add(
                Suggestion(
                    beforeLastSpace,
                    f,
                    false,
                    Suggestion.Companion.TYPE_FILE,
                    afterLastSpaceWithoutALS
                )
            )
        }
    }

    private fun suggestDirsInDir(
        suggestions: MutableList<Suggestion?>,
        dir: File?,
        afterLastSeparator: String?,
        beforeLastSpace: String?,
        afterLastSpaceWithoutALS: String?
    ) {
        if (dir == null || !dir.isDirectory()) return

        if (afterLastSeparator == null || afterLastSeparator.length == 0) {
            suggestDirsInDir(null, suggestions, dir, beforeLastSpace)
            return
        }

        val dirs = dir.list(FilenameFilter { current: File?, name: String? ->
            File(
                current,
                name
            ).isDirectory()
        })
        if (dirs == null) {
            return
        }

        val temp = rmQuotes.matcher(afterLastSeparator).replaceAll(Tuils.EMPTYSTRING)
        val counter = quickCompare(
            temp,
            dirs,
            suggestions,
            beforeLastSpace,
            suggestionsPerCategory,
            false,
            Suggestion.Companion.TYPE_FILE,
            false
        )
        if (suggestionsPerCategory - counter <= 0) return

        val matches = fuzzyMatchesOrNone { CompareStrings.topMatchesWithDeadline(
            temp,
            dirs,
            suggestionsPerCategory - counter,
            suggestionsDeadline,
            FILE_SPLITTERS,
            algInstance,
            alg
        ) }
        for (match in matches) {
            suggestions.add(
                Suggestion(
                    beforeLastSpace,
                    match,
                    false,
                    Suggestion.Companion.TYPE_FILE,
                    afterLastSpaceWithoutALS
                )
            )
        }
    }

    private fun suggestFilesOnlyInDir(
        suggestions: MutableList<Suggestion?>,
        dir: File?,
        afterLastSeparator: String?,
        beforeLastSpace: String?,
        afterLastSpaceWithoutALS: String?
    ) {
        if (dir == null || !dir.isDirectory()) return

        if (afterLastSeparator == null || afterLastSeparator.length == 0) {
            suggestFilesOnlyInDir(null, suggestions, dir, beforeLastSpace)
            return
        }

        val files = dir.list(FilenameFilter { current: File?, name: String? ->
            File(
                current,
                name
            ).isFile()
        })
        if (files == null) {
            return
        }

        val temp = rmQuotes.matcher(afterLastSeparator).replaceAll(Tuils.EMPTYSTRING)
        val counter = quickCompare(
            temp,
            files,
            suggestions,
            beforeLastSpace,
            suggestionsPerCategory,
            false,
            Suggestion.Companion.TYPE_FILE,
            false
        )
        if (suggestionsPerCategory - counter <= 0) return

        val matches = fuzzyMatchesOrNone { CompareStrings.topMatchesWithDeadline(
            temp,
            files,
            suggestionsPerCategory - counter,
            suggestionsDeadline,
            FILE_SPLITTERS,
            algInstance,
            alg
        ) }
        for (match in matches) {
            suggestions.add(
                Suggestion(
                    beforeLastSpace,
                    match,
                    false,
                    Suggestion.Companion.TYPE_FILE,
                    afterLastSpaceWithoutALS
                )
            )
        }
    }

    private fun suggestFilesInDir(
        afterLastSpaceHolder: String?,
        suggestions: MutableList<Suggestion?>,
        dir: File?,
        beforeLastSpace: String?
    ) {
        if (dir == null || !dir.isDirectory()) {
            return
        }

        try {
            val files = dir.list()
            if (files == null) {
                return
            }
            Arrays.sort(files)
            for (s in files) {
                suggestions.add(
                    Suggestion(
                        beforeLastSpace,
                        s,
                        false,
                        Suggestion.Companion.TYPE_FILE,
                        afterLastSpaceHolder
                    )
                )
            }
        } catch (e: NullPointerException) {
            Tuils.log(e)
        }
    }

    private fun suggestDirsInDir(
        afterLastSpaceHolder: String?,
        suggestions: MutableList<Suggestion?>,
        dir: File?,
        beforeLastSpace: String?
    ) {
        if (dir == null || !dir.isDirectory()) {
            return
        }

        try {
            val dirs = dir.list(FilenameFilter { current: File?, name: String? ->
                File(
                    current,
                    name
                ).isDirectory()
            })
            if (dirs == null) {
                return
            }
            Arrays.sort(dirs)
            for (s in dirs) {
                suggestions.add(
                    Suggestion(
                        beforeLastSpace,
                        s,
                        false,
                        Suggestion.Companion.TYPE_FILE,
                        afterLastSpaceHolder
                    )
                )
            }
        } catch (e: NullPointerException) {
            Tuils.log(e)
        }
    }

    private fun suggestFilesOnlyInDir(
        afterLastSpaceHolder: String?,
        suggestions: MutableList<Suggestion?>,
        dir: File?,
        beforeLastSpace: String?
    ) {
        if (dir == null || !dir.isDirectory()) {
            return
        }

        try {
            val files = dir.list(FilenameFilter { current: File?, name: String? ->
                File(
                    current,
                    name
                ).isFile()
            })
            if (files == null) {
                return
            }
            Arrays.sort(files)
            for (s in files) {
                suggestions.add(
                    Suggestion(
                        beforeLastSpace,
                        s,
                        false,
                        Suggestion.Companion.TYPE_FILE,
                        afterLastSpaceHolder
                    )
                )
            }
        } catch (e: NullPointerException) {
            Tuils.log(e)
        }
    }
}
