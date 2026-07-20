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
    private lateinit var preview: CsakuraView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyFullscreen(this)

        val root = FrameLayout(this)
        preview = CsakuraView(this).apply { loadPosition() }
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
        selectors.addView(spinner(listOf("csakura"), 0) { })
        selectors.addView(label("COLOR"))
        val paletteIndex = CsakuraView.PALETTE_NAMES.indexOf(preview.paletteName).coerceAtLeast(0)
        selectors.addView(spinner(CsakuraView.PALETTE_NAMES, paletteIndex, preview::setPalette))
        panel.addView(selectors)

        val tuning = row()
        tuning.addView(label("HEIGHT"))
        tuning.addView(compactControl("−") { preview.treeHeight -= 0.05f })
        tuning.addView(compactControl("+") { preview.treeHeight += 0.05f })
        tuning.addView(label("ZOOM"))
        tuning.addView(compactControl("−") { preview.treeScale -= 0.1f })
        tuning.addView(compactControl("+") { preview.treeScale += 0.1f })
        panel.addView(tuning)

        val shape = row()
        shape.addView(label("BOUNDS"))
        shape.addView(compactControl("−") { preview.treeWidth -= 0.1f })
        shape.addView(compactControl("+") { preview.treeWidth += 0.1f })
        shape.addView(label("PETALS"))
        shape.addView(compactControl("−") { preview.petalDensity-- })
        shape.addView(compactControl("+") { preview.petalDensity++ })
        panel.addView(shape)

        val regrow = row()
        regrow.addView(compactControl("REGROW") { preview.regrow() })
        panel.addView(regrow)

        val apply = row()
        apply.addView(compactControl("USE ON PHONE") { useOnPhone() })
        panel.addView(apply)
        root.addView(panel, FrameLayout.LayoutParams(-1, dp(268), Gravity.TOP).apply {
            leftMargin = dp(8); topMargin = dp(8); rightMargin = dp(8)
        })
        setContentView(root)
    }

    override fun onPause() {
        save()
        super.onPause()
    }

    private fun move(dx: Float, dy: Float) {
        preview.offsetX += dx
        preview.offsetY += dy
    }

    private fun save() = RetuiWallpaperSettings.save(
        this, preview.offsetX, preview.offsetY, preview.treeScale,
        preview.treeHeight, preview.treeWidth, preview.petalDensity, preview.treeSeed,
        preview.paletteName
    )

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
