package ohi.andre.consolelauncher.tuils;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import ohi.andre.consolelauncher.managers.settings.AppearanceSettings;
import ohi.andre.consolelauncher.managers.xml.XMLPrefsManager;
import ohi.andre.consolelauncher.managers.xml.options.Theme;

public class TuixtWindow extends FrameLayout {

    private String title;
    private FrameLayout contentContainer;
    private TextView label;
    private LinearLayout panel;

    public TuixtWindow(Context context) {
        super(context);
        init(context);
    }

    public TuixtWindow(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    private void init(Context context) {
        int padding = (int) UIUtils.dpToPx(context, 18);
        setPadding(padding, padding, padding, padding);

        panel = new LinearLayout(context);
        panel.setOrientation(LinearLayout.VERTICAL);
        
        int panelPaddingHorizontal = (int) UIUtils.dpToPx(context, 14);
        int panelPaddingTop = (int) UIUtils.dpToPx(context, 32);
        int panelPaddingBottom = (int) UIUtils.dpToPx(context, 14);
        panel.setPadding(panelPaddingHorizontal, panelPaddingTop, panelPaddingHorizontal, panelPaddingBottom);

        updateStyle();

        contentContainer = new FrameLayout(context);
        panel.addView(contentContainer, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        addView(panel, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        label = new TextView(context);
        label.setTypeface(Tuils.getTypeface(context));
        label.setTextSize(14);
        label.setPadding((int) UIUtils.dpToPx(context, 8), 0, (int) UIUtils.dpToPx(context, 8), 0);
        
        FrameLayout.LayoutParams labelParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        labelParams.gravity = Gravity.TOP | Gravity.LEFT;
        labelParams.leftMargin = (int) UIUtils.dpToPx(context, 42);
        addView(label, labelParams);
    }

    public void updateStyle() {
        Context context = getContext();
        int accentColor = XMLPrefsManager.getColor(Theme.output_color);
        int borderColor = AppearanceSettings.terminalBorderColor();
        int bgColor = AppearanceSettings.terminalWindowBackground();
        int headerBgColor = AppearanceSettings.terminalHeaderBackground();

        GradientDrawable border = new GradientDrawable();
        border.setColor(bgColor);
        border.setCornerRadius(UIUtils.dpToPx(context, AppearanceSettings.outputCornerRadius()));
        if (AppearanceSettings.dashedBorders()) {
            border.setStroke((int) UIUtils.dpToPx(context, AppearanceSettings.dashedBorderStrokeWidthDp(1.33f)), borderColor,
                    UIUtils.dpToPx(context, AppearanceSettings.dashLength()),
                    UIUtils.dpToPx(context, AppearanceSettings.dashGap()));
        }
        panel.setBackground(border);

        if (label != null) {
            label.setTextColor(accentColor);
            label.setBackgroundColor(headerBgColor);
            if (title != null) {
                label.setText("[ " + title.toUpperCase() + " ]");
            }
        }
    }

    public void setTitle(String title) {
        this.title = title;
        if (label != null) {
            label.setText("[ " + title.toUpperCase() + " ]");
        }
    }

    public void setContent(View view) {
        contentContainer.removeAllViews();
        contentContainer.addView(view);
    }
    
    public ViewGroup getContentContainer() {
        return contentContainer;
    }
}
