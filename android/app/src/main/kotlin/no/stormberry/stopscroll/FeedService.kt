package no.stormberry.stopscroll

import android.accessibilityservice.AccessibilityService
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent

/*
 * The Accessibility Service (spec 6). It does four things and nothing else:
 *   - keeps Android's filter right: packageNames limited to the watched apps that are on and agreed
 *     to, never empty; event types 0 while no such app exists (ServiceConfig, Consent.agreedApps);
 *   - on a window or content change from a watched app, checks the screen through NodeFacts (the
 *     window check first, then only the IDs the rules name, and in X the short tag walk) and the
 *     rules (ScreenRules);
 *   - when the feed is open, runs the leave ladder (LeaveLadder): Back, Back, the tab or Home, Home
 *     (X's viewer has no tab bar, so there it is Back, Back, Home);
 *   - shows a short on-screen message saying why the screen changed (Notice.kt, NoticeWindow: its own
 *     Accessibility overlay window, because Android drops a Toast from an app without notifications,
 *     decision D2), and counts what it did (Settings).
 * It never touches a node itself, never reads text and never logs. It never acts while the screen
 * is off or the lock screen shows. It keeps the Accessibility cache off (NodeFacts clears it after
 * every check; here at connect and whenever the plan is not active).
 */
class FeedService : AccessibilityService() {
    private val main = Handler(Looper.getMainLooper())
    private lateinit var rules: ScreenRules
    private var plan: ServicePlan? = null
    private val ladders = HashMap<String, LeaveLadder>()
    private val throttle = CheckThrottle(SystemClock::uptimeMillis)
    private var pendingCheck: Runnable? = null
    private var pendingRecheck: Runnable? = null
    private val notice = NoticeWindow(this, main)
    private val onSettings: () -> Unit = { applyPlan() }
    private val screenOff = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            cancelTimers()
            ladders.values.forEach { it.reset() }
            notice.hide()
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        rules = Rules.load(this)
        running = this
        SettingsStore.addListener(onSettings)
        // SCREEN_OFF is a protected system broadcast; the platform call, not androidx's shim, whose
        // permission the manifest removes (spec 6.6).
        registerReceiver(screenOff, IntentFilter(Intent.ACTION_SCREEN_OFF), Context.RECEIVER_NOT_EXPORTED)
        // No Accessibility cache, so no other app's events reach this process (see NodeFacts).
        setCacheEnabled(false)
        applyPlan()
    }

    /** Applies the plan to the CURRENT service info; never a new one (an info without its list means every app). */
    private fun applyPlan() {
        val s = SettingsStore.get(this)
        val next = ServiceConfig.plan(rules.packages, s.modesFor(rules.packages), Consent.agreedApps(s.consent, rules.packages))
        check(next.packages.isNotEmpty())
        val info = serviceInfo ?: return
        info.packageNames = next.packages.toTypedArray()
        info.eventTypes = next.eventTypes
        info.notificationTimeout = 0
        serviceInfo = info
        plan = next
        if (!next.active) {
            cancelTimers()
            ladders.values.forEach { it.reset() }
            setCacheEnabled(false)
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val p = plan ?: return
        if (!ServiceConfig.mayRead(p, event.packageName?.toString())) return
        val delay = throttle.delayFor(event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) ?: return
        pendingCheck?.let { main.removeCallbacks(it) }
        val r = Runnable {
            pendingCheck = null
            throttle.ran()
            check()
        }
        pendingCheck = r
        main.postDelayed(r, delay)
    }

    /** Android asks the service to stop its feedback: the on-screen message goes too. */
    override fun onInterrupt() = notice.hide()

    /** Theme, font size or rotation changed: the message on screen is drawn again to match. */
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        notice.redraw()
    }

    private fun screenUsable(): Boolean {
        val power = getSystemService(PowerManager::class.java)
        val keyguard = getSystemService(KeyguardManager::class.java)
        return power?.isInteractive == true && keyguard?.isKeyguardLocked != true
    }

    /** One look at the screen: the feed, or not; may start a run of the ladder. */
    private fun check() {
        val p = plan ?: return
        if (!p.active || !screenUsable()) return
        val look = NodeFacts.look(this) { pkg -> if (ServiceConfig.mayRead(p, pkg)) rules.app(pkg) else null }
        val snapshot = (look as? NodeFacts.Look.Read)?.snapshot ?: return
        val verdict = rules.evaluate(snapshot) ?: return
        val pkg = verdict.app.packageName
        val ladder = ladders.getOrPut(pkg) { LeaveLadder(SystemClock::elapsedRealtime) }
        perform(verdict.app, ladder, ladder.onFeedSeen())
    }

    private fun perform(app: AppRules, ladder: LeaveLadder, step: LadderStep) {
        when (step) {
            LadderStep.Nothing -> Unit
            is LadderStep.Press -> {
                val pressed = when (step.action) {
                    LeaveAction.BACK -> performGlobalAction(GLOBAL_ACTION_BACK)
                    LeaveAction.HOME -> performGlobalAction(GLOBAL_ACTION_HOME)
                    LeaveAction.OTHER_TAB -> {
                        val tab = app.surfaces.firstNotNullOfOrNull { it.otherTab }
                        tab != null && NodeFacts.pressOtherTab(this, app.packageName, tab)
                    }
                }
                if (!pressed && step.action == LeaveAction.OTHER_TAB) {
                    perform(app, ladder, ladder.otherTabUnavailable())
                    return
                }
                scheduleRecheck(app, ladder, step.recheckInMs)
            }
            LadderStep.Closed -> SettingsStore.update(this) { it.withClosed(app.packageName, Today.iso()) }
            is LadderStep.Failed -> if (step.paused) SettingsStore.update(this) { it.withLoopPause(app.packageName) }
        }
        // After the press, so the message never delays Back: step 1 of a run, or the loop guard's pause.
        Notices.forStep(app, step)?.let(notice::show)
    }

    private fun scheduleRecheck(app: AppRules, ladder: LeaveLadder, delayMs: Long) {
        pendingRecheck?.let { main.removeCallbacks(it) }
        val r = Runnable {
            pendingRecheck = null
            val p = plan
            if (p == null || !p.active || !screenUsable()) {
                ladder.reset()
                return@Runnable
            }
            val look = NodeFacts.look(this) { pkg -> if (pkg == app.packageName && ServiceConfig.mayRead(p, pkg)) app else null }
            // Another app in front (the launcher after Home, say) means the feed has gone.
            val still = (look as? NodeFacts.Look.Read)?.snapshot?.let { rules.evaluate(it) } != null
            val hasTab = app.surfaces.any { it.otherTab != null }
            perform(app, ladder, ladder.onRecheck(still, hasTab))
        }
        pendingRecheck = r
        main.postDelayed(r, delayMs)
    }

    private fun cancelTimers() {
        pendingCheck?.let { main.removeCallbacks(it) }
        pendingRecheck?.let { main.removeCallbacks(it) }
        pendingCheck = null
        pendingRecheck = null
        throttle.clear()
    }

    /** The bridge's "Turn StopScroll off": Android's own way for a service to switch itself off. */
    fun turnOff() = disableSelf()

    override fun onUnbind(intent: Intent?): Boolean {
        stop()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        stop()
        super.onDestroy()
    }

    private fun stop() {
        notice.hide()
        if (running !== this) return
        running = null
        cancelTimers()
        SettingsStore.removeListener(onSettings)
        try {
            unregisterReceiver(screenOff)
        } catch (_: IllegalArgumentException) {
        }
    }

    companion object {
        /** The connected service, for the app screen's state and its Turn off button. */
        var running: FeedService? = null
            private set
    }
}

/**
 * Content changes come in bursts: at most one check per [GAP_MS], with a trailing check so the last
 * change of a burst is always looked at. A window state change is checked at once (spec 6.5).
 */
class CheckThrottle(private val now: () -> Long) {
    private var lastRun = Long.MIN_VALUE / 2
    private var scheduled = false

    /** The delay before a check, or null when one is already scheduled (a state change replaces it). */
    fun delayFor(stateChange: Boolean): Long? {
        if (stateChange) {
            scheduled = true
            return 0
        }
        if (scheduled) return null
        scheduled = true
        val wait = lastRun + GAP_MS - now()
        return if (wait > 0) wait else 0
    }

    fun ran() {
        scheduled = false
        lastRun = now()
    }

    fun clear() {
        scheduled = false
    }

    companion object {
        const val GAP_MS = 250L
    }
}
