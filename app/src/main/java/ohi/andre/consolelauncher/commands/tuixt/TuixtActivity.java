package ohi.andre.consolelauncher.commands.tuixt;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.text.TextUtils;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import ohi.andre.consolelauncher.managers.settings.LauncherSettings;
import ohi.andre.consolelauncher.managers.xml.XMLPrefsManager;
import ohi.andre.consolelauncher.managers.xml.classes.XMLPrefsSave;
import ohi.andre.consolelauncher.tuils.interfaces.Reloadable;

public class TuixtActivity extends Activity {

    public static final String PATH = "path";
    public static final String ERROR_KEY = "error";
    public static final int BACK_PRESSED = 2;
    public static final int SAVE_PRESSED = 3;

    private File file;
    private RecyclerView recyclerView;
    private TuixtAdapter adapter;
    private XMLPrefsManager.XMLPrefsRoot xmlRoot;
    private List<XMLPrefsSave> originalItems;
    private EditText plainTextEditor;
    private String originalRawText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent intent = getIntent();
        String path = intent.getStringExtra(PATH);
        if (path == null) {
            finish();
            return;
        }
        file = new File(path);

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

        TextView header = new TextView(this);
        header.setText("Themer/ " + file.getName());
        TuixtTheme.styleHeader(this, header);
        FrameLayout.LayoutParams headerParams = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        headerParams.gravity = Gravity.TOP | Gravity.START;
        headerParams.leftMargin = panelLeft + TuixtTheme.dp(this, 38);
        headerParams.topMargin = panelTop - TuixtTheme.dp(this, 11);
        screen.addView(header, headerParams);

        // RecyclerView
        recyclerView = new RecyclerView(this);
        recyclerView.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        root.addView(recyclerView);

        // Bottom Bar (Search + Buttons)
        LinearLayout bottomBar = new LinearLayout(this);
        bottomBar.setOrientation(LinearLayout.VERTICAL);
        bottomBar.setPadding(10, 10, 10, 10);
        bottomBar.setBackground(TuixtTheme.rect(this, TuixtTheme.surfaceColor(), TuixtTheme.borderColor(), 1.25f));

        // Search Box
        EditText searchBox = new EditText(this);
        searchBox.setHint("Search settings...");
        TuixtTheme.styleInput(this, searchBox);
        bottomBar.addView(searchBox);

        // Action Buttons
        LinearLayout btnLayout = new LinearLayout(this);
        btnLayout.setOrientation(LinearLayout.HORIZONTAL);
        btnLayout.setPadding(0, 10, 0, 0);

        Button btnCancel = new Button(this);
        btnCancel.setText("CANCEL");
        TuixtTheme.styleButton(this, btnCancel, false);
        btnCancel.setOnClickListener(v -> finish());
        btnLayout.addView(btnCancel);

        View spacer = new View(this);
        spacer.setLayoutParams(new LinearLayout.LayoutParams(0, 1, 1));
        btnLayout.addView(spacer);

        Button btnSave = new Button(this);
        btnSave.setText("SAVE");
        TuixtTheme.styleButton(this, btnSave, true);
        btnSave.setOnClickListener(v -> {
            Toast.makeText(this, "Applying changes...", Toast.LENGTH_SHORT).show();
            if (adapter != null) {
                adapter.saveAll(this);
            } else if (plainTextEditor != null) {
                try {
                    ohi.andre.consolelauncher.tuils.Tuils.write(file, "", plainTextEditor.getText().toString());
                    XMLPrefsManager.dispose();
                    XMLPrefsManager.loadCommons(this);
                    LauncherSettings.refreshFromLoadedPrefs();
                } catch (Exception e) {
                    Toast.makeText(this, "Error saving: " + e.getMessage(), Toast.LENGTH_LONG).show();
                }
            }
            setResult(SAVE_PRESSED);
            finish();
        });
        btnLayout.addView(btnSave);

        bottomBar.addView(btnLayout);
        root.addView(bottomBar);

        // Load data
        String fileName = file.getName().toLowerCase();
        for (XMLPrefsManager.XMLPrefsRoot rootEnum : XMLPrefsManager.XMLPrefsRoot.values()) {
            if (fileName.equals(rootEnum.path())) {
                xmlRoot = rootEnum;
                break;
            }
        }

        if (xmlRoot != null) {
            originalItems = new ArrayList<>(xmlRoot.enums);
            adapter = new TuixtAdapter(originalItems, file);
            recyclerView.setAdapter(adapter);

            searchBox.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                    filter(s.toString());
                }
                @Override public void afterTextChanged(Editable s) {}
            });
        } else {
            recyclerView.setVisibility(View.GONE);
            searchBox.setVisibility(View.GONE);

            plainTextEditor = new EditText(this);
            plainTextEditor.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
            plainTextEditor.setGravity(android.view.Gravity.TOP);
            TuixtTheme.styleInput(this, plainTextEditor);

            try {
                java.io.FileInputStream fis = new java.io.FileInputStream(file);
                originalRawText = ohi.andre.consolelauncher.tuils.Tuils.convertStreamToString(fis);
                plainTextEditor.setText(originalRawText);
                fis.close();
            } catch (Exception e) {
                originalRawText = "";
                plainTextEditor.setText("");
            }

            root.addView(plainTextEditor, 2);
        }

        setContentView(screen);
    }

    private void filter(String query) {
        List<XMLPrefsSave> filtered = new ArrayList<>();
        for (XMLPrefsSave item : originalItems) {
            if (item.label().toLowerCase().contains(query.toLowerCase()) || 
                item.info().toLowerCase().contains(query.toLowerCase())) {
                filtered.add(item);
            }
        }
        adapter.updateList(filtered);
    }

    @Override
    public void onBackPressed() {
        if (!hasUnsavedChanges()) {
            super.onBackPressed();
            return;
        }

        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Discard changes?")
                .setMessage("Unsaved settings changes will be lost.")
                .setPositiveButton("Discard", (dialog, which) -> {
                    setResult(BACK_PRESSED);
                    finish();
                })
                .setNegativeButton("Keep editing", null)
                .show();
    }

    private boolean hasUnsavedChanges() {
        if (adapter != null && adapter.hasPendingChanges()) {
            return true;
        }
        if (plainTextEditor != null) {
            return !TextUtils.equals(originalRawText, plainTextEditor.getText().toString());
        }
        return false;
    }
}
