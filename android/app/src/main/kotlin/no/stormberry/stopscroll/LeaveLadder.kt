package no.stormberry.stopscroll

/*
 * Leaving the feed (spec 6.5, LEAVE-1 at its default: the full ladder with the loop guard). Two
 * orders, chosen by the surface's rule (`"leave"`, rules schema 3, 0.0.4):
 *
 *   BACK FIRST (YouTube, Instagram and X; the default)
 *   step 1  the feed is recognised: Back
 *   step 2  700 ms later, still there: Back again (a comment sheet or a dialog can take the first)
 *   step 3  another 700 ms, still there: the rule's tab if it has one, else Home
 *   step 4  only after a tab press: another 700 ms, still there: Home (the tab can be the one
 *           already selected, which may do nothing)
 *
 *   OTHER TAB FIRST (YouTube Music's Samples, YTM-LEAVE-1 a of 31 October 2026: on the Samples tab
 *   Back closes YouTube Music and leaves the user's music paused; the Home tab closes Samples inside
 *   the app and the music carries on)
 *   step 1  the feed is recognised: the rule's tab; if it cannot be found, Back
 *   step 2  700 ms later, still there: Back (it closes a panel that took the tab press)
 *   step 3  another 700 ms, still there: the rule's tab again; if it cannot be found, Home
 *   step 4  only after a tab press at step 3: another 700 ms, still there: Home
 *
 *   then    700 ms after the last press the run is judged: the feed gone is a run that worked,
 *           still there a run that failed. Both orders press at most four times, and Home, which
 *           leaves the app, comes last.
 *
 * The loop guard counts FAILED RUNS, never presses (review F4): after 2 failed runs within 10 s in
 * one app, StopScroll leaves that app alone for 30 s and adds 1 to a counter. A run that leaves the
 * feed resets the count, so opening Shorts again and again never earns a free feed.
 *
 * Pure: the clock is injected and the ladder only says what to do; FeedService does it. The only
 * actions there are are the three in LeaveAction, and a unit test pins that list: the second order
 * uses the same three actions, only in another order.
 */

/** The order a run presses in, from the surface's rule (`"leave"`: `"back"` or `"otherTabFirst"`). */
enum class LeaveOrder { BACK_FIRST, OTHER_TAB_FIRST }

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
    private var order = LeaveOrder.BACK_FIRST // the order of the run under way
    private var tabPressed = false // step 3 pressed the tab (not Home), so step 4 is still to come
    private val failures = ArrayDeque<Long>()
    private var pausedUntil = Long.MIN_VALUE

    /** True while the loop guard leaves the app alone. */
    fun isPaused(): Boolean = now() < pausedUntil

    /** True while a run waits for its next check; new feed sightings are then left to that check. */
    fun isRunning(): Boolean = step != 0

    /**
     * A check found the feed. Starts a run in [order] (the surface's rule) unless one is under way or
     * the guard has paused.
     */
    fun onFeedSeen(order: LeaveOrder = LeaveOrder.BACK_FIRST): LadderStep {
        if (isPaused() || isRunning()) return LadderStep.Nothing
        step = 1
        this.order = order
        tabPressed = false
        val first = if (order == LeaveOrder.OTHER_TAB_FIRST) LeaveAction.OTHER_TAB else LeaveAction.BACK
        return LadderStep.Press(first, RECHECK_MS, startsRun = true)
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

    /**
     * The tab press could not be made (no such node now). At step 3: Home instead, as step 3 allows.
     * At step 1 of a tab-first run: Back instead, still the start of the run (so its message shows
     * once), and the run goes on as Back, then the tab or Home, then Home.
     */
    fun otherTabUnavailable(): LadderStep = when {
        step == 3 -> {
            tabPressed = false
            LadderStep.Press(LeaveAction.HOME, RECHECK_MS, startsRun = false)
        }
        step == 1 && order == LeaveOrder.OTHER_TAB_FIRST -> LadderStep.Press(LeaveAction.BACK, RECHECK_MS, startsRun = true)
        else -> LadderStep.Nothing
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
