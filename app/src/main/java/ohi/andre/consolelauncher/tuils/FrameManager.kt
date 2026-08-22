package ohi.andre.consolelauncher.tuils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.Drawable
import android.util.LruCache
import android.util.Log
import java.io.BufferedInputStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import ohi.andre.consolelauncher.managers.xml.options.SurfaceBorder
import org.json.JSONObject

enum class FrameTarget(val id: String, val label: String) {
    STATUS_GROUP("status_group", "Unified status group"),
    STATUS_RAM("status_ram", "RAM status"),
    STATUS_DEVICE("status_device", "Device status"),
    STATUS_TIME("status_time", "Time status"),
    STATUS_BATTERY("status_battery", "Battery status"),
    STATUS_STORAGE("status_storage", "Storage status"),
    STATUS_NETWORK("status_network", "Network status"),
    STATUS_NOTES("status_notes", "Notes status"),
    STATUS_WEATHER("status_weather", "Weather status"),
    STATUS_UNLOCK("status_unlock", "Unlock status"),
    STATUS_ASCII("status_ascii", "ASCII status"),
    OUTPUT("output", "Terminal output"),
    INPUT("input", "Terminal input"),
    TOOLBAR("toolbar", "Toolbar buttons"),
    SUGGESTIONS("suggestions", "Suggestion chips"),
    MUSIC("music", "Music widget"),
    NOTIFICATIONS("notifications", "Notification widget"),
    MODULES("modules", "Modules"),
    MODULE_DOCK("module_dock", "Module dock"),
    APP_DRAWER("app_drawer", "App drawer"),
    WIDGET_DRAWER("widget_drawer", "Widget drawer"),
    KEYBOARD("keyboard", "Re:TUI Keyboard"),
    FILES("files", "Re:TUI Files"),
    OVERLAYS("overlays", "Overlay windows"),
    SETTINGS("settings", "Settings and dialogs"),
    DIALOG("dialog", "Dialogs"),
    HEADER("header", "Headers"),
    LIST_ITEM("list_item", "List items"),
    LIST_ITEM_SELECTED("list_item_selected", "Selected list items"),
    UI_INPUT("ui_input", "Settings inputs"),
    BUTTON("button", "Buttons"),
    BUTTON_PRESSED("button_pressed", "Pressed buttons"),
    BUTTON_PRIMARY("button_primary", "Primary buttons"),
    ICON_BUTTON("icon_button", "Icon buttons"),
    TOGGLE_OFF("toggle_off", "Toggle off"),
    TOGGLE_ON("toggle_on", "Toggle on"),
    SLIDER_TRACK("slider_track", "Slider track"),
    SLIDER_PROGRESS("slider_progress", "Slider progress"),
    SLIDER_THUMB("slider_thumb", "Slider thumb"),
    CONTROLS("controls", "Other launcher controls");

    companion object {
        fun fromSurface(surface: SurfaceBorder): FrameTarget = when (surface) {
            SurfaceBorder.RAM -> STATUS_RAM
            SurfaceBorder.DEVICE -> STATUS_DEVICE
            SurfaceBorder.TIME -> STATUS_TIME
            SurfaceBorder.BATTERY -> STATUS_BATTERY
            SurfaceBorder.STORAGE -> STATUS_STORAGE
            SurfaceBorder.NETWORK -> STATUS_NETWORK
            SurfaceBorder.NOTES -> STATUS_NOTES
            SurfaceBorder.WEATHER -> STATUS_WEATHER
            SurfaceBorder.UNLOCK -> STATUS_UNLOCK
            SurfaceBorder.ASCII -> STATUS_ASCII
            SurfaceBorder.INPUT -> INPUT
            SurfaceBorder.OUTPUT -> OUTPUT
            SurfaceBorder.TOOLBAR -> TOOLBAR
            SurfaceBorder.SUGGESTIONS -> SUGGESTIONS
        }
    }
}

object FrameManager {
    internal const val FRAME_FOLDER = "frames"
    internal const val STATE_FILE = "state.json"
    internal const val MAX_BUNDLE_BYTES = 5 * 1024 * 1024
    private const val PREFS = "retui_frames"
    private const val LEGACY_ENABLED = "enabled"
    private const val MIGRATED = "portable_storage_migrated"
    private const val GLOBAL_BUNDLE = "active.retui-frame"
    private const val ASSET_PREFIX = "library-"
    private const val MAX_MANIFEST_BYTES = 32 * 1024
    private const val MAX_PNG_BYTES = 4 * 1024 * 1024
    private const val MAX_IMAGE_SIZE = 2048
    private const val FRAME_PREVIEW_MAX_PX = 256
    private val ASSET_ID = Regex("[0-9a-f]{64}")

    private val cache = object : LruCache<String, LoadedFrame>(
        (Runtime.getRuntime().maxMemory() / 16L).coerceIn(4L * 1024 * 1024, 16L * 1024 * 1024).toInt()
    ) {
        override fun sizeOf(key: String, value: LoadedFrame): Int = value.bitmap.allocationByteCount
    }
    private val invalidAssets = HashSet<String>()
    @Volatile private var cachedState: FrameState? = null

    data class FrameAsset(val id: String, val name: String, val valid: Boolean)

    data class SharedFrameSource(
        val assetId: String,
        val png: ByteArray,
        val leftPx: Int,
        val topPx: Int,
        val rightPx: Int,
        val bottomPx: Int,
        val leftDp: Float,
        val topDp: Float,
        val rightDp: Float,
        val bottomDp: Float,
        val topMode: String,
        val rightMode: String,
        val bottomMode: String,
        val leftMode: String,
        val centerMode: String,
        val filtering: String
    )

    internal data class FrameState(
        var applyToAll: Boolean,
        val assignments: MutableMap<String, String>
    ) {
        fun copyState() = FrameState(applyToAll, HashMap(assignments))
    }

    class EditSession internal constructor(
        private val directory: File,
        private val original: FrameState
    ) {
        private val state = original.copyState()
        var applyToAll: Boolean
            get() = state.applyToAll
            set(value) { state.applyToAll = value }
        private var libraryChanged = false
        private val previews = HashMap<String, FramePreview?>()

        fun hasChanges(): Boolean = libraryChanged || state != original

        fun selectedAssetId(target: FrameTarget?): String? = state.assignments[assignmentKey(target)]

        fun select(target: FrameTarget?, assetId: String?) {
            val key = assignmentKey(target)
            if (assetId == null) state.assignments.remove(key)
            else {
                require(ASSET_ID.matches(assetId)) { "Invalid frame selection." }
                state.assignments[key] = assetId
            }
        }

        fun hasAssignedFrame(target: FrameTarget?): Boolean = selectedAssetId(target) != null

        fun assignedName(target: FrameTarget?): String? = load(selectedAssetId(target))?.name

        fun previewBitmap(target: FrameTarget?): Bitmap? = load(selectedAssetId(target))?.bitmap

        fun assignedFrameIsInvalid(target: FrameTarget?): Boolean =
            hasAssignedFrame(target) && previewBitmap(target) == null

        fun assets(): List<FrameAsset> = directory.listFiles().orEmpty()
            .mapNotNull { assetId(it.name)?.let { id -> id to it } }
            .map { (id, _) ->
                val loaded = load(id)
                FrameAsset(id, loaded?.name ?: "Missing or corrupt frame ${id.take(8)}", loaded != null)
            }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })

        fun references(assetId: String): List<String> = state.assignments
            .filterValues { it == assetId }
            .keys
            .mapNotNull { key ->
                if (key == "global") "All surfaces" else FrameTarget.entries.firstOrNull { it.id == key }?.label
            }

        fun importBundle(target: FrameTarget?, input: InputStream): FrameAsset {
            val bytes = input.readLimited(MAX_BUNDLE_BYTES, "Frame bundle is too large.")
            val asset = registerBundle(bytes)
            val id = asset.id
            select(target, id)
            return asset
        }

        fun importFrame(target: FrameTarget?, displayName: String?, input: InputStream): FrameAsset {
            val bytes = input.readLimited(MAX_BUNDLE_BYTES, "Frame file is too large.")
            val asset = if (hasPngSignature(bytes)) {
                require(bytes.size <= MAX_PNG_BYTES) { "Frame image is too large." }
                val (width, height) = imageBounds(bytes)
                val name = displayName.orEmpty().substringAfterLast('/').substringBeforeLast('.')
                    .trim().take(80).ifEmpty { target?.label ?: "Imported frame" }
                registerBundle(buildBundle(name, defaultPngSpec(width, height), bytes))
            } else {
                registerBundle(bytes)
            }
            select(target, asset.id)
            return asset
        }

        fun deleteAsset(assetId: String) {
            state.assignments.entries.removeAll { it.value == assetId }
            val file = assetFile(directory, assetId)
            if (file.exists() && !file.delete()) throw IllegalStateException("Unable to delete frame.")
            previews.remove(assetId)
            libraryChanged = true
        }

        fun save() {
            writeStateFile(directory, state)
            removeUnreferencedAssets(directory)
            validateFrameFolder(directory)
            applyFrameFolder(directory)
            discard()
        }

        fun discard() {
            if (directory.exists()) Tuils.delete(directory)
        }

        private fun load(assetId: String?): FramePreview? {
            if (assetId == null) return null
            if (previews.containsKey(assetId)) return previews[assetId]
            val loaded = loadFramePreview(assetFile(directory, assetId), assetId)
            previews[assetId] = loaded
            return loaded
        }

        private fun registerBundle(bytes: ByteArray): FrameAsset {
            val parsed = parseBundle(bytes)
            val id = sha256(bytes)
            val file = assetFile(directory, id)
            if (!file.isFile) {
                saveFile(file, bytes)
                libraryChanged = true
            }
            previews.remove(id)
            return FrameAsset(id, parsed.name, true)
        }
    }

    fun beginEdit(context: Context): EditSession {
        ensureMigrated(context)
        val directory = File(context.cacheDir, "frame-settings-editing")
        copyFrameFolder(frameDir(), directory)
        return EditSession(directory, readStateOrDefault(directory))
    }

    fun applyToAll(context: Context): Boolean {
        ensureMigrated(context)
        return currentState().applyToAll
    }

    fun drawable(
        context: Context,
        target: FrameTarget = FrameTarget.CONTROLS,
        intrinsicDp: Float? = null
    ): NineSliceFrameDrawable? {
        ensureMigrated(context)
        val state = currentState()
        val key = assignmentKey(resolvedTarget(state.applyToAll, target))
        val assetId = state.assignments[key] ?: return null
        return loadAsset(assetId)?.let {
            NineSliceFrameDrawable(it, context.resources.displayMetrics.density, intrinsicDp)
        }
    }

    fun sharedSource(context: Context, target: FrameTarget): SharedFrameSource? {
        ensureMigrated(context)
        val state = currentState()
        val assetId = state.assignments[assignmentKey(resolvedTarget(state.applyToAll, target))] ?: return null
        return try {
            val parsed = parseBundle(FileInputStream(assetFile(frameDir(), assetId)).use {
                it.readLimited(MAX_BUNDLE_BYTES, "Frame bundle is too large.")
            })
            SharedFrameSource(
                assetId,
                parsed.png,
                parsed.spec.leftPx,
                parsed.spec.topPx,
                parsed.spec.rightPx,
                parsed.spec.bottomPx,
                parsed.spec.leftDp,
                parsed.spec.topDp,
                parsed.spec.rightDp,
                parsed.spec.bottomDp,
                parsed.spec.topMode,
                parsed.spec.rightMode,
                parsed.spec.bottomMode,
                parsed.spec.leftMode,
                parsed.spec.centerMode,
                parsed.spec.filtering
            )
        } catch (error: Exception) {
            Log.e("TUI-FRAME", "Unable to share $assetId frame", error)
            null
        }
    }

    internal fun resolvedTarget(applyToAll: Boolean, target: FrameTarget): FrameTarget? =
        if (applyToAll) null else target

    fun wrap(context: Context, fallback: Drawable, target: FrameTarget = FrameTarget.CONTROLS): FramedDrawable? =
        drawable(context, target)?.let { FramedDrawable(fallback, it) }

    fun isActive(context: Context, target: FrameTarget = FrameTarget.CONTROLS): Boolean =
        drawable(context, target) != null

    fun copyCurrentTo(context: Context, destinationRoot: File) {
        ensureMigrated(context)
        copyFrameFolder(frameDir(), File(destinationRoot, FRAME_FOLDER))
        removeUnreferencedAssets(File(destinationRoot, FRAME_FOLDER))
    }

    fun copyStateTo(context: Context, destinationRoot: File) {
        ensureMigrated(context)
        val destination = File(destinationRoot, FRAME_FOLDER)
        check(destination.exists() || destination.mkdirs()) { "Unable to create Space frame settings." }
        Tuils.copy(File(frameDir(), STATE_FILE), File(destination, STATE_FILE))
    }

    fun applyStateFrom(context: Context, sourceRoot: File) {
        ensureMigrated(context)
        writeStateFile(frameDir(), stateForSpace(sourceRoot))
        clearCache()
    }

    internal fun stateForSpace(sourceRoot: File): FrameState {
        val source = File(sourceRoot, FRAME_FOLDER)
        return if (File(source, STATE_FILE).isFile) readState(source) else FrameState(true, HashMap())
    }

    fun copyPortableState(sourceRoot: File, destinationRoot: File) {
        val source = File(sourceRoot, FRAME_FOLDER)
        if (!source.isDirectory) return
        validatePortableState(sourceRoot)
        copyFrameFolder(source, File(destinationRoot, FRAME_FOLDER))
        normalizeFrameFolder(File(destinationRoot, FRAME_FOLDER))
    }

    fun applyPortableState(sourceRoot: File) {
        val source = File(sourceRoot, FRAME_FOLDER)
        if (!source.isDirectory) return
        validatePortableState(sourceRoot)
        normalizeFrameFolder(source)
        normalizeFrameFolder(frameDir())

        val merged = File(Tuils.getFolder(), ".$FRAME_FOLDER.merging")
        copyFrameFolder(frameDir(), merged)
        for (file in source.listFiles().orEmpty()) {
            if (assetId(file.name) != null) Tuils.copy(file, File(merged, file.name))
        }
        writeStateFile(merged, readState(source))
        applyFrameFolder(merged)
        if (merged.exists()) Tuils.delete(merged)
    }

    fun validatePortableState(root: File) {
        val dir = File(root, FRAME_FOLDER)
        if (!dir.isDirectory) return
        validateFrameFolder(dir)
    }

    private fun validateFrameFolder(dir: File) {
        val schema = stateSchema(dir)
        val state = readState(dir)
        val legacyAllowed = FrameTarget.entries.mapTo(hashSetOf(STATE_FILE, GLOBAL_BUNDLE)) { "${it.id}.retui-frame" }
        for (file in dir.listFiles().orEmpty()) {
            val allowed = file.name == STATE_FILE ||
                (schema == 1 && file.name in legacyAllowed) ||
                (schema >= 2 && assetId(file.name) != null)
            require(file.isFile && allowed) { "Unsupported frame preset file: ${file.name}" }
            if (file.name.endsWith(".retui-frame")) {
                parseBundle(FileInputStream(file).use { it.readLimited(MAX_BUNDLE_BYTES, "Frame bundle is too large.") })
            }
        }
        require(state.assignments.keys.all(::isKnownRole)) {
            "Frame settings contain an unknown surface."
        }
    }

    fun portableFiles(root: File): List<File> {
        val dir = File(root, FRAME_FOLDER)
        if (!dir.isDirectory) return emptyList()
        validatePortableState(root)
        val state = readState(dir)
        val referenced = state.assignments.values.toSet()
        return dir.listFiles().orEmpty().filter {
            it.isFile && (it.name == STATE_FILE || assetId(it.name)?.let(referenced::contains) == true || stateSchema(dir) == 1)
        }.sortedBy { it.name }
    }

    internal fun isPortableEntry(name: String): Boolean {
        if (!name.startsWith("$FRAME_FOLDER/") || name.count { it == '/' } != 1) return false
        val fileName = name.substringAfter('/')
        return fileName == STATE_FILE || fileName == GLOBAL_BUNDLE ||
            FrameTarget.entries.any { fileName == "${it.id}.retui-frame" } || assetId(fileName) != null
    }

    fun onFilesRestored(context: Context) {
        clearCache()
        ensureMigrated(context)
    }

    fun ensureCurrentState(context: Context) = ensureMigrated(context)

    @Synchronized
    private fun loadAsset(assetId: String): LoadedFrame? {
        cache.get(assetId)?.let { return it }
        if (assetId in invalidAssets) return null
        val loaded = loadFrameFile(assetFile(frameDir(), assetId), assetId)
        if (loaded == null) invalidAssets.add(assetId) else cache.put(assetId, loaded)
        return loaded
    }

    @Synchronized
    private fun clearCache() {
        cache.evictAll()
        invalidAssets.clear()
        cachedState = null
    }

    @Synchronized
    private fun ensureMigrated(context: Context) {
        val dir = frameDir()
        check(dir.exists() || dir.mkdirs()) { "Unable to create the frame folder." }
        val state = File(dir, STATE_FILE)
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!state.isFile) {
            val legacyBundle = File(File(context.filesDir, FRAME_FOLDER), GLOBAL_BUNDLE)
            val global = legacyBundleFile(dir, null)
            if (!prefs.getBoolean(MIGRATED, false) && !global.isFile && legacyBundle.isFile) {
                Tuils.copy(legacyBundle, global)
            }
            val applyAll = if (prefs.contains(LEGACY_ENABLED)) prefs.getBoolean(LEGACY_ENABLED, true) else true
            writeLegacyStateFile(dir, applyAll)
        }
        normalizeFrameFolder(dir)
        prefs.edit().putBoolean(MIGRATED, true).apply()
    }

    @Synchronized
    private fun currentState(): FrameState {
        cachedState?.let { return it }
        return readStateOrDefault(frameDir()).also { cachedState = it }
    }

    private fun readStateOrDefault(dir: File): FrameState = try {
        readState(dir)
    } catch (error: Exception) {
        Log.e("TUI-FRAME", "Unable to read frame settings; using default borders", error)
        FrameState(true, HashMap())
    }

    private fun readState(dir: File): FrameState {
        val file = File(dir, STATE_FILE)
        require(file.isFile && file.length() <= MAX_MANIFEST_BYTES) { "Frame settings are missing or invalid." }
        val state = JSONObject(file.readText(Charsets.UTF_8))
        return when (state.getInt("schema")) {
            1 -> {
                require(state.stringSet() == setOf("schema", "applyToAll")) { "Unsupported frame settings." }
                val assignments = HashMap<String, String>()
                for ((key, legacy) in legacyFiles(dir)) {
                    if (legacy.isFile) assignments[key] = sha256(FileInputStream(legacy).use {
                        it.readLimited(MAX_BUNDLE_BYTES, "Frame bundle is too large.")
                    })
                }
                FrameState(state.getBoolean("applyToAll"), assignments)
            }
            2 -> {
                require(state.stringSet() == setOf("schema", "applyToAll", "assignments")) { "Unsupported frame settings." }
                val values = state.getJSONObject("assignments")
                val assignments = HashMap<String, String>()
                for (key in values.keys()) {
                    val id = values.getString(key)
                    require(ASSET_ID.matches(id)) { "Invalid frame library reference." }
                    assignments[key] = id
                }
                FrameState(state.getBoolean("applyToAll"), assignments)
            }
            3 -> {
                require(state.stringSet() == setOf("schema", "applyToAll", "assignments", "packs")) {
                    "Unsupported frame settings."
                }
                val assignments = state.getJSONObject("assignments").assetMap()
                FrameState(state.getBoolean("applyToAll"), assignments)
            }
            else -> throw IllegalArgumentException("Unsupported frame settings.")
        }
    }

    private fun writeStateFile(dir: File, value: FrameState) {
        val state = File(dir, STATE_FILE)
        val temp = File(dir, "$STATE_FILE.tmp")
        val backup = File(dir, "$STATE_FILE.old")
        temp.delete()
        backup.delete()
        val assignments = JSONObject()
        for ((key, assetId) in value.assignments.toSortedMap()) assignments.put(key, assetId)
        temp.writeText(
            JSONObject().put("schema", 2).put("applyToAll", value.applyToAll)
                .put("assignments", assignments).toString(2),
            Charsets.UTF_8
        )
        if (state.exists()) check(state.renameTo(backup)) { "Unable to save frame settings." }
        if (!temp.renameTo(state)) {
            if (backup.exists()) backup.renameTo(state)
            throw IllegalStateException("Unable to save frame settings.")
        }
        backup.delete()
    }

    private fun writeLegacyStateFile(dir: File, applyAll: Boolean) {
        File(dir, STATE_FILE).writeText(JSONObject().put("schema", 1).put("applyToAll", applyAll).toString(2))
    }

    private fun normalizeFrameFolder(dir: File) {
        val schema = try {
            stateSchema(dir)
        } catch (error: Exception) {
            Log.e("TUI-FRAME", "Unable to read frame settings; leaving them for repair", error)
            return
        }
        if (schema >= 2) return
        val state = try {
            readState(dir)
        } catch (error: Exception) {
            Log.e("TUI-FRAME", "Unable to migrate frame settings; leaving them for repair", error)
            return
        }
        for ((key, legacy) in legacyFiles(dir)) {
            if (!legacy.isFile) continue
            val bytes = FileInputStream(legacy).use { it.readLimited(MAX_BUNDLE_BYTES, "Frame bundle is too large.") }
            val id = sha256(bytes)
            val asset = assetFile(dir, id)
            if (!asset.isFile) saveFile(asset, bytes)
            state.assignments[key] = id
        }
        writeStateFile(dir, state)
        for (legacy in legacyFiles(dir).values) legacy.delete()
    }

    private fun applyFrameFolder(source: File) {
        val root = Tuils.getFolder()
        val temp = File(root, ".$FRAME_FOLDER.importing")
        val backup = File(root, ".$FRAME_FOLDER.old")
        if (temp.exists()) Tuils.delete(temp)
        if (backup.exists()) Tuils.delete(backup)
        copyFrameFolder(source, temp)

        val current = frameDir()
        if (current.exists()) check(current.renameTo(backup)) { "Unable to replace frame settings." }
        if (!temp.renameTo(current)) {
            if (backup.exists()) backup.renameTo(current)
            throw IllegalStateException("Unable to apply frame settings.")
        }
        if (backup.exists()) Tuils.delete(backup)
        clearCache()
    }

    private fun saveFile(active: File, bytes: ByteArray) {
        val dir = active.parentFile ?: throw IllegalStateException("Unable to create the frame folder.")
        check(dir.exists() || dir.mkdirs()) { "Unable to create the frame folder." }
        val temp = File(dir, "${active.name}.tmp")
        val backup = File(dir, "${active.name}.old")
        temp.delete()
        backup.delete()
        FileOutputStream(temp).use { it.write(bytes) }
        check(temp.length() == bytes.size.toLong()) { "Unable to save the frame bundle." }

        if (active.exists()) check(active.renameTo(backup)) { "Unable to replace the active frame." }
        if (!temp.renameTo(active)) {
            if (backup.exists()) backup.renameTo(active)
            throw IllegalStateException("Unable to activate the imported frame.")
        }
        backup.delete()
    }

    private fun loadFrameFile(file: File, key: String): LoadedFrame? = try {
        if (!file.isFile) null else parseBundle(FileInputStream(file).use {
            it.readLimited(MAX_BUNDLE_BYTES, "Frame bundle is too large.")
        }).toLoadedFrame()
    } catch (error: Exception) {
        Log.e("TUI-FRAME", "Unable to load $key frame", error)
        null
    }

    private fun loadFramePreview(file: File, key: String): FramePreview? = try {
        if (!file.isFile) null else parseBundle(FileInputStream(file).use {
            it.readLimited(MAX_BUNDLE_BYTES, "Frame bundle is too large.")
        }).let { parsed -> FramePreview(parsed.name, decodePreview(parsed.png)) }
    } catch (error: Exception) {
        Log.e("TUI-FRAME", "Unable to preview $key frame", error)
        null
    }

    private fun decodePreview(image: ByteArray): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(image, 0, image.size, bounds)
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > FRAME_PREVIEW_MAX_PX) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        return requireNotNull(BitmapFactory.decodeByteArray(image, 0, image.size, options)) {
            "Unable to decode frame preview."
        }
    }

    private fun copyFrameFolder(source: File, destination: File) {
        if (destination.exists()) Tuils.delete(destination)
        check(destination.mkdirs()) { "Unable to create frame export folder." }
        for (file in source.listFiles().orEmpty()) {
            if (!file.isFile || file.name.endsWith(".tmp") || file.name.endsWith(".old")) continue
            Tuils.copy(file, File(destination, file.name))
        }
    }

    private fun frameDir() = File(Tuils.getFolder(), FRAME_FOLDER)
    private fun assignmentKey(target: FrameTarget?) = target?.id ?: "global"
    private fun legacyBundleFile(directory: File, target: FrameTarget?) =
        File(directory, target?.let { "${it.id}.retui-frame" } ?: GLOBAL_BUNDLE)
    private fun assetFile(directory: File, assetId: String) = File(directory, "$ASSET_PREFIX$assetId.retui-frame")
    private fun assetId(fileName: String): String? = fileName
        .takeIf { it.startsWith(ASSET_PREFIX) && it.endsWith(".retui-frame") }
        ?.removePrefix(ASSET_PREFIX)?.removeSuffix(".retui-frame")?.takeIf(ASSET_ID::matches)

    private fun legacyFiles(directory: File): Map<String, File> = buildMap {
        put("global", legacyBundleFile(directory, null))
        FrameTarget.entries.forEach { put(it.id, legacyBundleFile(directory, it)) }
    }

    private fun stateSchema(directory: File): Int =
        JSONObject(File(directory, STATE_FILE).readText(Charsets.UTF_8)).getInt("schema")

    private fun removeUnreferencedAssets(directory: File) {
        val state = readState(directory)
        val referenced = state.assignments.values.toSet()
        directory.listFiles().orEmpty().forEach { file ->
            val id = assetId(file.name)
            if (id != null && id !in referenced) file.delete()
        }
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes).joinToString("") { "%02x".format(it) }

    private fun imageBounds(image: ByteArray): Pair<Int, Int> {
        require(hasPngSignature(image)) {
            "Frame image is not a PNG image."
        }
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(image, 0, image.size, options)
        require(options.outWidth in 1..MAX_IMAGE_SIZE && options.outHeight in 1..MAX_IMAGE_SIZE) {
            "Frame image must be no larger than 2048 x 2048."
        }
        return options.outWidth to options.outHeight
    }

    internal fun hasPngSignature(image: ByteArray): Boolean =
        image.size >= PNG_SIGNATURE.size && image.copyOfRange(0, PNG_SIGNATURE.size).contentEquals(PNG_SIGNATURE)

    internal fun defaultPngSpec(width: Int, height: Int): FrameSpec {
        require(width == height && width >= 24 && width % 24 == 0) {
            "PNG frames must be square and sized 24 x 24, 48 x 48, 72 x 72, and so on."
        }
        val cell = width / 3
        return FrameSpec(
            leftPx = cell,
            topPx = cell,
            rightPx = cell,
            bottomPx = cell,
            leftDp = 8f,
            topDp = 8f,
            rightDp = 8f,
            bottomDp = 8f,
            topMode = "tile",
            rightMode = "tile",
            bottomMode = "tile",
            leftMode = "tile",
            centerMode = "stretch",
            filtering = "nearest"
        )
    }

    private fun buildBundle(name: String, spec: FrameSpec, png: ByteArray): ByteArray {
        val slices = JSONObject().put("left", spec.leftPx).put("top", spec.topPx)
            .put("right", spec.rightPx).put("bottom", spec.bottomPx)
        val borders = JSONObject().put("left", spec.leftDp).put("top", spec.topDp)
            .put("right", spec.rightDp).put("bottom", spec.bottomDp)
        val modes = JSONObject().put("left", spec.leftMode).put("top", spec.topMode)
            .put("right", spec.rightMode).put("bottom", spec.bottomMode).put("center", spec.centerMode)
        val manifest = JSONObject()
            .put("type", "retui-frame")
            .put("schema", 1)
            .put("name", name)
            .put("image", "frame.png")
            .put("slicePx", slices)
            .put("borderDp", borders)
            .put("modes", modes)
            .put("filtering", spec.filtering)
            .toString(2).toByteArray(Charsets.UTF_8)
        return ByteArrayOutputStream(manifest.size + png.size + 256).also { out ->
            ZipOutputStream(out).use { zip ->
                for ((entryName, data) in listOf("manifest.json" to manifest, "frame.png" to png)) {
                    zip.putNextEntry(ZipEntry(entryName).apply { time = 315532800000L })
                    zip.write(data)
                    zip.closeEntry()
                }
            }
        }.toByteArray()
    }

    private fun parseBundle(bytes: ByteArray): ParsedFrame {
        var manifestBytes: ByteArray? = null
        var pngBytes: ByteArray? = null
        val seen = HashSet<String>()
        ZipInputStream(BufferedInputStream(ByteArrayInputStream(bytes))).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                require(!entry.isDirectory) { "Frame bundle cannot contain folders." }
                val name = entry.name
                require(name == "manifest.json" || name == "frame.png") { "Unsupported frame bundle file: $name" }
                require(seen.add(name)) { "Frame bundle contains duplicate files." }
                when (name) {
                    "manifest.json" -> manifestBytes = zip.readLimited(MAX_MANIFEST_BYTES, "Frame manifest is too large.")
                    "frame.png" -> pngBytes = zip.readLimited(MAX_PNG_BYTES, "Frame image is too large.")
                }
                zip.closeEntry()
            }
        }
        require(seen == setOf("manifest.json", "frame.png")) { "Frame bundle must contain only manifest.json and frame.png." }

        val manifest = JSONObject(String(requireNotNull(manifestBytes), Charsets.UTF_8))
        require(manifest.stringSet() == setOf("type", "schema", "name", "image", "slicePx", "borderDp", "modes", "filtering")) {
            "Frame manifest has missing or unsupported fields."
        }
        require(manifest.getString("type") == "retui-frame") { "Unsupported frame type." }
        require(manifest.getInt("schema") == 1) { "Unsupported frame schema." }
        require(manifest.getString("image") == "frame.png") { "Frame image must be frame.png." }
        val name = manifest.getString("name").trim()
        require(name.isNotEmpty() && name.length <= 80) { "Frame name must be 1 to 80 characters." }

        val spec = parseSpec(manifest)

        val image = requireNotNull(pngBytes)
        val (width, height) = imageBounds(image)
        require(spec.leftPx + spec.rightPx < width && spec.topPx + spec.bottomPx < height) {
            "Frame slices must leave a center region."
        }
        return ParsedFrame(name, spec, image)
    }

    private fun parseSpec(manifest: JSONObject): FrameSpec {
        val slice = manifest.getJSONObject("slicePx")
        val border = manifest.getJSONObject("borderDp")
        val modes = manifest.getJSONObject("modes")
        val sides = setOf("left", "top", "right", "bottom")
        require(slice.stringSet() == sides && border.stringSet() == sides) { "Frame sides are incomplete." }
        require(modes.stringSet() == sides + "center") { "Frame modes are incomplete." }

        return FrameSpec(
            leftPx = slice.positiveInt("left"),
            topPx = slice.positiveInt("top"),
            rightPx = slice.positiveInt("right"),
            bottomPx = slice.positiveInt("bottom"),
            leftDp = border.dp("left"),
            topDp = border.dp("top"),
            rightDp = border.dp("right"),
            bottomDp = border.dp("bottom"),
            topMode = modes.edgeMode("top"),
            rightMode = modes.edgeMode("right"),
            bottomMode = modes.edgeMode("bottom"),
            leftMode = modes.edgeMode("left"),
            centerMode = modes.centerMode(),
            filtering = manifest.getString("filtering").also {
                require(it == "nearest" || it == "linear") { "Filtering must be nearest or linear." }
            }
        )
    }

    private fun ParsedFrame.toLoadedFrame(): LoadedFrame {
        val bitmap = requireNotNull(BitmapFactory.decodeByteArray(png, 0, png.size)) { "Unable to decode frame.png." }
        return LoadedFrame(name, spec, bitmap)
    }

    private fun InputStream.readLimited(limit: Int, message: String): ByteArray {
        val out = ByteArrayOutputStream(minOf(limit, 8192))
        val buffer = ByteArray(8192)
        var total = 0
        while (true) {
            val read = read(buffer)
            if (read < 0) break
            total += read
            require(total <= limit) { message }
            out.write(buffer, 0, read)
        }
        return out.toByteArray()
    }

    private fun JSONObject.stringSet(): Set<String> = keys().asSequence().toSet()

    private fun JSONObject.assetMap(): MutableMap<String, String> = HashMap<String, String>().also { result ->
        for (key in keys()) {
            val id = getString(key)
            require(ASSET_ID.matches(id)) { "Invalid frame library reference." }
            result[key] = id
        }
    }

    private fun isKnownRole(role: String): Boolean =
        role == "global" || FrameTarget.entries.any { it.id == role }

    private fun JSONObject.positiveInt(key: String): Int {
        val value = get(key)
        require(value is Number && value.toDouble().isFinite() && value.toDouble() == value.toInt().toDouble() && value.toInt() > 0) {
            "$key slice must be a positive whole number."
        }
        return value.toInt()
    }

    private fun JSONObject.dp(key: String): Float {
        val value = get(key)
        require(value is Number && value.toDouble().isFinite() && value.toDouble() in 0.0..256.0) {
            "$key border must be between 0 and 256 dp."
        }
        return value.toFloat()
    }

    private fun JSONObject.edgeMode(key: String): String = getString(key).also {
        require(it == "stretch" || it == "tile") { "$key mode must be stretch or tile." }
    }

    private fun JSONObject.centerMode(): String = getString("center").also {
        require(it == "stretch" || it == "tile" || it == "none") { "Center mode must be stretch, tile, or none." }
    }

    private data class ParsedFrame(val name: String, val spec: FrameSpec, val png: ByteArray)
    private data class FramePreview(val name: String, val bitmap: Bitmap)

    private val PNG_SIGNATURE = byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a)
}

data class FrameSpec(
    val leftPx: Int,
    val topPx: Int,
    val rightPx: Int,
    val bottomPx: Int,
    val leftDp: Float,
    val topDp: Float,
    val rightDp: Float,
    val bottomDp: Float,
    val topMode: String,
    val rightMode: String,
    val bottomMode: String,
    val leftMode: String,
    val centerMode: String,
    val filtering: String
)

class LoadedFrame(val name: String, val spec: FrameSpec, val bitmap: Bitmap) {
    val slices: Array<Array<Bitmap>>

    init {
        val x = intArrayOf(0, spec.leftPx, bitmap.width - spec.rightPx, bitmap.width)
        val y = intArrayOf(0, spec.topPx, bitmap.height - spec.bottomPx, bitmap.height)
        slices = Array(3) { row ->
            Array(3) { column ->
                Bitmap.createBitmap(
                    bitmap,
                    x[column],
                    y[row],
                    x[column + 1] - x[column],
                    y[row + 1] - y[row]
                )
            }
        }
    }
}
