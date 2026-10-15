package ohi.andre.consolelauncher.managers

import java.io.Closeable
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.Collections
import java.util.IdentityHashMap
import java.util.concurrent.CancellationException

/** Cancels preparation and provider I/O; live-file replacement must first enter commit. */
object FileOperationCancellation {
    class Token(private val onCommit: () -> Unit = {}) {
        private val lock = Any()
        private var cancelled = false
        private var committing = false
        private var completed = false
        private val streams = Collections.newSetFromMap(IdentityHashMap<Closeable, Boolean>())

        fun requestCancellation(): Boolean {
            val closing = synchronized(lock) {
                if (committing || completed) return false
                cancelled = true
                streams.toList().also { streams.clear() }
            }
            if (closing.isNotEmpty()) Thread({
                closing.forEach { runCatching { it.close() } }
            }, "file-operation-cancel").apply { isDaemon = true; start() }
            return true
        }

        fun isCommitting(): Boolean = synchronized(lock) { committing }

        fun isCompleted(): Boolean = synchronized(lock) { completed }

        fun markCompleted() = synchronized(lock) {
            check()
            completed = true
            streams.clear()
        }

        fun check() = synchronized(lock) {
            if (cancelled) throw CancellationException("File operation cancelled")
        }

        fun beginCommit() {
            synchronized(lock) {
                check()
                if (committing || completed) return
                committing = true
                streams.clear()
            }
            onCommit()
        }

        internal fun register(stream: Closeable): Boolean {
            synchronized(lock) {
                if (!cancelled) return !committing && !completed && streams.add(stream)
            }
            runCatching { stream.close() }
            check()
            return false
        }

        internal fun unregister(stream: Closeable) = synchronized(lock) { streams.remove(stream) }
    }

    private val current = ThreadLocal<Token?>()

    fun <T> run(token: Token, block: () -> T): T {
        val previous = current.get()
        current.set(token)
        return try { check(); block().also { check() } } finally {
            if (previous == null) current.remove() else current.set(previous)
        }
    }

    fun check() { current.get()?.check() }

    fun beginCommit() { current.get()?.beginCommit() }

    /** The caller retains normal close/use ownership; cancellation may close the resource early. */
    fun <C : Closeable, T> track(stream: C, block: (C) -> T): T {
        val token = current.get()
        val registered = token?.register(stream) == true
        return try {
            token?.check()
            block(stream).also { token?.check() }
        } catch (error: Exception) {
            token?.check()
            throw error
        } finally {
            if (registered) token?.unregister(stream)
        }
    }

    fun copy(input: InputStream, output: OutputStream, maxBytes: Long = Long.MAX_VALUE): Long =
        track(input) { source -> track(output) { destination ->
            require(maxBytes >= 0) { "Negative file size limit" }
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var total = 0L
            while (true) {
                check()
                val count = source.read(buffer)
                check()
                if (count < 0) break
                if (count.toLong() > maxBytes - total) throw IOException("File exceeds $maxBytes byte limit")
                destination.write(buffer, 0, count)
                total += count
            }
            check()
            total
        } }
}
