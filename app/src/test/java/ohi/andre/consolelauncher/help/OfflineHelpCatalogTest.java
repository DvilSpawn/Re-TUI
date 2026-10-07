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
        while (entries.find()) {
            String id = entries.group(1);
            assertTrue("Duplicate topic " + id, ids.add(id));
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
        assertEquals("reference", articleCategories.get("command-reference"));
        assertEquals(new HashSet<>(roots), new HashSet<>(articleCategories.values()));
    }
}
