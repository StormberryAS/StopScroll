package no.stormberry.stopscroll

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The leave sequence and the loop guard, with an injected clock (spec 6.5, review F4). */
class LeaveLadderTest {
    private var t = 1_000_000L
    private val ladder = LeaveLadder { t }

    private fun press(step: LadderStep): LeaveAction = (step as LadderStep.Press).action

    private fun failedRun(hasTab: Boolean = true): LadderStep {
        assertEquals(LeaveAction.BACK, press(ladder.onFeedSeen()))
        t += 700
        assertEquals(LeaveAction.BACK, press(ladder.onRecheck(feedStillThere = true, hasOtherTab = hasTab)))
        t += 700
        assertEquals(if (hasTab) LeaveAction.OTHER_TAB else LeaveAction.HOME, press(ladder.onRecheck(true, hasTab)))
        t += 700
        if (hasTab) {
            assertEquals(LeaveAction.HOME, press(ladder.onRecheck(true, hasTab)))
            t += 700
        }
        return ladder.onRecheck(true, hasTab)
    }

    @Test fun theServiceCanPressOnlyBackHomeAndOneTab() {
        assertEquals(listOf("BACK", "OTHER_TAB", "HOME"), LeaveAction.entries.map { it.name })
    }

    @Test fun theLadderIsBackBackThenTabOrHomeWith700MsBetween() {
        val first = ladder.onFeedSeen() as LadderStep.Press
        assertEquals(LadderStep.Press(LeaveAction.BACK, 700, startsRun = true), first)
        assertEquals(LadderStep.Nothing, ladder.onFeedSeen()) // a run is under way: no second run
        t += 700
        assertEquals(LadderStep.Press(LeaveAction.BACK, 700, startsRun = false), ladder.onRecheck(true, true))
        t += 700
        assertEquals(LadderStep.Press(LeaveAction.OTHER_TAB, 700, startsRun = false), ladder.onRecheck(true, true))
    }

    @Test fun withoutATabStepThreeIsHome() {
        assertEquals(LadderStep.Failed(paused = false), failedRun(hasTab = false))
    }

    @Test fun aTabThatCannotBePressedFallsBackToHome() {
        ladder.onFeedSeen(); t += 700; ladder.onRecheck(true, true); t += 700
        assertEquals(LeaveAction.OTHER_TAB, press(ladder.onRecheck(true, true)))
        assertEquals(LeaveAction.HOME, press(ladder.otherTabUnavailable()))
        t += 700
        // Home was pressed in place of the tab: no fourth press, the run is judged.
        assertEquals(LadderStep.Failed(paused = false), ladder.onRecheck(true, true))
    }

    @Test fun aTabThatDoesNotLeaveTheFeedIsFollowedByHome() {
        ladder.onFeedSeen(); t += 700; ladder.onRecheck(true, true); t += 700
        assertEquals(LeaveAction.OTHER_TAB, press(ladder.onRecheck(true, true)))
        t += 700
        assertEquals(LadderStep.Press(LeaveAction.HOME, 700, startsRun = false), ladder.onRecheck(true, true))
        t += 700
        assertEquals(LadderStep.Closed, ladder.onRecheck(false, true))
        assertFalse(ladder.isRunning())
    }

    @Test fun aTabThatLeavesTheFeedEndsTheRunWithoutHome() {
        ladder.onFeedSeen(); t += 700; ladder.onRecheck(true, true); t += 700
        assertEquals(LeaveAction.OTHER_TAB, press(ladder.onRecheck(true, true)))
        t += 700
        assertEquals(LadderStep.Closed, ladder.onRecheck(false, true))
    }

    @Test fun theFeedGoneIsAClosedRun() {
        ladder.onFeedSeen()
        t += 700
        assertEquals(LadderStep.Closed, ladder.onRecheck(feedStillThere = false, hasOtherTab = true))
        assertFalse(ladder.isRunning())
    }

    @Test fun twoFailedRunsWithin10SecondsPauseFor30Seconds() {
        assertEquals(LadderStep.Failed(paused = false), failedRun())
        t += 1_000
        assertEquals(LadderStep.Failed(paused = true), failedRun())
        assertTrue(ladder.isPaused())
        assertEquals(LadderStep.Nothing, ladder.onFeedSeen())
        t += 29_999
        assertEquals(LadderStep.Nothing, ladder.onFeedSeen())
        t += 1
        assertEquals(LeaveAction.BACK, press(ladder.onFeedSeen()))
    }

    @Test fun failedRunsFurtherApartThan10SecondsDoNotPause() {
        assertEquals(LadderStep.Failed(paused = false), failedRun())
        t += 10_001
        assertEquals(LadderStep.Failed(paused = false), failedRun())
        assertFalse(ladder.isPaused())
    }

    @Test fun aRunThatWorksResetsTheCount() {
        assertEquals(LadderStep.Failed(paused = false), failedRun())
        ladder.onFeedSeen(); t += 700
        assertEquals(LadderStep.Closed, ladder.onRecheck(false, true))
        assertEquals(LadderStep.Failed(paused = false), failedRun())
        assertFalse(ladder.isPaused())
    }

    @Test fun openingTheFeedAgainAndAgainNeverTripsTheGuard() {
        repeat(50) {
            assertEquals(LeaveAction.BACK, press(ladder.onFeedSeen()))
            t += 700
            assertEquals(LadderStep.Closed, ladder.onRecheck(false, true))
            t += 100
        }
        assertFalse(ladder.isPaused())
    }

    @Test fun resetDropsARunWithoutCountingIt() {
        ladder.onFeedSeen()
        ladder.reset()
        assertEquals(LadderStep.Nothing, ladder.onRecheck(true, true))
        assertEquals(LeaveAction.BACK, press(ladder.onFeedSeen()))
    }
}
