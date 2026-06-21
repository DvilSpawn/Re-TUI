package ohi.andre.consolelauncher.commands

interface CommandAbstraction {
    @Throws(Exception::class)
    fun exec(pack: ExecutePack): String?

    fun argType(): IntArray?
    fun priority(): Int
    fun helpRes(): Int
    fun onArgNotFound(pack: ExecutePack, indexNotFound: Int): String?
    fun onNotArgEnough(pack: ExecutePack, nArgs: Int): String?

    companion object {
        const val PLAIN_TEXT = 10
        const val FILE = 11
        const val VISIBLE_PACKAGE = 12
        const val CONTACTNUMBER = 13
        const val TEXTLIST = 14
        const val SONG = 15
        const val COMMAND = 17
        const val PARAM = 18
        const val BOOLEAN = 19
        const val HIDDEN_PACKAGE = 20
        const val COLOR = 21
        const val CONFIG_FILE = 22
        const val CONFIG_ENTRY = 23
        const val INT = 24
        const val DEFAULT_APP = 25
        const val ALL_PACKAGES = 26
        const val NO_SPACE_STRING = 27
        const val APP_GROUP = 28
        const val APP_INSIDE_GROUP = 29
        const val LONG = 30
        const val BOUND_REPLY_APP = 31
        const val DATASTORE_PATH_TYPE = 32
        const val THEME_PRESET = 33
        const val PRESET_NAME = 34
        const val SPACE_TARGET = 35
        const val SPACE_RENAME_NAME = 36
    }
}
