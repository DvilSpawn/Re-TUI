@file:Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")

package ohi.andre.consolelauncher.managers.termux

import android.app.IntentService
import android.content.Intent
import androidx.annotation.Nullable

class TermuxResultService : IntentService("TermuxResultService") {
    override fun getResources(): android.content.res.Resources =
        ohi.andre.consolelauncher.localization.LanguagePacks.resources(super.getResources())

    override fun onHandleIntent(intent: Intent?) {
        TermuxResultReceiver.forwardResult(applicationContext, intent)
    }
}
