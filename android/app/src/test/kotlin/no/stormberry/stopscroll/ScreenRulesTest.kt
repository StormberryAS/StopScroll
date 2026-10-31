package no.stormberry.stopscroll

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The rule engine over SANITISED fixtures of real screens (Pixel, Android 17): YouTube captured on
 * 2026-10-29, Instagram 453 on 2026-10-30, X 12.38.1 on 2026-10-31 (its tags found by the very
 * TagWalk the service runs).
 */
class ScreenRulesTest {
    private val rules = Repo.rules
    private val yt = "com.google.android.youtube"
    private val fixtures = Fixture.all("tests/fixtures/youtube")
    private val igFixtures = Fixture.all("tests/fixtures/instagram")
    private val xFixtures = Fixture.all("tests/fixtures/x")

    @Test fun theRulesFileParsesAndCoversYouTubeInstagramAndXOnly() {
        assertEquals(2, rules.schema)
        assertEquals(listOf(yt, IG, X), rules.packages)
        assertEquals(emptySet<String>(), rules.app(yt)!!.referencedTags)
        assertEquals(emptySet<String>(), rules.app(IG)!!.referencedTags)
        assertEquals(emptySet<String>(), rules.app(X)!!.referencedIds)
        assertEquals(setOf("VideoTab", "MediaGallery", "PostDetail"), rules.app(X)!!.referencedTags)
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
        assertEquals("the twenty-two X captures", 22, xFixtures.size)
        for (f in fixtures + igFixtures + xFixtures) {
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
            """{"schema":2,"rulesVersion":"t","date":"2026-10-29","apps":[{"id":"x","package":"com.example.x","name":"X","feedName":"X feed",
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
        assertThrows(IllegalArgumentException::class.java) { ScreenRules.parse(ok.replace("\"schema\": 2", "\"schema\": 1")) }
        assertThrows(IllegalArgumentException::class.java) { ScreenRules.parse(ok.replace("\"schema\": 2", "\"schema\": 3")) }
        // A tag is a plain name: no package, no colon, no wildcard; a view ID never takes a hyphen.
        assertThrows(IllegalArgumentException::class.java) { ScreenRules.parse(ok.replace("\"VideoTab\"", "\"com.twitter.android:id/VideoTab\"")) }
        assertThrows(IllegalArgumentException::class.java) { ScreenRules.parse(ok.replace("\"VideoTab\"", "\"Video*\"")) }
        assertThrows(IllegalArgumentException::class.java) { ScreenRules.parse(ok.replace("\"reel_watch_player\"", "\"reel-watch-player\"")) }
        assertThrows(IllegalArgumentException::class.java) { ScreenRules.parse(ok.replace("{ \"tag\": { \"id\": \"PostDetail\" } }", "{ \"tag\": { \"id\": \"PostDetail\", \"text\": \"x\" } }")) }
        assertThrows(IllegalArgumentException::class.java) { ScreenRules.parse(ok.replace("\"any\"", "\"some\"")) }
        assertThrows(IllegalArgumentException::class.java) { ScreenRules.parse(ok.replace("reel_watch_player", "reel_*")) }
        assertThrows(IllegalArgumentException::class.java) {
            ScreenRules.parse("""{"schema":2,"rulesVersion":"t","date":"2026-10-29","apps":[]}""")
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
        is Selector.Tag -> setOf(s.id)
    }

    @Test fun nodeFactHasNoTextField() {
        val fields = NodeFact::class.java.declaredFields.map { it.name }.filter { !it.startsWith("$") }.toSet()
        assertEquals(setOf("id", "className", "left", "top", "right", "bottom", "visible", "selected", "tag"), fields)
    }

    // X (0.0.3). X is a Compose app: its screens are named by test tags, which the walk finds.

    private fun x(label: String) = xFixtures.single { it.label == label }

    @Test fun xsVideoViewerIsClosedWhereverItOpens() {
        val viewers = listOf(
            "x-video-timeline-1", "x-video-next-1", "x-video-next-2", "x-video-post-1", "x-video-post-next-1",
            "x-video-search-1", "x-video-profile-1", "x-video-profile-2",
        )
        assertEquals(viewers.sorted(), xFixtures.filter { it.expect == "block" }.map { it.label }.sorted())
        viewers.forEach { assertEquals(it, "video-viewer", rules.evaluate(x(it).snapshot())?.surfaceId) }
        assertEquals("the X video feed", rules.app(X)!!.feedName)
        assertNull("X's viewer has no tab bar: Back, Back, Home", rules.app(X)!!.surfaces.single().otherTab)
    }

    @Test fun xsTimelinePostsSearchGalleryProfilesAndChatStayOpen() {
        val allowed = listOf(
            "x-home-1", "x-home-2", "x-post-1", "x-explore-1", "x-search-1", "x-notifications-1", "x-messages-1",
            "x-profile-1", "x-profile-own-1", "x-profile-videos-1", "x-drawer-1", "x-gallery-image-1", "x-gallery-video-1", "x-gallery-video-2",
        )
        assertEquals(allowed.sorted(), xFixtures.filter { it.expect == "allow" }.map { it.label }.sorted())
        allowed.forEach { assertNull(it, rules.evaluate(x(it).snapshot())) }
    }

    @Test fun theWalkStopsAtTheScreensOwnTagOnEveryXCapture() {
        // WALK-1 b, measured: exactly one bare tag on the first tagged level, the screen's own name, at
        // depth 10 as the 12th node (depth 6, the 8th node, in the compressed dump), never past the caps.
        val screenTag = mapOf(
            "x-home-1" to "MainLanding", "x-home-2" to "MainLanding", "x-explore-1" to "MainLanding", "x-notifications-1" to "MainLanding",
            "x-messages-1" to "MainLanding", "x-drawer-1" to "MainLanding", "x-post-1" to "PostDetail", "x-search-1" to "Search",
            "x-profile-1" to "UserProfileScreen", "x-profile-own-1" to "UserProfileScreen", "x-profile-videos-1" to "UserProfileScreen",
            "x-gallery-image-1" to "MediaGallery", "x-gallery-video-1" to "MediaGallery", "x-gallery-video-2" to "MediaGallery",
        ) + xFixtures.filter { it.expect == "block" }.associate { it.label to "VideoTab" }
        assertEquals(22, screenTag.size)
        for (f in xFixtures) {
            val found = f.walk()
            assertEquals(f.label, listOf(screenTag.getValue(f.label)), found.tagged.map { it.second })
            val compressed = f.label == "x-video-next-2"
            assertEquals(f.label, if (compressed) 7 else 11, found.levels)
            assertEquals(f.label, if (compressed) 8 else 12, found.fetched)
            assertTrue(found.levels <= TagWalk.MAX_WALK_LEVELS && found.fetched <= TagWalk.MAX_WALK_NODES)
        }
    }

    @Test fun theWalkIsCappedAt14LevelsAnd64NodesAndCountsEveryFetch() {
        // A chain of 30 untagged nodes: the walk stops after 14 levels (14 nodes).
        val chain = Fake.chain(30)
        val deep = TagWalk.walk(chain, { it.kids.size }, { n, i -> n.kids[i] }, { it.name })
        assertEquals(TagWalk.MAX_WALK_LEVELS, deep.levels)
        assertEquals(14, deep.fetched)
        assertTrue(deep.tagged.isEmpty())
        // A tag at depth 14 (the 15th level) is never reached; at depth 13 it is.
        assertTrue(TagWalk.walk(Fake.chain(30, tagAt = 14), { it.kids.size }, { n, i -> n.kids[i] }, { it.name }).tagged.isEmpty())
        assertEquals(listOf("VideoTab"), TagWalk.walk(Fake.chain(30, tagAt = 13), { it.kids.size }, { n, i -> n.kids[i] }, { it.name }).tagged.map { it.second })
        // A wide tree: 100 children under the root; the walk fetches 63 of them (64 with the root) and stops.
        var fetches = 0
        val wide = Fake("", List(100) { Fake("") })
        val r = TagWalk.walk(wide, { it.kids.size }, { n, i -> fetches++; n.kids[i] }, { it.name })
        assertEquals(TagWalk.MAX_WALK_NODES, r.fetched)
        assertEquals(63, fetches)
        // A child fetch that returns nothing still counts, and the walk goes on with the others.
        var calls = 0
        val holes = Fake("", listOf(Fake(""), Fake("VideoTab")))
        val h = TagWalk.walk(holes, { it.kids.size }, { n, i -> calls++; if (i == 0) null else n.kids[i] }, { it.name })
        assertEquals(2, calls)
        assertEquals(3, h.fetched)
        assertEquals(listOf("VideoTab"), h.tagged.map { it.second })
    }

    @Test fun theWalkStopsAtTheFirstTaggedLevelAndAViewIdIsNotATag() {
        // MainLanding wraps a node tagged VideoTab one level down: the walk stops at MainLanding (the
        // known fail-open case of WALK-1 b), so a viewer nested in another tagged screen is missed.
        val nested = Fake("", listOf(Fake("MainLanding", listOf(Fake("VideoTab")))))
        assertEquals(listOf("MainLanding"), TagWalk.walk(nested, { it.kids.size }, { n, i -> n.kids[i] }, { it.name }).tagged.map { it.second })
        // A real view ID ("<package>:id/<name>") is not a bare tag, and the walk goes past it.
        val view = Fake("com.twitter.android:id/VideoTab", listOf(Fake("PostDetail")))
        assertEquals(listOf("PostDetail"), TagWalk.walk(view, { it.kids.size }, { n, i -> n.kids[i] }, { it.name }).tagged.map { it.second })
        assertTrue(TagWalk.isBareTag("post-detail-reply-text-field"))
        assertTrue(!TagWalk.isBareTag("") && !TagWalk.isBareTag(null) && !TagWalk.isBareTag("android:id/content"))
        // Two tags on the same level are both found.
        val two = Fake("", listOf(Fake("PostDetail"), Fake("VideoTab")))
        assertEquals(listOf("PostDetail", "VideoTab"), TagWalk.walk(two, { it.kids.size }, { n, i -> n.kids[i] }, { it.name }).tagged.map { it.second })
    }

    @Test fun aTagAndAViewIdOfTheSameNameNeverStandInForEachOther() {
        val asView = NodeFact("VideoTab", "android.view.View", 0.0, 0.0, 1.0, 1.0, visible = true, selected = false)
        val asTag = asView.copy(tag = true)
        assertNull("a view called VideoTab is not the tag", rules.evaluate(Snapshot(X, listOf(asView))))
        assertNotNull(rules.evaluate(Snapshot(X, listOf(asTag))))
        // And the reverse: a tag named like YouTube's player is not the player.
        val ytAsTag = NodeFact("reel_watch_player", "android.widget.FrameLayout", 0.0, 0.0, 1.0, 1.0, visible = true, selected = false, tag = true)
        assertNull(rules.evaluate(Snapshot(yt, listOf(ytAsTag))))
        assertNotNull(rules.evaluate(Snapshot(yt, listOf(ytAsTag.copy(tag = false)))))
    }

    @Test fun aSmallVideoTabIsNotTheFeedAndTheExclusionsWin() {
        val viewer = NodeFact("VideoTab", "android.view.View", 0.0, 0.0, 1.0, 1.0, visible = true, selected = false, tag = true)
        assertNull("under 0.8 high (a future Video tab button, say)", rules.evaluate(Snapshot(X, listOf(viewer.copy(bottom = 0.79)))))
        assertNotNull(rules.evaluate(Snapshot(X, listOf(viewer.copy(top = 0.05, bottom = 0.95)))))
        assertNull("invisible", rules.evaluate(Snapshot(X, listOf(viewer.copy(visible = false)))))
        for (screen in listOf("MediaGallery", "PostDetail")) {
            assertNull(screen, rules.evaluate(Snapshot(X, listOf(viewer, viewer.copy(id = screen)))))
        }
        // The real viewer capture plus an exclusion tag: allowed.
        val real = x("x-video-timeline-1").snapshot()
        assertNull(rules.evaluate(real.copy(nodes = real.nodes + viewer.copy(id = "PostDetail"))))
    }

    @Test fun xsRulesReadOnlyXsWindowsAndTheOthersNeverReadX() {
        val viewer = x("x-video-timeline-1").snapshot()
        assertNotNull(rules.evaluate(viewer))
        assertNull(rules.evaluate(viewer.copy(packageName = yt)))
        assertNull(rules.evaluate(viewer.copy(packageName = IG)))
        assertNull(rules.evaluate(viewer.copy(packageName = "com.example.bank")))
        // YouTube's and Instagram's feeds as X snapshots: no verdict.
        assertNull(rules.evaluate(fixtures.first { it.expect == "block" }.snapshot().copy(packageName = X)))
        assertNull(rules.evaluate(ig("instagram-reel-feed-1").snapshot().copy(packageName = X)))
        // Every fixture of every app, read with the rules of every other app: never a verdict.
        for (f in fixtures + igFixtures + xFixtures) {
            for (other in rules.packages.filter { it != f.app }) {
                assertNull("${f.label} as $other", rules.evaluate(f.snapshot().copy(packageName = other)))
            }
        }
    }

    @Test fun anXSnapshotHoldsOnlyTheNamedTagsAndNoViewIds() {
        // Of the walk's tags only those the rules name become facts; the walk never reads past the
        // screen's own level, so timeline_post or post-detail-reply-text-field never reach a rule.
        for (f in xFixtures) {
            val snap = f.snapshot()
            assertTrue(f.label, snap.nodes.all { it.tag && it.id in rules.app(X)!!.referencedTags })
            assertTrue(f.label, snap.nodes.size <= 1)
        }
        assertEquals(listOf("PostDetail"), x("x-post-1").snapshot().nodes.map { it.id })
        assertTrue("MainLanding is not named by a rule", x("x-home-1").snapshot().nodes.isEmpty())
    }
}

/** A tiny tree for the TagWalk tests. */
private class Fake(val name: String?, val kids: List<Fake> = emptyList()) {
    companion object {
        /** A chain of [length] nodes, untagged, except the one at depth [tagAt] (VideoTab). */
        fun chain(length: Int, tagAt: Int = -1): Fake {
            var node: Fake? = null
            for (d in length - 1 downTo 0) node = Fake(if (d == tagAt) "VideoTab" else "", listOfNotNull(node))
            return node!!
        }
    }
}

private const val IG = "com.instagram.android"
private const val X = "com.twitter.android"
