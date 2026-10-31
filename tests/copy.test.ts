// The house style of every word a user sees (stormberry-content-core): British English, plain
// words, no em or en dashes, no double hyphens, no exclamation marks (one approved exception, the
// report link); no other company's app named beyond the watched YouTube, Instagram (since 0.0.2), X
// (since 0.0.3) and YouTube Music (since 0.0.4), and the two coming-soon apps, and those only where
// the coming-soon list is described. Since 0.0.4 no text names LinkedIn (LINKEDIN-1).
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { text } from '#core/copy.js';
import { read } from './helpers.ts';

function strings(x: unknown): string[] {
  if (typeof x === 'string') return [x];
  if (typeof x === 'function') {
    const f = x as (...a: unknown[]) => unknown;
    return strings(f.length === 2 ? f('YouTube', '21.42') : f.length === 1 ? f('the YouTube, Instagram, X or YouTube Music app') : f());
  }
  if (Array.isArray(x)) return x.flatMap(strings);
  if (typeof x === 'object' && x !== null) return Object.values(x).flatMap(strings);
  return [];
}

const visibleHtml = (p: string): string =>
  read(p).replace(/<!--[\s\S]*?-->/g, '').replace(/<svg[\s\S]*?<\/svg>/g, '').replace(/<head>[\s\S]*?<\/head>/, '').replace(/<[^>]+>/g, ' ');
const androidStrings = [...read('android/app/src/main/res/values/strings.xml').replace(/<!--[\s\S]*?-->/g, '').matchAll(/<string name="[^"]+">([^<]*)<\/string>/g)].map((m) => m[1] ?? '');

const all: [string, string][] = [
  ...strings(text).map((s): [string, string] => ['copy.ts', s]),
  ...androidStrings.map((s): [string, string] => ['strings.xml', s]),
  ['index.html', visibleHtml('index.html')],
  ['privacy.html', visibleHtml('privacy.html')],
];

test('there is copy to check', () => {
  assert.ok(all.length > 60);
});

// The one exclamation mark: the report link, word for word as Marcos approved it (2026-10-29).
const REPORT_LINK = 'Did StopScroll miss a feed? Or did it block a normal video? Email us!';

test('no em or en dashes, no double hyphens, no exclamation marks but the approved report link', () => {
  assert.equal(text.missedFeed, REPORT_LINK);
  for (const [where, s] of all) {
    assert.doesNotMatch(s, /[–—]/, `${where}: ${s}`);
    assert.doesNotMatch(s, /--/, `${where}: ${s}`);
    if (where === 'copy.ts' && s === REPORT_LINK) continue;
    assert.doesNotMatch(s, /!/, `${where}: ${s}`);
  }
});

test('British spelling', () => {
  const american = /\b(colou?r(?<!colour)|behavior|favorite|center|organiz\w*|recogniz\w*|realiz\w*|customiz\w*|optimiz\w*|analyz\w*|gray|catalog\b|license\b|defense|program\b)/i;
  for (const [where, s] of all) assert.doesNotMatch(s, american, `${where}: ${s}`);
});

test('no draft marks, placeholders or abbreviations of the name reach the screen', () => {
  for (const [where, s] of all) {
    assert.doesNotMatch(s, /DRAFT|TODO|lorem|\bSS\b|undefined|NaN/, `${where}: ${s}`);
  }
});

test('the coming-soon apps are named only where the coming-soon list is described; TikTok nowhere', () => {
  const soon = /\b(?:Facebook|Snapchat)\b/;
  const allowed = new Set<string>([text.introNext, text.comingSoonNote]);
  for (const [where, s] of all) {
    assert.doesNotMatch(s, /TikTok|Reels tab/, `${where}: ${s}`);
    if (where === 'copy.ts' && soon.test(s)) assert.ok(allowed.has(s), `copy.ts names a coming-soon app outside the coming-soon texts: ${s}`);
    if (where === 'strings.xml') assert.doesNotMatch(s, soon, `strings.xml: ${s}`);
  }
  assert.equal(text.introNext, 'Coming soon: Facebook and Snapchat. StopScroll does not watch them yet.');
  // privacy.html names them once, in the paragraph that says StopScroll does not watch them yet.
  const priv = visibleHtml('privacy.html');
  assert.equal([...priv.matchAll(/\b(?:Facebook|Snapchat)\b/g)].length, 2);
  assert.match(priv, /This version watches four apps: YouTube, Instagram, X and YouTube Music\. Facebook and Snapchat appear in StopScroll(?:&rsquo;|\u2019)s list as coming soon\. StopScroll does not watch them yet/);
});

// LINKEDIN-1 (Marcos, 2026-10-31): LinkedIn is out of StopScroll's plan, so from 0.0.4 no text the user
// or a store reads names it as an app. Stormberry's own company link in the footer (data-link, the
// LinkList) is a link to our page, not an app, and stays. Case matters: the decision's ID, LINKEDIN-1,
// may be cited in comments.
test('LinkedIn is named in no text as an app (LINKEDIN-1)', () => {
  for (const [where, s] of all) {
    const words = where === 'copy.ts' || where === 'strings.xml' ? s : s.replace(/aria-label="LinkedIn"/g, '');
    assert.doesNotMatch(words, /LinkedIn/, `${where}: ${s}`);
  }
  const visible = (p: string): string => read(p).replace(/<!--[\s\S]*?-->/g, '').replace(/<a href="https:\/\/linkedin\.com\/company\/StormberryAS"[^>]*>/g, '');
  for (const p of ['index.html', 'privacy.html']) assert.doesNotMatch(visible(p), /LinkedIn/, p);
  for (const p of ['README.md', 'DISCLAIMER.md', 'disclaimer.html', 'zapstore.yaml', 'fastlane/metadata/android/en-US/full_description.txt', 'fastlane/metadata/android/en-US/changelogs/4.txt', 'ts/core/flow.ts']) {
    assert.doesNotMatch(read(p), /LinkedIn/, p);
  }
  const changelog = read('CHANGELOG.md');
  assert.doesNotMatch(changelog.slice(0, changelog.indexOf('## 0.0.3')), /LinkedIn/, 'the 0.0.4 section');
});

test('YouTube Music (watched since 0.0.4) is named where the watched apps are, and the Android description says four apps', () => {
  const service = androidStrings.find((s) => s.startsWith('Closes the short-video feeds'));
  assert.equal(service, 'Closes the short-video feeds in YouTube, Instagram, X and YouTube Music. Watches only those four apps. No internet access.');
  assert.match(text.introBody[0] ?? '', /YouTube, Instagram, X and YouTube Music apps/);
  assert.match(text.introBody[0] ?? '', /A Reel someone sends you in a message is closed too\./);
  assert.equal(text.introBody[1], 'In X, the feed is the full-screen video viewer, where you swipe up for the next video. X opens it when you tap a video in the timeline, a post, search or a profile, so StopScroll closes it there too. Videos still play in the timeline, in posts and in search.');
  assert.equal(text.introBody[2], 'In YouTube Music, the feed is Samples, the short video clips of songs where you swipe up for the next one. StopScroll closes it on the Samples tab, from the Samples button on an album or artist page, and from a link. It leaves Samples by pressing the app\u2019s Home tab, or Back when the Home tab is already selected, so you stay in YouTube Music and the music you were playing can carry on. Songs, albums, playlists, music videos and the player keep working.');
  assert.equal(text.introBody.length, 5);
  assert.match(text.introBody[3] ?? '', /^No ads, no account, no server calls\./);
  assert.match(text.teachBody, /StopScroll: four apps;/);
  for (const p of ['index.html', 'privacy.html']) assert.match(read(p), /Accessibility only, watching four apps\./);
  assert.equal(text.footerPermissions, 'Accessibility only, watching four apps. No internet. No ads. No tracking.');
  const privYtm = visibleHtml('privacy.html');
  assert.match(privYtm, /only while you are in the YouTube, Instagram, X or YouTube Music app\./);
  assert.match(privYtm, /Songs, albums, playlists, music videos, the player, its queue and its lyrics stay open, and so does the full player when you open it from a Sample\./);
  assert.match(privYtm, /In YouTube Music the app(?:&rsquo;|\u2019)s Home tab comes first, because on the Samples tab Back would close YouTube Music and leave your music paused \(when the Home tab is already selected, Back comes first\); Back, the tab again and Home follow only if needed\. Nothing else\./);
  assert.match(privYtm, /StopScroll closes it on the Samples tab, from the Samples button on an album or artist page, and from a link\./);
  // The in-app privacy page names the Play edition neutrally (PARITY-3): nothing "planned".
  const priv = visibleHtml('privacy.html');
  assert.match(priv, /A separate edition for Google Play is built from the same source code, with the same lack of permissions\./);
  assert.doesNotMatch(priv, /planned/);
  assert.match(priv, /When a new version adds an app, StopScroll does not watch that app until you have read the updated disclosure and agreed\./);
});

// CONSENT-4 (0.0.4): DISCLOSURE_VERSION stays 2 because what the disclosure says about the apps agreed
// to before did not change. These are the version 2 sentences, word for word; changing any of them
// changes what a stored agreement covers, and then DISCLOSURE_VERSION must be bumped.
test('the version 2 disclosure sentences are kept word for word; YouTube Music adds one sentence of its own', () => {
  const does = text.disclosure('the YouTube, Instagram, X or YouTube Music app').find((p) => p.lead === 'What it does.')?.body ?? '';
  assert.ok(does.startsWith('When a feed you chose to block opens, StopScroll presses Back for you, twice if needed. If that does not leave the feed, it presses one tab in the same app where there is one, then Home. '));
  assert.ok(does.endsWith(' It presses nothing else.'));
  assert.match(does, /In YouTube Music it presses the app\u2019s Home tab first, because on the Samples tab Back would close YouTube Music and leave your music paused \(when the Home tab is already selected, it presses Back first\); Back, the tab again and Home follow only if needed\./);
  const looks = text.disclosure('the YouTube app').find((p) => p.lead === 'What it looks at.')?.body ?? '';
  assert.match(looks, /the Shorts or Reels player, X\u2019s full-screen video viewer or YouTube Music\u2019s Samples\./);
  assert.match(looks, /It does not read the words on your screen: not your messages, captions, passwords or anything you type\.$/);
});

test('the pending card for an added app (per-app consent, 0.0.3), word for word', () => {
  assert.equal(text.pendingTitle('YouTube Music'), 'StopScroll now works with YouTube Music too.');
  assert.equal(text.pendingBody('YouTube Music'), 'StopScroll does not watch YouTube Music until you read the updated disclosure and agree. The apps you agreed to before keep working as they are.');
  assert.equal(text.appWaiting('YouTube Music'), 'Waiting for your agreement: StopScroll does not watch YouTube Music yet.');
  assert.equal(text.pendingTitle('X'), 'StopScroll now works with X too.');
  assert.equal(text.pendingBody('X'), 'StopScroll does not watch X until you read the updated disclosure and agree. The apps you agreed to before keep working as they are.');
  assert.equal(text.pendingButton, 'Read and agree');
  assert.equal(text.appWaiting('X'), 'Waiting for your agreement: StopScroll does not watch X yet.');
});

test('the title never names another company\'s product or its feature', () => {
  for (const p of ['index.html', 'privacy.html']) {
    assert.doesNotMatch(read(p).match(/<title>([^<]*)<\/title>/)?.[1] ?? '', /Shorts|Reels|Samples|Music|YouTube|Instagram|Facebook|Snapchat|\bX\b/, p);
  }
  assert.equal(read('fastlane/metadata/android/en-US/title.txt').trim(), 'StopScroll');
});

test('the positioning: no internet, no ads, said plainly', () => {
  const joined = all.map(([, s]) => s).join(' ');
  assert.match(joined, /no internet permission/);
  assert.match(joined, /No ads/);
});
