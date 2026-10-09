package ohi.andre.consolelauncher.managers.xml.options;

import org.junit.Test;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import static org.junit.Assert.assertTrue;

public class BehaviorSettingsTest {
    private String setting(String name) throws Exception {
        File root = new File("src/main").isDirectory() ? new File(".") : new File("app");
        String source = new String(Files.readAllBytes(new File(root,
                "src/main/java/ohi/andre/consolelauncher/managers/xml/options/Behavior.kt").toPath()),
                StandardCharsets.UTF_8);
        Matcher entry = Pattern.compile("(?m)^    " + name + " \\{([\\s\\S]*?)(?=^    \\},)").matcher(source);
        assertTrue("Missing Behavior setting: " + name, entry.find());
        return entry.group(1);
    }

    @Test public void breathOutIsAnOptInBoolean() throws Exception {
        String entry = setting("breath_out");
        assertTrue(entry.contains("defaultValue(): String = \"false\""));
        assertTrue(entry.contains("XMLPrefsSave.BOOLEAN"));
        assertTrue(entry.contains("R.string.setting_behavior_breath_out_description"));
    }

    @Test public void breathOutTimeoutIsAnIntegerDefaultingTo900Seconds() throws Exception {
        String entry = setting("breath_out_seconds");
        assertTrue(entry.contains("defaultValue(): String = \"900\""));
        assertTrue(entry.contains("XMLPrefsSave.INTEGER"));
        assertTrue(entry.contains("R.string.setting_behavior_breath_out_seconds_description"));
    }
}
