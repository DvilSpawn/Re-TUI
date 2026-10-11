/*
 * Copyright (C) 2012-2019 Jorrit "Chainfire" Jongma
 * Modified by Re:TUI: Kotlin port and Launcher integration.
 * Licensed under Apache-2.0; see LICENSES/Material-Symbols-Apache-2.0.txt.
 * http://www.apache.org/licenses/LICENSE-2.0
 * Distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND.
 */
package ohi.andre.consolelauncher.tuils.libsuperuser

import java.io.BufferedReader
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader

class StreamGobbler : Thread {
    fun interface OnLineListener {
        fun onLine(line: String)
    }

    private var shell: String? = null
    private var reader: BufferedReader? = null
    private var writer: MutableList<String>? = null
    private var listener: OnLineListener? = null

    constructor(shell: String, inputStream: InputStream, outputList: MutableList<String>?) {
        this.shell = shell
        reader = BufferedReader(InputStreamReader(inputStream))
        writer = outputList
    }

    constructor(shell: String, inputStream: InputStream, onLineListener: OnLineListener?) {
        this.shell = shell
        reader = BufferedReader(InputStreamReader(inputStream))
        listener = onLineListener
    }

    override fun run() {
        try {
            while (true) {
                val line = reader!!.readLine() ?: break
                writer?.add(line)
                listener?.onLine(line)
            }
        } catch (ignored: IOException) {
        }

        try {
            reader!!.close()
        } catch (ignored: IOException) {
        }
    }
}
