// The house style of every word a user sees (stormberry-content-core): British English, plain
// words, no em or en dashes, no double hyphens, no exclamation marks (one approved exception, the
// report link); no other company's app named beyond YouTube (watched) and the five coming-soon
// apps, and those only where the coming-soon list is described (0.0.1).
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { text } from '#core/copy.js';
import { read } from './helpers.ts';

function strings(x: unknown): string[] {
  if (typeof x === 'string') return [x];
  if (typeof x === 'function') {
    const f = x as (...a: unknown[]) => unknown;
    return strings(f.length === 2 ? f('YouTube', '21.42') : f.length === 1 ? f('the YouTube app') : f());
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
  const soon = /\b(?:Instagram|Facebook|Snapchat|LinkedIn)\b|\bX\b/;
  const allowed = new Set<string>([text.introNext, text.comingSoonNote]);
  for (const [where, s] of all) {
    assert.doesNotMatch(s, /TikTok|Reels tab/, `${where}: ${s}`);
    if (where === 'copy.ts' && soon.test(s)) assert.ok(allowed.has(s), `copy.ts names a coming-soon app outside the coming-soon texts: ${s}`);
    if (where === 'strings.xml') assert.doesNotMatch(s, soon, `strings.xml: ${s}`);
  }
  // privacy.html names them once, in the paragraph that says StopScroll does not watch them yet.
  const priv = visibleHtml('privacy.html');
  const hits = [...priv.matchAll(/\b(?:Instagram|Facebook|Snapchat)\b/g)].length;
  assert.equal(hits, 3);
  assert.match(priv, /Instagram, Facebook, Snapchat, LinkedIn and X appear in StopScroll(?:&rsquo;|\u2019)s list as coming soon\. StopScroll does not watch them yet/);
});

test('the title never names another company\'s product or its feature', () => {
  assert.doesNotMatch(read('index.html').match(/<title>([^<]*)<\/title>/)?.[1] ?? '', /Shorts|Reels|YouTube|Instagram|Facebook/);
});

test('the positioning: no internet, no ads, said plainly', () => {
  const joined = all.map(([, s]) => s).join(' ');
  assert.match(joined, /no internet permission/);
  assert.match(joined, /No ads/);
});
