package ohi.andre.consolelauncher.managers.settings

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import ohi.andre.consolelauncher.managers.xml.options.Theme
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class PaletteCoverageRegistryTest {
    private val root = generateSequence(File(checkNotNull(System.getProperty("user.dir")))) { it.parentFile }
        .first { File(it, "scripts/terminal_colors/coverage.json").isFile }
    private val registry get() = JSONObject(File(root, "scripts/terminal_colors/coverage.json").readText())
    @Test fun everyColorRoleHasAnExplicitConversionRule() {
        assertEquals(Theme.entries.map { it.name }.toSet(), registry.getJSONObject("roles").keys().asSequence().toSet())
    }
    @Test fun everyActivityHasACaptureOrDocumentedException() {
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(File(root, "app/src/main/AndroidManifest.xml"))
        val activities = document.getElementsByTagName("activity")
        val declared = (0 until activities.length).map { activities.item(it).attributes.getNamedItem("android:name").nodeValue }.toSet()
        val surfaces = registry.getJSONArray("surfaces")
        val registered = (0 until surfaces.length()).map { surfaces.getJSONObject(it).getString("activity") }.toSet() +
            registry.getJSONObject("activityExceptions").keys().asSequence().toSet()
        assertEquals("Register new readable surfaces before the suite can pass", declared, registered)
    }

    @Test fun everyRoleBelongsToRegisteredRenderingChecks() {
        val surfaces = registry.getJSONArray("surfaces")
        val assigned = (0 until surfaces.length()).flatMap { index ->
            val surface = surfaces.getJSONObject(index)
            org.junit.Assert.assertTrue(surface.getJSONArray("renderingChecks").length() > 0)
            val roles = surface.getJSONArray("roles")
            (0 until roles.length()).map { roles.getString(it) }
        }.toSet()
        assertEquals(Theme.entries.map { it.name }.toSet(), assigned)
        val entries = registry.getJSONObject("surfaceEntryPoints")
        entries.keys().forEach { filename ->
            val source = File(root, "app/src/main/java/ohi/andre/consolelauncher/$filename").readText()
            val actual = Regex("fun\\s+((?:show|open)[A-Z]\\w*)\\s*\\(").findAll(source).map { it.groupValues[1] }.toSet()
            val expected = entries.getJSONArray(filename)
            assertEquals(filename, (0 until expected.length()).map { expected.getString(it) }.toSet(), actual)
        }
    }
}
