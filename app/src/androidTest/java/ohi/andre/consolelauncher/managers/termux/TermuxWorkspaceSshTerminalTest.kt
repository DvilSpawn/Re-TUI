package ohi.andre.consolelauncher.managers.termux

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TermuxWorkspaceSshTerminalTest {
    @Test fun explicitBackgroundDoesNotClipWideGlyphs() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            fun render(prefix: String): android.graphics.Bitmap {
                val grid = TerminalGridView(instrumentation.targetContext)
                grid.setTerminalTextSizeSp(22f)
                val terminal = TermuxWorkspaceTerminal(10, 4, grid) {}
                terminal.append(("\u001b[?25l" + prefix + "界😀").toByteArray(Charsets.UTF_8))
                grid.setEmulatorFrame(terminal.emulator)
                val bitmap = android.graphics.Bitmap.createBitmap(kotlin.math.ceil(grid.characterWidth() * 10).toInt(), grid.lineHeight() * 4, android.graphics.Bitmap.Config.ARGB_8888)
                bitmap.eraseColor(android.graphics.Color.BLACK)
                grid.layout(0, 0, bitmap.width, bitmap.height)
                grid.draw(android.graphics.Canvas(bitmap))
                return bitmap
            }
            val defaultBackground = render("")
            val explicitBackground = render("\u001b[48;2;0;0;0m")
            try { assertTrue("A wide glyph must look identical with the same explicit background", defaultBackground.sameAs(explicitBackground)) }
            finally { defaultBackground.recycle(); explicitBackground.recycle() }
        }
    }

    @Test fun streamingRetainsUtf8StateAndRespondsToTerminalQueries() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val replies = mutableListOf<ByteArray>()
            val terminal = TermuxWorkspaceTerminal(80, 24, TerminalGridView(instrumentation.targetContext)) { replies.add(it) }
            val bytes = "héllo 世界 e\u0301 😀".toByteArray(Charsets.UTF_8)
            bytes.forEach { terminal.append(byteArrayOf(it)) }
            assertTrue(terminal.text().contains("héllo 世界 e\u0301 😀"))
            terminal.append("\u001b[6n".toByteArray())
            assertTrue(replies.any { String(it).startsWith("\u001b[") && String(it).endsWith("R") })
            assertEquals("\u001b[A", String(terminal.key("Up")))
            terminal.append("\u001b[?1h".toByteArray())
            assertEquals("\u001bOA", String(terminal.key("Up")))
            assertEquals("\u0003", String(terminal.key("C-c")))
            assertEquals("\u001bx", String(terminal.key("M-x")))
            assertEquals("-", String(terminal.key("-")))
            assertEquals("\u001f", String(terminal.key("C-/")))
            assertEquals("\u001e", String(terminal.key("C-6")))
            assertEquals("\u007f", String(terminal.key("C-8")))
            terminal.append("\u001b[?1049hEDITOR".toByteArray())
            assertTrue(terminal.emulator.isAlternateBufferActive)
            assertTrue(terminal.text().contains("EDITOR"))
            terminal.append("\u001b[?1049l".toByteArray())
            assertTrue(terminal.text().contains("héllo 世界"))
            replies.clear()
            terminal.append("\u001b[?2004h".toByteArray())
            terminal.paste("first\nsecond")
            assertEquals("Paste delimiters and body must be one queued input", 1, replies.size)
            assertEquals("\u001b[200~first\rsecond\u001b[201~", replies.joinToString("") { String(it, Charsets.UTF_8) })
            terminal.resize(40, 12, TerminalGridView(instrumentation.targetContext))
            assertEquals(40, terminal.emulator.mColumns)
            assertEquals(12, terminal.emulator.mRows)
            assertTrue(terminal.text().contains("héllo 世界"))
        }
    }
}
