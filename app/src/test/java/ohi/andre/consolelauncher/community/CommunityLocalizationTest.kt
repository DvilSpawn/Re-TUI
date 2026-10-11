package ohi.andre.consolelauncher.community

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.zip.ZipFile

class CommunityLocalizationTest {
    @Test fun touchLabelsHaveMatchingRuntimeCatalogAndPackagedEnglishTemplate() {
        val root = generateSequence(File(System.getProperty("user.dir")!!)) { it.parentFile }.first { File(it, "app/src/main").isDirectory }
        val catalog = JSONObject(File(root, "app/src/main/assets/localization/catalog.json").readText())
        val keys = listOf("community_auth_username_guidance", "community_auth_password_guidance", "community_auth_username_error", "community_auth_password_error", "community_auth_confirmation_error", "community_channels", "community_suggestions", "community_new_thread", "community_thread_title", "community_edit_title", "community_edit_title_summary", "community_title_guidance", "community_title_error", "community_post_discussion", "community_mentions", "community_source_gone", "community_moderate", "community_private_uncertain", "community_retry")
        for (key in keys) assertTrue("Missing localized key $key", catalog.has(key))
        val messaging = listOf("community_join", "community_sign_in", "community_welcome", "community_general_context", "community_forum_context", "community_thread_context", "community_connection_live", "community_message_hint", "community_reply_hint", "community_discussion_hint", "community_message_actions", "community_copy_message", "community_mention_author", "community_reset_window")
        for (key in messaging) assertTrue("Missing messaging key $key", catalog.has(key))
        for (key in listOf("community_sending", "community_not_confirmed", "community_live")) assertTrue("Missing localized delivery key $key", catalog.has(key))
        ZipFile(File(root, "app/src/main/assets/localization/english-template.zip")).use { zip ->
            val xml = zip.getInputStream(zip.getEntry("strings.xml")).bufferedReader().readText()
            for (key in keys) assertTrue("Missing bundled template key $key", xml.contains("name=\"$key\""))
            for (key in messaging) assertTrue("Missing messaging template key $key", xml.contains("name=\"$key\""))
        }
        assertTrue(catalog.getJSONObject("community_help").getJSONObject("forms").getString("other").contains("/moderate"))
    }
}
