package ohi.andre.consolelauncher.managers

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyboardShortcutManagerTest {
    private val token = "abcdefghijklmnop"

    @Test fun acceptsTwoMappingsAndRejectsAThird() {
        val state = KeyboardShortcutManager.State(token)

        assertTrue(KeyboardShortcutManager.add(state, 'o', mapping("app:one", "One")))
        assertTrue(KeyboardShortcutManager.add(state, 'o', mapping("app:two", "Two")))
        assertFalse(KeyboardShortcutManager.add(state, 'o', mapping("app:three", "Three")))
        assertEquals(2, state.keys['o']?.size)
    }

    @Test fun storedStateRoundTripsAndClears() {
        val state = KeyboardShortcutManager.State(token)
        KeyboardShortcutManager.add(state, 'a', mapping("app:one", "One"))
        KeyboardShortcutManager.add(state, 'a', mapping("app:two", "Two"))

        val restored = KeyboardShortcutManager.parseStoredJson(KeyboardShortcutManager.storedJson(state))
        assertEquals(listOf("One", "Two"), restored?.keys?.get('a')?.map { it.label })
        assertTrue(KeyboardShortcutManager.remove(restored!!, 'a', 0))
        assertTrue(KeyboardShortcutManager.remove(restored, 'a', 0))
        assertFalse(restored.keys.containsKey('a'))
    }

    @Test fun authorizationRequiresCurrentTokenAndKnownOpaqueId() {
        val state = KeyboardShortcutManager.State(token)
        KeyboardShortcutManager.add(state, 'o', mapping("app:one", "Obsidian"))

        assertNotNull(KeyboardShortcutManager.authorize(state, token, "app:one"))
        assertNull(KeyboardShortcutManager.authorize(state, "wrong-token-value", "app:one"))
        assertNull(KeyboardShortcutManager.authorize(state, token, "app:unknown"))
    }

    @Test fun keyboardJsonMatchesTheKeyboardSchemaOnly() {
        val state = KeyboardShortcutManager.State(token)
        KeyboardShortcutManager.add(state, 'o', mapping("app:one", "Obsidian"))
        val raw = KeyboardShortcutManager.keyboardJson(state) {
            "content://com.dvil.tui_renewed.FILE_PROVIDER/keyboard-shortcuts/one.png"
        }

        val root = JSONObject(raw)
        assertEquals(setOf("version", "token", "keys"), root.keys().asSequence().toSet())
        assertEquals(1, root.getInt("version"))
        assertEquals(token, root.getString("token"))
        val entry = root.getJSONObject("keys").getJSONArray("o").getJSONObject(0)
        assertEquals(setOf("id", "label", "icon_uri"), entry.keys().asSequence().toSet())
    }

    @Test fun emptyStateSendsAnExplicitEmptyKeysObject() {
        val root = JSONObject(KeyboardShortcutManager.keyboardJson(KeyboardShortcutManager.State(token)) { null })
        assertEquals(0, root.getJSONObject("keys").length())
    }

    private fun mapping(id: String, label: String) = KeyboardShortcutManager.Mapping(
        id = id,
        label = label,
        appIdentity = "profile0-com.example-$label"
    )
}
