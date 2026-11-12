package ohi.andre.consolelauncher.commands

import ohi.andre.consolelauncher.SourceFiles
import org.junit.Assert.assertTrue
import org.junit.Test

class BareParamCommandTest {
    @Test fun aParamCommandWithNothingTypedShowsItsHelp() {
        val source = SourceFiles.kotlin("Command.kt").readText()
        val invalid = source.indexOf("R.string.output_invalid_param")
        val blankCheck = source.indexOf("isNullOrBlank()) return command.onNotArgEnough(info, 0)")
        assertTrue("Blank input must be handled before the invalid-option message", blankCheck in 0 until invalid)
    }
}
