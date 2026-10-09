package ohi.andre.consolelauncher.community

import java.io.File
import org.junit.Assert.*
import org.junit.Test

/** Host wiring guards; measured layout and lifecycle assertions live in instrumentation. */
class CommunityInlineWiringTest {
    private val root = generateSequence(File(System.getProperty("user.dir")!!)) { it.parentFile }.first { File(it, "app/src/main").isDirectory }
    private val source get() = File(root, "app/src/main/java/ohi/andre/consolelauncher/community/CommunityActivity.kt").readText()
    @Test fun authenticationUsesTheComposerWithoutDraftOrSuggestionRouting() {
        assertFalse(source.contains("authDialog"))
        assertTrue(source.contains("if (auth.active) { submitAuth(); return }"))
        assertTrue(source.substringAfter("private fun saveCurrentDraft()").substringBefore("private fun restoreDraft").contains("if (auth.active) return"))
        assertTrue(source.substringAfter("private fun scheduleLookup()").substringBefore("private fun lookupUsers").contains("auth.active"))
    }
    @Test fun suggestionsAreBelowPromptAndHiddenComposerRetainsItsEnvelope() {
        val xml = File(root, "app/src/main/res/layout/community_surface.xml").readText()
        assertTrue(xml.indexOf("community_completion_scroll") > xml.indexOf("community_prompt_row"))
        assertTrue(source.contains("View.INVISIBLE"))
        assertTrue(source.contains("IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS"))
        assertTrue(source.contains("community_auth_guidance"))
    }
    @Test fun paneThemeRefreshDoesNotOverwriteSuggestionPaletteOrFont() {
        assertTrue(source.substringAfter("private fun stylePane()").substringBefore("private fun adaptRail()").contains("if (view.id == R.id.community_completions) return"))
    }
    @Test fun suggestionRowUsesItsOwnConfiguredFontBudget() {
        val style = source.substringAfter("private fun stylePane()").substringBefore("private fun adaptRail()")
        assertTrue("Suggestion typography must not inherit composer font budget", style.contains("Suggestions.suggestions_size"))
        assertTrue("Include font padding to avoid clipping", style.contains("suggestionMetrics.bottom - suggestionMetrics.top"))
        assertTrue(style.contains("reserve(root.findViewById(R.id.community_completion_scroll), maxOf(dp(48), suggestionMetrics.bottom - suggestionMetrics.top + dp(12)))"))
    }
    @Test fun draftRestoreAndNewThreadCannotMixCredentialsWithConversationDraft() {
        assertTrue(source.substringAfter("private fun restoreDraft()").substringBefore("private fun cancelPageRequest()").contains("if (auth.active) return"))
        val handler = source.substringAfter("actions.addView(button(R.string.community_new_thread, \"community_new_thread\") {").substringBefore("})")
        assertTrue(handler.indexOf("cancelAuth()") >= 0 && handler.indexOf("cancelAuth()") < handler.indexOf("pane.composing = true"))
    }
    @Test fun lifecycleClearsCredentialsAndRestoresSecureFlag() {
        assertTrue(source.substringAfter("override fun onPause()").substringBefore("override fun onDestroy()").contains("cancelAuth()"))
        assertTrue(source.substringAfter("override fun onSaveInstanceState").substringBefore("override fun onResume").contains("cancelAuth()"))
        assertTrue(source.contains("FLAG_SECURE"))
        assertTrue(source.contains("IME_FLAG_NO_PERSONALIZED_LEARNING"))
        assertTrue(source.contains("IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS"))
    }
}
