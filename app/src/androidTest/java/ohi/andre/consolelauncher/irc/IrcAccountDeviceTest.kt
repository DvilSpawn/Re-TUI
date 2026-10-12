package ohi.andre.consolelauncher.irc

import android.content.Intent
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import androidx.test.platform.app.InstrumentationRegistry
import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.help.WindowPreferencesRule
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.net.Socket
import java.security.KeyStore
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory
import okhttp3.OkHttpClient

/** Explicit opt-in: isolated real Ergo signup, or one disposable cert on an existing account.
 * Never sends channel messages, changes an account password or removes an existing key.
 * Credentials arrive in a private app file, not instrumentation arguments or logs.
 */
class IrcAccountDeviceTest {
    @get:Rule val windows = WindowPreferencesRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private fun main(block: () -> Unit) = instrumentation.runOnMainSync(block)
    private fun waitFor(condition: () -> Boolean) {
        val deadline = System.nanoTime() + 20_000_000_000L
        while (System.nanoTime() < deadline) {
            instrumentation.waitForIdleSync()
            if (condition()) return
            android.os.SystemClock.sleep(50)
        }
        assertTrue("Expected account state arrived (credentials withheld)", condition())
    }
    private fun start(): IrcActivity {
        val a = instrumentation.startActivitySync(Intent(instrumentation.targetContext, IrcActivity::class.java)
            .putExtra("irc_offline_fixture", true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as IrcActivity
        waitFor { var ready = false; main { ready = a.lifecycle.currentState == androidx.lifecycle.Lifecycle.State.RESUMED }; ready }
        return a
    }
    private fun find(view: View, text: String): View? {
        if (view is TextView && view.text.toString().removePrefix("> ") == text) return view
        if (view is ViewGroup) for (i in 0 until view.childCount) find(view.getChildAt(i), text)?.let { return it }
        return null
    }
    private fun click(a: IrcActivity, text: String) {
        var target = find(a.window.decorView, text)
        while (target != null && !target.isClickable) target = target.parent as? View
        assertNotNull("Account action exists", target); target!!.performClick()
    }
    private fun submit(a: IrcActivity, text: String) {
        a.findViewById<EditText>(R.id.irc_input).setText(text)
        a.findViewById<View>(R.id.irc_send).performClick()
    }
    @Test fun realErgoSignupEnrollsReopensAndRevokesOnlyItsNewKey() = exercise(true)
    @Test fun productionAccountEnrollsReopensAndRevokesOnlyItsNewKey() = exercise(false)
    @Test fun restoreRememberedBindingAfterInterruptedFixture() {
        val expected = InstrumentationRegistry.getArguments().getString("irc_restore_binding_sha256")
        assumeTrue(!expected.isNullOrEmpty())
        val context = instrumentation.targetContext
        val backup = File(context.getExternalFilesDir(null), "irc-original-binding.json")
        val bytes = JSONObject(backup.readText()).toString().toByteArray()
        val hash = java.security.MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it.toInt() and 255) }
        assertEquals("Recover only the exact pre-test metadata", expected, hash)
        val data = JSONObject(String(bytes))
        val identity = IrcDeviceKey.Identity(data.getString("host"), data.getInt("port"), data.getString("account"))
        assertTrue(KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.containsAlias(identity.alias))
        File(context.noBackupFilesDir, "irc-device.json").writeBytes(bytes)
        assertEquals(identity, IrcDeviceKey(context).remembered())
        backup.delete()
    }
    private fun exercise(signup: Boolean) {
        val args = InstrumentationRegistry.getArguments()
        assumeTrue(args.getString(if (signup) "irc_stage_gate" else "irc_account_gate") == "true")
        val context = instrumentation.targetContext
        val credentials = File(context.getExternalFilesDir(null), "irc-gate-credentials.json")
        assertTrue("Private credential handoff exists", credentials.isFile)
        val c = JSONObject(credentials.readText())
        val account = if (signup) "phone" + System.nanoTime().toString().takeLast(10) else c.getString("account")
        val password = c.getString("password")
        val host = "irc.kokodoko.cloud"
        // Spare port isolates the certificate alias from the owner's existing binding.
        // The connector below keeps system trust + real hostname validation intact.
        val identity = IrcDeviceKey.Identity(host, if (signup) 16697 else 16698, account)
        val record = File(context.noBackupFilesDir, "irc-device.json")
        val original = record.takeIf { it.exists() }?.readBytes()
        val durableBackup = File(context.noBackupFilesDir, "irc-gate-original.json")
        assertFalse("Recover any interrupted gate before starting another", durableBackup.exists())
        durableBackup.writeBytes(original ?: byteArrayOf())
        val devices = IrcDeviceKey(context)
        assertFalse("Never reuse an existing key", KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.containsAlias(identity.alias))
        var a = start()
        fun configure() {
            a.session.host = host; a.session.port = identity.port
            a.session.connector = { _, _ ->
                val keys = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
                val factory = if (keys.containsAlias(identity.alias)) devices.factory(identity) else SSLSocketFactory.getDefault() as SSLSocketFactory
                val raw = Socket(if (signup) "127.0.0.1" else host, if (signup) 16697 else 6697)
                try {
                    raw.soTimeout = 15_000
                    val tls = factory.createSocket(raw, host, if (signup) 16697 else 6697, true) as SSLSocket
                    tls.startHandshake()
                    assertTrue("Real hostname and system trust verified", OkHttpClient().hostnameVerifier.verify(host, tls.session))
                    tls.soTimeout = 180_000
                    tls
                } catch (error: Exception) { raw.close(); throw error }
            }
        }
        try {
            main {
                configure()
                if (!signup) { devices.ensure(identity); a.session.rememberDevice = true }
                click(a, if (signup) "Create account" else "Sign in")
                submit(a, account); submit(a, password)
            }
            waitFor { devices.remembered() == identity && a.session.state.active.joined }
            assertTrue("Real server membership confirmed", a.session.client?.ready == true)
            assertFalse("No password in scrollback", a.session.state.buffers.any { b -> b.lines.any { it.text.contains(password) } })
            main { a.finish() }; instrumentation.waitForIdleSync()
            a = start()
            main { configure(); a.session.reconnectDevice() }
            waitFor { a.session.client?.ready == true && a.session.state.active.joined }
            assertEquals(identity, devices.remembered())
            main { a.session.forgetDevice() }
            waitFor { devices.remembered() == null && a.session.client == null }
            assertEquals(context.getString(R.string.irc_device_revoked), a.session.deviceStatus)
            // The exact server revocation confirmation and local deletion are required.
            assertFalse(KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.containsAlias(identity.alias))
        } finally {
            // On a failed run do not silently claim that remote authorization was revoked.
            if (devices.remembered() == identity && a.session.client?.ready == true) {
                main { a.session.forgetDevice() }
                waitFor { a.session.client == null }
            }
            main { a.finish() }; instrumentation.waitForIdleSync()
            devices.forget(identity)
            if (original != null) record.writeBytes(original) else record.delete()
            durableBackup.delete()
            credentials.delete()
        }
    }
}
