package no.stormberry.stopscroll

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Every page message is checked before the app acts; links are IDs, never addresses. */
class BridgeTest {
    private val yt = "com.google.android.youtube"
    private val x = "com.twitter.android"
    private fun parse(s: String) = Bridge.parse(s, listOf(yt), listOf(yt))

    @Test fun wellFormedMessages() {
        assertEquals(Command.GetState(3), parse("""{"v":1,"type":"getState","id":3}"""))
        assertEquals(Command.SetMode(4, yt, Mode.OFF), parse("""{"v":1,"type":"setMode","id":4,"app":"$yt","mode":"off"}"""))
        assertEquals(Command.OpenLink(5, "github"), parse("""{"v":1,"type":"openLink","id":5,"link":"github"}"""))
        assertEquals(Command.OpenLink(6, "email"), parse("""{"v":1,"type":"openLink","id":6,"link":"email"}"""))
        assertEquals(Command.OpenAccessibilitySettings(7), parse("""{"v":1,"type":"openAccessibilitySettings","id":7}"""))
        assertEquals(Command.TurnOff(8), parse("""{"v":1,"type":"turnOff","id":8}"""))
    }

    @Test fun refused() {
        assertNull(parse("not json"))
        assertNull(parse("""{"v":2,"type":"getState","id":1}"""))
        assertNull(parse("""{"v":1,"type":"getState"}"""))
        assertNull(parse("""{"v":1,"type":"eval","id":1}"""))
        assertNull(parse("""{"v":1,"type":"setMode","id":1,"app":"com.example.bank","mode":"off"}"""))
        assertNull(parse("""{"v":1,"type":"setMode","id":1,"app":"com.instagram.android","mode":"always"}"""))
        assertNull(parse("""{"v":1,"type":"setMode","id":1,"app":"$yt","mode":"strict"}"""))
        assertNull(parse("""{"v":1,"type":"openLink","id":1,"link":"https://example.com/?data=x"}"""))
        assertNull(parse("""{"v":1,"type":"openLink","id":1,"link":"mailto:someone@example.com"}"""))
    }

    /** Per-app consent (0.0.3): an app the user has not agreed to has no mode to set. */
    @Test fun setModeIsRefusedForAnAppNotAgreedTo() {
        val watched = listOf(yt, "com.instagram.android", x)
        val agreed = listOf(yt, "com.instagram.android")
        assertNull(Bridge.parse("""{"v":1,"type":"setMode","id":1,"app":"$x","mode":"off"}""", watched, agreed))
        assertNull(Bridge.parse("""{"v":1,"type":"setMode","id":1,"app":"$x","mode":"always"}""", watched, agreed))
        assertEquals(Command.SetMode(2, yt, Mode.OFF), Bridge.parse("""{"v":1,"type":"setMode","id":2,"app":"$yt","mode":"off"}""", watched, agreed))
        assertEquals(Command.SetMode(3, x, Mode.ALWAYS), Bridge.parse("""{"v":1,"type":"setMode","id":3,"app":"$x","mode":"always"}""", watched, watched))
        // Before any agreement nothing can be set, and an agreed package outside the rules is still refused.
        assertNull(Bridge.parse("""{"v":1,"type":"setMode","id":1,"app":"$yt","mode":"off"}""", watched, emptyList()))
        assertNull(Bridge.parse("""{"v":1,"type":"setMode","id":1,"app":"com.example.bank","mode":"off"}""", watched, listOf("com.example.bank")))
        // The other commands do not depend on the agreed list (openAccessibilitySettings is checked in MainActivity).
        assertEquals(Command.OpenAccessibilitySettings(4), Bridge.parse("""{"v":1,"type":"openAccessibilitySettings","id":4}""", watched, emptyList()))
    }

    @Test fun theLinkListIsFixedAndTheEmailCarriesVersionsOnly() {
        assertEquals(setOf("stormberry", "brreg", "linkedin", "github", "nostr", "onion", "contact", "email"), LinkList.IDS)
        assertEquals("contact", LinkList.idFor("mailto:info@stormberry.as"))
        assertNull(LinkList.idFor("mailto:info@stormberry.as?body=x"))
        assertEquals("stormberry", LinkList.idFor("https://stormberry.as/"))
        assertNull(LinkList.idFor("https://stormberry.as/anything"))
    }

    /** The report email (Marcos, 2026-10-29): exactly these lines, versions and the package name only. */
    @Test fun theReportEmailIsExactlyTheApprovedOne() {
        val mail = LinkList.email("0.0.3", 1003, "no.stormberry.stopscroll.play", "17", listOf("YouTube" to "21.42.36", "Instagram" to "453.0.0.57.79", "X" to "12.38.1-prod.01"))
        val (to, query) = mail.removePrefix("mailto:").split("?", limit = 2)
        assertEquals("info@stormberry.as", to)
        val params = query.split("&").associate { val (k, v) = it.split("=", limit = 2); k to java.net.URLDecoder.decode(v, "UTF-8") }
        assertEquals(setOf("subject", "body"), params.keys)
        assertEquals("StopScroll 0.0.3: missed a feed or blocked a normal video", params["subject"])
        assertEquals(
            "Which app:\nWhat happened:\n\nStopScroll 0.0.3 (1003)\nno.stormberry.stopscroll.play\nAndroid 17\nYouTube 21.42.36\nInstagram 453.0.0.57.79\nX 12.38.1-prod.01",
            params["body"],
        )
        // Spaces are %20, never "+", so every mail app shows them as spaces.
        assertEquals(false, mail.contains("+"))
        val none = LinkList.email("0.0.3", 3, "no.stormberry.stopscroll", "16", listOf("YouTube" to null, "Instagram" to null, "X" to null))
        assertEquals(true, java.net.URLDecoder.decode(none, "UTF-8").endsWith("\nno.stormberry.stopscroll\nAndroid 16\nYouTube not installed\nInstagram not installed\nX not installed"))
        // The rules' app names, in the rules' order, are the lines MainActivity passes.
        assertEquals(listOf("YouTube", "Instagram", "X"), Repo.rules.apps.map { it.name })
    }

    @Test fun navigationLetsOnlyTheAppsOwnPagesAndTheFixedLinksThrough() {
        assertEquals(NavigationPolicy.Decision.IN_APP, NavigationPolicy.decide("https://appassets.androidplatform.net/privacy.html"))
        assertEquals(NavigationPolicy.Decision.BLOCK, NavigationPolicy.decide("http://appassets.androidplatform.net/index.html"))
        assertEquals(NavigationPolicy.Decision.BLOCK, NavigationPolicy.decide("https://example.com/"))
        assertEquals(NavigationPolicy.Decision.BLOCK, NavigationPolicy.decide("javascript:alert(1)"))
        assertEquals(NavigationPolicy.Decision.LINK, NavigationPolicy.decide("https://github.com/StormberryAS"))
        assertEquals(NavigationPolicy.Decision.BLOCK, NavigationPolicy.decide("https://github.com/StormberryAS?x=1"))
    }

    @Test fun theStateMessageCarriesNoScreenContent() {
        val s = AppState(
            consent = ConsentRecord("2026-10-30", 2, listOf(yt)), anyAgreed = true, serviceEnabled = true, serviceRunning = true,
            sdk = 36, installedFromFile = false, returnedFromSettings = false, appVersion = "0.0.3", rulesVersion = "r", disclosureVersion = 2,
            apps = listOf(
                AppRow("youtube", yt, "YouTube", "YouTube Shorts", Mode.ALWAYS, agreed = true, installed = true, versionName = "21.42", stats = AppStats(2, 0, "2026-10-29")),
                AppRow("x", x, "X", "the X video feed", Mode.ALWAYS, agreed = false, installed = true, versionName = "12.38.1", stats = AppStats()),
            ),
        )
        val o = JSONObject(Bridge.state(s, 9))
        assertEquals("state", o.getString("type"))
        assertEquals(9, o.getInt("id"))
        val st = o.getJSONObject("state")
        assertEquals(
            setOf("consentGiven", "consentDate", "serviceEnabled", "serviceRunning", "sdk", "installedFromFile", "returnedFromSettings", "appVersion", "rulesVersion", "disclosureVersion", "apps"),
            st.keys().asSequence().toSet(),
        )
        assertEquals(
            setOf("id", "package", "name", "feedName", "mode", "agreed", "installed", "versionName", "closed", "loopPauses", "lastClosed"),
            st.getJSONArray("apps").getJSONObject(0).keys().asSequence().toSet(),
        )
        assertEquals(true, st.getBoolean("consentGiven"))
        assertEquals("2026-10-30", st.getString("consentDate"))
        assertEquals(true, st.getJSONArray("apps").getJSONObject(0).getBoolean("agreed"))
        assertEquals(false, st.getJSONArray("apps").getJSONObject(1).getBoolean("agreed"))
        // No app agreed: no consent, no date shown.
        val none = JSONObject(Bridge.state(s.copy(anyAgreed = false), null)).getJSONObject("state")
        assertEquals(false, none.getBoolean("consentGiven"))
        assertEquals(true, none.isNull("consentDate"))
    }
}
