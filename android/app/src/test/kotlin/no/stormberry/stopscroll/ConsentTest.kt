package no.stormberry.stopscroll

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Consent gating, per app (UPG-2, 0.0.3): inert until an agreement, and an agreement counts for the
 * apps it named. A 0.0.3 agreement keeps YouTube, Instagram and X and YouTube Music waits (0.0.4,
 * CONSENT-4); a 0.0.2 agreement keeps YouTube and Instagram, and X and YouTube Music wait; a 0.0.1
 * agreement counts for nothing.
 */
class ConsentTest {
    private val yt = "com.google.android.youtube"
    private val ig = "com.instagram.android"
    private val x = "com.twitter.android"
    private val ytm = "com.google.android.apps.youtube.music"
    private val watched = listOf(yt, ig, x, ytm)

    @Test fun theWatchedAppsAreTheRulesFour() {
        assertEquals(watched, Repo.rules.packages)
        assertEquals("adding YouTube Music does not bump it (CONSENT-4)", 2, Consent.DISCLOSURE_VERSION)
    }

    @Test fun theUpdateFrom003KeepsYouTubeInstagramAndXAndYouTubeMusicWaits() {
        // What 0.0.3 stored after "I agree" (version 2, the three apps it watched).
        val stored = SettingsModel.fromJson("""{"v":1,"consent":{"date":"2026-10-31","version":2,"apps":["$yt","$ig","$x"]},"modes":{},"stats":{}}""")
        val agreed = Consent.agreedApps(stored.consent, watched)
        assertEquals(listOf(yt, ig, x), agreed)
        val plan = ServiceConfig.plan(watched, stored.modesFor(watched), agreed)
        assertEquals(listOf(yt, ig, x), plan.packages)
        assertTrue(plan.active)
        assertTrue("YouTube Music is not even in the runtime list", ytm !in plan.packages)
        assertTrue(!ServiceConfig.mayRead(plan, ytm))
        // Only YouTube Music set to block, not agreed: nothing active, the full list kept, nothing read.
        val onlyYtm = ServiceConfig.plan(watched, mapOf(yt to Mode.OFF, ig to Mode.OFF, x to Mode.OFF, ytm to Mode.ALWAYS), agreed)
        assertTrue(!onlyYtm.active)
        assertEquals(0, onlyYtm.eventTypes)
        assertTrue(!ServiceConfig.mayRead(onlyYtm, ytm))
        // After "I agree": all four, YouTube Music Always block by default (spec 6.6).
        val after = Consent.recordFor("2026-11-01", watched)
        assertEquals(watched, Consent.agreedApps(after, watched))
        assertEquals(watched, ServiceConfig.plan(watched, stored.modesFor(watched), Consent.agreedApps(after, watched)).packages)
    }

    @Test fun anAgreementCountsForTheAppsItNamedAndItsVersionOnly() {
        assertEquals(emptyList<String>(), Consent.agreedApps(null, watched))
        assertEquals(watched, Consent.agreedApps(ConsentRecord("2026-10-31", Consent.DISCLOSURE_VERSION, watched), watched))
        assertEquals(emptyList<String>(), Consent.agreedApps(ConsentRecord("2026-10-31", Consent.DISCLOSURE_VERSION + 1, watched), watched))
        assertEquals(emptyList<String>(), Consent.agreedApps(ConsentRecord("yesterday", Consent.DISCLOSURE_VERSION, watched), watched))
        // In the watched order, whatever the record's order; a package the rules no longer cover is dropped.
        assertEquals(listOf(yt, x), Consent.agreedApps(ConsentRecord("2026-10-31", Consent.DISCLOSURE_VERSION, listOf(x, "com.example.gone", yt)), watched))
    }

    @Test fun theUpdateFrom002KeepsYouTubeAndInstagramAndXAndYouTubeMusicWait() {
        // What 0.0.2 stored after "I agree" (version 2, the two apps it watched).
        val stored = SettingsModel.fromJson("""{"v":1,"consent":{"date":"2026-10-30","version":2,"apps":["$yt","$ig"]},"modes":{},"stats":{}}""")
        val agreed = Consent.agreedApps(stored.consent, watched)
        assertEquals(listOf(yt, ig), agreed)
        val plan = ServiceConfig.plan(watched, stored.modesFor(watched), agreed)
        assertEquals(listOf(yt, ig), plan.packages)
        assertTrue(plan.active)
        assertTrue("X is not even in the runtime list", x !in plan.packages)
        assertTrue("nor is YouTube Music", ytm !in plan.packages)
        assertTrue(!ServiceConfig.mayRead(plan, x))
        assertTrue(!ServiceConfig.mayRead(plan, ytm))
    }

    @Test fun anAgreementFrom001StillCountsForNothing() {
        val stored = SettingsModel.fromJson("""{"v":1,"consent":{"date":"2026-10-29","version":1,"apps":["$yt"]},"modes":{},"stats":{}}""")
        assertEquals(emptyList<String>(), Consent.agreedApps(stored.consent, watched))
        val plan = ServiceConfig.plan(watched, stored.modesFor(watched), Consent.agreedApps(stored.consent, watched))
        assertEquals(0, plan.eventTypes)
        assertEquals(watched, plan.packages)
    }

    @Test fun iAgreeStoresTheDayThisVersionAndEveryWatchedApp() {
        val r = Consent.recordFor("2026-10-31", watched)
        assertEquals(ConsentRecord("2026-10-31", Consent.DISCLOSURE_VERSION, watched), r)
        assertEquals(watched, Consent.agreedApps(r, watched))
        // After the 0.0.2 user agrees to the updated disclosure, X and YouTube Music are agreed too, and the date is the new one.
        val after = SettingsModel(consent = ConsentRecord("2026-10-30", 2, listOf(yt, ig))).copy(consent = Consent.recordFor("2026-11-02", watched))
        assertEquals(watched, Consent.agreedApps(after.consent, watched))
        assertEquals("2026-11-02", after.consent?.date)
    }

    @Test fun withoutConsentTheServiceReceivesNothing() {
        val settings = SettingsModel() // Always block by default, but no consent
        assertEquals(Mode.ALWAYS, settings.mode(yt))
        val plan = ServiceConfig.plan(listOf(yt), settings.modesFor(listOf(yt)), Consent.agreedApps(settings.consent, listOf(yt)))
        assertEquals(0, plan.eventTypes)
        assertEquals(listOf(yt), plan.packages)
    }

    @Test fun theBridgeTakesOnlyTheCurrentDisclosureVersion() {
        assertNull(Bridge.parse("""{"v":1,"type":"recordConsent","id":1,"version":${Consent.DISCLOSURE_VERSION + 1}}""", listOf(yt), emptyList()))
        assertNull(Bridge.parse("""{"v":1,"type":"recordConsent","id":1}""", listOf(yt), emptyList()))
        assertEquals(Command.RecordConsent(1, Consent.DISCLOSURE_VERSION), Bridge.parse("""{"v":1,"type":"recordConsent","id":1,"version":${Consent.DISCLOSURE_VERSION}}""", listOf(yt), emptyList()))
    }

    @Test fun consentSurvivesARoundTripThroughTheStoredFile() {
        val s = SettingsModel(consent = ConsentRecord("2026-10-29", 1, listOf(yt)))
        assertEquals(s, SettingsModel.fromJson(s.toJson()))
        val four = SettingsModel(consent = Consent.recordFor("2026-10-31", watched))
        assertEquals(four, SettingsModel.fromJson(four.toJson()))
    }
}
