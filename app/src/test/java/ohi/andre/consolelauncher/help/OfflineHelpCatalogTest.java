package ohi.andre.consolelauncher.help;

import org.junit.Test;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.regex.*;
import javax.xml.parsers.DocumentBuilderFactory;
import static org.junit.Assert.*;

public class OfflineHelpCatalogTest {
    @Test public void packagedManualHasTaskCoverageAndReadableResources() throws Exception {
        File main = new File("src/main");
        if (!main.isDirectory()) main = new File("app/src/main");
        String catalog = new String(Files.readAllBytes(new File(main,
                "java/ohi/andre/consolelauncher/help/OfflineHelpCatalog.kt").toPath()), StandardCharsets.UTF_8);
        Map<String, String> resources = new HashMap<>();
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        for (File file : Objects.requireNonNull(new File(main, "res/values").listFiles())) {
            if (!file.getName().endsWith(".xml")) continue;
            NodeList nodes = factory.newDocumentBuilder().parse(file).getElementsByTagName("string");
            for (int i = 0; i < nodes.getLength(); i++) {
                Element node = (Element) nodes.item(i);
                resources.put(node.getAttribute("name"), node.getTextContent());
            }
        }
        Matcher entries = Pattern.compile("HelpTopic\\(\"([^\"]+)\", R.string.(\\w+), R.string.(\\w+)(?:, \"([^\"]+)\")?\\)").matcher(catalog);
        Set<String> ids = new HashSet<>();
        List<String> roots = new ArrayList<>();
        Map<String, String> articleCategories = new HashMap<>();
        Map<String, String> bodies = new HashMap<>();
        while (entries.find()) {
            String id = entries.group(1);
            assertTrue("Duplicate topic " + id, ids.add(id));
            bodies.put(id, resources.get(entries.group(3)));
            for (int part = 2; part <= 3; part++) {
                String text = resources.get(entries.group(part));
                assertNotNull("Missing resource for " + id, text);
                assertFalse("Empty resource for " + id, text.replace("\"", "").trim().isEmpty());
            }
            if (entries.group(4) == null) roots.add(id);
            else articleCategories.put(id, entries.group(4));
        }
        assertEquals(Arrays.asList("getting-started", "apps", "commands", "appearance", "presets",
                "tools", "automation", "troubleshooting", "reference"), roots);
        assertEquals("getting-started", articleCategories.get("terminal-basics"));
        assertEquals("automation", articleCategories.get("termux-workspace"));
        assertEquals("presets", articleCategories.get("personal-backup"));
        assertEquals("tools", articleCategories.get("notes-workflow"));
        assertEquals("getting-started", articleCategories.get("new-features"));
        assertEquals("tools", articleCategories.get("irc-community"));
        assertEquals("tools", articleCategories.get("irc-accounts-privacy"));
        assertEquals("appearance", articleCategories.get("floating-tools"));
        assertEquals("appearance", articleCategories.get("idle-fade"));
        assertEquals("automation", articleCategories.get("termux-helper-updates"));
        assertEquals("reference", articleCategories.get("command-reference"));
        assertEquals(new HashSet<>(roots), new HashSet<>(articleCategories.values()));
        assertTrue(bodies.get("irc-community").contains("Search text in navigation views is never posted"));
        assertTrue(bodies.get("irc-community").contains("on networks that allow signup from apps"));
        assertTrue(bodies.get("irc-community").contains("not recipient delivery or a read receipt"));
        assertTrue(bodies.get("irc-community").contains("Add a network"));
        assertTrue(bodies.get("irc-accounts-privacy").contains("not a portable restore code"));
        assertTrue(bodies.get("irc-accounts-privacy").contains("not end-to-end encrypted"));
        assertTrue(bodies.get("irc-accounts-privacy").contains("nothing can use it"));
        assertTrue(bodies.get("floating-tools").contains("Calculator and Help stay expanded in landscape"));
        assertTrue(bodies.get("idle-fade").contains("breath_out_seconds"));
        assertTrue(bodies.get("termux-helper-updates").contains(":update"));
        assertTrue(bodies.get("termux-helper-updates").contains("SHA-256"));
        assertTrue(bodies.get("calculator-help").contains("Check the live hint"));
        assertTrue(bodies.get("calculator-help").contains("61 d to min"));
        assertTrue(bodies.get("colors-type-and-status").contains("alphabet rails"));
    }
}
