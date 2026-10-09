#!/usr/bin/env python3
"""Guard launcher routing and detached workspace lifecycle ownership."""
from pathlib import Path
import unittest
ROOT = Path(__file__).resolve().parents[1]

class ActivityWiringTest(unittest.TestCase):
    def test_ime_control_keys_reach_key_dispatch_instead_of_literal_text(self):
        code = (ROOT / 'app/src/main/java/ohi/andre/consolelauncher/managers/termux/TermuxWorkspaceInputEditText.kt').read_text()
        self.assertIn('!Character.isISOControl(unicodeChar)', code)

    def test_consumed_external_commands_are_not_restored_from_stale_intents(self):
        code = (ROOT / 'app/src/main/java/ohi/andre/consolelauncher/managers/termux/TermuxWorkspaceActivity.kt').read_text()
        self.assertIn('pendingCommand = if (state != null) state.getString("pending") else intent.getStringExtra(EXTRA_COMMAND)', code)

    def test_frame_decoder_does_not_require_api_26(self):
        code = (ROOT / 'app/src/main/java/ohi/andre/consolelauncher/managers/termux/TermuxWorkspaceFrame.kt').read_text()
        self.assertNotIn('java.util.Base64', code)

    def test_existing_local_help_and_diagnostics_are_retained(self):
        code = (ROOT / 'app/src/main/java/ohi/andre/consolelauncher/managers/termux/TermuxWorkspaceActivity.kt').read_text()
        for resource in ('surface_session_5f810', 'surface_run_command_granted_b7cc8',
                         'surface_run_command_declared_3cdca', 'surface_launchers_c6320',
                         'surface_recovery_run_reconnect_then_refresh_if_the_pane_is_stale_af976'):
            self.assertIn(resource, code)

    def test_external_local_commands_do_not_wait_for_a_running_backend(self):
        code = (ROOT / 'app/src/main/java/ohi/andre/consolelauncher/managers/termux/TermuxWorkspaceActivity.kt').read_text()
        pending = code.split('private fun runPendingCommand()', 1)[1].split('private fun command(', 1)[0]
        self.assertIn('"help", "status", "diagnostics"', pending)
        self.assertIn('if (!local &&', pending)

    def test_launcher_has_no_embedded_workspace(self):
        ui = (ROOT / 'app/src/main/java/ohi/andre/consolelauncher/UIManager.kt').read_text()
        self.assertNotIn('inflater.inflate(R.layout.termux_workspace_page', ui)
        self.assertIn('TermuxWorkspaceActivity.open', ui)
        self.assertNotIn('private fun setupTermuxWorkspacePage', ui)

    def test_activity_owns_lifecycle_and_layout_chooser(self):
        path = ROOT / 'app/src/main/java/ohi/andre/consolelauncher/managers/termux/TermuxWorkspaceActivity.kt'
        self.assertTrue(path.exists(), 'Dedicated workspace activity is missing')
        code = path.read_text()
        self.assertNotIn('UIManager(', code)
        self.assertIn('override fun onPause()', code)
        self.assertIn('override fun onResume()', code)
        self.assertIn('showLayoutChooser()', code)
        self.assertIn('TermuxWorkspaceState.restore', code)
        self.assertIn('WindowInsetsCompat.Type.ime()', code)
        self.assertNotIn('kill-session', code)
        self.assertNotIn('kill-window', code)
        self.assertNotIn('"typing"', code)

if __name__ == '__main__': unittest.main()
