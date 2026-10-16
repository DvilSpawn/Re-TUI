package ohi.andre.consolelauncher.commands.tuixt

import org.junit.Assert.*
import org.junit.Test

class PresetMarketplaceTest {
    @Test fun resolvesOnlyTrustedCatalogueFiles() {
        assertEquals("https://re-tui.pages.dev/terminal-colors/dracula.retui", PresetMarketplace.resolve("dracula.retui"))
        assertEquals("https://re-tui.pages.dev/terminal-colors/dracula.retui",
            PresetMarketplace.resolve("https://re-tui.pages.dev/terminal-colors/dracula.retui"))
    }
    @Test fun rejectsRemoteAndAmbiguousDownloadAddresses() {
        listOf("http://re-tui.pages.dev/terminal-colors/a", "https://evil.test/a", "//evil.test/a", "../private/a",
            "%2e%2e/a", "https://re-tui.pages.dev:443/terminal-colors/a", "a?redirect=x", "a#fragment",
            "https://user@re-tui.pages.dev/terminal-colors/a", "a\\b", "/other/a").forEach { path ->
            try { PresetMarketplace.resolve(path); fail("Accepted $path") } catch (_: IllegalArgumentException) { }
        }
    }
}
