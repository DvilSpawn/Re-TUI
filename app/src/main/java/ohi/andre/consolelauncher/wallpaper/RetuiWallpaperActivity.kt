package ohi.andre.consolelauncher.wallpaper

import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.AdapterView
import androidx.appcompat.app.AppCompatActivity
import ohi.andre.consolelauncher.tuils.LauncherSystemUi.applyFullscreen

class RetuiWallpaperActivity : AppCompatActivity() {
    private lateinit var root: FrameLayout
    private lateinit var preview: android.view.View
    private lateinit var colorSpinner: Spinner
    private lateinit var densityLabel: TextView
    private lateinit var heightLabel: TextView
    private lateinit var boundsLabel: TextView
    private var scene = "csakura"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyFullscreen(this)

        root = FrameLayout(this)
        scene = RetuiWallpaperSettings.scene(this)
        preview = createPreview(scene)
        root.addView(preview, FrameLayout.LayoutParams(-1, -1))

        root.addView(control("↑") { move(0f, -16f) }, edge(Gravity.TOP or Gravity.CENTER_HORIZONTAL, top = 278))
        root.addView(control("↓") { move(0f, 16f) }, edge(Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL, bottom = 12))
        root.addView(control("←") { move(-16f, 0f) }, edge(Gravity.START or Gravity.CENTER_VERTICAL))
        root.addView(control("→") { move(16f, 0f) }, edge(Gravity.END or Gravity.CENTER_VERTICAL))

        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), dp(8), dp(8), dp(8))
            setBackgroundColor(Color.argb(184, 18, 14, 24))
        }
        val selectors = row()
        selectors.addView(label("WALLPAPER"))
        selectors.addView(spinner(listOf("csakura", "black hole"), if (scene == "black hole") 1 else 0, ::switchScene))
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

        val regrow = row()
        regrow.addView(compactControl("REGENERATE") {
            when (val current = preview) {
                is CsakuraView -> current.regrow()
                is BlackHoleView -> current.regenerate()
            }
        })
        panel.addView(regrow)

        val apply = row()
        apply.addView(compactControl("USE ON PHONE") { useOnPhone() })
        panel.addView(apply)
        root.addView(panel, FrameLayout.LayoutParams(-1, dp(268), Gravity.TOP).apply {
            leftMargin = dp(8); topMargin = dp(8); rightMargin = dp(8)
        })
        setContentView(root)
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
        }
    }

    private fun createPreview(name: String): android.view.View = if (name == "black hole") {
        BlackHoleView(this).apply { loadPosition() }
    } else {
        CsakuraView(this).apply { loadPosition() }
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
        else -> (current as CsakuraView).let { spinner(CsakuraView.PALETTE_NAMES, CsakuraView.PALETTE_NAMES.indexOf(it.paletteName).coerceAtLeast(0), it::setPalette) }
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
