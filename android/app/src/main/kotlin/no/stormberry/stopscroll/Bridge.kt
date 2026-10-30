package no.stormberry.stopscroll

import org.json.JSONArray
import org.json.JSONObject

/*
 * The page-to-app messages (spec 5, "Bridge"): CheckLink's WebMessage channel, which the app opens
 * ONLY to its own asset origin; no addJavascriptInterface. JSON text, v: 1. Every message is checked
 * here before MainActivity acts on it; anything unexpected is refused. Pure, so BridgeTest runs it.
 *
 *   page to app   { v, type, id, ... }
 *     getState                          the state below
 *     setMode { app, mode }             app: a watched package; mode: off | always
 *     recordConsent { version }         the disclosure version the user agreed to
 *     openAccessibilitySettings         refused unless a valid consent is stored
 *     openAppInfo                       Android's App info page for StopScroll (restricted settings)
 *     turnOff                           the service switches itself off (disableSelf)
 *     openLink { link }                 an ID from LinkList, never an address
 *   app to page
 *     { v, type: 'state', id?, state }  also sent unasked when the app comes back to the front
 *     { v, type: 'result', id, ok, error? }
 *
 * Settings live in Kotlin (the service reads them); the page keeps nothing in localStorage.
 */
sealed interface Command {
    val id: Int

    data class GetState(override val id: Int) : Command
    data class SetMode(override val id: Int, val app: String, val mode: Mode) : Command
    data class RecordConsent(override val id: Int, val version: Int) : Command
    data class OpenAccessibilitySettings(override val id: Int) : Command
    data class OpenAppInfo(override val id: Int) : Command
    data class TurnOff(override val id: Int) : Command
    data class OpenLink(override val id: Int, val link: String) : Command
}

/** Everything the page shows, gathered by MainActivity. */
data class AppState(
    val consent: ConsentRecord?,
    val consentValid: Boolean,
    val serviceEnabled: Boolean,
    val serviceRunning: Boolean,
    val sdk: Int,
    val installedFromFile: Boolean,
    val returnedFromSettings: Boolean,
    val appVersion: String,
    val rulesVersion: String,
    val disclosureVersion: Int,
    val apps: List<AppRow>,
)

data class AppRow(
    val id: String,
    val packageName: String,
    val name: String,
    val feedName: String,
    val mode: Mode,
    val installed: Boolean,
    val versionName: String?,
    val stats: AppStats,
)

object Bridge {
    const val VERSION = 1
    private val TYPES = setOf("getState", "setMode", "recordConsent", "openAccessibilitySettings", "openAppInfo", "turnOff", "openLink")

    fun parse(text: String, watched: List<String>): Command? {
        val o = try {
            JSONObject(text)
        } catch (_: Exception) {
            return null
        }
        if (o.optInt("v", -1) != VERSION) return null
        val type = o.optString("type")
        if (type !in TYPES) return null
        val id = o.optInt("id", -1)
        if (id < 0) return null
        return when (type) {
            "getState" -> Command.GetState(id)
            "setMode" -> {
                val app = o.optString("app")
                val mode = Mode.fromWire(o.optString("mode"))
                if (app !in watched || mode == null) null else Command.SetMode(id, app, mode)
            }
            "recordConsent" -> o.optInt("version", -1).takeIf { it == Consent.DISCLOSURE_VERSION }?.let { Command.RecordConsent(id, it) }
            "openAccessibilitySettings" -> Command.OpenAccessibilitySettings(id)
            "openAppInfo" -> Command.OpenAppInfo(id)
            "turnOff" -> Command.TurnOff(id)
            "openLink" -> o.optString("link").takeIf { it in LinkList.IDS }?.let { Command.OpenLink(id, it) }
            else -> null
        }
    }

    fun state(s: AppState, id: Int?): String {
        val state = JSONObject()
            .put("consentGiven", s.consentValid)
            .put("consentDate", if (s.consentValid) s.consent?.date else JSONObject.NULL)
            .put("serviceEnabled", s.serviceEnabled)
            .put("serviceRunning", s.serviceRunning)
            .put("sdk", s.sdk)
            .put("installedFromFile", s.installedFromFile)
            .put("returnedFromSettings", s.returnedFromSettings)
            .put("appVersion", s.appVersion)
            .put("rulesVersion", s.rulesVersion)
            .put("disclosureVersion", s.disclosureVersion)
            .put(
                "apps",
                JSONArray().apply {
                    s.apps.forEach { a ->
                        put(
                            JSONObject()
                                .put("id", a.id)
                                .put("package", a.packageName)
                                .put("name", a.name)
                                .put("feedName", a.feedName)
                                .put("mode", a.mode.wire)
                                .put("installed", a.installed)
                                .put("versionName", a.versionName ?: JSONObject.NULL)
                                .put("closed", a.stats.closed)
                                .put("loopPauses", a.stats.loopPauses)
                                .put("lastClosed", a.stats.lastClosed ?: JSONObject.NULL),
                        )
                    }
                },
            )
        val msg = JSONObject().put("v", VERSION).put("type", "state").put("state", state)
        if (id != null) msg.put("id", id)
        return msg.toString()
    }

    fun result(id: Int, ok: Boolean, error: String? = null): String {
        val msg = JSONObject().put("v", VERSION).put("type", "result").put("id", id).put("ok", ok)
        if (error != null) msg.put("error", error)
        return msg.toString()
    }
}
