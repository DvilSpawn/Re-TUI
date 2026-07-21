package ohi.andre.consolelauncher.commands.main.raw

import android.content.Intent
import android.os.Handler
import android.os.Looper
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.UIManager
import ohi.andre.consolelauncher.commands.CommandAbstraction
import ohi.andre.consolelauncher.commands.ExecutePack

class profile : CommandAbstraction {
    override fun exec(pack: ExecutePack): String? {
        Handler(Looper.getMainLooper()).post {
            LocalBroadcastManager.getInstance(pack.context.applicationContext)
                .sendBroadcast(Intent(UIManager.ACTION_PROFILE_SURFACE))
        }
        return null
    }

    override fun argType(): IntArray = intArrayOf()
    override fun priority(): Int = 3
    override fun helpRes(): Int = R.string.help_profile
    override fun onArgNotFound(pack: ExecutePack, indexNotFound: Int): String? = null
    override fun onNotArgEnough(pack: ExecutePack, nArgs: Int): String? = exec(pack)
}
