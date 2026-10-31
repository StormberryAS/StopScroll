package no.stormberry.stopscroll

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The rule engine over SANITISED fixtures of real screens (Pixel, Android 17): YouTube captured on
 * 2026-10-29, Instagram 453 on 2026-10-30.
 */
class ScreenRulesTest {
    private val rules = Repo.rules
    private val yt = "com.google.android.youtube"
    private val fixtures = Fixture.all("tests/fixtures/youtube")
    private val igFixtures = Fixture.all("tests/fixtures/instagram")

    @Test fun theRulesFileParsesAndCoversYouTubeAndInstagramOnly() {
        assertEquals(listOf(yt, IG), rules.packages)
        assertEquals(setOf("reel_player_page_container", "reel_watch_player"), rules.app(yt)!!.referencedIds)
        assertEquals(
            setOf(
                "clips_viewer_view_pager", "clips_single_media_component", "clips_tab",
                "reel_viewer_root", "direct_inbox_action_bar", "inbox_refreshable_thread_list_recyclerview",
                "message_thread_container", "row_thread_composer_container",
            ),
            rules.app(IG)!!.referencedIds,
        )
    }

    @Test fun everyFixtureGetsTheVerdictItsLabelExpects() {
        assertEquals("the eight YouTube captures", 8, fixtures.size)
        assertEquals("the eighteen Instagram captures", 18, igFixtures.size)
        for (f in fixtures + igFixtures) {
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

    // INSTAGRAM (0.0.2). In Instagram's code "reel" means a Story and "clips" a Reel.

    private fun ig(label: String) = igFixtures.single { it.label == label }

    @Test fun instagramReelsAreBlockedWhereverTheyOpen() {
        val reels = listOf(
            "instagram-reels-tab-1", "instagram-reels-tab-2", "instagram-reel-feed-1", "instagram-reel-explore-1",
            "instagram-reel-profile-1", "instagram-reel-message-1", "instagram-reel-message-2",
        )
        assertEquals(reels.sorted(), igFixtures.filter { it.expect == "block" }.map { it.label }.sorted())
        reels.forEach { assertEquals(it, "reels", rules.evaluate(ig(it).snapshot())?.surfaceId) }
    }

    @Test fun storiesHomeChatsProfilesAndExploreStayOpen() {
        for (label in listOf(
            "instagram-stories-1", "instagram-home-1", "instagram-home-2", "instagram-direct-inbox-1", "instagram-chat-1",
            "instagram-profile-1", "instagram-profile-own-1", "instagram-post-profile-1", "instagram-explore-1",
            "instagram-explore-post-1", "instagram-explore-post-2",
        )) {
            assertNull(label, rules.evaluate(ig(label).snapshot()))
        }
    }

    @Test fun theInstagramMatchNamesNoStoryIdAndNoInlineVideo() {
        val surface = rules.app(IG)!!.surfaces.single()
        val matchIds = idsOf(surface.match)
        assertEquals(setOf("clips_viewer_view_pager", "clips_single_media_component", "clips_tab"), matchIds)
        assertTrue(matchIds.none { it.startsWith("reel") })
        assertTrue("the home feed's inline video is never a marker", "video_container" !in rules.app(IG)!!.referencedIds)
        // The Stories viewer carries reel_ IDs and no clips_ ID at all; its root is also an exclusion.
        val stories = ig("instagram-stories-1")
        assertTrue(stories.allNodes.any { it.id == "reel_viewer_root" })
        assertTrue(stories.allNodes.none { it.id.startsWith("clips_") })
        assertTrue(surface.exclude.any { idsOf(it) == setOf("reel_viewer_root") })
    }

    @Test fun anExclusionWinsEvenOverAFullReel() {
        val reel = ig("instagram-reel-feed-1").snapshot()
        assertNotNull(rules.evaluate(reel))
        for (id in listOf("reel_viewer_root", "direct_inbox_action_bar", "inbox_refreshable_thread_list_recyclerview", "message_thread_container", "row_thread_composer_container")) {
            val marker = NodeFact(id, "android.widget.FrameLayout", 0.0, 0.1, 1.0, 0.9, visible = true, selected = false)
            assertNull(id, rules.evaluate(reel.copy(nodes = reel.nodes + marker)))
        }
    }

    @Test fun aReelSentInAMessageIsClosedByDefaultAndTheMarkersForALaterSwitchAreThere() {
        // DM-1 at its default: closed. The markers a later switch would use (spec 6.4) are in the
        // capture, but no rule reads them in 0.0.2.
        val sent = ig("instagram-reel-message-1")
        val next = ig("instagram-reel-message-2")
        val sentIds = sent.allNodes.map { it.id }.toSet()
        val nextIds = next.allNodes.map { it.id }.toSet()
        assertTrue(sentIds.containsAll(listOf("reply_bar_container_scroll_view", "sender_username_or_fullname")))
        assertTrue("suggested_title" !in sentIds)
        assertTrue("suggested_title" in nextIds)
        assertTrue("reply_bar_container_scroll_view" !in nextIds)
        assertNotNull(rules.evaluate(sent.snapshot()))
        assertNotNull(rules.evaluate(next.snapshot()))
        assertTrue(rules.app(IG)!!.referencedIds.none { it.startsWith("reply_bar") || it == "suggested_title" || it.startsWith("sender_") })
    }

    @Test fun aPostOpenedFromExploreIsNotAReelEvenInTheSameViewer() {
        // IG-EXP-1: a photo or carousel post opened from Explore opens in the Reels pager, which is
        // full height, but its item is a carousel, not a single video.
        for (label in listOf("instagram-explore-post-1", "instagram-explore-post-2")) {
            val f = ig(label)
            assertTrue(label, f.allNodes.any { it.id == "clips_viewer_view_pager" && it.height >= 0.6 })
            assertTrue(label, f.allNodes.none { it.id == "clips_single_media_component" })
            assertTrue(label, f.allNodes.any { it.id == "clips_carousel_viewpager" })
            assertNull(label, rules.evaluate(f.snapshot()))
        }
    }

    @Test fun onTheReelsTabThePagerIsTheFeedWhateverItHolds() {
        val pager = NodeFact("clips_viewer_view_pager", "androidx.viewpager.widget.ViewPager", 0.0, 0.07, 1.0, 0.92, visible = true, selected = false)
        val tab = NodeFact("clips_tab", "android.widget.FrameLayout", 0.2, 0.92, 0.4, 0.97, visible = true, selected = true)
        assertNotNull(rules.evaluate(Snapshot(IG, listOf(pager, tab))))
        assertNull(rules.evaluate(Snapshot(IG, listOf(pager, tab.copy(selected = false)))))
        assertNull("the tab alone is not the feed", rules.evaluate(Snapshot(IG, listOf(tab))))
        val reelsTab = ig("instagram-reels-tab-1")
        assertTrue(reelsTab.allNodes.any { it.id == "clips_tab" && it.selected })
        assertTrue(ig("instagram-home-1").allNodes.any { it.id == "clips_tab" && !it.selected })
    }

    @Test fun aSmallInstagramPagerIsNotTheFeed() {
        val pager = NodeFact("clips_viewer_view_pager", "c", 0.0, 0.1, 1.0, 0.5, visible = true, selected = false)
        val single = pager.copy(id = "clips_single_media_component")
        assertNull(rules.evaluate(Snapshot(IG, listOf(pager, single))))
        assertNotNull(rules.evaluate(Snapshot(IG, listOf(pager.copy(bottom = 0.75), single.copy(bottom = 0.75)))))
        assertNull(rules.evaluate(Snapshot(IG, listOf(pager.copy(bottom = 0.75), single.copy(visible = false, bottom = 0.75)))))
    }

    @Test fun theOtherTabIsInstagramsHomeFeedTab() {
        assertEquals(OtherTab("tab_bar", "firstClickable"), rules.app(IG)!!.surfaces.single().otherTab)
        // The five tabs sit directly under tab_bar (depth 1, within NodeFacts.TAB_DEPTH), the home
        // feed first, so the first clickable child is feed_tab. The bar is hidden on a Reel opened
        // from anywhere but the Reels tab, where the ladder uses Home instead (step 3).
        for (label in listOf("instagram-reels-tab-1", "instagram-home-1")) {
            assertEquals(label, listOf("feed_tab", "clips_tab", "direct_tab", "search_tab", "profile_tab"), ig(label).childIds("tab_bar"))
        }
        for (label in listOf("instagram-reel-feed-1", "instagram-reel-explore-1", "instagram-reel-profile-1", "instagram-reel-message-1")) {
            assertTrue(label, ig(label).allNodes.none { it.id == "tab_bar" })
        }
    }

    @Test fun eachAppsRulesReadOnlyThatAppsWindows() {
        val reel = ig("instagram-reel-feed-1").snapshot()
        assertNull(rules.evaluate(reel.copy(packageName = yt)))
        assertNull(rules.evaluate(reel.copy(packageName = "com.example.bank")))
    }

    private fun idsOf(s: Selector): Set<String> = when (s) {
        is Selector.Any -> s.of.flatMap { idsOf(it) }.toSet()
        is Selector.All -> s.of.flatMap { idsOf(it) }.toSet()
        is Selector.Not -> idsOf(s.of)
        is Selector.Node -> setOf(s.id)
    }

    @Test fun nodeFactHasNoTextField() {
        val fields = NodeFact::class.java.declaredFields.map { it.name }.filter { !it.startsWith("$") }.toSet()
        assertEquals(setOf("id", "className", "left", "top", "right", "bottom", "visible", "selected"), fields)
    }
}

private const val IG = "com.instagram.android"
