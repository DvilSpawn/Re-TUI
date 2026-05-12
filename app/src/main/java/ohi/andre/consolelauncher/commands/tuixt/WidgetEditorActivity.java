package ohi.andre.consolelauncher.commands.tuixt;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import java.util.Arrays;

import ohi.andre.consolelauncher.UIManager;
import ohi.andre.consolelauncher.managers.modules.ModuleManager;
import ohi.andre.consolelauncher.managers.widgets.LuaWidgetManager;
import ohi.andre.consolelauncher.tuils.Tuils;

public class WidgetEditorActivity extends Activity {

    public static final String EXTRA_WIDGET_ID = "widget_id";

    private String widgetId;
    private EditText nameEditor;
    private EditText codeEditor;
    private String originalName = "";
    private String originalCode = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        widgetId = LuaWidgetManager.normalizeId(getIntent().getStringExtra(EXTRA_WIDGET_ID));
        if (TextUtils.isEmpty(widgetId)) {
            finish();
            return;
        }

        originalName = LuaWidgetManager.getName(widgetId);
        originalCode = LuaWidgetManager.readScript(widgetId);
        if (TextUtils.isEmpty(originalCode)) {
            originalCode = LuaWidgetManager.newWidgetTemplate(widgetId);
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

        TextView header = new TextView(this);
        header.setText("Widgets/ " + widgetId);
        TuixtTheme.styleHeader(this, header);
        FrameLayout.LayoutParams headerParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        headerParams.gravity = Gravity.TOP | Gravity.START;
        headerParams.leftMargin = panelLeft + TuixtTheme.dp(this, 38);
        headerParams.topMargin = panelTop - TuixtTheme.dp(this, 11);
        screen.addView(header, headerParams);

        nameEditor = new EditText(this);
        nameEditor.setSingleLine(true);
        nameEditor.setHint("Widget name");
        nameEditor.setText(originalName);
        TuixtTheme.styleInput(this, nameEditor);
        LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        nameParams.setMargins(0, 0, 0, TuixtTheme.dp(this, 10));
        root.addView(nameEditor, nameParams);

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

        TextView run = button("SAVE/RUN", true);
        run.setOnClickListener(v -> save(true));
        bottomBar.addView(run);

        root.addView(bottomBar);
        setContentView(screen);
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
            String name = nameEditor.getText().toString().trim();
            String code = codeEditor.getText().toString();
            LuaWidgetManager.save(widgetId, name, code);
            ModuleManager.setScriptModule(this, widgetId, LuaWidgetManager.SOURCE_PREFIX + widgetId);
            ModuleManager.addToDock(this, Arrays.asList(widgetId));
            sendModule("rebuild");
            if (run) {
                sendModule("show");
                sendModule("refresh");
                finish();
            } else {
                originalName = LuaWidgetManager.getName(widgetId);
                originalCode = code;
                Toast.makeText(this, "Widget saved: " + widgetId, Toast.LENGTH_SHORT).show();
            }
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

    private void attemptClose() {
        if (!hasUnsavedChanges()) {
            finish();
            return;
        }
        TuixtDialog.showConfirm(
                this,
                "Discard Widget?",
                "Unsaved widget changes will be lost.",
                "Discard",
                "Keep Editing",
                this::finish);
    }

    private boolean hasUnsavedChanges() {
        return !TextUtils.equals(originalName, nameEditor.getText().toString().trim())
                || !TextUtils.equals(originalCode, codeEditor.getText().toString());
    }
}
