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

    /** Every node, as NodeFact, the screen being the root node's bounds. IDs of other namespaces become "". */
    val allNodes: List<NodeFact> by lazy {
        val nodes = mutableListOf<Element>()
        fun walk(e: Element) {
            if (e.tagName == "node") nodes += e
            val kids = e.childNodes
            for (i in 0 until kids.length) (kids.item(i) as? Element)?.let(::walk)
        }
        walk(doc)
        require(nodes.isNotEmpty()) { "$rel has no node" }
        val screen = bounds(nodes.first())
        val w = (screen[2] - screen[0]).toDouble()
        val h = (screen[3] - screen[1]).toDouble()
        nodes.map { n ->
            val b = bounds(n)
            val rid = n.getAttribute("resource-id")
            NodeFact(
                id = if (rid.startsWith("$app:id/")) rid.removePrefix("$app:id/") else "",
                className = n.getAttribute("class"),
                left = (b[0] - screen[0]) / w,
                top = (b[1] - screen[1]) / h,
                right = (b[2] - screen[0]) / w,
                bottom = (b[3] - screen[1]) / h,
                visible = true,
                selected = n.getAttribute("selected") == "true",
            )
        }
    }

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

    /** What NodeFacts.look would return: only the nodes whose IDs the app's rules name. */
    fun snapshot(rules: ScreenRules = Repo.rules): Snapshot {
        val ids = rules.app(app)?.referencedIds.orEmpty()
        return Snapshot(app, allNodes.filter { it.id in ids })
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
