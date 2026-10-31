# Changelog

StopScroll for Android, newest first. The release text of each version, which GitHub Releases and Zapstore show, is in `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt`.

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
