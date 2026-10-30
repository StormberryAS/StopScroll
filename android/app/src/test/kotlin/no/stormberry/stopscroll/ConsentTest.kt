package no.stormberry.stopscroll

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Consent gating: inert until a valid consent, and the disclosure comes back when the watched list changes. */
class ConsentTest {
    private val yt = "com.google.android.youtube"

    @Test fun aConsentCountsOnlyForItsVersionAndItsApps() {
        assertFalse(Consent.isValid(null, listOf(yt)))
        assertTrue(Consent.isValid(ConsentRecord("2026-10-29", Consent.DISCLOSURE_VERSION, listOf(yt)), listOf(yt)))
        assertFalse(Consent.isValid(ConsentRecord("2026-10-29", Consent.DISCLOSURE_VERSION + 1, listOf(yt)), listOf(yt)))
        assertFalse(Consent.isValid(ConsentRecord("2026-10-29", Consent.DISCLOSURE_VERSION, listOf(yt)), listOf(yt, "com.instagram.android")))
        assertFalse(Consent.isValid(ConsentRecord("yesterday", Consent.DISCLOSURE_VERSION, listOf(yt)), listOf(yt)))
    }

    @Test fun withoutConsentTheServiceReceivesNothing() {
        val settings = SettingsModel() // Always block by default, but no consent
        assertEquals(Mode.ALWAYS, settings.mode(yt))
        val plan = ServiceConfig.plan(listOf(yt), settings.modesFor(listOf(yt)), Consent.isValid(settings.consent, listOf(yt)))
        assertEquals(0, plan.eventTypes)
        assertEquals(listOf(yt), plan.packages)
    }

    @Test fun theBridgeTakesOnlyTheCurrentDisclosureVersion() {
        assertNull(Bridge.parse("""{"v":1,"type":"recordConsent","id":1,"version":${Consent.DISCLOSURE_VERSION + 1}}""", listOf(yt)))
        assertNull(Bridge.parse("""{"v":1,"type":"recordConsent","id":1}""", listOf(yt)))
        assertEquals(Command.RecordConsent(1, Consent.DISCLOSURE_VERSION), Bridge.parse("""{"v":1,"type":"recordConsent","id":1,"version":${Consent.DISCLOSURE_VERSION}}""", listOf(yt)))
    }

    @Test fun consentSurvivesARoundTripThroughTheStoredFile() {
        val s = SettingsModel(consent = ConsentRecord("2026-10-29", 1, listOf(yt)))
        assertEquals(s, SettingsModel.fromJson(s.toJson()))
    }
}
