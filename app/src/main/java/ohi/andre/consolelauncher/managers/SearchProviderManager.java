package ohi.andre.consolelauncher.managers;

import android.net.Uri;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.List;

import ohi.andre.consolelauncher.tuils.Tuils;

public class SearchProviderManager {

    public static final String PATH = "search.txt";
    private static final String ASSIGN = "=";
    private static final String FALLBACK = "=>";
    private static final String COMMENT = "#";

    private SearchProviderManager() {}

    public static Provider get(String name) {
        String normalized = normalizeName(name);
        if (normalized == null) return null;

        for (Provider provider : load()) {
            if (provider.name.equalsIgnoreCase(normalized)) {
                return provider;
            }
        }

        return null;
    }

    public static List<Provider> load() {
        ensureFile();
        List<Provider> providers = new ArrayList<>();

        try {
            BufferedReader reader = new BufferedReader(new FileReader(file()));
            String line;
            while ((line = reader.readLine()) != null) {
                Provider provider = parse(line);
                if (provider != null) providers.add(provider);
            }
            reader.close();
        } catch (Exception e) {
            Tuils.log(e);
        }

        return providers;
    }

    public static boolean add(String name, String template) {
        return add(name, template, null);
    }

    public static boolean add(String name, String template, String fallbackTemplate) {
        String normalized = normalizeName(name);
        if (normalized == null || !validTemplate(template)) {
            return false;
        }

        List<Provider> providers = load();
        for (int i = providers.size() - 1; i >= 0; i--) {
            if (providers.get(i).name.equalsIgnoreCase(normalized)) {
                providers.remove(i);
            }
        }

        providers.add(new Provider(normalized, template.trim(), clean(fallbackTemplate)));
        return write(providers);
    }

    public static boolean remove(String name) {
        String normalized = normalizeName(name);
        if (normalized == null) return false;

        List<Provider> providers = load();
        boolean removed = false;
        for (int i = providers.size() - 1; i >= 0; i--) {
            if (providers.get(i).name.equalsIgnoreCase(normalized)) {
                providers.remove(i);
                removed = true;
            }
        }

        return removed && write(providers);
    }

    public static boolean reset() {
        return write(defaultProviders());
    }

    public static File file() {
        return new File(Tuils.getFolder(), PATH);
    }

    public static String[] labelsWith(String[] baseLabels) {
        List<Provider> providers = load();
        String[] labels = new String[baseLabels.length + providers.size()];
        System.arraycopy(baseLabels, 0, labels, 0, baseLabels.length);

        for (int i = 0; i < providers.size(); i++) {
            labels[baseLabels.length + i] = Tuils.MINUS + providers.get(i).name;
        }

        return labels;
    }

    private static void ensureFile() {
        try {
            File file = file();
            if (!file.exists() || file.length() == 0) {
                write(defaultProviders());
            }
        } catch (Exception e) {
            Tuils.log(e);
        }
    }

    private static Provider parse(String rawLine) {
        if (rawLine == null) return null;

        String line = rawLine.trim();
        if (line.length() == 0 || line.startsWith(COMMENT)) return null;

        String[] pair = line.split(ASSIGN, 2);
        if (pair.length != 2) return null;

        String name = normalizeName(pair[0]);
        String value = pair[1].trim();
        String fallback = null;

        int fallbackIndex = value.indexOf(FALLBACK);
        if (fallbackIndex >= 0) {
            fallback = value.substring(fallbackIndex + FALLBACK.length()).trim();
            value = value.substring(0, fallbackIndex).trim();
        }

        if (name == null || !validTemplate(value)) {
            return null;
        }

        return new Provider(name, value, clean(fallback));
    }

    private static String normalizeName(String name) {
        if (name == null) return null;

        name = name.trim().toLowerCase();
        while (name.startsWith(Tuils.MINUS)) {
            name = name.substring(1);
        }

        if (name.length() == 0) return null;

        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            boolean valid = (c >= 'a' && c <= 'z')
                    || (c >= '0' && c <= '9')
                    || c == '_'
                    || c == '-';
            if (!valid) return null;
        }

        return name;
    }

    private static boolean validTemplate(String template) {
        template = clean(template);
        return template != null && containsToken(template);
    }

    private static boolean containsToken(String value) {
        return value.contains("{query}")
                || value.contains("{query+}")
                || value.contains("{slug}")
                || value.contains("{raw}")
                || value.contains("{url}");
    }

    private static String clean(String value) {
        if (value == null) return null;
        value = value.trim();
        return value.length() == 0 ? null : value;
    }

    private static boolean write(List<Provider> providers) {
        try {
            File file = file();
            BufferedWriter writer = new BufferedWriter(new FileWriter(file, false));
            writer.write("# Re:T-UI search providers");
            writer.newLine();
            writer.write("# Format: name=url_template");
            writer.newLine();
            writer.write("# Optional fallback: name=primary_template => fallback_template");
            writer.newLine();
            writer.write("# Tokens: {query}=URL encoded, {query+}=plus encoded, {slug}=spaces_to_underscores, {raw}=unchanged, {url}=open URL");
            writer.newLine();
            for (Provider provider : providers) {
                writer.write(provider.name);
                writer.write(ASSIGN);
                writer.write(provider.template);
                if (provider.fallbackTemplate != null) {
                    writer.write(" ");
                    writer.write(FALLBACK);
                    writer.write(" ");
                    writer.write(provider.fallbackTemplate);
                }
                writer.newLine();
            }
            writer.close();
            return true;
        } catch (Exception e) {
            Tuils.log(e);
            return false;
        }
    }

    private static List<Provider> defaultProviders() {
        List<Provider> providers = new ArrayList<>();
        providers.add(new Provider("gg", "https://www.google.com/search?q={query}", null));
        providers.add(new Provider("ps", "market://search?q={query}", "https://play.google.com/store/search?q={query}&c=apps"));
        providers.add(new Provider("yt", "https://www.youtube.com/results?search_query={query+}", null));
        providers.add(new Provider("dd", "https://duckduckgo.com/?q={query}", null));
        providers.add(new Provider("u", "{url}", null));
        return providers;
    }

    public static class Provider {
        public final String name;
        public final String template;
        public final String fallbackTemplate;

        public Provider(String name, String template, String fallbackTemplate) {
            this.name = name;
            this.template = template;
            this.fallbackTemplate = fallbackTemplate;
        }

        public String render(String query) {
            return renderTemplate(template, query);
        }

        public String renderFallback(String query) {
            if (fallbackTemplate == null) return null;
            return renderTemplate(fallbackTemplate, query);
        }

        private static String renderTemplate(String template, String query) {
            String raw = query == null ? Tuils.EMPTYSTRING : query.trim();
            String encoded = Uri.encode(raw);
            String plus = encoded.replace("%20", "+");
            String slug = Uri.encode(raw.replaceAll("\\s+", "_"));
            String url = raw;

            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                url = "https://" + url;
            }

            return template
                    .replace("{query+}", plus)
                    .replace("{query}", encoded)
                    .replace("{slug}", slug)
                    .replace("{raw}", raw)
                    .replace("{url}", url);
        }
    }
}
