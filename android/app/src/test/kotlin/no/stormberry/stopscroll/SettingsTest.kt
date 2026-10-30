package no.stormberry.stopscroll

import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsTest {
    private val yt = "com.google.android.youtube"

    @Test fun countsAndModesRoundTrip() {
        val s = SettingsModel().withMode(yt, Mode.OFF).withClosed(yt, "2026-10-29").withClosed(yt, "2026-10-30").withLoopPause(yt)
        assertEquals(AppStats(closed = 2, loopPauses = 1, lastClosed = "2026-10-30"), s.stats(yt))
        assertEquals(s, SettingsModel.fromJson(s.toJson()))
        assertEquals(Mode.OFF, SettingsModel.fromJson(s.toJson()).mode(yt))
    }

    @Test fun anUnreadableFileGivesTheDefaultsWithoutConsent() {
        for (bad in listOf(null, "", "{", "[]", """{"consent":{"date":1}}""")) {
            assertEquals(null, SettingsModel.fromJson(bad).consent)
        }
    }

    @Test fun theStoredFileHoldsOnlyTheListedFields() {
        val s = SettingsModel(ConsentRecord("2026-10-29", 1, listOf(yt))).withClosed(yt, "2026-10-29")
        val o = org.json.JSONObject(s.toJson())
        assertEquals(setOf("v", "consent", "modes", "stats"), o.keys().asSequence().toSet())
        assertEquals(setOf("closed", "loopPauses", "lastClosed"), o.getJSONObject("stats").getJSONObject(yt).keys().asSequence().toSet())
    }
}
