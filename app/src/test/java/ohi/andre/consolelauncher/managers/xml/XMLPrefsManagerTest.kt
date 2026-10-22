package ohi.andre.consolelauncher.managers.xml

import ohi.andre.consolelauncher.managers.AppsManager
import ohi.andre.consolelauncher.managers.xml.options.Apps
import org.junit.Assert.assertEquals
import org.junit.Test

class XMLPrefsManagerTest {
    @Test fun notificationFiltersAndFormatsRemainSeparateRecords() {
        val document = javax.xml.parsers.DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument()
        val root = document.createElement("NOTIFICATIONS"); document.appendChild(root)
        for (tag in listOf("filter","format")) for (id in 0..1) {
            root.appendChild(document.createElement(tag).apply {
                setAttribute("id",id.toString()); setAttribute("value","value-$id")
            })
        }
        assertEquals(false,XMLPrefsManager.migrateRootValues(document,root,XMLPrefsManager.XMLPrefsRoot.NOTIFICATIONS))
        for (tag in listOf("filter","format")) {
            assertEquals(2,root.getElementsByTagName(tag).length)
            assertEquals("value-1",(root.getElementsByTagName(tag).item(1) as org.w3c.dom.Element).getAttribute("value"))
        }
    }
    @Test fun conflictingNotificationSingletonStillFails() {
        val document = javax.xml.parsers.DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument()
        val root = document.createElement("NOTIFICATIONS"); document.appendChild(root)
        for (value in listOf("true","false")) root.appendChild(document.createElement("show_notifications").apply {setAttribute("value",value)})
        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
            XMLPrefsManager.migrateRootValues(document,root,XMLPrefsManager.XMLPrefsRoot.NOTIFICATIONS)
        }
    }
    @Test fun legacyTimeFormatIsMigratedWithoutChangingItsValue() {
        val document = javax.xml.parsers.DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument()
        val root = document.createElement("BEHAVIOR")
        document.appendChild(root)
        val format = "[size=30]HH:mm[/size]%nEEE, dd MMM@HH:mm #FF0088"
        root.appendChild(document.createElement("time_format").apply { setAttribute("value", format) })
        assertEquals(true, XMLPrefsManager.migrateRootValues(document, root, XMLPrefsManager.XMLPrefsRoot.BEHAVIOR))
        assertEquals(0, root.getElementsByTagName("time_format").length)
        assertEquals(format, (root.getElementsByTagName("status_time_format").item(0) as org.w3c.dom.Element).getAttribute("value"))
        assertEquals(false, XMLPrefsManager.migrateRootValues(document, root, XMLPrefsManager.XMLPrefsRoot.BEHAVIOR))
        assertEquals(format, (root.getElementsByTagName("output_time_format").item(0) as org.w3c.dom.Element).getAttribute("value"))
    }

    @Test fun existingStatusTimeFormatWinsOverLegacyValue() {
        val document = javax.xml.parsers.DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument()
        val root = document.createElement("BEHAVIOR")
        document.appendChild(root)
        for ((key, value) in listOf("time_format" to "HH:mm", "status_time_format" to "HH:mm:ss")) {
            root.appendChild(document.createElement(key).apply { setAttribute("value", value) })
        }
        XMLPrefsManager.migrateRootValues(document, root, XMLPrefsManager.XMLPrefsRoot.BEHAVIOR)
        assertEquals(0, root.getElementsByTagName("time_format").length)
        assertEquals("HH:mm:ss", (root.getElementsByTagName("status_time_format").item(0) as org.w3c.dom.Element).getAttribute("value"))
    }

    @Test fun migrationBackupIsExactAndNeverOverwritten() {
        val folder = java.nio.file.Files.createTempDirectory("migration-backup").toFile()
        try {
            val source = java.io.File(folder, "behavior.xml").apply { writeText("original bytes\n") }
            XMLPrefsManager.preserveMigrationSource(source)
            source.writeText("current bytes")
            XMLPrefsManager.preserveMigrationSource(source)
            assertEquals("original bytes\n", java.io.File(folder, "behavior.xml.pre-migration").readText())
            assertEquals("current bytes", source.readText())
        } finally { folder.deleteRecursively() }
    }

    @Test fun failedBackupLeavesSourceUntouched() {
        val folder = java.nio.file.Files.createTempDirectory("migration-backup-fail").toFile()
        try {
            val source = java.io.File(folder, "behavior.xml").apply { writeText("original") }
            java.io.File(folder, "behavior.xml.pre-migration").mkdir()
            org.junit.Assert.assertThrows(IllegalStateException::class.java) { XMLPrefsManager.preserveMigrationSource(source) }
            assertEquals("original", source.readText())
        } finally { folder.deleteRecursively() }
    }

    @Test
    fun disposePreservesLiveAppPreferences() {
        val previous = AppsManager.instance
        AppsManager.instance = null
        val values = XMLPrefsManager.XMLPrefsRoot.APPS.getValues()!!
        values.add(Apps.default_app_n1.label(), Apps.MOST_USED)

        try {
            XMLPrefsManager.dispose()
            assertEquals(Apps.MOST_USED, values[Apps.default_app_n1]?.value)
        } finally {
            values.list.clear()
            AppsManager.instance = previous
        }
    }
}
