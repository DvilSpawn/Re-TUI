package ohi.andre.consolelauncher.managers.ui

import android.content.Context
import android.os.Handler
import android.view.View
import android.view.GestureDetector
import android.view.MotionEvent
import android.widget.ScrollView
import android.widget.TextView
import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.managers.settings.AppearanceSettings.dashedBorders
import ohi.andre.consolelauncher.managers.settings.ThemeColorResolver
import ohi.andre.consolelauncher.managers.xml.options.Theme
import ohi.andre.consolelauncher.tuils.frame.FrameTarget
import ohi.andre.consolelauncher.tuils.frame.TerminalBorderRuntime
import ohi.andre.consolelauncher.tuils.Tuils

/** The hack command's full-screen theatrics overlay. */
class HackOverlayController(
    private val context: Context,
    private val rootView: View,
    private val handlerProvider: () -> Handler?,
    private val closeKeyboard: () -> Unit
) {
    private var hackOverlay: View? = null
    private var hackOverlayBasePaddingLeft = 0
    private var hackOverlayBasePaddingTop = 0
    private var hackOverlayBasePaddingRight = 0
    private var hackOverlayBasePaddingBottom = 0
    private val hackHideRunnable: Runnable = object : Runnable {
        override fun run() {
            val overlay = rootView.findViewById<View?>(R.id.hack_overlay)
            if (overlay != null) {
                overlay.animate().cancel()
                overlay.setVisibility(View.GONE)
                overlay.setAlpha(1f)
            }
        }
    }

    private val hackLines: Array<String> = arrayOf(
        context.getString(R.string.surface_breach_target_localhost_mode_theatrical_501ed),
        context.getString(R.string.surface_boot_attaching_remote_shell_a4ecf),
        context.getString(R.string.surface_boot_syncing_fake_intrusion_assets_c0461),
        context.getString(R.string.surface_auth_replaying_cached_credentials_ff954),
        context.getString(R.string.surface_auth_probing_token_vault_a1_159da),
        context.getString(R.string.surface_auth_probing_token_vault_a2_f7dd0),
        context.getString(R.string.surface_auth_probing_token_vault_a3_90360),
        context.getString(R.string.surface_trace_walking_local_package_graph_26e0b),
        context.getString(R.string.surface_trace_reading_launcher_aliases_62296),
        context.getString(R.string.surface_trace_reading_launcher_contacts_8d904),
        context.getString(R.string.surface_trace_reading_launcher_app_groups_97704),
        context.getString(R.string.surface_mem_dumping_volatile_session_tokens_06915),
        context.getString(R.string.surface_mem_scanning_keyboard_buffer_57074),
        context.getString(R.string.surface_mem_scanning_clipboard_buffer_adbc9),
        context.getString(R.string.surface_net_tunneling_through_relay_07_37b98),
        context.getString(R.string.surface_net_tunneling_through_relay_11_c3345),
        context.getString(R.string.surface_net_handshaking_with_mirror_node_b8bec),
        context.getString(R.string.surface_proc_escalating_pseudo_root_privileges_c36c9),
        context.getString(R.string.surface_proc_masking_shell_signature_9d67a),
        context.getString(R.string.surface_proc_detaching_watchdog_threads_79bd4),
        context.getString(R.string.surface_i_o_indexing_aliases_apps_contacts_f7518),
        context.getString(R.string.surface_i_o_reading_wallpaper_palette_cache_b75c8),
        context.getString(R.string.surface_i_o_reading_notification_mirror_bf032),
        context.getString(R.string.surface_cryp_brute_forcing_theme_entropy_43e64),
        context.getString(R.string.surface_cryp_brute_forcing_dashed_border_seed_72e74),
        context.getString(R.string.surface_cryp_deriving_surface_accent_offsets_8557f),
        context.getString(R.string.surface_sync_mirroring_notification_buffer_574fb),
        context.getString(R.string.surface_sync_mirroring_playback_metadata_e919a),
        context.getString(R.string.surface_sync_mirroring_quick_launch_slots_19159),
        context.getString(R.string.surface_warn_firewall_politely_ignored_01d0b),
        context.getString(R.string.surface_warn_device_insists_everything_is_fine_4c272),
        context.getString(R.string.surface_mesh_propagating_into_nearby_terminals_f7abe),
        context.getString(R.string.surface_mesh_seeding_ghost_sessions_9a742),
        context.getString(R.string.surface_mesh_flooding_loopback_channel_7c336),
        context.getString(R.string.surface_db_harvesting_battery_telemetry_fc93c),
        context.getString(R.string.surface_db_harvesting_session_hints_636b4),
        context.getString(R.string.surface_db_harvesting_stale_command_history_ffba7),
        context.getString(R.string.surface_vid_spoofing_viewport_overlays_cc228),
        context.getString(R.string.surface_vid_injecting_terminal_rain_12859),
        context.getString(R.string.surface_vid_pinning_cinematic_contrast_f7b43),
        context.getString(R.string.surface_aux_scrambling_keyboard_handshake_a188a),
        context.getString(R.string.surface_aux_bouncing_cursor_driver_55683),
        context.getString(R.string.surface_aux_destabilizing_glyph_cache_7fe2d),
        context.getString(R.string.surface_fs_mounting_storage_emulated_0_re_t_ui_52e6f),
        context.getString(R.string.surface_fs_enumerating_ui_xml_b7351),
        context.getString(R.string.surface_fs_enumerating_theme_xml_67346),
        context.getString(R.string.surface_fs_enumerating_suggestions_xml_b4f36),
        context.getString(R.string.surface_fs_enumerating_behavior_xml_705a2),
        context.getString(R.string.surface_mod_patching_fake_subsystem_notifications_b0995),
        context.getString(R.string.surface_mod_patching_fake_subsystem_music_760a7),
        context.getString(R.string.surface_mod_patching_fake_subsystem_wallpaper_5b40a),
        context.getString(R.string.surface_mod_patching_fake_subsystem_battery_b3009),
        context.getString(R.string.surface_ping_127_0_0_1_replied_in_0ms_8329b),
        context.getString(R.string.surface_ping_127_0_0_1_replied_in_0ms_8329b),
        context.getString(R.string.surface_ping_127_0_0_1_replied_in_0ms_8329b),
        context.getString(R.string.surface_scan_port_22_open_a2126),
        context.getString(R.string.surface_scan_port_80_filtered_a7a85),
        context.getString(R.string.surface_scan_port_443_open_c0202),
        context.getString(R.string.surface_scan_port_1337_aesthetically_required_d7959),
        context.getString(R.string.surface_seed_generating_panic_checksum_8f_2c_91_79931),
        context.getString(R.string.surface_seed_generating_panic_checksum_8f_2c_92_78c06),
        context.getString(R.string.surface_seed_generating_panic_checksum_8f_2c_93_a69e4),
        context.getString(R.string.surface_pipe_rerouting_stdout_to_dramatic_overlay_e0640),
        context.getString(R.string.surface_pipe_rerouting_stderr_to_dramatic_overlay_e9649),
        context.getString(R.string.surface_pipe_rerouting_common_sense_to_dev_null_0de7e),
        context.getString(R.string.surface_ovrd_replacing_launcher_calmness_with_urgency_e58e2),
        context.getString(R.string.surface_ovrd_amplifying_green_phosphor_output_5232f),
        context.getString(R.string.surface_ovrd_preserving_user_music_module_because_priorities_ceff2),
        context.getString(R.string.surface_hook_intercepting_idle_state_23263),
        context.getString(R.string.surface_hook_intercepting_wallpaper_refresh_da0db),
        context.getString(R.string.surface_hook_intercepting_harmless_command_execution_adcd5),
        context.getString(R.string.surface_task_assembling_unauthorized_vibes_4749e),
        context.getString(R.string.surface_task_replaying_synthetic_intrusion_frames_d923b),
        context.getString(R.string.surface_task_marking_sequence_irreversible_2ffa6),
        context.getString(R.string.surface_task_sequence_actually_reversible_d7cf9),
        context.getString(R.string.surface_lock_pretending_to_lock_subsystems_b0d7d),
        context.getString(R.string.surface_lock_pretending_to_exfiltrate_secrets_fdce3),
        context.getString(R.string.surface_lock_pretending_to_know_what_any_of_this_means_e6784),
        context.getString(R.string.surface_null_dereferencing_cinematic_stakes_7489a),
        context.getString(R.string.surface_null_recovering_from_fake_catastrophe_adc60),
        context.getString(R.string.surface_done_dramatic_effect_complete_3a5b9)
    )
    private val hackSequenceRunnables = ArrayList<Runnable>()

    fun styleHackOverlay(rootView: View) {
        val overlay = rootView.findViewById<View?>(R.id.hack_overlay)
        val hackText = rootView.findViewById<TextView?>(R.id.hack_text)
        if (overlay == null || hackText == null) {
            return
        }
        hackOverlay = overlay
        hackOverlayBasePaddingLeft = overlay.getPaddingLeft()
        hackOverlayBasePaddingTop = overlay.getPaddingTop()
        hackOverlayBasePaddingRight = overlay.getPaddingRight()
        hackOverlayBasePaddingBottom = overlay.getPaddingBottom()

        val accent = ThemeColorResolver.color(Theme.overlay_panel_text_color)
        val surface = ThemeColorResolver.color(Theme.overlay_panel_background_color)
        val border = ThemeColorResolver.color(Theme.overlay_panel_border_color)

        overlay.setBackground(
            TerminalBorderRuntime.panelDrawable(
                context,
                ThemeColorResolver.withMaxAlpha(surface, 232),
                border,
                1.5f,
                0,
                dashedBorders(),
                target = FrameTarget.OVERLAYS
            )
        )
        overlay.setOnClickListener(View.OnClickListener { v: View? -> dismissHackOverlay() })
        // A ScrollView consumes its touches without ever clicking, so taps are detected here.
        val singleTap = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapUp(e: MotionEvent): Boolean = true
        })
        overlay.setOnTouchListener { view, event ->
            if (singleTap.onTouchEvent(event)) view.performClick()
            false
        }

        hackText.setTextColor(accent)
        hackText.setTypeface(Tuils.getTypeface(context))
        hackText.setTextSize(11f)
    }

    fun playHackOverlay() {
        val overlay = rootView.findViewById<View?>(R.id.hack_overlay)
        val hackText = rootView.findViewById<TextView?>(R.id.hack_text)
        val hackScroll = overlay as? ScrollView
        if (overlay == null || hackText == null || hackScroll == null || handlerProvider() == null) {
            return
        }

        closeKeyboard()
        styleHackOverlay(rootView)
        clearHackCallbacks()

        hackText.setText(context.getString(R.string.surface_breach_protocol_engaged_8f35d))
        overlay.setAlpha(0f)
        overlay.setVisibility(View.VISIBLE)
        overlay.animate().alpha(1f).setDuration(120).start()

        for (i in hackLines.indices) {
            val line = hackLines[i]
            val delay = 120 + (i * 55)
            val lineRunnable = Runnable {
                hackText.append(line)
                hackText.append(Tuils.NEWLINE)
                hackScroll.post(Runnable { hackScroll.fullScroll(View.FOCUS_DOWN) })
            }
            hackSequenceRunnables.add(lineRunnable)
            handlerProvider()!!.postDelayed(lineRunnable, delay.toLong())
        }

        val exitRunnable = Runnable {
            hackText.append(context.getString(R.string.surface_exit_connection_severed_db482, Tuils.NEWLINE))
            hackScroll.post(Runnable { hackScroll.fullScroll(View.FOCUS_DOWN) })
        }
        hackSequenceRunnables.add(exitRunnable)
        handlerProvider()!!.postDelayed(exitRunnable, (120 + (hackLines.size * 55)).toLong())

        val fadeRunnable = Runnable {
            overlay.animate().alpha(0f).setDuration(180).withEndAction(
                Runnable {
                    overlay.setVisibility(View.GONE)
                    overlay.setAlpha(1f)
                }).start()
        }
        hackSequenceRunnables.add(fadeRunnable)
        handlerProvider()!!.postDelayed(fadeRunnable, 5200)
    }

    private fun clearHackCallbacks() {
        if (handlerProvider() == null) {
            return
        }

        for (runnable in hackSequenceRunnables) {
            handlerProvider()!!.removeCallbacks(runnable)
        }
        hackSequenceRunnables.clear()
        handlerProvider()!!.removeCallbacks(hackHideRunnable)
    }

    private fun dismissHackOverlay() {
        clearHackCallbacks()
        hackHideRunnable.run()
    }

    fun applyDisplayMargins(left: Int, top: Int, right: Int, bottom: Int) {
        OverlayLayoutManager.applyPaddingWithBase(
            hackOverlay,
            hackOverlayBasePaddingLeft,
            hackOverlayBasePaddingTop,
            hackOverlayBasePaddingRight,
            hackOverlayBasePaddingBottom,
            left,
            top,
            right,
            bottom
        )
    }
}
