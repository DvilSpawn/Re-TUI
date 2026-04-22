package ohi.andre.consolelauncher.commands.tuixt;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

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

        // Root Layout
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.BLACK);
        root.setPadding(16, 16, 16, 16);
        root.setFitsSystemWindows(true);

        // Header
        TextView header = new TextView(this);
        header.setText("> TUI-Themer: " + file.getName());
        header.setTextColor(Color.GREEN);
        header.setTypeface(android.graphics.Typeface.MONOSPACE);
        header.setTextSize(16);
        root.addView(header);

        // Divider
        View divider = new View(this);
        divider.setBackgroundColor(Color.parseColor("#222222"));
        divider.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 2));
        root.addView(divider);

        // RecyclerView
        recyclerView = new RecyclerView(this);
        recyclerView.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        root.addView(recyclerView);

        // Bottom Bar (Search + Buttons)
        LinearLayout bottomBar = new LinearLayout(this);
        bottomBar.setOrientation(LinearLayout.VERTICAL);
        bottomBar.setBackgroundColor(Color.parseColor("#111111"));
        bottomBar.setPadding(10, 10, 10, 10);

        // Search Box
        EditText searchBox = new EditText(this);
        searchBox.setHint("Search settings...");
        searchBox.setHintTextColor(Color.GRAY);
        searchBox.setTextColor(Color.WHITE);
        searchBox.setBackgroundColor(Color.parseColor("#222222"));
        searchBox.setPadding(20, 15, 20, 15);
        searchBox.setTypeface(android.graphics.Typeface.MONOSPACE);
        searchBox.setTextSize(14);
        bottomBar.addView(searchBox);

        // Action Buttons
        LinearLayout btnLayout = new LinearLayout(this);
        btnLayout.setOrientation(LinearLayout.HORIZONTAL);
        btnLayout.setPadding(0, 10, 0, 0);

        Button btnCancel = new Button(this);
        btnCancel.setText("CANCEL");
        btnCancel.setBackgroundColor(Color.TRANSPARENT);
        btnCancel.setTextColor(Color.RED);
        btnCancel.setTypeface(android.graphics.Typeface.MONOSPACE);
        btnCancel.setOnClickListener(v -> finish());
        btnLayout.addView(btnCancel);

        View spacer = new View(this);
        spacer.setLayoutParams(new LinearLayout.LayoutParams(0, 1, 1));
        btnLayout.addView(spacer);

        Button btnSave = new Button(this);
        btnSave.setText("SAVE");
        btnSave.setBackgroundColor(Color.TRANSPARENT);
        btnSave.setTextColor(Color.GREEN);
        btnSave.setTypeface(android.graphics.Typeface.MONOSPACE);
        btnSave.setOnClickListener(v -> {
            Toast.makeText(this, "Applying changes...", Toast.LENGTH_SHORT).show();
            if (adapter != null) {
                adapter.saveAll();
            } else if (plainTextEditor != null) {
                try {
                    ohi.andre.consolelauncher.tuils.Tuils.write(file, "", plainTextEditor.getText().toString());
                } catch (Exception e) {
                    Toast.makeText(this, "Error saving: " + e.getMessage(), Toast.LENGTH_LONG).show();
                }
            }
            ohi.andre.consolelauncher.managers.notifications.NotificationService.requestReload(this);
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
            plainTextEditor.setTextColor(Color.WHITE);
            plainTextEditor.setTypeface(android.graphics.Typeface.MONOSPACE);
            plainTextEditor.setBackgroundColor(Color.BLACK);
            plainTextEditor.setTextSize(14);

            try {
                java.io.FileInputStream fis = new java.io.FileInputStream(file);
                plainTextEditor.setText(ohi.andre.consolelauncher.tuils.Tuils.convertStreamToString(fis));
                fis.close();
            } catch (Exception e) {
                plainTextEditor.setText("");
            }

            root.addView(plainTextEditor, 2);
        }

        setContentView(root);
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
}
