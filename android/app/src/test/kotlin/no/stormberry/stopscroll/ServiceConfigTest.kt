package no.stormberry.stopscroll

import android.view.accessibility.AccessibilityEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** The empty-package trap (review B1): packageNames is never empty, on any path; per-app consent (0.0.3). */
class ServiceConfigTest {
    private val yt = "com.google.android.youtube"
    private val watched = listOf(yt)

    @Test fun theConstantsAreAndroids() {
        assertEquals(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED, ServiceConfig.TYPE_WINDOW_STATE_CHANGED)
        assertEquals(AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED, ServiceConfig.TYPE_WINDOW_CONTENT_CHANGED)
    }

    @Test fun consentAndAlwaysBlockWatchYouTube() {
        val p = ServiceConfig.plan(watched, mapOf(yt to Mode.ALWAYS), agreed = watched)
        assertEquals(listOf(yt), p.packages)
        assertEquals(ServiceConfig.EVENT_TYPES, p.eventTypes)
        assertTrue(p.active)
    }

    @Test fun everyAppOffKeepsTheListAndDropsTheEventTypesToZero() {
        val p = ServiceConfig.plan(watched, mapOf(yt to Mode.OFF), agreed = watched)
        assertEquals(listOf(yt), p.packages)
        assertEquals(0, p.eventTypes)
        assertFalse(p.active)
    }

    @Test fun noConsentKeepsTheListAndReceivesNothing() {
        for (mode in Mode.entries) {
            val p = ServiceConfig.plan(watched, mapOf(yt to mode), agreed = emptyList())
            assertEquals(listOf(yt), p.packages)
            assertEquals(0, p.eventTypes)
        }
        val p = ServiceConfig.plan(watched, emptyMap(), agreed = emptyList())
        assertEquals(listOf(yt), p.packages)
        assertEquals(0, p.eventTypes)
    }

    @Test fun noCombinationEverGivesAnEmptyListAPackageOutsideTheWatchedOnesOrAnUnagreedApp() {
        val four = listOf(yt, "com.instagram.android", "com.twitter.android", "com.google.android.apps.youtube.music")
        assertEquals(four, Repo.rules.packages)
        val choices = listOf<Mode?>(null) + Mode.entries
        // Every subset of the four as the agreed apps.
        val subsets = (0 until 16).map { bits -> four.filterIndexed { i, _ -> bits and (1 shl i) != 0 } }
        var active = 0
        for (a in choices) for (b in choices) for (c in choices) for (d in choices) for (agreed in subsets) {
            val modes = listOfNotNull(a?.let { four[0] to it }, b?.let { four[1] to it }, c?.let { four[2] to it }, d?.let { four[3] to it }).toMap()
            val p = ServiceConfig.plan(four, modes, agreed)
            assertTrue("$modes $agreed", p.packages.isNotEmpty())
            assertTrue("$modes $agreed", four.containsAll(p.packages))
            if (p.active) {
                active++
                assertTrue("$modes $agreed", p.packages.all { modes[it] == Mode.ALWAYS && it in agreed })
                // Exactly the agreed apps that are on: none is left out either.
                assertEquals(four.filter { modes[it] == Mode.ALWAYS && it in agreed }, p.packages)
            } else {
                assertEquals("inactive keeps the full list", four, p.packages)
                assertTrue(four.none { modes[it] == Mode.ALWAYS && it in agreed })
            }
        }
        assertTrue(active > 0)
    }

    @Test fun youTubeInstagramXAndYouTubeMusicEachFollowTheirOwnMode() {
        val ig = "com.instagram.android"
        val x = "com.twitter.android"
        val ytm = "com.google.android.apps.youtube.music"
        val four = listOf(yt, ig, x, ytm)
        assertEquals(four, Repo.rules.packages)
        val onlyYtm = ServiceConfig.plan(four, mapOf(yt to Mode.OFF, ig to Mode.OFF, x to Mode.OFF, ytm to Mode.ALWAYS), four)
        assertEquals(listOf(ytm), onlyYtm.packages)
        assertTrue(ServiceConfig.mayRead(onlyYtm, ytm))
        assertFalse("YouTube Music on does not open YouTube", ServiceConfig.mayRead(onlyYtm, yt))
        val ytOnly = ServiceConfig.plan(four, mapOf(yt to Mode.ALWAYS, ig to Mode.OFF, x to Mode.OFF, ytm to Mode.OFF), four)
        assertEquals(listOf(yt), ytOnly.packages)
        assertFalse(ServiceConfig.mayRead(ytOnly, ytm))
        // The 0.0.3 checks below run over the first three apps.
        val all = listOf(yt, ig, x)
        assertEquals(all, ServiceConfig.plan(all, mapOf(yt to Mode.ALWAYS, ig to Mode.ALWAYS, x to Mode.ALWAYS), all).packages)
        val igOff = ServiceConfig.plan(all, mapOf(yt to Mode.ALWAYS, ig to Mode.OFF, x to Mode.ALWAYS), all)
        assertEquals(listOf(yt, x), igOff.packages)
        assertFalse(ServiceConfig.mayRead(igOff, ig))
        val onlyX = ServiceConfig.plan(all, mapOf(yt to Mode.OFF, ig to Mode.OFF, x to Mode.ALWAYS), all)
        assertEquals(listOf(x), onlyX.packages)
        assertTrue(ServiceConfig.mayRead(onlyX, x))
        // X is Always block until the user chooses (spec 6.6), and inert until it is agreed to.
        val fresh = SettingsModel()
        assertEquals(Mode.ALWAYS, fresh.mode(x))
        assertEquals(all, ServiceConfig.plan(all, fresh.modesFor(all), all).packages)
        assertEquals(listOf(yt, ig), ServiceConfig.plan(all, fresh.modesFor(all), listOf(yt, ig)).packages)
        // Only X on, but X not agreed: nothing active, the full list kept.
        val xWaiting = ServiceConfig.plan(all, mapOf(yt to Mode.OFF, ig to Mode.OFF, x to Mode.ALWAYS), listOf(yt, ig))
        assertFalse(xWaiting.active)
        assertEquals(all, xWaiting.packages)
        assertFalse(ServiceConfig.mayRead(xWaiting, x))
    }

    @Test fun anEmptyWatchedListIsRefused() {
        assertThrows(IllegalArgumentException::class.java) { ServiceConfig.plan(emptyList(), emptyMap(), listOf(yt)) }
    }

    @Test fun theWindowCheckReadsOnlyActiveWatchedPackages() {
        val on = ServiceConfig.plan(watched, mapOf(yt to Mode.ALWAYS), watched)
        assertTrue(ServiceConfig.mayRead(on, yt))
        assertFalse(ServiceConfig.mayRead(on, "com.example.bank"))
        assertFalse(ServiceConfig.mayRead(on, "com.android.launcher3"))
        assertFalse(ServiceConfig.mayRead(on, null))
        val off = ServiceConfig.plan(watched, mapOf(yt to Mode.OFF), watched)
        assertFalse(ServiceConfig.mayRead(off, yt))
        val noConsent = ServiceConfig.plan(watched, mapOf(yt to Mode.ALWAYS), emptyList())
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
