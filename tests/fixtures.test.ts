// Every committed fixture is SANITISED: structure plus class, resource-id, bounds and selected, and
// nothing else. No text, no content-desc, no hint, no package, no other attribute.
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { read, walk } from './helpers.ts';

const fixtures = walk('tests/fixtures').filter((p) => p.endsWith('.xml'));
const ALLOWED = new Set(['class', 'resource-id', 'bounds', 'selected']);
const FIXTURE_ATTRS = new Set(['app', 'label', 'expect', 'captured', 'device', 'android', 'source']);

test('there are fixtures: four Shorts, two normal videos, Home and a transition', () => {
  assert.equal(fixtures.length, 8);
  const expects = fixtures.map((p) => /expect="(\w+)"/.exec(read(p))?.[1]);
  assert.equal(expects.filter((e) => e === 'block').length, 4);
  assert.equal(expects.filter((e) => e === 'allow').length, 4);
});

for (const path of fixtures) {
  test(`${path} holds nothing but the allowlist`, () => {
    const xml = read(path);
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
    for (const m of body.matchAll(/resource-id="([^"]*)"/g)) assert.match(m[1] ?? '', /^(?:[a-z][a-z0-9_.]*:id\/[A-Za-z0-9_]+)?$/);
    for (const m of body.matchAll(/bounds="([^"]*)"/g)) assert.match(m[1] ?? '', /^\[\d+,\d+\]\[\d+,\d+\]$/);
  });
}
