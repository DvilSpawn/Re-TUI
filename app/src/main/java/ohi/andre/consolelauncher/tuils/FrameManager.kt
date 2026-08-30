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
import java.util.Locale
import java.util.UUID
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
    private const val MAX_PACK_BYTES = 32 * 1024 * 1024
    private const val PREFS = "retui_frames"
    private const val LEGACY_ENABLED = "enabled"
    private const val FRAMES_ENABLED = "frames_enabled"
    private const val MIGRATED = "portable_storage_migrated"
    private const val GLOBAL_BUNDLE = "active.retui-frame"
    private const val ASSET_PREFIX = "library-"
    private const val MAX_MANIFEST_BYTES = 32 * 1024
    private const val MAX_README_BYTES = 256 * 1024
    private const val MAX_PNG_BYTES = 4 * 1024 * 1024
    private const val MAX_IMAGE_SIZE = 2048
    private const val FRAME_PREVIEW_MAX_PX = 256
    internal const val SPROUT_LANDS_PACK_ID =
        "1ac07ff867ef164c93e152ba09bb63806fea16f59cf730f0ff2b7e6b0b9b5383"
    private const val SPROUT_LANDS_PACK_NAME = "Sprout Lands — Art by Cup Nooble"
    private const val SPROUT_LANDS_ASSET_FOLDER = "frame_packs/sprout_lands"
    private val ASSET_ID = Regex("[0-9a-f]{64}")
    private val PACK_FILE_NAME = Regex("[a-z0-9][a-z0-9_-]*\\.png")

    private val cache = object : LruCache<String, LoadedFrame>(
        (Runtime.getRuntime().maxMemory() / 16L).coerceIn(4L * 1024 * 1024, 16L * 1024 * 1024).toInt()
    ) {
        override fun sizeOf(key: String, value: LoadedFrame): Int = value.bitmap.allocationByteCount
    }
    private val invalidAssets = HashSet<String>()
    @Volatile private var cachedState: FrameState? = null
    @Volatile private var bundledPackReady = false

    data class FrameAsset(val id: String, val name: String)

    data class FrameDetails(
        val name: String,
        val spec: FrameSpec,
        val width: Int,
        val height: Int
    )

    data class FramePack(
        val id: String,
        val name: String,
        val applyToAll: Boolean,
        val assignments: Map<String, String>
    )

    internal data class UiPackageRole(val file: String, val spec: FrameSpec)
    internal data class UiPackageManifest(val name: String, val roles: Map<String, UiPackageRole>)

    private data class BundledFrame(
        val targets: Set<FrameTarget>,
        val fileName: String,
        val displayName: String,
        val spec: FrameSpec
    )

    private val SPROUT_LANDS_FRAMES = listOf(
        BundledFrame(
            setOf(
                FrameTarget.STATUS_GROUP, FrameTarget.STATUS_RAM, FrameTarget.STATUS_DEVICE,
                FrameTarget.STATUS_TIME, FrameTarget.STATUS_BATTERY, FrameTarget.STATUS_STORAGE,
                FrameTarget.STATUS_NETWORK, FrameTarget.STATUS_NOTES, FrameTarget.STATUS_WEATHER,
                FrameTarget.STATUS_UNLOCK, FrameTarget.STATUS_ASCII, FrameTarget.OUTPUT,
                FrameTarget.INPUT, FrameTarget.SUGGESTIONS, FrameTarget.MUSIC,
                FrameTarget.NOTIFICATIONS, FrameTarget.MODULES, FrameTarget.MODULE_DOCK,
                FrameTarget.APP_DRAWER, FrameTarget.WIDGET_DRAWER, FrameTarget.FILES,
                FrameTarget.OVERLAYS, FrameTarget.SETTINGS, FrameTarget.DIALOG,
                FrameTarget.HEADER, FrameTarget.LIST_ITEM, FrameTarget.UI_INPUT,
                FrameTarget.BUTTON, FrameTarget.BUTTON_PRIMARY
            ),
            "rectangle_button.png",
            "Sprout Lands panel and button",
            bundledSpec(6, 6, 6, 8)
        ),
        BundledFrame(
            setOf(FrameTarget.BUTTON_PRESSED, FrameTarget.LIST_ITEM_SELECTED),
            "rectangle_button_pressed.png",
            "Sprout Lands pressed button",
            bundledSpec(6, 6, 6, 6)
        ),
        BundledFrame(
            setOf(FrameTarget.TOOLBAR, FrameTarget.ICON_BUTTON, FrameTarget.CONTROLS),
            "button.png",
            "Sprout Lands icon button",
            bundledSpec(6, 6, 6, 8)
        ),
        BundledFrame(setOf(FrameTarget.TOGGLE_OFF), "toggle_off.png", "Sprout Lands toggle off", bundledSpec(17, 8, 4, 8)),
        BundledFrame(setOf(FrameTarget.TOGGLE_ON), "toggle_on.png", "Sprout Lands toggle on", bundledSpec(4, 8, 17, 8)),
        BundledFrame(
            setOf(FrameTarget.SLIDER_TRACK, FrameTarget.SLIDER_PROGRESS),
            "slider.png",
            "Sprout Lands slider",
            bundledSpec(3, 3, 3, 3)
        ),
        BundledFrame(setOf(FrameTarget.SLIDER_THUMB), "slider_thumb.png", "Sprout Lands slider thumb", bundledSpec(5, 6, 5, 8)),
        BundledFrame(setOf(FrameTarget.KEYBOARD), "keys.png", "Sprout Lands keyboard", bundledSpec(5, 6, 5, 8))
    )

    internal fun sproutLandsTargets(): Set<FrameTarget> =
        SPROUT_LANDS_FRAMES.flatMapTo(LinkedHashSet()) { it.targets }

    data class SharedFrameSource(
        val assetId: String,
        val imageId: String,
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
        val assignments: MutableMap<String, String>,
        val packs: MutableMap<String, FramePack> = HashMap(),
        var activePackId: String? = null
    ) {
        fun copyState() = FrameState(applyToAll, HashMap(assignments), HashMap(packs), activePackId)
    }

    class EditSession internal constructor(
        private val directory: File,
        private val original: FrameState
    ) {
        private val state = original.copyState()
        var applyToAll: Boolean
            get() = state.applyToAll
            set(value) {
                if (state.applyToAll != value) {
                    state.applyToAll = value
                    state.activePackId = null
                }
            }
        private var libraryChanged = false
        private var currentPackId = state.activePackId
        private val previews = HashMap<String, FramePreview?>()

        fun hasChanges(): Boolean = libraryChanged || state != original

        fun isStale(): Boolean = isStale(currentState())

        internal fun isStale(active: FrameState): Boolean = active != original

        fun selectedAssetId(target: FrameTarget?): String? = state.assignments[assignmentKey(target)]

        fun select(target: FrameTarget?, assetId: String?) {
            val key = assignmentKey(target)
            if (assetId == null) state.assignments.remove(key)
            else {
                require(ASSET_ID.matches(assetId)) { "Invalid frame selection." }
                state.assignments[key] = assetId
            }
            state.activePackId = null
        }

        fun hasAssignedFrame(target: FrameTarget?): Boolean = selectedAssetId(target) != null

        fun assignedName(target: FrameTarget?): String? = load(selectedAssetId(target))?.name

        fun assignedDetails(target: FrameTarget?): FrameDetails? =
            load(selectedAssetId(target))?.let { FrameDetails(it.name, it.spec, it.width, it.height) }

        fun previewBitmap(target: FrameTarget?): Bitmap? = load(selectedAssetId(target))?.bitmap

        fun assignedFrameIsInvalid(target: FrameTarget?): Boolean =
            hasAssignedFrame(target) && previewBitmap(target) == null

        fun packs(): List<FramePack> = state.packs.values
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })

        fun activePackId(): String? = state.activePackId

        fun currentPackId(): String? = currentPackId
            ?.takeIf(state.packs::containsKey)
            ?.takeUnless(::isBuiltInPack)

        fun packNameError(name: String): String? {
            val clean = name.trim()
            if (clean.isEmpty() || clean.length > 80) return "Pack name must be 1 to 80 characters."
            if (state.packs.values.any { it.name.equals(clean, ignoreCase = true) }) {
                return "A frame pack with that name already exists."
            }
            return null
        }

        fun createPack(name: String): FramePack {
            val clean = name.trim()
            require(packNameError(clean) == null) { packNameError(clean) ?: "Invalid frame pack name." }
            val id = sha256(UUID.randomUUID().toString().toByteArray(Charsets.UTF_8))
            return FramePack(id, clean, state.applyToAll, HashMap(state.assignments)).also {
                state.packs[id] = it
                state.activePackId = id
                currentPackId = id
            }
        }

        fun replacePack(packId: String): FramePack {
            require(!isBuiltInPack(packId)) { "Built-in frame packs cannot be replaced." }
            val current = requireNotNull(state.packs[packId]) { "Frame pack is missing." }
            return current.copy(applyToAll = state.applyToAll, assignments = HashMap(state.assignments)).also {
                state.packs[packId] = it
                state.activePackId = packId
                currentPackId = packId
            }
        }

        fun applyPack(packId: String) {
            val pack = requireNotNull(state.packs[packId]) { "Frame pack is missing." }
            state.applyToAll = pack.applyToAll
            state.assignments.clear()
            state.assignments.putAll(pack.assignments)
            state.activePackId = packId
            currentPackId = packId
        }

        fun deletePack(packId: String): Boolean {
            require(!isBuiltInPack(packId)) { "Built-in frame packs cannot be deleted." }
            requireNotNull(state.packs.remove(packId)) { "Frame pack is missing." }
            if (currentPackId == packId) currentPackId = null
            val wasActive = state.activePackId == packId
            if (wasActive) {
                state.applyToAll = true
                state.assignments.clear()
                state.activePackId = null
            }
            return wasActive
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

        fun importUiPackageZip(input: InputStream): FramePack {
            var manifestBytes: ByteArray? = null
            val pngs = LinkedHashMap<String, ByteArray>()
            val seen = HashSet<String>()
            var totalBytes = 0L
            ZipInputStream(BufferedInputStream(input)).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    val name = entry.name
                    require(seen.add(name)) { "UI package ZIP contains duplicate entry: $name" }
                    when {
                        name == "frames/" && entry.isDirectory -> Unit
                        name == "manifest.json" && !entry.isDirectory -> {
                            manifestBytes = zip.readLimited(
                                MAX_MANIFEST_BYTES, "UI package manifest is too large."
                            ).also { totalBytes += it.size }
                        }
                        name == "readme.md" && !entry.isDirectory -> {
                            totalBytes += zip.readLimited(
                                MAX_README_BYTES, "UI package readme is too large."
                            ).size
                        }
                        name.startsWith("frames/") && !entry.isDirectory &&
                            name.count { it == '/' } == 1 && PACK_FILE_NAME.matches(name.substringAfter('/')) -> {
                            val fileName = name.substringAfter('/')
                            val png = zip.readLimited(MAX_PNG_BYTES, "$fileName is too large.")
                            require(pngs.put(fileName, png) == null) {
                                "UI package ZIP contains duplicate PNG: $fileName"
                            }
                            totalBytes += png.size
                        }
                        else -> throw IllegalArgumentException(
                            "Unsupported UI package ZIP entry: $name"
                        )
                    }
                    require(totalBytes <= MAX_PACK_BYTES) { "UI package is too large." }
                    zip.closeEntry()
                }
            }
            val bytes = requireNotNull(manifestBytes) { "UI package ZIP is missing manifest.json." }
            return installUiPackage(parseUiPackageManifest(bytes), bytes.size, pngs)
        }

        private fun installUiPackage(
            manifest: UiPackageManifest,
            manifestBytes: Int,
            pngs: Map<String, ByteArray>
        ): FramePack {
            require(packNameError(manifest.name) == null) {
                packNameError(manifest.name) ?: "Invalid UI package name."
            }
            val referencedFiles = manifest.roles.values.mapTo(LinkedHashSet()) { it.file }
            require(pngs.keys == referencedFiles) {
                "UI package manifest roles must exactly match the PNGs in frames/."
            }
            require(manifestBytes.toLong() + pngs.values.sumOf { it.size.toLong() } <= MAX_PACK_BYTES) {
                "UI package is too large."
            }
            for ((fileName, png) in pngs) {
                val (width, height) = imageBounds(png)
                manifest.roles.filterValues { it.file == fileName }.forEach { (role, definition) ->
                    require(frameSpecError(definition.spec, width, height) == null) {
                        "$role: ${frameSpecError(definition.spec, width, height)}"
                    }
                }
            }

            val importedAssets = HashMap<Pair<String, FrameSpec>, String>()
            val assignments = LinkedHashMap<String, String>()
            manifest.roles.forEach { (role, definition) ->
                val key = definition.file to definition.spec
                assignments[role] = importedAssets.getOrPut(key) {
                    val label = definition.file.removeSuffix(".png").replace('_', ' ')
                    registerBundle(
                        buildBundle(
                            "${manifest.name} - $label".take(80),
                            definition.spec,
                            requireNotNull(pngs[definition.file])
                        )
                    ).id
                }
            }
            return installImportedPack(manifest.name, assignments)
        }

        internal fun installImportedPack(name: String, assignments: Map<String, String>): FramePack {
            require(packNameError(name) == null) { packNameError(name) ?: "Invalid UI package name." }
            require(assignments.isNotEmpty() && assignments.keys.all { isKnownRole(it) && it != "global" }) {
                "UI package contains an unsupported role."
            }
            require(assignments.values.all(ASSET_ID::matches)) { "UI package contains an invalid frame asset." }
            val id = sha256(UUID.randomUUID().toString().toByteArray(Charsets.UTF_8))
            return FramePack(id, name.trim(), false, HashMap(assignments)).also {
                state.packs[id] = it
                libraryChanged = true
            }
        }

        fun updateFrameSpec(target: FrameTarget?, spec: FrameSpec) {
            val oldId = requireNotNull(selectedAssetId(target)) { "No frame is assigned." }
            val parsed = parseBundle(FileInputStream(assetFile(directory, oldId)).use {
                it.readLimited(MAX_BUNDLE_BYTES, "Frame bundle is too large.")
            })
            val (width, height) = imageBounds(parsed.png)
            require(frameSpecError(spec, width, height) == null) {
                frameSpecError(spec, width, height) ?: "Invalid frame settings."
            }
            select(target, registerBundle(buildBundle(parsed.name, spec, parsed.png)).id)
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
            return FrameAsset(id, parsed.name)
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

    fun isEnabled(context: Context): Boolean {
        ensureMigrated(context)
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(FRAMES_ENABLED, true)
    }

    fun setEnabled(context: Context, enabled: Boolean) {
        ensureMigrated(context)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(FRAMES_ENABLED, enabled).apply()
    }

    fun drawable(
        context: Context,
        target: FrameTarget = FrameTarget.CONTROLS,
        intrinsicDp: Float? = null
    ): NineSliceFrameDrawable? {
        if (!isEnabled(context)) return null
        val state = currentState()
        val key = assignmentKey(resolvedTarget(state.applyToAll, target))
        val assetId = state.assignments[key] ?: return null
        return loadAsset(assetId)?.let {
            NineSliceFrameDrawable(it, context.resources.displayMetrics.density, intrinsicDp)
        }
    }

    fun sharedSource(context: Context, target: FrameTarget): SharedFrameSource? {
        if (!isEnabled(context)) return null
        val state = currentState()
        val assetId = state.assignments[assignmentKey(resolvedTarget(state.applyToAll, target))] ?: return null
        return try {
            val parsed = parseBundle(FileInputStream(assetFile(frameDir(), assetId)).use {
                it.readLimited(MAX_BUNDLE_BYTES, "Frame bundle is too large.")
            })
            SharedFrameSource(
                assetId,
                imageContentId(parsed.png),
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
        val destination = File(destinationRoot, FRAME_FOLDER)
        copyFrameFolder(frameDir(), destination)
        removeUnreferencedAssets(destination)
        removeBundledAssets(destination)
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
        require(state.packs.values.all { pack -> pack.assignments.keys.all(::isKnownRole) }) {
            "Frame pack contains an unknown surface."
        }
    }

    fun portableFiles(root: File): List<File> {
        val dir = File(root, FRAME_FOLDER)
        if (!dir.isDirectory) return emptyList()
        validatePortableState(root)
        val state = readState(dir)
        val referenced = exportableAssetIds(state)
        return dir.listFiles().orEmpty().filter {
            it.isFile && (it.name == STATE_FILE || assetId(it.name)?.let(referenced::contains) == true || stateSchema(dir) == 1)
        }.sortedBy { it.name }
    }

    internal fun isBuiltInPack(packId: String): Boolean = packId == SPROUT_LANDS_PACK_ID

    internal fun bundledAssetFileNames(root: File): Set<String> {
        val dir = File(root, FRAME_FOLDER)
        if (!File(dir, STATE_FILE).isFile) return emptySet()
        return bundledAssetIds(readStateOrDefault(dir)).mapTo(HashSet()) {
            assetFile(dir, it).name
        }
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
        bundledPackReady = false
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
        if (!bundledPackReady) {
            installSproutLandsPack(context, dir)
            bundledPackReady = true
        }
        prefs.edit().putBoolean(MIGRATED, true).apply()
    }

    private fun installSproutLandsPack(context: Context, dir: File) {
        val assignments = LinkedHashMap<String, String>()
        for (frame in SPROUT_LANDS_FRAMES) {
            val png = context.assets.open("$SPROUT_LANDS_ASSET_FOLDER/${frame.fileName}").use {
                it.readLimited(MAX_PNG_BYTES, "Bundled frame image is too large.")
            }
            val (width, height) = imageBounds(png)
            require(frameSpecError(frame.spec, width, height) == null) {
                frameSpecError(frame.spec, width, height) ?: "Invalid bundled frame settings."
            }
            val bundle = buildBundle(frame.displayName, frame.spec, png)
            val id = sha256(bundle)
            val destination = assetFile(dir, id)
            if (!destination.isFile) saveFile(destination, bundle)
            frame.targets.forEach { assignments[it.id] = id }
        }

        val state = readStateOrDefault(dir)
        if (mergeBuiltInPack(state, assignments)) {
            writeStateFile(dir, state)
            removeUnreferencedAssets(dir)
            cachedState = null
        }
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
                val packs = HashMap<String, FramePack>()
                val values = state.getJSONObject("packs")
                for (id in values.keys()) {
                    require(ASSET_ID.matches(id)) { "Invalid frame pack reference." }
                    val value = values.getJSONObject(id)
                    require(value.stringSet() == setOf("name", "roles")) { "Unsupported frame pack record." }
                    val name = value.getString("name").trim()
                    require(name.isNotEmpty() && name.length <= 80) { "Frame pack name must be 1 to 80 characters." }
                    val roles = value.getJSONObject("roles").assetMap()
                    require(roles.keys.all(::isKnownRole)) { "Frame pack contains an unknown surface." }
                    packs[id] = FramePack(id, name, "global" in roles, roles)
                }
                val active = packs.values.firstOrNull {
                    it.applyToAll == state.getBoolean("applyToAll") && it.assignments == assignments
                }?.id
                FrameState(state.getBoolean("applyToAll"), assignments, packs, active)
            }
            4 -> {
                require(state.stringSet() == setOf("schema", "applyToAll", "assignments", "packs", "activePackId")) {
                    "Unsupported frame settings."
                }
                val assignments = state.getJSONObject("assignments").assetMap()
                require(assignments.keys.all(::isKnownRole)) { "Frame settings contain an unknown surface." }
                val packs = HashMap<String, FramePack>()
                val values = state.getJSONObject("packs")
                for (id in values.keys()) {
                    require(ASSET_ID.matches(id)) { "Invalid frame pack reference." }
                    val value = values.getJSONObject(id)
                    require(value.stringSet() == setOf("name", "applyToAll", "assignments")) {
                        "Unsupported frame pack record."
                    }
                    val name = value.getString("name").trim()
                    require(name.isNotEmpty() && name.length <= 80) { "Frame pack name must be 1 to 80 characters." }
                    require(packs.values.none { it.name.equals(name, ignoreCase = true) }) {
                        "Frame pack names must be unique."
                    }
                    val packAssignments = value.getJSONObject("assignments").assetMap()
                    require(packAssignments.keys.all(::isKnownRole)) { "Frame pack contains an unknown surface." }
                    packs[id] = FramePack(id, name, value.getBoolean("applyToAll"), packAssignments)
                }
                val active = if (state.isNull("activePackId")) null else state.getString("activePackId").also {
                    require(it in packs) { "Active frame pack is missing." }
                }
                FrameState(state.getBoolean("applyToAll"), assignments, packs, active)
            }
            else -> throw IllegalArgumentException("Unsupported frame settings.")
        }
    }

    private fun writeStateFile(dir: File, value: FrameState) {
        require(value.assignments.keys.all(::isKnownRole) && value.packs.values.all {
            it.assignments.keys.all(::isKnownRole)
        }) { "Frame settings contain an unknown surface." }
        require(value.packs.values.map { it.name.lowercase(Locale.ROOT) }.toSet().size == value.packs.size) {
            "Frame pack names must be unique."
        }
        value.activePackId?.let { activeId ->
            val active = requireNotNull(value.packs[activeId]) { "Active frame pack is missing." }
            require(active.applyToAll == value.applyToAll && active.assignments == value.assignments) {
                "Active frame settings do not match their pack."
            }
        }
        val state = File(dir, STATE_FILE)
        val temp = File(dir, "$STATE_FILE.tmp")
        val backup = File(dir, "$STATE_FILE.old")
        temp.delete()
        backup.delete()
        val assignments = JSONObject()
        for ((key, assetId) in value.assignments.toSortedMap()) assignments.put(key, assetId)
        val packs = JSONObject()
        for ((id, pack) in value.packs.toSortedMap()) {
            val packAssignments = JSONObject()
            for ((key, assetId) in pack.assignments.toSortedMap()) packAssignments.put(key, assetId)
            packs.put(
                id,
                JSONObject().put("name", pack.name).put("applyToAll", pack.applyToAll)
                    .put("assignments", packAssignments)
            )
        }
        temp.writeText(
            JSONObject().put("schema", 4).put("applyToAll", value.applyToAll)
                .put("assignments", assignments).put("packs", packs)
                .put("activePackId", value.activePackId ?: JSONObject.NULL).toString(2),
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
        }).let { parsed ->
            val (width, height) = imageBounds(parsed.png)
            FramePreview(parsed.name, parsed.spec, width, height, decodePreview(parsed.png))
        }
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
        val referenced = referencedAssetIds(state)
        directory.listFiles().orEmpty().forEach { file ->
            val id = assetId(file.name)
            if (id != null && id !in referenced) file.delete()
        }
    }

    private fun removeBundledAssets(directory: File) {
        val state = readState(directory)
        bundledAssetIds(state).forEach { assetFile(directory, it).delete() }
    }

    internal fun mergeBuiltInPack(state: FrameState, assignments: Map<String, String>): Boolean {
        require(assignments.keys.all(::isKnownRole)) { "Built-in frame pack contains an unknown surface." }
        val next = FramePack(SPROUT_LANDS_PACK_ID, SPROUT_LANDS_PACK_NAME, false, HashMap(assignments))
        if (state.packs[SPROUT_LANDS_PACK_ID] == next) return false
        val wasActive = state.activePackId == SPROUT_LANDS_PACK_ID
        state.packs[SPROUT_LANDS_PACK_ID] = next
        if (wasActive) {
            state.applyToAll = false
            state.assignments.clear()
            state.assignments.putAll(assignments)
        }
        return true
    }

    internal fun referencedAssetIds(state: FrameState): Set<String> =
        state.assignments.values.toSet() + state.packs.values.flatMap { it.assignments.values }

    internal fun bundledAssetIds(state: FrameState): Set<String> =
        state.packs[SPROUT_LANDS_PACK_ID]?.assignments?.values?.toSet().orEmpty()

    internal fun exportableAssetIds(state: FrameState): Set<String> =
        referencedAssetIds(state) - bundledAssetIds(state)

    internal fun parseUiPackageManifest(bytes: ByteArray): UiPackageManifest {
        val manifest = JSONObject(String(bytes, Charsets.UTF_8))
        require(manifest.stringSet() == setOf("type", "schema", "name", "filtering", "roles")) {
            "UI package manifest has missing or unsupported fields."
        }
        require(manifest.getString("type") == "retui-frame-pack") { "Unsupported UI package type." }
        require(manifest.getInt("schema") == 2) { "Unsupported UI package schema." }
        val name = manifest.getString("name").trim()
        require(name.isNotEmpty() && name.length <= 80) { "UI package name must be 1 to 80 characters." }
        val filtering = manifest.getString("filtering")
        require(filtering == "nearest") { "UI package filtering must be nearest." }
        val rolesJson = manifest.getJSONObject("roles")
        require(rolesJson.length() > 0) { "UI package contains no roles." }
        val roles = LinkedHashMap<String, UiPackageRole>()
        for (role in rolesJson.keys()) {
            require(isKnownRole(role) && role != "global") { "Unsupported UI package role: $role" }
            val value = rolesJson.getJSONObject(role)
            require(value.stringSet() == setOf("file", "slicePx", "borderDp", "modes")) {
                "UI package role $role has missing or unsupported fields."
            }
            val file = value.getString("file")
            require(PACK_FILE_NAME.matches(file)) { "Invalid UI package filename: $file" }
            val expectedFile = uiPackageFileName(role)
            require(file == expectedFile) { "UI package role $role must use $expectedFile." }
            roles[role] = UiPackageRole(
                file,
                parseSpec(JSONObject(value.toString()).put("filtering", filtering))
            )
        }
        return UiPackageManifest(name, roles)
    }

    internal fun uiPackageFileName(role: String): String =
        if (role == FrameTarget.SUGGESTIONS.id) "suggestion_chip.png" else "$role.png"

    internal fun isUiPackageZipName(name: String): Boolean =
        name.lowercase(Locale.ROOT).endsWith(".retui_ui.zip")

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes).joinToString("") { "%02x".format(it) }

    internal fun imageContentId(png: ByteArray): String = sha256(png)

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

    private fun bundledSpec(left: Int, top: Int, right: Int, bottom: Int) = FrameSpec(
        leftPx = left,
        topPx = top,
        rightPx = right,
        bottomPx = bottom,
        leftDp = left.toFloat(),
        topDp = top.toFloat(),
        rightDp = right.toFloat(),
        bottomDp = bottom.toFloat(),
        topMode = "stretch",
        rightMode = "stretch",
        bottomMode = "stretch",
        leftMode = "stretch",
        centerMode = "stretch",
        filtering = "nearest"
    )

    internal fun frameSpecError(spec: FrameSpec, width: Int, height: Int): String? = when {
        listOf(spec.leftPx, spec.topPx, spec.rightPx, spec.bottomPx).any { it <= 0 } ->
            "Slice values must be positive whole pixels."
        spec.leftPx + spec.rightPx >= width || spec.topPx + spec.bottomPx >= height ->
            "Slices must leave a center region inside the ${width} x ${height} PNG."
        listOf(spec.leftDp, spec.topDp, spec.rightDp, spec.bottomDp).any { !it.isFinite() || it !in 0f..256f } ->
            "Borders must be between 0 and 256 dp."
        listOf(spec.leftMode, spec.topMode, spec.rightMode, spec.bottomMode).any { it != "stretch" && it != "tile" } ->
            "Edge modes must be stretch or tile."
        spec.centerMode !in setOf("stretch", "tile", "none") ->
            "Center mode must be stretch, tile, or none."
        spec.filtering != "nearest" && spec.filtering != "linear" ->
            "Filtering must be nearest or linear."
        else -> null
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
    private data class FramePreview(
        val name: String,
        val spec: FrameSpec,
        val width: Int,
        val height: Int,
        val bitmap: Bitmap
    )

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
