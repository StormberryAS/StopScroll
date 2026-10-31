package no.stormberry.stopscroll

import android.accessibilityservice.AccessibilityService
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.SystemClock
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import android.view.accessibility.AccessibilityManager
import android.widget.TextView

/*
 * Draws the short on-screen message (Notice.kt, decision D2) in a window of the service's own:
 * TYPE_ACCESSIBILITY_OVERLAY, which an Accessibility Service may add without any permission (no
 * SYSTEM_ALERT_WINDOW, no notification permission; tests/guards.test.ts pins both).
 *   - Never in the way: FLAG_NOT_FOCUSABLE and FLAG_NOT_TOUCHABLE, so it never takes a tap, a key or
 *     the focus, and Back still goes to the app below. Android treats an Accessibility overlay as a
 *     trusted overlay, so taps on the app below pass through it too.
 *   - Placed like a Toast: bottom centre, [NoticeStyle.BOTTOM_GAP_DP] above the navigation bar,
 *     Android's Toast animation. The window asks Android to fit no insets (fitInsetsTypes 0), so its
 *     frame ends at the bottom of the screen on every Android version, and the navigation bar's
 *     height is added to y here (decision P1 of 0.0.3). Without that the card covered the top of the
 *     apps' tab bars (seen on Android 16 and 17, where Android applied no inset to this overlay); with
 *     Android's default fitting as well, Android 13 and 14 could inset it a second time.
 *   - Readable: light or dark card from the current theme, the text in sp (it follows the font size),
 *     drawn again on a configuration change (theme, font size, rotation) with the same words and end.
 *   - Announced: the card is an accessibility pane titled with the message, so TalkBack says it when
 *     it appears (the replacement for the deprecated announceForAccessibility).
 *   - Removed: when its time is up, when a new message replaces it, when the screen goes off and when
 *     the service stops. One window at most, held only here; nothing outlives the service.
 * It writes its own text and reads none: this file never sees another app's screen (only NodeFacts.kt
 * does), and every text API it uses is an assignment to its own TextView (tests/guards.test.ts).
 * Everything runs on the main thread.
 */
class NoticeWindow(private val service: AccessibilityService, private val main: Handler) {
    private val slot = NoticeSlot(SystemClock::uptimeMillis)
    private var view: TextView? = null
    private var drawnFor: Look? = null
    private val timer = Runnable { onTimer() }

    /** What the card's look depends on: the theme, the font scale and the screen width and density. */
    private data class Look(val uiMode: Int, val fontScale: Float, val widthPx: Int, val densityDpi: Int)

    private fun currentLook(): Look {
        val res = service.resources
        val c = res.configuration
        return Look(c.uiMode and Configuration.UI_MODE_NIGHT_MASK, c.fontScale, res.displayMetrics.widthPixels, res.displayMetrics.densityDpi)
    }

    /** Shows [request]'s message, replacing the one on screen (the same words only stay longer). */
    fun show(request: NoticeRequest) {
        val message = service.getString(request.kind.stringRes, request.name)
        val change = slot.show(message, request.kind.durationMs(::recommendedMs))
        if (change == NoticeSlot.Change.DRAW) draw(announce = true)
        schedule()
    }

    /**
     * After a configuration change: the same words and end, drawn for the new theme, size and font
     * scale. Only when the card would look different, and without the Toast animation, so a change
     * that does not touch the card (or a redraw) never makes it flicker.
     */
    fun redraw() {
        if (slot.showing == null || view == null) return
        val look = currentLook()
        if (look != drawnFor) draw(announce = false, animate = false, look = look)
    }

    /** Removes the message now, if there is one. Safe to call at any time and more than once. */
    fun hide() {
        main.removeCallbacks(timer)
        slot.clear()
        remove(immediate = true)
        drawnFor = null
    }

    private fun schedule() {
        main.removeCallbacks(timer)
        slot.remainingMs()?.let { main.postDelayed(timer, it) }
    }

    private fun onTimer() {
        if (slot.expire()) remove(immediate = false) else schedule()
    }

    private fun recommendedMs(ms: Long): Long {
        val manager = service.getSystemService(AccessibilityManager::class.java) ?: return ms
        return manager.getRecommendedTimeoutMillis(ms.toInt(), AccessibilityManager.FLAG_CONTENT_TEXT).toLong()
    }

    private fun draw(announce: Boolean, animate: Boolean = true, look: Look = currentLook()) {
        remove(immediate = true)
        val message = slot.showing?.message ?: return
        val wm = service.getSystemService(WindowManager::class.java) ?: return
        val label = card(message, announce)
        drawnFor = look
        try {
            wm.addView(label, params(animate, wm))
            view = label
        } catch (_: RuntimeException) {
            // BadTokenException (the service is going away), InvalidDisplayException, an "unknown
            // error code" or a dead system server: no message, nothing to remove, and the blocking
            // goes on, because the press already ran (FeedService shows the message after it).
        }
    }

    private fun remove(immediate: Boolean) {
        val v = view ?: return
        view = null
        val wm = service.getSystemService(WindowManager::class.java) ?: return
        try {
            if (immediate) wm.removeViewImmediate(v) else wm.removeView(v)
        } catch (_: RuntimeException) {
            // Already gone with the service's windows (IllegalArgumentException), or the system
            // server failed: either way the view is no longer held here.
        }
    }

    private fun card(message: String, announce: Boolean): TextView {
        val res = service.resources
        val night = (res.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val palette = NoticeStyle.palette(night)
        val density = res.displayMetrics.density
        fun dp(v: Int): Int = (v * density + 0.5f).toInt()
        val label = TextView(service)
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, NoticeStyle.TEXT_SP)
        label.setTextColor(palette.textColour)
        label.maxWidth = NoticeStyle.maxWidthPx(res.displayMetrics.widthPixels, density)
        label.setPadding(dp(NoticeStyle.PADDING_H_DP), dp(NoticeStyle.PADDING_V_DP), dp(NoticeStyle.PADDING_H_DP), dp(NoticeStyle.PADDING_V_DP))
        label.background = GradientDrawable().apply {
            cornerRadius = dp(NoticeStyle.CORNER_DP).toFloat()
            setColor(palette.background)
            setStroke(dp(NoticeStyle.OUTLINE_DP), palette.outline)
        }
        label.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
        label.text = message
        if (announce) label.accessibilityPaneTitle = message
        return label
    }

    private fun params(animate: Boolean, wm: WindowManager): WindowManager.LayoutParams {
        val density = service.resources.displayMetrics.density
        // The navigation bar's height, even while an app hides it (0 if Android cannot say).
        val navBar = try {
            wm.currentWindowMetrics.windowInsets.getInsetsIgnoringVisibility(WindowInsets.Type.navigationBars()).bottom
        } catch (_: RuntimeException) {
            0
        }
        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            y = navBar + (NoticeStyle.BOTTOM_GAP_DP * density + 0.5f).toInt()
            windowAnimations = if (animate) android.R.style.Animation_Toast else 0
            // No inset fitting by Android, on any version: y above is the only inset, so the card sits
            // the same distance above the navigation bar's place, even while an app hides the bar.
            fitInsetsTypes = 0
        }
    }
}
