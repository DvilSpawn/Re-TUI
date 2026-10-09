package ohi.andre.consolelauncher.community

import org.junit.Assert.*
import org.junit.Test

class CommunityInlineAuthTest {
    private fun invoke(mode: Any, name: String, vararg args: Any): Any? {
        val method = mode.javaClass.methods.firstOrNull { it.name == name && it.parameterCount == args.size }
        assertNotNull("Missing inline mode operation $name", method)
        return method!!.invoke(mode, *args)
    }
    private fun mode() = Class.forName("ohi.andre.consolelauncher.community.CommunityAuthInput").getDeclaredConstructor().newInstance()
    @Test fun registrationCollectsLiteralSecretsOnlyAfterValidUsername() {
        val mode = mode(); invoke(mode, "begin", true)
        invoke(mode, "submit", "ab"); assertEquals("USERNAME", invoke(mode, "getStage").toString())
        invoke(mode, "submit", "Alice_12"); assertEquals("PASSWORD", invoke(mode, "getStage").toString())
        invoke(mode, "submit", "/exit123"); assertEquals("CONFIRMATION", invoke(mode, "getStage").toString())
        val request = invoke(mode, "submit", "/exit123")!!
        assertEquals("SUBMITTING", invoke(mode, "getStage").toString())
        assertEquals("/exit123", invoke(request, "getPassword"))
        assertFalse(request.toString().contains("/exit123"))
        assertFalse(invoke(mode, "hasSecret") as Boolean)
        invoke(mode, "cancel"); assertEquals("NORMAL", invoke(mode, "getStage").toString())
    }
    @Test fun mismatchAndFailureClearSecretAndRestartUsername() {
        val mode = mode(); invoke(mode, "begin", true); invoke(mode, "submit", "alice")
        invoke(mode, "submit", "secret123"); invoke(mode, "submit", "different")
        assertEquals("USERNAME", invoke(mode, "getStage").toString()); assertFalse(invoke(mode, "hasSecret") as Boolean)
        invoke(mode, "submit", "alice"); invoke(mode, "submit", "secret123"); invoke(mode, "cancel")
        assertFalse(invoke(mode, "hasSecret") as Boolean)
        invoke(mode, "begin", false); invoke(mode, "submit", "alice"); invoke(mode, "submit", "secret123")
        invoke(mode, "restart"); assertEquals("USERNAME", invoke(mode, "getStage").toString())
        assertFalse(invoke(mode, "hasSecret") as Boolean)
    }
    @Test fun commandSuggestionsMatchLocalCommandsAndNeverContainMessageText() {
        val method = CommunityInput.javaClass.methods.firstOrNull { it.name == "suggestions" }
        assertNotNull("Missing local command suggestions", method)
        fun suggestions(text: String, signedIn: Boolean) = method!!.invoke(CommunityInput, text, signedIn) as List<*>
        assertEquals(listOf("/register", "/login"), suggestions("", false))
        assertEquals(listOf("/register"), suggestions("/r", false))
        assertTrue(suggestions("/", true).contains("/moderate"))
        assertFalse(suggestions("/", true).contains("/register"))
        assertTrue(suggestions("literal message", true).isEmpty())
        assertTrue(suggestions("//exit", true).isEmpty())
    }
    @Test fun invalidPasswordClearsInputModeAndRestartsSecureUsernameStage() {
        val mode = CommunityAuthInput(); mode.begin(true); mode.submit("alice"); mode.submit("short")
        assertEquals(CommunityAuthInput.Stage.USERNAME, mode.stage)
        assertFalse(mode.hasSecret()); assertEquals(CommunityAuthInput.Error.PASSWORD, mode.error)
        mode.submit("alice"); mode.submit("x".repeat(72)); assertEquals(CommunityAuthInput.Stage.USERNAME, mode.stage)
        mode.submit("alice"); mode.submit("😀".repeat(8)); assertEquals(CommunityAuthInput.Stage.CONFIRMATION, mode.stage)
        mode.cancel(); assertFalse(mode.hasSecret())
    }
    @Test fun inlineAuthenticationHasAnIsolatedNonSerializableMode() {
        val type = runCatching { Class.forName("ohi.andre.consolelauncher.community.CommunityAuthInput") }.getOrNull()
        assertNotNull("Inline credentials require an isolated input mode", type)
        assertFalse(java.io.Serializable::class.java.isAssignableFrom(type!!))
    }
}
