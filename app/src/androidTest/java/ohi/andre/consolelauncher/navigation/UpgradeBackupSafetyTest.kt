package ohi.andre.consolelauncher.navigation

import android.net.Uri
import androidx.test.platform.app.InstrumentationRegistry
import ohi.andre.consolelauncher.managers.BackupManager
import ohi.andre.consolelauncher.managers.lua.LuaWidgetManager
import ohi.andre.consolelauncher.managers.modules.ModuleManager
import ohi.andre.consolelauncher.managers.settings.LauncherSettings
import ohi.andre.consolelauncher.managers.xml.XMLPrefsManager
import ohi.andre.consolelauncher.managers.xml.options.Ui
import ohi.andre.consolelauncher.notes.Note
import ohi.andre.consolelauncher.notes.NoteStore
import ohi.andre.consolelauncher.tuils.Tuils
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID

/** Uses actual document export/import only in the disposable upgrade-check application. */
class UpgradeBackupSafetyTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun load() {
        Tuils.init(context)
        XMLPrefsManager.dispose()
        XMLPrefsManager.loadCommons(context)
        LauncherSettings.refreshFromLoadedPrefs()
    }
    private fun withOriginalState(test: (File) -> Unit) {
        org.junit.Assume.assumeTrue("Backup probes require the disposable upgrade-check package", context.packageName.endsWith(".upgradecheck"))
        load()
        val directory = File(context.cacheDir, "backup-safety-${UUID.randomUUID()}").apply { check(mkdirs()) }
        val original = File(directory, "original.retui-backup")
        BackupManager.exportBackup(context, Uri.fromFile(original))
        try { test(directory) } finally {
            try { BackupManager.importBackup(context, Uri.fromFile(original)); load() }
            finally { directory.deleteRecursively() }
        }
    }
    private data class Fixture(val id: String, val noteId: String, val files: Map<File, ByteArray>, val dock: List<String?>)
    private fun seed(): Fixture {
        val id = "backup_" + UUID.randomUUID().toString().replace("-", "").take(12)
        LauncherSettings.set(context, Ui.input_output_size, "27")
        LuaWidgetManager.save(id, "Backup sentinel", """-- name = "Backup sentinel"
-- type = "module"
-- retui = "1"
function on_resume() ui:show_text("$id") end
""")
        val widget = LuaWidgetManager.widgetDir(id)
        val prefs = File(widget, "prefs.json").apply { writeText("""{"counter":83,"label":"$id"}""") }
        val data = File(widget, "files/user-sentinel.txt").apply { parentFile!!.mkdirs(); writeText("module data: $id") }
        ModuleManager.setScriptModule(context, id, "lua:$id")
        val dock: List<String?> = listOf("timer", id, "notes")
        ModuleManager.setDock(context, dock.toMutableList())
        val provenance = context.getSharedPreferences("retui_module_library_installs", 0)
        check(provenance.edit().putString("$id.hash", "sentinel-hash-$id").putString("$id.version", "3.2").commit())
        val store = NoteStore(context)
        val library = store.load()
        val note = Note(title = "Backup sentinel $id", markdown = "# Preserved note\n\n$id", folderId = library.ensureDefaultFolder().id)
        note.markdown += "\n\n![Backup attachment](notes-library/.assets/${note.id}/sentinel.bin)"
        library.notes.add(note)
        store.save(library)
        val attachment = File(context.filesDir, "notes-library/.assets/${note.id}/sentinel.bin").apply {
            parentFile!!.mkdirs(); writeBytes(byteArrayOf(0, 1, 2, 3, 127, -1))
        }
        val user = File(Tuils.getFolder(), "$id-user.txt").apply { writeText("unrelated root file: $id") }
        val watched = listOf(File(Tuils.getFolder(), "ui.xml"), LuaWidgetManager.scriptFile(id), prefs, data,
            File(context.filesDir, "remember-notes.xml"), File(context.filesDir, note.file!!), attachment, user)
        return Fixture(id, note.id, watched.associateWith { it.readBytes() }, dock)
    }
    private fun assertFixture(fixture: Fixture) {
        fixture.files.forEach { (file, bytes) ->
            assertTrue("Restored ${file.path}", file.isFile)
            assertArrayEquals("Exact bytes: ${file.path}", bytes, file.readBytes())
        }
        assertEquals("Exact dock order", fixture.dock, ModuleManager.getDock(context))
        assertEquals("lua:${fixture.id}", ModuleManager.getModuleSource(context, fixture.id))
        val provenance = context.getSharedPreferences("retui_module_library_installs", 0)
        assertEquals("sentinel-hash-${fixture.id}", provenance.getString("${fixture.id}.hash", null))
        assertEquals("3.2", provenance.getString("${fixture.id}.version", null))
        assertTrue(NoteStore(context).load().notes.any { it.id == fixture.noteId && it.markdown.contains(fixture.id) })
    }

    @Test fun personalBackupRoundTripsSettingsModulesNotesAndInstallProvenance() = withOriginalState { directory ->
        val fixture = seed()
        val backup = File(directory, "roundtrip.retui-backup")
        BackupManager.exportBackup(context, Uri.fromFile(backup))
        assertTrue(backup.length() > 0)
        fixture.files.keys.forEach { it.writeText("changed after export") }
        ModuleManager.setDock(context, mutableListOf("notes", "timer"))
        check(context.getSharedPreferences("retui_module_library_installs", 0).edit()
            .remove("${fixture.id}.hash").remove("${fixture.id}.version").commit())
        assertNull(BackupManager.importBackup(context, Uri.fromFile(backup)))
        load()
        assertFixture(fixture)
        assertEquals("27", LauncherSettings.get(Ui.input_output_size))
    }

    @Test fun malformedAndTruncatedBackupsCannotReplaceCurrentSentinels() = withOriginalState { directory ->
        val fixture = seed()
        val valid = File(directory, "valid.retui-backup")
        BackupManager.exportBackup(context, Uri.fromFile(valid))
        val attempts = listOf("not a backup".toByteArray(), valid.readBytes().copyOf(24))
        attempts.forEachIndexed { index, bytes ->
            val broken = File(directory, "broken-$index.retui-backup").apply { writeBytes(bytes) }
            assertTrue("Invalid archive must be rejected", runCatching {
                BackupManager.importBackup(context, Uri.fromFile(broken))
            }.isFailure)
            assertFixture(fixture)
            assertFalse("Failed extraction must be cleaned", File(Tuils.getFolder(), ".restore-importing").exists())
        }
    }
}
