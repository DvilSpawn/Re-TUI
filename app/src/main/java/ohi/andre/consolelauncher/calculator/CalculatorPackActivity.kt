package ohi.andre.consolelauncher.calculator

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.commands.tuixt.TuixtTheme
import ohi.andre.consolelauncher.localization.LocalizedComponentActivity
import ohi.andre.consolelauncher.managers.xml.XMLPrefsManager

/** Android Open/Share and Startup use the same installer as the calculator's inline card. */
class CalculatorPackActivity : LocalizedComponentActivity() {
    private lateinit var panel: CalculatorPackPanel

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        XMLPrefsManager.loadCommons(this)
        panel = CalculatorPackPanel(this)
        val scroll = android.widget.ScrollView(this).apply { addView(panel.view) }
        TuixtTheme.styleScreen(this, scroll)
        setContentView(scroll)
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(scroll) { _, insets ->
            val bars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            panel.view.setPadding(24 + bars.left, 24 + bars.top, 24 + bars.right, 24 + bars.bottom)
            insets
        }
        androidx.core.view.ViewCompat.requestApplyInsets(scroll)
        if (state == null && intent.action != Intent.ACTION_MAIN && intent.action != null) {
            try {
                require(intent.type == CalculatorPackStore.MIME) { getString(R.string.optional_calculator_invalid_file) }
                @Suppress("DEPRECATION")
                val uri = when (intent.action) {
                    Intent.ACTION_VIEW -> intent.data
                    Intent.ACTION_SEND -> intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
                    else -> null
                }
                panel.confirmImport(requireNotNull(uri))
            } catch (error: Exception) { panel.showError(error.message ?: getString(R.string.optional_calculator_invalid_file)) }
        }
    }

    @Deprecated("Platform document picker result")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        panel.onActivityResult(requestCode, resultCode, data)
    }

    override fun onDestroy() {
        if (::panel.isInitialized) panel.destroy()
        super.onDestroy()
    }
}
