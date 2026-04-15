package ohi.andre.consolelauncher.managers;

import android.content.Context;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import ohi.andre.consolelauncher.managers.xml.XMLPrefsManager;
import ohi.andre.consolelauncher.tuils.Tuils;

public class HistoryManager {

    private static final String FILE_NAME = "history.xml";
    private static final String ROOT_NAME = "history";
    private static final String WEBHOOK_TAG = "webhook";
    private static final String ENTRY_TAG = "entry";
    private static final String NAME_ATTRIBUTE = "name";
    private static final String VALUE_ATTRIBUTE = "value";

    private static final int MAX_HISTORY = 5;

    private Map<String, List<String>> history;
    private File file;

    public HistoryManager() {
        this.file = new File(Tuils.getFolder(), FILE_NAME);
        reload();
    }

    public void reload() {
        history = new HashMap<>();
        try {
            Object[] o = XMLPrefsManager.buildDocument(file, ROOT_NAME);
            if (o == null) return;

            Element root = (Element) o[1];
            NodeList webhookNodes = root.getElementsByTagName(WEBHOOK_TAG);

            for (int i = 0; i < webhookNodes.getLength(); i++) {
                Node node = webhookNodes.item(i);
                if (node instanceof Element) {
                    Element webhookElement = (Element) node;
                    String name = webhookElement.getAttribute(NAME_ATTRIBUTE);
                    List<String> entries = new ArrayList<>();
                    
                    NodeList entryNodes = webhookElement.getElementsByTagName(ENTRY_TAG);
                    for (int j = 0; j < entryNodes.getLength(); j++) {
                        Node entryNode = entryNodes.item(j);
                        if (entryNode instanceof Element) {
                            entries.add(((Element) entryNode).getAttribute(VALUE_ATTRIBUTE));
                        }
                    }
                    history.put(name.toLowerCase(), entries);
                }
            }
        } catch (Exception e) {
            Tuils.log(e);
        }
    }

    public void add(String webhookName, String args) {
        webhookName = webhookName.toLowerCase();
        List<String> entries = history.get(webhookName);
        if (entries == null) {
            entries = new ArrayList<>();
            history.put(webhookName, entries);
        }

        // Remove if already exists to move to top
        entries.remove(args);
        entries.add(0, args);

        if (entries.size() > MAX_HISTORY) {
            entries.remove(entries.size() - 1);
        }

        save();
    }

    private void save() {
        try {
            Object[] o = XMLPrefsManager.buildDocument(file, ROOT_NAME);
            Document d = (Document) o[0];
            Element root = (Element) o[1];

            // Clear current content
            while (root.hasChildNodes()) {
                root.removeChild(root.getFirstChild());
            }

            for (Map.Entry<String, List<String>> entry : history.entrySet()) {
                Element webhookElement = d.createElement(WEBHOOK_TAG);
                webhookElement.setAttribute(NAME_ATTRIBUTE, entry.getKey());
                for (String value : entry.getValue()) {
                    Element entryElement = d.createElement(ENTRY_TAG);
                    entryElement.setAttribute(VALUE_ATTRIBUTE, value);
                    webhookElement.appendChild(entryElement);
                }
                root.appendChild(webhookElement);
            }

            XMLPrefsManager.writeTo(d, file);
        } catch (Exception e) {
            Tuils.log(e);
        }
    }

    public List<String> getHistory(String webhookName) {
        return history.get(webhookName.toLowerCase());
    }
}
