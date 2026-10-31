package no.stormberry.stopscroll

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Every page message is checked before the app acts; links are IDs, never addresses. */
class BridgeTest {
    private val yt = "com.google.android.youtube"
    private fun parse(s: String) = Bridge.parse(s, listOf(yt))

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

    @Test fun theLinkListIsFixedAndTheEmailCarriesVersionsOnly() {
        assertEquals(setOf("stormberry", "brreg", "linkedin", "github", "nostr", "onion", "contact", "email"), LinkList.IDS)
        assertEquals("contact", LinkList.idFor("mailto:info@stormberry.as"))
        assertNull(LinkList.idFor("mailto:info@stormberry.as?body=x"))
        assertEquals("stormberry", LinkList.idFor("https://stormberry.as/"))
        assertNull(LinkList.idFor("https://stormberry.as/anything"))
    }

    /** The report email (Marcos, 2026-10-29): exactly these lines, versions and the package name only. */
    @Test fun theReportEmailIsExactlyTheApprovedOne() {
        val mail = LinkList.email("0.0.2", 1002, "no.stormberry.stopscroll.play", "17", listOf("YouTube" to "21.42.36", "Instagram" to "453.0.0.57.79"))
        val (to, query) = mail.removePrefix("mailto:").split("?", limit = 2)
        assertEquals("info@stormberry.as", to)
        val params = query.split("&").associate { val (k, v) = it.split("=", limit = 2); k to java.net.URLDecoder.decode(v, "UTF-8") }
        assertEquals(setOf("subject", "body"), params.keys)
        assertEquals("StopScroll 0.0.2: missed a feed or blocked a normal video", params["subject"])
        assertEquals(
            "Which app:\nWhat happened:\n\nStopScroll 0.0.2 (1002)\nno.stormberry.stopscroll.play\nAndroid 17\nYouTube 21.42.36\nInstagram 453.0.0.57.79",
            params["body"],
        )
        // Spaces are %20, never "+", so every mail app shows them as spaces.
        assertEquals(false, mail.contains("+"))
        val none = LinkList.email("0.0.2", 2, "no.stormberry.stopscroll", "16", listOf("YouTube" to null, "Instagram" to null))
        assertEquals(true, java.net.URLDecoder.decode(none, "UTF-8").endsWith("\nno.stormberry.stopscroll\nAndroid 16\nYouTube not installed\nInstagram not installed"))
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
            consent = ConsentRecord("2026-10-29", 1, listOf(yt)), consentValid = true, serviceEnabled = true, serviceRunning = true,
            sdk = 36, installedFromFile = false, returnedFromSettings = false, appVersion = "0.0.1", rulesVersion = "r", disclosureVersion = 1,
            apps = listOf(AppRow("youtube", yt, "YouTube", "YouTube Shorts", Mode.ALWAYS, true, "21.42", AppStats(2, 0, "2026-10-29"))),
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
            setOf("id", "package", "name", "feedName", "mode", "installed", "versionName", "closed", "loopPauses", "lastClosed"),
            st.getJSONArray("apps").getJSONObject(0).keys().asSequence().toSet(),
        )
    }
}
