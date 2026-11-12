@file:Suppress("DEPRECATION", "UNCHECKED_CAST")

package ohi.andre.consolelauncher.commands

import android.content.Context
import java.util.ArrayList
import ohi.andre.consolelauncher.managers.apps.AppsManager
import ohi.andre.consolelauncher.managers.xml.classes.XMLPrefsSave

@Suppress("UNCHECKED_CAST")
private fun <T> uninitialized(): T = null as T

abstract class ExecutePack(@JvmField var commandGroup: CommandGroup) {
    @JvmField var args: Array<Any?>? = null
    @JvmField var context: Context = uninitialized()
    @JvmField var currentIndex: Int = 0

    fun <T> get(c: Class<T>): T? = get() as T?

    fun <T> get(c: Class<T>, index: Int): T? {
        val currentArgs = args
        return if (currentArgs != null && index < currentArgs.size) currentArgs[index] as T? else null
    }

    fun get(): Any? {
        val currentArgs = args
        if (currentArgs != null && currentIndex < currentArgs.size) {
            return currentArgs[currentIndex++]
        }
        return null
    }

    fun getString(): String = get() as String? ?: ""

    fun getInt(): Int = get() as Int

    fun getBoolean(): Boolean = get() as Boolean

    fun <T> getList(): ArrayList<T> = get() as? ArrayList<T> ?: ArrayList()

    fun getPrefsSave(): XMLPrefsSave = get() as XMLPrefsSave

    fun getLaunchInfo(): AppsManager.LaunchInfo = get() as AppsManager.LaunchInfo

    fun set(args: Array<Any?>?) {
        this.args = args
    }

    open fun clear() {
        args = null
        currentIndex = 0
    }
}
