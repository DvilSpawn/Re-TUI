package ohi.andre.consolelauncher.community

import org.junit.Assert.*
import org.junit.Test
import java.io.File

/** Source wiring checks only; rendered touch bounds require instrumentation. */
class CommunityPaneWiringTest {
    private val root = generateSequence(File(System.getProperty("user.dir"))) { it.parentFile }.first { File(it, "app/src/main").isDirectory }
    @Test fun mentionRowsAreRemovedEvenIfSerializationPrunedMetadataFirst() {
        val source = File(root, "app/src/main/java/ohi/andre/consolelauncher/community/CommunityActivity.kt").readText()
        val expiry = source.substringAfter("private val expireCache").substringBefore("private val saveDraft")
        assertTrue(expiry.contains("displayedMentions.any"))
        assertTrue(expiry.contains("state.mentions.items.none"))
    }
    @Test fun navigationCancelsCaretCompletionAndCollapsedRailKeepsMentionBadges() {
        val source = File(root, "app/src/main/java/ohi/andre/consolelauncher/community/CommunityActivity.kt").readText()
        val cancel = source.substringAfter("private fun cancelPageRequest()").substringBefore("private fun selectChannel")
        assertTrue("Old context lookup must not survive navigation", cancel.contains("lookupGeneration++"))
        assertTrue(cancel.contains("lookupClient.cancel()"))
        assertTrue(source.contains("getString(R.string.community_channels) + badge"))
        assertTrue(source.contains("getString(R.string.community_mentions) + badge"))
    }
    @Test fun touchReportsCaptureSourceIdentityBeforeDialogPollingCanReorderHistory() {
        val source = File(root, "app/src/main/java/ohi/andre/consolelauncher/community/CommunityActivity.kt").readText()
        assertTrue("Message source must be captured before choosing an action", source.contains("val id = record.optString(\"id\"); val context = pane.context; val accountId = state.account?.id"))
        assertTrue("An account switch must not reuse a captured source", source.contains("state.account?.id != accountId || pane.context != context"))
        assertTrue(source.contains("client.reportSource(account, collection, id, text)"))
    }
    @Test fun historicalForumBodiesAreRecheckedBeforeBeingCalledCurrent() {
        val source = File(root, "app/src/main/java/ohi/andre/consolelauncher/community/CommunityActivity.kt").readText()
        assertTrue("Thread cache must reflect moderation", source.contains("client.checked(account, ids, \"threads\")"))
        assertTrue(source.contains("client.checked(account, ids, \"thread_replies\")"))
    }
    @Test fun communityUsesTransparentFloatingWindowTheme() {
        val manifest = File(root, "app/src/main/AndroidManifest.xml").readText()
        val activity = manifest.substringAfter("android:name=\".community.CommunityActivity\"").substringBefore("/>")
        assertTrue("Window must preserve the existing wallpaper/transparency path", activity.contains("@style/Custom.Floating"))
    }
    @Test fun failureSpecificFeedbackIsNotOverwrittenByGenericNetworkStatus() {
        val source = File(root, "app/src/main/java/ohi/andre/consolelauncher/community/CommunityActivity.kt").readText()
        val request = source.substring(source.indexOf("private fun <T> request("), source.indexOf("private fun login("))
        assertTrue("Private uncertainty/source expiry must remain visible", request.indexOf("failed(error)") > request.indexOf("status.text = when (error)"))
    }
    @Test fun recipientHighlightsAndMentionReadMarkersAreWiredToVisibleRecords() {
        val source = File(root, "app/src/main/java/ohi/andre/consolelauncher/community/CommunityActivity.kt").readText()
        assertTrue("Body mentions must carry styles", source.contains("CommunityMentionInput.ranges(body, handle)"))
        assertTrue(source.contains("android.text.style.StyleSpan(Typeface.BOLD)"))
        assertTrue(source.contains("state.mentions.read(mention.optString(\"id\"))"))
        assertTrue(source.contains("mention.optString(\"source_id\") == id"))
    }
    @Test fun foregroundDiscoveryIsIndependentOfPostingAndCancelledOnPause() {
        val source = File(root, "app/src/main/java/ohi/andre/consolelauncher/community/CommunityActivity.kt").readText()
        assertTrue("Presence must have a separate client", source.contains("presenceClient = CommunityClient("))
        assertTrue(source.contains("presenceWorker.submit"))
        assertTrue(source.contains("ui.postDelayed(presencePoll, 30_000L)"))
        assertTrue(source.contains("presenceJob?.cancel(true); presenceClient.cancel()"))
        assertTrue(source.contains("ui.removeCallbacks(mentionLookup)"))
        assertTrue(source.contains("lookupClient.cancel()"))
        assertTrue(source.contains("input.selectionChanged = { scheduleLookup() }"))
        assertTrue(source.contains("ui.postDelayed(mentionLookup, 250)"))
        assertTrue(source.contains("epoch != foregroundGeneration"))
        assertTrue(source.contains("state.account?.id != account.id"))
    }
    @Test fun touchPaneReusesFloatingFrameAndOffersChannelNavigationWithoutShellDispatch() {
        val source = File(root, "app/src/main/java/ohi/andre/consolelauncher/community/CommunityActivity.kt").readText()
        assertTrue("Community must use the existing floating pane", source.contains("HelpWindowPanel("))
        assertTrue(source.contains("TerminalBorderRuntime.bind("))
        assertTrue(source.contains("LauncherFontScale.terminalTextSizeSp()"))
        assertTrue(source.contains("community_channels"))
        assertTrue(source.contains("community_new_thread"))
        assertTrue(source.contains("openThread("))
        assertTrue(source.contains("pane.thread = \"\""))
        assertFalse(source.contains("Shell.run"))
        assertFalse(source.contains("CommandExecutor"))
    }
}
