// Every word the app screen shows, in one place. British English, plain words, no em dashes, no
// double hyphens (stormberry-content-core). Approved by Marcos as written on 29 October 2026 (COPY-1).
// Texts taken from the spec (section 12) say "spec 12.x"; texts changed for the 0.0.1 scope (YouTube
// only, no minutes per day, no once-a-second check) or written new say "NEW". The 0.0.1 release
// kit (29 October 2026) added the coming-soon list (Marcos: "Release StopScroll as v 0.0.1 with
// youtube only, and add Instagram/Facebook/Snapchat/Linkedin/X coming soon"). 0.0.2 (30 October 2026)
// watches Instagram too (Marcos: "include Instagram on StopScroll"); every text it changed says
// "0.0.2" and was approved by Marcos on 30 October 2026 ("Everything is approved for StopScroll").
// tests/copy.test.ts checks the house style of every string here.

/** Bump when the disclosure's meaning changes: a consent counts only for the version it was given to. Equals Consent.DISCLOSURE_VERSION in Kotlin. */
export const DISCLOSURE_VERSION = 2;

export const text = {
  // App top (spec 12.6 hero line, also the Labs app-top tagline). 0.0.2, approved 2026-10-30: the spec's plural
  // hero line, now that two apps are watched.
  tagline: 'Closes the short-video feeds and leaves the rest of each app alone. No ads. No internet.',

  // Status line. NEW.
  statusNoConsent: 'StopScroll is not on yet.',
  statusOff: 'You agreed. Now turn StopScroll on in Android’s Accessibility settings.',
  statusAllOff: 'StopScroll is on, but every app is set to Off.',
  statusOn: 'StopScroll is on.',

  // Intro (explainer, spec 6.7 step 1). From spec 12.1, narrowed to YouTube and Instagram. The first
  // paragraph is 0.0.2, approved 2026-10-30 (DM-1 at its default: a Reel sent in a message is closed too).
  introTitle: 'What StopScroll does',
  introBody: [
    'StopScroll closes the endless short-video feeds in the YouTube and Instagram apps and leaves the rest of each app working. When the Shorts or Reels player opens, StopScroll presses Back for you. Search, subscriptions, normal videos, Stories, messages and profiles keep working. A Reel someone sends you in a message is closed too.',
    'No ads, no account, no server calls. StopScroll has no internet permission at all, so it never contacts a server, ours or anyone else’s.',
    'StopScroll is not an accessibility tool and not parental control: you can turn it off at any time.',
  ],
  // 0.0.1 kit; 0.0.2, approved 2026-10-30: Instagram left the list.
  introNext: 'Coming soon: Facebook, Snapchat, LinkedIn and X. StopScroll does not watch them yet.',
  turnOnButton: 'Turn on blocking',

  // The prominent disclosure (spec 12.3), for the apps this build watches. Changed for stage 1 part
  // one: no Facebook sentence (FB-1 is stage 2), "What it keeps" without minutes per day (no time
  // accounting yet), and "Other apps" says what this build really does (no once-a-second check).
  // 0.0.2, approved 2026-10-30 (DISCLOSURE_VERSION 2): "feeds" and "a Shorts or Reels player", spec 12.3's own
  // words; the apps come from the build's list ("the YouTube or Instagram app").
  disclosureTitle: 'StopScroll needs Accessibility access',
  disclosure: (apps: string): { lead: string | null; body: string }[] => [
    { lead: null, body: 'To find the short-video feeds, StopScroll uses Android’s Accessibility Service.' },
    {
      lead: 'What it looks at.',
      body: `Only while you are in ${apps}. It reads the names and positions of the parts of the screen to see whether a Shorts or Reels player is open. It does not read the words on your screen: not your messages, captions, passwords or anything you type.`,
    },
    {
      lead: 'What it does.',
      body: 'When a feed you chose to block opens, StopScroll presses Back for you, twice if needed. If that does not leave the feed, it presses one tab in the same app, then Home. It presses nothing else.',
    },
    {
      lead: 'What it keeps.',
      body: 'Your settings, the date you agreed to this and a few counts, such as when it last closed a feed, on this phone only. StopScroll has no internet permission, so it cannot connect to anything itself.',
    },
    {
      lead: 'Other apps.',
      body: 'StopScroll’s code gets nothing from other apps, with one exception: each time it checks the screen, it first reads which app is in front, and if that is not an app it watches, it stops there. It reads nothing else from other apps.',
    },
    {
      lead: null,
      body: 'Android will remind you later that StopScroll can see your screen. That is expected. You can turn StopScroll off at any time in this app or in Android’s settings.',
    },
  ],
  agree: 'I agree',
  notNow: 'Not now',

  // Setup, after consent (spec 6.7 step 3). NEW.
  setupTitle: 'Turn on StopScroll in Android’s settings',
  setupBody: 'Tap the button, then Downloaded apps (on some phones Installed apps or Installed services), then StopScroll, and turn it on. The words vary by phone maker.',
  openSettings: 'Open Accessibility settings',
  agreedOn: (day: string): string => `You agreed on ${day}.`,

  // Restricted settings (spec 10.2), always reachable from the setup screen.
  restrictedLink: 'Did Android say “Restricted setting”?',
  restrictedIntro: 'Android limits Accessibility for apps installed from a downloaded file. To allow StopScroll:',
  restrictedSteps: [
    'Try to turn on StopScroll in Settings, Accessibility. Android says “Restricted setting”. (This attempt is needed: on several phones the option in step 3 appears only after it.)',
    'Open Settings, Apps, StopScroll.',
    'Tap the three-dot menu, then “Allow restricted settings”, and confirm with your PIN or fingerprint.',
    'Go back to Settings, Accessibility, and turn StopScroll on.',
  ],
  restrictedOutro: 'Wording varies by phone maker. The simplest way round it is to install StopScroll from Zapstore or Google Play.',
  openAppInfo: 'Open App info for StopScroll',

  // Advanced Protection (spec 10.4, AP-2 b), on Android 17 and later while the service is not running.
  advancedProtection: 'If Android’s Advanced Protection is on, it does not allow apps like StopScroll to use Accessibility, so StopScroll cannot work on this phone while it is on. This is Android’s choice, made to protect you, not a fault in StopScroll.',

  // Per-app settings (MODE-1: Off and Always block). NEW.
  appsTitle: 'Which feeds to close',
  modeOff: 'Off',
  modeAlways: 'Always block',
  appInstalled: (name: string, version: string | null): string => (version ? `${name} ${version} is installed.` : `${name} is installed.`),
  appNotInstalled: (name: string): string => `${name} is not installed on this phone, so there is nothing to close.`,
  // The coming-soon rows (0.0.1 kit, NEW). These apps are listed only: they are not in the
  // service's packageNames, so StopScroll receives nothing from them until a release adds them
  // (0.0.2 added Instagram; Facebook, Snapchat, LinkedIn and X remain).
  comingSoonLabel: 'Coming soon',
  comingSoonNote: 'StopScroll does not watch these apps yet. Each one comes in a later release, once its screens have been checked on a phone.',

  // Health (spec 6.8). "Last closed a feed" is the spec's; the rest NEW.
  neverClosed: 'No feed closed yet.',
  lastClosed: (day: string): string => `Last closed a feed: ${day}.`,
  counts: (closed: number, pauses: number): string =>
    `Feeds closed: ${closed}. Pauses after a loop: ${pauses}.`,
  // The report link, word for word as Marcos approved it on 29 October 2026 (the copy's one exclamation
  // mark; tests/copy.test.ts pins it). LinkList.email in Kotlin builds the email it opens.
  missedFeed: 'Did StopScroll miss a feed? Or did it block a normal video? Email us!',

  // Turning off (spec 10.3). NEW wording around the spec's button name.
  turnOffTitle: 'Turn StopScroll off',
  turnOffBody: 'StopScroll switches itself off in Android’s settings. You can turn it on again there at any time.',
  turnOff: 'Turn StopScroll off',

  // Teaching panel (spec 12.4), narrowed to this build. NEW where it differs; "two apps" is 0.0.2, approved 2026-10-30.
  teachTitle: 'Before you turn on Accessibility for any app',
  teachBody: 'Accessibility lets an app see and press things on your screen. Before you allow it, check four things: which apps it watches, what it can press, whether it has internet access, and whether you can read its code. StopScroll: two apps; only Back, Home and one tab; no internet; its code is open source (MIT).',

  // Privacy notes (spec 8 and 6.1). NEW.
  privacyTitle: 'Privacy',
  privacyBody: [
    'StopScroll has no internet permission, so it cannot send anything anywhere itself. It shows no ads and has no account.',
    'It keeps your settings, the date you agreed to the disclosure and a few counts on this phone only. Backups and phone-to-phone transfers leave them out.',
    'One thing Android does on its own: while an Accessibility service looks at a screen, Android’s own code inside the app can also receive notices of screen changes in other apps, for a cache. StopScroll turns that cache off straight after each check, so this lasts only a moment and never happens while StopScroll is off. StopScroll’s code never sees these notices.',
  ],
  privacyMore: 'More about privacy',

  // Footer (spec 12.2). footerPermissions "two apps" is 0.0.2, approved 2026-10-30 (index.html and privacy.html carry the same line).
  footerCredits: 'Runs entirely on your device: our own code and the open-source parts listed in NOTICE. Nothing is sent.',
  footerPermissions: 'Accessibility only, watching two apps. No internet. No ads. No tracking.',
  footerOffline: 'Your settings and feed counts stay on this phone, nowhere else. Disconnect and try it.',

  // Website, where the page runs without the app (stage 4 writes the real website). NEW.
  webTitle: 'This is the StopScroll app’s settings screen',
  webBody: 'StopScroll is an Android app. Its settings screen works only inside the app.',

  // Errors from the app. NEW.
  errorNoConsent: 'Please read and agree to the disclosure first.',
  errorGeneric: 'That did not work. Please try again.',
} as const;
