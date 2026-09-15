package ohi.andre.consolelauncher.notes


import android.os.Bundle
import java.util.UUID

object PreviewContract {
    const val ACTION = "com.dvil.retui.remember.RESOLVE_PREVIEWS"
    const val LAUNCHER_PACKAGE = "com.dvil.tui_renewed"
    const val PROTOCOL_VERSION = 1
    const val MAX_IDS = 64
    const val MAX_PREVIEW_CHARS = 240

    const val EXTRA_PROTOCOL_VERSION = "protocol_version"
    const val EXTRA_NOTE_IDS = "note_ids"
    const val EXTRA_STATUS = "resolution_status"
    const val EXTRA_PREVIEWS = "previews"

    const val STATUS_OK = "ok"
    const val STATUS_BAD_REQUEST = "bad_request"
    const val STATUS_UNAUTHORIZED = "unauthorized"
    const val STATUS_UNSUPPORTED_VERSION = "unsupported_version"
    const val STATUS_INTERNAL_ERROR = "internal_error"

    /** Canonical Launcher appearance keys. Keep all cross-app names in one place. */
    object Visual {
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
        const val INPUT_TEXT_SIZE = "input_font_size"
        const val DISPLAY_MARGIN_TOP = "display_margin_top"
        const val DISPLAY_MARGIN_BOTTOM = "display_margin_bottom"
        const val DASHED = "dashed_borders"
        const val DASH = "dashed_border_dash_length"
        const val GAP = "dashed_border_gap_length"
        const val STROKE = "dashed_border_stroke_width_dp"
        const val MODULE_RADIUS = "module_corner_radius"
        const val HEADER_RADIUS = "header_corner_radius"
        const val OUTPUT_RADIUS = "output_corner_radius"
        const val HEADER_TEXT_SIZE = "header_text_size"
        const val BODY_TEXT_SIZE = "body_text_size"
        const val OUTPUT_HEADER_TEXT_SIZE = "output_header_text_size"
        const val CYBERDECK = "cyberdeck_mode"
        const val CRT = "crt_filter"
        const val VIGNETTE = "crt_vignette"

        const val FONT_NAME = "font_name"
        const val FONT_FILE = "font_file"
        const val FONT_PATH = "font_path"

        const val FRAME_AVAILABLE = "frame_available"
        const val FRAME_ASSET_ID = "frame_asset_id"
        const val FRAME_IMAGE_URI = "frame_image_uri"
        const val FRAME_SLICE_LEFT = "frame_slice_left_px"
        const val FRAME_SLICE_TOP = "frame_slice_top_px"
        const val FRAME_SLICE_RIGHT = "frame_slice_right_px"
        const val FRAME_SLICE_BOTTOM = "frame_slice_bottom_px"
        const val FRAME_BORDER_LEFT = "frame_border_left_dp"
        const val FRAME_BORDER_TOP = "frame_border_top_dp"
        const val FRAME_BORDER_RIGHT = "frame_border_right_dp"
        const val FRAME_BORDER_BOTTOM = "frame_border_bottom_dp"
        const val FRAME_MODE_TOP = "frame_mode_top"
        const val FRAME_MODE_RIGHT = "frame_mode_right"
        const val FRAME_MODE_BOTTOM = "frame_mode_bottom"
        const val FRAME_MODE_LEFT = "frame_mode_left"
        const val FRAME_MODE_CENTER = "frame_mode_center"
        const val FRAME_FILTERING = "frame_filtering"

        val COLORS = setOf(
            BG, TEXT, BORDER, TERMINAL_BG, PANEL_BG, PANEL_TEXT, PANEL_BORDER,
            HEADER_BG, HEADER_TEXT, BUTTON_BG, BUTTON_TEXT, BUTTON_BORDER,
            INPUT_BG, INPUT_TEXT, OUTPUT_BG, OUTPUT_TEXT, OUTPUT_BORDER,
            DIRECTORY_TEXT, SELECTION_BG, SELECTION_TEXT
        )
        val INTS = setOf(
            TOP_MARGIN, INPUT_TEXT_SIZE, DASH, GAP, MODULE_RADIUS, HEADER_RADIUS,
            OUTPUT_RADIUS, HEADER_TEXT_SIZE, BODY_TEXT_SIZE, OUTPUT_HEADER_TEXT_SIZE
        )
        val FLOATS = setOf(STROKE)
        val BOOLEANS = setOf(DASHED, CYBERDECK, CRT, VIGNETTE)
        val STRINGS = setOf(DISPLAY_MARGIN_TOP, DISPLAY_MARGIN_BOTTOM, FONT_NAME, FONT_FILE, FONT_PATH)
        val THEME_KEYS = COLORS + INTS + FLOATS + BOOLEANS + STRINGS
        val FRAME_KEYS = setOf(
            FRAME_AVAILABLE, FRAME_ASSET_ID, FRAME_IMAGE_URI, FRAME_SLICE_LEFT, FRAME_SLICE_TOP,
            FRAME_SLICE_RIGHT, FRAME_SLICE_BOTTOM, FRAME_BORDER_LEFT, FRAME_BORDER_TOP,
            FRAME_BORDER_RIGHT, FRAME_BORDER_BOTTOM, FRAME_MODE_TOP, FRAME_MODE_RIGHT,
            FRAME_MODE_BOTTOM, FRAME_MODE_LEFT, FRAME_MODE_CENTER, FRAME_FILTERING
        )
    }

    fun validateIds(ids: List<String>?): List<String>? {
        if (ids == null || ids.size > MAX_IDS) return null
        val unique = LinkedHashSet<String>()
        for (id in ids) {
            val canonical = runCatching { UUID.fromString(id).toString() }.getOrNull() ?: return null
            if (canonical != id) return null
            unique += id
        }
        return unique.toList()
    }

    fun preview(note: Note?, id: String) = Bundle().apply {
        putString("note_id", id)
        putString("preview", note?.preview()?.take(MAX_PREVIEW_CHARS).orEmpty())
        putLong("updated_at", note?.updatedAt ?: 0L)
        val counts = note?.checkCounts() ?: (0 to 0)
        putInt("completed_checklist_count", counts.first.coerceIn(0, counts.second))
        putInt("total_checklist_count", counts.second.coerceAtLeast(0))
        putString("state", if (note == null) "missing" else "available")
    }
}
