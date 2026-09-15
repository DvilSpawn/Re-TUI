package ohi.andre.consolelauncher.commands.main.raw

import android.content.Intent
import android.net.Uri
import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.commands.CommandAbstraction
import ohi.andre.consolelauncher.commands.ExecutePack
import ohi.andre.consolelauncher.commands.main.MainPack
import ohi.andre.consolelauncher.managers.AppsManager
import ohi.andre.consolelauncher.tuils.Tuils

class uninstall : CommandAbstraction {
    override fun exec(pack: ExecutePack): String {
        val info = pack as MainPack

        val launchInfo = info.getLaunchInfo()
        val componentName = launchInfo.componentName
        if (componentName == null) {
            return info.res.getString(R.string.output_appnotfound)
        }

        return uninstall(info, componentName.packageName)
    }

    private fun uninstall(info: MainPack, packageName: String?): String {
        if (packageName == null || packageName.isEmpty()) {
            return info.res.getString(R.string.output_appnotfound)
        }

        try {
            val packageURI = Uri.fromParts("package", packageName, null)
            val uninstallIntent = Intent(Intent.ACTION_DELETE, packageURI)
            uninstallIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            info.context.startActivity(uninstallIntent)
        } catch (e: Exception) {
            return e.toString()
        }

        return info.context.getString(R.string.uninstalling_package, packageName)
    }

    override fun helpRes(): Int = R.string.help_uninstall

    override fun argType(): IntArray = intArrayOf(CommandAbstraction.ALL_PACKAGES)

    override fun priority(): Int = 3

    override fun onNotArgEnough(pack: ExecutePack, nArgs: Int): String {
        val info = pack as MainPack
        return info.res.getString(helpRes())
    }

    override fun onArgNotFound(pack: ExecutePack, indexNotFound: Int): String {
        val info = pack as MainPack
        val arg = pack.args!![indexNotFound] as String

        var li = info.appsManager.findLaunchInfoWithLabel(arg, AppsManager.HIDDEN_APPS)
        if (li == null) {
            li = info.appsManager.findLaunchInfoWithLabel(arg, AppsManager.SHOWN_APPS)
        }

        val componentName = li?.componentName
        if (componentName != null) {
            return uninstall(info, componentName.packageName)
        }

        return uninstall(info, arg)
    }
}
