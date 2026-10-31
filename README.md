# StopScroll

Closes the short-video feeds and leaves the rest of each app alone. No ads. No internet.

StopScroll is an Android app. When the Shorts or Reels player opens in the YouTube or Instagram app, the full-screen video viewer in the X app or Samples in the YouTube Music app, StopScroll closes it for you: it presses Back, or in YouTube Music the app's Home tab. Search, subscriptions, normal videos, Stories, messages and profiles keep working, in X videos keep playing in the timeline and in posts, and in YouTube Music songs, albums and music videos keep playing. It has no internet permission and no other permission at all, it shows no ads, and it needs no account.

A functioning prototype from [Stormberry AS](https://stormberry.as), Norway. Not affiliated with or endorsed by Google, YouTube, Meta, Snap or X.

## What it does

- **Closes YouTube Shorts.** When the Shorts player opens (from the Shorts tab, a Short on Home, in search or on a channel), StopScroll presses Back, twice if needed. If that does not leave the feed, it presses one tab in the same app, then Home. It presses nothing else.
- **Closes Instagram Reels** (since 0.0.2). The Reels tab, and a Reel opened from the home feed, from Explore, from a profile's Reels tab or from a message: a Reel someone sends you is closed too. Stories, the home feed, messages, profiles and search stay open, and so does a photo or a carousel post opened from Explore, which Instagram shows in the same viewer as Reels. The same steps as in YouTube; the tab it presses is Instagram's home feed tab (`feed_tab`, where the tab bar shows; elsewhere Home).
- **Closes X's full-screen video viewer** (since 0.0.3). In X this viewer is the endless video feed: it opens when you tap a video in the timeline, a post, search or a profile, and you swipe up for the next one. Videos still play in the timeline, in posts and in search results, and a post with several photos or videos opens in X's gallery, which stays open. X builds its screens with Jetpack Compose and names each screen with a test tag, so StopScroll looks for the viewer's tag (`VideoTab`, at least 80% of the screen high) in a short walk down the screen's tree: no prefetching, at most 14 levels and 64 parts, stopping at the first level that has a name. The walk reads the same facts as the view-ID lookups (name, class, position, visible, selected) and no text. Leaving: Back, twice if needed, then Home (X's viewer has no tab bar).
- **Closes YouTube Music's Samples** (since 0.0.4). Samples is YouTube Music's feed of short video clips of songs, where you swipe up for the next one; StopScroll closes it on the Samples tab, from the Samples button on an album or artist page, and from a link. Songs, albums, playlists, music videos and the player keep working. Samples uses the same kind of player as YouTube Shorts (its view IDs start with `reel_`), so the rule looks for three of those view IDs (`reel_recycler`, `reel_watch_player` or `reel_player_page_container`, at least 60% of the screen high), and the full player (`player_page`, at least 90% high) always wins, so a song opened from a Sample keeps playing. Leaving: on the Samples tab Back closes YouTube Music and leaves the music you were playing paused, so here StopScroll presses the app's Home tab first (the first tab in `pivot_bar`); YouTube Music then carries on with your music. YouTube Music makes its selected tab not clickable, so when Home is already the selected tab (Samples opened from an album or artist page that you reached from Home), StopScroll presses no tab and Back comes first, which takes you back to that page. Back, the tab again and Home follow only if needed. From an album or artist page reached from another tab you land on Home, not back on the page. The rule says this with `"leave": "otherTabFirst"` and the tab `"child": "first"` (rules schema 3).
- **Per app, you choose:** Off or Always block. Off is one tap away, and "Turn StopScroll off" switches the service off in Android's settings.
- **Asks first.** Before Android's Accessibility settings open, the app shows what the service looks at, what it does and what it keeps, and waits for "I agree". A service turned on without that agreement does nothing. An agreement counts for the apps it named: when a version adds an app, StopScroll does not watch it until you agree again, and the apps you agreed to before keep working.
- **A loop guard.** If the feed is still there after two full attempts within 10 seconds, StopScroll leaves the app alone for 30 seconds and counts a pause.

### Why these four apps, and not the others yet

StopScroll recognises a feed by the names an app gives the parts of its screen, checked on a real phone and kept in `rules/screen-rules.json` with sanitised test captures in `tests/fixtures/`. YouTube's Shorts player (0.0.1), Instagram's Reels viewer (0.0.2), X's full-screen video viewer (0.0.3) and YouTube Music's Samples (0.0.4) have been checked; the other apps have not yet. Facebook and Snapchat appear in the app as **coming soon**: they are names on the screen and nothing more. They are not in the service's app list (`android:packageNames` in `android/app/src/main/res/xml/accessibility_service_config.xml`), so Android delivers StopScroll's code nothing from them, and the disclosure names YouTube, Instagram, X and YouTube Music only. Each one is added in a later release, together with its rules, its captures and a disclosure that names it. An agreement counts per app: after the update from 0.0.3, YouTube, Instagram and X keep working as you set them, and YouTube Music waits until you open StopScroll and agree (after an update from 0.0.2, X waits too). An agreement from 0.0.1 still does not count, so after an update from 0.0.1 StopScroll does nothing until you agree.

In Instagram's own code "reel" means a Story, so the Instagram rule names no `reel_` view at all: it needs Instagram's short-video pager (`clips_viewer_view_pager`, at least 60% of the screen high) holding a single video, or the Reels tab selected, and the Stories viewer and every chat screen are listed as exclusions that always win.

## Privacy

- **No internet permission.** StopScroll cannot contact a server itself, ours or anyone else's. Links in the app open in your browser or mail app, and only from a fixed list compiled into the app.
- **No ads, no account, no analytics, no logs.**
- **What it looks at.** Only while you are in the YouTube, Instagram, X or YouTube Music app, it reads the names and positions of the parts of the screen to see whether a short-video feed is open. It does not read the words on your screen: not your messages, captions, passwords or anything you type. One file, `NodeFacts.kt`, touches another app's screen, and its node type has no text field; `tests/guards.test.ts` fails the build if any code reads screen text.
- **Other apps.** Each time it checks the screen, it first reads which app is in front; if that is not YouTube, Instagram, X or YouTube Music, it stops there and reads nothing else. StopScroll's code receives nothing from an app you have not agreed to.
- **What it keeps, on the phone only:** your settings, the date you agreed to the disclosure, and per app the number of feeds closed, the number of pauses and the date it last closed a feed. Backups and phone-to-phone transfers leave them out (`allowBackup="false"` and both data-extraction rule files exclude everything).
- **What Android does on its own.** While an Accessibility service looks at a screen, Android's own code inside the app can also receive notices of screen changes in other apps, for a cache Android keeps. StopScroll turns that cache off straight after each check (`setCacheEnabled(false)`), so this lasts only for the check and never happens while StopScroll is off. StopScroll's code never sees these notices.
- **The honest limit.** On Android, an app update can add the internet permission without asking you. So the promise rests on this open code and on the permission check every release runs, not on Android asking again.

The full text is in `privacy.html`, which the app shows from its footer.

## Permissions: none

```bash
aapt dump permissions <the APK you downloaded>
```

The only line you should see is the package name, `package: no.stormberry.stopscroll`. The service is protected by `android.permission.BIND_ACCESSIBILITY_SERVICE`, which only Android itself holds; the app does not ask for it. INTERNET is removed explicitly in the manifest, in case a library ever merges it in. Every build compares the APK's permissions with `android/expected-permissions.txt` in both directions, and the release workflow does it again before the key is used.

## Verify a release

```bash
sha256sum -c stopscroll-v0.0.4.apk.sha256
apksigner verify --print-certs stopscroll-v0.0.4.apk
gh attestation verify stopscroll-v0.0.4.apk -R StormberryAS/StopScroll
```

**Signing certificate SHA-256:** `1e4aa9ee4d927009dbe8ba17cfd05f496213e6001adf79afdb2d5b90dbc5591a`

The release workflow (`.github/workflows/android-release.yml`, started by `android-v*` tags only) builds the APK unsigned in a job that holds no key. A second job, which runs no Gradle, checks that APK before the key is decoded: no permission but the package line, not debuggable, the version the tag names, the watched apps (exactly YouTube, Instagram, X and YouTube Music), every bundled page and file byte for byte the tagged commit's, and the licence texts. Only then does it align the APK and sign it with apksigner, and it refuses to publish unless there is exactly one signer, with the certificate above. Each release also waits for an approval on GitHub before the key is used.

## Reproducible build

The same commit gives the same unsigned APK, byte for byte, in two clean builds on our machine. Whether GitHub's build gives the same bytes is checked at every release, and this README will say "reproducible" only once a release has matched. To check a release against its tag yourself, build it unsigned, sign it with a key of your own the way the workflow signs, and compare the two with their signatures taken out (`android/tools/apk-without-signature.py`, Python 3 standard library only):

```bash
git clone https://github.com/StormberryAS/StopScroll.git
cd StopScroll && git checkout android-v0.0.4
cd android && ./gradlew --no-daemon --no-build-cache --no-configuration-cache -PunsignedRelease=true :app:assembleRelease
BT="$ANDROID_HOME/build-tools/36.0.0"
"$BT/zipalign" -f 4 app/build/outputs/apk/release/app-release-unsigned.apk aligned.apk
keytool -genkeypair -keystore mine.p12 -storetype PKCS12 -alias mine -keyalg RSA -keysize 4096 -validity 1 -dname CN=mine
"$BT/apksigner" sign --ks mine.p12 --ks-key-alias mine \
  --v1-signing-enabled false --v2-signing-enabled true --v3-signing-enabled true --v4-signing-enabled false \
  --out mine.apk aligned.apk
cd .. && python3 android/tools/apk-without-signature.py android/mine.apk
python3 android/tools/apk-without-signature.py /path/to/stopscroll-v0.0.4.apk
```

The two lines must show the same SHA-256. Use JDK 21 (the workflow uses Temurin 21) and a 4096-bit key as above, the size of the release key, so the signing block takes the same room.

## Build and test

Requirements: JDK 21, the Android SDK with platform 37.1 and build-tools 36.0.0, and Node 24.

```bash
tools/build-ts.sh              # compile the TypeScript (pinned 6.0.3) into app.js, core/ and the bridge script
tools/build-ts.sh --check      # fail if a compiled .js is stale or hand-edited
npm test                       # Node tests (TypeScript, run by Node 24)
cd android
./gradlew --no-daemon :app:testDebugUnitTest :app:lintRelease :app:assembleDebug
./gradlew --no-daemon -PunsignedRelease=true :app:assembleRelease    # the unsigned release APK
```

The app bundles an explicit list of files (`WebAssets.INCLUDES` in `android/app/build.gradle.kts`): the pages, their compiled scripts, the style sheets, the icons, the Inter font and its licence. Every build checks that each bundled file is byte for byte the one in the repository (apart from one line in `index.html` that loads `android/web-overlay/android-bridge.js`), and copies `rules/screen-rules.json` beside them. `fastlane/`, `zapstore.yaml` and this README are never bundled.

**The "in TS" rule:** there is no hand-written JavaScript in this repository. `app.js`, `core/*.js` and `android/web-overlay/android-bridge.js` are compiled from TypeScript and marked `linguist-generated` in `.gitattributes`; `tests/guards.test.ts` fails on any other tracked `.js`. Tools are TypeScript, Python or shell; the Android app is Kotlin.

## Layout

| Path | What |
|-|-|
| `ts/` | The settings screen in TypeScript (`app.ts`), its pure logic (`core/flow.ts`) and words (`core/copy.ts`) |
| `app.js`, `core/*.js` | Compiled from `ts/` by `tools/build-ts.sh` (never edit) |
| `index.html`, `privacy.html`, `disclaimer.html`, `style.css`, `favicon.svg` | The pages the app's WebView shows; `disclaimer.html` and `disclaimer.css` are generated from `DISCLAIMER.md` by Stormberry's shared generator |
| `rules/screen-rules.json` | The screen rules, data; copied into the APK |
| `android/` | The Kotlin project: `FeedService` (the service), `NodeFacts` (the only file touching another app's screen), `TagWalk` (the walk that finds a screen tag, pure), `ScreenRules`, `LeaveLadder`, `ServiceConfig`, `Consent`, `Settings`, `Bridge`, `MainActivity` |
| `android/web-overlay/android-bridge.ts` | The page side of the app bridge (compiled to `android-bridge.js`) |
| `tests/` | Node tests in TypeScript; `tests/fixtures/` holds sanitised captures (structure only, no screen text) |
| `tools/scrub-fixture.ts` | Turns a raw screen dump into a sanitised fixture |
| `fastlane/`, `zapstore.yaml` | Store texts and images |

## Google Play edition

The same source also builds a Google Play edition, a separate package, `no.stormberry.stopscroll.play`, with the same features and the same lack of permissions. Only the application ID, the versionCode and the signing differ: Google re-signs the bundle with an app signing key it holds, and our upload key (certificate SHA-256 `08f36e5e3accd91a64d7e945c477f24124ea7eecc573925fca0c59462995e2fd`) only proves who uploaded it. The two packages install side by side and neither can update the other.

```bash
./gradlew --no-configuration-cache :app:bundleRelease -PplayBuild=true   # needs the Play upload key (PLAY_UPLOAD_*)
```

The build refuses a release bundle without `-PplayBuild=true` or without a complete upload key, a release APK with `-PplayBuild=true`, an upload key that is the release key, and a signing password with the configuration cache on. Every Play bundle is built from a release tag that shipped on GitHub Releases; Play shows the same version name, and its versionCode is 1000 plus the sovereign one.

| Release tag | versionCode | Play versionName | Play versionCode |
|-|-|-|-|
| `android-v0.0.1` | 1 | 0.0.1 | 1001 |
| `android-v0.0.2` | 2 | 0.0.2 | 1002 (built, never uploaded) |
| `android-v0.0.3` | 3 | 0.0.3 | 1003 (never uploaded) |
| `android-v0.0.4` | 4 | 0.0.4 | 1004 |

## Licence and disclaimer

MIT (`LICENSE`); third-party notices in `NOTICE`. StopScroll is a functioning prototype, not a finished product: see `DISCLAIMER.md`, shown in the app as `disclaimer.html`. It is not an accessibility tool and not parental control: you can turn it off at any time.

Built by [Stormberry AS](https://stormberry.as). Proudly powered by sovereign AI agents.
