package ohi.andre.consolelauncher.managers.status

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import ohi.andre.consolelauncher.managers.xml.XMLPrefsManager
import ohi.andre.consolelauncher.managers.xml.options.Behavior
import ohi.andre.consolelauncher.managers.xml.options.Theme
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UnlockManagerIntegrationTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    private fun withManager(onMain: Boolean = true, block: (UnlockManager, android.content.SharedPreferences) -> Unit) {
        val context = instrumentation.targetContext
        val prefs = context.getSharedPreferences("ui", Context.MODE_PRIVATE)
        val before = prefs.all
        val behavior = XMLPrefsManager.XMLPrefsRoot.BEHAVIOR.getValues()!!
        val theme = XMLPrefsManager.XMLPrefsRoot.THEME.getValues()!!
        val oldBehavior = behavior.list.toList()
        val oldTheme = theme.list.toList()
        lateinit var manager: UnlockManager
        instrumentation.runOnMainSync {
            behavior.list.clear()
            theme.list.clear()
            behavior.add(Behavior.unlock_counter_format.label(), "%c")
            theme.add(Theme.unlock_counter_text_color.label(), "#ffffff")
            prefs.edit().putInt(UnlockManager.UNLOCK_KEY, 0)
                .putLong(UnlockManager.NEXT_UNLOCK_CYCLE_RESTART, System.currentTimeMillis() + 86400000).commit()
            manager = UnlockManager(context, 12, null)
        }
        try {
            if (onMain) instrumentation.runOnMainSync { block(manager, prefs) }
            else block(manager, prefs)
        } finally {
            instrumentation.runOnMainSync {
                manager.dispose()
                behavior.list.clear()
                behavior.list.addAll(oldBehavior)
                theme.list.clear()
                theme.list.addAll(oldTheme)
                val editor = prefs.edit().clear()
                before.forEach { (key, value) ->
                    when (value) {
                        is Int -> editor.putInt(key, value)
                        is Long -> editor.putLong(key, value)
                        is Boolean -> editor.putBoolean(key, value)
                        is Float -> editor.putFloat(key, value)
                        is String -> editor.putString(key, value)
                        is Set<*> -> @Suppress("UNCHECKED_CAST") editor.putStringSet(key, value as Set<String>)
                    }
                }
                editor.commit()
            }
        }
    }

    private fun receiver(manager: UnlockManager): android.content.BroadcastReceiver? =
        UnlockManager::class.java.getDeclaredField("lockReceiver").apply { isAccessible = true }
            .get(manager) as android.content.BroadcastReceiver?

    private fun event(manager: UnlockManager, action: String) {
        receiver(manager)!!.onReceive(instrumentation.targetContext, android.content.Intent(action))
    }

    @Test fun pausedTimerKeepsObserverAndResumeDoesNotInventUnlock() = withManager { manager, _ ->
        manager.start()
        val registered = receiver(manager)
        manager.stop()
        assertSame(registered, receiver(manager))
        assertEquals(0, manager.count())
        event(manager, android.content.Intent.ACTION_SCREEN_OFF)
        event(manager, android.content.Intent.ACTION_SCREEN_ON)
        event(manager, android.content.Intent.ACTION_USER_PRESENT)
        assertEquals(1, manager.count())
        manager.start()
        manager.start()
        assertSame(registered, receiver(manager))
        assertEquals(1, manager.count())
    }

    @Test fun overdueCycleResetsBeforePausedUnlockAndPersistsRestart() = withManager { manager, prefs ->
        manager.start()
        manager.stop()
        UnlockManager::class.java.getDeclaredField("unlockTimes").apply { isAccessible = true }.setInt(manager, 9)
        UnlockManager::class.java.getDeclaredField("nextUnlockCycleRestart").apply { isAccessible = true }.setLong(manager, 1)
        UnlockManager::class.java.getDeclaredMethod("onUnlock").apply { isAccessible = true }.invoke(manager)
        assertEquals(1, manager.count())
        assertEquals(1, prefs.getInt(UnlockManager.UNLOCK_KEY, -1))
        assertTrue(prefs.getLong(UnlockManager.NEXT_UNLOCK_CYCLE_RESTART, 0) > System.currentTimeMillis())
    }

    @Test fun rapidCyclesPersistAndRefreshDisposesOldObserver() = withManager { manager, prefs ->
        manager.start()
        repeat(2) {
            event(manager, android.content.Intent.ACTION_SCREEN_OFF)
            event(manager, android.content.Intent.ACTION_SCREEN_ON)
            event(manager, android.content.Intent.ACTION_USER_PRESENT)
            event(manager, android.content.Intent.ACTION_USER_PRESENT)
        }
        assertEquals(2, manager.count())
        assertEquals(2, prefs.getInt(UnlockManager.UNLOCK_KEY, -1))
        manager.dispose()
        manager.dispose()
        assertNull(receiver(manager))
        val replacement = UnlockManager(instrumentation.targetContext, 12, null)
        try {
            replacement.start()
            replacement.start()
            assertEquals(2, replacement.count())
            event(replacement, android.content.Intent.ACTION_USER_PRESENT)
            assertEquals(2, replacement.count())
        } finally {
            replacement.dispose()
        }
    }

    @Test fun launcherPauseOnlyStopsRenderingAndKeepsObserver() = withManager { manager, _ ->
        manager.start()
        val type = Class.forName("sun.misc.Unsafe")
        val unsafe = type.getDeclaredField("theUnsafe").apply { isAccessible = true }.get(null)
        val ui = type.getMethod("allocateInstance", Class::class.java)
            .invoke(unsafe, ohi.andre.consolelauncher.UIManager::class.java) as ohi.andre.consolelauncher.UIManager
        ohi.andre.consolelauncher.UIManager::class.java.getDeclaredField("unlockManager")
            .apply { isAccessible = true }.set(ui, manager)
        val registered = receiver(manager)
        ui.pause()
        assertSame(registered, receiver(manager))
        event(manager, android.content.Intent.ACTION_SCREEN_OFF)
        event(manager, android.content.Intent.ACTION_SCREEN_ON)
        assertEquals(1, manager.count())
        manager.start()
        assertEquals(1, manager.count())
    }

    @Test fun resetClearsOptionalHistoryBeforeRecordingNewUnlock() = withManager { manager, _ ->
        val history = longArrayOf(42, 41, 40)
        UnlockManager::class.java.getDeclaredField("lastUnlocks").apply { isAccessible = true }.set(manager, history)
        UnlockManager::class.java.getDeclaredField("nextUnlockCycleRestart").apply { isAccessible = true }.setLong(manager, 1)
        UnlockManager::class.java.getDeclaredMethod("onUnlock").apply { isAccessible = true }.invoke(manager)
        assertEquals(1, manager.count())
        assertTrue(history[0] > 42)
        assertEquals(-1L, history[1])
        assertEquals(-1L, history[2])
    }

    @Test fun protectedActionsCannotBeSpoofedByApps() = withManager { manager, _ ->
        manager.start()
        for (action in listOf(android.content.Intent.ACTION_SCREEN_OFF, android.content.Intent.ACTION_SCREEN_ON,
            android.content.Intent.ACTION_USER_PRESENT)) {
            try {
                instrumentation.targetContext.sendBroadcast(android.content.Intent(action))
                fail("App was allowed to send protected action $action")
            } catch (_: SecurityException) {
                // Exporting this system-only filter does not permit app-originated unlocks.
            }
        }
        assertEquals(0, manager.count())
    }

    @Test fun emulatorSystemWakeIsObservedWhileStatusTimerPaused() {
        org.junit.Assume.assumeTrue(android.os.Build.HARDWARE.startsWith("ranchu") ||
            android.os.Build.HARDWARE.startsWith("goldfish"))
        fun shell(command: String) {
            instrumentation.uiAutomation.executeShellCommand(command).use { descriptor ->
                java.io.FileInputStream(descriptor.fileDescriptor).use { it.readBytes() }
            }
        }
        shell("input keyevent WAKEUP")
        shell("wm dismiss-keyguard")
        withManager(onMain = false) { manager, _ ->
            instrumentation.runOnMainSync { manager.start(); manager.stop() }
            try {
                repeat(2) { cycle ->
                    shell("input keyevent SLEEP")
                    android.os.SystemClock.sleep(300)
                    instrumentation.waitForIdleSync()
                    instrumentation.runOnMainSync { assertEquals(cycle, manager.count()) }
                    shell("input keyevent WAKEUP")
                    shell("wm dismiss-keyguard")
                    val deadline = android.os.SystemClock.uptimeMillis() + 5000
                    var count = -1
                    do {
                        android.os.SystemClock.sleep(50)
                        instrumentation.runOnMainSync { count = manager.count() }
                    } while (count != cycle + 1 && android.os.SystemClock.uptimeMillis() < deadline)
                    assertEquals(cycle + 1, count)
                }
                instrumentation.runOnMainSync {
                    manager.start()
                    assertEquals(2, manager.count())
                }
            } finally {
                shell("input keyevent WAKEUP")
                shell("wm dismiss-keyguard")
            }
        }
    }

    @Test fun optedInPhysicalUnlockIsObservedWhileTimerPaused() {
        org.junit.Assume.assumeTrue("Requires an explicit human lock/unlock cycle",
            InstrumentationRegistry.getArguments().getString("physicalUnlock") == "true")
        org.junit.Assume.assumeFalse(android.os.Build.HARDWARE.startsWith("ranchu") ||
            android.os.Build.HARDWARE.startsWith("goldfish"))
        // Keep the tested package foreground; OEMs can freeze an activity-less
        // instrumented package while the user's separate Launcher owns Home.
        androidx.test.core.app.ActivityScenario.launch<ohi.andre.consolelauncher.LauncherActivity>(
            android.content.Intent(instrumentation.targetContext, ohi.andre.consolelauncher.LauncherActivity::class.java)
        ).use {
            withManager(onMain = false) { manager, prefs ->
                instrumentation.runOnMainSync { manager.start(); manager.stop() }
                instrumentation.sendStatus(2, android.os.Bundle().apply { putString("stream", "Physical unlock observer ready\n") })
                val deadline = android.os.SystemClock.elapsedRealtime() + 180000
                var count = 0
                do {
                    android.os.SystemClock.sleep(100)
                    instrumentation.runOnMainSync { count = manager.count() }
                } while (count == 0 && android.os.SystemClock.elapsedRealtime() < deadline)
                assertEquals("One real protected-system unlock must be counted", 1, count)
                assertEquals(1, prefs.getInt(UnlockManager.UNLOCK_KEY, -1))
            }
        }
    }

    @Test fun screenOffNeverCounts() = withManager { manager, _ ->
        manager.start()
        val receiver = UnlockManager::class.java.getDeclaredField("lockReceiver")
            .apply { isAccessible = true }.get(manager) as android.content.BroadcastReceiver
        receiver.onReceive(instrumentation.targetContext, android.content.Intent(android.content.Intent.ACTION_SCREEN_OFF))
        assertEquals(0, manager.count())
    }

    @Test fun countOnlyFormatPersistsUnlocks() = withManager { manager, prefs ->
        UnlockManager::class.java.getDeclaredMethod("onUnlock").apply { isAccessible = true }.invoke(manager)
        assertEquals(1, manager.count())
        assertEquals(1, prefs.getInt(UnlockManager.UNLOCK_KEY, -1))
    }
}
