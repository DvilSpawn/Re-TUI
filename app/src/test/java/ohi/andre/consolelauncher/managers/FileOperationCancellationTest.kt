package ohi.andre.consolelauncher.managers

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.Closeable
import java.io.OutputStream
import java.util.concurrent.CancellationException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.*
import org.junit.Test

class FileOperationCancellationTest {
    @Test fun cancellationBeforeStartPreventsWork() {
        val token = FileOperationCancellation.Token()
        assertTrue(token.requestCancellation())
        try {
            FileOperationCancellation.run(token) { fail("Cancelled work must not start") }
            fail("Cancellation should propagate")
        } catch (_: CancellationException) { }
    }

    @Test fun cancellationClosesBlockedReadAndPreservesCancellationCause() {
        val entered = CountDownLatch(1)
        val closed = CountDownLatch(1)
        val input = object : InputStream() {
            override fun read(): Int = throw AssertionError("Expected bulk read")
            override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                entered.countDown()
                check(closed.await(3, TimeUnit.SECONDS)) { "Cancellation did not close blocked input" }
                throw IOException("Closed provider stream")
            }
            override fun close() { closed.countDown() }
        }
        val token = FileOperationCancellation.Token()
        val failure = AtomicReference<Throwable>()
        val output = ByteArrayOutputStream()
        val worker = Thread {
            try { FileOperationCancellation.run(token) { FileOperationCancellation.copy(input, output) } }
            catch (error: Throwable) { failure.set(error) }
        }
        worker.start()
        assertTrue(entered.await(3, TimeUnit.SECONDS))
        assertTrue(token.requestCancellation())
        worker.join(3000)
        assertFalse(worker.isAlive)
        assertTrue(failure.get() is CancellationException)
        assertEquals(0, output.size())
    }

    @Test fun boundedCopyDoesNotCloseCallerStreamsOnSuccess() {
        var inputClosed = false
        val input = object : ByteArrayInputStream(byteArrayOf(1, 2, 3)) {
            override fun close() { inputClosed = true; super.close() }
        }
        val output = ByteArrayOutputStream()
        val token = FileOperationCancellation.Token()
        assertEquals(3L, FileOperationCancellation.run(token) { FileOperationCancellation.copy(input, output, 3) })
        assertFalse(inputClosed)
        assertArrayEquals(byteArrayOf(1, 2, 3), output.toByteArray())
        token.requestCancellation()
        assertFalse("Completed streams must be unregistered", inputClosed)
        try {
            FileOperationCancellation.copy(ByteArrayInputStream(byteArrayOf(1, 2, 3)), ByteArrayOutputStream(), 2)
            fail("Oversized input must be rejected")
        } catch (_: IOException) { }
    }

    @Test fun cancellationAlsoClosesBlockedProviderWrites() {
        val entered = CountDownLatch(1)
        val closed = CountDownLatch(1)
        val output = object : OutputStream() {
            override fun write(value: Int) = throw AssertionError("Expected bulk write")
            override fun write(buffer: ByteArray, offset: Int, length: Int) {
                entered.countDown()
                check(closed.await(3, TimeUnit.SECONDS)) { "Cancellation did not close blocked output" }
                throw IOException("Closed provider stream")
            }
            override fun close() { closed.countDown() }
        }
        val token = FileOperationCancellation.Token()
        val failure = AtomicReference<Throwable>()
        val worker = Thread {
            try { FileOperationCancellation.run(token) {
                FileOperationCancellation.copy(ByteArrayInputStream(byteArrayOf(1)), output)
            } } catch (error: Throwable) { failure.set(error) }
        }
        worker.start()
        assertTrue(entered.await(3, TimeUnit.SECONDS))
        assertTrue(token.requestCancellation())
        worker.join(3000)
        assertFalse(worker.isAlive)
        assertTrue(failure.get() is CancellationException)
    }

    @Test fun commitRejectsCancellationAndCallsHookOnce() {
        var commits = 0
        val token = FileOperationCancellation.Token { commits++ }
        FileOperationCancellation.run(token) {
            FileOperationCancellation.beginCommit()
            assertTrue(token.isCommitting())
            FileOperationCancellation.beginCommit()
            assertFalse(token.requestCancellation())
            FileOperationCancellation.check()
        }
        assertEquals(1, commits)
        val cancelled = FileOperationCancellation.Token()
        cancelled.requestCancellation()
        try { cancelled.beginCommit(); fail("Cancelled preparation cannot commit") }
        catch (_: CancellationException) { }
    }

    @Test fun completedOperationsRejectLateCancellation() {
        val token = FileOperationCancellation.Token()
        token.markCompleted()
        assertTrue(token.isCompleted())
        assertFalse(token.requestCancellation())
        token.check()
    }

    @Test fun slowProviderCloseDoesNotBlockCancellationCaller() {
        val closeStarted = CountDownLatch(1)
        val releaseClose = CountDownLatch(1)
        val cancellationReturned = CountDownLatch(1)
        val token = FileOperationCancellation.Token()
        val provider = Closeable {
            closeStarted.countDown()
            releaseClose.await(3, TimeUnit.SECONDS)
        }
        try {
            FileOperationCancellation.run(token) {
                FileOperationCancellation.track(provider) {
                    Thread { token.requestCancellation(); cancellationReturned.countDown() }.start()
                    assertTrue(closeStarted.await(3, TimeUnit.SECONDS))
                    assertTrue("Cancellation must return before provider close finishes",
                        cancellationReturned.await(1, TimeUnit.SECONDS))
                }
            }
        } catch (_: CancellationException) { }
        finally { releaseClose.countDown() }
    }

    @Test fun nestedOperationsRestoreTheirPreviousToken() {
        val outer = FileOperationCancellation.Token()
        val inner = FileOperationCancellation.Token()
        try {
            FileOperationCancellation.run(outer) {
                FileOperationCancellation.run(inner) {
                    outer.requestCancellation()
                    FileOperationCancellation.check()
                }
                FileOperationCancellation.check()
            }
            fail("Outer token must be restored")
        } catch (_: CancellationException) { }
        FileOperationCancellation.check()
    }
}
