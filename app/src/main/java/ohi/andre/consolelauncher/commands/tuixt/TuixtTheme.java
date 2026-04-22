package ohi.andre.consolelauncher.commands.tuixt;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import androidx.core.graphics.ColorUtils;

import ohi.andre.consolelauncher.managers.settings.AppearanceSettings;
import ohi.andre.consolelauncher.managers.settings.LauncherSettings;
import ohi.andre.consolelauncher.managers.xml.options.Theme;
import ohi.andre.consolelauncher.tuils.Tuils;

final class TuixtTheme {

    private TuixtTheme() {
    }

    static int borderColor() {
        return LauncherSettings.getColor(Theme.input_color);
    }

    static int accentColor() {
        return LauncherSettings.getColor(Theme.apps_drawer_color);
    }

    static int textColor() {
        return LauncherSettings.getColor(Theme.output_color);
    }

    static int surfaceColor() {
        return AppearanceSettings.terminalWindowBackground();
    }

    static int overlayColor() {
        return ColorUtils.setAlphaComponent(0xFF000000, 96);
    }

    static void stylePanel(Context context, View view) {
        view.setBackground(rect(context, surfaceColor(), borderColor(), 1.5f));
    }

    static void styleHeader(Context context, TextView view) {
        view.setTextColor(accentColor());
        view.setTypeface(Tuils.getTypeface(context), Typeface.BOLD);
        view.setTextSize(15);
        view.setGravity(android.view.Gravity.CENTER);
        view.setPadding(dp(context, 12), dp(context, 3), dp(context, 12), dp(context, 3));
        view.setBackground(rect(context, surfaceColor(), borderColor(), 1.5f));
    }

    static void styleListItem(Context context, TextView view, boolean selected) {
        int fill = selected ? selectionColor() : ColorUtils.setAlphaComponent(surfaceColor(), 210);
        int text = selected ? surfaceColor() : textColor();
        view.setTextColor(text);
        view.setTypeface(Tuils.getTypeface(context), Typeface.BOLD);
        view.setTextSize(15);
        view.setGravity(android.view.Gravity.CENTER_VERTICAL);
        view.setPadding(dp(context, 14), dp(context, 12), dp(context, 14), dp(context, 12));
        view.setMinHeight(dp(context, 48));
        view.setBackground(rect(context, fill, borderColor(), 1.25f));
    }

    static void styleInput(Context context, EditText view) {
        view.setTextColor(textColor());
        view.setHintTextColor(ColorUtils.setAlphaComponent(textColor(), 150));
        view.setTypeface(Tuils.getTypeface(context), Typeface.BOLD);
        view.setTextSize(13);
        view.setSingleLine(false);
        view.setPadding(dp(context, 10), dp(context, 8), dp(context, 10), dp(context, 8));
        view.setBackground(rect(context, ColorUtils.setAlphaComponent(surfaceColor(), 220), borderColor(), 1.25f));
    }

    static void styleButton(Context context, TextView view, boolean primary) {
        view.setTextColor(primary ? surfaceColor() : textColor());
        view.setTypeface(Tuils.getTypeface(context), Typeface.BOLD);
        view.setTextSize(13);
        view.setGravity(android.view.Gravity.CENTER);
        view.setPadding(dp(context, 14), dp(context, 8), dp(context, 14), dp(context, 8));
        view.setBackground(rect(context, primary ? selectionColor() : surfaceColor(), borderColor(), 1.25f));
    }

    static void styleToggle(Context context, TextView view, boolean checked) {
        view.setText(checked ? "ON" : "OFF");
        view.setTextColor(checked ? surfaceColor() : textColor());
        view.setTypeface(Tuils.getTypeface(context), Typeface.BOLD);
        view.setTextSize(13);
        view.setGravity(android.view.Gravity.CENTER);
        view.setPadding(dp(context, 18), dp(context, 9), dp(context, 18), dp(context, 9));
        view.setMinWidth(dp(context, 76));
        view.setBackground(rect(context, checked ? selectionColor() : surfaceColor(), borderColor(), 1.25f));
    }

    static void styleColorPreview(Context context, View view, int color) {
        GradientDrawable drawable = rect(context, color, borderColor(), 1.25f);
        view.setBackground(drawable);
    }

    static GradientDrawable rect(Context context, int fill, int stroke, float strokeDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.RECTANGLE);
        drawable.setCornerRadius(dp(context, 1));
        if (AppearanceSettings.dashedBorders()) {
            drawable.setStroke(
                    Math.max(1, dp(context, strokeDp)),
                    stroke,
                    Tuils.dpToPx(context, AppearanceSettings.dashLength()),
                    Tuils.dpToPx(context, AppearanceSettings.dashGap()));
        } else {
            drawable.setStroke(Math.max(1, dp(context, strokeDp)), stroke);
        }
        drawable.setColor(fill);
        return drawable;
    }

    static int dp(Context context, float value) {
        return (int) Tuils.dpToPx(context, value);
    }

    private static int selectionColor() {
        return ColorUtils.blendARGB(accentColor(), 0xFFFFFFFF, 0.42f);
    }
}
