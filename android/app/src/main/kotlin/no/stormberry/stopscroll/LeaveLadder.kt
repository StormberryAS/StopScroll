package no.stormberry.stopscroll

/*
 * Leaving the feed (spec 6.5, LEAVE-1 at its default: the full ladder with the loop guard).
 *
 *   step 1  the feed is recognised: Back
 *   step 2  700 ms later, still there: Back again (a comment sheet or a dialog can take the first)
 *   step 3  another 700 ms, still there: the rule's tab if it has one, else Home
 *   step 4  only after a tab press: another 700 ms, still there: Home (the tab can be the one
 *           already selected, which may do nothing)
 *   then    700 ms after the last press the run is judged: the feed gone is a run that worked,
 *           still there a run that failed
 *
 * The loop guard counts FAILED RUNS, never presses (review F4): after 2 failed runs within 10 s in
 * one app, StopScroll leaves that app alone for 30 s and adds 1 to a counter. A run that leaves the
 * feed resets the count, so opening Shorts again and again never earns a free feed.
 *
 * Pure: the clock is injected and the ladder only says what to do; FeedService does it. The only
 * actions there are are the three in LeaveAction, and a unit test pins that list.
 */

/** Everything the service may ever press. Pinned by LeaveLadderTest and tests/guards.test.ts. */
enum class LeaveAction { BACK, OTHER_TAB, HOME }

sealed interface LadderStep {
    /** Do nothing now. */
    data object Nothing : LadderStep

    /** Press [action], then call [LeaveLadder.onRecheck] after [recheckInMs]. [startsRun] marks step 1. */
    data class Press(val action: LeaveAction, val recheckInMs: Long, val startsRun: Boolean) : LadderStep

    /** The feed has gone: the run worked. */
    data object Closed : LadderStep

    /** The run failed; [paused] is true when this failure tripped the loop guard. */
    data class Failed(val paused: Boolean) : LadderStep
}

class LeaveLadder(private val now: () -> Long) {
    private var step = 0 // 0: no run; 1 to 4: the last step pressed
    private var tabPressed = false // step 3 pressed the tab (not Home), so step 4 is still to come
    private val failures = ArrayDeque<Long>()
    private var pausedUntil = Long.MIN_VALUE

    /** True while the loop guard leaves the app alone. */
    fun isPaused(): Boolean = now() < pausedUntil

    /** True while a run waits for its next check; new feed sightings are then left to that check. */
    fun isRunning(): Boolean = step != 0

    /** A check found the feed. Starts a run unless one is under way or the guard has paused. */
    fun onFeedSeen(): LadderStep {
        if (isPaused() || isRunning()) return LadderStep.Nothing
        step = 1
        return LadderStep.Press(LeaveAction.BACK, RECHECK_MS, startsRun = true)
    }

    /** The timed check after a press. [feedStillThere]: what the check found; [hasOtherTab]: whether the rule names one and it can be found. */
    fun onRecheck(feedStillThere: Boolean, hasOtherTab: Boolean): LadderStep {
        if (!isRunning()) return LadderStep.Nothing
        if (!feedStillThere) {
            step = 0
            tabPressed = false
            failures.clear()
            return LadderStep.Closed
        }
        return when (step) {
            1 -> {
                step = 2
                LadderStep.Press(LeaveAction.BACK, RECHECK_MS, startsRun = false)
            }
            2 -> {
                step = 3
                tabPressed = hasOtherTab
                LadderStep.Press(if (hasOtherTab) LeaveAction.OTHER_TAB else LeaveAction.HOME, RECHECK_MS, startsRun = false)
            }
            3 -> if (tabPressed) {
                step = 4
                tabPressed = false
                LadderStep.Press(LeaveAction.HOME, RECHECK_MS, startsRun = false)
            } else {
                failed()
            }
            else -> failed()
        }
    }

    private fun failed(): LadderStep {
        step = 0
        tabPressed = false
        val t = now()
        failures.addLast(t)
        while (failures.isNotEmpty() && t - failures.first() > FAILURE_WINDOW_MS) failures.removeFirst()
        if (failures.size >= FAILURES_TO_PAUSE) {
            failures.clear()
            pausedUntil = t + PAUSE_MS
            return LadderStep.Failed(paused = true)
        }
        return LadderStep.Failed(paused = false)
    }

    /** The tab press could not be made (no such node now): Home instead, as step 3 allows. */
    fun otherTabUnavailable(): LadderStep {
        if (step != 3) return LadderStep.Nothing
        tabPressed = false
        return LadderStep.Press(LeaveAction.HOME, RECHECK_MS, startsRun = false)
    }

    /** The app left the screen, the screen went off or the user switched the app Off: drop the run. */
    fun reset() {
        step = 0
        tabPressed = false
    }

    companion object {
        const val RECHECK_MS = 700L
        const val FAILURE_WINDOW_MS = 10_000L
        const val FAILURES_TO_PAUSE = 2
        const val PAUSE_MS = 30_000L
    }
}
