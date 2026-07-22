package ohi.andre.consolelauncher.commands.main.raw

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

class FilesCommandTest {
    private val currentDirectory = File("/storage/emulated/0")

    @Test
    fun parsesNoArguments() {
        assertEquals(files.FilesRequest(), files.parseRequest(null, currentDirectory))
    }

    @Test
    fun parsesStructuredSearch() {
        assertEquals(
            files.FilesRequest(action = "search", searchName = "note"),
            files.parseRequest("-search note", currentDirectory)
        )
    }

    @Test
    fun parsesSearchWithType() {
        assertEquals(
            files.FilesRequest(action = "search", searchName = "note", searchType = "txt"),
            files.parseRequest("-search note txt", currentDirectory)
        )
    }

    @Test
    fun resolvesRelativeOpenPath() {
        assertEquals(
            files.FilesRequest(action = "open", path = "/storage/emulated/0/Download"),
            files.parseRequest("-open Download", currentDirectory)
        )
    }

    @Test
    fun preservesAbsoluteOpenPath() {
        assertEquals(
            files.FilesRequest(action = "open", path = "/storage/emulated/0/Documents"),
            files.parseRequest("-open /storage/emulated/0/Documents", currentDirectory)
        )
    }

    @Test
    fun preservesQuotedPathWithSpaces() {
        assertEquals(
            files.FilesRequest(action = "open", path = "/storage/emulated/0/Download/My Folder"),
            files.parseRequest("-open \"Download/My Folder\"", currentDirectory)
        )
    }

    @Test
    fun rejectsMissingStructuredArguments() {
        assertEquals(
            "Usage: files -search <name> [type]",
            files.parseRequest("-search", currentDirectory).error
        )
        assertEquals(
            "Usage: files -open <directory>",
            files.parseRequest("-open", currentDirectory).error
        )
    }

    @Test
    fun keepsLegacySearchShorthand() {
        val request = files.parseRequest("note txt", currentDirectory)
        assertEquals("search", request.action)
        assertEquals("note", request.searchName)
        assertEquals("txt", request.searchType)
        assertNull(request.error)
    }
}
