# Changelog

StopScroll for Android, newest first. The release text of each version, which GitHub Releases and Zapstore show, is in `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt`.

## 0.0.1 (versionCode 1)

First release. StopScroll closes YouTube's Shorts player as soon as it opens, from the Shorts tab, Home, search or a channel, and leaves the rest of YouTube working: search, subscriptions and normal videos.

- Per app: Off or Always block. Instagram, Facebook, Snapchat, LinkedIn and X are listed as coming soon; StopScroll does not watch them yet (they are not in the service's app list).
- The disclosure comes before Android's Accessibility settings, and a service turned on without agreement does nothing.
- Leaving the feed: Back, twice if needed, then one tab in the app, then Home; a loop guard pauses for 30 seconds after two failed attempts within 10 seconds.
- No permission at all, no internet, no ads, no account; settings and a few counts stay on the phone, left out of backups.
- A Google Play edition is built from the same source with `-PplayBuild=true`, as the package `no.stormberry.stopscroll.play` (versionName 0.0.1, versionCode 1001), with the same features.
