package ohi.andre.consolelauncher.commands.tuixt;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.List;

final class TuixtDialog {

    interface ItemAction {
        void onItemSelected(int index);
    }

    interface InputAction {
        void onInput(String value);
    }

    interface ConfirmAction {
        void onConfirm();
    }

    private TuixtDialog() {
    }

    static void showOptions(Context context, String title, List<String> items, ItemAction action) {
        Dialog dialog = createDialog(context);
        LinearLayout content = createContent(context);

        for (int i = 0; i < items.size(); i++) {
            int index = i;
            TextView row = new TextView(context);
            row.setText(items.get(i).toUpperCase());
            TuixtTheme.styleListItem(context, row, false);
            LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            rowParams.bottomMargin = TuixtTheme.dp(context, 8);
            content.addView(row, rowParams);
            row.setOnClickListener(v -> {
                dialog.dismiss();
                action.onItemSelected(index);
            });
        }

        dialog.setContentView(wrap(context, title, content, null));
        show(dialog);
    }

    static void showInput(Context context, String title, String hint, String positive, String negative, InputAction action) {
        Dialog dialog = createDialog(context);
        LinearLayout content = createContent(context);

        EditText input = new EditText(context);
        input.setHint(hint);
        TuixtTheme.styleInput(context, input);
        content.addView(input, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout buttons = buttons(context, dialog, positive, negative, () -> action.onInput(input.getText().toString()));
        dialog.setContentView(wrap(context, title, content, buttons));
        show(dialog);
    }

    static void showConfirm(Context context, String title, String message, String positive, String negative, ConfirmAction action) {
        Dialog dialog = createDialog(context);
        LinearLayout content = createContent(context);

        TextView messageView = new TextView(context);
        messageView.setText(message);
        messageView.setTextColor(TuixtTheme.textColor());
        messageView.setTypeface(ohi.andre.consolelauncher.tuils.Tuils.getTypeface(context));
        messageView.setTextSize(14);
        messageView.setPadding(
                TuixtTheme.dp(context, 10),
                TuixtTheme.dp(context, 12),
                TuixtTheme.dp(context, 10),
                TuixtTheme.dp(context, 12));
        content.addView(messageView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout buttons = buttons(context, dialog, positive, negative, action);
        dialog.setContentView(wrap(context, title, content, buttons));
        show(dialog);
    }

    static void showContent(Context context, String title, View customContent, String positive, String negative, ConfirmAction action) {
        Dialog dialog = createDialog(context);
        LinearLayout content = createContent(context);
        content.addView(customContent, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout buttons = buttons(context, dialog, positive, negative, action);
        dialog.setContentView(wrap(context, title, content, buttons));
        show(dialog);
    }

    private static Dialog createDialog(Context context) {
        Dialog dialog = new Dialog(context);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        return dialog;
    }

    private static LinearLayout createContent(Context context) {
        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(0, 0, 0, 0);
        return content;
    }

    private static View wrap(Context context, String title, View content, View buttons) {
        FrameLayout frame = new FrameLayout(context);
        frame.setPadding(
                TuixtTheme.dp(context, 18),
                TuixtTheme.dp(context, 18),
                TuixtTheme.dp(context, 18),
                TuixtTheme.dp(context, 18));

        LinearLayout panel = new LinearLayout(context);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(
                TuixtTheme.dp(context, 14),
                TuixtTheme.dp(context, 36),
                TuixtTheme.dp(context, 14),
                TuixtTheme.dp(context, 14));
        TuixtTheme.stylePanel(context, panel);

        ScrollView scrollView = new ScrollView(context);
        scrollView.setFillViewport(false);
        scrollView.addView(content, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        panel.addView(scrollView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        if (buttons != null) {
            panel.addView(buttons);
        }

        frame.addView(panel, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER));

        TextView header = new TextView(context);
        header.setText(title.toUpperCase());
        TuixtTheme.styleHeader(context, header);
        FrameLayout.LayoutParams headerParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        headerParams.gravity = Gravity.TOP | Gravity.START;
        headerParams.leftMargin = TuixtTheme.dp(context, 54);
        headerParams.topMargin = TuixtTheme.dp(context, 6);
        frame.addView(header, headerParams);

        return frame;
    }

    private static LinearLayout buttons(Context context, Dialog dialog, String positive, String negative, ConfirmAction action) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, TuixtTheme.dp(context, 12), 0, 0);

        TextView cancel = new TextView(context);
        cancel.setText(negative.toUpperCase());
        TuixtTheme.styleButton(context, cancel, false);
        cancel.setOnClickListener(v -> dialog.dismiss());
        row.addView(cancel, new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1));

        View spacer = new View(context);
        row.addView(spacer, new LinearLayout.LayoutParams(TuixtTheme.dp(context, 12), 1));

        TextView confirm = new TextView(context);
        confirm.setText(positive.toUpperCase());
        TuixtTheme.styleButton(context, confirm, true);
        confirm.setOnClickListener(v -> {
            dialog.dismiss();
            action.onConfirm();
        });
        row.addView(confirm, new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1));

        return row;
    }

    private static void show(Dialog dialog) {
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setDimAmount(0f);
            WindowManager.LayoutParams params = new WindowManager.LayoutParams();
            params.copyFrom(window.getAttributes());
            params.width = WindowManager.LayoutParams.MATCH_PARENT;
            params.height = WindowManager.LayoutParams.WRAP_CONTENT;
            window.setAttributes(params);
        }
    }
}
