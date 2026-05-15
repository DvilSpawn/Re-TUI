package ohi.andre.consolelauncher.commands.tuixt;

import android.content.Intent;
import android.content.ActivityNotFoundException;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.database.Cursor;
import android.app.Dialog;
import android.graphics.Color;
import android.net.Uri;
import android.graphics.Typeface;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.text.InputType;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import ohi.andre.consolelauncher.LauncherActivity;
import ohi.andre.consolelauncher.managers.BackupManager;
import ohi.andre.consolelauncher.managers.PresetManager;
import ohi.andre.consolelauncher.managers.settings.MusicSettings;
import ohi.andre.consolelauncher.managers.settings.LauncherSettings;
import ohi.andre.consolelauncher.managers.xml.XMLPrefsManager;
import ohi.andre.consolelauncher.managers.xml.options.Behavior;
import ohi.andre.consolelauncher.managers.xml.options.Ui;
import ohi.andre.consolelauncher.tuils.Tuils;

public class ThemerActivity extends AppCompatActivity {

    public static final String EXTRA_SECTION = "section";
    public static final String SECTION_HOME = "home";
    public static final String SECTION_APPEARANCE = "appearance";
    public static final String SECTION_BEHAVIOR = "behavior";
    public static final String SECTION_PERSONALIZATION = "personalization";
    public static final String SECTION_INTEGRATIONS = "integrations";
    public static final String SECTION_SYSTEM = "system";
    private static final int BACKUP_EXPORT_REQUEST = 201;
    private static final int BACKUP_RESTORE_REQUEST = 202;
    private static final int SHAREABLE_CONFIG_EXPORT_REQUEST = 203;
    private static final int FONT_IMPORT_REQUEST = 204;

    private RecyclerView recyclerView;
    private TextView header;
    private RecyclerView.Adapter<ViewHolder> sectionsAdapter;
    private final List<String> sectionItems = new ArrayList<>();
    private String section;
    private Uri pendingBackupUri;
    private Uri pendingRestoreUri;
    private String pendingShareablePresetName;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        section = getIntent() != null ? getIntent().getStringExtra(EXTRA_SECTION) : null;
        if (section == null || section.length() == 0) {
            section = SECTION_HOME;
        }

        FrameLayout screen = new FrameLayout(this);
        screen.setBackgroundColor(TuixtTheme.overlayColor());
        screen.setFitsSystemWindows(true);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(TuixtTheme.dp(this, 14), TuixtTheme.dp(this, 50), TuixtTheme.dp(this, 14), TuixtTheme.dp(this, 14));
        TuixtTheme.stylePanel(this, root);

        int panelLeft = TuixtTheme.dp(this, 28);
        int panelTop = TuixtTheme.dp(this, 34);
        FrameLayout.LayoutParams panelParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT);
        panelParams.setMargins(panelLeft, panelTop, TuixtTheme.dp(this, 28), TuixtTheme.dp(this, 28));
        screen.addView(root, panelParams);

        header = new TextView(this);
        header.setText(getHeaderText(section));
        TuixtTheme.styleHeader(this, header);
        FrameLayout.LayoutParams headerParams = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        headerParams.gravity = Gravity.TOP | Gravity.START;
        headerParams.leftMargin = panelLeft + TuixtTheme.dp(this, 38);
        headerParams.topMargin = panelTop - TuixtTheme.dp(this, 11);
        screen.addView(header, headerParams);

        recyclerView = new RecyclerView(this);
        recyclerView.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        sectionItems.clear();
        sectionItems.addAll(getItemsForSection(section));

        sectionsAdapter = new RecyclerView.Adapter<ViewHolder>() {
            @Override
            public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
                TextView tv = new TextView(parent.getContext());
                tv.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                return new ViewHolder(tv);
            }

            @Override
            public void onBindViewHolder(ViewHolder holder, int position) {
                String fileName = sectionItems.get(position);
                TextView itemView = (TextView) holder.itemView;
                itemView.setText(fileName.toUpperCase());
                TuixtTheme.styleListItem(ThemerActivity.this, itemView, false);
                RecyclerView.LayoutParams params = new RecyclerView.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT);
                params.setMargins(0, 0, 0, TuixtTheme.dp(ThemerActivity.this, 8));
                itemView.setLayoutParams(params);
                holder.itemView.setOnClickListener(v -> {
                    if (fileName.equals("Appearance")) {
                        openSection(SECTION_APPEARANCE);
                    } else if (fileName.equals("Behavior")) {
                        openSection(SECTION_BEHAVIOR);
                    } else if (fileName.equals("Personalization")) {
                        openSection(SECTION_PERSONALIZATION);
                    } else if (fileName.equals("Integrations")) {
                        openSection(SECTION_INTEGRATIONS);
                    } else if (fileName.equals("System & Support")) {
                        openSection(SECTION_SYSTEM);
                    } else if (fileName.equals("Open Wallpaper Picker")) {
                        launchWallpaperPicker();
                    } else if (fileName.equals("Open Live Wallpaper Picker")) {
                        launchLiveWallpaperPicker();
                    } else if (fileName.startsWith("Preferred Music App")) {
                        showPreferredMusicAppPicker();
                    } else if (fileName.equals("Fonts")) {
                        showFontsDialog();
                    } else if (fileName.equals("Presets")) {
                        showPresetsDialog();
                    } else if (fileName.equals("View Crash Log")) {
                        File crashFile = new File(Tuils.getFolder(), "crash.txt");
                        if (!crashFile.exists() || crashFile.length() == 0) {
                            Toast.makeText(ThemerActivity.this, "No crash log found.", Toast.LENGTH_SHORT).show();
                        } else {
                            Intent intent = new Intent(ThemerActivity.this, TuixtActivity.class);
                            intent.putExtra(TuixtActivity.PATH, crashFile.getAbsolutePath());
                            startActivity(intent);
                        }
                    } else if (fileName.equals("Backup")) {
                        launchBackupPicker();
                    } else if (fileName.equals("Create Shareable Configuration")) {
                        showShareableConfigurationSourcePicker();
                    } else if (fileName.equals("Restore")) {
                        launchRestorePicker();
                    } else {
                        openConfigFile(fileName);
                    }
                });
            }

            @Override
            public int getItemCount() {
                return sectionItems.size();
            }
        };

        recyclerView.setAdapter(sectionsAdapter);

        root.addView(recyclerView);
        setContentView(screen);
    }

    private void showPresetsDialog() {
        List<String> options = Arrays.asList("Save Current as Preset", "Apply Preset");
        TuixtDialog.showOptions(ThemerActivity.this, "Presets", options, which -> {
            if (which == 0) {
                TuixtDialog.showInput(ThemerActivity.this, "Save Preset", "Preset name", "Save", "Cancel", value -> {
                    String name = value.trim();
                    if (name.length() > 0) {
                        savePreset(name);
                    }
                });
            } else {
                List<String> presetNames = PresetManager.listAllPresetNames();
                if (presetNames.isEmpty()) {
                    Toast.makeText(ThemerActivity.this, "No presets found.", Toast.LENGTH_SHORT).show();
                    return;
                }

                TuixtDialog.showOptions(ThemerActivity.this, "Select Preset", presetNames, w -> applyPreset(presetNames.get(w)));
            }
        });
    }

    private void savePreset(String name) {
        try {
            PresetManager.save(name);
            Toast.makeText(ThemerActivity.this, "Preset saved! Reloading...", Toast.LENGTH_SHORT).show();
            recyclerView.postDelayed(() -> {
                if (LauncherActivity.instance != null) {
                    LauncherActivity.instance.reload();
                }
                finish();
            }, 500);
        } catch (Exception e) {
            Toast.makeText(ThemerActivity.this, e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void applyPreset(String name) {
        try {
            PresetManager.apply(name);

            Toast.makeText(ThemerActivity.this, "Preset applied! Reloading...", Toast.LENGTH_SHORT).show();
            recyclerView.postDelayed(() -> {
                if (LauncherActivity.instance != null) {
                    LauncherActivity.instance.reload();
                }
                finish();
            }, 500);
        } catch (Exception e) {
            Toast.makeText(ThemerActivity.this, e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private String getHeaderText(String section) {
        if (SECTION_APPEARANCE.equals(section)) {
            return "Re:T-UI Appearance Settings";
        } else if (SECTION_BEHAVIOR.equals(section)) {
            return "Re:T-UI Behavior Settings";
        } else if (SECTION_PERSONALIZATION.equals(section)) {
            return "Re:T-UI Personalization Settings";
        } else if (SECTION_INTEGRATIONS.equals(section)) {
            return "Re:T-UI Integrations";
        } else if (SECTION_SYSTEM.equals(section)) {
            return "Re:T-UI System & Support";
        }
        return "Re:T-UI Settings Hub";
    }

    private List<String> getItemsForSection(String section) {
        if (SECTION_APPEARANCE.equals(section)) {
            return Arrays.asList(
                    "theme.xml",
                    "ui.xml",
                    "toolbar.xml",
                    "suggestions.xml",
                    "Fonts",
                    "Presets",
                    "Open Wallpaper Picker",
                    "Open Live Wallpaper Picker"
            );
        } else if (SECTION_BEHAVIOR.equals(section)) {
            return Arrays.asList(
                    "behavior.xml",
                    "apps.xml",
                    "notifications.xml",
                    "cmd.xml"
            );
        } else if (SECTION_PERSONALIZATION.equals(section)) {
            return Arrays.asList(
                    "alias.txt",
                    "ascii.txt",
                    "rss.xml"
            );
        } else if (SECTION_INTEGRATIONS.equals(section)) {
            return Arrays.asList("Preferred Music App: " + getPreferredMusicAppSummary());
        } else if (SECTION_SYSTEM.equals(section)) {
            return Arrays.asList("Backup", "Create Shareable Configuration", "Restore", "View Crash Log");
        }

        return Arrays.asList(
                "Appearance",
                "Behavior",
                "Personalization",
                "Integrations",
                "System & Support"
        );
    }

    private void openSection(String targetSection) {
        section = targetSection;
        header.setText(getHeaderText(section));
        sectionItems.clear();
        sectionItems.addAll(getItemsForSection(section));
        sectionsAdapter.notifyDataSetChanged();
        recyclerView.scrollToPosition(0);
    }

    @Override
    public void onBackPressed() {
        if (!SECTION_HOME.equals(section)) {
            openSection(SECTION_HOME);
            return;
        }
        super.onBackPressed();
    }

    private void openConfigFile(String fileName) {
        Intent intent = new Intent(ThemerActivity.this, TuixtActivity.class);
        intent.putExtra(TuixtActivity.PATH, new File(Tuils.getFolder(), fileName).getAbsolutePath());
        startActivityForResult(intent, LauncherActivity.TUIXT_REQUEST);
    }

    private void showFontsDialog() {
        File fontsDir = getFontsDir();
        File[] fonts = listFontFiles(fontsDir);

        TuixtDialog.showCustom(this, "Select Font", dialog -> {
            LinearLayout content = new LinearLayout(this);
            content.setOrientation(LinearLayout.VERTICAL);

            addFontActionRow(content, dialog, "Default (System Font)", this::applySystemFont);
            addFontActionRow(content, dialog, "Import Font...", this::launchFontImportPicker);
            for (File font : fonts) {
                addFontFileRow(content, dialog, font);
            }

            return content;
        });
    }

    private void addFontActionRow(LinearLayout content, Dialog dialog, String label, Runnable action) {
        TextView row = new TextView(this);
        row.setText(label.toUpperCase(Locale.US));
        TuixtTheme.styleListItem(this, row, false);
        row.setOnClickListener(v -> {
            dialog.dismiss();
            action.run();
        });

        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        rowParams.bottomMargin = TuixtTheme.dp(this, 8);
        content.addView(row, rowParams);
    }

    private void addFontFileRow(LinearLayout content, Dialog dialog, File font) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        TextView label = new TextView(this);
        label.setText(font.getName().toUpperCase(Locale.US));
        TuixtTheme.styleListItem(this, label, false);
        label.setOnClickListener(v -> {
            dialog.dismiss();
            applyFont(font);
        });

        TextView delete = new TextView(this);
        delete.setText("X");
        TuixtTheme.styleButton(this, delete, false);
        delete.setMinWidth(TuixtTheme.dp(this, 52));
        delete.setMinHeight(TuixtTheme.dp(this, 48));
        delete.setOnClickListener(v -> confirmDeleteFont(dialog, font));

        row.addView(label, new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1));

        View spacer = new View(this);
        row.addView(spacer, new LinearLayout.LayoutParams(TuixtTheme.dp(this, 8), 1));

        row.addView(delete, new LinearLayout.LayoutParams(
                TuixtTheme.dp(this, 58),
                TuixtTheme.dp(this, 48)));

        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        rowParams.bottomMargin = TuixtTheme.dp(this, 8);
        content.addView(row, rowParams);
    }

    private void confirmDeleteFont(Dialog fontsDialog, File font) {
        TuixtDialog.showConfirm(
                this,
                "Delete Font",
                "Delete " + font.getName() + "?",
                "Delete",
                "Cancel",
                () -> {
                    fontsDialog.dismiss();
                    deleteFont(font);
                });
    }

    private void deleteFont(File font) {
        String deletedName = font.getName();
        boolean deleted = !font.exists() || font.delete();

        File rootCopy = new File(Tuils.getFolder(), deletedName);
        if (rootCopy.exists() && rootCopy.isFile()) {
            Tuils.insertOld(rootCopy);
        }

        if (!deleted) {
            Toast.makeText(this, "Could not delete font.", Toast.LENGTH_LONG).show();
            return;
        }

        String selected = LauncherSettings.get(Ui.font_file);
        if (selected != null && selected.equals(deletedName)) {
            LauncherSettings.set(this, Ui.system_font, "true");
            LauncherSettings.set(this, Ui.font_file, "");
            finalizeFontChange("Font deleted. System font applied!");
            return;
        }

        Toast.makeText(this, "Font deleted.", Toast.LENGTH_SHORT).show();
        showFontsDialog();
    }

    private File getFontsDir() {
        File fontsDir = new File(Tuils.getFolder(), "fonts");
        if (!fontsDir.exists() && !fontsDir.mkdirs()) {
            Log.e("TUI-THEMER", "Unable to create fonts folder: " + fontsDir.getAbsolutePath());
        }
        return fontsDir;
    }

    private File[] listFontFiles(File fontsDir) {
        File[] fonts = fontsDir.listFiles((dir, name) -> isFontFileName(name));
        if (fonts == null) {
            return new File[0];
        }
        Arrays.sort(fonts, (left, right) -> left.getName().compareToIgnoreCase(right.getName()));
        return fonts;
    }

    private void launchFontImportPicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[] {
                "font/ttf",
                "font/otf",
                "application/x-font-ttf",
                "application/x-font-otf",
                "application/vnd.ms-opentype",
                "application/font-sfnt",
                "application/octet-stream"
        });
        try {
            startActivityForResult(intent, FONT_IMPORT_REQUEST);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "Font picker is unavailable on this device.", Toast.LENGTH_SHORT).show();
        }
    }

    private void applySystemFont() {
        LauncherSettings.set(this, Ui.system_font, "true");
        LauncherSettings.set(this, Ui.font_file, "");

        sweepCurrentFonts();
        finalizeFontChange("System font applied!");
    }

    private void applyFont(File source) {
        LauncherSettings.set(this, Ui.system_font, "false");
        LauncherSettings.set(this, Ui.font_file, source.getName());

        sweepCurrentFonts();

        File tuiFolder = Tuils.getFolder();
        File dest = new File(tuiFolder, source.getName());
        try {
            Log.e("TUI-THEMER", "Copying font from " + source.getAbsolutePath() + " to " + dest.getAbsolutePath());
            Tuils.copy(source, dest);

            if (dest.exists() && dest.length() > 0) {
                Log.e("TUI-THEMER", "Copy successful! Size: " + dest.length());
            } else {
                Log.e("TUI-THEMER", "Copy failed or file is empty!");
            }

            finalizeFontChange("Font applied!");
        } catch (Exception e) {
            Toast.makeText(this, "Error applying font: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void sweepCurrentFonts() {
        File tuiFolder = Tuils.getFolder();
        File[] currentFiles = tuiFolder.listFiles();
        if (currentFiles != null) {
            for (File f : currentFiles) {
                String name = f.getName().toLowerCase(Locale.US);
                if (name.endsWith(".ttf") || name.endsWith(".otf")) {
                    Tuils.insertOld(f);
                }
            }
        }
    }

    private void finalizeFontChange(String message) {
        Tuils.cancelFont();

        Toast.makeText(this, message + " Reloading...", Toast.LENGTH_SHORT).show();

        recyclerView.postDelayed(() -> {
            if (LauncherActivity.instance != null) {
                LauncherActivity.instance.reload();
            }
            finish();
        }, 500);
    }

    private void launchWallpaperPicker() {
        try {
            startActivity(Intent.createChooser(new Intent(Intent.ACTION_SET_WALLPAPER), "Select wallpaper"));
        } catch (Exception e) {
            Toast.makeText(this, "Wallpaper picker is unavailable on this device.", Toast.LENGTH_SHORT).show();
        }
    }

    private void launchLiveWallpaperPicker() {
        try {
            startActivity(new Intent(android.app.WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER));
        } catch (Exception e) {
            Toast.makeText(this, "Live wallpaper picker is unavailable on this device.", Toast.LENGTH_SHORT).show();
        }
    }

    private void launchBackupPicker() {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/zip");
        intent.putExtra(Intent.EXTRA_TITLE, BackupManager.defaultBackupName());
        try {
            startActivityForResult(intent, BACKUP_EXPORT_REQUEST);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "Backup picker is unavailable on this device.", Toast.LENGTH_SHORT).show();
        }
    }

    private void showShareableConfigurationSourcePicker() {
        List<String> presets = PresetManager.listSavedPresetFolders();
        List<String> options = new ArrayList<>();
        options.add("Current Active Look");
        for (String preset : presets) {
            options.add("Preset: " + preset);
        }

        TuixtDialog.showOptions(this, "Shareable Source", options, which -> {
            pendingShareablePresetName = which == 0 ? null : presets.get(which - 1);
            launchShareableConfigurationPicker();
        });
    }

    private void launchShareableConfigurationPicker() {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/zip");
        intent.putExtra(Intent.EXTRA_TITLE, BackupManager.defaultShareableConfigurationName(pendingShareablePresetName));
        try {
            startActivityForResult(intent, SHAREABLE_CONFIG_EXPORT_REQUEST);
        } catch (ActivityNotFoundException e) {
            pendingShareablePresetName = null;
            Toast.makeText(this, "Configuration picker is unavailable on this device.", Toast.LENGTH_SHORT).show();
        }
    }

    private void launchRestorePicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[] {"application/zip", "application/octet-stream"});
        try {
            startActivityForResult(intent, BACKUP_RESTORE_REQUEST);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "Restore picker is unavailable on this device.", Toast.LENGTH_SHORT).show();
        }
    }

    private String getPreferredMusicAppSummary() {
        String packageName = MusicSettings.preferredPackage();
        if (packageName == null || packageName.length() == 0) {
            return "Auto detect";
        }

        PackageManager packageManager = getPackageManager();
        try {
            CharSequence label = packageManager.getApplicationLabel(packageManager.getApplicationInfo(packageName, 0));
            if (label != null && label.length() > 0) {
                return label + " (" + packageName + ")";
            }
        } catch (Exception ignored) {
        }

        return packageName;
    }

    private void showPreferredMusicAppPicker() {
        final List<AppChoice> choices = getLaunchableAppChoices();
        final List<String> labels = new ArrayList<>();
        labels.add("Auto detect");
        for (AppChoice choice : choices) {
            labels.add(choice.label + " (" + choice.packageName + ")");
        }

        TuixtDialog.showOptions(this, "Preferred Music App", labels, which -> {
            if (which == 0) {
                LauncherSettings.set(this, Behavior.preferred_music_app, Tuils.EMPTYSTRING);
                Toast.makeText(this, "Preferred music app reset to automatic detection.", Toast.LENGTH_SHORT).show();
            } else {
                AppChoice choice = choices.get(which - 1);
                LauncherSettings.set(this, Behavior.preferred_music_app, choice.packageName);
                Toast.makeText(this, "Preferred music app set to " + choice.label + ".", Toast.LENGTH_SHORT).show();
            }
            recreate();
        });
    }

    private List<AppChoice> getLaunchableAppChoices() {
        PackageManager packageManager = getPackageManager();
        Intent launcherIntent = new Intent(Intent.ACTION_MAIN);
        launcherIntent.addCategory(Intent.CATEGORY_LAUNCHER);

        List<ResolveInfo> resolved = packageManager.queryIntentActivities(launcherIntent, 0);
        List<AppChoice> choices = new ArrayList<>();
        List<String> seenPackages = new ArrayList<>();

        for (ResolveInfo info : resolved) {
            if (info.activityInfo == null || info.activityInfo.packageName == null) {
                continue;
            }

            String packageName = info.activityInfo.packageName;
            if (seenPackages.contains(packageName)) {
                continue;
            }

            CharSequence loadedLabel = info.loadLabel(packageManager);
            String label = loadedLabel != null ? loadedLabel.toString() : packageName;
            choices.add(new AppChoice(label, packageName));
            seenPackages.add(packageName);
        }

        Collections.sort(choices, new Comparator<AppChoice>() {
            @Override
            public int compare(AppChoice left, AppChoice right) {
                return left.label.compareToIgnoreCase(right.label);
            }
        });

        return choices;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == LauncherActivity.TUIXT_REQUEST && resultCode == TuixtActivity.SAVE_PRESSED) {
            if (LauncherActivity.instance != null) {
                LauncherActivity.instance.reload();
            }
            finish();
        } else if (requestCode == BACKUP_EXPORT_REQUEST) {
            handleBackupResult(resultCode, data);
        } else if (requestCode == SHAREABLE_CONFIG_EXPORT_REQUEST) {
            handleShareableConfigurationResult(resultCode, data);
        } else if (requestCode == BACKUP_RESTORE_REQUEST) {
            handleRestoreResult(resultCode, data);
        } else if (requestCode == FONT_IMPORT_REQUEST) {
            handleFontImportResult(resultCode, data);
        }
    }

    private void handleFontImportResult(int resultCode, Intent data) {
        if (resultCode != RESULT_OK || data == null || data.getData() == null) {
            Toast.makeText(this, "Font import cancelled.", Toast.LENGTH_SHORT).show();
            return;
        }

        Uri uri = data.getData();
        String sourceName = getDisplayName(uri);
        if (sourceName == null || sourceName.trim().length() == 0) {
            sourceName = uri.getLastPathSegment();
        }

        String fileName = sanitizeFontFileName(sourceName);
        if (!isFontFileName(fileName)) {
            Toast.makeText(this, "Choose a .ttf or .otf font file.", Toast.LENGTH_LONG).show();
            return;
        }

        File dest = uniqueFontFile(getFontsDir(), fileName);
        try (InputStream in = getContentResolver().openInputStream(uri);
             OutputStream out = new FileOutputStream(dest)) {
            if (in == null) {
                throw new IllegalStateException("Unable to read selected font.");
            }

            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
        } catch (Exception e) {
            if (dest.exists()) {
                dest.delete();
            }
            Toast.makeText(this, "Font import failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
            return;
        }

        if (!dest.exists() || dest.length() == 0) {
            dest.delete();
            Toast.makeText(this, "Font import failed: empty file.", Toast.LENGTH_LONG).show();
            return;
        }

        Toast.makeText(this, "Font imported.", Toast.LENGTH_SHORT).show();
        applyFont(dest);
    }

    private String getDisplayName(Uri uri) {
        Cursor cursor = null;
        try {
            cursor = getContentResolver().query(uri, new String[] { OpenableColumns.DISPLAY_NAME }, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (index >= 0) {
                    return cursor.getString(index);
                }
            }
        } catch (Exception ignored) {
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return null;
    }

    private String sanitizeFontFileName(String name) {
        if (name == null) {
            return "font.ttf";
        }

        name = name.replace('\\', '/');
        int slash = name.lastIndexOf('/');
        if (slash >= 0 && slash < name.length() - 1) {
            name = name.substring(slash + 1);
        }

        name = name.trim().replaceAll("[^A-Za-z0-9._ -]", "_").replaceAll("\\s+", "_");
        if (name.length() == 0) {
            return "font.ttf";
        }
        return name;
    }

    private boolean isFontFileName(String name) {
        if (name == null) {
            return false;
        }
        String lower = name.toLowerCase(Locale.US);
        return lower.endsWith(".ttf") || lower.endsWith(".otf");
    }

    private File uniqueFontFile(File fontsDir, String fileName) {
        File file = new File(fontsDir, fileName);
        if (!file.exists()) {
            return file;
        }

        int dot = fileName.lastIndexOf('.');
        String base = dot > 0 ? fileName.substring(0, dot) : fileName;
        String extension = dot > 0 ? fileName.substring(dot) : "";
        int counter = 2;
        while (true) {
            File candidate = new File(fontsDir, base + "-" + counter + extension);
            if (!candidate.exists()) {
                return candidate;
            }
            counter++;
        }
    }

    private void handleBackupResult(int resultCode, Intent data) {
        if (resultCode != RESULT_OK || data == null || data.getData() == null) {
            Toast.makeText(this, "Backup cancelled.", Toast.LENGTH_SHORT).show();
            return;
        }

        pendingBackupUri = data.getData();
        TuixtDialog.showOptions(this, "Backup Protection", Arrays.asList("Encrypt with Password", "Export Without Password"), which -> {
            if (which == 0) {
                showBackupPasswordDialog();
            } else {
                exportBackup(null);
            }
        });
    }

    private void showBackupPasswordDialog() {
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);

        EditText password = passwordInput("Password");
        EditText confirm = passwordInput("Confirm password");
        content.addView(password, inputParams());
        content.addView(confirm, inputParams());

        TuixtDialog.showContent(this, "Backup Password", content, "Export", "Cancel", () -> {
            String first = password.getText().toString();
            String second = confirm.getText().toString();
            if (first.length() == 0) {
                Toast.makeText(this, "Password is required.", Toast.LENGTH_SHORT).show();
                recyclerView.postDelayed(this::showBackupPasswordDialog, 250);
                return;
            }
            if (!first.equals(second)) {
                Toast.makeText(this, "Passwords do not match.", Toast.LENGTH_SHORT).show();
                recyclerView.postDelayed(this::showBackupPasswordDialog, 250);
                return;
            }
            exportBackup(first);
        });
    }

    private void exportBackup(String password) {
        try {
            BackupManager.exportBackup(this, pendingBackupUri, password);
            Toast.makeText(this, "Backup exported.", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, e.getMessage() == null ? "Backup failed." : e.getMessage(), Toast.LENGTH_LONG).show();
        } finally {
            pendingBackupUri = null;
        }
    }

    private void handleShareableConfigurationResult(int resultCode, Intent data) {
        if (resultCode != RESULT_OK || data == null || data.getData() == null) {
            pendingShareablePresetName = null;
            Toast.makeText(this, "Configuration export cancelled.", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            BackupManager.exportShareableConfiguration(this, data.getData(), pendingShareablePresetName);
            Toast.makeText(this, "Shareable configuration exported.", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, e.getMessage() == null ? "Configuration export failed." : e.getMessage(), Toast.LENGTH_LONG).show();
        } finally {
            pendingShareablePresetName = null;
        }
    }

    private void handleRestoreResult(int resultCode, Intent data) {
        if (resultCode != RESULT_OK || data == null || data.getData() == null) {
            Toast.makeText(this, "Restore cancelled.", Toast.LENGTH_SHORT).show();
            return;
        }

        Uri uri = data.getData();
        try {
            if ((data.getFlags() & Intent.FLAG_GRANT_READ_URI_PERMISSION) != 0) {
                getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            }
        } catch (Exception ignored) {
        }

        pendingRestoreUri = uri;
        try {
            if (BackupManager.isEncryptedBackup(this, uri)) {
                showRestorePasswordDialog();
            } else {
                restoreBackup(null);
            }
        } catch (Exception e) {
            Toast.makeText(this, e.getMessage() == null ? "Restore failed." : e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void showRestorePasswordDialog() {
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);

        EditText password = passwordInput("Backup password");
        content.addView(password, inputParams());

        TuixtDialog.showContent(this, "Restore Password", content, "Restore", "Cancel", () -> {
            String value = password.getText().toString();
            if (value.length() == 0) {
                Toast.makeText(this, "Password is required.", Toast.LENGTH_SHORT).show();
                recyclerView.postDelayed(this::showRestorePasswordDialog, 250);
                return;
            }
            restoreBackup(value);
        });
    }

    private void restoreBackup(String password) {
        try {
            BackupManager.importBackup(this, pendingRestoreUri, password);
            Toast.makeText(this, "Backup restored. Reloading...", Toast.LENGTH_SHORT).show();
            pendingRestoreUri = null;
            recyclerView.postDelayed(() -> {
                if (LauncherActivity.instance != null) {
                    LauncherActivity.instance.reload();
                }
                finish();
            }, 500);
        } catch (Exception e) {
            Toast.makeText(this, e.getMessage() == null ? "Restore failed." : e.getMessage(), Toast.LENGTH_LONG).show();
            if (password != null && pendingRestoreUri != null) {
                recyclerView.postDelayed(this::showRestorePasswordDialog, 500);
            }
        }
    }

    private EditText passwordInput(String hint) {
        EditText input = new EditText(this);
        input.setHint(hint);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        TuixtTheme.styleInput(this, input);
        return input;
    }

    private LinearLayout.LayoutParams inputParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 0, 0, TuixtTheme.dp(this, 10));
        return params;
    }

    private static class ViewHolder extends RecyclerView.ViewHolder {
        public ViewHolder(View itemView) {
            super(itemView);
        }
    }

    private static class AppChoice {
        final String label;
        final String packageName;

        AppChoice(String label, String packageName) {
            this.label = label;
            this.packageName = packageName;
        }
    }
}
