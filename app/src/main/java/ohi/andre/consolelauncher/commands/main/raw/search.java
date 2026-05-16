package ohi.andre.consolelauncher.commands.main.raw;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import ohi.andre.consolelauncher.R;
import ohi.andre.consolelauncher.commands.CommandAbstraction;
import ohi.andre.consolelauncher.commands.ExecutePack;
import ohi.andre.consolelauncher.commands.main.MainPack;
import ohi.andre.consolelauncher.commands.main.specific.ParamCommand;
import ohi.andre.consolelauncher.managers.SearchProviderManager;
import ohi.andre.consolelauncher.managers.xml.classes.XMLPrefsSave;
import ohi.andre.consolelauncher.managers.xml.options.Cmd;
import ohi.andre.consolelauncher.tuils.SimpleMutableEntry;
import ohi.andre.consolelauncher.tuils.Tuils;

public class search extends ParamCommand {

    private enum Param implements ohi.andre.consolelauncher.commands.main.Param {

        add {
            @Override
            public String exec(ExecutePack pack) {
                ArrayList<String> args = pack.getList();
                if (args == null || args.size() < 2) {
                    return usageAdd();
                }

                String name = args.get(0);
                if (isReserved(name)) {
                    return "Search param " + name + " is reserved.";
                }

                String template = args.get(1);
                String fallback = null;
                if (args.size() > 2) {
                    List<String> rest = args.subList(2, args.size());
                    if (!rest.isEmpty() && "=>".equals(rest.get(0))) {
                        rest = rest.subList(1, rest.size());
                    }
                    fallback = Tuils.toPlanString(rest, Tuils.SPACE);
                }

                boolean saved = SearchProviderManager.add(name, template, fallback);
                return saved
                        ? "Search provider -" + stripParam(name) + " saved."
                        : usageAdd();
            }

            @Override
            public int[] args() {
                return new int[] {CommandAbstraction.TEXTLIST};
            }
        },
        rm {
            @Override
            public String exec(ExecutePack pack) {
                ArrayList<String> args = pack.getList();
                if (args == null || args.isEmpty()) return "Usage: search -rm [param]";

                String name = args.get(0);
                if (isReserved(name)) {
                    return "Search param " + name + " is reserved.";
                }

                boolean removed = SearchProviderManager.remove(name);
                return removed
                        ? "Search provider -" + stripParam(name) + " removed."
                        : "Search provider -" + stripParam(name) + " not found.";
            }

            @Override
            public int[] args() {
                return new int[] {CommandAbstraction.TEXTLIST};
            }
        },
        ls {
            @Override
            public String exec(ExecutePack pack) {
                return listProviders();
            }

            @Override
            public int[] args() {
                return new int[0];
            }
        },
        file {
            @Override
            public String exec(ExecutePack pack) {
                File file = SearchProviderManager.file();
                pack.context.startActivity(Tuils.openFile(pack.context, file));
                return null;
            }

            @Override
            public int[] args() {
                return new int[0];
            }
        },
        reset {
            @Override
            public String exec(ExecutePack pack) {
                boolean reset = SearchProviderManager.reset();
                return reset ? "Search providers reset." : "Unable to reset search providers.";
            }

            @Override
            public int[] args() {
                return new int[0];
            }
        };

        static Param get(String p) {
            p = stripParam(p);
            if (p == null) return null;

            for (Param param : values()) {
                if (p.equals(param.name())) {
                    return param;
                }
            }

            return null;
        }

        static String[] labels() {
            Param[] ps = values();
            String[] labels = new String[ps.length];
            for (int i = 0; i < ps.length; i++) {
                labels[i] = ps[i].label();
            }
            return labels;
        }

        @Override
        public String label() {
            return Tuils.MINUS + name();
        }

        @Override
        public String onNotArgEnough(ExecutePack pack, int n) {
            return pack.context.getString(R.string.help_search);
        }

        @Override
        public String onArgNotFound(ExecutePack pack, int index) {
            return null;
        }
    }

    private static class SearchProviderParam implements ohi.andre.consolelauncher.commands.main.Param {
        private final SearchProviderManager.Provider provider;

        SearchProviderParam(SearchProviderManager.Provider provider) {
            this.provider = provider;
        }

        @Override
        public int[] args() {
            return new int[] {CommandAbstraction.TEXTLIST};
        }

        @Override
        public String exec(ExecutePack pack) {
            ArrayList<String> args = pack.getList();
            String query = args == null ? Tuils.EMPTYSTRING : Tuils.toPlanString(args, Tuils.SPACE);
            return open(provider, query, pack);
        }

        @Override
        public String label() {
            return Tuils.MINUS + provider.name;
        }

        @Override
        public String onNotArgEnough(ExecutePack pack, int n) {
            return pack.context.getString(R.string.help_search);
        }

        @Override
        public String onArgNotFound(ExecutePack pack, int index) {
            return null;
        }
    }

    @Override
    public SimpleMutableEntry<Boolean, ohi.andre.consolelauncher.commands.main.Param> getParam(MainPack pack, String param) {
        ohi.andre.consolelauncher.commands.main.Param managementParam = Param.get(param);
        if (managementParam != null) {
            return new SimpleMutableEntry<>(false, managementParam);
        }

        SearchProviderManager.Provider provider = SearchProviderManager.get(param);
        if (provider != null) {
            return new SimpleMutableEntry<>(false, new SearchProviderParam(provider));
        }

        return super.getParam(pack, param);
    }

    @Override
    protected ohi.andre.consolelauncher.commands.main.Param paramForString(MainPack pack, String param) {
        ohi.andre.consolelauncher.commands.main.Param managementParam = Param.get(param);
        if (managementParam != null) return managementParam;

        SearchProviderManager.Provider provider = SearchProviderManager.get(param);
        return provider == null ? null : new SearchProviderParam(provider);
    }

    @Override
    public XMLPrefsSave defaultParamReference() {
        return Cmd.default_search;
    }

    @Override
    protected String doThings(ExecutePack pack) {
        if (!(pack instanceof MainPack)) return null;

        String input = ((MainPack) pack).lastCommand;
        List<String> split = Tuils.splitArgs(input);
        if (split.size() < 2) {
            return pack.context.getString(R.string.help_search)
                    + Tuils.NEWLINE
                    + Tuils.NEWLINE
                    + listProviders();
        }

        return null;
    }

    @Override
    public String[] params() {
        return SearchProviderManager.labelsWith(Param.labels());
    }

    public static String playstoreSearch(String query, android.content.Context c) {
        SearchProviderManager.Provider provider = SearchProviderManager.get("ps");
        if (provider == null) {
            provider = new SearchProviderManager.Provider(
                    "ps",
                    "market://search?q={query}",
                    "https://play.google.com/store/search?q={query}&c=apps");
        }

        return open(provider, query, c);
    }

    private static String open(SearchProviderManager.Provider provider, String query, ExecutePack pack) {
        return open(provider, query, pack.context);
    }

    private static String open(SearchProviderManager.Provider provider, String query, Context context) {
        try {
            start(provider.render(query), context);
        } catch (Exception firstError) {
            String fallback = provider.renderFallback(query);
            if (fallback == null) {
                return firstError.toString();
            }

            try {
                start(fallback, context);
            } catch (Exception secondError) {
                return secondError.toString();
            }
        }

        return Tuils.EMPTYSTRING;
    }

    private static void start(String rendered, Context context) {
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(rendered));
        context.startActivity(intent);
    }

    private static String listProviders() {
        List<SearchProviderManager.Provider> providers = SearchProviderManager.load();
        if (providers.isEmpty()) return "No search providers configured.";

        StringBuilder output = new StringBuilder("Search providers:");
        for (SearchProviderManager.Provider provider : providers) {
            output.append(Tuils.NEWLINE)
                    .append(Tuils.MINUS)
                    .append(provider.name)
                    .append(" -> ")
                    .append(provider.template);
            if (provider.fallbackTemplate != null) {
                output.append(" => ").append(provider.fallbackTemplate);
            }
        }

        return output.toString();
    }

    private static String usageAdd() {
        return "Usage: search -add [param] [url_template]"
                + Tuils.NEWLINE
                + "Example: search -add sdw https://stardewvalleywiki.com/{slug}";
    }

    private static boolean isReserved(String param) {
        return Param.get(param) != null;
    }

    private static String stripParam(String param) {
        if (param == null) return null;

        param = param.trim().toLowerCase();
        while (param.startsWith(Tuils.MINUS)) {
            param = param.substring(1);
        }

        return param.length() == 0 ? null : param;
    }

    @Override
    public int helpRes() {
        return R.string.help_search;
    }

    @Override
    public int priority() {
        return 4;
    }
}
