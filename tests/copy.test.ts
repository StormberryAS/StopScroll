// The house style of every word a user sees (stormberry-content-core): British English, plain
// words, no em or en dashes, no double hyphens, no exclamation marks (one approved exception, the
// report link); no other company's app named beyond the watched YouTube, Instagram (since 0.0.2) and
// X (since 0.0.3), and the three coming-soon apps, and those only where the coming-soon list is
// described.
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { text } from '#core/copy.js';
import { read } from './helpers.ts';

function strings(x: unknown): string[] {
  if (typeof x === 'string') return [x];
  if (typeof x === 'function') {
    const f = x as (...a: unknown[]) => unknown;
    return strings(f.length === 2 ? f('YouTube', '21.42') : f.length === 1 ? f('the YouTube or Instagram app') : f());
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
  const soon = /\b(?:Facebook|Snapchat|LinkedIn)\b/;
  const allowed = new Set<string>([text.introNext, text.comingSoonNote]);
  for (const [where, s] of all) {
    assert.doesNotMatch(s, /TikTok|Reels tab/, `${where}: ${s}`);
    if (where === 'copy.ts' && soon.test(s)) assert.ok(allowed.has(s), `copy.ts names a coming-soon app outside the coming-soon texts: ${s}`);
    if (where === 'strings.xml') assert.doesNotMatch(s, soon, `strings.xml: ${s}`);
  }
  assert.equal(text.introNext, 'Coming soon: Facebook, Snapchat and LinkedIn. StopScroll does not watch them yet.');
  // privacy.html names them once, in the paragraph that says StopScroll does not watch them yet.
  const priv = visibleHtml('privacy.html');
  assert.equal([...priv.matchAll(/\b(?:Facebook|Snapchat|LinkedIn)\b/g)].length, 3);
  assert.match(priv, /This version watches three apps: YouTube, Instagram and X\. Facebook, Snapchat and LinkedIn appear in StopScroll(?:&rsquo;|\u2019)s list as coming soon\. StopScroll does not watch them yet/);
});

test('X (watched since 0.0.3) is named where the watched apps are, and the Android description says three apps', () => {
  const service = androidStrings.find((s) => s.startsWith('Closes the short-video feeds'));
  assert.equal(service, 'Closes the short-video feeds in YouTube, Instagram and X. Watches only those three apps. No internet access.');
  assert.match(text.introBody[0] ?? '', /YouTube, Instagram and X apps/);
  assert.match(text.introBody[0] ?? '', /A Reel someone sends you in a message is closed too\./);
  assert.equal(text.introBody[1], 'In X, the feed is the full-screen video viewer, where you swipe up for the next video. X opens it when you tap a video in the timeline, a post, search or a profile, so StopScroll closes it there too. Videos still play in the timeline, in posts and in search.');
  assert.equal(text.introBody.length, 4);
  assert.match(text.teachBody, /StopScroll: three apps;/);
  for (const p of ['index.html', 'privacy.html']) assert.match(read(p), /Accessibility only, watching three apps\./);
  assert.equal(text.footerPermissions, 'Accessibility only, watching three apps. No internet. No ads. No tracking.');
  // The in-app privacy page names the Play edition neutrally (PARITY-3): nothing "planned".
  const priv = visibleHtml('privacy.html');
  assert.match(priv, /A separate edition for Google Play is built from the same source code, with the same lack of permissions\./);
  assert.doesNotMatch(priv, /planned/);
  assert.match(priv, /When a new version adds an app, StopScroll does not watch that app until you have read the updated disclosure and agreed\./);
});

test('the pending card for an added app (per-app consent, 0.0.3), word for word', () => {
  assert.equal(text.pendingTitle('X'), 'StopScroll now works with X too.');
  assert.equal(text.pendingBody('X'), 'StopScroll does not watch X until you read the updated disclosure and agree. The apps you agreed to before keep working as they are.');
  assert.equal(text.pendingButton, 'Read and agree');
  assert.equal(text.appWaiting('X'), 'Waiting for your agreement: StopScroll does not watch X yet.');
});

test('the title never names another company\'s product or its feature', () => {
  assert.doesNotMatch(read('index.html').match(/<title>([^<]*)<\/title>/)?.[1] ?? '', /Shorts|Reels|YouTube|Instagram|Facebook/);
});

test('the positioning: no internet, no ads, said plainly', () => {
  const joined = all.map(([, s]) => s).join(' ');
  assert.match(joined, /no internet permission/);
  assert.match(joined, /No ads/);
});
