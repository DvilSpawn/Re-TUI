package ohi.andre.consolelauncher.managers

import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File
import ohi.andre.consolelauncher.tuils.GenericFileProvider

class PortablePresetIntegrationTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun initializePresetStorage() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R &&
            !"playstore".equals(ohi.andre.consolelauncher.BuildConfig.FLAVOR, ignoreCase = true)
        ) {
            android.os.ParcelFileDescriptor.AutoCloseInputStream(
                InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(
                    "appops set ${context.packageName} MANAGE_EXTERNAL_STORAGE allow"
                )
            ).use { it.readBytes() }
            assertTrue("Fixture requires all-files access", android.os.Environment.isExternalStorageManager())
        }
        ohi.andre.consolelauncher.tuils.Tuils.init(context)
    }

    private fun texts(view: android.view.View): List<android.widget.TextView> {
        val result = mutableListOf<android.widget.TextView>()
        if (view is android.widget.TextView) result.add(view)
        if (view is android.view.ViewGroup) for (i in 0 until view.childCount) result.addAll(texts(view.getChildAt(i)))
        return result
    }
    private fun await(activity: android.app.Activity, expected: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val deadline = android.os.SystemClock.uptimeMillis() + 10000
        while (android.os.SystemClock.uptimeMillis() < deadline) {
            var found = false
            instrumentation.runOnMainSync { found = texts(activity.window.decorView).any { it.text.toString().contains(expected) } }
            if (found) return
            Thread.sleep(50)
        }
        var output = ""
        instrumentation.runOnMainSync { output = texts(activity.window.decorView).joinToString { it.text.toString() } }
        fail("Expected $expected; UI: $output")
    }

    @Test
    fun manualPickerAcceptsWronglyTypedLegacyPackageWithoutSaving() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val before = PresetManager.listPresets().toSet()
        val uri = Uri.parse("content://${instrumentation.context.packageName}.portable-fixtures/legacy.retui-backup")
        var picker: Intent? = null
        val monitor = object : android.app.Instrumentation.ActivityMonitor() {
            override fun onStartActivity(intent: Intent): android.app.Instrumentation.ActivityResult? {
                if (intent.action != Intent.ACTION_OPEN_DOCUMENT) return null
                picker = intent
                return android.app.Instrumentation.ActivityResult(android.app.Activity.RESULT_OK, Intent().setData(uri))
            }
        }
        instrumentation.addMonitor(monitor)
        var activity: android.app.Activity? = null
        try {
            activity = instrumentation.startActivitySync(Intent(context,
                ohi.andre.consolelauncher.commands.tuixt.PresetImportActivity::class.java)
                .setAction(Intent.ACTION_MAIN).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            await(activity, "Preset: Portable fixture")
            assertTrue(picker!!.hasCategory(Intent.CATEGORY_OPENABLE))
            assertEquals(setOf(BackupManager.PRESET_MIME, "application/zip", "application/octet-stream"),
                picker!!.getStringArrayExtra(Intent.EXTRA_MIME_TYPES)!!.toSet())
            assertEquals(before, PresetManager.listPresets().toSet())
        } finally {
            activity?.let { instrumentation.runOnMainSync { it.finish() } }
            instrumentation.removeMonitor(monitor)
        }
    }

    @Test
    fun dispatcherBackWaitsForSaveAndCancelsStagedInspection() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val before = PresetManager.listPresets().toSet()
        val uri = Uri.parse("content://${instrumentation.context.packageName}.portable-fixtures/legacy.retui-backup")
        val activity = instrumentation.startActivitySync(Intent(context,
            ohi.andre.consolelauncher.commands.tuixt.PresetImportActivity::class.java)
            .setAction(Intent.ACTION_VIEW).setDataAndType(uri, BackupManager.PRESET_MIME)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        try {
            await(activity, "Preset: Portable fixture")
            assertTrue("Import must support gesture back dispatch", activity is androidx.activity.ComponentActivity)
            val saving = activity.javaClass.getDeclaredField("saving").apply { isAccessible = true }
            val staged = activity.javaClass.getDeclaredField("staged").apply { isAccessible = true }
            var folder: File? = null
            instrumentation.runOnMainSync {
                folder = (staged.get(activity) as BackupManager.StagedPreset).folder
                saving.setBoolean(activity, true)
                val component = activity as androidx.activity.ComponentActivity
                component.onBackPressedDispatcher.onBackPressed()
                assertFalse("Back must not interrupt an in-flight save", activity.isFinishing)
                saving.setBoolean(activity, false)
                component.onBackPressedDispatcher.onBackPressed()
                assertTrue("Back cancels inspection without confirmation", activity.isFinishing)
            }
            instrumentation.waitForIdleSync()
            val deadline = android.os.SystemClock.uptimeMillis() + 10000
            while (folder!!.exists() && android.os.SystemClock.uptimeMillis() < deadline) Thread.sleep(50)
            assertFalse("Cancellation removes the captured staging folder", folder!!.exists())
            assertEquals(before, PresetManager.listPresets().toSet())
        } finally { instrumentation.runOnMainSync { activity.finish() } }
    }

    @Test
    fun invalidExternalUrisAndActionsCannotEnableImport() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val before = PresetManager.listPresets().toSet()
        for (uri in listOf("file:///sdcard/preset.retui", "https://example.com/preset.retui", "content://missing.provider/preset.retui")) {
            val activity = instrumentation.startActivitySync(Intent(context,
                ohi.andre.consolelauncher.commands.tuixt.PresetImportActivity::class.java)
                .setAction(Intent.ACTION_VIEW).setDataAndType(Uri.parse(uri), BackupManager.PRESET_MIME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            try {
                await(activity, "Unable to import preset")
                instrumentation.runOnMainSync { assertFalse(texts(activity.window.decorView).first { it is android.widget.Button && it.text == "Import" }.isEnabled) }
            } finally { instrumentation.runOnMainSync { activity.finish() } }
        }
        assertEquals(before, PresetManager.listPresets().toSet())
    }

    @Test
    fun cachedSelfShareIsInspectableWhilePrivateProviderPathsAreRejected() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val file = File(context.cacheDir, "self-share-destination.retui")
        ohi.andre.consolelauncher.tuils.Tuils.init(context)
        val source = File(PresetManager.presetsDir, "Self sharing fixture").apply { mkdirs() }
        try {
            File(source, "theme.xml").writeText("<THEME/>")
            File(source, "suggestions.xml").writeText("<SUGGESTIONS/>")
            val uri = BackupManager.exportShareableConfiguration(context,
                FileProvider.getUriForFile(context, GenericFileProvider.PROVIDER_NAME, file), source.name)
            val activity = instrumentation.startActivitySync(Intent(context,
                ohi.andre.consolelauncher.commands.tuixt.PresetImportActivity::class.java)
                .setAction(Intent.ACTION_SEND).setType(BackupManager.PRESET_MIME).putExtra(Intent.EXTRA_STREAM, uri)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            try { await(activity, "Preset: Self sharing fixture") }
            finally { instrumentation.runOnMainSync { activity.finish() } }
            val privateUri = FileProvider.getUriForFile(context, GenericFileProvider.PROVIDER_NAME, file)
            for (blockedUri in listOf(privateUri, privateUri.buildUpon().authority("0@${privateUri.authority}").build())) {
                val refused = instrumentation.startActivitySync(Intent(context,
                    ohi.andre.consolelauncher.commands.tuixt.PresetImportActivity::class.java)
                    .setAction(Intent.ACTION_VIEW).setDataAndType(blockedUri, BackupManager.PRESET_MIME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                try { await(refused, "Choose the exported preset") }
                finally { instrumentation.runOnMainSync { refused.finish() } }
            }
        } finally { source.deleteRecursively(); file.delete() }
    }

    @Test
    fun coldExternalConfirmationInitializesPresetStorage() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        ohi.andre.consolelauncher.tuils.Tuils.init(context)
        val before = PresetManager.listPresets().toSet()
        val field = ohi.andre.consolelauncher.tuils.Tuils.javaClass.getDeclaredField("folder").apply { isAccessible = true }
        val previous = field.get(ohi.andre.consolelauncher.tuils.Tuils)
        field.set(ohi.andre.consolelauncher.tuils.Tuils, null)
        val uri = Uri.parse("content://${instrumentation.context.packageName}.portable-fixtures/legacy.retui-backup")
        val activity = instrumentation.startActivitySync(Intent(context,
            ohi.andre.consolelauncher.commands.tuixt.PresetImportActivity::class.java)
            .setAction(Intent.ACTION_VIEW).setDataAndType(uri, BackupManager.PRESET_MIME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        try {
            await(activity, "Preset: Portable fixture")
            instrumentation.runOnMainSync { texts(activity.window.decorView).first { it is android.widget.Button && it.text == "Import" }.performClick() }
            await(activity, "Saved preset:")
            assertEquals(1, (PresetManager.listPresets().toSet() - before).size)
        } finally {
            instrumentation.runOnMainSync { activity.finish() }
            field.set(ohi.andre.consolelauncher.tuils.Tuils, previous)
            (PresetManager.listPresets().toSet() - before).forEach { File(PresetManager.presetsDir, it).deleteRecursively() }
        }
    }

    @Test
    fun externalInspectionCancelAndConfirmNeverApplyAndRejectPersonalBackup() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val authority = instrumentation.context.packageName + ".portable-fixtures"
        ohi.andre.consolelauncher.tuils.Tuils.init(context)
        val before = PresetManager.listPresets().toSet()
        val active = ohi.andre.consolelauncher.tuils.Tuils.getFolder().listFiles()!!.filter { it.isFile }.associate { it.name to it.readBytes() }
        fun launch(variant: String, action: String): android.app.Activity {
            val uri = Uri.parse("content://$authority/$variant")
            val intent = Intent(context, ohi.andre.consolelauncher.commands.tuixt.PresetImportActivity::class.java)
                .setAction(action).setType(BackupManager.PRESET_MIME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
            if (action == Intent.ACTION_SEND) intent.putExtra(Intent.EXTRA_STREAM, uri) else intent.setDataAndType(uri, BackupManager.PRESET_MIME)
            return instrumentation.startActivitySync(intent)
        }
        try {
            val cancel = launch("legacy.retui-backup", Intent.ACTION_VIEW)
            await(cancel, "Preset: Portable fixture")
            assertEquals(before, PresetManager.listPresets().toSet())
            instrumentation.runOnMainSync { cancel.finish() }
            instrumentation.waitForIdleSync()
            val confirm = launch("selected.retui", Intent.ACTION_SEND)
            await(confirm, "echo inspected-not-run")
            instrumentation.runOnMainSync { texts(confirm.window.decorView).first { it is android.widget.Button && it.text == "Import" }.performClick() }
            await(confirm, "Saved preset:")
            val added = PresetManager.listPresets().toSet() - before
            assertEquals(1, added.size)
            assertTrue(File(PresetManager.presetsDir, added.single() + "/behavior.xml").readText().contains("echo inspected-not-run"))
            instrumentation.runOnMainSync { confirm.finish() }
            val personal = launch("personal.retui", Intent.ACTION_VIEW)
            await(personal, "Personal backups cannot")
            instrumentation.runOnMainSync {
                assertFalse(texts(personal.window.decorView).first { it is android.widget.Button && it.text == "Import" }.isEnabled)
                personal.finish()
            }
            active.forEach { (name, bytes) -> assertArrayEquals(bytes, File(ohi.andre.consolelauncher.tuils.Tuils.getFolder(), name).readBytes()) }
        } finally {
            (PresetManager.listPresets().toSet() - before).forEach { File(PresetManager.presetsDir, it).deleteRecursively() }
        }
    }

    @Test
    fun settingsExposeManualImportAndCustomExportPicker() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val activity = instrumentation.startActivitySync(Intent(context,
            ohi.andre.consolelauncher.commands.tuixt.ThemerActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        try {
            val method = activity.javaClass.getDeclaredMethod("getItemsForSection", String::class.java).apply { isAccessible = true }
            var items: List<*>? = null
            instrumentation.runOnMainSync { items = method.invoke(activity, "system") as List<*> }
            assertTrue("Settings must expose manual portable import", items!!.contains("IMPORT_RETUI_PRESET"))
            var captured: Intent? = null
            val monitor = object : android.app.Instrumentation.ActivityMonitor() {
                override fun onStartActivity(intent: Intent): android.app.Instrumentation.ActivityResult? {
                    captured = intent
                    return android.app.Instrumentation.ActivityResult(android.app.Activity.RESULT_CANCELED, null)
                }
            }
            instrumentation.addMonitor(monitor)
            try {
                instrumentation.runOnMainSync {
                    activity.javaClass.getDeclaredMethod("launchShareableConfigurationPicker").apply { isAccessible = true }.invoke(activity)
                }
                assertEquals(BackupManager.PRESET_MIME, captured?.type)
                assertTrue(captured?.getStringExtra(Intent.EXTRA_TITLE)?.endsWith(".retui") == true)
            } finally { instrumentation.removeMonitor(monitor) }
        } finally { instrumentation.runOnMainSync { activity.finish() } }
    }

    @Test
    fun shareSheetReceivesCachedUriAndTemporaryReadGrant() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val activity = instrumentation.startActivitySync(Intent(context,
            ohi.andre.consolelauncher.commands.tuixt.ThemerActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        try {
            val method = activity.javaClass.declaredMethods.firstOrNull { it.name == "sharePreset" }
            assertNotNull("A verified export needs a share sheet action", method)
            val uri = Uri.parse("content://${GenericFileProvider.PROVIDER_NAME}/cache_files/preset-share/test/test.retui")
            var captured: Intent? = null
            val monitor = object : android.app.Instrumentation.ActivityMonitor() {
                override fun onStartActivity(intent: Intent): android.app.Instrumentation.ActivityResult? {
                    captured = intent
                    return android.app.Instrumentation.ActivityResult(0, null)
                }
            }
            instrumentation.addMonitor(monitor)
            try {
                instrumentation.runOnMainSync { method!!.apply { isAccessible = true }.invoke(activity, uri) }
                assertEquals(Intent.ACTION_CHOOSER, captured?.action)
                val send = captured!!.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)!!
                assertEquals(Intent.ACTION_SEND, send.action)
                assertEquals(BackupManager.PRESET_MIME, send.type)
                assertEquals(uri, send.getParcelableExtra<Uri>(Intent.EXTRA_STREAM))
                assertEquals(uri, send.clipData!!.getItemAt(0).uri)
                assertTrue(send.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
            } finally { instrumentation.removeMonitor(monitor) }
        } finally { instrumentation.runOnMainSync { activity.finish() } }
    }

    @Test
    fun saveKeepsActiveFilesAndAllocatesCaseInsensitiveUniqueNames() {
        ohi.andre.consolelauncher.tuils.Tuils.init(context)
        val name = "Portable unique ${System.nanoTime()}"
        val root = File(context.cacheDir, "save-test-${System.nanoTime()}").apply { mkdir() }
        val out = java.io.ByteArrayOutputStream()
        java.util.zip.ZipOutputStream(out).use { zip ->
            mapOf("manifest.txt" to "type=retui-shareable-config\nschema=1\nprofile=shareable\nsections=theme,suggestions\npresetName=$name\n",
                "theme.xml" to "<THEME/>", "suggestions.xml" to "<SUGGESTIONS/>").forEach { (file, text) ->
                zip.putNextEntry(java.util.zip.ZipEntry(file)); zip.write(text.toByteArray()); zip.closeEntry()
            }
        }
        val active = ohi.andre.consolelauncher.tuils.Tuils.getFolder().listFiles()!!.filter { it.isFile }.associate { it.name to it.readBytes() }
        try {
            BackupManager.inspectPreset(out.toByteArray().inputStream(), root).use { staged ->
                assertFalse(File(PresetManager.presetsDir, name).exists())
                assertEquals(name, staged.save())
            }
            val lower = name.lowercase()
            PresetManager.importExtractedFolder(lower, File(PresetManager.presetsDir, name))
            assertTrue("Case-insensitive duplicate must be renamed", File(PresetManager.presetsDir, "$lower (2)").isDirectory)
            active.forEach { (file, bytes) -> assertArrayEquals(bytes, File(ohi.andre.consolelauncher.tuils.Tuils.getFolder(), file).readBytes()) }
            assertEquals(0, root.listFiles()!!.size)
        } finally {
            File(PresetManager.presetsDir, name).deleteRecursively()
            File(PresetManager.presetsDir, name.lowercase()).deleteRecursively()
            File(PresetManager.presetsDir, "${name.lowercase()} (2)").deleteRecursively()
            root.deleteRecursively()
        }
    }

    @Test
    fun verifiedExportReturnsCachedShareUriWithMatchingBytes() {
        ohi.andre.consolelauncher.tuils.Tuils.init(context)
        val source = File(PresetManager.presetsDir, "Portable export test").apply { mkdirs() }
        val destination = File(context.cacheDir, "export-destination.retui")
        try {
            File(source, "theme.xml").writeText("<THEME/>")
            File(source, "suggestions.xml").writeText("<SUGGESTIONS/>")
            val destinationUri = FileProvider.getUriForFile(context, GenericFileProvider.PROVIDER_NAME, destination)
            val method = BackupManager.javaClass.getMethod("exportShareableConfiguration", android.content.Context::class.java,
                Uri::class.java, String::class.java, Set::class.java)
            val shareUri = method.invoke(BackupManager, context, destinationUri, source.name, emptySet<String>())
            assertTrue("Verified export must return a cached content URI", shareUri is Uri)
            shareUri as Uri
            assertEquals("content", shareUri.scheme)
            assertNotEquals(destinationUri, shareUri)
            assertEquals(BackupManager.PRESET_MIME, context.contentResolver.getType(shareUri))
            assertArrayEquals(destination.readBytes(), context.contentResolver.openInputStream(shareUri)!!.use { it.readBytes() })
            val recipient = InstrumentationRegistry.getInstrumentation().context.packageName
            val recipientProvider = Uri.parse("content://$recipient.portable-fixtures")
            context.grantUriPermission(recipient, shareUri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            try {
                val read = context.contentResolver.call(recipientProvider, "read-share", shareUri.toString(), null)!!
                assertNull(read.getString("error"))
                assertEquals(BackupManager.PRESET_MIME, read.getString("mime"))
                assertArrayEquals(destination.readBytes(), read.getByteArray("bytes"))
            } finally { context.revokeUriPermission(shareUri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            val denied = context.contentResolver.call(recipientProvider, "read-share", shareUri.toString(), null)!!
            assertNotNull("Recipient access must end after revocation", denied.getString("error"))
        } finally { source.deleteRecursively(); destination.delete() }
    }

    @Test
    fun providerTypesPortablePresetWithoutChangingPersonalBackupType() {
        val file = File(context.cacheDir, "typing.retui").apply { writeText("preset") }
        try {
            val uri = FileProvider.getUriForFile(context, GenericFileProvider.PROVIDER_NAME, file)
            assertEquals("application/vnd.retui+zip", context.contentResolver.getType(uri))
            assertEquals("preset", context.contentResolver.openInputStream(uri)!!.bufferedReader().use { it.readText() })
            val personal = File(context.cacheDir, "typing.retui-backup").apply { writeText("backup") }
            val unrelated = File(context.cacheDir, "typing.txt").apply { writeText("text") }
            try {
                assertEquals("application/octet-stream", context.contentResolver.getType(FileProvider.getUriForFile(context, GenericFileProvider.PROVIDER_NAME, personal)))
                assertEquals("text/plain", context.contentResolver.getType(FileProvider.getUriForFile(context, GenericFileProvider.PROVIDER_NAME, unrelated)))
            } finally { personal.delete(); unrelated.delete() }
        } finally { file.delete() }
    }

    @Test
    fun customViewAndSendResolveButGenericZipDoesNot() {
        for (action in listOf(Intent.ACTION_VIEW, Intent.ACTION_SEND)) {
            val intent = Intent(action).setType("application/vnd.retui+zip").setPackage(context.packageName)
            if (action == Intent.ACTION_VIEW) intent.setDataAndType(Uri.parse("content://example/preset"), intent.type)
            val resolved = context.packageManager.queryIntentActivities(intent, 0)
            assertTrue("Missing portable preset handler for $action", resolved.any { it.activityInfo.name.endsWith("PresetImportActivity") })
            intent.setType("application/zip")
            assertFalse(context.packageManager.queryIntentActivities(intent, 0).any { it.activityInfo.name.endsWith("PresetImportActivity") })
        }
    }
}
