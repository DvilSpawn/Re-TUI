package ohi.andre.consolelauncher.commands.main.raw

import ohi.andre.consolelauncher.R
import org.junit.Assert.assertEquals
import org.junit.Test

class FilesCommandTest {
    @Test
    fun usesPackagedOpenConsoleContract() {
        assertEquals("com.dvil.retui.fm", files.FM_PACKAGE)
        assertEquals("com.dvil.retui.fm.OPEN_CONSOLE", files.FM_ACTION)
    }

    @Test
    fun parsesNoArguments() {
        assertEquals(files.FilesRequest(), files.parseRequest(null))
    }

    @Test
    fun parsesStructuredSearch() {
        assertEquals(
            files.FilesRequest(action = "search", searchName = "note"),
            files.parseRequest("-search note")
        )
    }

    @Test
    fun parsesSearchWithType() {
        assertEquals(
            files.FilesRequest(action = "search", searchName = "note", searchType = "txt"),
            files.parseRequest("-search note txt")
        )
    }

    @Test
    fun opensRelativeFileFromSharedDirectory() {
        assertEquals(
            files.FilesRequest(action = "open", target = "photo.jpg"),
            files.parseRequest("-open photo.jpg")
        )
    }

    @Test
    fun preservesAbsoluteOpenTarget() {
        assertEquals(
            files.FilesRequest(action = "open", target = "/storage/emulated/0/Documents/photo.jpg"),
            files.parseRequest("-open /storage/emulated/0/Documents/photo.jpg")
        )
    }

    @Test
    fun preservesQuotedPathWithSpaces() {
        assertEquals(
            files.FilesRequest(action = "open", target = "My File.jpg"),
            files.parseRequest("-open \"My File.jpg\"")
        )
    }

    @Test
    fun parsesListActionAndSuggestionReplacements() {
        assertEquals(
            files.FilesRequest(action = "ls"),
            files.parseRequest("-ls")
        )
        assertEquals(
            files.FilesRequest(action = "share"),
            files.parseRequest("-share")
        )
        assertEquals(
            files.FilesRequest(action = "share", target = "photo.jpg"),
            files.parseRequest("-share photo.jpg")
        )
        assertEquals(files.FilesRequest(changeDirectory = "Download"), files.parseRequest("-cd Download"))
        val command = files()
        assertEquals(listOf("-open", "-ls", "-share", "-search", "-cd"), files.SUGGESTIONS.toList())
        assertEquals("files -ls", command.permanentSuggestionReplacement("-ls"))
        assertEquals("files -cd", command.permanentSuggestionReplacement("-cd"))
    }

    @Test
    fun rejectsMissingStructuredArguments() {
        assertEquals(
            R.string.files_usage_search,
            files.parseRequest("-search").error
        )
        assertEquals(
            R.string.files_usage_open,
            files.parseRequest("-open").error
        )
    }

    @Test
    fun rejectsRemovedLegacySearchShorthand() {
        assertEquals(R.string.files_unknown_option, files.parseRequest("note txt").error)
        assertEquals("note", files.parseRequest("note txt").errorArgument)
    }
}
