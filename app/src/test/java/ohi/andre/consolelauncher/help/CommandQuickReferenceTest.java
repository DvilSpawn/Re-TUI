package ohi.andre.consolelauncher.help;

import org.junit.Test;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.xml.parsers.DocumentBuilderFactory;

import static org.junit.Assert.assertTrue;

/** Static contracts for the same resources used by runtime help and the offline reader. */
public class CommandQuickReferenceTest {
    @Test public void quickReferencesKeepSupportedSyntaxAndSafetyWarnings() throws Exception {
        File file = new File("src/main/res/values/strings.xml");
        if (!file.isFile()) file = new File("app/src/main/res/values/strings.xml");
        assertTrue("Run from the app module or repository root", file.isFile());
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        NodeList nodes = factory.newDocumentBuilder().parse(file).getElementsByTagName("string");
        Map<String, String> resources = new HashMap<>();
        for (int i = 0; i < nodes.getLength(); i++) {
            Element node = (Element) nodes.item(i);
            resources.put(node.getAttribute("name"), node.getTextContent());
        }
        List<String> failures = new ArrayList<>();
        forbidText(resources, failures, "help_config", "-setr");
        forbidText(resources, failures, "help_shortcut", "-cp");
        forbidText(resources, failures, "help_weather", "-set_key", "custom weather key", "- update");
        forbidText(resources, failures, "help_termux", "termux -setup", "termux -app-add");
        forbidText(resources, failures, "help_tbridge", "file_backend=termux");
        forbidText(resources, failures, "help_tuixt", "[textFile]");
        forbidText(resources, failures, "help_ls", "$ ls [folder]");
        forbidText(resources, failures, "help_donate", "PayPal");
        forbidText(resources, failures, "help_reminder", "31/08/26");
        forbidText(resources, failures, "help_rss", "-entry_tag [ID] ->", "-rm_format [ID] [format]");
        requireText(resources, failures, "help_config", "-set [option] [value]", "-ls", "-fontsize", "-apply", "move", "backup", "restart");
        requireText(resources, failures, "help_shortcut", "-ls", "-use", "@", "25");
        requireText(resources, failures, "help_weather", "-update", "-set_location", "met.no", "network");
        requireText(resources, failures, "help_termux", "-run", "-status", "-open", "-app", "-apps", "tmux save", "tbridge -setup");
        requireText(resources, failures, "help_tbridge", "-status", "-doctor", "-setup", "-probe", "Re:T-UI Files", "permission");
        requireText(resources, failures, "help_ls", "cd", "Re:T-UI Files", "ignored");
        requireText(resources, failures, "help_tuixt", "known", "config -file", "saving");
        requireText(resources, failures, "help_notifications", "-open", "launcher", "-clear", "dismiss", "access");
        requireText(resources, failures, "help_rss", "-entry_tag [ID] [tag]", "-rm_format [ID] ->", "-menu [ID]", "-time_format [ID]", "network", "commands");
        requireText(resources, failures, "help_regex", "-add", "-get [ID]", "-rm", "-test");
        requireText(resources, failures, "help_alias", "-add", "-rm", "-ls", "-file", "-tutorial", "external");
        requireText(resources, failures, "help_donate", "GitHub");
        requireText(resources, failures, "help_time", "time [index]", "zero", "output_time_format", "time_format_separator");
        requireText(resources, failures, "help_retuitoken", "-status", "-show", "-on", "-rotate", "-off", "clipboard", "credential", "history", "integrations");
        requireText(resources, failures, "help_tui", "-reset", "immediately", "deletes", "backup", "confirmation");
        requireText(resources, failures, "help_reminder", "-add", "dd/mm/yy", "HH:mm", "future", "-rm");
        requireText(resources, failures, "help_preset", "-save", "-apply", "-duplicate", "-ls", "-market", "-rm", "behavior", "ui.xml", "frame", "portable");
        assertTrue(String.join("\n", failures), failures.isEmpty());
    }

    @Test public void workspaceHelpDocumentsBothLocalAndHomeWindowSelection() throws Exception {
        File root = new File("src/main").isDirectory() ? new File(".") : new File("app");
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        NodeList nodes = factory.newDocumentBuilder().parse(
                new File(root, "src/main/res/values/strings_surfaces.xml")).getElementsByTagName("string");
        Map<String, String> resources = new HashMap<>();
        for (int i = 0; i < nodes.getLength(); i++) {
            Element node = (Element) nodes.item(i);
            resources.put(node.getAttribute("name"), node.getTextContent().replaceAll("^\"|\"$", ""));
        }
        org.json.JSONObject catalog = new org.json.JSONObject(new String(java.nio.file.Files.readAllBytes(
                new File(root, "src/main/assets/localization/catalog.json").toPath()),
                java.nio.charset.StandardCharsets.UTF_8));
        List<String> failures = new ArrayList<>();
        for (String key : new String[] {
                "surface_switch_window_jump_to_a_tmux_window_index_name_d002c",
                "surface_usage_tmux_switch_window_or_switch_window_82893" }) {
            requireText(resources, failures, key, ":switch <window>", ":prev", ":next", "adjacent", "tmux switch <window>", "Launcher Home");
            String fallback = catalog.getJSONObject(key).getJSONObject("forms").getString("other");
            assertTrue(key + " must match the English localization fallback", fallback.equals(resources.get(key)));
        }
        assertTrue(String.join("\n", failures), failures.isEmpty());
    }

    private static void requireText(Map<String, String> resources, List<String> failures,
                                    String key, String... terms) {
        checkText(resources, failures, key, true, terms);
    }

    private static void forbidText(Map<String, String> resources, List<String> failures,
                                   String key, String... terms) {
        checkText(resources, failures, key, false, terms);
    }

    private static void checkText(Map<String, String> resources, List<String> failures,
                                  String key, boolean required, String... terms) {
        String text = resources.get(key);
        if (text == null) {
            failures.add("Missing resource: " + key);
            return;
        }
        for (String term : terms) {
            if (text.toLowerCase(Locale.ROOT).contains(term.toLowerCase(Locale.ROOT)) != required) {
                failures.add(key + (required ? " must include " : " must not include ") + term);
            }
        }
    }
}
