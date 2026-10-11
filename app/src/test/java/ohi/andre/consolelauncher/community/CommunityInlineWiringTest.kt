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
    @Test fun startsExpandedAndOnlyShowsGuidanceWhenItIsNeeded() {
        assertTrue(source.contains("initiallyMaximized = true"))
        val window = File(root, "app/src/main/java/ohi/andre/consolelauncher/help/HelpWindowPanel.kt").readText()
        assertTrue("All panes default to expanded", window.contains("initiallyMaximized: Boolean = true"))
        val xml = File(root, "app/src/main/res/layout/community_surface.xml").readText()
        val prompt = xml.substringAfter("android:id=\"@+id/community_prompt_row\"").substringBefore('>')
        assertTrue(prompt.contains("android:layout_height=\"wrap_content\""))
        assertTrue(source.contains("if (auth.active || (!mentionsView && pane.composing)) View.VISIBLE else View.GONE"))
        assertFalse(xml.contains("<EditText android:id=\"@+id/community_thread_title\""))
        assertTrue(xml.contains("community_thread_summary"))
    }
    @Test fun suggestionRowUsesHomeScreenChipGeometryAndReservesItsTouchTarget() {
        val style = source.substringAfter("private fun stylePane()").substringBefore("private fun adaptRail()")
        val home = File(root, "app/src/main/java/ohi/andre/consolelauncher/managers/suggestions/SuggestionsManager.kt").readText()
        assertTrue(home.contains("SuggestionChipStyler.createView(context, spaces)"))
        assertTrue(style.contains("SuggestionChipStyler.createView(this, spaces)"))
        assertTrue(style.contains("maxOf(dp(48), suggestionChip.measuredHeight) + spaces[1] * 2"))
        val chip = source.substringAfter("private fun suggestion(").substringBefore("private fun dp(")
        assertTrue(chip.contains("SuggestionChipStyler(this).apply(chip"))
        assertTrue(chip.contains("minimumHeight = dp(48)"))
        assertTrue(chip.contains("contentDescription = label"))
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
