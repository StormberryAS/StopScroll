package no.stormberry.stopscroll

import android.content.Context
import android.util.AtomicFile
import org.json.JSONObject
import java.io.File

/*
 * What StopScroll keeps (spec 6.3 item 5, stage 1): the consent date, each app's mode, and per app
 * two counters and the date a feed was last closed. Nothing else: no history, no screen content.
 * Kept in noBackupFilesDir, which no backup or device transfer copies (allowBackup is false and the
 * rule files exclude everything as well).
 *
 * The model is pure (SettingsTest); the store reads and writes one JSON file atomically and tells
 * its listeners (the running service) when something changed. Main thread only.
 */

data class AppStats(val closed: Int = 0, val loopPauses: Int = 0, val lastClosed: String? = null)

data class SettingsModel(
    val consent: ConsentRecord? = null,
    val modes: Map<String, Mode> = emptyMap(),
    val stats: Map<String, AppStats> = emptyMap(),
) {
    /** The mode of [pkg]: Always block until the user chooses (spec 6.6), inert until consent. */
    fun mode(pkg: String): Mode = modes[pkg] ?: Mode.ALWAYS

    fun modesFor(watched: List<String>): Map<String, Mode> = watched.associateWith { mode(it) }

    fun stats(pkg: String): AppStats = stats[pkg] ?: AppStats()

    fun withMode(pkg: String, mode: Mode) = copy(modes = modes + (pkg to mode))

    fun withClosed(pkg: String, day: String) = stats(pkg).let { copy(stats = stats + (pkg to it.copy(closed = it.closed + 1, lastClosed = day))) }

    fun withLoopPause(pkg: String) = stats(pkg).let { copy(stats = stats + (pkg to it.copy(loopPauses = it.loopPauses + 1))) }

    fun toJson(): String {
        val o = JSONObject().put("v", 1)
        consent?.let { o.put("consent", JSONObject().put("date", it.date).put("version", it.version).put("apps", org.json.JSONArray(it.apps))) }
        o.put("modes", JSONObject().apply { modes.toSortedMap().forEach { (k, v) -> put(k, v.wire) } })
        o.put(
            "stats",
            JSONObject().apply {
                stats.toSortedMap().forEach { (k, s) ->
                    put(k, JSONObject().put("closed", s.closed).put("loopPauses", s.loopPauses).apply { s.lastClosed?.let { put("lastClosed", it) } })
                }
            },
        )
        return o.toString()
    }

    companion object {
        /** Reads a stored file; anything unreadable gives the defaults (no consent), never a crash. */
        fun fromJson(text: String?): SettingsModel {
            if (text.isNullOrBlank()) return SettingsModel()
            return try {
                val o = JSONObject(text)
                val c = o.optJSONObject("consent")?.let { c ->
                    val a = c.getJSONArray("apps")
                    ConsentRecord(c.getString("date"), c.getInt("version"), (0 until a.length()).map { a.getString(it) })
                }
                val m = o.optJSONObject("modes")
                val modes = m?.keys()?.asSequence()?.mapNotNull { k -> Mode.fromWire(m.optString(k))?.let { k to it } }?.toMap().orEmpty()
                val s = o.optJSONObject("stats")
                val stats = s?.keys()?.asSequence()?.map { k ->
                    val e = s.getJSONObject(k)
                    k to AppStats(e.optInt("closed"), e.optInt("loopPauses"), if (e.has("lastClosed")) e.getString("lastClosed") else null)
                }?.toMap().orEmpty()
                SettingsModel(c, modes, stats)
            } catch (_: Exception) {
                SettingsModel()
            }
        }
    }
}

object SettingsStore {
    private const val FILE = "settings.json"
    private var cached: SettingsModel? = null
    private val listeners = mutableSetOf<() -> Unit>()

    private fun file(context: Context) = AtomicFile(File(context.applicationContext.noBackupFilesDir, FILE))

    fun get(context: Context): SettingsModel =
        cached ?: SettingsModel.fromJson(
            try {
                String(file(context).readFully(), Charsets.UTF_8)
            } catch (_: Exception) {
                null
            },
        ).also { cached = it }

    fun update(context: Context, change: (SettingsModel) -> SettingsModel) {
        val next = change(get(context))
        val f = file(context)
        val out = f.startWrite()
        try {
            out.write(next.toJson().toByteArray(Charsets.UTF_8))
            f.finishWrite(out)
        } catch (e: Exception) {
            f.failWrite(out)
            throw e
        }
        cached = next
        listeners.toList().forEach { it() }
    }

    fun addListener(l: () -> Unit) {
        listeners += l
    }

    fun removeListener(l: () -> Unit) {
        listeners -= l
    }
}
