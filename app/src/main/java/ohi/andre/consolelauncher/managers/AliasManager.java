package ohi.andre.consolelauncher.managers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import ohi.andre.consolelauncher.BuildConfig;
import ohi.andre.consolelauncher.R;
import ohi.andre.consolelauncher.managers.xml.XMLPrefsManager;
import ohi.andre.consolelauncher.managers.xml.options.Behavior;
import ohi.andre.consolelauncher.tuils.Tuils;

public class AliasManager {

    public static String ACTION_LS = BuildConfig.APPLICATION_ID + ".alias_ls";
    public static String ACTION_ADD = BuildConfig.APPLICATION_ID + ".alias_add";
    public static String ACTION_RM = BuildConfig.APPLICATION_ID + ".alias_rm";

    public static String NAME = "name";

    public static final String PATH = "alias.txt";
    public static final String SCOPE_APP = "a";
    public static final String SCOPE_SCRIPT = "s";
    public static final String SCOPE_DEFAULT = SCOPE_APP;
    private static final String SCOPE_SEPARATOR = "|";

    private List<Alias> aliases;
    private String paramSeparator, aliasLabelFormat;
    private boolean replaceAllMarkers;

    private Context context;

    private String paramMarker;
    private Pattern parameterPattern;

    private BroadcastReceiver receiver;

    public AliasManager(Context c) {
        this.context = c;

        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_ADD);
        filter.addAction(ACTION_LS);
        filter.addAction(ACTION_RM);

        receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();

                if(action.equals(ACTION_ADD)) {
                    add(context, intent.getStringExtra(NAME), intent.getStringExtra(XMLPrefsManager.VALUE_ATTRIBUTE));
                } else if(action.equals(ACTION_RM)) {
                    remove(context, intent.getStringExtra(NAME));
                } else if(action.equals(ACTION_LS)) {
                    Tuils.sendOutput(context, printAliases());
                }
            }
        };

        paramMarker = XMLPrefsManager.get(Behavior.alias_param_marker);
        parameterPattern = Pattern.compile(Pattern.quote(paramMarker));
        paramSeparator = XMLPrefsManager.get(Behavior.alias_param_separator);
        aliasLabelFormat = XMLPrefsManager.get(Behavior.alias_content_format);
        replaceAllMarkers = XMLPrefsManager.getBoolean(Behavior.alias_replace_all_markers);

        reload();
    }

    public String printAliases() {
        String output = Tuils.EMPTYSTRING;
        for (Alias a : aliases) {
            output = output.concat("[" + a.scope + "] " + a.name + " --> " + a.value + Tuils.NEWLINE);
        }

        return output.trim();
    }

//    [0] = aliasValue
//    [1] = aliasName
//    [2] = residualString
    public String[] getAlias(String alias, boolean supportSpaces) {
        return getAlias(alias, supportSpaces, SCOPE_DEFAULT);
    }

    public String[] getAlias(String alias, boolean supportSpaces, String scope) {
        if(supportSpaces) {
            String args = Tuils.EMPTYSTRING;

            String aliasValue = null;
            while (true) {
                aliasValue = getALias(alias, scope);
                if(aliasValue != null) break;
                else {
                    int index = alias.lastIndexOf(Tuils.SPACE);
                    if(index == -1) return new String[] {null, null, alias};

                    args = alias.substring(index + 1) + Tuils.SPACE + args;
                    args = args.trim();
                    alias = alias.substring(0,index);
                }
            }

            return new String[] {aliasValue, alias, args};
        } else {
            return new String[] {getALias(alias, scope), alias, Tuils.EMPTYSTRING};
        }
    }

//    this prevents some errors related to the % sign
    private final String SECURITY_REPLACEMENT = "{#@";
    private Pattern securityPattern = Pattern.compile(Pattern.quote(SECURITY_REPLACEMENT));

    public String format(String aliasValue, String params) {
        params = params.trim();
        if(params.length() == 0) return aliasValue;

        int before = aliasValue.length();
        aliasValue = parameterPattern.matcher(aliasValue).replaceAll(SECURITY_REPLACEMENT);
        int replaced = (aliasValue.length() - before) / Math.abs(SECURITY_REPLACEMENT.length() - paramMarker.length());

        String[] split = params.split(Pattern.quote(paramSeparator), replaced);

        for(String s : split) {
            aliasValue = securityPattern.matcher(aliasValue).replaceFirst(s);
        }

        if(replaceAllMarkers) aliasValue = securityPattern.matcher(aliasValue).replaceAll(split[0]);

        return aliasValue;
    }

    private String getALias(String name) {
        return getALias(name, SCOPE_DEFAULT);
    }

    private String getALias(String name, String scope) {
        scope = sanitizeScope(scope);
        for(Alias a : aliases) {
            if(name.equals(a.name) && scope.equals(a.scope)) return a.value;
        }

        return null;
    }

    private boolean removeAlias(String name) {
        boolean removed = false;
        for(int c = aliases.size() - 1; c >= 0; c--) {
            Alias a = aliases.get(c);
            if(name.equals(a.name)) {
                aliases.remove(c);
                removed = true;
            }
        }

        return removed;
    }

    private final Pattern pv = Pattern.compile("%v", Pattern.CASE_INSENSITIVE | Pattern.LITERAL);
    private final Pattern pa = Pattern.compile("%a", Pattern.CASE_INSENSITIVE | Pattern.LITERAL);
    public String formatLabel(String aliasName, String aliasValue) {
        String a = aliasLabelFormat;
        a = Tuils.patternNewline.matcher(a).replaceAll(Matcher.quoteReplacement(Tuils.NEWLINE));
        a = pv.matcher(a).replaceAll(Matcher.quoteReplacement(aliasValue));
        a = pa.matcher(a).replaceAll(Matcher.quoteReplacement(aliasName));
        return a;
    }

    public void reload() {
        if(aliases != null) aliases.clear();
        else aliases = new ArrayList<>();

        File root = Tuils.getFolder();
        if(root == null) return;

        File file = new File(root, PATH);

        try {
            if(!file.exists()) file.createNewFile();

            BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file)));

            String line;
            while((line = reader.readLine()) != null) {
                String[] splatted = line.split("=", 2);
                if(splatted.length < 2) continue;

                String name, value = Tuils.EMPTYSTRING;
                name = splatted[0];
                value = splatted[1];

                name = name.trim();
                value = value.trim();
                String scope = SCOPE_DEFAULT;
                int scopeIndex = name.lastIndexOf(SCOPE_SEPARATOR);
                if(scopeIndex > 0 && scopeIndex < name.length() - 1) {
                    scope = sanitizeScope(name.substring(scopeIndex + 1));
                    name = name.substring(0, scopeIndex).trim();
                }

                if(name.equalsIgnoreCase(value)) {
                    Tuils.sendOutput(Color.RED, context,
                            context.getString(R.string.output_notaddingalias1) + Tuils.SPACE + name + Tuils.SPACE + context.getString(R.string.output_notaddingalias2));
                } else if(value.startsWith(name + Tuils.SPACE)) {
                    Tuils.sendOutput(Color.RED, context,
                            context.getString(R.string.output_notaddingalias1) + Tuils.SPACE + name + Tuils.SPACE + context.getString(R.string.output_notaddingalias3));
                } else {
                    aliases.add(new Alias(name, value, scope, parameterPattern));
                }
            }
        } catch (Exception e) {
            Tuils.log(e);
        }
    }

    public void dispose() {
        LocalBroadcastManager.getInstance(context.getApplicationContext()).unregisterReceiver(receiver);
    }

    public void add(Context context, String name, String value) {
        add(context, name, value, SCOPE_DEFAULT);
    }

    public void add(Context context, String name, String value, String scope) {
        scope = sanitizeScope(scope);
        for(Alias a : aliases) {
            if(name.equals(a.name) && scope.equals(a.scope)) {
                Tuils.sendOutput(context, R.string.unavailable_name);
                return;
            }
        }

        FileOutputStream fos;
        try {
            fos = new FileOutputStream(new File(Tuils.getFolder(), PATH), true);
            fos.write((Tuils.NEWLINE + serializeName(name, scope) + "=" + value).getBytes());
            fos.close();

            aliases.add(new Alias(name, value, scope, parameterPattern));
        } catch (Exception e) {
            Tuils.sendOutput(context, e.toString());
        }

    }

    public void remove(Context context, String name) {
        reload();

        if(!removeAlias(name)) {
            Tuils.sendOutput(context, R.string.invalid_name);
            return;
        }

        try {
            File inputFile = new File(Tuils.getFolder(), PATH);
            File tempFile = new File(Tuils.getFolder(), PATH + "2");

            BufferedReader reader = new BufferedReader(new FileReader(inputFile));
            BufferedWriter writer = new BufferedWriter(new FileWriter(tempFile));

            String prefix = name + "=";
            String scopedPrefix = name + SCOPE_SEPARATOR;
            String line;
            while((line = reader.readLine()) != null) {
                if(line.startsWith(prefix)) continue;
                if(line.startsWith(scopedPrefix)) continue;
                writer.write(line + Tuils.NEWLINE);
            }
            writer.close();
            reader.close();

            tempFile.renameTo(inputFile);
        } catch (Exception e) {
            Tuils.sendOutput(context, e.toString());
        }
    }

    public List<Alias> getAliases(boolean excludeEmtpy) {
        return getAliases(excludeEmtpy, null);
    }

    public List<Alias> getAliases(boolean excludeEmtpy, String scope) {
        scope = scope == null ? null : sanitizeScope(scope);
        List<Alias> l = new ArrayList<>(aliases);
        if(scope != null) {
            for(int c = l.size() - 1; c >= 0; c--) {
                if(!scope.equals(l.get(c).scope)) {
                    l.remove(c);
                }
            }
        }

        if(excludeEmtpy) {
            for(int c = 0; c < l.size(); c++) {
                if(l.get(c).name.length() == 0) {
                    l.remove(c);
                    break;
                }
            }
        }

        return l;
    }

    private String sanitizeScope(String scope) {
        if(scope == null || scope.trim().length() == 0) {
            return SCOPE_DEFAULT;
        }

        scope = scope.trim().toLowerCase();
        if(scope.startsWith(Tuils.MINUS)) {
            scope = scope.substring(1);
        }

        if(SCOPE_SCRIPT.equals(scope)) {
            return SCOPE_SCRIPT;
        }

        return SCOPE_APP;
    }

    private String serializeName(String name, String scope) {
        scope = sanitizeScope(scope);
        if(SCOPE_DEFAULT.equals(scope)) {
            return name;
        }

        return name + SCOPE_SEPARATOR + scope;
    }

    public static class Alias {
        public String name, value, scope;
        public boolean isParametrized;

        public Alias(String name, String value, Pattern parameterPattern) {
            this(name, value, SCOPE_DEFAULT, parameterPattern);
        }

        public Alias(String name, String value, String scope, Pattern parameterPattern) {
            this.name = name;
            this.value = value;
            this.scope = scope == null ? SCOPE_DEFAULT : scope;

            isParametrized = parameterPattern.matcher(value).find();
        }

        @Override
        public boolean equals(Object obj) {
            return (obj instanceof Alias && ((Alias) obj).name.equals(name)) || obj.equals(name);
        }
    }
}
