package no.stormberry.stopscroll

import org.json.JSONArray
import org.json.JSONObject

/*
 * The screen rules (spec 6.4): data, read from rules/screen-rules.json, which the build copies into
 * the APK's assets. Pure Kotlin: no Android type, so the JVM unit tests run the very code the
 * service runs, over sanitised fixtures of real screens.
 *
 * A rule never sees text. It sees NodeFact, which has no text field: a view ID or a screen tag, a
 * class name, the bounds as fractions of the screen, visible and selected. IDs match exactly, never
 * by prefix (the normal YouTube player carries reel_time_bar too, spec 6.4).
 *
 * Schema 2 (0.0.3, X): a second selector kind, `tag`, for the names an app built with Jetpack Compose
 * gives its screens (test tags, which X exposes as view IDs without a package: "VideoTab"). A tag is
 * a developer's name for a part of the screen, never on-screen text. Android cannot look a tag up by
 * name (findAccessibilityNodeInfosByViewId resolves real View IDs only), so NodeFacts finds tags with
 * a short walk (TagWalk.kt), and a tag fact carries `tag = true`: a `node` selector matches only
 * facts from a view-ID lookup and a `tag` selector only facts from the walk, so a view and a tag of
 * the same name can never stand in for each other. A schema 1 reader refuses this file.
 *
 * Fail open: a screen no rule matches is allowed. A wrong block is worse than a missed feed.
 */

/** What the service may know about one node. Deliberately no text, no description, no hint. */
data class NodeFact(
    /** The view ID without its package prefix ("reel_watch_player"), or the bare tag ("VideoTab"); "" when it has none. */
    val id: String,
    val className: String,
    /** Bounds as fractions of the screen, 0.0 to 1.0. */
    val left: Double,
    val top: Double,
    val right: Double,
    val bottom: Double,
    val visible: Boolean,
    val selected: Boolean,
    /** True when [id] is a screen tag found by the walk (schema 2), false for a view-ID lookup. */
    val tag: Boolean = false,
) {
    val width: Double get() = (right - left).coerceAtLeast(0.0)
    val height: Double get() = (bottom - top).coerceAtLeast(0.0)
}

/** The facts read from one window: its package and the nodes the rules asked for. */
data class Snapshot(val packageName: String, val nodes: List<NodeFact>)

/** A rule's verdict: this screen is the feed [surfaceId] of [app]. */
data class Verdict(val app: AppRules, val surfaceId: String)

sealed interface Selector {
    data class Any(val of: List<Selector>) : Selector
    data class All(val of: List<Selector>) : Selector
    data class Not(val of: Selector) : Selector
    data class Node(
        val id: String,
        val className: String? = null,
        val minHeight: Double? = null,
        val minWidth: Double? = null,
        val selected: Boolean? = null,
    ) : Selector

    /** A screen tag (schema 2): matches only facts the tag walk found. The same keys as [Node]. */
    data class Tag(
        val id: String,
        val className: String? = null,
        val minHeight: Double? = null,
        val minWidth: Double? = null,
        val selected: Boolean? = null,
    ) : Selector
}

/** Where to find the tab pressed when Back does not leave the feed (spec 6.5, step 3). */
data class OtherTab(val parentId: String, val child: String)

data class Surface(
    val id: String,
    val match: Selector,
    val exclude: List<Selector>,
    val otherTab: OtherTab?,
)

data class VerifiedOn(
    val device: String,
    val android: String,
    val appVersionName: String,
    val appVersionCode: Long,
    val date: String,
)

data class AppRules(
    val id: String,
    val packageName: String,
    val name: String,
    val feedName: String,
    val surfaces: List<Surface>,
    val verifiedOn: List<VerifiedOn>,
) {
    /** Every view ID a rule of this app names: the only IDs the service ever looks up. */
    val referencedIds: Set<String> by lazy { collect { (it as? Selector.Node)?.id } }

    /** Every screen tag a rule of this app names: the only tags the walk turns into facts. Empty: no walk. */
    val referencedTags: Set<String> by lazy { collect { (it as? Selector.Tag)?.id } }

    private fun collect(pick: (Selector) -> String?): Set<String> {
        val out = sortedSetOf<String>()
        fun walk(s: Selector) {
            when (s) {
                is Selector.Any -> s.of.forEach(::walk)
                is Selector.All -> s.of.forEach(::walk)
                is Selector.Not -> walk(s.of)
                is Selector.Node, is Selector.Tag -> pick(s)?.let { out += it }
            }
        }
        surfaces.forEach { surface ->
            walk(surface.match)
            surface.exclude.forEach(::walk)
        }
        return out
    }
}

data class ScreenRules(
    val schema: Int,
    val rulesVersion: String,
    val date: String,
    val apps: List<AppRules>,
) {
    /** The packages this build's rules cover, in file order. Never empty (the parser insists). */
    val packages: List<String> get() = apps.map { it.packageName }

    fun app(packageName: String): AppRules? = apps.firstOrNull { it.packageName == packageName }

    /**
     * The verdict for [snapshot], or null to allow it. A snapshot from a package without rules gets
     * null at once: the window check (spec 6.2) has already stopped reading such a window, and this
     * is the second lock.
     */
    fun evaluate(snapshot: Snapshot): Verdict? {
        val app = app(snapshot.packageName) ?: return null
        for (surface in app.surfaces) {
            if (surface.exclude.any { matches(it, snapshot.nodes) }) continue
            if (matches(surface.match, snapshot.nodes)) return Verdict(app, surface.id)
        }
        return null
    }

    companion object {
        const val SCHEMA = 2
        private val ID = Regex("^[A-Za-z0-9_]{1,80}$")
        /** A Compose test tag: a letter first, then letters, digits, underscores or hyphens. */
        private val TAG = Regex("^[A-Za-z][A-Za-z0-9_-]{0,79}$")
        private val PACKAGE = Regex("^[a-z][a-z0-9_]*(\\.[a-z0-9_]+)+$")
        private val DATE = Regex("^\\d{4}-\\d{2}-\\d{2}$")

        fun matches(selector: Selector, nodes: List<NodeFact>): Boolean = when (selector) {
            is Selector.Any -> selector.of.any { matches(it, nodes) }
            is Selector.All -> selector.of.all { matches(it, nodes) }
            is Selector.Not -> !matches(selector.of, nodes)
            is Selector.Node -> nodes.any { n ->
                n.visible && !n.tag && n.id == selector.id &&
                    (selector.className == null || n.className == selector.className) &&
                    (selector.minHeight == null || n.height >= selector.minHeight) &&
                    (selector.minWidth == null || n.width >= selector.minWidth) &&
                    (selector.selected == null || n.selected == selector.selected)
            }
            is Selector.Tag -> nodes.any { n ->
                n.visible && n.tag && n.id == selector.id &&
                    (selector.className == null || n.className == selector.className) &&
                    (selector.minHeight == null || n.height >= selector.minHeight) &&
                    (selector.minWidth == null || n.width >= selector.minWidth) &&
                    (selector.selected == null || n.selected == selector.selected)
            }
        }

        /** Parses the rules file strictly: an unknown key or a missing one is an error, not a guess. */
        fun parse(text: String): ScreenRules {
            val o = JSONObject(text)
            keys(o, "rules file", setOf("schema", "rulesVersion", "date", "apps"))
            val schema = o.getInt("schema")
            require(schema == SCHEMA) { "rules schema $schema, this build reads $SCHEMA" }
            val date = o.getString("date")
            require(DATE.matches(date)) { "rules date is not YYYY-MM-DD" }
            val appsJson = o.getJSONArray("apps")
            val apps = (0 until appsJson.length()).map { parseApp(appsJson.getJSONObject(it)) }
            require(apps.isNotEmpty()) { "the rules name no app; the service's package list must never be empty" }
            require(apps.map { it.packageName }.toSet().size == apps.size) { "an app appears twice" }
            return ScreenRules(schema, o.getString("rulesVersion"), date, apps)
        }

        private fun keys(o: JSONObject, where: String, allowed: Set<String>, required: Set<String> = allowed) {
            val present = o.keys().asSequence().toSet()
            val unknown = present - allowed
            require(unknown.isEmpty()) { "$where: unknown key(s) ${unknown.sorted()}" }
            val missing = required - present
            require(missing.isEmpty()) { "$where: missing key(s) ${missing.sorted()}" }
        }

        private fun parseApp(o: JSONObject): AppRules {
            keys(o, "app", setOf("id", "package", "name", "feedName", "surfaces", "verifiedOn"))
            val pkg = o.getString("package")
            require(PACKAGE.matches(pkg)) { "not a package name: $pkg" }
            val s = o.getJSONArray("surfaces")
            val surfaces = (0 until s.length()).map { parseSurface(s.getJSONObject(it)) }
            require(surfaces.isNotEmpty()) { "$pkg has no surface" }
            val v = o.getJSONArray("verifiedOn")
            val verified = (0 until v.length()).map { i ->
                val r = v.getJSONObject(i)
                keys(r, "verifiedOn", setOf("device", "android", "appVersionName", "appVersionCode", "date", "how"))
                VerifiedOn(r.getString("device"), r.getString("android"), r.getString("appVersionName"), r.getLong("appVersionCode"), r.getString("date"))
            }
            require(verified.isNotEmpty()) { "$pkg has no verifiedOn record" }
            return AppRules(o.getString("id"), pkg, o.getString("name"), o.getString("feedName"), surfaces, verified)
        }

        private fun parseSurface(o: JSONObject): Surface {
            keys(o, "surface", setOf("id", "match", "exclude", "otherTab"), required = setOf("id", "match", "exclude"))
            val ex = o.getJSONArray("exclude")
            val tab = o.optJSONObject("otherTab")?.let {
                keys(it, "otherTab", setOf("parentId", "child"))
                val child = it.getString("child")
                require(child == "firstClickable") { "otherTab child '$child' is not supported" }
                OtherTab(id(it.getString("parentId")), child)
            }
            return Surface(o.getString("id"), parseSelector(o.getJSONObject("match")), (0 until ex.length()).map { parseSelector(ex.getJSONObject(it)) }, tab)
        }

        private fun parseSelector(o: JSONObject): Selector {
            require(o.length() == 1) { "a selector has exactly one of any, all, not, node, tag" }
            return when (val k = o.keys().next()) {
                "any" -> Selector.Any(list(o.getJSONArray(k)))
                "all" -> Selector.All(list(o.getJSONArray(k)))
                "not" -> Selector.Not(parseSelector(o.getJSONObject(k)))
                "node" -> {
                    val n = o.getJSONObject(k)
                    keys(n, "node", setOf("id", "class", "minHeight", "minWidth", "selected"), required = setOf("id"))
                    Selector.Node(
                        id = id(n.getString("id")),
                        className = if (n.has("class")) n.getString("class") else null,
                        minHeight = if (n.has("minHeight")) fraction(n.getDouble("minHeight")) else null,
                        minWidth = if (n.has("minWidth")) fraction(n.getDouble("minWidth")) else null,
                        selected = if (n.has("selected")) n.getBoolean("selected") else null,
                    )
                }
                "tag" -> {
                    val n = o.getJSONObject(k)
                    keys(n, "tag", setOf("id", "class", "minHeight", "minWidth", "selected"), required = setOf("id"))
                    Selector.Tag(
                        id = tag(n.getString("id")),
                        className = if (n.has("class")) n.getString("class") else null,
                        minHeight = if (n.has("minHeight")) fraction(n.getDouble("minHeight")) else null,
                        minWidth = if (n.has("minWidth")) fraction(n.getDouble("minWidth")) else null,
                        selected = if (n.has("selected")) n.getBoolean("selected") else null,
                    )
                }
                else -> throw IllegalArgumentException("unknown selector '$k'")
            }
        }

        private fun list(a: JSONArray): List<Selector> {
            require(a.length() > 0) { "an empty any or all" }
            return (0 until a.length()).map { parseSelector(a.getJSONObject(it)) }
        }

        private fun id(s: String): String {
            require(ID.matches(s)) { "not a plain view ID: $s" }
            return s
        }

        private fun tag(s: String): String {
            require(TAG.matches(s)) { "not a plain screen tag: $s" }
            return s
        }

        private fun fraction(d: Double): Double {
            require(d in 0.0..1.0) { "a fraction outside 0 to 1: $d" }
            return d
        }
    }
}
