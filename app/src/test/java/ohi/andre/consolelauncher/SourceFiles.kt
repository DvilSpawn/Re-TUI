package ohi.andre.consolelauncher

import java.io.File

/** Locates repository files for source-text tests, so moving a Kotlin file needs no test edits. */
internal object SourceFiles {
    private val root: File by lazy {
        generateSequence(File(checkNotNull(System.getProperty("user.dir"))).absoluteFile) { it.parentFile }
            .first { File(it, "app/src/main/java").isDirectory }
    }
    private val kotlinSources: Map<String, List<File>> by lazy {
        File(root, "app/src/main/java").walkTopDown().filter { it.isFile && it.extension == "kt" }.groupBy { it.name }
    }

    /** The Kotlin source named [fileName] anywhere under app/src/main/java; fails unless exactly one matches. */
    @JvmStatic fun kotlin(fileName: String): File {
        val matches = kotlinSources[fileName].orEmpty()
        check(matches.size == 1) { "Expected exactly one $fileName under app/src/main/java, found ${matches.size}: $matches" }
        return matches.single()
    }

    /** The file at [path] relative to the repository root, which need not exist. */
    @JvmStatic fun repo(path: String): File = File(root, path)
}
