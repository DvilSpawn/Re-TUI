package com.dvil.retui.contract

import android.content.Intent
import android.graphics.Color
import android.os.Bundle

/**
 * Shared visual bag for Re:T-UI and companion apps.
 * One set of semantic keys — companions take what they need; unknown keys are ignored.
 * Companion-specific launch/control extras (path, OPEN_CONSOLE, …) stay in each app.
 */
object RetuiVisualContract {
    const val BG = "bg"
    const val TEXT = "text"
    const val BORDER = "border"

    const val TERMINAL_BG = "terminal_bg"
    const val PANEL_BG = "panel_bg"
    const val PANEL_TEXT = "panel_text"
    const val PANEL_BORDER = "panel_border"

    const val HEADER_BG = "header_bg"
    const val HEADER_TEXT = "header_text"
    const val BUTTON_BG = "button_bg"
    const val BUTTON_TEXT = "button_text"
    const val BUTTON_BORDER = "button_border"

    const val INPUT_BG = "input_bg"
    const val INPUT_TEXT = "input_text"
    const val OUTPUT_BG = "output_bg"
    const val OUTPUT_TEXT = "output_text"
    const val OUTPUT_BORDER = "output_border"

    const val DIRECTORY_TEXT = "directory_text"
    const val SELECTION_BG = "selection_bg"
    const val SELECTION_TEXT = "selection_text"

    const val TOP_MARGIN = "top_margin"
    const val INPUT_FONT_SIZE = "input_font_size"
    const val HEADER_TEXT_SIZE = "header_text_size"
    const val BODY_TEXT_SIZE = "body_text_size"
    const val OUTPUT_HEADER_TEXT_SIZE = "output_header_text_size"
    const val DISPLAY_MARGIN_TOP = "display_margin_top"
    const val DISPLAY_MARGIN_BOTTOM = "display_margin_bottom"

    const val MODULE_CORNER_RADIUS = "module_corner_radius"
    const val HEADER_CORNER_RADIUS = "header_corner_radius"
    const val OUTPUT_CORNER_RADIUS = "output_corner_radius"

    const val DASHED_BORDERS = "dashed_borders"
    const val DASHED_BORDER_DASH_LENGTH = "dashed_border_dash_length"
    const val DASHED_BORDER_GAP_LENGTH = "dashed_border_gap_length"
    const val DASHED_BORDER_STROKE_WIDTH_DP = "dashed_border_stroke_width_dp"

    const val CYBERDECK_MODE = "cyberdeck_mode"
    const val CRT_FILTER = "crt_filter"
    const val CRT_VIGNETTE = "crt_vignette"

    const val FONT_PATH = "font_path"
    const val FONT_FILE = "font_file"
    const val FONT_NAME = "font_name"
    const val TERMINAL_BG_IMAGE = "terminal_bg_image"

    const val CONTEXT = "retui_context"
    const val MODE = "retui_mode"

    /** All visual keys the launcher may put in the bag. */
    val KEYS = arrayOf(
        BG, TEXT, BORDER,
        TERMINAL_BG, PANEL_BG, PANEL_TEXT, PANEL_BORDER,
        HEADER_BG, HEADER_TEXT, BUTTON_BG, BUTTON_TEXT, BUTTON_BORDER,
        INPUT_BG, INPUT_TEXT, OUTPUT_BG, OUTPUT_TEXT, OUTPUT_BORDER,
        DIRECTORY_TEXT, SELECTION_BG, SELECTION_TEXT,
        TOP_MARGIN, INPUT_FONT_SIZE, HEADER_TEXT_SIZE, BODY_TEXT_SIZE, OUTPUT_HEADER_TEXT_SIZE,
        DISPLAY_MARGIN_TOP, DISPLAY_MARGIN_BOTTOM,
        MODULE_CORNER_RADIUS, HEADER_CORNER_RADIUS, OUTPUT_CORNER_RADIUS,
        DASHED_BORDERS, DASHED_BORDER_DASH_LENGTH, DASHED_BORDER_GAP_LENGTH, DASHED_BORDER_STROKE_WIDTH_DP,
        CYBERDECK_MODE, CRT_FILTER, CRT_VIGNETTE,
        FONT_PATH, FONT_FILE, FONT_NAME, TERMINAL_BG_IMAGE,
        CONTEXT, MODE
    )

    fun putInto(intent: Intent, bundle: Bundle) {
        intent.putExtras(bundle)
    }

    fun hasVisualPayload(intent: Intent?): Boolean = hasVisualPayload(intent?.extras)

    fun hasVisualPayload(extras: Bundle?): Boolean {
        if (extras == null) return false
        for (key in KEYS) {
            if (extras.containsKey(key)) return true
        }
        // one-shot legacy detection while old APKs still push old names
        for (key in LEGACY_ANY) {
            if (extras.containsKey(key)) return true
        }
        return false
    }

    fun color(intent: Intent?, fallback: Int, key: String, vararg legacy: String): Int =
        color(intent?.extras, fallback, key, *legacy)

    fun color(extras: Bundle?, fallback: Int, key: String, vararg legacy: String): Int {
        if (extras == null) return fallback
        for (candidate in sequenceOf(key) + legacy.asSequence() + legacyFor(key).asSequence()) {
            if (!extras.containsKey(candidate)) continue
            @Suppress("DEPRECATION")
            parseColor(extras.get(candidate))?.let { return it }
        }
        return fallback
    }

    fun int(intent: Intent?, fallback: Int, key: String, vararg legacy: String): Int =
        int(intent?.extras, fallback, key, *legacy)

    fun int(extras: Bundle?, fallback: Int, key: String, vararg legacy: String): Int {
        if (extras == null) return fallback
        for (candidate in sequenceOf(key) + legacy.asSequence() + legacyFor(key).asSequence()) {
            if (!extras.containsKey(candidate)) continue
            @Suppress("DEPRECATION")
            val value = extras.get(candidate)
            when (value) {
                is Number -> return value.toInt()
                is String -> value.trim().toIntOrNull()?.let { return it }
            }
        }
        return fallback
    }

    fun boolean(intent: Intent?, fallback: Boolean, key: String, vararg legacy: String): Boolean =
        boolean(intent?.extras, fallback, key, *legacy)

    fun boolean(extras: Bundle?, fallback: Boolean, key: String, vararg legacy: String): Boolean {
        if (extras == null) return fallback
        for (candidate in sequenceOf(key) + legacy.asSequence() + legacyFor(key).asSequence()) {
            if (!extras.containsKey(candidate)) continue
            @Suppress("DEPRECATION")
            val value = extras.get(candidate)
            when (value) {
                is Boolean -> return value
                is Number -> return value.toInt() != 0
                is String -> {
                    val raw = value.trim().lowercase()
                    if (raw == "true" || raw == "1" || raw == "yes") return true
                    if (raw == "false" || raw == "0" || raw == "no") return false
                }
            }
        }
        return fallback
    }

    fun booleanOrNull(intent: Intent?, key: String, vararg legacy: String): Boolean? =
        booleanOrNull(intent?.extras, key, *legacy)

    fun booleanOrNull(extras: Bundle?, key: String, vararg legacy: String): Boolean? {
        if (extras == null) return null
        for (candidate in sequenceOf(key) + legacy.asSequence() + legacyFor(key).asSequence()) {
            if (!extras.containsKey(candidate)) continue
            return boolean(extras, false, candidate)
        }
        return null
    }

    fun string(intent: Intent?, key: String, vararg legacy: String): String? =
        string(intent?.extras, key, *legacy)

    fun string(extras: Bundle?, key: String, vararg legacy: String): String? {
        if (extras == null) return null
        for (candidate in sequenceOf(key) + legacy.asSequence() + legacyFor(key).asSequence()) {
            val value = extras.getString(candidate)?.trim()
            if (!value.isNullOrEmpty()) return value
            @Suppress("DEPRECATION")
            val any = extras.get(candidate)?.toString()?.trim()
            if (!any.isNullOrEmpty()) return any
        }
        return null
    }

    fun float(intent: Intent?, fallback: Float, key: String, vararg legacy: String): Float =
        float(intent?.extras, fallback, key, *legacy)

    fun float(extras: Bundle?, fallback: Float, key: String, vararg legacy: String): Float {
        if (extras == null) return fallback
        for (candidate in sequenceOf(key) + legacy.asSequence() + legacyFor(key).asSequence()) {
            if (!extras.containsKey(candidate)) continue
            @Suppress("DEPRECATION")
            val value = extras.get(candidate)
            when (value) {
                is Number -> return value.toFloat()
                is String -> value.trim().toFloatOrNull()?.let { return it }
            }
        }
        return fallback
    }

    fun colorToOption(color: Int): String =
        "#" + Integer.toHexString(color).padStart(8, '0').takeLast(8)

    fun parseColor(value: Any?): Int? {
        if (value == null) return null
        if (value is Number) return value.toInt()
        val raw = value.toString().trim()
        if (raw.isEmpty()) return null
        return try {
            when {
                raw.startsWith("#") -> Color.parseColor(raw)
                raw.startsWith("0x", ignoreCase = true) -> {
                    var parsed = raw.substring(2).toLong(16)
                    if (raw.length <= 8) parsed = parsed or 0xff000000L
                    parsed.toInt()
                }
                else -> raw.toInt()
            }
        } catch (_: Exception) {
            null
        }
    }

    // ponytail: legacy aliases until old companion/launcher APKs are gone; delete LEGACY_* then.
    private fun legacyFor(key: String): Array<String> = LEGACY[key] ?: emptyArray()

    private val LEGACY = mapOf(
        BG to arrayOf("theme_bg"),
        TEXT to arrayOf("theme_text", "fm_text_color"),
        BORDER to arrayOf("theme_border"),
        TERMINAL_BG to arrayOf("terminal_window_background_color"),
        PANEL_BG to arrayOf("module_bg_color", "terminal_window_background_color"),
        PANEL_TEXT to arrayOf("module_text_color"),
        PANEL_BORDER to arrayOf("module_border_color"),
        HEADER_BG to arrayOf("module_header_bg_color", "fm_header_background_color"),
        HEADER_TEXT to arrayOf("module_header_text_color", "fm_header_text_color"),
        BUTTON_BG to arrayOf(
            "module_button_bg_color",
            "module_button_background_color",
            "fm_button_background_color"
        ),
        BUTTON_TEXT to arrayOf("module_button_text_color", "fm_button_text_color"),
        BUTTON_BORDER to arrayOf("module_button_border_color", "fm_button_border_color"),
        INPUT_BG to arrayOf("input_background_color", "input_bg_color"),
        INPUT_TEXT to arrayOf("input_text_color"),
        OUTPUT_BG to arrayOf("output_background_color", "output_bg_color", "fm_panel_background_color"),
        OUTPUT_TEXT to arrayOf("output_text_color"),
        OUTPUT_BORDER to arrayOf("output_border_color", "fm_border_color"),
        DIRECTORY_TEXT to arrayOf("fm_directory_text_color"),
        SELECTION_BG to arrayOf("fm_selection_background_color"),
        SELECTION_TEXT to arrayOf("fm_selection_text_color"),
        DISPLAY_MARGIN_TOP to arrayOf("display_margin_mm", "display_margin_top_section"),
        DISPLAY_MARGIN_BOTTOM to arrayOf("display_margin_bottom_section"),
        HEADER_TEXT_SIZE to arrayOf("module_header_text_size"),
        BODY_TEXT_SIZE to arrayOf("module_body_text_size", "output_text_size"),
        DASHED_BORDERS to arrayOf("enable_dashed_border"),
        CYBERDECK_MODE to arrayOf("enable_cyberdeck_mode"),
        CRT_FILTER to arrayOf("enable_crt_filter"),
        CRT_VIGNETTE to arrayOf("enable_crt_vignette"),
        CONTEXT to arrayOf("keyboard_context"),
        MODE to arrayOf("keyboard_mode")
    )

    private val LEGACY_ANY = LEGACY.values.flatMap { it.asList() }.toTypedArray()
}
