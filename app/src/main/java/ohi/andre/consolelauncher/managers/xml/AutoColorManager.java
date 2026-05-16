package ohi.andre.consolelauncher.managers.xml;

import android.app.WallpaperColors;
import android.app.WallpaperManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Build;

import androidx.annotation.RequiresApi;
import androidx.core.graphics.ColorUtils;

import ohi.andre.consolelauncher.managers.settings.AppearanceSettings;
import ohi.andre.consolelauncher.managers.xml.classes.XMLPrefsSave;
import ohi.andre.consolelauncher.managers.xml.options.Suggestions;
import ohi.andre.consolelauncher.managers.xml.options.Theme;

public final class AutoColorManager {

    private static final int NO_OVERRIDE = Integer.MAX_VALUE;

    private static Context appContext;
    private static Palette cachedPalette;

    private AutoColorManager() {}

    public static void init(Context context) {
        if (context != null) {
            appContext = context.getApplicationContext();
        }
    }

    public static void invalidate() {
        cachedPalette = null;
    }

    public static int getColor(XMLPrefsSave prefsSave, int fallbackColor) {
        if (!AppearanceSettings.autoColorPick()) {
            return fallbackColor;
        }
        return getAutoColor(prefsSave, fallbackColor);
    }

    public static int getAutoColor(XMLPrefsSave prefsSave, int fallbackColor) {
        if (prefsSave == null || appContext == null) {
            return fallbackColor;
        }

        Palette palette = getPalette();
        if (palette == null) {
            return fallbackColor;
        }

        if (prefsSave instanceof Theme) {
            return resolveThemeColor((Theme) prefsSave, palette, fallbackColor);
        } else if (prefsSave instanceof Suggestions) {
            return resolveSuggestionsColor((Suggestions) prefsSave, palette, fallbackColor);
        }

        return fallbackColor;
    }

    private static Palette getPalette() {
        if (cachedPalette == null) {
            cachedPalette = buildPalette();
        }
        return cachedPalette;
    }

    private static Palette buildPalette() {
        try {
            WallpaperManager wallpaperManager = WallpaperManager.getInstance(appContext);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                WallpaperColors colors = wallpaperManager.getWallpaperColors(WallpaperManager.FLAG_SYSTEM);
                if (colors != null) {
                    return buildFromWallpaperColors(colors);
                }
            }

            Drawable drawable = wallpaperManager.getDrawable();
            if (drawable == null) {
                return null;
            }

            Bitmap bitmap = Bitmap.createBitmap(48, 48, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
            drawable.draw(canvas);

            return buildFromBitmap(bitmap);
        } catch (Exception ignored) {
            return null;
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.O_MR1)
    private static Palette buildFromWallpaperColors(WallpaperColors colors) {
        int primary = colors.getPrimaryColor() != null ? colors.getPrimaryColor().toArgb() : Color.BLACK;
        int secondary = colors.getSecondaryColor() != null ? colors.getSecondaryColor().toArgb() : primary;
        int tertiary = colors.getTertiaryColor() != null ? colors.getTertiaryColor().toArgb() : secondary;

        int base = darkestOf(primary, secondary, tertiary);
        int accentSeed = mostSaturatedOf(primary, secondary, tertiary);
        int background = clampLuminance(base, 0.14f);
        int surface = ColorUtils.blendARGB(background, accentSeed, 0.18f);
        int surfaceStrong = ColorUtils.blendARGB(background, accentSeed, 0.28f);
        int accent = ensureReadable(accentSeed, background, 4.2f);
        int text = readableTextFor(background);
        int mutedText = ColorUtils.blendARGB(text, accent, 0.28f);
        int overlay = Color.argb(196, Color.red(background), Color.green(background), Color.blue(background));

        return new Palette(background, surface, surfaceStrong, accent, text, mutedText, overlay,
                buildSuggestionStyle(accent, background, 0f, 0.96f, 0.04f),
                buildSuggestionStyle(accent, background, 18f, 0.82f, 0.10f),
                buildSuggestionStyle(accent, background, -18f, 1.12f, -0.02f),
                buildSuggestionStyle(accent, background, 34f, 0.78f, 0.18f),
                buildSuggestionStyle(accent, background, -34f, 0.88f, 0.20f),
                buildSuggestionStyle(accent, background, 52f, 0.74f, 0.08f),
                buildSuggestionStyle(accent, background, -8f, 0.60f, 0.14f));
    }

    private static Palette buildFromBitmap(Bitmap bitmap) {
        long red = 0L;
        long green = 0L;
        long blue = 0L;
        long count = 0L;
        int darkest = Color.BLACK;
        int mostSaturated = Color.WHITE;
        float darkestLuma = 1f;
        float highestSaturation = 0f;
        float[] hsl = new float[3];

        for (int y = 0; y < bitmap.getHeight(); y += 2) {
            for (int x = 0; x < bitmap.getWidth(); x += 2) {
                int color = bitmap.getPixel(x, y);
                if (Color.alpha(color) < 16) {
                    continue;
                }

                red += Color.red(color);
                green += Color.green(color);
                blue += Color.blue(color);
                count++;

                float luma = (float) ColorUtils.calculateLuminance(color);
                if (luma < darkestLuma) {
                    darkestLuma = luma;
                    darkest = color;
                }

                ColorUtils.colorToHSL(color, hsl);
                if (hsl[1] > highestSaturation) {
                    highestSaturation = hsl[1];
                    mostSaturated = color;
                }
            }
        }

        if (count == 0L) {
            return null;
        }

        int average = Color.rgb((int) (red / count), (int) (green / count), (int) (blue / count));
        int background = clampLuminance(ColorUtils.blendARGB(darkest, average, 0.15f), 0.14f);
        int accentSeed = mostSaturated == Color.WHITE ? average : mostSaturated;
        int surface = ColorUtils.blendARGB(background, average, 0.20f);
        int surfaceStrong = ColorUtils.blendARGB(background, accentSeed, 0.26f);
        int accent = ensureReadable(accentSeed, background, 4.2f);
        int text = readableTextFor(background);
        int mutedText = ColorUtils.blendARGB(text, accent, 0.25f);
        int overlay = Color.argb(196, Color.red(background), Color.green(background), Color.blue(background));

        return new Palette(background, surface, surfaceStrong, accent, text, mutedText, overlay,
                buildSuggestionStyle(accent, background, 0f, 0.96f, 0.04f),
                buildSuggestionStyle(accent, background, 18f, 0.82f, 0.10f),
                buildSuggestionStyle(accent, background, -18f, 1.12f, -0.02f),
                buildSuggestionStyle(accent, background, 34f, 0.78f, 0.18f),
                buildSuggestionStyle(accent, background, -34f, 0.88f, 0.20f),
                buildSuggestionStyle(accent, background, 52f, 0.74f, 0.08f),
                buildSuggestionStyle(accent, background, -8f, 0.60f, 0.14f));
    }

    private static int resolveThemeColor(Theme theme, Palette palette, int fallbackColor) {
        switch (theme) {
            case bg_color:
            case statusbar_color:
            case navigationbar_color:
                return palette.background;
            case overlay_color:
                return palette.overlay;
            case window_terminal_bg:
            case toolbar_bg:
                return palette.surface;
            case input_bgrectcolor:
            case output_bgrectcolor:
            case toolbar_bgrectcolor:
            case suggestions_bgrectcolor:
                return ColorUtils.setAlphaComponent(palette.surfaceStrong, 224);
            case input_bg:
            case output_bg:
            case suggestions_bg:
                return ColorUtils.setAlphaComponent(palette.surface, 208);
            case input_color:
            case device_color:
            case ascii_color:
            case time_color:
            case storage_color:
            case ram_color:
            case network_info_color:
            case alias_content_color:
            case hint_color:
            case notes_color:
            case notes_locked_color:
            case weather_color:
            case unlock_counter_color:
            case dashed_border_color:
            case module_name_text_color:
            case module_button_border_color:
                return palette.accent;
            case module_button_bg_color:
                return ColorUtils.setAlphaComponent(palette.surface, 208);
            case output_color:
            case toolbar_color:
            case enter_color:
            case cursor_color:
            case restart_message_color:
            case session_info_color:
            case link_color:
            case mark_color:
            case app_installed_color:
            case app_uninstalled_color:
            case apps_drawer_color:
                return palette.text;
            case battery_text_high:
            case battery_text_medium:
            case battery_text_low:
            case status_lines_bgrectcolor:
            case status_lines_bg:
            case status_lines_shadow_color:
            case input_shadow_color:
            case output_shadow_color:
                return fallbackColor;
            default:
                return fallbackColor;
        }
    }

    private static int resolveSuggestionsColor(Suggestions suggestion, Palette palette, int fallbackColor) {
        switch (suggestion) {
            case default_text_color:
                return palette.defaultSuggestionStyle.text;
            case apps_text_color:
                return palette.appSuggestionStyle.text;
            case alias_text_color:
                return palette.aliasSuggestionStyle.text;
            case cmd_text_color:
                return palette.commandSuggestionStyle.text;
            case song_text_color:
                return palette.songSuggestionStyle.text;
            case contact_text_color:
                return palette.contactSuggestionStyle.text;
            case file_text_color:
                return palette.fileSuggestionStyle.text;
            case default_bg_color:
                return palette.defaultSuggestionStyle.background;
            case apps_bg_color:
                return palette.appSuggestionStyle.background;
            case alias_bg_color:
                return palette.aliasSuggestionStyle.background;
            case cmd_bg_color:
                return palette.commandSuggestionStyle.background;
            case song_bg_color:
                return palette.songSuggestionStyle.background;
            case contact_bg_color:
                return palette.contactSuggestionStyle.background;
            case file_bg_color:
                return palette.fileSuggestionStyle.background;
            default:
                return fallbackColor;
        }
    }

    private static int darkestOf(int... colors) {
        int darkest = colors[0];
        double luminance = ColorUtils.calculateLuminance(darkest);
        for (int i = 1; i < colors.length; i++) {
            double candidate = ColorUtils.calculateLuminance(colors[i]);
            if (candidate < luminance) {
                darkest = colors[i];
                luminance = candidate;
            }
        }
        return darkest;
    }

    private static int mostSaturatedOf(int... colors) {
        int selected = colors[0];
        float bestSaturation = -1f;
        float[] hsl = new float[3];
        for (int color : colors) {
            ColorUtils.colorToHSL(color, hsl);
            if (hsl[1] > bestSaturation) {
                bestSaturation = hsl[1];
                selected = color;
            }
        }
        return selected;
    }

    private static int clampLuminance(int color, float maxLuminance) {
        float[] hsl = new float[3];
        ColorUtils.colorToHSL(color, hsl);
        hsl[2] = Math.min(hsl[2], maxLuminance);
        if (hsl[1] < 0.18f) {
            hsl[1] = 0.18f;
        }
        return ColorUtils.HSLToColor(hsl);
    }

    private static int readableTextFor(int background) {
        int preferred = ColorUtils.blendARGB(Color.WHITE, background, 0.10f);
        if (ColorUtils.calculateContrast(preferred, background) >= 4.5d) {
            return preferred;
        }
        return Color.WHITE;
    }

    private static int ensureReadable(int seed, int background, double minContrast) {
        if (ColorUtils.calculateContrast(seed, background) >= minContrast) {
            return seed;
        }

        float[] hsl = new float[3];
        ColorUtils.colorToHSL(seed, hsl);
        hsl[1] = Math.max(hsl[1], 0.28f);

        for (int i = 0; i < 8; i++) {
            hsl[2] = Math.min(0.92f, hsl[2] + 0.08f);
            int adjusted = ColorUtils.HSLToColor(hsl);
            if (ColorUtils.calculateContrast(adjusted, background) >= minContrast) {
                return adjusted;
            }
        }

        return readableTextFor(background);
    }

    private static SuggestionStyle buildSuggestionStyle(int accent, int background, float hueShiftDegrees, float saturationScale, float lightnessDelta) {
        float[] hsl = new float[3];
        ColorUtils.colorToHSL(accent, hsl);

        hsl[0] = (hsl[0] + hueShiftDegrees + 360f) % 360f;
        hsl[1] = clamp(hsl[1] * saturationScale, 0.18f, 0.92f);
        hsl[2] = clamp(hsl[2] + lightnessDelta, 0.34f, 0.78f);

        int tone = ColorUtils.HSLToColor(hsl);
        int backgroundTone = ColorUtils.blendARGB(tone, background, 0.20f);
        int textTone = readableTextFor(backgroundTone);

        if (ColorUtils.calculateContrast(textTone, backgroundTone) < 4.5d) {
            backgroundTone = ensureReadable(backgroundTone, background, 3.0d);
            textTone = readableTextFor(backgroundTone);
        }

        return new SuggestionStyle(backgroundTone, textTone);
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private static final class SuggestionStyle {
        final int background;
        final int text;

        SuggestionStyle(int background, int text) {
            this.background = background;
            this.text = text;
        }
    }

    private static final class Palette {
        final int background;
        final int surface;
        final int surfaceStrong;
        final int accent;
        final int text;
        final int mutedText;
        final int overlay;
        final SuggestionStyle appSuggestionStyle;
        final SuggestionStyle aliasSuggestionStyle;
        final SuggestionStyle commandSuggestionStyle;
        final SuggestionStyle songSuggestionStyle;
        final SuggestionStyle contactSuggestionStyle;
        final SuggestionStyle fileSuggestionStyle;
        final SuggestionStyle defaultSuggestionStyle;

        Palette(int background, int surface, int surfaceStrong, int accent, int text, int mutedText, int overlay,
                SuggestionStyle appSuggestionStyle, SuggestionStyle aliasSuggestionStyle, SuggestionStyle commandSuggestionStyle,
                SuggestionStyle songSuggestionStyle, SuggestionStyle contactSuggestionStyle, SuggestionStyle fileSuggestionStyle,
                SuggestionStyle defaultSuggestionStyle) {
            this.background = background;
            this.surface = surface;
            this.surfaceStrong = surfaceStrong;
            this.accent = accent;
            this.text = text;
            this.mutedText = mutedText;
            this.overlay = overlay;
            this.appSuggestionStyle = appSuggestionStyle;
            this.aliasSuggestionStyle = aliasSuggestionStyle;
            this.commandSuggestionStyle = commandSuggestionStyle;
            this.songSuggestionStyle = songSuggestionStyle;
            this.contactSuggestionStyle = contactSuggestionStyle;
            this.fileSuggestionStyle = fileSuggestionStyle;
            this.defaultSuggestionStyle = defaultSuggestionStyle;
        }
    }
}
