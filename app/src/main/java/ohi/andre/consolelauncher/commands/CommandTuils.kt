package ohi.andre.consolelauncher.commands

import android.annotation.SuppressLint
import android.graphics.Color
import ohi.andre.consolelauncher.commands.main.MainPack
import ohi.andre.consolelauncher.commands.main.Param
import ohi.andre.consolelauncher.commands.main.specific.ParamCommand
import ohi.andre.consolelauncher.managers.AppsManager
import ohi.andre.consolelauncher.managers.ContactManager
import ohi.andre.consolelauncher.managers.FileManager
import ohi.andre.consolelauncher.managers.FileManager.DirInfo
import ohi.andre.consolelauncher.managers.FileManager.SpecificExtensionFileFilter
import ohi.andre.consolelauncher.managers.FileManager.SpecificNameFileFilter
import ohi.andre.consolelauncher.managers.HTMLExtractManager
import ohi.andre.consolelauncher.managers.RssManager
import ohi.andre.consolelauncher.managers.music.MusicManager2
import ohi.andre.consolelauncher.managers.notifications.NotificationManager
import ohi.andre.consolelauncher.managers.settings.LauncherSettings.getBoolean
import ohi.andre.consolelauncher.managers.xml.XMLPrefsManager.XMLPrefsRoot
import ohi.andre.consolelauncher.managers.xml.classes.XMLPrefsSave
import ohi.andre.consolelauncher.managers.xml.options.Apps
import ohi.andre.consolelauncher.managers.xml.options.Behavior
import ohi.andre.consolelauncher.managers.xml.options.Notifications
import ohi.andre.consolelauncher.managers.xml.options.Rss
import ohi.andre.consolelauncher.tuils.SimpleMutableEntry
import ohi.andre.consolelauncher.tuils.Tuils
import java.io.File
import java.util.Arrays
import java.util.Collections
import java.util.ArrayList
import ohi.andre.consolelauncher.managers.settings.LauncherSettings
import ohi.andre.consolelauncher.managers.xml.XMLPrefsManager

@SuppressLint("DefaultLocale")
object CommandTuils {
    const val DUO_COMMAND: String = "duo"
    val DUO_USAGE: String = DUO_COMMAND + " on|off|left|right|toggle|status"
    private val DUO_OPTIONS: MutableList<String?> = Collections.unmodifiableList<String?>(
        mutableListOf<String?>("on", "off", "left", "right", "toggle", "status")
    )

    private val HIDDEN_COMMANDS = arrayOf<String?>(
        "cntcts",
        "ctrlc",
        "data",
        "devutils",
        "install",
        "shell",
        "termuxopen",
        "theme",
        "themer",
        "tuixt"
    )

    @JvmStatic
    fun isHiddenCommandName(name: String?): Boolean {
        if (name == null) {
            return false
        }

        if (DUO_COMMAND.equals(name, ignoreCase = true) && !getBoolean(Behavior.duo_mode)) {
            return true
        }

        for (hidden in HIDDEN_COMMANDS) {
            if (hidden.equals(name, ignoreCase = true)) {
                return true
            }
        }
        return false
    }

    @JvmStatic
    fun duoOptions(): MutableList<String?> {
        return DUO_OPTIONS
    }

    private val extensionFileFilter = SpecificExtensionFileFilter()
    private val nameFileFilter = SpecificNameFileFilter()

    @JvmField
    var xmlPrefsEntrys: MutableList<XMLPrefsSave>? = null
    @JvmField
    var xmlPrefsFiles: MutableList<String>? = null

    //	parse a command
    @JvmStatic
    @Throws(Exception::class)
    fun parse(input: String?, info: ExecutePack): Command? {
        var input = input
        val command = Command()

        val name = findName(input)
        val cmd = info.commandGroup.getCommandByName(name)
        if (cmd == null) {
            return null
        }
        command.cmd = cmd

        input = input!!.substring(name.length)
        input = input.trim { it <= ' ' }

        val args = ArrayList<Any?>()
        var nArgs = 0
        val types: IntArray?

        try {
            if (cmd is ParamCommand) {
                val arg = param(info as MainPack, cmd, input)
                if (arg == null || !arg.found) {
                    command.indexNotFound = 0
                    args.add(input)
                    command.nArgs = 1
                    command.mArgs = args.toTypedArray<Any?>()
                    return command
                }

                input = arg.residualString
                val p = arg.arg as Param
                types = p.args()

                nArgs++
                args.add(p)
            } else {
                types = cmd.argType()
            }

            if (types != null) {
                for (count in types.indices) {
                    if (input == null) break

                    input = input.trim { it <= ' ' }
                    if (input.length == 0) {
                        break
                    }

                    val arg = getArg(info, input, types[count])
                    if (arg == null) {
                        return null
                    }

                    if (!arg.found) {
                        command.indexNotFound = if (cmd is ParamCommand) count + 1 else count
                        args.add(input)
                        command.mArgs = args.toTypedArray<Any?>()
                        command.nArgs = nArgs
                        return command
                    }

                    nArgs += arg.n
                    args.add(arg.arg)
                    input = arg.residualString
                    if (input != null && input.trim { it <= ' ' }.length == 0) {
                        input = null
                    }
                }
            }
        } catch (e: Exception) {
            Tuils.log(e)
        }

        command.mArgs = args.toTypedArray<Any?>()
        command.nArgs = nArgs

        return command
    }

    //	find command name
    private fun findName(input: String?): String {
        if (input == null || input.isEmpty()) return Tuils.EMPTYSTRING

        var inDoubleQuote = false
        var inSingleQuote = false
        var escaped = false

        for (i in 0..<input.length) {
            val c = input.get(i)
            if (escaped) {
                escaped = false
            } else if (c == '\\') {
                escaped = true
            } else if (c == '\"' && !inSingleQuote) {
                inDoubleQuote = !inDoubleQuote
            } else if (c == '\'' && !inDoubleQuote) {
                inSingleQuote = !inSingleQuote
            } else if (Character.isWhitespace(c) && !inDoubleQuote && !inSingleQuote) {
                return input.substring(0, i)
            }
        }
        return input
    }

    //	find args
    fun getArg(info: ExecutePack, input: String, type: Int): ArgInfo? {
        if (type == CommandAbstraction.FILE && info is MainPack) {
            val pack = info
            return file(input, pack.currentDirectory)
        } else if (type == CommandAbstraction.CONTACTNUMBER && info is MainPack) {
            val pack = info
            return contactNumber(input, pack.contacts)
        } else if (type == CommandAbstraction.PLAIN_TEXT || type == CommandAbstraction.THEME_PRESET || type == CommandAbstraction.PRESET_NAME || type == CommandAbstraction.SPACE_TARGET || type == CommandAbstraction.SPACE_RENAME_NAME) {
            return plainText(input)
        } else if (type == CommandAbstraction.VISIBLE_PACKAGE && info is MainPack) {
            val pack = info
            return activityName(input, pack.appsManager)
        } else if (type == CommandAbstraction.HIDDEN_PACKAGE && info is MainPack) {
            val pack = info
            return hiddenPackage(input, pack.appsManager)
        } else if (type == CommandAbstraction.TEXTLIST) {
            return textList(input)
        } else if (type == CommandAbstraction.SONG && info is MainPack) {
            val pack = info
            if (pack.player == null) return null

            return song(input, pack.player)
        } else if (type == CommandAbstraction.COMMAND) {
            return command(input, info.commandGroup)
        } else if (type == CommandAbstraction.BOOLEAN) {
            return bln(input)
        } else if (type == CommandAbstraction.COLOR) {
            return color(input)
        } else if (type == CommandAbstraction.CONFIG_ENTRY) {
            return configEntry(input)
        } else if (type == CommandAbstraction.CONFIG_FILE) {
            return configFile(input)
        } else if (type == CommandAbstraction.INT) {
            return integer(input)
        } else if (type == CommandAbstraction.DEFAULT_APP) {
            return defaultApp(input, (info as MainPack).appsManager)
        } else if (type == CommandAbstraction.ALL_PACKAGES) {
            return allPackages(input, (info as MainPack).appsManager)
        } else if (type == CommandAbstraction.NO_SPACE_STRING || type == CommandAbstraction.APP_GROUP || type == CommandAbstraction.BOUND_REPLY_APP) {
            return noSpaceString(input)
        } else if (type == CommandAbstraction.APP_INSIDE_GROUP) {
            return activityName(input, (info as MainPack).appsManager)
        } else if (type == CommandAbstraction.LONG) {
            return numberLong(input)
        } else if (type == CommandAbstraction.DATASTORE_PATH_TYPE) {
            return dataStoreType(input)
        }

        return null
    }

    //	args extractors {
    private fun dataStoreType(input: String?): ArgInfo? {
        val a = noSpaceString(input)
        if (a.found) {
            val s = a.arg as String?
            try {
                HTMLExtractManager.StoreableValue.Type.valueOf(s!!)
                return a
            } catch (e: Exception) {
                return null
            }
        }

        a.found = false
        return a
    }

    private fun numberLong(input: String): ArgInfo {
        val split: Array<String?> =
            input.split(Tuils.SPACE.toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()

        try {
            val l = split[0]!!.toLong()

            val builder = StringBuilder()
            for (c in 1..<split.size) {
                builder.append(split[c]).append(Tuils.SPACE)
            }
            return ArgInfo(l, builder.toString().trim { it <= ' ' }, true, 1)
        } catch (e: Exception) {
            return ArgInfo(null, input, false, 0)
        }
    }

    private fun color(input: String): ArgInfo {
        var input = input
        input = input.trim { it <= ' ' }

        val space = input.indexOf(Tuils.SPACE)
        val cl = input.substring(0, if (space == -1) input.length else space)
        input = if (space == -1) Tuils.EMPTYSTRING else input.substring(space + 1)

        try {
            Color.parseColor(cl)
            return ArgInfo(cl, input, true, 1)
        } catch (e: Exception) {
            return ArgInfo(null, input, false, 0)
        }
    }

    private fun bln(input: String): ArgInfo {
        val used: String?
        val notUsed: String?
        if (input.contains(Tuils.SPACE)) {
            val space = input.indexOf(Tuils.SPACE)
            used = input.substring(0, space)
            notUsed = input.substring(space + 1)
        } else {
            used = input
            notUsed = null
        }

        val result: Any = used.toBoolean()
        return ArgInfo(result, notUsed, used.length > 0, if (used.length > 0) 1 else 0)
    }

    private fun plainText(input: String?): ArgInfo {
        return ArgInfo(input, "", true, 1)
    }

    private fun textList(input: String?): ArgInfo? {
        if (input == null) {
            return null
        }

        val arg = Tuils.splitArgs(input)
        return ArgInfo(arg, null, true, arg.size)
    }

    private fun noSpaceString(input: String?): ArgInfo {
        if (input == null || input.isEmpty()) return ArgInfo(null, input, false, 0)

        var inDoubleQuote = false
        var inSingleQuote = false
        var escaped = false
        val arg = StringBuilder()

        var i = 0
        while (i < input.length) {
            val c = input.get(i)
            if (escaped) {
                arg.append(c)
                escaped = false
            } else if (c == '\\') {
                escaped = true
            } else if (c == '\"' && !inSingleQuote) {
                inDoubleQuote = !inDoubleQuote
            } else if (c == '\'' && !inDoubleQuote) {
                inSingleQuote = !inSingleQuote
            } else if (Character.isWhitespace(c) && !inDoubleQuote && !inSingleQuote) {
                break
            } else {
                arg.append(c)
            }
            i++
        }

        var residual: String? = null
        if (i < input.length) {
            residual = input.substring(i + 1).trim { it <= ' ' }
        }

        return ArgInfo(arg.toString(), residual, true, 1)
    }

    private fun command(string: String?, active: CommandGroup): ArgInfo {
        var string = string
        val i = noSpaceString(string)
        string = i.arg as String?

        var abstraction: CommandAbstraction? = null
        try {
            abstraction = active.getCommandByName(string)
        } catch (e: Exception) {
            Tuils.log(e)
            Tuils.toFile(e)
        }

        if (isHiddenCommandName(string)) {
            abstraction = null
        }

        return ArgInfo(abstraction, i.residualString, abstraction != null, 1)
    }

    private fun file(input: String, cd: File?): ArgInfo {
        var input = input
        input = input.trim { it <= ' ' }
        if ((input.startsWith("\"") || input.startsWith("'")) && (input.substring(1, input.length)
                .contains("\"") || input.substring(1, input.length).contains("'"))
        ) {
            val afterFirst = input.substring(1, input.length)

            var endIndex = afterFirst.indexOf("\"")
            if (endIndex == -1) endIndex = afterFirst.indexOf("'")

            if (endIndex != -1) {
                val file = afterFirst.substring(0, endIndex)
                val residual = afterFirst.substring(endIndex + 1, afterFirst.length)

                val f: File?
                if (afterFirst.startsWith("/"))  /*absolute*/ f = File(file)
                else f = File(cd, file)

                return ArgInfo(f, residual, true, 1)
            }
        }

        val strings = textList(input)!!.arg as MutableList<String?>

        var toVerify = Tuils.EMPTYSTRING
        var count = 0
        while (count < strings.size) {
            toVerify = toVerify + strings.get(count)

            val info = getFile(toVerify, cd)
            if (info.file != null && info.notFound == null) {
                while (count-- >= 0) strings.removeAt(0)

                val residual = Tuils.toPlanString(strings, Tuils.SPACE)
                return ArgInfo(info.file, residual, true, 1)
            }

            toVerify = toVerify + Tuils.SPACE
            count++
        }

        return ArgInfo(null, input, false, 0)
    }

    //    @SuppressWarnings("unchecked")
    //    private static ArgInfo fileList(String input, File cd) {
    //        List<File> files = new ArrayList<>();
    //        List<String> strings = (List<String>) CommandTuils.textList(input).arg;
    //
    //        String toVerify = Tuils.EMPTYSTRING;
    //        for (int count = 0; count < strings.size(); count++) {
    //            String s = strings.get(count);
    //
    //            toVerify = toVerify.concat(s);
    //
    //            DirInfo dir = CommandTuils.getFile(toVerify, cd);
    //            if (dir.notFound == null) {
    //                files.add(dir.file);
    //
    //                toVerify = Tuils.EMPTYSTRING;
    //                continue;
    //            }
    //
    //            List<File> tempFiles = CommandTuils.attemptWildcard(dir);
    //            if (tempFiles != null) {
    //                files.addAll(tempFiles);
    //
    //                toVerify = Tuils.EMPTYSTRING;
    //                continue;
    //            }
    //
    //            toVerify = toVerify.concat(Tuils.SPACE);
    //        }
    //
    //        if (toVerify.length() > 0) return new ArgInfo(null, null, false, 0);
    //        return new ArgInfo(files, null, files.size() > 0, files.size());
    //    }
    private fun getFile(path: String, cd: File?): DirInfo {
        return FileManager.cd(cd, path)
    }

    private fun attemptWildcard(dir: DirInfo): MutableList<File?>? {
        val files: MutableList<File?>?

        val info = FileManager.wildcard(dir.notFound)
        if (info == null) {
            return null
        }

        val cd = dir.file
        if (!cd.isDirectory()) {
            return null
        }

        if (info.allExtensions && info.allNames) {
            files = Arrays.asList<File?>(*cd.listFiles())
        } else if (info.allNames) {
            extensionFileFilter.setExtension(info.extension)
            files = Arrays.asList<File?>(*cd.listFiles(extensionFileFilter))
        } else if (info.allExtensions) {
            nameFileFilter.setName(info.name)
            files = Arrays.asList<File?>(*cd.listFiles(nameFileFilter))
        } else {
            return null
        }

        if (files.size > 0) {
            return files
        } else {
            return null
        }
    }

    private fun param(pack: MainPack, cmd: ParamCommand, input: String?): ArgInfo? {
        if (input == null || input.trim { it <= ' ' }.length == 0) return null

        var indexOfFirstSpace = input.indexOf(Tuils.SPACE)
        if (indexOfFirstSpace == -1) {
            indexOfFirstSpace = input.length
        }

        var param = input.substring(0, indexOfFirstSpace).trim { it <= ' ' }
        if (!param.startsWith("-")) param = "-" + param

        val sm: SimpleMutableEntry<Boolean, Param?> = cmd.getParam(pack, param)
        val p = sm.value
        val df: Boolean = sm.key

        return ArgInfo(
            p,
            if (df) input else input.substring(indexOfFirstSpace),
            p != null,
            if (p != null) 1 else 0
        )
    }

    private fun activityName(input: String?, apps: AppsManager): ArgInfo {
        val info = apps.findLaunchInfoWithLabel(input, AppsManager.SHOWN_APPS)
        return ArgInfo(info, null, info != null, if (info != null) 1 else 0)
    }

    private fun hiddenPackage(input: String?, apps: AppsManager): ArgInfo {
        val info = apps.findLaunchInfoWithLabel(input, AppsManager.HIDDEN_APPS)
        return ArgInfo(info, null, info != null, if (info != null) 1 else 0)
    }

    private fun allPackages(input: String?, apps: AppsManager): ArgInfo {
        var info = apps.findLaunchInfoWithLabel(input, AppsManager.SHOWN_APPS)
        if (info == null) {
            info = apps.findLaunchInfoWithLabel(input, AppsManager.HIDDEN_APPS)
        }

        return ArgInfo(info, null, info != null, if (info != null) 1 else 0)
    }

    private fun defaultApp(input: String?, apps: AppsManager): ArgInfo {
        val info = apps.findLaunchInfoWithLabel(input, AppsManager.SHOWN_APPS)
        if (info == null) {
            return ArgInfo(input, null, true, 1)
        } else {
            return ArgInfo(info, null, true, 1)
        }
    }

    private fun contactNumber(input: String?, contacts: ContactManager): ArgInfo {
        val number: String?

        if (Tuils.isPhoneNumber(input)) number = input
        else number = contacts.findNumber(input)

        return ArgInfo(number, null, number != null, 1)
    }

    private fun song(input: String?, music: MusicManager2?): ArgInfo {
        return ArgInfo(input, null, true, 1)
    }

    private fun configEntry(input: String): ArgInfo {
        val index = input.indexOf(Tuils.SPACE)

        if (xmlPrefsEntrys == null) {
            xmlPrefsEntrys = ArrayList<XMLPrefsSave>()

            for (element in XMLPrefsRoot.values()) {
                xmlPrefsEntrys!!.addAll(element.enums)
            }
            Collections.addAll<Apps>(xmlPrefsEntrys, *Apps.values())
            Collections.addAll<Notifications>(xmlPrefsEntrys, *Notifications.values())
            Collections.addAll<Rss>(xmlPrefsEntrys, *Rss.values())
        }

        val candidate = if (index == -1) input else input.substring(0, index)
        for (xs in xmlPrefsEntrys!!) {
            if (xs.label() == candidate) {
                return ArgInfo(
                    xs,
                    if (index == -1) null else input.substring(index + 1, input.length),
                    true,
                    1
                )
            }
        }
        return ArgInfo(null, input, false, 0)
    }

    private fun configFile(input: String?): ArgInfo {
        if (xmlPrefsFiles == null) {
            xmlPrefsFiles = ArrayList<String>()
            for (element in XMLPrefsRoot.values()) xmlPrefsFiles!!.add(
                element.path
            )
            xmlPrefsFiles!!.add(AppsManager.PATH)
            xmlPrefsFiles!!.add(NotificationManager.PATH)
            xmlPrefsFiles!!.add(RssManager.PATH)
        }

        for (xs in xmlPrefsFiles!!) {
            if (xs.equals(input, ignoreCase = true)) return ArgInfo(xs, null, true, 1)
        }
        return ArgInfo(null, input, false, 0)
    }

    private fun integer(input: String): ArgInfo {
        val n: Int
        val s: String?

        val index = input.indexOf(Tuils.SPACE)
        if (index == -1) s = input
        else s = input.substring(0, index)

        try {
            n = s.toInt()
        } catch (e: NumberFormatException) {
            return ArgInfo(null, input, false, 0)
        }

        return ArgInfo(n, if (index == -1) null else input.substring(index + 1), true, 1)
    }

    fun isSuRequest(input: String): Boolean {
        return input == "su"
    }

    fun isSuCommand(input: String): Boolean {
        return input.startsWith("su ")
    }

    class ArgInfo(var arg: Any?, var residualString: String?, var found: Boolean, var n: Int)
}
