package no.stormberry.stopscroll

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** The rule engine over SANITISED fixtures of real YouTube screens (Pixel, Android 17, 2026-10-29). */
class ScreenRulesTest {
    private val rules = Repo.rules
    private val yt = "com.google.android.youtube"
    private val fixtures = Fixture.all("tests/fixtures/youtube")

    @Test fun theRulesFileParsesAndCoversYouTubeOnly() {
        assertEquals(listOf(yt), rules.packages)
        assertEquals(setOf("reel_player_page_container", "reel_watch_player"), rules.app(yt)!!.referencedIds)
    }

    @Test fun everyFixtureGetsTheVerdictItsLabelExpects() {
        assertEquals("the eight YouTube captures", 8, fixtures.size)
        for (f in fixtures) {
            val verdict = rules.evaluate(f.snapshot())
            when (f.expect) {
                "block" -> assertNotNull("${f.label} must be blocked", verdict)
                "allow" -> assertNull("${f.label} must be allowed", verdict)
                else -> error("${f.label}: unknown expect ${f.expect}")
            }
        }
    }

    @Test fun shortsAreBlocked() {
        val shorts = fixtures.filter { it.label.startsWith("youtube-shorts-") }
        assertEquals(4, shorts.size)
        shorts.forEach { assertEquals(it.label, "shorts", rules.evaluate(it.snapshot())?.surfaceId) }
    }

    @Test fun normalVideoHomeAndTransitionAreAllowed() {
        for (label in listOf("youtube-video-1", "youtube-video-2", "youtube-home-1", "youtube-transition-1")) {
            val f = fixtures.single { it.label == label }
            assertNull(label, rules.evaluate(f.snapshot()))
        }
    }

    @Test fun theNormalPlayerCarriesAReelIdSoNoPrefixMatchIsEverUsed() {
        // The trap of spec 6.4: reel_time_bar is on the normal watch page too.
        val video = fixtures.single { it.label == "youtube-video-1" }
        assertTrue(video.allNodes.any { it.id == "reel_time_bar" })
        assertTrue(video.allNodes.any { it.id == "player_fragment_container" })
        assertNull(rules.evaluate(Snapshot(yt, video.allNodes)))
    }

    @Test fun theTransitionCaptureHasOnlyTheTimeBar() {
        val t = fixtures.single { it.label == "youtube-transition-1" }
        assertEquals(setOf("reel_time_bar"), t.allNodes.map { it.id }.filter { it.startsWith("reel_") }.toSet())
        assertNull(rules.evaluate(Snapshot(yt, t.allNodes)))
    }

    @Test fun anotherPackageGetsNoVerdictEvenWithTheShortsIds() {
        val shorts = fixtures.first { it.expect == "block" }.snapshot()
        assertNull(rules.evaluate(shorts.copy(packageName = "com.example.bank")))
        assertNull(rules.evaluate(shorts.copy(packageName = "com.instagram.android")))
    }

    @Test fun aSmallPlayerIsNotTheFeed() {
        val small = NodeFact("reel_watch_player", "android.widget.FrameLayout", 0.0, 0.1, 1.0, 0.4, visible = true, selected = false)
        assertNull(rules.evaluate(Snapshot(yt, listOf(small))))
        assertNotNull(rules.evaluate(Snapshot(yt, listOf(small.copy(bottom = 0.75)))))
    }

    @Test fun anInvisibleNodeNeverMatches() {
        val hidden = NodeFact("reel_watch_player", "android.widget.FrameLayout", 0.0, 0.0, 1.0, 1.0, visible = false, selected = false)
        assertNull(rules.evaluate(Snapshot(yt, listOf(hidden))))
    }

    @Test fun anEmptyScreenFailsOpen() {
        assertNull(rules.evaluate(Snapshot(yt, emptyList())))
    }

    @Test fun excludesWinOverAMatch() {
        val r = ScreenRules.parse(
            """{"schema":1,"rulesVersion":"t","date":"2026-10-29","apps":[{"id":"x","package":"com.example.x","name":"X","feedName":"X feed",
               "surfaces":[{"id":"feed","match":{"node":{"id":"feed"}},"exclude":[{"node":{"id":"chat"}}]}],
               "verifiedOn":[{"device":"d","android":"17","appVersionName":"1","appVersionCode":1,"date":"2026-10-29","how":"h"}]}]}""",
        )
        val feed = NodeFact("feed", "c", 0.0, 0.0, 1.0, 1.0, true, false)
        assertNotNull(r.evaluate(Snapshot("com.example.x", listOf(feed))))
        assertNull(r.evaluate(Snapshot("com.example.x", listOf(feed, feed.copy(id = "chat")))))
    }

    @Test fun theParserRefusesWhatItDoesNotKnow() {
        val ok = Repo.file("rules/screen-rules.json").readText()
        assertThrows(IllegalArgumentException::class.java) { ScreenRules.parse(ok.replace("\"minHeight\"", "\"minHieght\"")) }
        assertThrows(IllegalArgumentException::class.java) { ScreenRules.parse(ok.replace("\"schema\": 1", "\"schema\": 2")) }
        assertThrows(IllegalArgumentException::class.java) { ScreenRules.parse(ok.replace("\"any\"", "\"some\"")) }
        assertThrows(IllegalArgumentException::class.java) { ScreenRules.parse(ok.replace("reel_watch_player", "reel_*")) }
        assertThrows(IllegalArgumentException::class.java) {
            ScreenRules.parse("""{"schema":1,"rulesVersion":"t","date":"2026-10-29","apps":[]}""")
        }
    }

    @Test fun theOtherTabIsYouTubesBottomBar() {
        val tab = rules.app(yt)!!.surfaces.single().otherTab
        assertEquals(OtherTab("pivot_bar", "firstClickable"), tab)
        // The bar is in every Shorts capture, and its tab Buttons sit within NodeFacts.TAB_DEPTH
        // levels of it (one wrapper LinearLayout in uiautomator's tree). Whether the service sees
        // the Buttons as clickable, and what the press does, waits for the device pass.
        fixtures.filter { it.expect == "block" }.forEach { f ->
            assertTrue(f.label, f.allNodes.any { it.id == "pivot_bar" })
            val depth = f.depthOfFirst("pivot_bar", "android.widget.Button")
            assertTrue("${f.label}: tab Button at depth $depth", depth != null && depth <= NodeFacts.TAB_DEPTH)
        }
    }

    @Test fun nodeFactHasNoTextField() {
        val fields = NodeFact::class.java.declaredFields.map { it.name }.filter { !it.startsWith("$") }.toSet()
        assertEquals(setOf("id", "className", "left", "top", "right", "bottom", "visible", "selected"), fields)
    }
}
