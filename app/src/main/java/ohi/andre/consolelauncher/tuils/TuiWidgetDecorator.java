package ohi.andre.consolelauncher.tuils;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.widget.TextView;

import androidx.core.content.res.ResourcesCompat;
import androidx.core.graphics.ColorUtils;

import ohi.andre.consolelauncher.R;
import ohi.andre.consolelauncher.managers.settings.AppearanceSettings;

/**
 * Centralizes the styling of TUI widgets (Music, Notifications, etc.)
 * to ensure visual consistency and reduce UIManager bloat.
 */
public class TuiWidgetDecorator {

    public static void decorateWidget(View widgetRoot, int borderViewId, int labelViewId) {
        decorateWidget(widgetRoot, borderViewId, labelViewId,
                AppearanceSettings.musicWidgetBorderColor(),
                AppearanceSettings.musicWidgetTextColor());
    }

    public static void decorateWidget(View widgetRoot, int borderViewId, int labelViewId, int borderColor, int textColor) {
        if (widgetRoot == null) return;

        Context context = widgetRoot.getContext();
        int widgetBgColor = AppearanceSettings.terminalWindowBackground();
        int labelMaskColor = AppearanceSettings.terminalHeaderBackground();
        boolean useDashed = AppearanceSettings.dashedBorders();

        // 1. Decorate Border
        View borderView = widgetRoot.findViewById(borderViewId);
        if (borderView != null) {
            borderView.setBackground(panelDrawable(
                    context,
                    widgetBgColor,
                    AppearanceSettings.terminalBorderColor(),
                    1.5f,
                    AppearanceSettings.moduleCornerRadius(),
                    useDashed));
        }

        // 2. Decorate Label
        TextView widgetLabel = widgetRoot.findViewById(labelViewId);
        if (widgetLabel != null) {
            widgetLabel.setTextColor(textColor);
            widgetLabel.setTypeface(Tuils.getTypeface(context), Typeface.BOLD);
            widgetLabel.setTextSize(AppearanceSettings.moduleHeaderTextSize());
            try {
                GradientDrawable gd = (GradientDrawable) ResourcesCompat.getDrawable(
                        context.getResources(), R.drawable.apps_drawer_header_border, null);
                if (gd != null) {
                    gd = (GradientDrawable) gd.mutate();
                    gd.setCornerRadius(Tuils.dpToPx(context, AppearanceSettings.headerCornerRadius()));
                    if (useDashed) {
                        gd.setStroke(dashedStrokePx(context, 1f), AppearanceSettings.terminalBorderColor(),
                                Tuils.dpToPx(context, AppearanceSettings.dashLength()),
                                Tuils.dpToPx(context, AppearanceSettings.dashGap()));
                    } else {
                        gd.setStroke(0, Color.TRANSPARENT);
                    }
                    gd.setColor(labelMaskColor);
                    widgetLabel.setBackground(gd);
                }
            } catch (Exception ignored) {}
        }
    }

    public static GradientDrawable getRowBackground(Context context) {
        return getRowBackground(context, AppearanceSettings.notificationWidgetBorderColor());
    }

    public static GradientDrawable getRowBackground(Context context, int borderColor) {
        int widgetBgColor = AppearanceSettings.terminalWindowBackground();
        int rowBackground = ColorUtils.blendARGB(widgetBgColor, Color.BLACK, 0.22f);
        int strokeColor = ColorUtils.setAlphaComponent(AppearanceSettings.terminalBorderColor(), 140);

        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setCornerRadius(Tuils.dpToPx(context, AppearanceSettings.moduleCornerRadius()));
        bg.setColor(rowBackground);
        if (AppearanceSettings.dashedBorders()) {
            bg.setStroke(dashedStrokePx(context, 0.8f), strokeColor,
                    Tuils.dpToPx(context, AppearanceSettings.dashLength()),
                    Tuils.dpToPx(context, AppearanceSettings.dashGap()));
        }
        return bg;
    }

    public static GradientDrawable panelDrawable(Context context, int fillColor, int borderColor,
                                                 float strokeDp, int radiusDp, boolean dashed) {
        GradientDrawable gd = new GradientDrawable();
        gd.setShape(GradientDrawable.RECTANGLE);
        gd.setCornerRadius(Tuils.dpToPx(context, radiusDp));
        gd.setColor(fillColor);
        if (dashed) {
            gd.setStroke(dashedStrokePx(context, strokeDp / 1.5f), borderColor,
                    Tuils.dpToPx(context, AppearanceSettings.dashLength()),
                    Tuils.dpToPx(context, AppearanceSettings.dashGap()));
        }
        return gd;
    }

    private static int dashedStrokePx(Context context, float scale) {
        return Math.max(1, (int) Tuils.dpToPx(context, AppearanceSettings.dashedBorderStrokeWidthDp(scale)));
    }
}
