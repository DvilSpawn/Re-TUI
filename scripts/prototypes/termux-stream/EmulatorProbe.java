import com.termux.terminal.TerminalEmulator;
import com.termux.terminal.TerminalOutput;
import com.termux.terminal.TerminalSessionClient;
import com.termux.terminal.TextStyle;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/** Host-only probe: the real Termux emulator, without a local PTY or Android view. */
public final class EmulatorProbe {
    private static final class Output extends TerminalOutput {
        int replies;
        // tmux already answers terminal queries; forwarding these would answer twice.
        public void write(byte[] bytes, int offset, int count) { replies++; }
        public void titleChanged(String oldTitle, String newTitle) {}
        public void onCopyTextToClipboard(String text) {}
        public void onPasteTextFromClipboard() {}
        public void onBell() {}
        public void onColorsChanged() {}
    }

    private static final class Pane {
        final Output output = new Output();
        final TerminalEmulator emulator;
        Pane(int cols, int rows) {
            TerminalSessionClient client = (TerminalSessionClient) Proxy.newProxyInstance(
                TerminalSessionClient.class.getClassLoader(), new Class<?>[]{TerminalSessionClient.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("getTerminalCursorStyle")) return 0;
                    if (method.getName().startsWith("log")) System.err.println(method.getName());
                    return null;
                });
            emulator = new TerminalEmulator(output, cols, rows, 8, 16, 1000, client);
        }
    }

    public static void main(String[] args) throws Exception {
        Map<String, Pane> panes = new HashMap<>();
        BufferedReader input = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        String line;
        while ((line = input.readLine()) != null) {
            String[] fields = line.split(" ", 4);
            String command = fields[0];
            if (command.equals("QUIT")) return;
            String id = fields[1];
            if (command.equals("CREATE")) panes.put(id, new Pane(Integer.parseInt(fields[2]), Integer.parseInt(fields[3])));
            Pane pane = panes.get(id);
            if (pane == null) throw new IllegalArgumentException("Unknown pane");
            TerminalEmulator emulator = pane.emulator;
            if (command.equals("FEED")) {
                byte[] data = Base64.getDecoder().decode(fields[2]);
                emulator.append(data, data.length);
            } else if (command.equals("RESIZE")) {
                emulator.resize(Integer.parseInt(fields[2]), Integer.parseInt(fields[3]), 8, 16);
            } else if (command.equals("STYLE")) {
                long style = emulator.getScreen().getStyleAt(Integer.parseInt(fields[3]), Integer.parseInt(fields[2]));
                System.out.println("STYLE " + TextStyle.decodeForeColor(style) + " " + TextStyle.decodeBackColor(style));
                continue;
            }
            String text = emulator.getSelectedText(0, 0, emulator.mColumns - 1, emulator.mRows - 1);
            String encoded = Base64.getEncoder().encodeToString(text.getBytes(StandardCharsets.UTF_8));
            System.out.println("STATE " + emulator.mColumns + " " + emulator.mRows + " " + emulator.getCursorCol() + " "
                + emulator.getCursorRow() + " " + emulator.isAlternateBufferActive() + " " + emulator.isCursorEnabled()
                + " " + pane.output.replies + " " + encoded);
        }
    }
}
