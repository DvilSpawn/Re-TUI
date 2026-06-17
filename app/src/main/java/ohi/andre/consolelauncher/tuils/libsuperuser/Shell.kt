/*
 * Copyright (C) 2012-2015 Jorrit "Chainfire" Jongma
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package ohi.andre.consolelauncher.tuils.libsuperuser

import android.os.Build
import android.os.Handler
import android.os.Looper
import ohi.andre.consolelauncher.tuils.Tuils.log
import ohi.andre.consolelauncher.tuils.libsuperuser.StreamGobbler.OnLineListener
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream
import java.util.Collections
import java.util.LinkedList
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.lang.reflect.Method
import java.util.ArrayList
import java.util.HashMap
import java.util.Map
import ohi.andre.consolelauncher.tuils.Tuils
import ohi.andre.consolelauncher.tuils.libsuperuser.StreamGobbler.*

/**
 * Class providing functionality to execute commands in a (root) shell
 */
object Shell {
    /**
     * 
     * 
     * Runs commands using the supplied shell, and returns the output, or null
     * in case of errors.
     * 
     * 
     * 
     * This method is deprecated and only provided for backwards compatibility.
     * Use [.run] instead, and see
     * that same method for usage notes.
     * 
     * 
     * @param shell      The shell to use for executing the commands
     * @param commands   The commands to execute
     * @param wantSTDERR Return STDERR in the output ?
     * @return Output of the commands, or null in case of an error
     */
    @Deprecated("")
    fun run(shell: String, commands: Array<String?>, wantSTDERR: Boolean): MutableList<String>? {
        return run(shell, commands, null, wantSTDERR)
    }

    /**
     * 
     * 
     * Runs commands using the supplied shell, and returns the output, or null
     * in case of errors.
     * 
     * 
     * 
     * Note that due to compatibility with older Android versions, wantSTDERR is
     * not implemented using redirectErrorStream, but rather appended to the
     * output. STDOUT and STDERR are thus not guaranteed to be in the correct
     * order in the output.
     * 
     * 
     * 
     * Note as well that this code will intentionally crash when run in debug
     * mode from the main thread of the application. You should always execute
     * shell commands from a background thread.
     * 
     * 
     * 
     * When in debug mode, the code will also excessively log the commands
     * passed to and the output returned from the shell.
     * 
     * 
     * 
     * Though this function uses background threads to gobble STDOUT and STDERR
     * so a deadlock does not occur if the shell produces massive output, the
     * output is still stored in a List&lt;String&gt;, and as such doing
     * something like *'ls -lR /'* will probably have you run out of
     * memory.
     * 
     * 
     * @param shell       The shell to use for executing the commands
     * @param commands    The commands to execute
     * @param environment List of all environment variables (in 'key=value'
     * format) or null for defaults
     * @param wantSTDERR  Return STDERR in the output ?
     * @return Output of the commands, or null in case of an error
     */
    fun run(
        shell: String, commands: Array<String?>, environment: Array<String>?,
        wantSTDERR: Boolean
    ): MutableList<String>? {
        var environment = environment
        val shellUpper = shell.uppercase()

        var res = Collections.synchronizedList<String>(ArrayList<String>())

        try {
            // Combine passed environment with system environment
            if (environment != null) {
                val newEnvironment: MutableMap<String, String> = HashMap<String, String>()
                newEnvironment.putAll(System.getenv())
                var split: Int
                for (entry in environment) {
                    if ((entry.indexOf("=").also { split = it }) >= 0) {
                        newEnvironment.put(entry.substring(0, split), entry.substring(split + 1))
                    }
                }
                var i = 0
                val env = arrayOfNulls<String>(newEnvironment.size)
                for (entry in newEnvironment.entries) {
                    env[i] = entry.key + "=" + entry.value
                    i++
                }
                environment = env.requireNoNulls()
            }

            // setup our process, retrieve STDIN stream, and STDOUT/STDERR
            // gobblers
            val process = Runtime.getRuntime().exec(shell, environment)
            val STDIN = DataOutputStream(process.getOutputStream())
            val STDOUT = StreamGobbler(
                shellUpper + "-", process.getInputStream(),
                res
            )
            val STDERR = StreamGobbler(
                shellUpper + "*", process.getErrorStream(),
                if (wantSTDERR) res else null
            )

            // start gobbling and write our commands to the shell
            STDOUT.start()
            STDERR.start()
            try {
                for (write in commands) {
                    STDIN.write((write + "\n").toByteArray(charset("UTF-8")))
                    STDIN.flush()
                }
                STDIN.write("exit\n".toByteArray(charset("UTF-8")))
                STDIN.flush()
            } catch (e: IOException) {
                if (e.message!!.contains("EPIPE") || e.message!!.contains("Stream closed")) {
                    // Method most horrid to catch broken pipe, in which case we
                    // do nothing. The command is not a shell, the shell closed
                    // STDIN, the script already contained the exit command, etc.
                    // these cases we want the output instead of returning null.
                } else {
                    // other issues we don't know how to handle, leads to
                    // returning null
                    throw e
                }
            }

            // wait for our process to finish, while we gobble away in the
            // background
            process.waitFor()

            // make sure our threads are done gobbling, our streams are closed,
            // and the process is destroyed - while the latter two shouldn't be
            // needed in theory, and may even produce warnings, in "normal" Java
            // they are required for guaranteed cleanup of resources, so lets be
            // safe and do this on Android as well
            try {
                STDIN.close()
            } catch (e: IOException) {
                // might be closed already
            }
            STDOUT.join()
            STDERR.join()
            process.destroy()

            // in case of su, 255 usually indicates access denied
            if (SU.isSU(shell) && (process.exitValue() == 255)) {
                res = null
            }
        } catch (e: IOException) {
            // shell probably not found
            log(e)
            res = null
        } catch (e: InterruptedException) {
            // this should really be re-thrown
            log(e)
            res = null
        }

        return res
    }

    internal var availableTestCommands: Array<String?> = arrayOf<String?>(
        "echo -BOC-",
        "id"
    )

    /**
     * See if the shell is alive, and if so, check the UID
     * 
     * @param ret          Standard output from running availableTestCommands
     * @param checkForRoot true if we are expecting this shell to be running as
     * root
     * @return true on success, false on error
     */
    internal fun parseAvailableResult(ret: MutableList<String>?, checkForRoot: Boolean): Boolean {
        if (ret == null) return false

        // this is only one of many ways this can be done
        var echo_seen = false

        for (line in ret) {
            if (line.contains("uid=")) {
                // id command is working, let's see if we are actually root
                return !checkForRoot || line.contains("uid=0")
            } else if (line.contains("-BOC-")) {
                // if we end up here, at least the su command starts some kind
                // of shell, let's hope it has root privileges - no way to know without
                // additional native binaries
                echo_seen = true
            }
        }

        return echo_seen
    }

    /**
     * This class provides utility functions to easily execute commands using SH
     */
    object SH {
        /**
         * Runs command and return output
         * 
         * @param command The command to run
         * @return Output of the command, or null in case of an error
         */
        fun run(command: String?): MutableList<String>? {
            return run(
                "sh", arrayOf<String?>(
                    command
                ), null, false
            )
        }

        /**
         * Runs commands and return output
         * 
         * @param commands The commands to run
         * @return Output of the commands, or null in case of an error
         */
        fun run(commands: MutableList<String?>): MutableList<String>? {
            return run("sh", commands.toTypedArray<String?>(), null, false)
        }

        /**
         * Runs commands and return output
         * 
         * @param commands The commands to run
         * @return Output of the commands, or null in case of an error
         */
        fun run(commands: Array<String?>): MutableList<String>? {
            return run("sh", commands, null, false)
        }
    }

    /**
     * This class provides utility functions to easily execute commands using SU
     * (root shell), as well as detecting whether or not root is available, and
     * if so which version.
     */
    object SU {
        private var isSELinuxEnforcing: Boolean? = null
        private val suVersion = arrayOf<String?>(
            null, null
        )

        /**
         * Runs command as root (if available) and return output
         * 
         * @param command The command to run
         * @return Output of the command, or null if root isn't available or in
         * case of an error
         */
        fun run(command: String?): MutableList<String>? {
            return run(
                "su", arrayOf<String?>(
                    command
                ), null, false
            )
        }

        /**
         * Runs commands as root (if available) and return output
         * 
         * @param commands The commands to run
         * @return Output of the commands, or null if root isn't available or in
         * case of an error
         */
        fun run(commands: MutableList<String?>): MutableList<String>? {
            return run("su", commands.toTypedArray<String?>(), null, false)
        }

        /**
         * Runs commands as root (if available) and return output
         * 
         * @param commands The commands to run
         * @return Output of the commands, or null if root isn't available or in
         * case of an error
         */
        fun run(commands: Array<String?>): MutableList<String>? {
            return run("su", commands, null, false)
        }

        /**
         * Detects whether or not superuser access is available, by checking the
         * output of the "id" command if available, checking if a shell runs at
         * all otherwise
         * 
         * @return True if superuser access available
         */
        fun available(): Boolean {
            // this is only one of many ways this can be done

            val ret: MutableList<String>? = run(availableTestCommands)
            return parseAvailableResult(ret, true)
        }

        /**
         * 
         * 
         * Detects the version of the su binary installed (if any), if supported
         * by the binary. Most binaries support two different version numbers,
         * the public version that is displayed to users, and an internal
         * version number that is used for version number comparisons. Returns
         * null if su not available or retrieving the version isn't supported.
         * 
         * 
         * 
         * Note that su binary version and GUI (APK) version can be completely
         * different.
         * 
         * 
         * 
         * This function caches its result to improve performance on multiple
         * calls
         * 
         * 
         * @param internal Request human-readable version or application
         * internal version
         * @return String containing the su version or null
         */
        @Synchronized
        fun version(internal: Boolean): String? {
            val idx = if (internal) 0 else 1
            if (suVersion[idx] == null) {
                var version: String? = null

                val ret = Shell.run(
                    if (internal) "su -V" else "su -v",
                    arrayOf<String?>("exit"),
                    null,
                    false
                )

                if (ret != null) {
                    for (line in ret) {
                        if (!internal) {
                            if (line.trim { it <= ' ' } != "") {
                                version = line
                                break
                            }
                        } else {
                            try {
                                if (line.toInt() > 0) {
                                    version = line
                                    break
                                }
                            } catch (e: NumberFormatException) {
                                // should be parsable, try next line otherwise
                            }
                        }
                    }
                }

                suVersion[idx] = version
            }
            return suVersion[idx]
        }

        /**
         * Attempts to deduce if the shell command refers to a su shell
         * 
         * @param shell Shell command to run
         * @return Shell command appears to be su
         */
        fun isSU(shell: String): Boolean {
            // Strip parameters
            var shell = shell
            var pos = shell.indexOf(' ')
            if (pos >= 0) {
                shell = shell.substring(0, pos)
            }

            // Strip path
            pos = shell.lastIndexOf('/')
            if (pos >= 0) {
                shell = shell.substring(pos + 1)
            }

            return shell == "su"
        }

        /**
         * Constructs a shell command to start a su shell using the supplied uid
         * and SELinux context. This is can be an expensive operation, consider
         * caching the result.
         * 
         * @param uid     Uid to use (0 == root)
         * @param context (SELinux) context name to use or null
         * @return Shell command
         */
        fun shell(uid: Int, context: String?): String {
            // su[ --context <context>][ <uid>]
            var shell = "su"

            if ((context != null) && isSELinuxEnforcing()) {
                val display: String? = version(false)
                val internal: String? = version(true)

                // We only know the format for SuperSU v1.90+ right now
                //TODO add detection for other su's that support this
                if ((display != null) &&
                    (internal != null) &&
                    (display.endsWith("SUPERSU")) &&
                    (internal.toInt() >= 190)
                ) {
                    shell = String.format(Locale.ENGLISH, "%s --context %s", shell, context)
                }
            }

            // Most su binaries support the "su <uid>" format, but in case
            // they don't, lets skip it for the default 0 (root) case
            if (uid > 0) {
                shell = String.format(Locale.ENGLISH, "%s %d", shell, uid)
            }

            return shell
        }

        /**
         * Constructs a shell command to start a su shell connected to mount
         * master daemon, to perform public mounts on Android 4.3+ (or 4.2+ in
         * SELinux enforcing mode)
         * 
         * @return Shell command
         */
        fun shellMountMaster(): String {
            if (Build.VERSION.SDK_INT >= 17) {
                return "su --mount-master"
            }
            return "su"
        }

        /**
         * Detect if SELinux is set to enforcing, caches result
         * 
         * @return true if SELinux set to enforcing, or false in the case of
         * permissive or not present
         */
        @Synchronized
        fun isSELinuxEnforcing(): Boolean {
            if (isSELinuxEnforcing == null) {
                var enforcing: Boolean? = null

                // First known firmware with SELinux built-in was a 4.2 (17)
                // leak
                if (Build.VERSION.SDK_INT >= 17) {
                    // Detect enforcing through sysfs, not always present
                    val f = File("/sys/fs/selinux/enforce")
                    if (f.exists()) {
                        try {
                            val `is`: InputStream = FileInputStream("/sys/fs/selinux/enforce")
                            try {
                                enforcing = (`is`.read() == '1'.code)
                            } finally {
                                `is`.close()
                            }
                        } catch (e: Exception) {
                            // we might not be allowed to read, thanks SELinux
                        }
                    }

                    // 4.4+ has a new API to detect SELinux mode, so use it
                    // SELinux is typically in enforced mode, but emulators may have SELinux disabled
                    if (enforcing == null) {
                        try {
                            val seLinux = Class.forName("android.os.SELinux")
                            val isSELinuxEnforced = seLinux.getMethod("isSELinuxEnforced")
                            enforcing = isSELinuxEnforced.invoke(seLinux.newInstance()) as Boolean?
                        } catch (e: Exception) {
                            // 4.4+ release builds are enforcing by default, take the gamble
                            enforcing = (Build.VERSION.SDK_INT >= 19)
                        }
                    }
                }

                if (enforcing == null) {
                    enforcing = false
                }

                isSELinuxEnforcing = enforcing
            }
            return isSELinuxEnforcing!!
        }

        /**
         * 
         * 
         * Clears results cached by isSELinuxEnforcing() and version(boolean
         * internal) calls.
         * 
         * 
         * 
         * Most apps should never need to call this, as neither enforcing status
         * nor su version is likely to change on a running device - though it is
         * not impossible.
         * 
         */
        @Synchronized
        fun clearCachedResults() {
            isSELinuxEnforcing = null
            suVersion[0] = null
            suVersion[1] = null
        }
    }

    interface OnResult {
        companion object {
            // for any onCommandResult callback
            val WATCHDOG_EXIT: Int = -1
            val SHELL_DIED: Int = -2

            // for Interactive.open() callbacks only
            val SHELL_EXEC_FAILED: Int = -3
            val SHELL_WRONG_UID: Int = -4
            const val SHELL_RUNNING: Int = 0
        }
    }

    /**
     * Command result callback, notifies the recipient of the completion of a
     * command block, including the (last) exit code, and the full output
     */
    fun interface OnCommandResultListener : OnResult {
        /**
         * 
         * 
         * Command result callback
         * 
         * 
         * 
         * Depending on how and on which thread the shell was created, this
         * callback may be executed on one of the gobbler threads. In that case,
         * it is important the callback returns as quickly as possible, as
         * delays in this callback may pause the native process or even result
         * in a deadlock
         * 
         * 
         * 
         * See [Interactive] for threading details
         * 
         * 
         * @param commandCode Value previously supplied to addCommand
         * @param exitCode    Exit code of the last command in the block
         * @param output      All output generated by the command block
         */
        fun onCommandResult(commandCode: Int, exitCode: Int, output: MutableList<String>?)
    }

    /**
     * Command per line callback for parsing the output line by line without
     * buffering It also notifies the recipient of the completion of a command
     * block, including the (last) exit code.
     */
    interface OnCommandLineListener : OnResult, OnLineListener {
        /**
         * 
         * 
         * Command result callback
         * 
         * 
         * 
         * Depending on how and on which thread the shell was created, this
         * callback may be executed on one of the gobbler threads. In that case,
         * it is important the callback returns as quickly as possible, as
         * delays in this callback may pause the native process or even result
         * in a deadlock
         * 
         * 
         * 
         * See [Interactive] for threading details
         * 
         * 
         * @param commandCode Value previously supplied to addCommand
         * @param exitCode    Exit code of the last command in the block
         */
        fun onCommandResult(commandCode: Int, exitCode: Int)
    }

    /**
     * Internal class to store command block properties
     */
    internal class Command(
        val commands: Array<String?>, val code: Int,
        val onCommandResultListener: OnCommandResultListener?,
        val onCommandLineListener: OnCommandLineListener?
    ) {
        val marker: String

        init {
            this.marker = UUID.randomUUID().toString() + String.format("-%08x", ++commandCounter)
        }

        companion object {
            private var commandCounter = 0
        }
    }

    /**
     * Builder class for [Interactive]
     */
    class Builder {
        var handler: Handler? = null
        var autoHandler = true
        var shell = "sh"
        var wantSTDERR = false
        internal val commands: MutableList<Command> = LinkedList<Command>()
        val environment: MutableMap<String?, String?> = HashMap<String?, String?>()
        var onSTDOUTLineListener: OnLineListener? = null
        var onSTDERRLineListener: OnLineListener? = null
        var watchdogTimeout = 0

        /**
         * 
         * 
         * Set a custom handler that will be used to post all callbacks to
         * 
         * 
         * 
         * See [Interactive] for further details on threading and
         * handlers
         * 
         * 
         * @param handler Handler to use
         * @return This Builder object for method chaining
         */
        fun setHandler(handler: Handler?): Builder {
            this.handler = handler
            return this
        }

        /**
         * 
         * 
         * Automatically create a handler if possible ? Default to true
         * 
         * 
         * 
         * See [Interactive] for further details on threading and
         * handlers
         * 
         * 
         * @param autoHandler Auto-create handler ?
         * @return This Builder object for method chaining
         */
        fun setAutoHandler(autoHandler: Boolean): Builder {
            this.autoHandler = autoHandler
            return this
        }

        /**
         * Set shell binary to use. Usually "sh" or "su", do not use a full path
         * unless you have a good reason to
         * 
         * @param shell Shell to use
         * @return This Builder object for method chaining
         */
        fun setShell(shell: String): Builder {
            this.shell = shell
            return this
        }

        /**
         * Convenience function to set "sh" as used shell
         * 
         * @return This Builder object for method chaining
         */
        fun useSH(): Builder {
            return setShell("sh")
        }

        /**
         * Convenience function to set "su" as used shell
         * 
         * @return This Builder object for method chaining
         */
        fun useSU(): Builder {
            return setShell("su")
        }

        /**
         * Set if error output should be appended to command block result output
         * 
         * @param wantSTDERR Want error output ?
         * @return This Builder object for method chaining
         */
        fun setWantSTDERR(wantSTDERR: Boolean): Builder {
            this.wantSTDERR = wantSTDERR
            return this
        }

        /**
         * Add or update an environment variable
         * 
         * @param key   Key of the environment variable
         * @param value Value of the environment variable
         * @return This Builder object for method chaining
         */
        fun addEnvironment(key: String?, value: String?): Builder {
            environment.put(key, value)
            return this
        }

        /**
         * Add or update environment variables
         * 
         * @param addEnvironment Map of environment variables
         * @return This Builder object for method chaining
         */
        fun addEnvironment(addEnvironment: MutableMap<String?, String?>): Builder {
            environment.putAll(addEnvironment)
            return this
        }

        /**
         * 
         * 
         * Add a command to execute, with a callback to be called on completion
         * 
         * 
         * 
         * The thread on which the callback executes is dependent on various
         * factors, see [Interactive] for further details
         * 
         * 
         * @param command                 Command to execute
         * @param code                    User-defined value passed back to the callback
         * @param onCommandResultListener Callback to be called on completion
         * @return This Builder object for method chaining
         */
        /**
         * Add a command to execute
         * 
         * @param command Command to execute
         * @return This Builder object for method chaining
         */
        @JvmOverloads
        fun addCommand(
            command: String?, code: Int = 0,
            onCommandResultListener: OnCommandResultListener? = null
        ): Builder {
            return addCommand(
                arrayOf<String?>(
                    command
                ), code, onCommandResultListener
            )
        }

        /**
         * 
         * 
         * Add commands to execute, with a callback to be called on completion
         * (of all commands)
         * 
         * 
         * 
         * The thread on which the callback executes is dependent on various
         * factors, see [Interactive] for further details
         * 
         * 
         * @param commands                Commands to execute
         * @param code                    User-defined value passed back to the callback
         * @param onCommandResultListener Callback to be called on completion
         * (of all commands)
         * @return This Builder object for method chaining
         */
        /**
         * Add commands to execute
         * 
         * @param commands Commands to execute
         * @return This Builder object for method chaining
         */
        @JvmOverloads
        fun addCommand(
            commands: MutableList<String?>, code: Int = 0,
            onCommandResultListener: OnCommandResultListener? = null
        ): Builder {
            return addCommand(
                commands.toTypedArray<String?>(), code,
                onCommandResultListener
            )
        }

        /**
         * 
         * 
         * Add commands to execute, with a callback to be called on completion
         * (of all commands)
         * 
         * 
         * 
         * The thread on which the callback executes is dependent on various
         * factors, see [Interactive] for further details
         * 
         * 
         * @param commands                Commands to execute
         * @param code                    User-defined value passed back to the callback
         * @param onCommandResultListener Callback to be called on completion
         * (of all commands)
         * @return This Builder object for method chaining
         */
        /**
         * Add commands to execute
         * 
         * @param commands Commands to execute
         * @return This Builder object for method chaining
         */
        @JvmOverloads
        fun addCommand(
            commands: Array<String?>, code: Int = 0,
            onCommandResultListener: OnCommandResultListener? = null
        ): Builder {
            this.commands.add(Command(commands, code, onCommandResultListener, null))
            return this
        }

        /**
         * 
         * 
         * Set a callback called for every line output to STDOUT by the shell
         * 
         * 
         * 
         * The thread on which the callback executes is dependent on various
         * factors, see [Interactive] for further details
         * 
         * 
         * @param onLineListener Callback to be called for each line
         * @return This Builder object for method chaining
         */
        fun setOnSTDOUTLineListener(onLineListener: OnLineListener?): Builder {
            this.onSTDOUTLineListener = onLineListener
            return this
        }

        /**
         * 
         * 
         * Set a callback called for every line output to STDERR by the shell
         * 
         * 
         * 
         * The thread on which the callback executes is dependent on various
         * factors, see [Interactive] for further details
         * 
         * 
         * @param onLineListener Callback to be called for each line
         * @return This Builder object for method chaining
         */
        fun setOnSTDERRLineListener(onLineListener: OnLineListener?): Builder {
            this.onSTDERRLineListener = onLineListener
            return this
        }

        /**
         * 
         * 
         * Enable command timeout callback
         * 
         * 
         * 
         * This will invoke the onCommandResult() callback with exitCode
         * WATCHDOG_EXIT if a command takes longer than watchdogTimeout seconds
         * to complete.
         * 
         * 
         * 
         * If a watchdog timeout occurs, it generally means that the Interactive
         * session is out of sync with the shell process. The caller should
         * close the current session and open a new one.
         * 
         * 
         * @param watchdogTimeout Timeout, in seconds; 0 to disable
         * @return This Builder object for method chaining
         */
        fun setWatchdogTimeout(watchdogTimeout: Int): Builder {
            this.watchdogTimeout = watchdogTimeout
            return this
        }

        /**
         * Construct a [Interactive] instance, and start the shell
         * 
         * @return Interactive shell
         */
        fun open(): Interactive {
            return Interactive(this, null)
        }

        /**
         * Construct a [Interactive] instance, try to start the
         * shell, and call onCommandResultListener to report success or failure
         * 
         * @param onCommandResultListener Callback to return shell open status
         * @return Interactive shell
         */
        fun open(onCommandResultListener: OnCommandResultListener?): Interactive {
            return Interactive(this, onCommandResultListener)
        }
    }

    /**
     * 
     * 
     * An interactive shell - initially created with [Builder] -
     * that executes blocks of commands you supply in the background, optionally
     * calling callbacks as each block completes.
     * 
     * 
     * 
     * STDERR output can be supplied as well, but due to compatibility with
     * older Android versions, wantSTDERR is not implemented using
     * redirectErrorStream, but rather appended to the output. STDOUT and STDERR
     * are thus not guaranteed to be in the correct order in the output.
     * 
     * 
     * 
     * Note as well that the close() and waitForIdle() methods will
     * intentionally crash when run in debug mode from the main thread of the
     * application. Any blocking call should be run from a background thread.
     * 
     * 
     * 
     * When in debug mode, the code will also excessively log the commands
     * passed to and the output returned from the shell.
     * 
     * 
     * 
     * Though this function uses background threads to gobble STDOUT and STDERR
     * so a deadlock does not occur if the shell produces massive output, the
     * output is still stored in a List&lt;String&gt;, and as such doing
     * something like *'ls -lR /'* will probably have you run out of
     * memory when using a [OnCommandResultListener]. A work-around
     * is to not supply this callback, but using (only)
     * [Builder.setOnSTDOUTLineListener]. This way,
     * an internal buffer will not be created and wasting your memory.
     * 
     * <h3>Callbacks, threads and handlers</h3>
     * 
     * 
     * On which thread the callbacks execute is dependent on your
     * initialization. You can supply a custom Handler using
     * [Builder.setHandler] if needed. If you do not supply
     * a custom Handler - unless you set
     * [Builder.setAutoHandler] to false - a Handler will
     * be auto-created if the thread used for instantiation of the object has a
     * Looper.
     * 
     * 
     * 
     * If no Handler was supplied and it was also not auto-created, all
     * callbacks will be called from either the STDOUT or STDERR gobbler
     * threads. These are important threads that should be blocked as little as
     * possible, as blocking them may in rare cases pause the native process or
     * even create a deadlock.
     * 
     * 
     * 
     * The main thread must certainly have a Looper, thus if you call
     * [Builder.open] from the main thread, a handler will (by
     * default) be auto-created, and all the callbacks will be called on the
     * main thread. While this is often convenient and easy to code with, you
     * should be aware that if your callbacks are 'expensive' to execute, this
     * may negatively impact UI performance.
     * 
     * 
     * 
     * Background threads usually do *not* have a Looper, so calling
     * [Builder.open] from such a background thread will (by
     * default) result in all the callbacks being executed in one of the gobbler
     * threads. You will have to make sure the code you execute in these
     * callbacks is thread-safe.
     * 
     */
    class Interactive constructor(
        builder: Builder,
        onCommandResultListener: OnCommandResultListener?
    ) {
        private val handler: Handler?
        private val autoHandler: Boolean
        private val shell: String
        private val wantSTDERR: Boolean
        private val commands: MutableList<Command>
        private val environment: MutableMap<String?, String?>
        private val onSTDOUTLineListener: OnLineListener?
        private val onSTDERRLineListener: OnLineListener?
        private var watchdogTimeout: Int

        private var process: Process? = null
        private var STDIN: DataOutputStream? = null
        private var STDOUT: StreamGobbler? = null
        private var STDERR: StreamGobbler? = null
        private var watchdog: ScheduledThreadPoolExecutor? = null

        @Volatile
        private var running = false

        @Volatile
        private var idle = true // read/write only synchronized

        @Volatile
        private var closed = true

        @Volatile
        private var callbacks = 0

        @Volatile
        private var watchdogCount = 0

        private val idleSync = Any()
        private val callbackSync = Any()

        @Volatile
        private var lastExitCode = 0

        @Volatile
        private var lastMarkerSTDOUT: String? = null

        @Volatile
        private var lastMarkerSTDERR: String? = null

        @Volatile
        private var command: Command? = null

        @Volatile
        private var buffer: MutableList<String>? = null

        /**
         * The only way to create an instance: Shell.Builder::open()
         * 
         * @param builder Builder class to take values from
         */
        init {
            autoHandler = builder.autoHandler
            shell = builder.shell
            wantSTDERR = builder.wantSTDERR
            commands = builder.commands
            environment = builder.environment
            onSTDOUTLineListener = builder.onSTDOUTLineListener
            onSTDERRLineListener = builder.onSTDERRLineListener
            watchdogTimeout = builder.watchdogTimeout

            // If a looper is available, we offload the callbacks from the
            // gobbling threads
            // to whichever thread created us. Would normally do this in open(),
            // but then we could not declare handler as final
            if ((Looper.myLooper() != null) && (builder.handler == null) && autoHandler) {
                handler = Handler()
            } else {
                handler = builder.handler
            }

            if (onCommandResultListener != null) {
                // Allow up to 60 seconds for SuperSU/Superuser dialog, then enable
                // the user-specified timeout for all subsequent operations
                watchdogTimeout = 60
                commands.add(
                    0,
                    Command(
                        availableTestCommands,
                        0,
                        OnCommandResultListener { commandCode: Int, exitCode: Int, output: MutableList<String>? ->
                            var exitCode = exitCode
                            if ((exitCode == OnResult.Companion.SHELL_RUNNING) &&
                                !parseAvailableResult(output, SU.isSU(shell))
                            ) {
                                // shell is up, but it's brain-damaged
                                exitCode = OnResult.Companion.SHELL_WRONG_UID
                            }
                            watchdogTimeout = builder.watchdogTimeout
                            onCommandResultListener.onCommandResult(0, exitCode, output)
                        },
                        null
                    )
                )
            }

            if (!open() && (onCommandResultListener != null)) {
                onCommandResultListener.onCommandResult(
                    0,
                    OnResult.Companion.SHELL_EXEC_FAILED, null
                )
            }
        }

        /**
         * 
         * 
         * Add a command to execute, with a callback to be called on completion
         * 
         * 
         * 
         * The thread on which the callback executes is dependent on various
         * factors, see [Interactive] for further details
         * 
         * 
         * @param command                 Command to execute
         * @param code                    User-defined value passed back to the callback
         * @param onCommandResultListener Callback to be called on completion
         */
        /**
         * Add a command to execute
         * 
         * @param command Command to execute
         */
        @JvmOverloads
        fun addCommand(
            command: String?, code: Int = 0,
            onCommandResultListener: OnCommandResultListener? = null as OnCommandResultListener?
        ) {
            addCommand(
                arrayOf<String?>(
                    command
                ), code, onCommandResultListener
            )
        }

        /**
         * 
         * 
         * Add a command to execute, with a callback. This callback gobbles the
         * output line by line without buffering it and also returns the result
         * code on completion.
         * 
         * 
         * 
         * The thread on which the callback executes is dependent on various
         * factors, see [Interactive] for further details
         * 
         * 
         * @param command               Command to execute
         * @param code                  User-defined value passed back to the callback
         * @param onCommandLineListener Callback
         */
        fun addCommand(command: String?, code: Int, onCommandLineListener: OnCommandLineListener?) {
            addCommand(
                arrayOf<String?>(
                    command
                ), code, onCommandLineListener
            )
        }

        /**
         * 
         * 
         * Add commands to execute, with a callback to be called on completion
         * (of all commands)
         * 
         * 
         * 
         * The thread on which the callback executes is dependent on various
         * factors, see [Interactive] for further details
         * 
         * 
         * @param commands                Commands to execute
         * @param code                    User-defined value passed back to the callback
         * @param onCommandResultListener Callback to be called on completion
         * (of all commands)
         */
        /**
         * Add commands to execute
         * 
         * @param commands Commands to execute
         */
        @JvmOverloads
        fun addCommand(
            commands: MutableList<String?>, code: Int = 0,
            onCommandResultListener: OnCommandResultListener? = null as OnCommandResultListener?
        ) {
            addCommand(commands.toTypedArray<String?>(), code, onCommandResultListener)
        }

        /**
         * 
         * 
         * Add commands to execute, with a callback. This callback gobbles the
         * output line by line without buffering it and also returns the result
         * code on completion.
         * 
         * 
         * 
         * The thread on which the callback executes is dependent on various
         * factors, see [Interactive] for further details
         * 
         * 
         * @param commands              Commands to execute
         * @param code                  User-defined value passed back to the callback
         * @param onCommandLineListener Callback
         */
        fun addCommand(
            commands: MutableList<String?>, code: Int,
            onCommandLineListener: OnCommandLineListener?
        ) {
            addCommand(commands.toTypedArray<String?>(), code, onCommandLineListener)
        }

        /**
         * Add commands to execute
         * 
         * @param commands Commands to execute
         */
        fun addCommand(commands: Array<String?>) {
            addCommand(commands, 0, null as OnCommandResultListener?)
        }

        /**
         * 
         * 
         * Add commands to execute, with a callback to be called on completion
         * (of all commands)
         * 
         * 
         * 
         * The thread on which the callback executes is dependent on various
         * factors, see [Interactive] for further details
         * 
         * 
         * @param commands                Commands to execute
         * @param code                    User-defined value passed back to the callback
         * @param onCommandResultListener Callback to be called on completion
         * (of all commands)
         */
        @Synchronized
        fun addCommand(
            commands: Array<String?>, code: Int,
            onCommandResultListener: OnCommandResultListener?
        ) {
            this.commands.add(Command(commands, code, onCommandResultListener, null))
            runNextCommand()
        }

        /**
         * 
         * 
         * Add commands to execute, with a callback. This callback gobbles the
         * output line by line without buffering it and also returns the result
         * code on completion.
         * 
         * 
         * 
         * The thread on which the callback executes is dependent on various
         * factors, see [Interactive] for further details
         * 
         * 
         * @param commands              Commands to execute
         * @param code                  User-defined value passed back to the callback
         * @param onCommandLineListener Callback
         */
        @Synchronized
        fun addCommand(
            commands: Array<String?>, code: Int,
            onCommandLineListener: OnCommandLineListener?
        ) {
            this.commands.add(Command(commands, code, null, onCommandLineListener))
            runNextCommand()
        }

        /**
         * Called from a ScheduledThreadPoolExecutor timer thread every second
         * when there is an outstanding command
         */
        @Synchronized
        private fun handleWatchdog() {
            val exitCode: Int

            if (watchdog == null) return
            if (watchdogTimeout == 0) return

            if (!isRunning()) {
                exitCode = OnResult.Companion.SHELL_DIED
            } else if (watchdogCount++ < watchdogTimeout) {
                return
            } else {
                exitCode = OnResult.Companion.WATCHDOG_EXIT
            }

            postCallback(command!!, exitCode, buffer)

            // prevent multiple callbacks for the same command
            command = null
            buffer = null
            idle = true

            watchdog!!.shutdown()
            watchdog = null
            kill()
        }

        /**
         * Start the periodic timer when a command is submitted
         */
        private fun startWatchdog() {
            if (watchdogTimeout == 0) {
                return
            }
            watchdogCount = 0
            watchdog = ScheduledThreadPoolExecutor(1)
            watchdog!!.scheduleWithFixedDelay(
                Runnable { this.handleWatchdog() },
                1,
                1,
                TimeUnit.SECONDS
            )
        }

        /**
         * Disable the watchdog timer upon command completion
         */
        private fun stopWatchdog() {
            if (watchdog != null) {
                watchdog!!.shutdownNow()
                watchdog = null
            }
        }

        /**
         * Run the next command if any and if ready
         * 
         * @param notifyIdle signals idle state if no commands left ?
         */
        /**
         * Run the next command if any and if ready, signals idle state if no
         * commands left
         */
        private fun runNextCommand(notifyIdle: Boolean = true) {
            // must always be called from a synchronized method

            val running = isRunning()
            if (!running) idle = true

            if (running && idle && (commands.size > 0)) {
                val command = commands.get(0)
                commands.removeAt(0)

                buffer = null
                lastExitCode = 0
                lastMarkerSTDOUT = null
                lastMarkerSTDERR = null

                if (command.commands.size > 0) {
                    try {
                        if (command.onCommandResultListener != null) {
                            // no reason to store the output if we don't have an
                            // OnCommandResultListener
                            // user should catch the output with an
                            // OnLineListener in this case
                            buffer = Collections.synchronizedList<String?>(ArrayList<String?>())
                        }

                        idle = false
                        this.command = command
                        startWatchdog()
                        for (write in command.commands) {
                            STDIN!!.write((write + "\n").toByteArray(charset("UTF-8")))
                        }
                        STDIN!!.write(("echo " + command.marker + " $?\n").toByteArray(charset("UTF-8")))
                        STDIN!!.write(("echo " + command.marker + " >&2\n").toByteArray(charset("UTF-8")))
                        STDIN!!.flush()
                    } catch (e: IOException) {
                        // STDIN might have closed
                    }
                } else {
                    runNextCommand(false)
                }
            } else if (!running) {
                // our shell died for unknown reasons - abort all submissions
                while (commands.size > 0) {
                    postCallback(commands.removeAt(0), OnResult.Companion.SHELL_DIED, null)
                }
            }

            if (idle && notifyIdle) {
                synchronized(idleSync) {
                    (idleSync as Object).notifyAll()
                }
            }
        }

        /**
         * Processes a STDOUT/STDERR line containing an end/exitCode marker
         */
        @Synchronized
        private fun processMarker() {
            val currentCommand = command ?: return
            if (currentCommand.marker == lastMarkerSTDOUT
                && (currentCommand.marker == lastMarkerSTDERR)
            ) {
                postCallback(currentCommand, lastExitCode, buffer)
                stopWatchdog()
                command = null
                buffer = null
                idle = true
                runNextCommand()
            }
        }

        /**
         * Process a normal STDOUT/STDERR line
         * 
         * @param line     Line to process
         * @param listener Callback to call or null
         */
        @Synchronized
        private fun processLine(line: String, listener: OnLineListener?) {
            if (listener != null) {
                if (handler != null) {
                    val fLine = line
                    val fListener: OnLineListener? = listener

                    startCallback()
                    handler.post(Runnable {
                        try {
                            fListener!!.onLine(fLine)
                        } finally {
                            endCallback()
                        }
                    })
                } else {
                    listener.onLine(line)
                }
            }
        }

        /**
         * Add line to internal buffer
         * 
         * @param line Line to add
         */
        @Synchronized
        private fun addBuffer(line: String?) {
            if (buffer != null) {
                buffer!!.add(line!!)
            }
        }

        /**
         * Increase callback counter
         */
        private fun startCallback() {
            synchronized(callbackSync) {
                callbacks++
            }
        }

        /**
         * Schedule a callback to run on the appropriate thread
         */
        private fun postCallback(
            fCommand: Command, fExitCode: Int,
            fOutput: MutableList<String>?
        ) {
            if (fCommand.onCommandResultListener == null && fCommand.onCommandLineListener == null) {
                return
            }
            if (handler == null) {
                if (fCommand.onCommandResultListener != null) fCommand.onCommandResultListener.onCommandResult(
                    fCommand.code, fExitCode,
                    fOutput
                )
                if (fCommand.onCommandLineListener != null) fCommand.onCommandLineListener.onCommandResult(
                    fCommand.code,
                    fExitCode
                )
                return
            }
            startCallback()
            handler.post(Runnable {
                try {
                    if (fCommand.onCommandResultListener != null) fCommand.onCommandResultListener.onCommandResult(
                        fCommand.code,
                        fExitCode, fOutput
                    )
                    if (fCommand.onCommandLineListener != null) fCommand.onCommandLineListener
                        .onCommandResult(fCommand.code, fExitCode)
                } finally {
                    endCallback()
                }
            })
        }

        /**
         * Decrease callback counter, signals callback complete state when
         * dropped to 0
         */
        private fun endCallback() {
            synchronized(callbackSync) {
                callbacks--
                if (callbacks == 0) {
                    (callbackSync as Object).notifyAll()
                }
            }
        }

        /**
         * Internal call that launches the shell, starts gobbling, and starts
         * executing commands. See [Interactive]
         * 
         * @return Opened successfully ?
         */
        @Synchronized
        private fun open(): Boolean {
            try {
                // setup our process, retrieve STDIN stream, and STDOUT/STDERR
                // gobblers
                if (environment.size == 0) {
                    process = Runtime.getRuntime().exec(shell)
                } else {
                    val newEnvironment: MutableMap<String, String> = HashMap<String, String>()
                    newEnvironment.putAll(System.getenv())
                    for (entry in environment.entries) {
                        val key = entry.key
                        val value = entry.value
                        if (key != null && value != null) {
                            newEnvironment[key] = value
                        }
                    }
                    var i = 0
                    val env = arrayOfNulls<String>(newEnvironment.size)
                    for (entry in newEnvironment.entries) {
                        env[i] = entry.key + "=" + entry.value
                        i++
                    }
                    process = Runtime.getRuntime().exec(shell, env.requireNoNulls())
                }

                STDIN = DataOutputStream(process!!.getOutputStream())
                STDOUT = StreamGobbler(
                    shell.uppercase() + "-",
                    process!!.getInputStream(), OnLineListener { line: String? ->
                        synchronized(this@Interactive) {
                            val currentCommand = command ?: return@OnLineListener
                            var contentPart = line
                            var markerPart: String? = null

                            val markerIndex = line!!.indexOf(currentCommand.marker)
                            if (markerIndex == 0) {
                                contentPart = null
                                markerPart = line
                            } else if (markerIndex > 0) {
                                contentPart = line.substring(0, markerIndex)
                                markerPart = line.substring(markerIndex)
                            }

                            if (contentPart != null) {
                                addBuffer(contentPart)
                                processLine(contentPart, onSTDOUTLineListener)
                                processLine(contentPart, currentCommand.onCommandLineListener)
                            }
                            if (markerPart != null) {
                                try {
                                    lastExitCode =
                                        markerPart.substring(currentCommand.marker.length + 1).toInt(10)
                                } catch (e: Exception) {
                                    // this really shouldn't happen
                                    e.printStackTrace()
                                }
                                lastMarkerSTDOUT = currentCommand.marker
                                processMarker()
                            }
                        }
                    })
                STDERR = StreamGobbler(
                    shell.uppercase() + "*",
                    process!!.getErrorStream(), OnLineListener { line: String? ->
                        synchronized(this@Interactive) {
                            val currentCommand = command ?: return@OnLineListener
                            var contentPart = line

                            val markerIndex = line!!.indexOf(currentCommand.marker)
                            if (markerIndex == 0) {
                                contentPart = null
                            } else if (markerIndex > 0) {
                                contentPart = line.substring(0, markerIndex)
                            }

                            if (contentPart != null) {
                                if (wantSTDERR) addBuffer(contentPart)
                                processLine(contentPart, onSTDERRLineListener)
                            }
                            if (markerIndex >= 0) {
                                lastMarkerSTDERR = currentCommand.marker
                                processMarker()
                            }
                        }
                    })

                // start gobbling and write our commands to the shell
                STDOUT!!.start()
                STDERR!!.start()

                running = true
                closed = false

                runNextCommand()

                return true
            } catch (e: IOException) {
                // shell probably not found
                return false
            }
        }

        /**
         * Close shell and clean up all resources. Call this when you are done
         * with the shell. If the shell is not idle (all commands completed) you
         * should not call this method from the main UI thread because it may
         * block for a long time. This method will intentionally crash your app
         * (if in debug mode) if you try to do this anyway.
         */
        fun close() {
            val _idle = isIdle() // idle must be checked synchronized

            synchronized(this) {
                if (!running) return
                running = false
                closed = true
            }

            if (!_idle) waitForIdle()

            try {
                try {
                    STDIN!!.write(("exit\n").toByteArray(charset("UTF-8")))
                    STDIN!!.flush()
                } catch (e: IOException) {
                    if (e.message!!.contains("EPIPE") || e.message!!.contains("Stream closed")) {
                        // we're not running a shell, the shell closed STDIN,
                        // the script already contained the exit command, etc.                        
                    } else {
                        throw e
                    }
                }

                // wait for our process to finish, while we gobble away in the
                // background
                process!!.waitFor()

                // make sure our threads are done gobbling, our streams are
                // closed, and the process is destroyed - while the latter two
                // shouldn't be needed in theory, and may even produce warnings,
                // in "normal" Java they are required for guaranteed cleanup of
                // resources, so lets be safe and do this on Android as well
                try {
                    STDIN!!.close()
                } catch (e: IOException) {
                    // STDIN going missing is no reason to abort 
                }
                STDOUT!!.join()
                STDERR!!.join()
                stopWatchdog()
                process!!.destroy()
            } catch (e: IOException) {
                // various unforseen IO errors may still occur
            } catch (e: InterruptedException) {
                // this should really be re-thrown
            }
        }

        /**
         * Try to clean up as much as possible from a shell that's gotten itself
         * wedged. Hopefully the StreamGobblers will croak on their own when the
         * other side of the pipe is closed.
         */
        @Synchronized
        fun kill() {
            running = false
            closed = true

            try {
                STDIN!!.close()
            } catch (e: IOException) {
                // in case it was closed
            }
            try {
                process!!.destroy()
            } catch (e: Exception) {
                // in case it was already destroyed or can't be
            }

            idle = true
            synchronized(idleSync) {
                (idleSync as Object).notifyAll()
            }
        }

        /**
         * Is our shell still running ?
         * 
         * @return Shell running ?
         */
        fun isRunning(): Boolean {
            if (process == null) {
                return false
            }
            try {
                process!!.exitValue()
                return false
            } catch (e: IllegalThreadStateException) {
                // if this is thrown, we're still running
            }
            return true
        }

        /**
         * Have all commands completed executing ?
         * 
         * @return Shell idle ?
         */
        @Synchronized
        fun isIdle(): Boolean {
            if (!isRunning()) {
                idle = true
                synchronized(idleSync) {
                    (idleSync as Object).notifyAll()
                }
            }
            return idle
        }

        /**
         * 
         * 
         * Wait for idle state. As this is a blocking call, you should not call
         * it from the main UI thread. If you do so and debug mode is enabled,
         * this method will intentionally crash your app.
         * 
         * 
         * 
         * If not interrupted, this method will not return until all commands
         * have finished executing. Note that this does not necessarily mean
         * that all the callbacks have fired yet.
         * 
         * 
         * 
         * If no Handler is used, all callbacks will have been executed when
         * this method returns. If a Handler is used, and this method is called
         * from a different thread than associated with the Handler's Looper,
         * all callbacks will have been executed when this method returns as
         * well. If however a Handler is used but this method is called from the
         * same thread as associated with the Handler's Looper, there is no way
         * to know.
         * 
         * 
         * 
         * In practice this means that in most simple cases all callbacks will
         * have completed when this method returns, but if you actually depend
         * on this behavior, you should make certain this is indeed the case.
         * 
         * 
         * 
         * See [Interactive] for further details on threading and
         * handlers
         * 
         * 
         * @return True if wait complete, false if wait interrupted
         */
        fun waitForIdle(): Boolean {
            if (isRunning()) {
                synchronized(idleSync) {
                    while (!idle) {
                        try {
                            (idleSync as Object).wait()
                        } catch (e: InterruptedException) {
                            return false
                        }
                    }
                }

                if ((handler != null) &&
                    (handler.getLooper() != null) &&
                    (handler.getLooper() != Looper.myLooper())
                ) {
                    // If the callbacks are posted to a different thread than
                    // this one, we can wait until all callbacks have called
                    // before returning. If we don't use a Handler at all, the
                    // callbacks are already called before we get here. If we do
                    // use a Handler but we use the same Looper, waiting here
                    // would actually block the callbacks from being called

                    synchronized(callbackSync) {
                        while (callbacks > 0) {
                            try {
                                (callbackSync as Object).wait()
                            } catch (e: InterruptedException) {
                                return false
                            }
                        }
                    }
                }
            }

            return true
        }

        /**
         * Are we using a Handler to post callbacks ?
         * 
         * @return Handler used ?
         */
        fun hasHandler(): Boolean {
            return (handler != null)
        }
    }
}
