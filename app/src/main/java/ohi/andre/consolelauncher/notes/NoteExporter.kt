package ohi.andre.consolelauncher.notes


import ohi.andre.consolelauncher.R
import android.content.Context
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class NoteExporter(
    private val context: Context,
    private val revealRedactions: (String) -> String = { it }
) {
    private val exportRoot = File(context.cacheDir, "notes-exports")

    fun noteFiles(note: Note): List<File> {
        resetExports()
        val bundle = File(exportRoot, safeName(note.title.ifBlank { "untitled" })).apply { mkdirs() }
        val images = File(bundle, "images").apply { mkdirs() }
        val refs = mutableMapOf<String, String>()
        val copied = mutableListOf<File>()
        MarkdownDocument.imagePaths(MarkdownDocument.ensure(note)).forEach { path ->
            ownedImage(path)?.let { source ->
                val name = uniqueImageName(path, source)
                val target = File(images, name)
                source.copyTo(target)
                refs[path] = "images/$name"
                copied.add(target)
            }
        }
        val markdown = File(bundle, "${safeName(note.title.ifBlank { "untitled" })}.md")
        markdown.writeText(markdown(note, revealRedactions) { refs[it] })
        return listOf(markdown) + copied
    }

    fun folderZip(name: String, notes: List<Note>): File {
        resetExports()
        val rootName = safeName(name.ifBlank { "folder" })
        val zip = File(exportRoot, "$rootName.zip")
        ZipOutputStream(zip.outputStream().buffered()).use { output ->
            output.putNextEntry(ZipEntry("$rootName/"))
            output.closeEntry()
            notes.sortedBy { it.title.lowercase() }.forEach { note ->
                val noteName = "${safeName(note.title.ifBlank { "untitled" })}-${note.id.take(8)}"
                val base = "$rootName/$noteName"
                val refs = mutableMapOf<String, String>()
                MarkdownDocument.imagePaths(MarkdownDocument.ensure(note)).forEach { path ->
                    ownedImage(path)?.let { source ->
                        val imageName = uniqueImageName(path, source)
                        refs[path] = "images/$imageName"
                        output.putNextEntry(ZipEntry("$base/images/$imageName"))
                        source.inputStream().use { it.copyTo(output) }
                        output.closeEntry()
                    }
                }
                output.putNextEntry(ZipEntry("$base/$noteName.md"))
                output.write(markdown(note, revealRedactions) { refs[it] }.toByteArray(Charsets.UTF_8))
                output.closeEntry()
            }
        }
        return zip
    }

    fun clearExports() {
        if (exportRoot.exists()) exportRoot.deleteRecursively()
    }

    private fun resetExports() {
        clearExports()
        check(exportRoot.mkdirs()) { context.getString(R.string.notes_export_directory_failed) }
    }

    private fun ownedImage(relativePath: String): File? {
        val file = File(context.filesDir, relativePath).canonicalFile
        val roots = listOf(File(context.filesDir, "images"), File(context.filesDir, "notes-library/.assets")).map(File::getCanonicalFile)
        return file.takeIf { candidate -> candidate.isFile && roots.any { root -> candidate.path.startsWith(root.path + File.separator) } }
    }

    private fun uniqueImageName(path: String, source: File): String =
        "${path.hashCode().toUInt().toString(16)}-${safeName(source.name)}"

    companion object {
        fun safeName(raw: String): String = raw.trim()
            .replace(Regex("[^\\p{L}\\p{M}\\p{N}._-]+"), "_")
            .trim('_', '.')
            .take(80)
            .ifBlank { "untitled" }

        fun markdown(
            note: Note,
            revealRedactions: (String) -> String = { it },
            imagePath: (String) -> String? = { null }
        ): String = buildString {
            if (note.pinned) append("Pinned: yes\n\n")
            if (note.tags.isNotEmpty()) append("Tags: ").append(note.tags.joinToString(", ") { "#$it" }).append("\n\n")
            note.location?.let { append("Location: ").append(it.label).append(" (").append(it.latitude).append(", ").append(it.longitude).append(")\n\n") }
            append(revealRedactions(MarkdownDocument.rewriteImages(MarkdownDocument.ensure(note), imagePath)))
        }.trimEnd() + "\n"
    }
}
