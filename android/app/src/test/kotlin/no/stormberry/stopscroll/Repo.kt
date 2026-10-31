package no.stormberry.stopscroll

import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/** The repository root, passed in by app/build.gradle.kts, and helpers to read it. */
object Repo {
    val root: File = File(System.getProperty("stopscroll.root") ?: error("stopscroll.root is not set; run the tests through Gradle"))

    fun file(rel: String): File = File(root, rel).also { require(it.isFile) { "missing $rel" } }

    val rules: ScreenRules by lazy { ScreenRules.parse(file("rules/screen-rules.json").readText()) }

    fun xml(rel: String): Element =
        DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }.newDocumentBuilder().parse(file(rel)).documentElement
}

/** A sanitised fixture (tests/fixtures/): its labels and the facts the service would read from it. */
class Fixture(val rel: String) {
    private val doc: Element = Repo.xml(rel)
    val app: String = doc.getAttribute("app")
    val label: String = doc.getAttribute("label")
    val expect: String = doc.getAttribute("expect")

    /** Every node element, in document order (the first is the window's root). */
    private val elements: List<Element> by lazy {
        val nodes = mutableListOf<Element>()
        fun walk(e: Element) {
            if (e.tagName == "node") nodes += e
            val kids = e.childNodes
            for (i in 0 until kids.length) (kids.item(i) as? Element)?.let(::walk)
        }
        walk(doc)
        require(nodes.isNotEmpty()) { "$rel has no node" }
        nodes
    }

    private val facts: Map<Element, NodeFact> by lazy {
        val screen = bounds(elements.first())
        val w = (screen[2] - screen[0]).toDouble()
        val h = (screen[3] - screen[1]).toDouble()
        elements.associateWith { n ->
            val b = bounds(n)
            val rid = n.getAttribute("resource-id")
            val tag = TagWalk.isBareTag(rid)
            NodeFact(
                id = if (rid.startsWith("$app:id/")) rid.removePrefix("$app:id/") else if (tag) rid else "",
                className = n.getAttribute("class"),
                left = (b[0] - screen[0]) / w,
                top = (b[1] - screen[1]) / h,
                right = (b[2] - screen[0]) / w,
                bottom = (b[3] - screen[1]) / h,
                visible = true,
                selected = n.getAttribute("selected") == "true",
                tag = tag,
            )
        }
    }

    /**
     * Every node, as NodeFact, the screen being the root node's bounds. IDs of other namespaces
     * become ""; a bare screen tag (X) is kept as the ID with tag = true.
     */
    val allNodes: List<NodeFact> by lazy { elements.map { facts.getValue(it) } }

    private fun children(e: Element): List<Element> =
        (0 until e.childNodes.length).mapNotNull { e.childNodes.item(it) as? Element }.filter { it.tagName == "node" }

    /**
     * The very walk the service runs (TagWalk), over this capture's tree: the name of a node is its
     * resource-id as uiautomator wrote it ("com.twitter.android:id/action_bar_root", "VideoTab", "").
     */
    fun walk(): TagWalk.Found<Element> =
        TagWalk.walk(elements.first(), { children(it).size }, { e, i -> children(e).getOrNull(i) }, { it.getAttribute("resource-id") })

    /** The resource IDs (without the package) of the direct children of the first node with [id], in order. */
    fun childIds(id: String): List<String> {
        fun find(e: Element): Element? {
            if (e.tagName == "node" && e.getAttribute("resource-id") == "$app:id/$id") return e
            val kids = e.childNodes
            for (i in 0 until kids.length) (kids.item(i) as? Element)?.let(::find)?.let { return it }
            return null
        }
        val parent = find(doc) ?: return emptyList()
        return (0 until parent.childNodes.length).mapNotNull { parent.childNodes.item(it) as? Element }
            .filter { it.tagName == "node" }
            .map { it.getAttribute("resource-id").removePrefix("$app:id/") }
    }

    /** How many nodes sit on each of the first [depth] levels under the first node with [id] (empty when there is none). */
    fun levelSizes(id: String, depth: Int): List<Int> {
        fun find(e: Element): Element? {
            if (e.tagName == "node" && e.getAttribute("resource-id") == "$app:id/$id") return e
            val kids = e.childNodes
            for (i in 0 until kids.length) (kids.item(i) as? Element)?.let(::find)?.let { return it }
            return null
        }
        var level = listOf(find(doc) ?: return emptyList())
        return (1..depth).map {
            level = level.flatMap { children(it) }
            level.size
        }
    }

    /** The node with [id] (this app's namespace), the first in document order, or null. */
    private fun byId(id: String): Element? {
        fun find(e: Element): Element? {
            if (e.tagName == "node" && e.getAttribute("resource-id") == "$app:id/$id") return e
            val kids = e.childNodes
            for (i in 0 until kids.length) (kids.item(i) as? Element)?.let(::find)?.let { return it }
            return null
        }
        return find(doc)
    }

    /** The tab row under [id]: the first of its first [depth] levels with at least three nodes (empty when none). */
    private fun tabRow(id: String, depth: Int): List<Element> {
        var level = listOf(byId(id) ?: return emptyList())
        repeat(depth) {
            level = level.flatMap { children(it) }
            if (level.size >= 3) return level
        }
        return emptyList()
    }

    /** The indexes of the selected tabs in the tab row under [id]. */
    fun selectedTabs(id: String, depth: Int): List<Int> =
        tabRow(id, depth).withIndex().filter { it.value.getAttribute("selected") == "true" }.map { it.index }

    /**
     * Runs the service's TabPick over the tree under [id]: the index in the tab row of the node it would
     * press, -1 for a node outside the row, or null for no press. A capture keeps no `clickable`, so it is
     * modelled as the raw dumps show it (31 October 2026): a tab of the row is clickable unless it is the
     * selected one ([selectedOverride], when given, plays the selected tab instead); nothing else is.
     */
    fun pickTab(id: String, way: String, depth: Int, selectedOverride: Int? = null): Int? {
        val parent = byId(id) ?: return null
        val row = tabRow(id, depth)
        fun clickable(e: Element): Boolean {
            val i = row.indexOf(e)
            if (i < 0) return false
            return if (selectedOverride != null) i != selectedOverride else e.getAttribute("selected") != "true"
        }
        val picked = TabPick.pick(parent, way, depth, { children(it).size }, { e, i -> children(e).getOrNull(i) }, ::clickable, { true }) ?: return null
        return row.indexOf(picked)
    }

    /** How many levels under the node with [id] the first node of [className] sits, or null. */
    fun depthOfFirst(id: String, className: String): Int? {
        fun find(e: Element): Element? {
            if (e.tagName == "node" && e.getAttribute("resource-id") == "$app:id/$id") return e
            val kids = e.childNodes
            for (i in 0 until kids.length) (kids.item(i) as? Element)?.let(::find)?.let { return it }
            return null
        }
        var level = listOf(find(doc) ?: return null)
        var depth = 0
        while (level.isNotEmpty()) {
            depth++
            val next = level.flatMap { p -> (0 until p.childNodes.length).mapNotNull { p.childNodes.item(it) as? Element }.filter { it.tagName == "node" } }
            if (next.any { it.getAttribute("class") == className }) return depth
            level = next
        }
        return null
    }

    /**
     * What NodeFacts.look would return: the view-ID lookups (only the IDs the app's rules name), then,
     * when the rules name a tag, the walk's tags that the rules name, as tag facts.
     */
    fun snapshot(rules: ScreenRules = Repo.rules): Snapshot {
        val appRules = rules.app(app)
        val ids = appRules?.referencedIds.orEmpty()
        val tags = appRules?.referencedTags.orEmpty()
        val byId = allNodes.filter { !it.tag && it.id in ids }
        val byTag = if (tags.isEmpty()) emptyList() else walk().tagged.filter { (_, t) -> t in tags }.map { (e, _) -> facts.getValue(e) }
        return Snapshot(app, byId + byTag)
    }

    private fun bounds(e: Element): IntArray {
        val m = Regex("""^\[(\d+),(\d+)\]\[(\d+),(\d+)\]$""").matchEntire(e.getAttribute("bounds")) ?: error("$rel: bad bounds")
        return IntArray(4) { m.groupValues[it + 1].toInt() }
    }

    companion object {
        fun all(dir: String): List<Fixture> =
            File(Repo.root, dir).listFiles { f -> f.extension == "xml" }.orEmpty().sortedBy { it.name }.map { Fixture("$dir/${it.name}") }
    }
}
