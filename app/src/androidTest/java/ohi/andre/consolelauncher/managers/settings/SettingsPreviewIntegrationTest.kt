package ohi.andre.consolelauncher.managers.settings

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import ohi.andre.consolelauncher.BuildConfig
import ohi.andre.consolelauncher.LauncherActivity
import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.commands.tuixt.SettingsAppearancePreview
import ohi.andre.consolelauncher.commands.tuixt.TuixtActivity
import ohi.andre.consolelauncher.commands.tuixt.TuixtAdapter
import ohi.andre.consolelauncher.managers.notifications.NotificationManager
import ohi.andre.consolelauncher.managers.xml.XMLPrefsManager
import ohi.andre.consolelauncher.managers.xml.classes.XMLPrefsSave
import ohi.andre.consolelauncher.managers.xml.options.*
import ohi.andre.consolelauncher.tuils.Tuils
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Real Home must exist before opening the editor: the preview never starts another launcher. */
internal object SettingsPreviewFixture {
    val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    val context get() = instrumentation.targetContext
    fun views(view: View): List<View> = listOf(view) + if (view is ViewGroup)
        (0 until view.childCount).flatMap { views(view.getChildAt(it)) } else emptyList()
    fun all(activity: TuixtActivity) = views(activity.window.decorView)
    fun settings(activity: TuixtActivity) = all(activity).filterIsInstance<RecyclerView>().first { it.adapter is TuixtAdapter }
    fun adapter(activity: TuixtActivity) = settings(activity).adapter as TuixtAdapter
    fun preview(activity: TuixtActivity) = all(activity).filterIsInstance<SettingsAppearancePreview>().single()
    fun thumbnail(activity: TuixtActivity) = preview(activity).pager
    fun source(root: XMLPrefsManager.XMLPrefsRoot) = File(Tuils.getFolder(), root.path())
    fun settle() { Thread.sleep(180); instrumentation.waitForIdleSync() }
    fun open(root: XMLPrefsManager.XMLPrefsRoot): TuixtActivity =
        (instrumentation.startActivitySync(Intent(context, TuixtActivity::class.java)
            .putExtra(TuixtActivity.PATH, source(root).absolutePath).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as TuixtActivity)
            .also { settle() }
    fun search(activity: TuixtActivity, label: String) {
        instrumentation.runOnMainSync {
            all(activity).filterIsInstance<EditText>().first { it.hint == context.getString(R.string.editor_tuixtactivity_search_settings_d25ad) }.setText(label)
        }
        settle()
    }
    fun edit(activity: TuixtActivity, value: String) {
        instrumentation.runOnMainSync { settings(activity).findViewById<EditText>(R.id.setting_input).setText(value) }
        settle()
    }
    fun click(activity: TuixtActivity, label: Int) {
        instrumentation.runOnMainSync { all(activity).filterIsInstance<TextView>().first { it.text == context.getString(label) }.performClick() }
        settle()
    }
    fun withHome(values: Map<XMLPrefsSave, String> = emptyMap(), run: () -> Unit) {
        Tuils.init(context); XMLPrefsManager.loadCommons(context); LauncherSettings.refreshFromLoadedPrefs()
        NotificationManager.create(context)
        val fixture = mapOf<XMLPrefsSave, String>(Behavior.auto_show_keyboard to "false") + values + mapOf<XMLPrefsSave, String>(Notifications.terminal_notifications to "false", Notifications.show_notifications to "false")
        val original = fixture.keys.associateWith { LauncherSettings.get(it) }
        context.getSharedPreferences("retui_startup", 0).edit().putBoolean("initialized", true).putBoolean("complete", true).putBoolean("active", false).commit()
        context.getSharedPreferences("changelogPrefs", 0).edit().putInt("last_seen_version_code", BuildConfig.VERSION_CODE).commit()
        try {
            fixture.forEach { (key, value) -> LauncherSettings.set(key, value) }
            ActivityScenario.launch<LauncherActivity>(Intent(context, LauncherActivity::class.java)).use {
                val deadline=android.os.SystemClock.uptimeMillis()+5000
                var ready=false
                while(!ready && android.os.SystemClock.uptimeMillis()<deadline) {
                    settle()
                    instrumentation.runOnMainSync { ready=LauncherActivity.captureHomePreview()!=null }
                }
                assertTrue("Home must draw before opening a preview",ready)
                run()
            }
        } finally { instrumentation.runOnMainSync { original.forEach { (key, value) -> LauncherSettings.set(key, value) } } }
    }
    data class SavedAppearanceState(val files: Map<String, ByteArray>, val preferences: Map<String, Map<String, *>>) {
        fun assertUnchanged() {
            val current = appearanceState()
            assertEquals("Preview changed the saved configuration file set", files.keys, current.files.keys)
            files.forEach { (path, bytes) -> assertArrayEquals("Preview wrote $path", bytes, current.files.getValue(path)) }
            assertEquals("Preview changed frame or wallpaper preferences", preferences, current.preferences)
        }
    }
    fun appearanceState(): SavedAppearanceState {
        val folder = Tuils.getFolder()
        val files = folder.listFiles().orEmpty().filter { it.isFile && it.extension.equals("xml", true) }
            .associate { it.name to it.readBytes() }.toMutableMap()
        File(folder, "frames/state.json").takeIf { it.exists() }?.let { files["frames/state.json"] = it.readBytes() }
        val preferences = listOf("retui_frames", "retui_wallpaper").associateWith { name ->
            context.getSharedPreferences(name, 0).all.mapValues { (_, value) -> if (value is Set<*>) value.toSet() else value }
        }
        return SavedAppearanceState(files, preferences)
    }
    fun hideKeyboard(activity: TuixtActivity) {
        instrumentation.runOnMainSync {
            (activity.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager)
                .hideSoftInputFromWindow(activity.window.decorView.windowToken, 0)
            activity.currentFocus?.clearFocus()
        }
        settle()
    }
    /** Golden scenes hold transient cursors and scrollbar fading steady; every rendered pixel is still compared. */
    fun steadyPresentation(root: View) {
        if(root is EditText) root.isCursorVisible = false
        if(root is ohi.andre.consolelauncher.tuils.OutlineEditText) root.setIdleCursorVisible(false)
        if(root.isHorizontalScrollBarEnabled || root.isVerticalScrollBarEnabled) root.isScrollbarFadingEnabled = false
        if(root is ViewGroup) for(index in 0 until root.childCount) steadyPresentation(root.getChildAt(index))
    }
    fun capture(name: String) {
        val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot()) { "Unable to capture $name" }
        val folder = File(context.getExternalFilesDir(null), "issue23").apply { mkdirs() }
        File(folder, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
    fun scenePixels(activity: TuixtActivity, captureName: String? = null): Int {
        val scene = thumbnail(activity).getChildAt(0)
        val width = scene.width
        val height = scene.height
        // A mounted passive scene still owns native scrollbar animation state. Hold it steady for both hashes.
        steadyPresentation(scene)
        scene.requestLayout()
        scene.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
        scene.layout(0, 0, width, height)
        fun preDraw(view: View) {
            if(view is TextView) view.onPreDraw()
            if(view is ViewGroup) for(index in 0 until view.childCount) preDraw(view.getChildAt(index))
        }
        preDraw(scene)
        if(captureName != null) {
            val folder = File(context.getExternalFilesDir(null), "issue23").apply { mkdirs() }
            File(folder, "$captureName-scrollbars.txt").writeText(views(scene).filter {
                it.isHorizontalScrollBarEnabled || it.isVerticalScrollBarEnabled
            }.joinToString("\n") { view ->
                val id = runCatching { view.resources.getResourceEntryName(view.id) }.getOrDefault(view.javaClass.simpleName)
                "$id ${view.width}x${view.height} fade=${view.isScrollbarFadingEnabled} style=${view.scrollBarStyle} scroll=${view.scrollX},${view.scrollY} padding=${view.paddingLeft},${view.paddingTop},${view.paddingRight},${view.paddingBottom}"
            })
        }
        return pixels(scene, captureName)
    }
    fun pixels(view: View, captureName: String? = null): Int {
        assertTrue(view.width > 0 && view.height > 0)
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        if(captureName != null) File(File(context.getExternalFilesDir(null), "issue23").apply { mkdirs() }, "$captureName.png")
            .outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        bitmap.recycle()
        return pixels.contentHashCode()
    }
}

@RunWith(AndroidJUnit4::class)
class SettingsPreviewIntegrationTest {
    @Test fun editsRenderLocallyAndOnlySaveWritesSettings() = with(SettingsPreviewFixture) {
        // The root fill is always visible and remains meaningful with a custom frame installed.
        withHome(mapOf(Ui.system_wallpaper to "true", Theme.wallpaper_overlay_color to "#FF101010")) {
            val source = source(XMLPrefsManager.XMLPrefsRoot.THEME)
            val before = source.readBytes()
            val activity = open(XMLPrefsManager.XMLPrefsRoot.THEME)
            try {
                var oldPixels = 0
                var editedPixels = 0
                instrumentation.runOnMainSync { oldPixels = scenePixels(activity, "save-hash-before-edit") }
                search(activity, "wallpaper_overlay_color"); edit(activity, "#FF1256CC")
                instrumentation.runOnMainSync {
                    assertEquals("#FF1256CC", adapter(activity).draftValue(Theme.wallpaper_overlay_color))
                    assertEquals("#FF101010", LauncherSettings.get(Theme.wallpaper_overlay_color))
                    assertFalse(ThemeColorResolver.hasPreview())
                    assertArrayEquals(before, source.readBytes())
                    editedPixels = scenePixels(activity, "save-hash-draft")
                    assertNotEquals("Draft fill must change the real Home scene", oldPixels, editedPixels)
                    val recovered = TuixtAdapter(mutableListOf(TuixtAdapter.SettingsRow.setting(Theme.wallpaper_overlay_color, "Color")), source)
                    recovered.restoreDraftState(adapter(activity).saveDraftState())
                    assertEquals("#FF1256CC", recovered.draftValue(Theme.wallpaper_overlay_color))
                }
                click(activity, R.string.editor_tuixtactivity_save_50815)
                assertEquals("#FF1256CC", LauncherSettings.get(Theme.wallpaper_overlay_color))
                assertFalse(adapter(activity).hasPendingChanges())
                instrumentation.runOnMainSync {
                    assertEquals("Saving must retain the exact frozen draft scene", editedPixels, scenePixels(activity, "save-hash-after-save"))
                }
                assertFalse(before.contentEquals(source.readBytes()))
                edit(activity, "#FFDE5432")
                instrumentation.runOnMainSync { activity.finish() }
                settle()
                assertEquals("#FF1256CC", LauncherSettings.get(Theme.wallpaper_overlay_color))
            } finally { instrumentation.runOnMainSync { activity.finish() } }
        }
    }

    @Test fun themeEditsDoNotPopulateLauncherPreviewCache() = with(SettingsPreviewFixture) {
        Tuils.init(context); XMLPrefsManager.loadCommons(context); LauncherSettings.refreshFromLoadedPrefs()
        instrumentation.runOnMainSync {
            val source = File(Tuils.getFolder(), "theme.xml")
            val original = LauncherSettings.get(Theme.terminal_border_color)
            val adapter = TuixtAdapter(mutableListOf(TuixtAdapter.SettingsRow.setting(Theme.terminal_border_color, "Color")), source)
            val recycler = RecyclerView(context).apply { layoutManager = androidx.recyclerview.widget.LinearLayoutManager(context) }
            val holder = adapter.onCreateViewHolder(recycler, adapter.getItemViewType(0))
            adapter.onBindViewHolder(holder, 0)
            holder.itemView.findViewById<EditText>(R.id.setting_input).setText("#FF123456")
            assertEquals("#FF123456", adapter.draftValue(Theme.terminal_border_color))
            assertEquals(original, LauncherSettings.get(Theme.terminal_border_color))
            assertFalse(ThemeColorResolver.hasPreview())
            holder.itemView.findViewById<EditText>(R.id.setting_input).setText("#bad-color")
            assertEquals("#bad-color", adapter.draftValue(Theme.terminal_border_color))
            assertNotNull(holder.itemView.findViewById<EditText>(R.id.setting_input).error)
            assertEquals(original, LauncherSettings.get(Theme.terminal_border_color))
            assertFalse(ThemeColorResolver.hasPreview())
        }
    }
}
