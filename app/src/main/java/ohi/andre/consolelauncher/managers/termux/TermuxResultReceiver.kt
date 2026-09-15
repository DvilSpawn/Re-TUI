package ohi.andre.consolelauncher.managers.termux

import ohi.andre.consolelauncher.R
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import ohi.andre.consolelauncher.UIManager

class TermuxResultReceiver : BroadcastReceiver() {
    override fun onReceive(rawContext: Context, intent: Intent) {
        val context = ohi.andre.consolelauncher.localization.LanguagePacks.wrap(rawContext)
        forwardResult(context, intent)
    }

    companion object {
        private const val EXTRA_PLUGIN_RESULT_BUNDLE = "result"
        private const val EXTRA_RUN_COMMAND_RESULT = "com.termux.RUN_COMMAND_RESULT"
        private const val EXTRA_RUN_COMMAND_RESULT_BUNDLE = "com.termux.RUN_COMMAND_RESULT_BUNDLE"
        private const val BUNDLE_STDOUT = "stdout"
        private const val BUNDLE_STDERR = "stderr"
        private const val BUNDLE_EXIT_CODE = "exitCode"
        private const val BUNDLE_EXIT_CODE_ALT = "exit_code"
        private const val BUNDLE_ERR = "err"
        private const val BUNDLE_ERRMSG = "errmsg"

        @JvmStatic
        fun forwardResult(context: Context, intent: Intent?) {
            val result = Intent(UIManager.ACTION_TERMUX_RESULT)
            if (intent != null) {
                result.putExtra(UIManager.EXTRA_TERMUX_RESULT_PATH, intent.getStringExtra(UIManager.EXTRA_TERMUX_RESULT_PATH))
                result.putExtra(UIManager.EXTRA_TERMUX_RESULT_MODULE, intent.getStringExtra(UIManager.EXTRA_TERMUX_RESULT_MODULE))

                val bundle = findResultBundle(intent)
                if (bundle != null) {
                    result.putExtra(UIManager.EXTRA_TERMUX_RESULT_STDOUT, stringValue(bundle, BUNDLE_STDOUT))
                    result.putExtra(UIManager.EXTRA_TERMUX_RESULT_STDERR, stringValue(bundle, BUNDLE_STDERR))
                    result.putExtra(
                        UIManager.EXTRA_TERMUX_RESULT_EXIT_CODE,
                        if (bundle.containsKey(BUNDLE_EXIT_CODE)) {
                            bundle.getInt(BUNDLE_EXIT_CODE, Int.MIN_VALUE)
                        } else {
                            bundle.getInt(BUNDLE_EXIT_CODE_ALT, Int.MIN_VALUE)
                        }
                    )

                    var error = stringValue(bundle, BUNDLE_ERRMSG)
                    if (error == null) {
                        error = errorValue(bundle, BUNDLE_ERR)
                    }
                    result.putExtra(UIManager.EXTRA_TERMUX_RESULT_ERROR, error)
                } else {
                    copyDirectExtras(intent, result)
                    result.putExtra(UIManager.EXTRA_TERMUX_RESULT_ERROR, context.getString(R.string.integration_termuxresultreceiver_termux_returned_no_result_bundle_ea063))
                    result.putExtra(UIManager.EXTRA_TERMUX_RESULT_DEBUG, describeExtras(intent.extras))
                }
            }

            LocalBroadcastManager.getInstance(context.applicationContext).sendBroadcast(result)
        }

        private fun findResultBundle(intent: Intent): Bundle? {
            var bundle = intent.getBundleExtra(EXTRA_PLUGIN_RESULT_BUNDLE)
            if (bundle != null) {
                return bundle
            }

            bundle = intent.getBundleExtra(EXTRA_RUN_COMMAND_RESULT)
            if (bundle != null) {
                return bundle
            }

            bundle = intent.getBundleExtra(EXTRA_RUN_COMMAND_RESULT_BUNDLE)
            if (bundle != null) {
                return bundle
            }

            val extras = intent.extras ?: return null
            for (key in extras.keySet()) {
                val value = extras.get(key)
                if (value is Bundle) {
                    return value
                }
            }

            return null
        }

        private fun copyDirectExtras(source: Intent, result: Intent) {
            result.putExtra(UIManager.EXTRA_TERMUX_RESULT_STDOUT, stringValue(source.extras, BUNDLE_STDOUT))
            result.putExtra(UIManager.EXTRA_TERMUX_RESULT_STDERR, stringValue(source.extras, BUNDLE_STDERR))
            result.putExtra(
                UIManager.EXTRA_TERMUX_RESULT_EXIT_CODE,
                if (source.hasExtra(BUNDLE_EXIT_CODE)) {
                    source.getIntExtra(BUNDLE_EXIT_CODE, Int.MIN_VALUE)
                } else {
                    source.getIntExtra(BUNDLE_EXIT_CODE_ALT, Int.MIN_VALUE)
                }
            )

            var error = stringValue(source.extras, BUNDLE_ERRMSG)
            if (error == null) {
                error = errorValue(source.extras, BUNDLE_ERR)
            }
            result.putExtra(UIManager.EXTRA_TERMUX_RESULT_ERROR, error)
        }

        private fun stringValue(bundle: Bundle?, key: String): String? {
            if (bundle == null || !bundle.containsKey(key)) {
                return null
            }

            val value = bundle.get(key)
            return value?.toString()
        }

        private fun errorValue(bundle: Bundle?, key: String): String? {
            if (bundle == null || !bundle.containsKey(key)) {
                return null
            }

            val value = bundle.get(key)
            if (value is Number && value.toInt() == -1) {
                return null
            }
            return value?.toString()
        }

        private fun describeExtras(extras: Bundle?): String {
            if (extras == null || extras.isEmpty) {
                return "extras=<none>"
            }

            val builder = StringBuilder("extras=")
            var first = true
            for (key in extras.keySet()) {
                if (!first) {
                    builder.append(", ")
                }
                val value = extras.get(key)
                builder.append(key).append("(")
                    .append(value?.javaClass?.simpleName ?: "null")
                    .append(")")
                first = false
            }
            return builder.toString()
        }
    }
}
