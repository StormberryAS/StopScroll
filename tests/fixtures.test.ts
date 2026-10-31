// Every committed fixture is SANITISED: structure plus class, resource-id, bounds and selected, and
// nothing else. No text, no content-desc, no hint, no package, no other attribute. A resource-id is a
// view ID with its package, empty, or (since 0.0.3, X) a bare screen tag on the app's reviewed list
// in tools/scrub-fixture.ts (decision FX-2), so a tag built from data can never be committed. YouTube
// Music (0.0.4) has no reviewed tag: its fixtures hold view IDs only.
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { read, walk } from './helpers.ts';

const fixtures = walk('tests/fixtures').filter((p) => p.endsWith('.xml'));
const ALLOWED = new Set(['class', 'resource-id', 'bounds', 'selected']);
const FIXTURE_ATTRS = new Set(['app', 'label', 'expect', 'captured', 'device', 'android', 'source']);
const FOLDERS: Record<string, string> = {
  youtube: 'com.google.android.youtube',
  instagram: 'com.instagram.android',
  x: 'com.twitter.android',
  ytmusic: 'com.google.android.apps.youtube.music',
};

/** The scrubber's reviewed screen tags per app, read from tools/scrub-fixture.ts (one line per app). */
function reviewedTags(): Map<string, Set<string>> {
  const src = read('tools/scrub-fixture.ts');
  const block = /const REVIEWED_TAGS: Record<string, readonly string\[\]> = \{\n([\s\S]*?)\n\};/.exec(src)?.[1] ?? '';
  const out = new Map<string, Set<string>>();
  for (const m of block.matchAll(/^ {2}'([a-z][a-z0-9_.]+)': \[([^\]]*)\],$/gm)) {
    out.set(m[1] ?? '', new Set([...(m[2] ?? '').matchAll(/'([^']+)'/g)].map((t) => t[1] ?? '')));
  }
  return out;
}
const REVIEWED = reviewedTags();

test('the scrubber\'s reviewed tag list covers each watched app, and only X has tags', () => {
  assert.deepEqual([...REVIEWED.keys()], Object.values(FOLDERS));
  assert.equal(REVIEWED.get('com.google.android.youtube')?.size, 0);
  assert.equal(REVIEWED.get('com.instagram.android')?.size, 0);
  assert.equal(REVIEWED.get('com.google.android.apps.youtube.music')?.size, 0);
  const x = REVIEWED.get('com.twitter.android') ?? new Set();
  assert.equal(x.size, 14);
  for (const tag of ['MainLanding', 'VideoTab', 'PostDetail', 'MediaGallery', 'Search', 'UserProfileScreen']) assert.ok(x.has(tag), tag);
});

test('there are fixtures: YouTube (4 Shorts, 4 other), Instagram (7 Reels, 11 other), X (8 viewers, 14 other) and YouTube Music (15 Samples, 26 other)', () => {
  assert.equal(fixtures.length, 89);
  const count = (dir: string, expect: string): number =>
    fixtures.filter((p) => p.startsWith(`tests/fixtures/${dir}/`) && new RegExp(`expect="${expect}"`).test(read(p))).length;
  assert.equal(count('youtube', 'block'), 4);
  assert.equal(count('youtube', 'allow'), 4);
  assert.equal(count('instagram', 'block'), 7);
  assert.equal(count('instagram', 'allow'), 11);
  assert.equal(count('x', 'block'), 8);
  assert.equal(count('x', 'allow'), 14);
  assert.equal(count('ytmusic', 'block'), 15);
  assert.equal(count('ytmusic', 'allow'), 26);
  assert.equal(fixtures.filter((p) => /expect="block"/.test(read(p))).length, 34);
  assert.equal(fixtures.filter((p) => /expect="allow"/.test(read(p))).length, 55);
  // A YouTube Music fixture holds no bare tag at all.
  for (const p of fixtures.filter((f) => f.startsWith('tests/fixtures/ytmusic/'))) {
    for (const m of read(p).matchAll(/resource-id="([^"]*)"/g)) assert.ok(m[1] === '' || (m[1] ?? '').startsWith('com.google.android.apps.youtube.music:id/') || (m[1] ?? '').startsWith('android:id/'), p);
  }
  // Each fixture sits in the folder of its app, so the Kotlin tests read it with that app's rules.
  for (const p of fixtures) {
    const app = /<fixture app="([^"]+)"/.exec(read(p))?.[1];
    const folder = p.split('/')[2] ?? '';
    assert.ok(folder in FOLDERS, `${p}: unknown folder`);
    assert.equal(app, FOLDERS[folder], p);
  }
});

for (const path of fixtures) {
  test(`${path} holds nothing but the allowlist`, () => {
    const xml = read(path);
    const app = /<fixture app="([^"]+)"/.exec(xml)?.[1] ?? '';
    const reviewed = REVIEWED.get(app) ?? new Set<string>();
    assert.match(xml, /SANITISED by tools\/scrub-fixture\.ts: every text, content-desc and hint\s+was removed/, 'the header says so');
    const body = xml.replace(/<!--[\s\S]*?-->/g, '').replace(/^<\?xml[^>]*\?>/, '');
    const tags = [...body.matchAll(/<\/?([A-Za-z]+)([^>]*)>/g)];
    assert.ok(tags.length > 50);
    for (const [, tag, attrs] of tags) {
      assert.ok(tag === 'node' || tag === 'fixture', `unexpected element ${tag}`);
      const names = [...(attrs ?? '').matchAll(/\s([A-Za-z:-]+)=/g)].map((m) => m[1]);
      for (const n of names) assert.ok((tag === 'node' ? ALLOWED : FIXTURE_ATTRS).has(n ?? ''), `attribute ${n} on ${tag}`);
    }
    // Between the tags there is only white space: no text node anywhere.
    assert.equal(body.replace(/<[^>]+>/g, '').trim(), '');
    for (const m of body.matchAll(/class="([^"]*)"/g)) assert.match(m[1] ?? '', /^[A-Za-z0-9_.$]*$/);
    for (const m of body.matchAll(/resource-id="([^"]*)"/g)) {
      const rid = m[1] ?? '';
      assert.match(rid, /^(?:[a-z][a-z0-9_.]*:id\/[A-Za-z0-9_]+|[A-Za-z][A-Za-z0-9_-]{0,79})?$/);
      if (rid !== '' && !rid.includes(':')) assert.ok(reviewed.has(rid), `a screen tag not on the reviewed list of ${app}`);
    }
    for (const m of body.matchAll(/bounds="([^"]*)"/g)) assert.match(m[1] ?? '', /^\[\d+,\d+\]\[\d+,\d+\]$/);
  });
}
