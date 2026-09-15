package ohi.andre.consolelauncher.localization

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.res.Resources
import android.os.Bundle
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.appcompat.app.AppCompatActivity
import ohi.andre.consolelauncher.tuils.interfaces.Reloadable

class LauncherApplication : Application() {
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        LanguagePacks.initialize(base)
    }
    override fun getResources(): Resources = LanguagePacks.resources(super.getResources())
}

/** Preserve AppCompat's factory, then resolve XML text attributes through the same pack lookup. */
private fun installInflater(activity: Activity) {
    val inflater = activity.layoutInflater
    if (inflater.factory2 != null) return
    inflater.factory2 = object : LayoutInflater.Factory2 {
        override fun onCreateView(name: String, context: Context, attrs: AttributeSet): View? = onCreateView(null, name, context, attrs)
        override fun onCreateView(parent: View?, name: String, context: Context, attrs: AttributeSet): View? {
            val actualName = if (name == "view") attrs.getAttributeValue(null, "class") else name
            val view = (activity as? AppCompatActivity)?.delegate?.createView(parent, actualName, context, attrs)
                ?: run {
                    val creator = inflater.cloneInContext(context)
                    if ('.' in actualName) runCatching { creator.createView(actualName, null, attrs) }.getOrNull()
                    else sequenceOf("android.widget.", "android.view.", "android.webkit.")
                        .mapNotNull { prefix -> runCatching { creator.createView(actualName, prefix, attrs) }.getOrNull() }.firstOrNull()
                } ?: return null
            fun text(attribute: String): CharSequence? {
                val id = attrs.getAttributeResourceValue("http://schemas.android.com/apk/res/android", attribute, 0)
                return if (id == 0) null else context.resources.getText(id)
            }
            if (view is TextView) {
                text("text")?.let { view.text = it }
                text("hint")?.let { view.hint = it }
            }
            text("contentDescription")?.let { view.contentDescription = it }
            return view
        }
    }
}

private fun localizeTitle(activity: Activity) {
    @Suppress("DEPRECATION")
    val label = activity.packageManager.getActivityInfo(activity.componentName, 0).labelRes
    if (label != 0) activity.title = activity.getText(label)
}

private fun refresh(activity: Activity, created: Int) {
    if (created != LanguagePacks.generation && !activity.isFinishing) activity.window.decorView.post {
        if (!activity.isFinishing) {
            if (activity is Reloadable) activity.reload() else activity.recreate()
        }
    }
}

open class LocalizedActivity : Activity() {
    private var languageGeneration = -1
    override fun attachBaseContext(newBase: Context) = super.attachBaseContext(LanguagePacks.wrap(newBase))
    override fun getResources(): Resources = LanguagePacks.resources(super.getResources())
    override fun onCreate(state: Bundle?) { languageGeneration = LanguagePacks.generation; installInflater(this); super.onCreate(state); localizeTitle(this) }
    override fun onResume() { super.onResume(); refresh(this, languageGeneration) }
}
open class LocalizedComponentActivity : ComponentActivity() {
    private var languageGeneration = -1
    override fun attachBaseContext(newBase: Context) = super.attachBaseContext(LanguagePacks.wrap(newBase))
    override fun getResources(): Resources = LanguagePacks.resources(super.getResources())
    override fun onCreate(state: Bundle?) { languageGeneration = LanguagePacks.generation; installInflater(this); super.onCreate(state); localizeTitle(this) }
    override fun onResume() { super.onResume(); refresh(this, languageGeneration) }
}
open class LocalizedAppCompatActivity : AppCompatActivity() {
    private var languageGeneration = -1
    override fun attachBaseContext(newBase: Context) = super.attachBaseContext(LanguagePacks.wrap(newBase))
    override fun getResources(): Resources = LanguagePacks.resources(super.getResources())
    override fun onCreate(state: Bundle?) { languageGeneration = LanguagePacks.generation; installInflater(this); super.onCreate(state); localizeTitle(this) }
    override fun onResume() { super.onResume(); refresh(this, languageGeneration) }
}
