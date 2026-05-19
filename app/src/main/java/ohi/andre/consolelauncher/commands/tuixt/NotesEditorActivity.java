package ohi.andre.consolelauncher.commands.tuixt;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.PorterDuff;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

import ohi.andre.consolelauncher.R;
import ohi.andre.consolelauncher.managers.NotesManager;
import ohi.andre.consolelauncher.tuils.LauncherSystemUi;
import ohi.andre.consolelauncher.tuils.Tuils;

public class NotesEditorActivity extends Activity {

    private LinearLayout notesContainer;
    private String originalSnapshot = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        LauncherSystemUi.requestNoTitleIfFullscreen(this);
        super.onCreate(savedInstanceState);
        LauncherSystemUi.applyFullscreen(this);

        List<NotesManager.NoteRecord> records = NotesManager.loadRecords(this);
        originalSnapshot = snapshot(records);

        FrameLayout screen = new FrameLayout(this);
        screen.setBackgroundColor(TuixtTheme.overlayColor());
        screen.setFitsSystemWindows(true);
        FrameLayout contentHost = TuixtLayout.addFoldAwareHost(this, screen, ViewGroup.LayoutParams.MATCH_PARENT);

        FrameLayout panelShell = new FrameLayout(this);
        panelShell.setClipChildren(false);
        panelShell.setClipToPadding(false);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(TuixtTheme.dp(this, 14), TuixtTheme.dp(this, 50), TuixtTheme.dp(this, 14), TuixtTheme.dp(this, 14));
        TuixtTheme.stylePanel(this, root);

        FrameLayout.LayoutParams panelParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        panelParams.gravity = Gravity.CENTER;
        panelParams.setMargins(TuixtTheme.dp(this, 28), TuixtTheme.dp(this, 28), TuixtTheme.dp(this, 28), TuixtTheme.dp(this, 28));
        contentHost.addView(panelShell, panelParams);

        FrameLayout.LayoutParams rootParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        rootParams.topMargin = TuixtTheme.dp(this, 11);
        panelShell.addView(root, rootParams);

        TextView header = new TextView(this);
        header.setText("Notes");
        TuixtTheme.styleHeader(this, header);
        FrameLayout.LayoutParams headerParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        headerParams.gravity = Gravity.TOP | Gravity.START;
        headerParams.leftMargin = TuixtTheme.dp(this, 38);
        panelShell.addView(header, headerParams);

        ContentSizedScrollView scrollView = new ContentSizedScrollView(this);
        scrollView.setFillViewport(false);
        scrollView.setMaxHeight(maxNotesScrollHeight());
        notesContainer = new LinearLayout(this);
        notesContainer.setOrientation(LinearLayout.VERTICAL);
        notesContainer.setPadding(0, 0, 0, TuixtTheme.dp(this, 8));
        scrollView.addView(notesContainer, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(scrollView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        if(records.isEmpty()) {
            addNoteRow(null, true);
        } else {
            for(NotesManager.NoteRecord record : records) {
                addNoteRow(record, false);
            }
        }

        TextView add = button("ADD NOTE", false);
        add.setOnClickListener(v -> addNoteRow(null, true));
        notesContainer.addView(add, addButtonParams());

        LinearLayout bottomBar = new LinearLayout(this);
        bottomBar.setOrientation(LinearLayout.HORIZONTAL);
        bottomBar.setGravity(Gravity.CENTER_VERTICAL);
        bottomBar.setPadding(0, TuixtTheme.dp(this, 10), 0, 0);

        TextView cancel = button("CANCEL", false);
        cancel.setOnClickListener(v -> attemptClose());
        bottomBar.addView(cancel);

        View spacer = new View(this);
        bottomBar.addView(spacer, new LinearLayout.LayoutParams(0, 1, 1));

        TextView save = button("SAVE", true);
        save.setOnClickListener(v -> saveAndClose());
        bottomBar.addView(save);
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
        if(hasFocus) {
            LauncherSystemUi.applyFullscreen(this);
        }
    }

    @Override
    public void onBackPressed() {
        attemptClose();
    }

    private void addNoteRow(NotesManager.NoteRecord record, boolean requestFocus) {
        final NoteRow holder = new NoteRow();
        holder.creationTime = record == null ? 0 : record.creationTime;
        holder.locked = record != null && record.lock;

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.TOP);
        row.setPadding(0, 0, 0, 0);

        EditText editor = new EditText(this);
        editor.setGravity(Gravity.TOP | Gravity.START);
        editor.setMinLines(2);
        editor.setHint("Note");
        editor.setText(record == null ? "" : record.text);
        TuixtTheme.styleInput(this, editor);
        LinearLayout.LayoutParams editorParams = new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1);
        row.addView(editor, editorParams);
        holder.editor = editor;

        ImageButton lock = iconButton(holder.locked ? R.drawable.ic_tuixt_lock_24 : R.drawable.ic_tuixt_lock_open_24, "Toggle lock");
        lock.setOnClickListener(v -> {
            holder.locked = !holder.locked;
            updateLockButton(holder);
        });
        row.addView(lock, iconParams());
        holder.lockButton = lock;

        ImageButton delete = iconButton(R.drawable.ic_tuixt_delete_24, "Delete note");
        delete.setOnClickListener(v -> deleteRow(holder));
        row.addView(delete, iconParams());
        holder.deleteButton = delete;

        holder.row = row;
        row.setTag(holder);
        updateLockButton(holder);

        int insertAt = Math.max(0, notesContainer.getChildCount() - 1);
        notesContainer.addView(row, insertAt, rowParams());

        if(requestFocus) {
            editor.requestFocus();
        }
    }

    private void deleteRow(NoteRow holder) {
        if(holder.locked) {
            Toast.makeText(this, "Unlock note before deleting.", Toast.LENGTH_SHORT).show();
            return;
        }

        notesContainer.removeView(holder.row);
        if(rows().isEmpty()) {
            addNoteRow(null, true);
        }
    }

    private void updateLockButton(NoteRow holder) {
        holder.lockButton.setImageResource(holder.locked ? R.drawable.ic_tuixt_lock_24 : R.drawable.ic_tuixt_lock_open_24);
        holder.lockButton.setColorFilter(holder.locked ? TuixtTheme.accentColor() : TuixtTheme.textColor(), PorterDuff.Mode.SRC_IN);
        holder.lockButton.setContentDescription(holder.locked ? "Unlock note" : "Lock note");
    }

    private ImageButton iconButton(int imageRes, String description) {
        ImageButton button = new ImageButton(this);
        button.setImageResource(imageRes);
        button.setContentDescription(description);
        button.setBackground(TuixtTheme.rect(this, TuixtTheme.surfaceColor(), TuixtTheme.borderColor(), 1.25f));
        button.setPadding(TuixtTheme.dp(this, 8), TuixtTheme.dp(this, 8), TuixtTheme.dp(this, 8), TuixtTheme.dp(this, 8));
        button.setScaleType(ImageButton.ScaleType.CENTER);
        button.setColorFilter(TuixtTheme.textColor(), PorterDuff.Mode.SRC_IN);
        return button;
    }

    private TextView button(String label, boolean primary) {
        TextView view = new TextView(this);
        view.setText(label);
        TuixtTheme.styleButton(this, view, primary);
        return view;
    }

    private LinearLayout.LayoutParams rowParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 0, 0, TuixtTheme.dp(this, 10));
        return params;
    }

    private LinearLayout.LayoutParams iconParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                TuixtTheme.dp(this, 44),
                TuixtTheme.dp(this, 44));
        params.setMargins(TuixtTheme.dp(this, 8), 0, 0, 0);
        return params;
    }

    private LinearLayout.LayoutParams addButtonParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 0, 0, TuixtTheme.dp(this, 10));
        return params;
    }

    private int maxNotesScrollHeight() {
        int screenHeight = getResources().getDisplayMetrics().heightPixels;
        int reserved = TuixtTheme.dp(this, 220);
        int fallback = TuixtTheme.dp(this, 280);
        return Math.max(fallback, screenHeight - reserved);
    }

    private void saveAndClose() {
        try {
            NotesManager.saveRecords(this, currentRecords());
            setResult(TuixtActivity.SAVE_PRESSED, new Intent());
            finish();
        } catch (Exception e) {
            Tuils.log(e);
            Toast.makeText(this, "Save failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void attemptClose() {
        if(!hasUnsavedChanges()) {
            finish();
            return;
        }

        TuixtDialog.showConfirm(
                this,
                "Discard Changes?",
                "Unsaved notes will be lost.",
                "Discard",
                "Keep Editing",
                this::finish);
    }

    private boolean hasUnsavedChanges() {
        return !TextUtils.equals(originalSnapshot, snapshot(currentRecords()));
    }

    private List<NotesManager.NoteRecord> currentRecords() {
        ArrayList<NotesManager.NoteRecord> records = new ArrayList<>();
        for(NoteRow row : rows()) {
            String text = row.editor.getText().toString();
            if(text.trim().length() == 0) {
                continue;
            }
            records.add(new NotesManager.NoteRecord(row.creationTime, text, row.locked));
        }
        return records;
    }

    private List<NoteRow> rows() {
        ArrayList<NoteRow> rows = new ArrayList<>();
        if(notesContainer == null) {
            return rows;
        }
        for(int count = 0; count < notesContainer.getChildCount(); count++) {
            Object tag = notesContainer.getChildAt(count).getTag();
            if(tag instanceof NoteRow) {
                rows.add((NoteRow) tag);
            }
        }
        return rows;
    }

    private String snapshot(List<NotesManager.NoteRecord> records) {
        StringBuilder builder = new StringBuilder();
        for(NotesManager.NoteRecord record : records) {
            if(record == null || record.text == null || record.text.trim().length() == 0) {
                continue;
            }
            builder.append(record.creationTime)
                    .append('|')
                    .append(record.lock)
                    .append('|')
                    .append(record.text)
                    .append('\n');
        }
        return builder.toString();
    }

    private static class NoteRow {
        long creationTime;
        boolean locked;
        LinearLayout row;
        EditText editor;
        ImageButton lockButton;
        ImageButton deleteButton;
    }

    private static class ContentSizedScrollView extends ScrollView {
        private int maxHeight;

        ContentSizedScrollView(Context context) {
            super(context);
        }

        public ContentSizedScrollView(Context context, AttributeSet attrs) {
            super(context, attrs);
        }

        void setMaxHeight(int maxHeight) {
            this.maxHeight = maxHeight;
            requestLayout();
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            if(maxHeight > 0) {
                int heightMode = MeasureSpec.getMode(heightMeasureSpec);
                int heightSize = MeasureSpec.getSize(heightMeasureSpec);
                int constrainedHeight = heightMode == MeasureSpec.UNSPECIFIED
                        ? maxHeight
                        : Math.min(heightSize, maxHeight);
                heightMeasureSpec = MeasureSpec.makeMeasureSpec(constrainedHeight, MeasureSpec.AT_MOST);
            }
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        }
    }
}
