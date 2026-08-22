package ohi.andre.consolelauncher.managers.termux

import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.PermissionInfo
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import ohi.andre.consolelauncher.UIManager
import android.Manifest
import android.content.pm.ResolveInfo
import android.content.pm.PackageInfo

object TermuxBridgeManager {
    const val TERMUX_PACKAGE: String = "com.termux"
    const val TERMUX_RUN_COMMAND_PERMISSION: String = "com.termux.permission.RUN_COMMAND"
    const val TERMUX_RUN_COMMAND_ACTION: String = "com.termux.RUN_COMMAND"
    const val TERMUX_RUN_COMMAND_SERVICE: String = "com.termux.app.RunCommandService"
    const val TERMUX_RUN_COMMAND_PATH: String = "com.termux.RUN_COMMAND_PATH"
    const val TERMUX_RUN_COMMAND_ARGUMENTS: String = "com.termux.RUN_COMMAND_ARGUMENTS"
    const val TERMUX_RUN_COMMAND_WORKDIR: String = "com.termux.RUN_COMMAND_WORKDIR"
    const val TERMUX_RUN_COMMAND_BACKGROUND: String = "com.termux.RUN_COMMAND_BACKGROUND"
    const val TERMUX_RUN_COMMAND_PENDING_INTENT: String = "com.termux.RUN_COMMAND_PENDING_INTENT"

    const val TERMUX_HOME: String = "/data/data/com.termux/files/home"
    const val TERMUX_SH: String = "/data/data/com.termux/files/usr/bin/sh"
    const val RESULT_PREFIX: String = "retui-bridge:"

    fun isTermuxInstalled(context: Context): Boolean {
        try {
            context.getPackageManager().getPackageInfo(TERMUX_PACKAGE, 0)
            return true
        } catch (e: Exception) {
            return false
        }
    }

    fun termuxDeclaresRunCommandPermission(context: Context): Boolean {
        try {
            val info = context.getPackageManager()
                .getPackageInfo(TERMUX_PACKAGE, PackageManager.GET_PERMISSIONS)
            if (info.permissions != null) {
                for (permission in info.permissions) {
                    if (permission != null && TERMUX_RUN_COMMAND_PERMISSION == permission.name) {
                        return true
                    }
                }
            }
        } catch (ignored: Exception) {
        }
        return resolvesRunCommandService(context)
    }

    fun hasRunCommandPermission(context: Context): Boolean {
        val permission = findRunCommandPermission(context)
        return permission == null
                || ContextCompat.checkSelfPermission(
            context,
            TERMUX_RUN_COMMAND_PERMISSION
        ) == PackageManager.PERMISSION_GRANTED
    }

    @JvmStatic
    @JvmOverloads
    fun requestRunCommandPermissionIfPossible(context: Context?, requestCode: Int = 8127): Boolean {
        if (context !is Activity) {
            return false
        }

        val permission = findRunCommandPermission(context)
        if (permission == null) {
            return false
        }

        ActivityCompat.requestPermissions(
            context,
            arrayOf<String>(TERMUX_RUN_COMMAND_PERMISSION),
            requestCode
        )
        return true
    }

    @JvmStatic
    fun status(context: Context): BridgeStatus {
        val status = BridgeStatus()
        status.termuxInstalled = isTermuxInstalled(context)
        status.runCommandDeclared =
            status.termuxInstalled && termuxDeclaresRunCommandPermission(context)
        status.runCommandGranted = status.termuxInstalled && hasRunCommandPermission(context)
        return status
    }

    @JvmStatic
    fun dispatchShell(
        context: Context,
        label: String?,
        script: String?,
        workDir: String?,
        vararg args: String?
    ): Boolean {
        val commandArgs = arrayOfNulls<String>(args.size + 3)
        commandArgs[0] = "-c"
        commandArgs[1] = script
        // sh -c uses the first extra argument as $0. Keep caller args starting at $1.
        commandArgs[2] = "retui"
        System.arraycopy(args, 0, commandArgs, 3, args.size)

        startRunCommand(
            context,
            TERMUX_SH,
            if (workDir == null) TERMUX_HOME else workDir,
            createResultPendingIntent(context, RESULT_PREFIX + label, null),
            commandArgs
        )
        return true
    }

    @JvmStatic
    fun startRunCommand(
        context: Context,
        path: String?,
        workDir: String?,
        resultPendingIntent: PendingIntent?,
        args: Array<String?>?
    ) {
        val intent = Intent(TERMUX_RUN_COMMAND_ACTION)
        intent.setClassName(TERMUX_PACKAGE, TERMUX_RUN_COMMAND_SERVICE)
        intent.putExtra(TERMUX_RUN_COMMAND_PATH, path)
        intent.putExtra(TERMUX_RUN_COMMAND_WORKDIR, if (workDir == null) TERMUX_HOME else workDir)
        intent.putExtra(TERMUX_RUN_COMMAND_BACKGROUND, true)
        intent.putExtra(TERMUX_RUN_COMMAND_PENDING_INTENT, resultPendingIntent)
        if (args != null && args.size > 0) {
            intent.putExtra(TERMUX_RUN_COMMAND_ARGUMENTS, args)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ContextCompat.startForegroundService(context, intent)
        } else {
            context.startService(intent)
        }
    }

    @JvmStatic
    fun createResultPendingIntent(
        context: Context?,
        path: String?,
        module: String?
    ): PendingIntent? {
        val resultIntent = Intent(context, TermuxResultService::class.java)
        resultIntent.putExtra(UIManager.EXTRA_TERMUX_RESULT_PATH, path)
        if (module != null && module.length > 0) {
            resultIntent.putExtra(UIManager.EXTRA_TERMUX_RESULT_MODULE, module)
        }

        var flags = PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_UPDATE_CURRENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            flags = flags or PendingIntent.FLAG_MUTABLE
        }

        return PendingIntent.getService(
            context,
            System.currentTimeMillis().toInt(),
            resultIntent,
            flags
        )
    }

    private fun findRunCommandPermission(context: Context): PermissionInfo? {
        try {
            val info = context.getPackageManager()
                .getPackageInfo(TERMUX_PACKAGE, PackageManager.GET_PERMISSIONS)
            if (info.permissions == null) {
                return null
            }

            for (permission in info.permissions) {
                if (permission != null && TERMUX_RUN_COMMAND_PERMISSION == permission.name) {
                    return permission
                }
            }
        } catch (ignored: Exception) {
        }
        return null
    }

    private fun resolvesRunCommandService(context: Context): Boolean {
        val intent = Intent(TERMUX_RUN_COMMAND_ACTION)
        intent.setClassName(TERMUX_PACKAGE, TERMUX_RUN_COMMAND_SERVICE)
        try {
            val info = context.getPackageManager().resolveService(intent, 0)
            return info != null
        } catch (ignored: Exception) {
            return false
        }
    }

    class BridgeStatus {
        @JvmField
        var termuxInstalled: Boolean = false
        @JvmField
        var runCommandDeclared: Boolean = false
        @JvmField
        var runCommandGranted: Boolean = false
    }
}
