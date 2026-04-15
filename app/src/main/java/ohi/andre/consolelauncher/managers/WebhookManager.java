package ohi.andre.consolelauncher.managers;

import android.content.Context;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import ohi.andre.consolelauncher.managers.xml.XMLPrefsManager;
import ohi.andre.consolelauncher.tuils.Tuils;

public class WebhookManager {

    private static final String FILE_NAME = "webhooks.xml";
    private static final String ROOT_NAME = "webhooks";
    private static final String WEBHOOK_TAG = "webhook";
    private static final String NAME_ATTRIBUTE = "name";
    private static final String URL_ATTRIBUTE = "url";
    private static final String BODY_ATTRIBUTE = "body";

    private List<Webhook> webhooks;
    private Context context;
    private File file;

    public WebhookManager(Context context) {
        this.context = context;
        this.file = new File(Tuils.getFolder(), FILE_NAME);
        reload();
    }

    public void reload() {
        webhooks = new ArrayList<>();
        try {
            Object[] o = XMLPrefsManager.buildDocument(file, ROOT_NAME);
            if (o == null) return;

            Document d = (Document) o[0];
            Element root = (Element) o[1];
            NodeList nodes = root.getElementsByTagName(WEBHOOK_TAG);

            for (int i = 0; i < nodes.getLength(); i++) {
                Node node = nodes.item(i);
                if (node instanceof Element) {
                    Element e = (Element) node;
                    String name = e.getAttribute(NAME_ATTRIBUTE);
                    String url = e.getAttribute(URL_ATTRIBUTE);
                    String body = e.getAttribute(BODY_ATTRIBUTE);
                    if (name != null && !name.isEmpty()) {
                        webhooks.add(new Webhook(name, url, body));
                    }
                }
            }
        } catch (Exception e) {
            Tuils.log(e);
        }
    }

    public Webhook getWebhook(String name) {
        for (Webhook w : webhooks) {
            if (w.name.equalsIgnoreCase(name)) return w;
        }
        return null;
    }

    public List<Webhook> getWebhooks() {
        return webhooks;
    }

    public void add(String name, String url, String body) {
        XMLPrefsManager.add(file, WEBHOOK_TAG, 
            new String[]{NAME_ATTRIBUTE, URL_ATTRIBUTE, BODY_ATTRIBUTE}, 
            new String[]{name, url, body});
        reload();
    }

    public void remove(String name) {
        XMLPrefsManager.removeNode(file, WEBHOOK_TAG, new String[]{NAME_ATTRIBUTE}, new String[]{name});
        reload();
    }

    public static class Webhook {
        public String name;
        public String url;
        public String bodyTemplate;

        public Webhook(String name, String url, String bodyTemplate) {
            this.name = name;
            this.url = url;
            this.bodyTemplate = bodyTemplate;
        }

        public String substitute(String[] args) {
            String result = bodyTemplate;
            for (int i = 0; i < args.length; i++) {
                result = result.replace("%" + (i + 1), args[i]);
            }
            return result;
        }
    }
}
