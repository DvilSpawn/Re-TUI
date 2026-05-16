package ohi.andre.consolelauncher.commands.tuixt;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import java.io.File;
import java.util.Arrays;

import ohi.andre.consolelauncher.UIManager;
import ohi.andre.consolelauncher.managers.modules.ModuleManager;
import ohi.andre.consolelauncher.managers.settings.LauncherSettings;
import ohi.andre.consolelauncher.managers.widgets.LuaWidgetManager;
import ohi.andre.consolelauncher.managers.xml.XMLPrefsManager;
import ohi.andre.consolelauncher.tuils.LauncherSystemUi;
import ohi.andre.consolelauncher.tuils.Tuils;

public class WidgetEditorActivity extends Activity {

    public static final String EXTRA_WIDGET_ID = "widget_id";
    public static final String EXTRA_FILE_PATH = "file_path";

    private boolean widgetMode = true;
    private String widgetId;
    private File documentFile;
    private TextView header;
    private EditText documentNameEditor;
    private EditText codeEditor;
    private TextView capabilityView;
    private String originalDocumentName = "";
    private String originalCode = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        LauncherSystemUi.requestNoTitleIfFullscreen(this);
        super.onCreate(savedInstanceState);
        LauncherSystemUi.applyFullscreen(this);

        Intent intent = getIntent();
        String filePath = intent.getStringExtra(EXTRA_FILE_PATH);
        widgetMode = TextUtils.isEmpty(filePath);

        if (widgetMode) {
            widgetId = LuaWidgetManager.normalizeId(intent.getStringExtra(EXTRA_WIDGET_ID));
            if (TextUtils.isEmpty(widgetId)) {
                finish();
                return;
            }

            originalDocumentName = LuaWidgetManager.getName(widgetId);
            originalCode = LuaWidgetManager.readScript(widgetId);
            if (TextUtils.isEmpty(originalCode)) {
                originalCode = LuaWidgetManager.newWidgetTemplate(widgetId);
            }
        } else {
            documentFile = new File(filePath);
            if (documentFile.isDirectory()) {
                finish();
                return;
            }
            originalDocumentName = documentFile.getName();
            try (java.io.FileInputStream in = new java.io.FileInputStream(documentFile)) {
                originalCode = Tuils.convertStreamToString(in);
            } catch (Exception e) {
                originalCode = "";
            }
        }

        if (TextUtils.isEmpty(originalDocumentName)) {
            originalDocumentName = widgetMode ? widgetId : "Document";
        }

        if (widgetMode && TextUtils.isEmpty(originalCode)) {
            originalCode = LuaWidgetManager.newWidgetTemplate(widgetId);
        }

        if (!widgetMode && documentFile == null) {
            finish();
            return;
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
        updateHeader();
        TuixtTheme.styleHeader(this, header);
        FrameLayout.LayoutParams headerParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        headerParams.gravity = Gravity.TOP | Gravity.START;
        headerParams.leftMargin = panelLeft + TuixtTheme.dp(this, 38);
        headerParams.topMargin = panelTop - TuixtTheme.dp(this, 11);
        screen.addView(header, headerParams);

        documentNameEditor = new EditText(this);
        documentNameEditor.setSingleLine(true);
        documentNameEditor.setHint(widgetMode ? "Document name" : "File name");
        documentNameEditor.setText(originalDocumentName);
        if (!widgetMode) {
            documentNameEditor.setFocusable(false);
            documentNameEditor.setFocusableInTouchMode(false);
            documentNameEditor.setCursorVisible(false);
        }
        TuixtTheme.styleInput(this, documentNameEditor);
        LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        nameParams.setMargins(0, 0, 0, TuixtTheme.dp(this, 10));
        root.addView(documentNameEditor, nameParams);

        capabilityView = new TextView(this);
        capabilityView.setSingleLine(false);
        capabilityView.setTextSize(11);
        capabilityView.setTypeface(Tuils.getTypeface(this));
        capabilityView.setTextColor(TuixtTheme.textColor());
        capabilityView.setAlpha(widgetMode ? 0.82f : 0.55f);
        LinearLayout.LayoutParams capabilityParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        capabilityParams.setMargins(0, 0, 0, TuixtTheme.dp(this, 10));
        root.addView(capabilityView, capabilityParams);

        codeEditor = new EditText(this);
        codeEditor.setGravity(Gravity.TOP | Gravity.START);
        codeEditor.setSingleLine(false);
        codeEditor.setHorizontallyScrolling(true);
        codeEditor.setTypeface(Typeface.MONOSPACE);
        codeEditor.setTextSize(13);
        codeEditor.setText(originalCode);
        TuixtTheme.styleInput(this, codeEditor);
        root.addView(codeEditor, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1));
        codeEditor.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                updateCapabilityPreview();
            }
            @Override public void afterTextChanged(Editable s) {}
        });
        documentNameEditor.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                updateCapabilityPreview();
            }
            @Override public void afterTextChanged(Editable s) {}
        });
        updateCapabilityPreview();

        LinearLayout bottomBar = new LinearLayout(this);
        bottomBar.setOrientation(LinearLayout.HORIZONTAL);
        bottomBar.setGravity(Gravity.CENTER_VERTICAL);
        bottomBar.setPadding(0, TuixtTheme.dp(this, 10), 0, 0);

        TextView cancel = button("CANCEL", false);
        cancel.setOnClickListener(v -> attemptClose());
        bottomBar.addView(cancel);

        View spacer = new View(this);
        bottomBar.addView(spacer, new LinearLayout.LayoutParams(0, 1, 1));

        TextView save = button("SAVE", false);
        save.setOnClickListener(v -> save(false));
        bottomBar.addView(save);

        if (widgetMode) {
            TextView run = button("SAVE/RUN", true);
            run.setOnClickListener(v -> save(true));
            bottomBar.addView(run);
        }

        root.addView(bottomBar);
        setContentView(screen);
    }

    @Override
    protected void onResume() {
        super.onResume();
        LauncherSystemUi.applyFullscreen(this);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            LauncherSystemUi.applyFullscreen(this);
        }
    }

    @Override
    public void onBackPressed() {
        attemptClose();
    }

    private TextView button(String label, boolean primary) {
        TextView view = new TextView(this);
        view.setText(label);
        TuixtTheme.styleButton(this, view, primary);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(TuixtTheme.dp(this, 6), 0, 0, 0);
        view.setLayoutParams(params);
        return view;
    }

    private void save(boolean run) {
        try {
            String code = codeEditor.getText().toString();

            if (!widgetMode) {
                Tuils.write(documentFile, "", code);
                XMLPrefsManager.dispose();
                XMLPrefsManager.loadCommons(this);
                LauncherSettings.refreshFromLoadedPrefs();
                setResult(TuixtActivity.SAVE_PRESSED);
                finish();
                return;
            }

            String name = documentNameEditor.getText().toString().trim();
            if (TextUtils.isEmpty(name)) {
                throw new IllegalArgumentException("Document name is required");
            }
            if (!TextUtils.equals(originalDocumentName, name)) {
                String newId = LuaWidgetManager.idFromName(name);
                if (TextUtils.isEmpty(newId)) {
                    throw new IllegalArgumentException("Document name needs letters or numbers");
                }
                if (!TextUtils.equals(widgetId, newId)) {
                    if (ModuleManager.isKnown(this, newId) || LuaWidgetManager.exists(newId)) {
                        throw new IllegalArgumentException("Widget id already exists: " + newId);
                    }
                    LuaWidgetManager.rename(widgetId, newId);
                    ModuleManager.renameScriptModule(this, widgetId, newId, LuaWidgetManager.SOURCE_PREFIX + newId);
                    widgetId = newId;
                }
            }

            LuaWidgetManager.save(widgetId, name, code);
            boolean dockable = LuaWidgetManager.isDockableScript(code);
            if (dockable) {
                ModuleManager.setScriptModule(this, widgetId, LuaWidgetManager.SOURCE_PREFIX + widgetId);
                ModuleManager.addToDock(this, Arrays.asList(widgetId));
            } else {
                ModuleManager.removeScriptModule(this, widgetId);
            }
            sendModule("rebuild");
            if (run && dockable) {
                sendModule("show");
                sendModule("refresh");
                finish();
                return;
            } else if (run) {
                Toast.makeText(this, "Suggestion script saved: " + name, Toast.LENGTH_SHORT).show();
            }

            originalDocumentName = LuaWidgetManager.getName(widgetId);
            originalCode = code;
            documentNameEditor.setText(originalDocumentName);
            updateHeader();
            Toast.makeText(this, "Document saved: " + originalDocumentName, Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "Save failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void sendModule(String command) {
        Intent intent = new Intent(UIManager.ACTION_MODULE_COMMAND);
        intent.putExtra(UIManager.EXTRA_MODULE_COMMAND, command);
        intent.putExtra(UIManager.EXTRA_MODULE_NAME, widgetId);
        LocalBroadcastManager.getInstance(getApplicationContext()).sendBroadcast(intent);
    }

    private void updateHeader() {
        if (header != null) {
            header.setText(widgetMode ? "Widgets" : "Documents");
        }
    }

    private void updateCapabilityPreview() {
        if (capabilityView == null) {
            return;
        }
        if (!widgetMode) {
            capabilityView.setText("Document editor");
            return;
        }

        String code = codeEditor == null ? "" : codeEditor.getText().toString();
        java.util.Map<String, String> meta = LuaWidgetManager.metadata(code);
        String type = LuaWidgetManager.getScriptTypeFromScript(code);
        String capabilities = LuaWidgetManager.describeCapabilities(code);
        String permissions = LuaWidgetManager.describeRequiredPermissions(code);
        String api = LuaWidgetManager.apiVersionFromScript(code);
        String name = documentNameEditor == null ? "" : documentNameEditor.getText().toString().trim();
        String id = LuaWidgetManager.idFromName(name);
        capabilityView.setText("Type: " + type
                + "  |  ID: " + (TextUtils.isEmpty(id) ? "n/a" : id)
                + "  |  API: " + api
                + "  |  Capabilities: " + capabilities
                + "  |  Permissions: " + permissions
                + capabilityWarning(meta, code));
    }

    private String capabilityWarning(java.util.Map<String, String> meta, String code) {
        String declared = meta.get("permissions");
        java.util.List<String> missing = LuaWidgetManager.missingPermissionDeclarations(code);
        java.util.List<String> unsupported = LuaWidgetManager.unsupportedPermissions(code);
        if (!unsupported.isEmpty()) {
            return "  |  Unsupported: " + TextUtils.join(", ", unsupported);
        }
        if (!missing.isEmpty()) {
            return "  |  Declare: " + TextUtils.join(", ", missing);
        }
        if (TextUtils.isEmpty(declared)) {
            return "  |  Metadata: inferred";
        }
        return "  |  Metadata: " + declared;
    }

    private void attemptClose() {
        if (!hasUnsavedChanges()) {
            finish();
            return;
        }
        TuixtDialog.showConfirm(
                this,
                "Discard Changes?",
                "Unsaved document changes will be lost.",
                "Discard",
                "Keep Editing",
                this::finish);
    }

    private boolean hasUnsavedChanges() {
        return !TextUtils.equals(originalDocumentName, documentNameEditor.getText().toString().trim())
                || !TextUtils.equals(originalCode, codeEditor.getText().toString());
    }
}
