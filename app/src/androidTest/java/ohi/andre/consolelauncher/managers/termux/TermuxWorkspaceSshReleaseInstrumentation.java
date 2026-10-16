package ohi.andre.consolelauncher.managers.termux;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import ohi.andre.consolelauncher.UIManager;
import ohi.andre.consolelauncher.help.OfflineHelpActivity;
import java.util.UUID;
import android.os.Bundle;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.security.spec.ECGenParameterSpec;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.Mac;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import kotlin.Unit;

/** Exercises production entry points; does not preserve library APIs removed by R8. */
public final class TermuxWorkspaceSshReleaseInstrumentation extends Instrumentation {
    private Bundle arguments;
    private static final java.nio.charset.Charset UTF8 = java.nio.charset.StandardCharsets.UTF_8;
    private static void require(boolean value, String explanation) { if (!value) throw new AssertionError(explanation); }

    @Override public void onCreate(Bundle arguments) {
        super.onCreate(arguments);
        this.arguments = arguments == null ? new Bundle() : arguments;
        start();
    }

    private void platformCrypto() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec("secp256r1"));
        java.security.KeyPair alice = generator.generateKeyPair(), bob = generator.generateKeyPair();
        byte[] payload = "Re:TUI platform signature".getBytes(UTF8);
        Signature signature = Signature.getInstance("SHA256withECDSA");
        signature.initSign(alice.getPrivate()); signature.update(payload); byte[] signed = signature.sign();
        signature.initVerify(alice.getPublic()); signature.update(payload); require(signature.verify(signed), "P256 signature failed");
        KeyAgreement a = KeyAgreement.getInstance("ECDH"), b = KeyAgreement.getInstance("ECDH");
        a.init(alice.getPrivate()); a.doPhase(bob.getPublic(), true);
        b.init(bob.getPrivate()); b.doPhase(alice.getPublic(), true);
        require(Arrays.equals(a.generateSecret(), b.generateSecret()), "P256 key agreement failed");
        Cipher cipher = Cipher.getInstance("AES/CTR/NoPadding");
        SecretKeySpec key = new SecretKeySpec(new byte[16], "AES");
        IvParameterSpec iv = new IvParameterSpec(new byte[16]);
        cipher.init(Cipher.ENCRYPT_MODE, key, iv); byte[] encrypted = cipher.doFinal(payload);
        cipher.init(Cipher.DECRYPT_MODE, key, iv); require(Arrays.equals(payload, cipher.doFinal(encrypted)), "AES CTR round trip failed");
        Mac mac = Mac.getInstance("HmacSHA256"); mac.init(new SecretKeySpec(new byte[32], "HmacSHA256"));
        require(mac.doFinal(payload).length == 32, "HMAC SHA256 failed");
    }

    private void productionIdentityLoad() throws Exception {
        String[] args = TermuxWorkspaceSshSetup.INSTANCE.commandArguments(getTargetContext(), false);
        String[] publicParts = args[3].trim().split("\\s+");
        require(publicParts.length >= 2 && "ecdsa-sha2-nistp256".equals(publicParts[0]), "Production setup did not generate P256 identity");
        File identity = new File(getTargetContext().getNoBackupFilesDir(), "termux-workspace-ssh/identity");
        require(identity.isFile(), "Production private identity missing");
        int unusedPort;
        try (ServerSocket reservation = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))) { unusedPort = reservation.getLocalPort(); }
        CountDownLatch stopped = new CountDownLatch(1);
        AtomicReference<String> result = new AtomicReference<>();
        TermuxWorkspaceSshConnection connection = new TermuxWorkspaceSshConnection(
                new TermuxWorkspaceSshSetup.ConnectionInfo("u0_a23", unusedPort, publicParts[0] + " " + publicParts[1], identity),
                new TermuxWorkspaceSshConnection.Listener() {
                    @Override public void onReady(List windows) { result.set("Unexpected server connection"); stopped.countDown(); }
                    @Override public void onWindows(List windows) {}
                    @Override public void onTargetGone(String target) {}
                    @Override public void onError(String message) { result.set(message); stopped.countDown(); }
                });
        try {
            connection.connect();
            require(stopped.await(12, TimeUnit.SECONDS), "Production SSH identity initialization did not finish");
            require(result.get() != null && result.get().startsWith("Termux SSH did not start."),
                    "Production identity/host-key initialization failed: " + result.get());
        } finally { connection.close(); }
    }

    private void productionTerminal() {
        runOnMainSync(() -> {
            List<byte[]> replies = new ArrayList<>();
            TermuxWorkspaceTerminal terminal = new TermuxWorkspaceTerminal(80, 24, new TerminalGridView(getTargetContext()),
                    bytes -> { replies.add(bytes); return Unit.INSTANCE; });
            for (byte value : "héllo 世界".getBytes(UTF8)) terminal.append(new byte[] { value });
            require(terminal.text().contains("héllo 世界"), "Incremental UTF8 state lost");
            terminal.append("\u001b[6n".getBytes(UTF8));
            require(!replies.isEmpty() && new String(replies.get(replies.size() - 1), UTF8).endsWith("R"), "Terminal query reply missing");
            require(Arrays.equals("\u001b[A".getBytes(UTF8), terminal.key("Up")), "Normal cursor key incorrect");
            terminal.append("\u001b[?1h".getBytes(UTF8));
            require(Arrays.equals("\u001bOA".getBytes(UTF8), terminal.key("Up")), "Application cursor key incorrect");
            terminal.append("\u001b[?1049hEDITOR".getBytes(UTF8)); require(terminal.text().contains("EDITOR"), "Alternate screen missing");
            terminal.append("\u001b[?1049l".getBytes(UTF8)); require(terminal.text().contains("héllo 世界"), "Normal screen restoration failed");
        });
    }

    private String nativeCommand(String[] args) throws Exception {
        Context context = getTargetContext();
        String label = "retui-release-" + UUID.randomUUID();
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<Intent> result = new AtomicReference<>();
        BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override public void onReceive(Context ignored, Intent intent) {
                if (label.equals(intent.getStringExtra(UIManager.EXTRA_TERMUX_RESULT_PATH))) {
                    result.set(intent); done.countDown();
                }
            }
        };
        LocalBroadcastManager broadcasts = LocalBroadcastManager.getInstance(context);
        // This is the native result protocol defined in UIManager; its Companion is R8-internal.
        broadcasts.registerReceiver(receiver, new IntentFilter(context.getPackageName() + ".ui_termux_result"));
        try {
            TermuxBridgeManager.INSTANCE.startRunCommand(context, TermuxBridgeManager.TERMUX_SH,
                    TermuxBridgeManager.TERMUX_HOME,
                    TermuxBridgeManager.INSTANCE.createResultPendingIntent(context, label, null), args);
            require(done.await(20, TimeUnit.SECONDS), "Native command timed out");
            Intent response = result.get();
            require(response != null && response.getIntExtra(UIManager.EXTRA_TERMUX_RESULT_EXIT_CODE, -1) == 0,
                    "Native command failed: " + (response == null ? "missing result" : response.getStringExtra(UIManager.EXTRA_TERMUX_RESULT_STDERR)));
            String output = response.getStringExtra(UIManager.EXTRA_TERMUX_RESULT_STDOUT);
            return output == null ? "" : output;
        } finally { broadcasts.unregisterReceiver(receiver); }
    }

    /** Pure Java harness avoids depending on Kotlin helper methods legitimately removed from the app. */
    private void productionDeviceConnection() throws Exception {
        Context context = getTargetContext();
        Activity host = startActivitySync(new Intent(context, OfflineHelpActivity.class)
                .putExtra("topic", "termux-workspace").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        List<String> created = new ArrayList<>();
        AtomicReference<String> error = new AtomicReference<>();
        TermuxWorkspaceSshConnection connection = null;
        try {
            require(TermuxBridgeManager.INSTANCE.isTermuxInstalled(context), "Termux missing");
            require(TermuxBridgeManager.INSTANCE.hasRunCommandPermission(context), "Run-command permission missing");
            TermuxWorkspaceSshSetup.ConnectionInfo info = TermuxWorkspaceSshSetup.INSTANCE.parse(context,
                    nativeCommand(TermuxWorkspaceSshSetup.INSTANCE.commandArguments(context, false)));
            TermuxBridgeManager.INSTANCE.startRunCommand(context, TermuxBridgeManager.TERMUX_SH,
                    TermuxBridgeManager.TERMUX_HOME, null, TermuxWorkspaceSshSetup.INSTANCE.daemonArguments(context));
            CountDownLatch ready = new CountDownLatch(1);
            connection = new TermuxWorkspaceSshConnection(info, new TermuxWorkspaceSshConnection.Listener() {
                @Override public void onReady(List windows) { ready.countDown(); }
                @Override public void onWindows(List windows) {}
                @Override public void onTargetGone(String target) {}
                @Override public void onError(String message) { error.set(message); ready.countDown(); }
            });
            connection.connect();
            require(ready.await(20, TimeUnit.SECONDS) && connection.getConnected() && error.get() == null,
                    "Pinned SSH connection failed: " + error.get());
            for (int tile = 0; tile < 2; tile++) {
                CountDownLatch made = new CountDownLatch(1);
                AtomicReference<String> target = new AtomicReference<>();
                connection.newWindow("release-proof-" + UUID.randomUUID(), null, value -> {
                    target.set(value); made.countDown(); return Unit.INSTANCE;
                });
                require(made.await(10, TimeUnit.SECONDS), "New window timed out: " + error.get());
                require(target.get() != null && target.get().matches("@[0-9]+"), "Invalid created window");
                created.add(target.get());
                String suffix = "TILE_" + tile + "_" + UUID.randomUUID();
                String marker = "SSH_PROOF_" + suffix;
                StringBuilder seen = new StringBuilder();
                CountDownLatch output = new CountDownLatch(1);
                connection.attach(tile, target.get(), 60 + tile * 10, 20 + tile * 4, bytes -> {
                    synchronized (seen) {
                        seen.append(new String(bytes, UTF8));
                        if (seen.indexOf(marker) >= 0) output.countDown();
                    }
                    return Unit.INSTANCE;
                }, () -> Unit.INSTANCE);
                byte[] command = ("printf '%s%s\\n' 'SSH_PROOF_' '" + suffix + "'\r").getBytes(UTF8);
                long deadline = android.os.SystemClock.uptimeMillis() + 5000;
                while (!connection.write(tile, command) && android.os.SystemClock.uptimeMillis() < deadline) Thread.sleep(50);
                require(output.await(10, TimeUnit.SECONDS), "Tile did not stream: " + error.get());
            }
            connection.detach(0); connection.detach(1);
            String windows = nativeCommand(new String[]{"-c", "tmux list-windows -t '=retui_workspace' -F '#{window_id}'"});
            List<String> remaining = Arrays.asList(windows.trim().split("\\s+"));
            for (String target : created) require(remaining.contains(target), "Detach killed " + target);
        } finally {
            if (connection != null) connection.close();
            try {
                if (!created.isEmpty()) {
                    StringBuilder cleanup = new StringBuilder();
                    for (String target : created) {
                        require(target.matches("@[0-9]+"), "Unsafe fixture target");
                        cleanup.append("tmux kill-window -t '").append(target).append("' 2>/dev/null || true; ");
                    }
                    nativeCommand(new String[]{"-c", cleanup.toString()});
                }
            } finally { runOnMainSync(host::finish); }
        }
    }

    @Override public void onStart() {
        Bundle result = new Bundle(); int checks = 0;
        try {
            InstrumentationRegistry.registerInstance(this, arguments);
            platformCrypto(); checks++;
            productionIdentityLoad(); checks++;
            productionTerminal(); checks++;
            if ("true".equals(arguments.getString("termuxSshDevice"))) {
                productionDeviceConnection(); checks++;
            }
            result.putString("stream", "OK (" + checks + " minified SSH checks)\n");
            result.putInt("checksPassed", checks); finish(Activity.RESULT_OK, result);
        } catch (Throwable error) {
            StringWriter stack = new StringWriter(); error.printStackTrace(new PrintWriter(stack));
            result.putString("stream", "FAILED after " + checks + " checks\n" + stack);
            result.putInt("checksPassed", checks); finish(Activity.RESULT_CANCELED, result);
        }
    }
}
