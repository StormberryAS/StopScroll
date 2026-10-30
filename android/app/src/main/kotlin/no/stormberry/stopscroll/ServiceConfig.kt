package no.stormberry.stopscroll

/*
 * What the service asks Android for (spec 6.1, review B1).
 *
 * THE TRAP: in AOSP an empty packageNames list means EVERY app
 * (AbstractAccessibilityServiceConnection: packageNames.isEmpty() || packageNames.contains(...)), and
 * setServiceInfo with a null list clears it. So the list is never empty and never null:
 *   - only the user's manual Off removes a package;
 *   - when no app is on, or consent is missing, the list stays non-empty (every package the rules
 *     cover) and the event types drop to 0, because canReceiveEventsLocked() is false when
 *     mEventTypes == 0;
 *   - FeedService applies a plan to the service's CURRENT AccessibilityServiceInfo and never builds
 *     a new one (tests/guards.test.ts fails on a constructor call).
 * ServiceConfigTest runs every combination of modes and consent and fails if any plan is empty.
 */

enum class Mode(val wire: String) {
    OFF("off"),
    ALWAYS("always");

    companion object {
        fun fromWire(s: String?): Mode? = entries.firstOrNull { it.wire == s }
    }
}

data class ServicePlan(
    /** Never empty: see the trap above. */
    val packages: List<String>,
    /** 0 when the service must receive nothing. */
    val eventTypes: Int,
) {
    val active: Boolean get() = eventTypes != 0
}

object ServiceConfig {
    // AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED and TYPE_WINDOW_CONTENT_CHANGED, written out so
    // this file needs no Android type and runs in the JVM tests; ServiceConfigTest checks the values.
    const val TYPE_WINDOW_STATE_CHANGED = 0x00000020
    const val TYPE_WINDOW_CONTENT_CHANGED = 0x00000800
    const val EVENT_TYPES = TYPE_WINDOW_STATE_CHANGED or TYPE_WINDOW_CONTENT_CHANGED

    /**
     * The plan for [watched] (the packages the rules cover, never empty), the user's [modes] and
     * whether a valid [consent] is stored.
     */
    fun plan(watched: List<String>, modes: Map<String, Mode>, consent: Boolean): ServicePlan {
        require(watched.isNotEmpty()) { "the watched list is empty; an empty packageNames means every app" }
        val on = watched.filter { modes[it] == Mode.ALWAYS }
        return if (!consent || on.isEmpty()) {
            ServicePlan(watched.toList(), 0)
        } else {
            ServicePlan(on, EVENT_TYPES)
        }
    }

    /** True when the service may read past the package name of a window from [packageName]. */
    fun mayRead(plan: ServicePlan, packageName: String?): Boolean =
        plan.active && packageName != null && packageName in plan.packages
}
