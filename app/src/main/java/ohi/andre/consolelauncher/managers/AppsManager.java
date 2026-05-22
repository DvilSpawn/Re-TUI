package ohi.andre.consolelauncher.managers;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.LauncherActivityInfo;
import android.content.pm.LauncherApps;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.content.pm.ShortcutInfo;
import android.graphics.Color;
import android.os.Build;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.Process;
import android.os.UserHandle;
import android.os.UserManager;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXParseException;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import it.andreuzzi.comparestring2.StringableObject;
import ohi.andre.consolelauncher.MainManager;
import ohi.andre.consolelauncher.R;
import ohi.andre.consolelauncher.UIManager;
import ohi.andre.consolelauncher.commands.main.MainPack;
import ohi.andre.consolelauncher.managers.xml.XMLPrefsManager;
import ohi.andre.consolelauncher.managers.xml.classes.XMLPrefsElement;
import ohi.andre.consolelauncher.managers.xml.classes.XMLPrefsList;
import ohi.andre.consolelauncher.managers.xml.classes.XMLPrefsSave;
import ohi.andre.consolelauncher.managers.xml.options.Apps;
import ohi.andre.consolelauncher.managers.xml.options.Behavior;
import ohi.andre.consolelauncher.managers.xml.options.Theme;
import ohi.andre.consolelauncher.managers.xml.options.Ui;
import ohi.andre.consolelauncher.tuils.StoppableThread;
import ohi.andre.consolelauncher.tuils.Tuils;

import static ohi.andre.consolelauncher.managers.xml.XMLPrefsManager.VALUE_ATTRIBUTE;
import static ohi.andre.consolelauncher.managers.xml.XMLPrefsManager.resetFile;
import static ohi.andre.consolelauncher.managers.xml.XMLPrefsManager.set;
import static ohi.andre.consolelauncher.managers.xml.XMLPrefsManager.writeTo;

public class AppsManager implements XMLPrefsElement {

    public static final int SHOWN_APPS = 10;
    public static final int HIDDEN_APPS = 11;

    public static final String PATH = "apps.xml";
    private final String NAME = "APPS";
    private File file;

    private final String SHOW_ATTRIBUTE = "show", APPS_ATTRIBUTE = "apps", BGCOLOR_ATTRIBUTE = "bgColor", FORECOLOR_ATTRIBUTE = "foreColor";
    private static final String APPS_SEPARATOR = ";";

    private Context context;

    private AppsHolder appsHolder;
    private List<LaunchInfo> hiddenApps;

    private final String PREFS = "apps";
    private static final String PROFILE_PREFIX = "profile";
    private SharedPreferences preferences;
    private SharedPreferences.Editor editor;

    public static XMLPrefsElement instance = null;

    private XMLPrefsList prefsList;

    public List<Group> groups;

    private Pattern pp, pl;
    private String appInstalledFormat, appUpdatedFormat, appUninstalledFormat;
    int appInstalledColor, appUninstalledColor;

    private String lastInstalledPackage;
    private long lastInstallTime;
    private String lastUpdatedPackage;
    private long lastUpdateTime;
    private String lastUninstalledPackage;
    private long lastUninstallTime;

    @Override
    public String[] delete() {
        return null;
    }

    @Override
    public void write(XMLPrefsSave save, String value) {
        set(new File(Tuils.getFolder(), PATH), save.label(), new String[] {VALUE_ATTRIBUTE}, new String[] {value});
        prefsList.add(save.label(), value);
    }

    @Override
    public String path() {
        return PATH;
    }

    @Override
    public XMLPrefsList getValues() {
        return prefsList;
    }

    private BroadcastReceiver appsBroadcast = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            String data = intent.getData().getSchemeSpecificPart();
            if (action.equals(Intent.ACTION_PACKAGE_ADDED)) {
                appInstalled(data, intent.getBooleanExtra(Intent.EXTRA_REPLACING, false));
            }
            else {
                appUninstalled(data, intent.getBooleanExtra(Intent.EXTRA_REPLACING, false));
            }
        }
    };

    public AppsManager(final Context context) {
        instance = this;

        this.context = context;

        appInstalledFormat = XMLPrefsManager.getBoolean(Ui.show_app_installed) ? XMLPrefsManager.get(Behavior.app_installed_format) : null;
        appUpdatedFormat = XMLPrefsManager.getBoolean(Ui.show_app_installed) ? XMLPrefsManager.get(Behavior.app_updated_format) : null;
        appUninstalledFormat = XMLPrefsManager.getBoolean(Ui.show_app_uninstalled) ? XMLPrefsManager.get(Behavior.app_uninstalled_format) : null;

        if(appInstalledFormat != null || appUninstalledFormat != null) {
            pp = Pattern.compile("%p", Pattern.CASE_INSENSITIVE);
            pl = Pattern.compile("%l", Pattern.CASE_INSENSITIVE);

            appInstalledColor = XMLPrefsManager.getColor(Theme.app_installed_color);
            appUninstalledColor = XMLPrefsManager.getColor(Theme.app_uninstalled_color);
        } else {
            pp = null;
            pl = null;
        }

        File root = Tuils.getFolder();
        if(root == null) this.file = null;
        else this.file = new File(root, PATH);

        this.preferences = context.getSharedPreferences(PREFS, 0);
        this.editor = preferences.edit();

        this.groups = new ArrayList<>();

        initAppListener(context);

        new StoppableThread() {
            @Override
            public void run() {
                super.run();

                fill();
                LocalBroadcastManager.getInstance(context.getApplicationContext()).sendBroadcast(new Intent(UIManager.ACTION_UPDATE_SUGGESTIONS));
            }
        }.start();
    }

    private void initAppListener(Context c) {
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(Intent.ACTION_PACKAGE_ADDED);
        intentFilter.addAction(Intent.ACTION_PACKAGE_REMOVED);
        intentFilter.addDataScheme("package");

        ContextCompat.registerReceiver(c, appsBroadcast, intentFilter, ContextCompat.RECEIVER_NOT_EXPORTED);
    }

    public void fill() {
        final List<LaunchInfo> allApps = createAppMap(context.getPackageManager());
        hiddenApps = new ArrayList<>();

        groups.clear();

        try {
            prefsList = new XMLPrefsList();

            if(file != null) {
                if(!file.exists()) {
                    resetFile(file, NAME);
                }

                Object[] o;
                try {
                    o = XMLPrefsManager.buildDocument(file, NAME);
                    if(o == null) {
                        Tuils.sendXMLParseError(context, PATH);
                        return;
                    }
                } catch (SAXParseException e) {
                    Tuils.sendXMLParseError(context, PATH, e);
                    return;
                } catch (Exception e) {
                    Tuils.log(e);
                    return;
                }

                Document d = (Document) o[0];
                Element root = (Element) o[1];

                List<Apps> enums = new ArrayList<>(Arrays.asList(Apps.values()));
                NodeList nodes = root.getElementsByTagName("*");

                for (int count = 0; count < nodes.getLength(); count++) {
                    final Node node = nodes.item(count);

                    String nn = node.getNodeName();
                    int nodeIndex = Tuils.find(nn, (List) enums);
                    if (nodeIndex != -1) {
//                        default_app...
                        if(nn.startsWith("d")) {
                            prefsList.add(nn, node.getAttributes().getNamedItem(VALUE_ATTRIBUTE).getNodeValue());
                        } else {
                            prefsList.add(nn, XMLPrefsManager.getStringAttribute((Element) node, VALUE_ATTRIBUTE));
                        }

                        for (int en = 0; en < enums.size(); en++) {
                            if (enums.get(en).label().equals(nn)) {
                                enums.remove(en);
                                break;
                            }
                        }
                    }
//                todo support delete
                    else {
                        if (node.getNodeType() == Node.ELEMENT_NODE) {
                            final Element e = (Element) node;

                            if(e.hasAttribute(APPS_ATTRIBUTE)) {
                                final String name = e.getNodeName();
                                if(name.contains(Tuils.SPACE)) {
                                    Tuils.sendOutput(Color.RED, context, PATH + ": " + context.getString(R.string.output_groupspace) + ": " + name);
                                    continue;
                                }

                                new StoppableThread() {
                                    @Override
                                    public void run() {
                                        super.run();

                                        Group g = new Group(name);

                                        String apps = e.getAttribute(APPS_ATTRIBUTE);
                                        String[] split = apps.split(APPS_SEPARATOR);

                                        List<LaunchInfo> as = new ArrayList<>(allApps);

                                        External:
                                        for(String s : split) {
                                            for(int c = 0; c < as.size(); c++) {
                                                if(as.get(c).equals(s)) {
                                                    g.add(as.remove(c), false);
                                                    continue External;
                                                }
                                            }
                                        }

                                        g.sort();

                                        if(e.hasAttribute(BGCOLOR_ATTRIBUTE)) {
                                            String c = e.getAttribute(BGCOLOR_ATTRIBUTE);
                                            if(c.length() > 0) {
                                                try {
                                                    g.setBgColor(Color.parseColor(c));
                                                } catch (Exception e) {
                                                    Tuils.sendOutput(Color.RED, context, PATH + ": " + context.getString(R.string.output_invalidcolor) + ": " + c);
                                                }
                                            }
                                        }

                                        if(e.hasAttribute(FORECOLOR_ATTRIBUTE)) {
                                            String c = e.getAttribute(FORECOLOR_ATTRIBUTE);
                                            if(c.length() > 0) {
                                                try {
                                                    g.setForeColor(Color.parseColor(c));
                                                } catch (Exception e) {
                                                    Tuils.sendOutput(Color.RED, context, PATH + ": " + context.getString(R.string.output_invalidcolor) + ": " + c);
                                                }
                                            }
                                        }

                                        groups.add(g);
                                    }
                                }.start();
                            } else {
                                boolean shown = !e.hasAttribute(SHOW_ATTRIBUTE) || Boolean.parseBoolean(e.getAttribute(SHOW_ATTRIBUTE));
                                if (!shown) {
                                    LaunchInfo.Identity identity = LaunchInfo.identityInfo(nn);
                                    if (identity == null) continue;

                                    LaunchInfo removed = AppUtils.findLaunchInfoWithIdentity(allApps, identity);
                                    if (removed != null) {
                                        allApps.remove(removed);
                                        hiddenApps.add(removed);
                                    }
                                }
                            }
                        }
                    }
                }

                if (enums.size() > 0) {
                    for (XMLPrefsSave s : enums) {
                        String value = s.defaultValue();

                        Element em = d.createElement(s.label());
                        em.setAttribute(VALUE_ATTRIBUTE, value);
                        root.appendChild(em);

                        prefsList.add(s.label(), value);
                    }
                    writeTo(d, file);
                }
            } else {
                Tuils.sendOutput(Color.RED, context, R.string.tuinotfound_app);
            }

            for (Map.Entry<String, ?> entry : this.preferences.getAll().entrySet()) {
                Object value = entry.getValue();
                if (value instanceof Integer) {
                    LaunchInfo.Identity identity = LaunchInfo.identityInfo(entry.getKey());
                    if (identity == null) continue;

                    LaunchInfo info = AppUtils.findLaunchInfoWithIdentity(allApps, identity);
                    if (info != null) info.launchedTimes = (Integer) value;
                }
            }

        } catch (Exception e1) {
            Tuils.toFile(e1);
        }

        appsHolder = new AppsHolder(allApps, prefsList);
        AppUtils.checkEquality(hiddenApps);

        Group.sorting = XMLPrefsManager.getInt(Apps.app_groups_sorting);
        for(Group g : groups) g.sort();
        Collections.sort(groups, (o1, o2) -> Tuils.alphabeticCompare(o1.name(), o2.name()));
    }

    private List<LaunchInfo> createAppMap(PackageManager mgr) {
        LinkedHashMap<String, LaunchInfo> deduped = new LinkedHashMap<>();
        LauncherApps launcherApps = null;
        UserManager userManager = (UserManager) context.getSystemService(Context.USER_SERVICE);
        UserHandle currentProfile = Process.myUserHandle();
        long currentSerial = profileSerial(userManager, currentProfile);
        boolean canReadShortcuts = false;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            try {
                launcherApps = (LauncherApps) context.getSystemService(Context.LAUNCHER_APPS_SERVICE);
                canReadShortcuts = launcherApps != null
                        && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1
                        && Tuils.isMyLauncherDefault(context.getPackageManager());

                List<UserHandle> profiles = userManager != null ? userManager.getUserProfiles() : null;
                if (profiles == null || profiles.size() == 0) {
                    profiles = new ArrayList<>();
                    profiles.add(currentProfile);
                }

                for (UserHandle profile : profiles) {
                    List<LauncherActivityInfo> activities = launcherApps != null
                            ? launcherApps.getActivityList(null, profile)
                            : null;
                    if (activities == null) {
                        continue;
                    }

                    boolean current = profile.equals(currentProfile);
                    long serial = profileSerial(userManager, profile);
                    for (LauncherActivityInfo activity : activities) {
                        ComponentName component = activity.getComponentName();
                        String label = activity.getLabel() != null ? activity.getLabel().toString() : component.getClassName();
                        LaunchInfo li = new LaunchInfo(component.getPackageName(), component.getClassName(), label, profile, serial, current);
                        maybeLoadShortcuts(launcherApps, li, canReadShortcuts);
                        deduped.put(li.write(), li);
                    }
                }
            } catch (Throwable e) {
                Tuils.log(e);
            }
        }

        Intent i = new Intent(Intent.ACTION_MAIN);
        i.addCategory(Intent.CATEGORY_LAUNCHER);

        List<ResolveInfo> main;
        try {
            main = mgr.queryIntentActivities(i, 0);
        } catch (Exception e) {
            return new ArrayList<>(deduped.values());
        }

        for (ResolveInfo ri : main) {
            LaunchInfo li = new LaunchInfo(ri.activityInfo.packageName, ri.activityInfo.name, ri.loadLabel(mgr).toString(), currentProfile, currentSerial, true);
            if (launcherApps != null) {
                maybeLoadShortcuts(launcherApps, li, canReadShortcuts);
            }
            deduped.put(li.write(), li);
        }

        Log.i("TUI-APPS", "Loaded " + deduped.size() + " launchable activities");

        return new ArrayList<>(deduped.values());
    }

    private long profileSerial(UserManager userManager, UserHandle profile) {
        if (userManager == null || profile == null) {
            return 0L;
        }
        try {
            long serial = userManager.getSerialNumberForUser(profile);
            if (serial >= 0L) {
                return serial;
            }
        } catch (Throwable e) {
        }
        return profile.equals(Process.myUserHandle()) ? 0L : Integer.toUnsignedLong(profile.hashCode());
    }

    private void maybeLoadShortcuts(LauncherApps launcherApps, LaunchInfo li, boolean canReadShortcuts) {
        if (!canReadShortcuts || launcherApps == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.N_MR1) {
            return;
        }

        try {
            LauncherApps.ShortcutQuery query = new LauncherApps.ShortcutQuery();
            query.setQueryFlags(LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST
                    | LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC
                    | LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED);
            query.setPackage(li.componentName.getPackageName());
            li.setShortcuts(launcherApps.getShortcuts(query, li.userHandle));
        } catch (SecurityException e) {
            Log.w("TUI-APPS", "Shortcut access denied for " + li.componentName.getPackageName());
        } catch (Throwable e) {
            Tuils.log(e);
        }
    }

    private boolean shouldSuppressPackageEvent(String packageName, String lastPackage, long lastTime) {
        return packageName.equals(lastPackage) && System.currentTimeMillis() - lastTime < 2000;
    }

    private String formatPackageEvent(String format, String packageName, PackageManager manager, PackageInfo packageInfo, List<LaunchInfo> infos) {
        String cp = format;

        cp = pp.matcher(cp).replaceAll(packageName);
        if(packageInfo != null) {
            CharSequence sequence = packageInfo.applicationInfo.loadLabel(manager);
            if(sequence != null) cp = pl.matcher(cp).replaceAll(sequence.toString());
        } else if(infos != null && infos.size() > 0) {
            cp = pl.matcher(cp).replaceAll(infos.get(0).publicLabel);
        } else {
            int index = packageName.lastIndexOf(Tuils.DOT);
            if(index == -1) cp = pl.matcher(cp).replaceAll(Tuils.EMPTYSTRING);
            else {
                cp = pl.matcher(cp).replaceAll(packageName.substring(index + 1));
            }
        }

        return Tuils.patternNewline.matcher(cp).replaceAll(Tuils.NEWLINE);
    }

    private void appInstalled(String packageName, boolean replacing) {
        try {
            PackageManager manager = context.getPackageManager();

            PackageInfo packageInfo = manager.getPackageInfo(packageName, 0);

            if(replacing) {
                if(appUpdatedFormat != null && !shouldSuppressPackageEvent(packageName, lastUpdatedPackage, lastUpdateTime)) {
                    Tuils.sendOutput(appInstalledColor, context, formatPackageEvent(appUpdatedFormat, packageName, manager, packageInfo, null));
                    lastUpdatedPackage = packageName;
                    lastUpdateTime = System.currentTimeMillis();
                }
            } else if(appInstalledFormat != null && !shouldSuppressPackageEvent(packageName, lastInstalledPackage, lastInstallTime)) {
                Tuils.sendOutput(appInstalledColor, context, formatPackageEvent(appInstalledFormat, packageName, manager, packageInfo, null));
                lastInstalledPackage = packageName;
                lastInstallTime = System.currentTimeMillis();
            }

            Intent i = manager.getLaunchIntentForPackage(packageName);
            if(i == null) return;

            ComponentName name = i.getComponent();
            String activity = name.getClassName();
            String label = manager.getActivityInfo(name, 0).loadLabel(manager).toString();

            UserManager userManager = (UserManager) context.getSystemService(Context.USER_SERVICE);
            UserHandle currentProfile = Process.myUserHandle();
            LaunchInfo app = new LaunchInfo(packageName, activity, label, currentProfile, profileSerial(userManager, currentProfile), true);
            appsHolder.add(app);
        } catch (Exception e) {}
    }

    private void appUninstalled(String packageName, boolean replacing) {
        if(appsHolder == null || context == null) return;

        if (replacing || shouldSuppressPackageEvent(packageName, lastUninstalledPackage, lastUninstallTime)) {
            return;
        }
        lastUninstalledPackage = packageName;
        lastUninstallTime = System.currentTimeMillis();

        List<LaunchInfo> infos = AppUtils.findLaunchInfosWithPackage(packageName, appsHolder.getApps());

        if(appUninstalledFormat != null) {
            Tuils.sendOutput(appUninstalledColor, context, formatPackageEvent(appUninstalledFormat, packageName, context.getPackageManager(), null, infos));
        }

        for(LaunchInfo i : infos) appsHolder.remove(i);

    }

    public LaunchInfo findLaunchInfoWithLabel(String label, int type) {
        if(appsHolder == null) return null;

        List<LaunchInfo> appList;
        if(type == SHOWN_APPS) {
            appList = appsHolder.getApps();
        } else {
            appList = hiddenApps;
        }

        if(appList == null) return null;

        LaunchInfo i = AppUtils.findLaunchInfoWithLabel(appList, label);
        if(i != null) {
            return i;
        }

        List<LaunchInfo> is = AppUtils.findLaunchInfosWithPackage(label, appList);
        if(is == null || is.size() == 0) return null;
        return is.get(0);
    }

    public void writeLaunchTimes(LaunchInfo info) {
        editor.putInt(info.write(), info.launchedTimes);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.GINGERBREAD) {
            editor.apply();
        } else {
            editor.commit();
        }

        if(appsHolder != null) appsHolder.update(true);
    }

    public boolean launch(Context launchContext, final LaunchInfo info) {
        if (launchContext == null || info == null) {
            return false;
        }

        info.launchedTimes++;
        new StoppableThread() {
            @Override
            public void run() {
                super.run();

                if (appsHolder != null) appsHolder.requestSuggestionUpdate(info);
                writeLaunchTimes(info);
            }
        }.start();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && !info.currentProfile) {
            try {
                LauncherApps launcherApps = (LauncherApps) launchContext.getSystemService(Context.LAUNCHER_APPS_SERVICE);
                if (launcherApps != null) {
                    launcherApps.startMainActivity(info.componentName, info.userHandle, null, null);
                    return true;
                }
            } catch (Throwable e) {
                Tuils.log(e);
                return false;
            }
        }

        Intent intent = getIntent(info);
        if (intent == null) {
            return false;
        }
        launchContext.startActivity(intent);
        return true;
    }

    public Intent getIntent(final LaunchInfo info) {
        if (info == null) {
            return null;
        }

        return new Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LAUNCHER)
                .setComponent(info.componentName)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
    }

    public String hideActivity(LaunchInfo info) {
        set(file, info.write(), new String[] {SHOW_ATTRIBUTE}, new String[] {false + Tuils.EMPTYSTRING});

        appsHolder.remove(info);
        appsHolder.update(true);
        hiddenApps.add(info);
        AppUtils.checkEquality(hiddenApps);

        return info.publicLabel;
    }

    public String showActivity(LaunchInfo info) {
        set(file, info.write(), new String[]{SHOW_ATTRIBUTE}, new String[]{true + Tuils.EMPTYSTRING});

        hiddenApps.remove(info);
        appsHolder.add(info);
        appsHolder.update(false);

        return info.publicLabel;
    }

    public String createGroup(String name) {
        int index = Tuils.find(name, groups);
        if(index == -1) {
            groups.add(new Group(name));
            return XMLPrefsManager.set(file, name, new String[]{APPS_ATTRIBUTE}, new String[]{Tuils.EMPTYSTRING});
        }

        return context.getString(R.string.output_groupexists);
    }

    public String groupBgColor(String name, String color) {
        int index = Tuils.find(name, groups);
        if(index == -1) {
            return context.getString(R.string.output_groupnotfound);
        }

        groups.get(index).setBgColor(Color.parseColor(color));
        return XMLPrefsManager.set(file, name, new String[]{BGCOLOR_ATTRIBUTE}, new String[]{color});
    }

    public String groupForeColor(String name, String color) {
        int index = Tuils.find(name, groups);
        if(index == -1) {
            return context.getString(R.string.output_groupnotfound);
        }

        groups.get(index).setForeColor(Color.parseColor(color));
        return XMLPrefsManager.set(file, name, new String[]{FORECOLOR_ATTRIBUTE}, new String[]{color});
    }

    public String removeGroup(String name) {
        String output = XMLPrefsManager.removeNode(file, name);

        if(output == null) return null;
        if(output.length() == 0) return context.getString(R.string.output_groupnotfound);

        int index = Tuils.find(name, groups);
        if(index != -1) groups.remove(index);

        return output;
    }

    public String addAppToGroup(String group, LaunchInfo app) {
        Object[] o;
        try {
            o = XMLPrefsManager.buildDocument(file, null);
            if(o == null) {
                Tuils.sendXMLParseError(context, PATH);
                return null;
            }
        } catch (Exception e) {
            return e.toString();
        }

        Document d = (Document) o[0];
        Element root = (Element) o[1];

        Node node = XMLPrefsManager.findNode(root, group);
        if(node == null) return context.getString(R.string.output_groupnotfound);

        Element e = (Element) node;
        String apps = e.getAttribute(APPS_ATTRIBUTE);

        if(apps != null && app.isInside(apps)) return null;

        apps = apps + APPS_SEPARATOR + app.write();
        if(apps.startsWith(APPS_SEPARATOR)) apps = apps.substring(1);

        e.setAttribute(APPS_ATTRIBUTE, apps);

        XMLPrefsManager.writeTo(d, file);

        int index = Tuils.find(group, groups);
        if(index != -1) groups.get(index).add(app, true);

        return null;
    }

    public String removeAppFromGroup(String group, LaunchInfo app) {
        Object[] o;
        try {
            o = XMLPrefsManager.buildDocument(file, null);
            if(o == null) {
                Tuils.sendXMLParseError(context, PATH);
                return null;
            }
        } catch (Exception e) {
            return e.toString();
        }

        Document d = (Document) o[0];
        Element root = (Element) o[1];

        Node node = XMLPrefsManager.findNode(root, group);
        if(node == null) return context.getString(R.string.output_groupnotfound);

        Element e = (Element) node;

        String apps = e.getAttribute(APPS_ATTRIBUTE);
        if(apps == null) return null;

        if(!app.isInside(apps)) return null;

        String temp = apps.replace(app.write(), Tuils.EMPTYSTRING);
        if(temp.length() < apps.length()) {
            apps = temp;
            apps = apps.replaceAll(APPS_SEPARATOR + APPS_SEPARATOR, APPS_SEPARATOR);
            if(apps.startsWith(APPS_SEPARATOR)) apps = apps.substring(1);
            if(apps.endsWith(APPS_SEPARATOR)) apps = apps.substring(0, apps.length() - 1);

            e.setAttribute(APPS_ATTRIBUTE, apps);

            XMLPrefsManager.writeTo(d, file);

            int index = Tuils.find(group, groups);
            if(index != -1) groups.get(index).remove(app);
        }

        return null;
    }

    public String listGroup(String group) {
        Object[] o;
        try {
            o = XMLPrefsManager.buildDocument(file, null);
            if(o == null) {
                Tuils.sendXMLParseError(context, PATH);
                return null;
            }
        } catch (Exception e) {
            return e.toString();
        }

        Element root = (Element) o[1];

        Node node = XMLPrefsManager.findNode(root, group);
        if(node == null) return context.getString(R.string.output_groupnotfound);

        Element e = (Element) node;

        String apps = e.getAttribute(APPS_ATTRIBUTE);
        if(apps == null) return "[]";

        String labels = Tuils.EMPTYSTRING;

        String[] split = apps.split(APPS_SEPARATOR);
        for(String s : split) {
            if(s.length() == 0) continue;

            LaunchInfo.Identity identity = LaunchInfo.identityInfo(s);
            LaunchInfo info = AppUtils.findLaunchInfoWithIdentity(appsHolder.getApps(), identity);
            if (info == null) {
                info = AppUtils.findLaunchInfoWithIdentity(hiddenApps, identity);
            }
            if (info == null) continue;

            labels = labels + Tuils.NEWLINE + info.publicLabel;
        }

        return labels.trim();
    }

    public String listGroups() {
        Object[] o;
        try {
            o = XMLPrefsManager.buildDocument(file, null);
            if(o == null) {
                Tuils.sendXMLParseError(context, PATH);
                return null;
            }
        } catch (Exception e) {
            return e.toString();
        }

        Element root = (Element) o[1];

        String groups = Tuils.EMPTYSTRING;

        NodeList list = root.getElementsByTagName("*");
        for(int count = 0; count < list.getLength(); count++) {
            Node node = list.item(count);
            if(! (node instanceof Element)) continue;

            Element e = (Element) node;
            if(!e.hasAttribute(APPS_ATTRIBUTE)) continue;

            groups = groups + Tuils.NEWLINE + e.getNodeName();
        }

        if(groups.length() == 0) return "[]";
        return groups.trim();
    }

    public List<LaunchInfo> shownApps() {
        if(appsHolder == null) return new ArrayList<>();
        return appsHolder.getApps();
    }

    public List<LaunchInfo> hiddenApps() {
        return hiddenApps;
    }

    public LaunchInfo[] getSuggestedApps() {
        if(appsHolder == null) return new LaunchInfo[0];
        return appsHolder.getSuggestedApps();
    }

    public String printApps(int type) {
        return printNApps(type, -1);
    }

    public String printApps(int type, String text) {
        boolean ok;
        int length = 0;
        try {
            length = Integer.parseInt(text);
            ok = true;
        } catch (NumberFormatException exc) {
            ok = false;
        }

        if(ok) {
            return printNApps(type, length);
        } else {
            return printAppsThatBegins(type, text);
        }
    }

    private String printNApps(int type, int n) {
        try {
            List<String> labels = AppUtils.labelList(type == SHOWN_APPS ? appsHolder.getApps() : hiddenApps, true);

            if(n >= 0) {
                int toRemove = labels.size() - n;
                if(toRemove <= 0) return "[]";

                for(int c = 0; c < toRemove; c++) {
                    labels.remove(labels.size() - 1);
                }
            }

            return AppUtils.printApps(labels);
        } catch (NullPointerException e) {
            return "[]";
        }
    }

    private String printAppsThatBegins(int type, String with) {
        try {
            List<String> labels = AppUtils.labelList(type == SHOWN_APPS ? appsHolder.getApps() : hiddenApps, true);

            if(with != null && with.length() > 0) {
                with = with.toLowerCase();

                Iterator<String> it = labels.iterator();
                while(it.hasNext()) {
                    if(!it.next().toLowerCase().startsWith(with)) it.remove();
                }
            }

            return AppUtils.printApps(labels);
        } catch (NullPointerException e) {
            return "[]";
        }
    }

    public void unregisterReceiver(Context context) {
        context.unregisterReceiver(appsBroadcast);
    }

    public void onDestroy() {
        unregisterReceiver(context);
    }

    public static class Group implements MainManager.Group, StringableObject {

        public static final int ALPHABETIC_UP_DOWN = 0;
        public static final int ALPHABETIC_DOWN_UP = 1;
        public static final int TIME_UP_DOWN = 2;
        public static final int TIME_DOWN_UP = 3;
        public static final int MOSTUSED_UP_DOWN = 4;
        public static final int MOSTUSED_DOWN_UP = 5;

        public static int sorting;

        public static Comparator<GroupLaunchInfo> comparator = new Comparator<GroupLaunchInfo>() {
            @Override
            public int compare(GroupLaunchInfo o1, GroupLaunchInfo o2) {
                switch (sorting) {
                    case ALPHABETIC_UP_DOWN:
                        return Tuils.alphabeticCompare(o1.publicLabel, o2.publicLabel);
                    case ALPHABETIC_DOWN_UP:
                        return Tuils.alphabeticCompare(o2.publicLabel, o1.publicLabel);
                    case TIME_UP_DOWN:
                        return o1.initialIndex - o2.initialIndex;
                    case TIME_DOWN_UP:
                        return o2.initialIndex - o1.initialIndex;
                    case MOSTUSED_UP_DOWN:
                        return o2.launchedTimes - o1.launchedTimes;
                    case MOSTUSED_DOWN_UP:
                        return o1.launchedTimes - o2.launchedTimes;
                }

                return 0;
            }
        };

        List<GroupLaunchInfo> apps;

        int bgColor = Integer.MAX_VALUE;
        int foreColor = Integer.MAX_VALUE;

        String name, lowercaseName;

        public Group(String name) {
            this.name = name;
            this.lowercaseName = name.toLowerCase();

            apps = new ArrayList<>();
        }

        public void add(LaunchInfo info, boolean sort) {
            apps.add(new GroupLaunchInfo(info, apps.size()));

            if(sort) sort();
        }

        public void remove(LaunchInfo info) {
            Iterator<GroupLaunchInfo> iterator = apps.iterator();
            while (iterator.hasNext()) {
                if(iterator.next().equals(info)) {
                    iterator.remove();
                    return;
                }
            }
        }

        public void remove(String app) {
            Iterator<GroupLaunchInfo> iterator = apps.iterator();
            while (iterator.hasNext()) {
                if(iterator.next().componentName.getPackageName().equals(app)) {
                    iterator.remove();
                    return;
                }
            }
        }

        public void sort() {
            Collections.sort(apps, comparator);
        }

        public boolean contains(LaunchInfo info) {
            return apps.contains(info);
        }

        public int getBgColor() {
            return bgColor;
        }

        public void setBgColor(int color) {
            this.bgColor = color;
        }

        public int getForeColor() {
            return foreColor;
        }

        public void setForeColor(int foreColor) {
            this.foreColor = foreColor;
        }

        @Override
        public List<? extends Object> members() {
            return apps;
        }

        @Override
        public boolean use(MainPack mainPack, String input) {
            LaunchInfo info = AppUtils.findLaunchInfoWithLabel(apps, input);
            if(info == null) return false;

            return mainPack.appsManager.launch(mainPack.context, info);
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public boolean equals(Object obj) {
            if(obj instanceof Group) {
                return name.equals(((Group) obj).name());
            } else if(obj instanceof String) {
                return obj.equals(name);
            }

            return false;
        }

        @Override
        public String getLowercaseString() {
            return lowercaseName;
        }

        @Override
        public String getString() {
            return name();
        }

        public class GroupLaunchInfo extends LaunchInfo {

            int initialIndex;

            public GroupLaunchInfo(LaunchInfo info, int index) {
                super(info);
                launchedTimes = info.launchedTimes;
                unspacedLowercaseLabel = info.unspacedLowercaseLabel;

                this.initialIndex = index;
            }
        }

    }

    public static class LaunchInfo implements Parcelable, StringableObject, Comparable<LaunchInfo> {

        private static final String COMPONENT_SEPARATOR = "-";

        public ComponentName componentName;
        public UserHandle userHandle;
        public long profileSerial;
        public boolean currentProfile;

        public String publicLabel, unspacedLowercaseLabel, lowercaseLabel;
        public int launchedTimes = 0;

        public List<ShortcutInfo> shortcuts;

        public LaunchInfo(String packageName, String activityName, String label) {
            this(packageName, activityName, label, Process.myUserHandle(), 0L, true);
        }

        public LaunchInfo(String packageName, String activityName, String label, UserHandle userHandle, long profileSerial, boolean currentProfile) {
            this.componentName = new ComponentName(packageName, activityName);
            this.userHandle = userHandle != null ? userHandle : Process.myUserHandle();
            this.profileSerial = profileSerial;
            this.currentProfile = currentProfile;
            setLabel(displayLabel(label, currentProfile));
        }

        public LaunchInfo(LaunchInfo info) {
            this(info.componentName.getPackageName(), info.componentName.getClassName(), info.publicLabel, info.userHandle, info.profileSerial, info.currentProfile);
            launchedTimes = info.launchedTimes;
            unspacedLowercaseLabel = info.unspacedLowercaseLabel;
            lowercaseLabel = info.lowercaseLabel;
            shortcuts = info.shortcuts;
        }

        protected LaunchInfo(Parcel in) {
            componentName = in.readParcelable(ComponentName.class.getClassLoader());
            userHandle = in.readParcelable(UserHandle.class.getClassLoader());
            profileSerial = in.readLong();
            currentProfile = in.readByte() != 0;
            setLabel(in.readString());
            launchedTimes = in.readInt();
        }

        public static final Creator<LaunchInfo> CREATOR = new Creator<LaunchInfo>() {
            @Override
            public LaunchInfo createFromParcel(Parcel in) {
                return new LaunchInfo(in);
            }

            @Override
            public LaunchInfo[] newArray(int size) {
                return new LaunchInfo[size];
            }
        };

        public void setLabel(String s) {
            this.publicLabel = s;
            this.lowercaseLabel = s.toLowerCase();
            this.unspacedLowercaseLabel = Tuils.removeSpaces(lowercaseLabel);
        }

        private static String displayLabel(String label, boolean currentProfile) {
            if (label == null) {
                label = Tuils.EMPTYSTRING;
            }
            if (currentProfile || label.endsWith(" (Work)")) {
                return label;
            }
            return label + " (Work)";
        }

        public boolean isInside(String apps) {
            String[] split = apps.split(AppsManager.APPS_SEPARATOR);
            for(String s : split) {
                if(is(s)) return true;
            }

            return false;
        }

        public boolean is(String app) {
            Identity identity = identityInfo(app);
            return identity != null && profileSerial == identity.profileSerial && componentName.equals(identity.componentName);
        }

        public static Identity identityInfo(String app) {
            if (app == null) {
                return null;
            }

            String[] split = app.split(COMPONENT_SEPARATOR, 3);
            if (split.length != 3 || !split[0].startsWith(PROFILE_PREFIX)) {
                return null;
            }

            try {
                long serial = Long.parseLong(split[0].substring(PROFILE_PREFIX.length()));
                return new Identity(serial, new ComponentName(split[1], split[2]));
            } catch (Exception e) {
                return null;
            }
        }

        @Override
        public boolean equals(Object o) {
            if(o == null) {
                return false;
            }

            if(o instanceof LaunchInfo) {
                LaunchInfo i = (LaunchInfo) o;
                try {
                    return this.profileSerial == i.profileSerial && this.componentName.equals(i.componentName);
                } catch (Exception e) {
                    return false;
                }
            }
            else if(o instanceof ComponentName) {
                return this.componentName.equals(o);
            }
            else if(o instanceof String) {
                return is((String) o) || this.componentName.getClassName().equals(o);
            }

            return false;
        }

        @Override
        public String toString() {
            return write() + " --> " + publicLabel + ", n=" + launchedTimes;
        }

        @Override
        public String getLowercaseString() {
            return lowercaseLabel;
        }

        @Override
        public String getString() {
            return publicLabel;
        }

        public String write() {
            return PROFILE_PREFIX + profileSerial + COMPONENT_SEPARATOR + this.componentName.getPackageName() + COMPONENT_SEPARATOR + this.componentName.getClassName();
        }

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            dest.writeParcelable(componentName, flags);
            dest.writeParcelable(userHandle, flags);
            dest.writeLong(profileSerial);
            dest.writeByte((byte) (currentProfile ? 1 : 0));
            dest.writeString(publicLabel);
            dest.writeInt(launchedTimes);
        }

        public void setShortcuts(List<ShortcutInfo> s) {
            this.shortcuts = s;
        }

        @Override
        public int compareTo(@NonNull LaunchInfo o) {
            return o.launchedTimes - launchedTimes;
        }

        public static class Identity {
            final long profileSerial;
            final ComponentName componentName;

            Identity(long profileSerial, ComponentName componentName) {
                this.profileSerial = profileSerial;
                this.componentName = componentName;
            }
        }
    }

    private class AppsHolder {

        final int MOST_USED = 10, NULL = 11, USER_DEFINIED = 12;

        private List<LaunchInfo> infos;
        private XMLPrefsList values;

        private SuggestedAppMgr suggestedAppMgr;

        private class SuggestedAppMgr {
            private List<SuggestedApp> suggested;
            private int lastWriteable = -1;

            public SuggestedAppMgr(XMLPrefsList values, List<LaunchInfo> apps) {
                suggested = new ArrayList<>();

                final String PREFIX = "default_app_n";
                for(int count = 0; count < 5; count++) {
                    String vl = values.get(Apps.valueOf(PREFIX + (count + 1))).value;

                    if(vl.equals(Apps.NULL)) continue;
                    if(vl.equals(Apps.MOST_USED)) suggested.add(new SuggestedApp(MOST_USED, count + 1));
                    else {
                        LaunchInfo.Identity identity = LaunchInfo.identityInfo(vl);
                        if(identity == null) continue;

                        LaunchInfo info = AppUtils.findLaunchInfoWithIdentity(infos, identity);
                        if(info == null) continue;
                        suggested.add(new SuggestedApp(info, USER_DEFINIED, count + 1));
                    }
                }

                sort();
            }

            public int size() {
                return suggested.size();
            }

            private void sort() {
                Collections.sort(suggested);
                for(int count = 0; count < suggested.size(); count++) {
                    if(suggested.get(count).type != MOST_USED) {
                        lastWriteable = count - 1;
                        return;
                    }
                }
                lastWriteable = suggested.size() - 1;
            }

            public SuggestedApp get(int index) {
                return suggested.get(index);
            }

            public void set(int index, LaunchInfo info) {
                suggested.get(index).change(info);
            }

            public void attemptInsertSuggestion(LaunchInfo info) {
                if (info.launchedTimes == 0 || lastWriteable == -1) {
                    return;
                }

                int index = Tuils.find(info, suggested);
                if (index == -1) {
                    for (int count = 0; count <= lastWriteable; count++) {
                        SuggestedApp app = get(count);

                        if (app.app == null || info.launchedTimes > app.app.launchedTimes) {
                            SuggestedApp s = suggested.get(count);

                            LaunchInfo before = s.app;
                            s.change(info);

                            if(before != null) {
                                attemptInsertSuggestion(before);
                            }

                            break;
                        }
                    }
                }
                sort();
            }

            public List<LaunchInfo> apps() {
                List<LaunchInfo> list = new ArrayList<>();

                List<SuggestedApp> cp = new ArrayList<>(suggested);
                Collections.sort(cp, (o1, o2) -> o1.index - o2.index);

                for(int count = 0; count < cp.size(); count++) {
                    SuggestedApp app = cp.get(count);
                    if(app.type != NULL && app.app != null) list.add(app.app);
                }
                return list;
            }

            private class SuggestedApp implements Comparable {
                int type;
                LaunchInfo app;
                int index;

                public SuggestedApp(int type, int index) {
                    this(null, type, index);
                }

                public SuggestedApp(LaunchInfo info, int type, int index) {
                    this.app = info;
                    this.type = type;
                    this.index = index;
                }

                public SuggestedApp change(LaunchInfo info) {
                    this.app = info;
                    return this;
                }

                @Override
                public boolean equals(Object o) {
                    if(o instanceof SuggestedApp) {
                        try {
                            return (app == null && ((SuggestedApp) o).app == null) || app.equals(((SuggestedApp) o).app);
                        } catch (NullPointerException e) {
                            return false;
                        }
                    } else if(o instanceof LaunchInfo) {
                        if(app == null) return false;
                        return app.equals(o);
                    }
                    return false;
                }

                @Override
                public int compareTo(@NonNull Object o) {
                    SuggestedApp other = (SuggestedApp) o;

                    if(this.type == USER_DEFINIED || other.type == USER_DEFINIED) {
                        if(this.type == USER_DEFINIED && other.type == USER_DEFINIED) return other.app.launchedTimes - this.app.launchedTimes;
                        if(this.type == USER_DEFINIED) return 1;
                        return -1;
                    }

//                    most_used
                    if(this.app == null || other.app == null) {
                        if(this.app == null && other.app == null) return 0;
                        if(this.app == null) return 1;
                        return -1;
                    }
                    return this.app.launchedTimes - other.app.launchedTimes;
                }
            }
        }

        Comparator<LaunchInfo> mostUsedComparator = (lhs, rhs) -> rhs.launchedTimes > lhs.launchedTimes ? -1 : rhs.launchedTimes == lhs.launchedTimes ? 0 : 1;

        public AppsHolder(List<LaunchInfo> infos, XMLPrefsList values) {
            this.infos = infos;
            this.values = values;
            update(true);
        }

        public void add(LaunchInfo info) {
            if(!infos.contains(info) ) {
                infos.add(info);
                update(false);
            }
        }

        public void remove(LaunchInfo info) {
            infos.remove(info);
            update(true);
        }

        private void sort() {
            try {
                Collections.sort(infos, mostUsedComparator);
            } catch (NullPointerException e) {}
        }

        private void fillSuggestions() {
            suggestedAppMgr = new SuggestedAppMgr(values, getApps());
            for(LaunchInfo info : infos) {
                suggestedAppMgr.attemptInsertSuggestion(info);
            }
        }

        public void requestSuggestionUpdate(LaunchInfo info) {
            suggestedAppMgr.attemptInsertSuggestion(info);
        }

        private void update(boolean refreshSuggestions) {
            AppUtils.checkEquality(infos);
            sort();
            if(refreshSuggestions) {
                fillSuggestions();
            }
        }

        public List<LaunchInfo> getApps() {
            return infos;
        }

        public LaunchInfo[] getSuggestedApps() {
            List<LaunchInfo> apps = suggestedAppMgr.apps();
            return apps.toArray(new LaunchInfo[apps.size()]);
        }
    }

    public static class AppUtils {

        public static LaunchInfo findLaunchInfoWithIdentity(List<LaunchInfo> appList, LaunchInfo.Identity identity) {
            if(identity == null) return null;

            for(LaunchInfo i : appList) {
                if(i.profileSerial == identity.profileSerial && i.componentName.equals(identity.componentName)) return i;
            }

            return null;
        }

        public static LaunchInfo findLaunchInfoWithLabel(List<? extends LaunchInfo> appList, String label) {
            label = Tuils.removeSpaces(label);
            for(LaunchInfo i : appList) if(i.unspacedLowercaseLabel.equalsIgnoreCase(label)) return i;
            return null;
        }

        private static List<LaunchInfo> findLaunchInfosWithPackage(String packageName, List<LaunchInfo> infos) {
            List<LaunchInfo> result = new ArrayList<>();
            for(LaunchInfo info : infos) if (info.componentName.getPackageName().equals(packageName)) result.add(info);
            return result;
        }

        public static void checkEquality(List<LaunchInfo> list) {

            for (LaunchInfo info : list) {

                if(info == null || info.publicLabel == null) {
                    continue;
                }

                for (int count = 0; count < list.size(); count++) {
                    LaunchInfo info2 = list.get(count);

                    if(info2 == null || info2.publicLabel == null) {
                        continue;
                    }

                    if(info == info2) {
                        continue;
                    }

                    if (info.unspacedLowercaseLabel.equals(info2.unspacedLowercaseLabel)) {
//                        there are two activities in the same app loadlabel gives the same result
                        if(info.componentName.getPackageName().equals(info2.componentName.getPackageName())) {
                            info.setLabel(insertActivityName(info.publicLabel, info.componentName.getClassName()));
                            info2.setLabel(insertActivityName(info2.publicLabel, info2.componentName.getClassName()));
                        }
                        else {
                            info2.setLabel(getNewLabel(info2.publicLabel, info2.componentName.getClassName()));
                        }
                    }
                }
            }
        }

        static Pattern activityPattern = Pattern.compile("activity", Pattern.CASE_INSENSITIVE | Pattern.LITERAL);
        public static String insertActivityName(String oldLabel, String activityName) {
            String name;

            int lastDot = activityName.lastIndexOf(".");
            if(lastDot == -1) {
                name = activityName;
            } else {
                name = activityName.substring(lastDot + 1);
            }

            name = activityPattern.matcher(name).replaceAll(Tuils.EMPTYSTRING);
            name = name.substring(0,1).toUpperCase() + name.substring(1);
            return oldLabel + Tuils.SPACE + "-" + Tuils.SPACE + name;
        }

        public static String getNewLabel(String oldLabel, String packageName) {
            try {

                int firstDot = packageName.indexOf(Tuils.DOT);
                if(firstDot == -1) {
//                    no dots in package name (nearly impossible)
                    return packageName;
                }
                firstDot++;

                int secondDot = packageName.substring(firstDot).indexOf(Tuils.DOT);
                String prefix;
                if(secondDot == -1) {
//                    only one dot, so two words. The first is most likely to be the company name
//                    facebook.messenger
//                    is better than
//                    messenger.facebook
                    prefix = packageName.substring(0, firstDot - 1);
                    prefix = prefix.substring(0,1).toUpperCase() + prefix.substring(1).toLowerCase();
                    return prefix + Tuils.SPACE + oldLabel;
                } else {
//                    two dots or more, the second word is the company name
                    prefix = packageName.substring(firstDot, secondDot + firstDot);
                    prefix = prefix.substring(0,1).toUpperCase() + prefix.substring(1).toLowerCase();
                    return prefix + Tuils.SPACE + oldLabel;
                }

            } catch (Exception e) {
                return packageName;
            }
        }

        public static String format(LaunchInfo app, PackageInfo info) {
            StringBuilder builder = new StringBuilder();

            builder.append(info.packageName).append(Tuils.NEWLINE);
            builder.append("vrs: ").append(info.versionCode).append(" - ").append(info.versionName).append(Tuils.NEWLINE).append(Tuils.NEWLINE);
            builder.append("launched_times: ").append(app.launchedTimes).append(Tuils.NEWLINE).append(Tuils.NEWLINE);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.GINGERBREAD) {
                builder.append("Install: ").append(TimeManager.instance.replace("%t0", info.firstInstallTime, Integer.MAX_VALUE)).append(Tuils.NEWLINE).append(Tuils.NEWLINE);
            }

            ActivityInfo[] a = info.activities;
            if(a != null && a.length > 0) {
                List<String> as = new ArrayList<>();
                for(ActivityInfo i : a) as.add(i.name.replace(info.packageName, Tuils.EMPTYSTRING));
                builder.append("Activities: ").append(Tuils.NEWLINE).append(Tuils.toPlanString(as, Tuils.NEWLINE)).append(Tuils.NEWLINE).append(Tuils.NEWLINE);
            }

            ServiceInfo[] s = info.services;
            if(s != null && s.length > 0) {
                List<String> ss = new ArrayList<>();
                for(ServiceInfo i : s) ss.add(i.name.replace(info.packageName, Tuils.EMPTYSTRING));
                builder.append("Services: ").append(Tuils.NEWLINE).append(Tuils.toPlanString(ss, Tuils.NEWLINE)).append(Tuils.NEWLINE).append(Tuils.NEWLINE);
            }

            ActivityInfo[] r = info.receivers;
            if(r != null && r.length > 0) {
                List<String> rs = new ArrayList<>();
                for(ActivityInfo i : r) rs.add(i.name.replace(info.packageName, Tuils.EMPTYSTRING));
                builder.append("Receivers: ").append(Tuils.NEWLINE).append(Tuils.toPlanString(rs, Tuils.NEWLINE)).append(Tuils.NEWLINE).append(Tuils.NEWLINE);
            }

            String[] p = info.requestedPermissions;
            if(p != null && p.length > 0) {
                List<String> ps = new ArrayList<>();
                for(String i : p) ps.add(i.substring(i.lastIndexOf(".") + 1));
                builder.append("Permissions: ").append(Tuils.NEWLINE).append(Tuils.toPlanString(ps, ", "));
            }

            return builder.toString();
        }

        public static String printApps(List<String> apps) {
            if(apps.size() == 0) {
                return apps.toString();
            }

            List<String> list = new ArrayList<>(apps);

            Collections.sort(list, Tuils::alphabeticCompare);

            Tuils.addPrefix(list, Tuils.DOUBLE_SPACE);
            Tuils.insertHeaders(list, false);
            return Tuils.toPlanString(list);
        }

        public static List<String> labelList(List<LaunchInfo> infos, boolean sort) {
            List<String> labels = new ArrayList<>();
            for (LaunchInfo info : infos) {
                labels.add(info.publicLabel);
            }
            if(sort) Collections.sort(labels);
            return labels;
        }
    }

}
