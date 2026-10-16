package ohi.andre.consolelauncher.managers.termux

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Uses isolated preference files; never changes the user's saved launchers. */
@RunWith(AndroidJUnit4::class)
class TermuxWorkspaceLauncherMigrationTest {
    private val context = object : ContextWrapper(InstrumentationRegistry.getInstrumentation().targetContext) {
        override fun getApplicationContext(): Context = this
        override fun getSharedPreferences(name: String, mode: Int): SharedPreferences =
            baseContext.getSharedPreferences("registry_migration_test_$name", mode)
    }
    private fun store(name: String) = context.getSharedPreferences(name, Context.MODE_PRIVATE)
    @Before fun prepare() = clear()
    @After fun clear() {
        store(TermuxAppManager.PREFS).edit().clear().commit()
        store(TermuxWorkspaceLauncherManager.PREFS).edit().clear().commit()
    }

    @Test fun migratesTitleCommandAndDirectoryOnceWithoutDeletingLegacyData() {
        assertTrue(TermuxAppManager.add(context, "worknotes", "My notes", "nvim inbox.md", "~/my notes"))
        assertTrue(TermuxAppManager.addAction(context, "worknotes", "Save", ":w"))
        val oldValues = store(TermuxAppManager.PREFS).all
        val migrated = TermuxWorkspaceLauncherManager.resolve(context, "worknotes")!!
        assertEquals("My notes", migrated.title)
        assertEquals("nvim inbox.md", migrated.command)
        assertEquals("~/my notes", migrated.workDir)
        assertEquals("cd -- \"\$HOME\"/'my notes' && {\nnvim inbox.md\n}", migrated.launchCommand)
        assertEquals(oldValues, store(TermuxAppManager.PREFS).all)
        assertTrue(TermuxWorkspaceLauncherManager.remove(context, "worknotes"))
        assertNull(TermuxWorkspaceLauncherManager.resolve(context, "worknotes"))
        assertEquals(oldValues, store(TermuxAppManager.PREFS).all)
    }

    @Test fun existingWorkspaceDefinitionWinsAndAddRemoveUsesSameStore() {
        assertTrue(TermuxAppManager.add(context, "worknotes", "Old notes", "old-editor", "/old"))
        store(TermuxWorkspaceLauncherManager.PREFS).edit()
            .putStringSet("ids", setOf("worknotes")).putString("command_worknotes", "new-editor").commit()
        assertEquals("new-editor", TermuxWorkspaceLauncherManager.resolve(context, "worknotes")!!.command)
        assertTrue(TermuxWorkspaceLauncherManager.save(context, "radio", "play radio.mp3"))
        assertEquals("play radio.mp3", TermuxWorkspaceLauncherManager.resolve(context, "radio")!!.command)
        assertTrue(TermuxWorkspaceLauncherManager.remove(context, "radio"))
        assertNull(TermuxWorkspaceLauncherManager.resolve(context, "radio"))
        assertFalse(TermuxWorkspaceLauncherManager.remove(context, "shell"))
    }

    @Test fun legacyNameMatchingBuiltInSurvivesUnderExplicitLegacyName() {
        assertTrue(TermuxAppManager.add(context, "shell", "My terminal", "bash custom.sh", "/old"))
        assertTrue(TermuxWorkspaceLauncherManager.resolve(context, "shell")!!.builtIn)
        assertEquals("bash custom.sh", TermuxWorkspaceLauncherManager.resolve(context, "shell-legacy")!!.command)
        assertNotNull(TermuxAppManager.resolve(context, "shell"))
    }
}
