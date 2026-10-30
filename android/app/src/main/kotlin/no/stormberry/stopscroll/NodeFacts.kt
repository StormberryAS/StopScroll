package no.stormberry.stopscroll

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo

/*
 * THE ONLY FILE THAT TOUCHES ANOTHER APP'S SCREEN (spec 6.2, 6.3). tests/guards.test.ts fails the
 * build if AccessibilityNodeInfo or getRootInActiveWindow appears in any other source file, and if
 * any text API (text, contentDescription, hintText, stateDescription, tooltipText, paneTitle, error,
 * extras and the rest, in property or getter form, or ByText lookups) appears in any source file at
 * all, or if a node or event is printed: stage 1 reads no text, not even here.
 *
 * What it does, in order:
 *   1. fetches the active window's root with getRootInActiveWindow(0): API 33, no prefetching, so
 *      only the root node crosses into this process, never its neighbours (MIN-1);
 *   2. reads the root's PACKAGE NAME and stops there, reading nothing else, unless the caller says
 *      the package may be read (the window check: packageNames limits events, not window reading);
 *   3. looks up only the view IDs the rules name, with findAccessibilityNodeInfosByViewId, one call
 *      per ID that returns only matching nodes;
 *   4. turns each into a NodeFact: ID, class, bounds as fractions of the screen, visible, selected.
 *      NodeFact has no text field.
 * It also makes the one press inside another app the service may make: ACTION_CLICK on the first
 * visible, clickable node under the rule's otherTab parent, at most [TAB_DEPTH] levels down (spec
 * 6.5, step 3; in YouTube the tab Buttons sit one level under a wrapper). Nothing here is ever logged.
 *
 * THE CACHE. Every lookup makes Android set "this service uses the Accessibility cache", and from
 * then on Android also sends this process screen-change events from EVERY app for that cache, until
 * the flag is cleared. So both functions end with setCacheEnabled(false) in a finally block: the
 * flag is set only for the few milliseconds of one check, and the in-process cache is emptied and
 * kept off (AccessibilityService.setCacheEnabled, API 33, the minSdk). tests/guards.test.ts pins it.
 */
object NodeFacts {
    /** What a look at the active window found. */
    sealed interface Look {
        /** No window, or a window the caller may not read: nothing but its package name was read. */
        data object NotReadable : Look

        data class Read(val snapshot: Snapshot) : Look
    }

    /**
     * Looks at the active window. [rulesFor] returns the rules for a package that may be read, or
     * null; it sees only the package name.
     */
    fun look(service: AccessibilityService, rulesFor: (String) -> AppRules?): Look =
        try {
            lookOnce(service, rulesFor)
        } finally {
            service.setCacheEnabled(false)
        }

    private fun lookOnce(service: AccessibilityService, rulesFor: (String) -> AppRules?): Look {
        val root = service.getRootInActiveWindow(0) ?: return Look.NotReadable
        val pkg = root.packageName?.toString() ?: return Look.NotReadable
        val app = rulesFor(pkg) ?: return Look.NotReadable
        val screen = Rect()
        root.getBoundsInScreen(screen)
        if (screen.width() <= 0 || screen.height() <= 0) return Look.NotReadable
        val facts = ArrayList<NodeFact>()
        for (id in app.referencedIds) {
            for (node in root.findAccessibilityNodeInfosByViewId("$pkg:id/$id")) {
                facts += fact(node, id, screen)
            }
        }
        return Look.Read(Snapshot(pkg, facts))
    }

    /** Presses the rule's other tab in [pkg]'s window; false when there is none to press. */
    fun pressOtherTab(service: AccessibilityService, pkg: String, tab: OtherTab): Boolean =
        try {
            pressOnce(service, pkg, tab)
        } finally {
            service.setCacheEnabled(false)
        }

    private fun pressOnce(service: AccessibilityService, pkg: String, tab: OtherTab): Boolean {
        val root = service.getRootInActiveWindow(0) ?: return false
        if (root.packageName?.toString() != pkg) return false
        val parent = root.findAccessibilityNodeInfosByViewId("$pkg:id/${tab.parentId}").firstOrNull() ?: return false
        val target = firstClickable(parent, TAB_DEPTH) ?: return false
        return target.performAction(AccessibilityNodeInfo.ACTION_CLICK)
    }

    /** The first visible, clickable node under [parent], breadth first, at most [depth] levels down. */
    private fun firstClickable(parent: AccessibilityNodeInfo, depth: Int): AccessibilityNodeInfo? {
        var level = listOf(parent)
        repeat(depth) {
            val next = ArrayList<AccessibilityNodeInfo>()
            for (node in level) {
                for (i in 0 until node.childCount) {
                    val child = node.getChild(i, 0) ?: continue
                    if (child.isClickable && child.isVisibleToUser) return child
                    next += child
                }
            }
            level = next
        }
        return null
    }

    /** How far under the otherTab parent the tab may sit. */
    const val TAB_DEPTH = 2

    private fun fact(node: AccessibilityNodeInfo, id: String, screen: Rect): NodeFact {
        val r = Rect()
        node.getBoundsInScreen(r)
        val w = screen.width().toDouble()
        val h = screen.height().toDouble()
        return NodeFact(
            id = id,
            className = node.className?.toString().orEmpty(),
            left = ((r.left - screen.left) / w).coerceIn(0.0, 1.0),
            top = ((r.top - screen.top) / h).coerceIn(0.0, 1.0),
            right = ((r.right - screen.left) / w).coerceIn(0.0, 1.0),
            bottom = ((r.bottom - screen.top) / h).coerceIn(0.0, 1.0),
            visible = node.isVisibleToUser,
            selected = node.isSelected,
        )
    }
}
