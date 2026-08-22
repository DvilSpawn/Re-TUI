package ohi.andre.consolelauncher.commands.main.raw

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import ohi.andre.consolelauncher.LauncherActivity
import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.commands.CommandAbstraction
import ohi.andre.consolelauncher.commands.ExecutePack
import ohi.andre.consolelauncher.managers.flashlight.TorchManager

class flash : CommandAbstraction {
    override fun exec(pack: ExecutePack): String? {
        if (ContextCompat.checkSelfPermission(pack.context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                pack.context as Activity,
                arrayOf(Manifest.permission.CAMERA),
                LauncherActivity.COMMAND_REQUEST_PERMISSION
            )
            return pack.context.getString(R.string.output_waitingpermission)
        }

        TorchManager.toggle(pack.context)
        return null
    }

    override fun helpRes(): Int = R.string.help_flash

    override fun argType(): IntArray = IntArray(0)

    override fun priority(): Int = 4

    override fun onNotArgEnough(pack: ExecutePack, nArgs: Int): String? = null

    override fun onArgNotFound(pack: ExecutePack, indexNotFound: Int): String? = null
}
