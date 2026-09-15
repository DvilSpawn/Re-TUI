package ohi.andre.consolelauncher.notes


import ohi.andre.consolelauncher.R
import java.util.UUID

const val DEFAULT_NOTES_FOLDER_ID = "00000000-0000-0000-0000-000000000001"

data class NoteFolder(
    val id: String = UUID.randomUUID().toString(),
    var name: String
)

data class NoteLibrary(
    val folders: MutableList<NoteFolder> = mutableListOf(),
    val notes: MutableList<Note> = mutableListOf()
) {
    fun ensureDefaultFolder(): NoteFolder = folders.firstOrNull { it.id == DEFAULT_NOTES_FOLDER_ID }
        ?: NoteFolder(DEFAULT_NOTES_FOLDER_ID, "notes").also(folders::add)

    fun deleteFolder(id: String) {
        notes.filter { it.folderId == id }.forEach { it.folderId = null }
        folders.removeAll { it.id == id }
    }

    companion object {
        fun fromLegacy(notes: MutableList<Note>): NoteLibrary = NoteLibrary(notes = notes).apply {
            val folder = ensureDefaultFolder()
            this.notes.forEach { it.folderId = folder.id }
        }
    }
}

data class Note(
    val id: String = UUID.randomUUID().toString(),
    val createdAt: Long = System.currentTimeMillis(),
    var updatedAt: Long = createdAt,
    var title: String = "",
    var markdown: String = "",
    val blocks: MutableList<Block> = mutableListOf(),
    var location: Place? = null,
    val tags: MutableList<String> = mutableListOf(),
    var pinned: Boolean = false,
    var locked: Boolean = false,
    var folderId: String? = null,
    var file: String? = null
)

sealed class Block(open val id: String = UUID.randomUUID().toString()) {
    data class Text(override val id: String = UUID.randomUUID().toString(), var text: String = "") : Block(id)
    data class Checklist(override val id: String = UUID.randomUUID().toString(), val items: MutableList<Item> = mutableListOf()) : Block(id)
    data class Image(override val id: String = UUID.randomUUID().toString(), var file: String, var alt: String = "") : Block(id)
}

data class Item(val id: String = UUID.randomUUID().toString(), var checked: Boolean = false, var text: String = "")
data class Place(val latitude: Double, val longitude: Double, val label: String)

fun Note.preview(): String = title.ifBlank { MarkdownDocument.plainText(markdown).take(80).ifBlank { "Untitled note" } }

fun Note.checkCounts(): Pair<Int, Int> = MarkdownDocument.checkCounts(markdown)

fun Note.hasMeaningfulContent(): Boolean = title.isNotBlank() || MarkdownDocument.plainText(markdown).isNotBlank() || tags.isNotEmpty() || location != null || pinned || blocks.any { block ->
    when (block) {
        is Block.Text -> block.text.isNotBlank()
        is Block.Checklist -> block.items.any { it.text.isNotBlank() }
        is Block.Image -> block.file.isNotBlank()
    }
}

fun Note.normalizeTags() {
    val cleaned = tags.map { it.trim().trimStart('#') }.filter { it.isNotEmpty() }
        .distinctBy { it.lowercase(java.util.Locale.ROOT) }
    tags.clear()
    tags.addAll(cleaned)
}

fun List<Note>.findTitleDuplicate(name: String): Note? =
    filter { it.title.trim().equals(name.trim(), ignoreCase = true) }.minByOrNull { it.createdAt }

object FolderRules {
    fun error(name: String, folders: List<NoteFolder>, excludingId: String? = null): Int? {
        val clean = name.trim()
        return when {
            clean.isEmpty() -> R.string.notes_folder_required
            clean.length > 80 -> R.string.notes_folder_long
            clean.any { it == '/' || it == '\\' || it.isISOControl() } -> R.string.notes_folder_invalid
            folders.any { it.id != excludingId && it.name.equals(clean, true) } -> R.string.notes_folder_duplicate
            else -> null
        }
    }
}
