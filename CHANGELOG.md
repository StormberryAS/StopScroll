# Changelog

StopScroll for Android, newest first. The release text of each version, which GitHub Releases and Zapstore show, is in `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt`.

## 0.0.3 (versionCode 3)

StopScroll now closes X's full-screen video viewer too, the endless video feed X opens when you tap a video, and leaves the rest of X working: the timeline, posts, search, notifications, messages and profiles.

- X moves from "Coming soon" to a normal row: Off or Always block. Facebook, Snapchat and LinkedIn stay coming soon.
- Closed: the viewer opened from the timeline, a post, search or a profile's Videos tab. Videos still play in the timeline, in posts and in search, and a video in a post with several photos or videos opens in X's gallery, which stays open.
- How: X names its screens with Jetpack Compose test tags. A new rule selector, `tag`, matches such a name, found by a short walk down the screen's tree (no prefetching, at most 14 levels and 64 nodes, stopping at the first level that holds a tag). It reads the same facts as the view-ID lookups and no text. The rules file is schema 2.
- Leaving X's viewer: Back, twice if needed, then Home, with the same loop guard.
- An agreement now counts per app. After the update from 0.0.2, YouTube and Instagram keep working as you set them, and X waits until you open StopScroll and agree; until then StopScroll's code receives nothing from X. An agreement from 0.0.1 still does not count.
- The short message after a close now sits higher, so it no longer covers the apps' tab bar icons.
- The service's app list, the package query list, the rules and the release workflow's pinned list cover exactly YouTube, Instagram and X.
- 22 sanitised X captures (X 12.38.1, Android 17) join the YouTube and Instagram ones in `tests/fixtures/`, and the Kotlin tests run the rules over every one of them.
- The store description, the screenshots and this release's note are the same on Zapstore and Google Play.
- Google Play edition: versionName 0.0.3, versionCode 1003. 1002 was built but never uploaded.

## 0.0.2 (versionCode 2)

StopScroll now closes Instagram Reels too, and leaves the rest of Instagram working: Stories, the home feed, messages, profiles, Explore and search.

- Instagram moves from "Coming soon" to a normal row: Off or Always block, Always block until you choose. Facebook, Snapchat, LinkedIn and X stay coming soon.
- Closed: the Reels tab, and a Reel opened from the home feed, from Explore, from a profile's Reels tab or from a message (a Reel someone sends you is closed too; a switch to allow it may come later). A photo or carousel post opened from Explore stays open, although Instagram shows it in the same viewer.
- Stories are never matched: in Instagram's own code "reel" means a Story, so the rule names no `reel_` view, and the Stories viewer and the chat screens are exclusions that always win. Leaving the feed: Back, twice if needed, then Instagram's home feed tab (where the tab bar shows; elsewhere Home), then Home, with the same loop guard as YouTube.
- The disclosure names YouTube and Instagram (disclosure version 2), so after the update StopScroll does nothing until you open it and agree again. The Accessibility description, the privacy page, the disclaimer and the report email (which now lists the installed Instagram version too) say the same.
- The service's app list, the package query list and the rules cover exactly YouTube and Instagram; the release workflow refuses an APK that watches anything else.
- 18 sanitised Instagram captures (Instagram 453, Android 17) join the 8 YouTube ones in `tests/fixtures/`, and the Kotlin tests run the rules over every one of them.
- Google Play edition: versionName 0.0.2, versionCode 1002.

## 0.0.1 (versionCode 1)

First release. StopScroll closes YouTube's Shorts player as soon as it opens, from the Shorts tab, Home, search or a channel, and leaves the rest of YouTube working: search, subscriptions and normal videos.

- Per app: Off or Always block. Instagram, Facebook, Snapchat, LinkedIn and X are listed as coming soon; StopScroll does not watch them yet (they are not in the service's app list).
- The disclosure comes before Android's Accessibility settings, and a service turned on without agreement does nothing.
- Leaving the feed: Back, twice if needed, then one tab in the app, then Home; a loop guard pauses for 30 seconds after two failed attempts within 10 seconds.
- No permission at all, no internet, no ads, no account; settings and a few counts stay on the phone, left out of backups.
- A Google Play edition is built from the same source with `-PplayBuild=true`, as the package `no.stormberry.stopscroll.play` (versionName 0.0.1, versionCode 1001), with the same features.
