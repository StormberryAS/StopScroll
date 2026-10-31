package no.stormberry.stopscroll

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

/** The on-screen message that replaced the Toast (decision D2): which words, how long, one at a time, readable. */
class NoticeTest {
    private var t = 50_000L
    private val slot = NoticeSlot { t }

    private val youTube = Repo.rules.apps.single { it.id == "youtube" }
    private val instagram = Repo.rules.apps.single { it.id == "instagram" }
    private val x = Repo.rules.apps.single { it.id == "x" }

    /** strings.xml's text for [kind], formatted as Android's getString(id, name) does. */
    private fun words(request: NoticeRequest): String {
        val name = when (request.kind.stringRes) {
            R.string.toast_closed -> "toast_closed"
            R.string.toast_paused -> "toast_paused"
            else -> error("unknown string")
        }
        val strings = Repo.xml("android/app/src/main/res/values/strings.xml").getElementsByTagName("string")
        val template = (0 until strings.length).map { strings.item(it) as org.w3c.dom.Element }.single { it.getAttribute("name") == name }.textContent
        return String.format(template, request.name)
    }

    @Test fun step1OfARunShowsTheClosedMessageWithTheFeedName() {
        val step = LadderStep.Press(LeaveAction.BACK, 700, startsRun = true)
        assertEquals(NoticeRequest(NoticeKind.CLOSED, "YouTube Shorts"), Notices.forStep(youTube, step))
        assertEquals(NoticeRequest(NoticeKind.CLOSED, "Instagram Reels"), Notices.forStep(instagram, step))
        assertEquals("StopScroll closed YouTube Shorts", words(Notices.forStep(youTube, step)!!))
        assertEquals("StopScroll closed Instagram Reels", words(Notices.forStep(instagram, step)!!))
        assertEquals(NoticeRequest(NoticeKind.CLOSED, "the X video feed"), Notices.forStep(x, step))
        assertEquals("StopScroll closed the X video feed", words(Notices.forStep(x, step)!!))
    }

    @Test fun theLoopGuardsPauseShowsThePauseMessageWithTheAppName() {
        val step = LadderStep.Failed(paused = true)
        assertEquals(NoticeRequest(NoticeKind.PAUSED, "Instagram"), Notices.forStep(instagram, step))
        assertEquals(
            "StopScroll paused for 30 seconds in Instagram. If this keeps happening, please tell us.",
            words(Notices.forStep(instagram, step)!!),
        )
        assertEquals(
            "StopScroll paused for 30 seconds in YouTube. If this keeps happening, please tell us.",
            words(Notices.forStep(youTube, step)!!),
        )
        assertEquals(
            "StopScroll paused for 30 seconds in X. If this keeps happening, please tell us.",
            words(Notices.forStep(x, step)!!),
        )
    }

    @Test fun noOtherStepShowsAnything() {
        for (step in listOf(
            LadderStep.Nothing,
            LadderStep.Closed,
            LadderStep.Failed(paused = false),
            LadderStep.Press(LeaveAction.BACK, 700, startsRun = false),
            LadderStep.Press(LeaveAction.OTHER_TAB, 700, startsRun = false),
            LadderStep.Press(LeaveAction.HOME, 700, startsRun = false),
        )) {
            assertNull("$step", Notices.forStep(instagram, step))
        }
    }

    @Test fun theClosedMessageStaysAbout2SecondsAndThePauseMessageAbout5() {
        assertEquals(2_000L, NoticeKind.CLOSED.durationMs { it })
        assertEquals(5_000L, NoticeKind.PAUSED.durationMs { it })
    }

    @Test fun theUsersLongerReadingTimeWinsAndNothingShortensTheMessage() {
        assertEquals(10_000L, NoticeKind.CLOSED.durationMs { 10_000L })
        assertEquals(10_000L, NoticeKind.PAUSED.durationMs { 10_000L })
        assertEquals(2_000L, NoticeKind.CLOSED.durationMs { 500L })
    }

    @Test fun aMessageIsDrawnAndGoesWhenItsTimeIsUp() {
        assertNull(slot.remainingMs())
        assertEquals(NoticeSlot.Change.DRAW, slot.show("a", 2_000))
        assertEquals(2_000L, slot.remainingMs())
        t += 1_999
        assertFalse(slot.expire())
        assertEquals(1L, slot.remainingMs())
        t += 1
        assertTrue(slot.expire())
        assertNull(slot.showing)
        assertFalse(slot.expire()) // nothing left to remove
    }

    @Test fun aNewMessageReplacesTheOneOnScreenWithItsOwnTime() {
        slot.show("closed", 2_000)
        t += 1_000
        assertEquals(NoticeSlot.Change.DRAW, slot.show("paused", 5_000))
        assertEquals(NoticeSlot.Showing("paused", t + 5_000), slot.showing)
        t += 4_999
        assertFalse(slot.expire())
        t += 1
        assertTrue(slot.expire())
    }

    @Test fun theSameWordsAgainKeepTheWindowAndOnlyPushTheEndOut() {
        slot.show("closed", 2_000)
        t += 1_500
        assertEquals(NoticeSlot.Change.KEEP, slot.show("closed", 2_000))
        assertEquals(2_000L, slot.remainingMs())
        // The timer posted for the first end fires early: not yet, post again for the rest.
        t += 500
        assertFalse(slot.expire())
        assertEquals(1_500L, slot.remainingMs())
        t += 1_500
        assertTrue(slot.expire())
    }

    @Test fun theSameWordsNeverShortenAMessage() {
        slot.show("closed", 5_000)
        t += 100
        assertEquals(NoticeSlot.Change.KEEP, slot.show("closed", 2_000))
        assertEquals(4_900L, slot.remainingMs())
    }

    @Test fun clearRemovesAtOnceAndADurationMustBePositive() {
        slot.show("a", 2_000)
        slot.clear()
        assertNull(slot.showing)
        assertNull(slot.remainingMs())
        assertEquals(NoticeSlot.Change.DRAW, slot.show("a", 2_000)) // drawn again after a clear
        try {
            slot.show("b", 0)
            error("a zero duration was accepted")
        } catch (_: IllegalArgumentException) {
        }
    }

    /** WCAG 2 contrast ratio. */
    private fun contrast(a: Int, b: Int): Double {
        fun lum(c: Int): Double {
            fun ch(v: Int): Double = (v / 255.0).let { if (it <= 0.03928) it / 12.92 else ((it + 0.055) / 1.055).pow(2.4) }
            return 0.2126 * ch((c shr 16) and 0xFF) + 0.7152 * ch((c shr 8) and 0xFF) + 0.0722 * ch(c and 0xFF)
        }
        val (hi, lo) = listOf(lum(a), lum(b)).sortedDescending()
        return (hi + 0.05) / (lo + 0.05)
    }

    @Test fun theWordsAreReadableInTheLightAndTheDarkTheme() {
        for ((night, p) in listOf(false to NoticeStyle.LIGHT, true to NoticeStyle.DARK)) {
            assertEquals(p, NoticeStyle.palette(night))
            assertEquals("opaque", 0xFF, (p.background ushr 24) and 0xFF)
            assertEquals("opaque", 0xFF, (p.textColour ushr 24) and 0xFF)
            assertTrue("text contrast ${contrast(p.textColour, p.background)}", contrast(p.textColour, p.background) >= 7.0)
        }
        // The outline sets the card apart from a white screen (light) and a black one (dark).
        assertTrue(contrast(NoticeStyle.LIGHT.outline, 0xFFFFFFFF.toInt()) >= 3.0)
        assertTrue(contrast(NoticeStyle.DARK.outline, 0xFF000000.toInt()) >= 3.0)
        assertTrue(NoticeStyle.TEXT_SP >= 14f)
    }

    @Test fun theCardKeepsItsMarginsAndStaysNarrowOnALargeScreen() {
        assertEquals(1080 - 2 * 24 * 3, NoticeStyle.maxWidthPx(1080, 3f)) // a phone at 3x
        assertEquals(480 * 2, NoticeStyle.maxWidthPx(2560, 2f)) // a tablet: capped at 480 dp
        assertEquals(1, NoticeStyle.maxWidthPx(10, 3f)) // never zero or negative
    }
}
