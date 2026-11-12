package ohi.andre.consolelauncher.commands

import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.commands.main.Param
import ohi.andre.consolelauncher.commands.main.specific.ParamCommand
import ohi.andre.consolelauncher.tuils.Tuils

class Command {
    @JvmField var cmd: CommandAbstraction? = null
    @JvmField var mArgs: Array<Any?>? = null
    @JvmField var nArgs: Int = 0
    @JvmField var indexNotFound: Int = -1

    @Throws(Exception::class)
    fun exec(info: ExecutePack): String? {
        info.set(mArgs)

        val command = cmd!!
        val currentArgs = mArgs
        if (command is ParamCommand) {
            if (indexNotFound == 0) {
                if (currentArgs!![0]?.toString().isNullOrBlank()) return command.onNotArgEnough(info, 0)
                return info.context.getString(R.string.output_invalid_param) + Tuils.SPACE + currentArgs!![0]
            }

            val pCmd = command
            val param = currentArgs!![0] as Param
            val args = param.args()

            if (indexNotFound != -1) {
                return param.onArgNotFound(info, indexNotFound)
            }

            if (pCmd.defaultParamReference() != null) {
                if (args!!.size > nArgs) {
                    return param.onNotArgEnough(info, nArgs)
                }
            } else {
                if (args!!.size + 1 > nArgs) {
                    return param.onNotArgEnough(info, nArgs)
                }
            }
        } else if (indexNotFound != -1) {
            return command.onArgNotFound(info, indexNotFound)
        } else {
            val args = command.argType()
            if (nArgs < args!!.size || (currentArgs == null && args.isNotEmpty())) {
                return command.onNotArgEnough(info, nArgs)
            }
        }

        return command.exec(info)
    }

    fun nextArg(): Int {
        val command = cmd!!
        val currentArgs = mArgs
        val useParamArgs = command is ParamCommand && currentArgs != null && currentArgs.size >= 1

        val args: IntArray? = if (useParamArgs) {
            if (currentArgs!![0] !is Param) null else (currentArgs[0] as Param).args()
        } else {
            command.argType()
        }

        if (args == null || args.isEmpty()) {
            return 0
        }

        return try {
            args[if (useParamArgs) nArgs - 1 else nArgs]
        } catch (e: ArrayIndexOutOfBoundsException) {
            0
        }
    }
}
