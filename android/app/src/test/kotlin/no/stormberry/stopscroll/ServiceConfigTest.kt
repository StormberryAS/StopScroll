package no.stormberry.stopscroll

import android.view.accessibility.AccessibilityEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** The empty-package trap (review B1): packageNames is never empty, on any path. */
class ServiceConfigTest {
    private val yt = "com.google.android.youtube"
    private val watched = listOf(yt)

    @Test fun theConstantsAreAndroids() {
        assertEquals(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED, ServiceConfig.TYPE_WINDOW_STATE_CHANGED)
        assertEquals(AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED, ServiceConfig.TYPE_WINDOW_CONTENT_CHANGED)
    }

    @Test fun consentAndAlwaysBlockWatchYouTube() {
        val p = ServiceConfig.plan(watched, mapOf(yt to Mode.ALWAYS), consent = true)
        assertEquals(listOf(yt), p.packages)
        assertEquals(ServiceConfig.EVENT_TYPES, p.eventTypes)
        assertTrue(p.active)
    }

    @Test fun everyAppOffKeepsTheListAndDropsTheEventTypesToZero() {
        val p = ServiceConfig.plan(watched, mapOf(yt to Mode.OFF), consent = true)
        assertEquals(listOf(yt), p.packages)
        assertEquals(0, p.eventTypes)
        assertFalse(p.active)
    }

    @Test fun noConsentKeepsTheListAndReceivesNothing() {
        for (mode in Mode.entries) {
            val p = ServiceConfig.plan(watched, mapOf(yt to mode), consent = false)
            assertEquals(listOf(yt), p.packages)
            assertEquals(0, p.eventTypes)
        }
        val p = ServiceConfig.plan(watched, emptyMap(), consent = false)
        assertEquals(listOf(yt), p.packages)
        assertEquals(0, p.eventTypes)
    }

    @Test fun noCombinationEverGivesAnEmptyListOrAPackageOutsideTheWatchedOnes() {
        val three = listOf(yt, "com.instagram.android", "com.facebook.katana")
        val choices = listOf<Mode?>(null) + Mode.entries
        for (a in choices) for (b in choices) for (c in choices) for (consent in listOf(true, false)) {
            val modes = listOfNotNull(a?.let { three[0] to it }, b?.let { three[1] to it }, c?.let { three[2] to it }).toMap()
            val p = ServiceConfig.plan(three, modes, consent)
            assertTrue("$modes $consent", p.packages.isNotEmpty())
            assertTrue("$modes $consent", three.containsAll(p.packages))
            if (p.active) assertTrue(p.packages.all { modes[it] == Mode.ALWAYS })
        }
    }

    @Test fun youTubeAndInstagramEachFollowTheirOwnMode() {
        val ig = "com.instagram.android"
        val both = listOf(yt, ig)
        assertEquals(both, Repo.rules.packages)
        assertEquals(both, ServiceConfig.plan(both, mapOf(yt to Mode.ALWAYS, ig to Mode.ALWAYS), true).packages)
        val igOff = ServiceConfig.plan(both, mapOf(yt to Mode.ALWAYS, ig to Mode.OFF), true)
        assertEquals(listOf(yt), igOff.packages)
        assertFalse(ServiceConfig.mayRead(igOff, ig))
        val ytOff = ServiceConfig.plan(both, mapOf(yt to Mode.OFF, ig to Mode.ALWAYS), true)
        assertEquals(listOf(ig), ytOff.packages)
        assertTrue(ServiceConfig.mayRead(ytOff, ig))
        // Instagram is Always block until the user chooses (spec 6.6), and inert until consent.
        val fresh = SettingsModel()
        assertEquals(Mode.ALWAYS, fresh.mode(ig))
        assertEquals(both, ServiceConfig.plan(both, fresh.modesFor(both), true).packages)
    }

    @Test fun anEmptyWatchedListIsRefused() {
        assertThrows(IllegalArgumentException::class.java) { ServiceConfig.plan(emptyList(), emptyMap(), true) }
    }

    @Test fun theWindowCheckReadsOnlyActiveWatchedPackages() {
        val on = ServiceConfig.plan(watched, mapOf(yt to Mode.ALWAYS), true)
        assertTrue(ServiceConfig.mayRead(on, yt))
        assertFalse(ServiceConfig.mayRead(on, "com.example.bank"))
        assertFalse(ServiceConfig.mayRead(on, "com.android.launcher3"))
        assertFalse(ServiceConfig.mayRead(on, null))
        val off = ServiceConfig.plan(watched, mapOf(yt to Mode.OFF), true)
        assertFalse(ServiceConfig.mayRead(off, yt))
        val noConsent = ServiceConfig.plan(watched, mapOf(yt to Mode.ALWAYS), false)
        assertFalse(ServiceConfig.mayRead(noConsent, yt))
    }

    @Test fun theServiceXmlListsExactlyTheRulesPackagesAndThePinnedFlags() {
        val x = Repo.xml("android/app/src/main/res/xml/accessibility_service_config.xml")
        val ns = "http://schemas.android.com/apk/res/android"
        assertEquals(Repo.rules.packages.joinToString(","), x.getAttributeNS(ns, "packageNames"))
        assertEquals("0", x.getAttributeNS(ns, "notificationTimeout"))
        assertEquals("true", x.getAttributeNS(ns, "canRetrieveWindowContent"))
        assertEquals("false", x.getAttributeNS(ns, "isAccessibilityTool"))
        assertEquals("flagReportViewIds", x.getAttributeNS(ns, "accessibilityFlags"))
        assertEquals("typeWindowStateChanged|typeWindowContentChanged", x.getAttributeNS(ns, "accessibilityEventTypes"))
        assertEquals("", x.getAttributeNS(ns, "canPerformGestures"))
        assertEquals("", x.getAttributeNS(ns, "canTakeScreenshot"))
        assertEquals("", x.getAttributeNS(ns, "canRequestFilterKeyEvents"))
    }
}
