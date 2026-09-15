package ohi.andre.consolelauncher.localization

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import ohi.andre.consolelauncher.R
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Run twice with -e packStage install / verify. Each instrumentation invocation starts a fresh app process. */
@RunWith(AndroidJUnit4::class)
class LanguagePackRestartTest {
    @Test fun selectionSurvivesNewProcess() {
        val stage = InstrumentationRegistry.getArguments().getString("packStage")
        assumeTrue(stage == "install" || stage == "verify")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val state = File(context.cacheDir, "language-pack-restart-fixture.json")
        if (stage == "install") {
            check(!state.exists()) { "Finish the previous verification first" }
            val id = "test-restart-${System.nanoTime()}"
            state.writeText(JSONObject().put("id", id).put("previous", LanguagePacks.selectedId()).toString())
            LanguagePacks.import(LanguagePackTest().archive(id).inputStream())
            LanguagePacks.select(id)
            assertEquals(id, LanguagePacks.active?.id)
        } else {
            val saved = JSONObject(state.readText())
            val id = saved.getString("id")
            try {
                assertEquals(id, LanguagePacks.active?.id)
                assertEquals("اختبار اللغة", context.applicationContext.getString(R.string.language_packs_title))
            } finally {
                if (LanguagePacks.installed().any { it.id == id }) LanguagePacks.remove(id)
                LanguagePacks.select(saved.getString("previous"))
                state.delete()
            }
        }
    }
}
