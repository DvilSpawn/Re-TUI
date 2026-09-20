package ohi.andre.consolelauncher.wallpaper

import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.AdapterView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import java.util.Locale
import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.commands.tuixt.TuixtDialog
import ohi.andre.consolelauncher.commands.tuixt.TuixtDialog.ConfirmAction
import ohi.andre.consolelauncher.commands.tuixt.TuixtTheme.styleHeader
import ohi.andre.consolelauncher.managers.settings.AppearanceSettings
import ohi.andre.consolelauncher.managers.settings.LauncherSettings
import ohi.andre.consolelauncher.managers.xml.options.Theme
import ohi.andre.consolelauncher.tuils.CrtOverlayDrawable
import ohi.andre.consolelauncher.tuils.LauncherSystemUi.applyFullscreen

class RetuiWallpaperActivity : ohi.andre.consolelauncher.localization.LocalizedAppCompatActivity() {
    private lateinit var root: FrameLayout
    private lateinit var preview: android.view.View
    private lateinit var colorSpinner: Spinner
    private lateinit var densityLabel: TextView
    private lateinit var heightLabel: TextView
    private lateinit var boundsLabel: TextView
    private lateinit var zoomLabel: TextView
    private lateinit var zoomMinus: Button
    private lateinit var zoomPlus: Button
    private val positionControls = mutableListOf<View>()
    private val tuningControls = mutableListOf<View>()
    private val topoColorControls = mutableListOf<Button>()
    private val pixelColorControls = mutableListOf<Button>()
    private lateinit var topoColorRow: View
    private lateinit var pixelColorRow: View
    private lateinit var settingsPanel: View
    private lateinit var panelParams: FrameLayout.LayoutParams
    private var scene = "csakura"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyFullscreen(this)

        root = FrameLayout(this)
        scene = RetuiWallpaperSettings.scene(this)
        preview = createPreview(scene)
        root.addView(preview, FrameLayout.LayoutParams(-1, -1))

        addPositionControl("↑", edge(Gravity.TOP or Gravity.CENTER_HORIZONTAL, top = 278)) { move(0f, -16f) }
        addPositionControl("↓", edge(Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL, bottom = 12)) { move(0f, 16f) }
        addPositionControl("←", edge(Gravity.START or Gravity.CENTER_VERTICAL)) { move(-16f, 0f) }
        addPositionControl("→", edge(Gravity.END or Gravity.CENTER_VERTICAL)) { move(16f, 0f) }

        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), dp(8), dp(8), dp(8))
            setBackgroundColor(Color.argb(184, 18, 14, 24))
        }
        settingsPanel = panel
        val selectors = row()
        selectors.addView(label(getString(R.string.editor_retuiwallpaperactivity_wallpaper_f00e8)))
        val scenes = listOf("csakura", "black hole", TopoNoiseView.SCENE, PixelDreamView.SCENE, "solid")
        selectors.addView(spinner(scenes, scenes.indexOf(scene).coerceAtLeast(0), ::switchScene))
        selectors.addView(label(getString(R.string.editor_retuiwallpaperactivity_color_34171)))
        colorSpinner = paletteSpinner()
        selectors.addView(colorSpinner)
        panel.addView(selectors)

        topoColorRow = row().apply {
            addView(label(getString(R.string.wallpaper_topo_colors)))
            listOf("BG", "LINES", "INDEX").forEachIndexed { index, name ->
                topoColorControls += compactControl(name) { showTopoColorPicker(index) }
                addView(topoColorControls.last())
            }
        }
        panel.addView(topoColorRow)

        pixelColorRow = row().apply {
            addView(label(getString(R.string.wallpaper_pixel_colors)))
            listOf("BG", "DIM", "MID", "LIT", "HOVER", "CREST").forEachIndexed { index, name ->
                pixelColorControls += compactControl(name) { showPixelColorPicker(index) }
                addView(pixelColorControls.last())
            }
        }
        panel.addView(pixelColorRow)

        val tuning = row()
        heightLabel = label(when (scene) {
            "black hole" -> getString(R.string.editor_retuiwallpaperactivity_tilt_aceae)
            TopoNoiseView.SCENE -> getString(R.string.wallpaper_topo_relief)
            else -> getString(R.string.editor_retuiwallpaperactivity_height_6ea6c)
        })
        tuning.addView(heightLabel)
        tuning.addView(compactControl("−") { adjustHeight(-0.05f) })
        tuning.addView(compactControl("+") { adjustHeight(0.05f) })
        zoomLabel = label(getString(R.string.editor_retuiwallpaperactivity_zoom_c6653))
        zoomMinus = compactControl("−") { adjustScale(-0.1f) }
        zoomPlus = compactControl("+") { adjustScale(0.1f) }
        tuning.addView(zoomLabel)
        tuning.addView(zoomMinus)
        tuning.addView(zoomPlus)
        panel.addView(tuning)
        tuningControls.add(tuning)

        val shape = row()
        boundsLabel = label(when (scene) {
            "black hole" -> getString(R.string.editor_retuiwallpaperactivity_radius_69ea4)
            TopoNoiseView.SCENE -> getString(R.string.wallpaper_topo_contours)
            else -> getString(R.string.editor_retuiwallpaperactivity_bounds_d399f)
        })
        shape.addView(boundsLabel)
        shape.addView(compactControl("−") { adjustWidth(-0.1f) })
        shape.addView(compactControl("+") { adjustWidth(0.1f) })
        densityLabel = label(when (scene) {
            "black hole" -> getString(R.string.editor_retuiwallpaperactivity_dust_1717e)
            TopoNoiseView.SCENE -> getString(R.string.wallpaper_topo_detail)
            else -> getString(R.string.editor_retuiwallpaperactivity_petals_5dc16)
        })
        shape.addView(densityLabel)
        shape.addView(compactControl("−") { adjustDensity(-1) })
        shape.addView(compactControl("+") { adjustDensity(1) })
        panel.addView(shape)
        tuningControls.add(shape)

        val regrow = row()
        regrow.addView(compactControl(getString(R.string.editor_retuiwallpaperactivity_regenerate_e63ad)) {
            when (val current = preview) {
                is CsakuraView -> current.regrow()
                is BlackHoleView -> current.regenerate()
                is TopoNoiseView -> current.regenerate()
                is PixelDreamView -> current.regenerate()
            }
        })
        panel.addView(regrow)
        tuningControls.add(regrow)

        val apply = row()
        apply.addView(compactControl(getString(R.string.editor_retuiwallpaperactivity_use_on_phone_d908b)) { useOnPhone() })
        panel.addView(apply)
        panelParams = FrameLayout.LayoutParams(-1, dp(318), Gravity.TOP).apply {
            leftMargin = dp(8); topMargin = dp(8); rightMargin = dp(8)
        }
        root.addView(panel, panelParams)
        setContentView(root)
        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            panelParams.topMargin = insets.getInsets(
                WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.displayCutout()
            ).top + dp(8)
            panel.layoutParams = panelParams
            insets
        }
        ViewCompat.requestApplyInsets(root)
        updateSceneControls()
    }

    private fun move(dx: Float, dy: Float) {
        when (val current = preview) {
            is CsakuraView -> { current.offsetX += dx; current.offsetY += dy }
            is BlackHoleView -> { current.offsetX += dx; current.offsetY += dy }
            is TopoNoiseView -> { current.offsetX += dx; current.offsetY += dy }
        }
    }

    private fun save() {
        RetuiWallpaperSettings.saveScene(this, scene)
        when (val current = preview) {
            is CsakuraView -> RetuiWallpaperSettings.save(
                this, current.offsetX, current.offsetY, current.treeScale,
                current.treeHeight, current.treeWidth, current.petalDensity, current.treeSeed,
                current.paletteName
            )
            is BlackHoleView -> {
                RetuiWallpaperSettings.save(
                    this, current.offsetX, current.offsetY, current.sceneScale,
                    current.diskTilt, current.diskWidth,
                    current.particleDensity, RetuiWallpaperSettings.treeSeed(this), RetuiWallpaperSettings.palette(this)
                )
                RetuiWallpaperSettings.saveBlackHolePalette(this, current.paletteName)
            }
            is TopoNoiseView -> RetuiWallpaperSettings.saveTopo(
                this, current.offsetX, current.offsetY, current.noiseScale, current.relief,
                current.contourDensity, current.seed, current.paletteName,
                current.color(0), current.color(1), current.color(2)
            )
            is PixelDreamView -> RetuiWallpaperSettings.savePixelDream(
                this, current.paletteName, current.currentSeed(),
                current.color(0), current.color(1), current.color(2), current.color(3),
                current.color(4), current.color(5)
            )
            is SolidColorView -> RetuiWallpaperSettings.saveSolidColor(this, hex(current.color))
        }
        sendBroadcast(Intent(RetuiWallpaperService.ACTION_REFRESH).setPackage(packageName))
    }

    private fun createPreview(name: String): android.view.View = when (name) {
        "black hole" -> BlackHoleView(this).apply { loadPosition() }
        TopoNoiseView.SCENE -> TopoNoiseView(this).apply { loadPosition() }
        PixelDreamView.SCENE -> PixelDreamView(this).apply { loadPosition() }
        "solid" -> SolidColorView(this)
        else -> CsakuraView(this).apply { loadPosition() }
    }.also { view ->
        if (AppearanceSettings.crtFilter()) {
            view.foreground = CrtOverlayDrawable(this).apply {
                setAccentColor(LauncherSettings.getColor(Theme.output_text_color))
            }
        }
    }

    private fun switchScene(name: String) {
        if (name == scene) return
        scene = name
        root.removeView(preview)
        preview = createPreview(scene)
        root.addView(preview, 0, FrameLayout.LayoutParams(-1, -1))
        densityLabel.text = when (scene) {
            "black hole" -> getString(R.string.editor_retuiwallpaperactivity_dust_1717e)
            TopoNoiseView.SCENE -> getString(R.string.wallpaper_topo_detail)
            else -> getString(R.string.editor_retuiwallpaperactivity_petals_5dc16)
        }
        heightLabel.text = when (scene) {
            "black hole" -> getString(R.string.editor_retuiwallpaperactivity_tilt_aceae)
            TopoNoiseView.SCENE -> getString(R.string.wallpaper_topo_relief)
            else -> getString(R.string.editor_retuiwallpaperactivity_height_6ea6c)
        }
        boundsLabel.text = when (scene) {
            "black hole" -> getString(R.string.editor_retuiwallpaperactivity_radius_69ea4)
            TopoNoiseView.SCENE -> getString(R.string.wallpaper_topo_contours)
            else -> getString(R.string.editor_retuiwallpaperactivity_bounds_d399f)
        }
        updateSceneControls()
        val replacement = paletteSpinner()
        (colorSpinner.parent as ViewGroup).let { parent ->
            val index = parent.indexOfChild(colorSpinner)
            parent.removeView(colorSpinner)
            colorSpinner = replacement
            parent.addView(colorSpinner, index)
        }
    }

    private fun paletteSpinner(): Spinner = when (val current = preview) {
        is BlackHoleView -> spinner(BlackHoleView.PALETTE_NAMES, BlackHoleView.PALETTE_NAMES.indexOf(current.paletteName).coerceAtLeast(0), current::setPalette)
        is TopoNoiseView -> spinner(TopoNoiseView.PALETTE_NAMES, TopoNoiseView.PALETTE_NAMES.indexOf(current.paletteName).coerceAtLeast(0)) {
            if (it != TopoNoiseView.CUSTOM) current.setPalette(it)
        }
        is PixelDreamView -> spinner(PixelDreamThemes.names, PixelDreamThemes.indexOf(current.paletteName)) {
            val id = PixelDreamThemes.idAt(PixelDreamThemes.names.indexOf(it))
            if (id != PixelDreamView.CUSTOM) {
                current.setPalette(id)
                updateSceneControls()
            }
        }
        is SolidColorView -> {
            val currentHex = hex(current.color)
            val themeColors = RetuiWallpaperSettings.themeColors()
                .map { hex(Color.parseColor(it) or Color.BLACK) }
                .distinct()
            val colors = listOf("PICK…", currentHex) + themeColors.filterNot { it == currentHex }
            solidColorSpinner(colors, 1) { value ->
                if (value == "PICK…") showSolidColorPicker(current) else current.color = Color.parseColor(value) or Color.BLACK
            }
        }
        else -> (current as CsakuraView).let { spinner(CsakuraView.PALETTE_NAMES, CsakuraView.PALETTE_NAMES.indexOf(it.paletteName).coerceAtLeast(0), it::setPalette) }
    }

    private fun updateSceneControls() {
        val visibility = if (scene == "solid" || scene == PixelDreamView.SCENE) View.GONE else View.VISIBLE
        positionControls.forEach { it.visibility = visibility }
        tuningControls.forEach { it.visibility = visibility }
        topoColorRow.visibility = if (scene == TopoNoiseView.SCENE) View.VISIBLE else View.GONE
        pixelColorRow.visibility = if (scene == PixelDreamView.SCENE) View.VISIBLE else View.GONE
        val zoomVisibility = if (scene == TopoNoiseView.SCENE) View.GONE else View.VISIBLE
        zoomLabel.visibility = zoomVisibility
        zoomMinus.visibility = zoomVisibility
        zoomPlus.visibility = zoomVisibility
        panelParams.height = dp(if (scene == TopoNoiseView.SCENE) 318 else 268)
        settingsPanel.layoutParams = panelParams
        topoColorControls.forEachIndexed { index, button ->
            val topo = preview as? TopoNoiseView ?: return@forEachIndexed
            button.setBackgroundColor(topo.color(index))
            button.setTextColor(if (Color.luminance(topo.color(index)) > 0.5f) Color.BLACK else Color.WHITE)
        }
        pixelColorControls.forEachIndexed { index, button ->
            val pixel = preview as? PixelDreamView ?: return@forEachIndexed
            button.setBackgroundColor(pixel.color(index))
            button.setTextColor(if (Color.luminance(pixel.color(index)) > 0.5f) Color.BLACK else Color.WHITE)
        }
    }

    private fun addPositionControl(label: String, params: FrameLayout.LayoutParams, action: () -> Unit) {
        control(label, action).also {
            positionControls.add(it)
            root.addView(it, params)
        }
    }

    private fun showSolidColorPicker(solid: SolidColorView) = showColorPicker(
        solid.color,
        getString(R.string.editor_retuiwallpaperactivity_pick_solid_color_04728)
    ) { color ->
        solid.color = color
        replacePaletteSpinner()
    }

    private fun showTopoColorPicker(index: Int) {
        val topo = preview as? TopoNoiseView ?: return
        showColorPicker(topo.color(index), getString(R.string.wallpaper_topo_pick_color)) { color ->
            topo.setCustomColor(index, color)
            replacePaletteSpinner()
            updateSceneControls()
        }
    }

    private fun showPixelColorPicker(index: Int) {
        val pixel = preview as? PixelDreamView ?: return
        showColorPicker(pixel.color(index), getString(R.string.wallpaper_pixel_pick_color)) { color ->
            pixel.setCustomColor(index, color)
            replacePaletteSpinner()
            updateSceneControls()
        }
    }

    private fun replacePaletteSpinner() {
        val replacement = paletteSpinner()
        (colorSpinner.parent as ViewGroup).let { parent ->
            val index = parent.indexOfChild(colorSpinner)
            parent.removeView(colorSpinner)
            colorSpinner = replacement
            parent.addView(colorSpinner, index)
        }
    }

    private fun showColorPicker(initialColor: Int, title: String, onUse: (Int) -> Unit) {
        val content = LayoutInflater.from(this).inflate(R.layout.color_picker_dialog, root, false)
        val preview = content.findViewById<View>(R.id.color_preview)
        val alpha = content.findViewById<SeekBar>(R.id.seek_alpha)
        val hue = content.findViewById<SeekBar>(R.id.seek_hue)
        val saturation = content.findViewById<SeekBar>(R.id.seek_sat)
        val brightness = content.findViewById<SeekBar>(R.id.seek_val)
        val hexPreview = content.findViewById<TextView>(R.id.hex_preview)
        val hexInput = content.findViewById<EditText>(R.id.hex_input)
        styleHeader(this, content.findViewById(R.id.picker_title))
        hexInput.visibility = View.VISIBLE
        hexInput.setText(hex(initialColor))
        hexInput.setSelection(hexInput.length())
        val hsv = FloatArray(3)
        Color.colorToHSV(initialColor, hsv)
        alpha.progress = Color.alpha(initialColor)
        hue.progress = hsv[0].toInt()
        saturation.progress = (hsv[1] * 100).toInt()
        brightness.progress = (hsv[2] * 100).toInt()

        val listener = object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val color = Color.HSVToColor(alpha.progress, floatArrayOf(
                    hue.progress.toFloat(), saturation.progress / 100f, brightness.progress / 100f
                ))
                preview.setBackgroundColor(color)
                hexPreview.text = hex(color)
                if (fromUser) {
                    hexInput.setText(hex(color))
                    hexInput.setSelection(hexInput.length())
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
        }
        listOf(alpha, hue, saturation, brightness).forEach { it.setOnSeekBarChangeListener(listener) }
        hexInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                val color = s?.toString()?.let(RetuiWallpaperSettings::parseColorValue) ?: return
                val newHsv = FloatArray(3)
                Color.colorToHSV(color, newHsv)
                alpha.progress = Color.alpha(color)
                hue.progress = newHsv[0].toInt()
                saturation.progress = (newHsv[1] * 100).toInt()
                brightness.progress = (newHsv[2] * 100).toInt()
                preview.setBackgroundColor(color)
                hexPreview.text = hex(color)
                hexInput.error = null
            }
        })
        listener.onProgressChanged(null, 0, false)

        TuixtDialog.showContent(this, title, content, getString(R.string.editor_retuiwallpaperactivity_use_7dcf4), getString(R.string.editor_retuiwallpaperactivity_cancel_1507c), ConfirmAction {
            RetuiWallpaperSettings.parseColorValue(hexInput.text.toString())?.let(onUse)
                ?: hexInput.setError(getString(R.string.wallpaper_hex_invalid))
        })
    }

    private fun hex(color: Int): String = String.format(Locale.ROOT, "#%08X", color)

    private fun solidColorSpinner(items: List<String>, selected: Int, onSelected: (String) -> Unit) =
        spinner(items, selected, onSelected).apply {
            adapter = object : ArrayAdapter<String>(
                this@RetuiWallpaperActivity,
                android.R.layout.simple_spinner_dropdown_item,
                items
            ) {
                override fun getView(position: Int, convertView: View?, parent: ViewGroup): View =
                    decorate(super.getView(position, convertView, parent) as TextView, items[position])

                override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View =
                    decorate(super.getDropDownView(position, convertView, parent) as TextView, items[position])

                private fun decorate(text: TextView, value: String): TextView = text.apply {
                    if (value == "PICK…") text.text = getString(R.string.wallpaper_pick_color)
                    val swatch = value.takeUnless { it == "PICK…" }?.let {
                        GradientDrawable().apply {
                            shape = GradientDrawable.RECTANGLE
                            setColor(Color.parseColor(it))
                            setStroke(dp(1), Color.WHITE)
                            setBounds(0, 0, dp(24), dp(24))
                        }
                    }
                    setCompoundDrawables(swatch, null, null, null)
                    compoundDrawablePadding = dp(8)
                }
            }
            setSelection(selected)
        }

    private fun adjustHeight(delta: Float) = when (val current = preview) {
        is CsakuraView -> current.treeHeight += delta
        is BlackHoleView -> current.diskTilt += delta
        is TopoNoiseView -> current.relief += delta
        else -> Unit
    }
    private fun adjustWidth(delta: Float) = when (val current = preview) {
        is CsakuraView -> current.treeWidth += delta
        is BlackHoleView -> current.diskWidth += delta
        is TopoNoiseView -> current.contourDensity += if (delta < 0f) -1 else 1
        else -> Unit
    }
    private fun adjustScale(delta: Float) = when (val current = preview) {
        is CsakuraView -> current.treeScale += delta
        is BlackHoleView -> current.sceneScale += delta
        is TopoNoiseView -> current.noiseScale += delta
        else -> Unit
    }
    private fun adjustDensity(delta: Int) = when (val current = preview) {
        is CsakuraView -> current.petalDensity += delta
        is BlackHoleView -> current.particleDensity += delta
        is TopoNoiseView -> current.noiseScale += delta * 0.1f
        else -> Unit
    }

    private fun useOnPhone() {
        save()
        try {
            startActivity(Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).apply {
                putExtra(
                    WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                    ComponentName(this@RetuiWallpaperActivity, RetuiWallpaperService::class.java)
                )
            })
        } catch (_: Exception) {
            startActivity(Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER))
        }
    }

    private fun control(label: String, action: () -> Unit) = Button(this).apply {
        text = label
        setTextColor(Color.WHITE)
        setBackgroundColor(Color.argb(180, 30, 22, 40))
        setOnClickListener { action() }
        minWidth = 0
        minHeight = 0
    }

    private fun compactControl(text: String, action: () -> Unit) = control(text, action).apply {
        textSize = 11f
        layoutParams = LinearLayout.LayoutParams(0, dp(46), 1f).apply { marginStart = dp(2); marginEnd = dp(2) }
    }

    private fun label(text: String) = TextView(this).apply {
        this.text = text
        setTextColor(Color.WHITE)
        textSize = 10f
        gravity = Gravity.CENTER
        layoutParams = LinearLayout.LayoutParams(0, dp(46), 0.7f)
    }

    private fun row() = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER
        layoutParams = LinearLayout.LayoutParams(-1, dp(50))
    }

    private fun spinner(items: List<String>, selected: Int, onSelected: (String) -> Unit) = Spinner(this).apply {
        adapter = ArrayAdapter(this@RetuiWallpaperActivity, android.R.layout.simple_spinner_dropdown_item, items.map {
            when (it) {
                "csakura" -> getString(R.string.wallpaper_sakura)
                "black hole" -> getString(R.string.wallpaper_black_hole)
                TopoNoiseView.SCENE -> getString(R.string.wallpaper_topo_noise)
                PixelDreamView.SCENE -> getString(R.string.wallpaper_pixel_dream)
                "solid" -> getString(R.string.wallpaper_solid)
                else -> it
            }
        })
        setSelection(selected)
        setBackgroundColor(Color.argb(150, 30, 22, 40))
        layoutParams = LinearLayout.LayoutParams(0, dp(46), 1.3f).apply { marginEnd = dp(4) }
        onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: android.view.View?, position: Int, id: Long) = onSelected(items[position])
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
    }

    private fun edge(gravity: Int, top: Int = 0, bottom: Int = 0) =
        FrameLayout.LayoutParams(dp(56), dp(56), gravity).apply {
            topMargin = dp(top); bottomMargin = dp(bottom)
        }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
