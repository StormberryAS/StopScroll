package no.stormberry.stopscroll

/*
 * The short on-screen message that says why the screen changed (spec 6.5, step 1, and the loop
 * guard). Decision D2 (Marcos, 30 October 2026): Android drops a Toast from an app whose
 * notifications are off, and StopScroll asks for no notification permission, so on the Pixel the
 * Toast never showed. The service now draws the message itself in an Accessibility overlay window
 * (NoticeWindow), which needs no permission at all. The texts are the Toast's (strings.xml
 * toast_closed and toast_paused).
 *
 * This file is the pure part, unit-tested in NoticeTest: which message a ladder step shows, for how
 * long, the one-message slot (the newest replaces the one on screen) and the colours and sizes.
 * NoticeWindow does the drawing and the timers.
 */

/** The two messages, and how long each stays at least (about a short Toast, the pause one longer). */
enum class NoticeKind(val baseMs: Long) {
    /** "StopScroll closed %1$s", with the rules' feedName (YouTube Shorts, Instagram Reels, the X video feed). */
    CLOSED(2_000L),

    /** "StopScroll paused for 30 seconds in %1$s ...", with the app name: it asks the user to act. */
    PAUSED(5_000L),
    ;

    /** The string resource; the argument is [NoticeRequest.name]. */
    val stringRes: Int
        get() = when (this) {
            CLOSED -> R.string.toast_closed
            PAUSED -> R.string.toast_paused
        }

    /**
     * How long it stays: [baseMs], or longer when the user asked Android for more time to read
     * ("Time to take action", AccessibilityManager.getRecommendedTimeoutMillis). Never shorter.
     */
    fun durationMs(recommended: (Long) -> Long): Long = maxOf(baseMs, recommended(baseMs))
}

data class NoticeRequest(val kind: NoticeKind, val name: String)

object Notices {
    /** The message a ladder step shows, or null: step 1 of a run, and a failure that paused the app. */
    fun forStep(app: AppRules, step: LadderStep): NoticeRequest? =
        when (step) {
            is LadderStep.Press -> if (step.startsRun) NoticeRequest(NoticeKind.CLOSED, app.feedName) else null
            is LadderStep.Failed -> if (step.paused) NoticeRequest(NoticeKind.PAUSED, app.name) else null
            LadderStep.Nothing, LadderStep.Closed -> null
        }
}

/**
 * One message on screen at a time. The newest replaces the one showing; the same words again keep
 * the window (no flicker and no second TalkBack announcement) and only push the end out. Pure: the
 * clock is injected, and NoticeWindow draws and removes what this says.
 */
class NoticeSlot(private val now: () -> Long) {
    data class Showing(val message: String, val hideAt: Long)

    enum class Change {
        /** Draw a new window (removing any old one). */
        DRAW,

        /** Keep the window on screen; only the end moved. */
        KEEP,
    }

    var showing: Showing? = null
        private set

    fun show(message: String, durationMs: Long): Change {
        require(durationMs > 0)
        val end = now() + durationMs
        val current = showing
        return if (current != null && current.message == message) {
            showing = current.copy(hideAt = maxOf(current.hideAt, end))
            Change.KEEP
        } else {
            showing = Showing(message, end)
            Change.DRAW
        }
    }

    /** The time left, or null when nothing is showing. At least 1 ms while something is. */
    fun remainingMs(): Long? = showing?.let { maxOf(1L, it.hideAt - now()) }

    /** The hide timer fired: true when the message must go now; false when it was pushed out (post again). */
    fun expire(): Boolean {
        val current = showing ?: return false
        if (now() < current.hideAt) return false
        showing = null
        return true
    }

    fun clear() {
        showing = null
    }
}

/** Colours and sizes of the message, chosen for the light or the dark theme (contrast pinned by NoticeTest). */
object NoticeStyle {
    data class Palette(val background: Int, val outline: Int, val textColour: Int)

    /** Light theme: a light card with dark words, outlined so it stands out on a white screen. */
    val LIGHT = Palette(background = 0xFFF4F2FA.toInt(), outline = 0xFF79747E.toInt(), textColour = 0xFF1C1B1F.toInt())

    /** Dark theme: a dark card with light words, outlined so it stands out on a black screen. */
    val DARK = Palette(background = 0xFF2B2930.toInt(), outline = 0xFF938F99.toInt(), textColour = 0xFFF4EFF4.toInt())

    fun palette(night: Boolean): Palette = if (night) DARK else LIGHT

    /** In sp, so it follows the font size setting. */
    const val TEXT_SP = 14f
    const val PADDING_H_DP = 16
    const val PADDING_V_DP = 10
    const val CORNER_DP = 16
    const val OUTLINE_DP = 1

    /** Above the navigation bar (NoticeWindow adds the bar's height itself, P1 of 0.0.3), about where a Toast sits. */
    const val BOTTOM_GAP_DP = 48

    /** The gap kept on each side, and the widest the card gets on a large screen. */
    const val SIDE_MARGIN_DP = 24
    const val MAX_WIDTH_DP = 480

    fun maxWidthPx(screenWidthPx: Int, density: Float): Int {
        val margins = (2 * SIDE_MARGIN_DP * density).toInt()
        val cap = (MAX_WIDTH_DP * density).toInt()
        return maxOf(1, minOf(screenWidthPx - margins, cap))
    }
}
