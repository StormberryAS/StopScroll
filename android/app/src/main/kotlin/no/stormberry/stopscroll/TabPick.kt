package no.stormberry.stopscroll

/*
 * Which node under a rule's otherTab parent StopScroll presses, and when it presses none (spec 6.5).
 * The rule's `otherTab.child` names the way:
 *
 *   "firstClickable" (YouTube and Instagram, since 0.0.1): the first visible, clickable node, breadth
 *       first. Their press is step 3 of the Back-first ladder.
 *
 *   "first" (YouTube Music, since 0.0.4): the FIRST node of the first level that holds a clickable
 *       node, pressed only when that node is itself clickable and visible; otherwise none. YouTube
 *       Music makes its selected tab not clickable: in all 45 raw dumps of 31 October 2026 that show
 *       a selected tab, that tab is not clickable and every other tab is. So when Samples is opened
 *       from an album or artist page reached from the Home tab, Home is the selected tab, and
 *       "firstClickable" would find the Samples tab instead: it would open the Samples tab's feed,
 *       and the Back after it would close YouTube Music. "first" finds no tab to press there, so the
 *       ladder's step 1 becomes Back, which returns the user to the album or artist page (review of
 *       31 October 2026, defect 1).
 *
 * Pure and generic: no Android type. NodeFacts.kt runs it over AccessibilityNodeInfo, fetching each
 * child with getChild(i, 0) (no prefetching) and reading only clickable and visible; the JVM tests run
 * the very same code over the fixture trees. It never reads a name, a text or a description.
 * tests/guards.test.ts pins that.
 */
object TabPick {
    const val FIRST_CLICKABLE = "firstClickable"
    const val FIRST = "first"

    /** The ways a rule may name its tab; the rules parser refuses any other. */
    val WAYS: Set<String> = setOf(FIRST_CLICKABLE, FIRST)

    /**
     * The node to press under [parent], looking at most [depth] levels down, or null when there is
     * none. [childCount] and [child] fetch the tree (each [child] call is one fetch); [clickable] and
     * [visible] read one flag of a node (no fetch).
     */
    fun <N : Any> pick(
        parent: N,
        way: String,
        depth: Int,
        childCount: (N) -> Int,
        child: (N, Int) -> N?,
        clickable: (N) -> Boolean,
        visible: (N) -> Boolean,
    ): N? {
        require(way in WAYS) { "otherTab child '$way' is not supported" }
        var level = listOf(parent)
        repeat(depth) {
            val next = ArrayList<N>()
            for (node in level) {
                for (i in 0 until childCount(node)) {
                    val c = child(node, i) ?: continue
                    if (way == FIRST_CLICKABLE && clickable(c) && visible(c)) return c
                    next += c
                }
            }
            if (way == FIRST && next.any(clickable)) return next.first().takeIf { clickable(it) && visible(it) }
            level = next
        }
        return null
    }
}
