package ohi.andre.consolelauncher.sidebar

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import ohi.andre.consolelauncher.managers.xml.XMLPrefsManager

/** An app update stops the edge sidebar; bring it back straight away rather than when the launcher next opens. */
class EdgeSidebarRestart : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        if (!XMLPrefsManager.commonsLoaded) XMLPrefsManager.loadCommons(context)
        EdgeSidebar.sync(context.applicationContext)
    }
}
