package ohi.andre.consolelauncher.wallpaper

import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
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

class RetuiWallpaperActivity : AppCompatActivity() {
    private lateinit var root: FrameLayout
    private lateinit var preview: android.view.View
    private lateinit var colorSpinner: Spinner
    private lateinit var densityLabel: TextView
    private lateinit var heightLabel: TextView
    private lateinit var boundsLabel: TextView
    private val positionControls = mutableListOf<View>()
    private val tuningControls = mutableListOf<View>()
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
        val selectors = row()
        selectors.addView(label("WALLPAPER"))
        val scenes = listOf("csakura", "black hole", "solid")
        selectors.addView(spinner(scenes, scenes.indexOf(scene).coerceAtLeast(0), ::switchScene))
        selectors.addView(label("COLOR"))
        colorSpinner = paletteSpinner()
        selectors.addView(colorSpinner)
        panel.addView(selectors)

        val tuning = row()
        heightLabel = label(if (scene == "black hole") "TILT" else "HEIGHT")
        tuning.addView(heightLabel)
        tuning.addView(compactControl("−") { adjustHeight(-0.05f) })
        tuning.addView(compactControl("+") { adjustHeight(0.05f) })
        tuning.addView(label("ZOOM"))
        tuning.addView(compactControl("−") { adjustScale(-0.1f) })
        tuning.addView(compactControl("+") { adjustScale(0.1f) })
        panel.addView(tuning)
        tuningControls.add(tuning)

        val shape = row()
        boundsLabel = label(if (scene == "black hole") "RADIUS" else "BOUNDS")
        shape.addView(boundsLabel)
        shape.addView(compactControl("−") { adjustWidth(-0.1f) })
        shape.addView(compactControl("+") { adjustWidth(0.1f) })
        densityLabel = label(if (scene == "black hole") "DUST" else "PETALS")
        shape.addView(densityLabel)
        shape.addView(compactControl("−") { adjustDensity(-1) })
        shape.addView(compactControl("+") { adjustDensity(1) })
        panel.addView(shape)
        tuningControls.add(shape)

        val regrow = row()
        regrow.addView(compactControl("REGENERATE") {
            when (val current = preview) {
                is CsakuraView -> current.regrow()
                is BlackHoleView -> current.regenerate()
            }
        })
        panel.addView(regrow)
        tuningControls.add(regrow)

        val apply = row()
        apply.addView(compactControl("USE ON PHONE") { useOnPhone() })
        panel.addView(apply)
        val panelParams = FrameLayout.LayoutParams(-1, dp(268), Gravity.TOP).apply {
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
            is SolidColorView -> RetuiWallpaperSettings.saveSolidColor(this, hex(current.color))
        }
    }

    private fun createPreview(name: String): android.view.View = when (name) {
        "black hole" -> BlackHoleView(this).apply { loadPosition() }
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
        densityLabel.text = if (scene == "black hole") "DUST" else "PETALS"
        heightLabel.text = if (scene == "black hole") "TILT" else "HEIGHT"
        boundsLabel.text = if (scene == "black hole") "RADIUS" else "BOUNDS"
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
        val visibility = if (scene == "solid") View.GONE else View.VISIBLE
        positionControls.forEach { it.visibility = visibility }
        tuningControls.forEach { it.visibility = visibility }
    }

    private fun addPositionControl(label: String, params: FrameLayout.LayoutParams, action: () -> Unit) {
        control(label, action).also {
            positionControls.add(it)
            root.addView(it, params)
        }
    }

    private fun showSolidColorPicker(solid: SolidColorView) {
        val content = LayoutInflater.from(this).inflate(R.layout.color_picker_dialog, root, false)
        val preview = content.findViewById<View>(R.id.color_preview)
        val alpha = content.findViewById<SeekBar>(R.id.seek_alpha)
        val hue = content.findViewById<SeekBar>(R.id.seek_hue)
        val saturation = content.findViewById<SeekBar>(R.id.seek_sat)
        val brightness = content.findViewById<SeekBar>(R.id.seek_val)
        val hexPreview = content.findViewById<TextView>(R.id.hex_preview)
        styleHeader(this, content.findViewById(R.id.picker_title))
        content.findViewById<View>(R.id.alpha_label).visibility = View.GONE
        alpha.visibility = View.GONE
        val hsv = FloatArray(3)
        Color.colorToHSV(solid.color, hsv)
        hue.progress = hsv[0].toInt()
        saturation.progress = (hsv[1] * 100).toInt()
        brightness.progress = (hsv[2] * 100).toInt()

        val listener = object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val color = Color.HSVToColor(255, floatArrayOf(
                    hue.progress.toFloat(), saturation.progress / 100f, brightness.progress / 100f
                ))
                preview.setBackgroundColor(color)
                hexPreview.text = hex(color)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
        }
        listOf(hue, saturation, brightness).forEach { it.setOnSeekBarChangeListener(listener) }
        listener.onProgressChanged(null, 0, false)

        TuixtDialog.showContent(this, "Pick Solid Color", content, "USE", "CANCEL", ConfirmAction {
            solid.color = Color.parseColor(hexPreview.text.toString())
            val replacement = paletteSpinner()
            (colorSpinner.parent as ViewGroup).let { parent ->
                val index = parent.indexOfChild(colorSpinner)
                parent.removeView(colorSpinner)
                colorSpinner = replacement
                parent.addView(colorSpinner, index)
            }
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
        else -> Unit
    }
    private fun adjustWidth(delta: Float) = when (val current = preview) {
        is CsakuraView -> current.treeWidth += delta
        is BlackHoleView -> current.diskWidth += delta
        else -> Unit
    }
    private fun adjustScale(delta: Float) = when (val current = preview) {
        is CsakuraView -> current.treeScale += delta
        is BlackHoleView -> current.sceneScale += delta
        else -> Unit
    }
    private fun adjustDensity(delta: Int) = when (val current = preview) {
        is CsakuraView -> current.petalDensity += delta
        is BlackHoleView -> current.particleDensity += delta
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
        adapter = ArrayAdapter(this@RetuiWallpaperActivity, android.R.layout.simple_spinner_dropdown_item, items)
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
