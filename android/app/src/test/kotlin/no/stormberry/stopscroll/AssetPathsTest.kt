package no.stormberry.stopscroll

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The asset handler's path rules, with the font, the touch icon and the disclaimer added in 0.0.1. */
class AssetPathsTest {
    @Test fun thePagesAndWhatTheyLoadResolve() {
        assertEquals("web/index.html", AssetPaths.resolve(""))
        assertEquals("web/disclaimer.html", AssetPaths.resolve("disclaimer.html"))
        assertEquals("web/fonts/inter/InterVariable.woff2", AssetPaths.resolve("fonts/inter/InterVariable.woff2"))
        assertEquals("font/woff2", AssetPaths.mimeType("web/fonts/inter/InterVariable.woff2"))
        assertEquals("image/png", AssetPaths.mimeType("web/apple-touch-icon.png"))
        assertEquals("text/css", AssetPaths.mimeType("web/disclaimer.css"))
    }

    @Test fun bytesCarryNoCharsetAndTextCarriesUtf8() {
        assertNull(AssetPaths.encoding("web/fonts/inter/InterVariable.woff2"))
        assertNull(AssetPaths.encoding("web/apple-touch-icon.png"))
        assertEquals("utf-8", AssetPaths.encoding("web/index.html"))
    }

    @Test fun oddPathsAndUnknownTypesAreRefused() {
        assertNull(AssetPaths.resolve("../secret.html"))
        assertNull(AssetPaths.resolve("fonts/./inter.css"))
        assertNull(AssetPaths.resolve(".hidden.css"))
        assertNull(AssetPaths.resolve("rules/screen-rules.json"))
        assertNull(AssetPaths.resolve("a\\b.html"))
        assertNull(AssetPaths.resolve("/index.html"))
    }
}
