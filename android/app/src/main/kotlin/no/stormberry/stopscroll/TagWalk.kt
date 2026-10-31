package no.stormberry.stopscroll

/*
 * How StopScroll finds a screen tag (0.0.3, decision WALK-1 b of 31 October 2026).
 *
 * X builds its screens with Jetpack Compose and names each screen with a test tag ("MainLanding",
 * "PostDetail", "VideoTab"), which it exposes as the node's view ID without a package. Android's
 * findAccessibilityNodeInfosByViewId cannot find such a name: it resolves the name to a resource
 * number and tests real Views only. So the tag is found by walking down the screen's tree.
 *
 * The walk is breadth first, level by level, and STOPS AT THE END OF THE FIRST LEVEL THAT HOLDS A
 * BARE TAG (a non-empty name without ':'), or after [MAX_WALK_LEVELS] levels, or after
 * [MAX_WALK_NODES] nodes, whichever comes first. Measured on 22 sanitised X captures and 35 raw
 * dumps (X 12.38.1, 31 October 2026): every tagged X screen has exactly one bare tag on that level,
 * the screen's own name, at depth 10 as the 12th node (depth 6 and the 8th node in the
 * important-only tree). So a check costs 12 node fetches, not up to 64. The price: if X ever nests
 * the video viewer inside another tagged screen, the walk stops above it and StopScroll misses that
 * feed (it fails open; a wrong block is worse than a missed feed).
 *
 * Pure and generic: no Android type. NodeFacts.kt runs it over AccessibilityNodeInfo, fetching each
 * child with getChild(i, 0) (no prefetching) and reading only the view ID name; the JVM tests run
 * the very same code over the fixture trees. It reads a name and nothing else: what a found tag
 * looks like (class, bounds, visible, selected) is read by NodeFacts, and only for the tags the
 * rules name. tests/guards.test.ts pins both limits.
 */
object TagWalk {
    /** At most this many levels, the root's included (depths 0 to 13). */
    const val MAX_WALK_LEVELS = 14

    /** At most this many nodes, the root included: every child fetch counts, even one that returns nothing. */
    const val MAX_WALK_NODES = 64

    /** What a walk found: the nodes with a bare tag on the first level holding one, and what it cost. */
    data class Found<N>(val tagged: List<Pair<N, String>>, val fetched: Int, val levels: Int)

    /** True for a bare tag: a non-empty name with no ':' (a real view ID is "<package>:id/<name>"). */
    fun isBareTag(name: String?): Boolean = !name.isNullOrEmpty() && ':' !in name

    /**
     * Walks down from [root]. [childCount] and [child] fetch the tree (each [child] call is one
     * fetch), [name] reads a node's view ID name (no fetch).
     */
    fun <N : Any> walk(root: N, childCount: (N) -> Int, child: (N, Int) -> N?, name: (N) -> String?): Found<N> {
        var level = listOf(root)
        var fetched = 1
        var levels = 1
        while (true) {
            val tagged = level.mapNotNull { n -> name(n)?.takeIf(::isBareTag)?.let { n to it } }
            if (tagged.isNotEmpty() || levels >= MAX_WALK_LEVELS || fetched >= MAX_WALK_NODES) {
                return Found(tagged, fetched, levels)
            }
            val next = ArrayList<N>()
            fill@ for (n in level) {
                for (i in 0 until childCount(n)) {
                    if (fetched >= MAX_WALK_NODES) break@fill
                    fetched++
                    child(n, i)?.let { next += it }
                }
            }
            if (next.isEmpty()) return Found(emptyList(), fetched, levels)
            level = next
            levels++
        }
    }
}
