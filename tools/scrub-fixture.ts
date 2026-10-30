/* Turns one raw uiautomator dump into a SANITISED test fixture (spec 14.2, step 6).
 *
 *   node tools/scrub-fixture.ts RAW.xml OUT.xml --app com.google.android.youtube \
 *        --label youtube-shorts-1 --expect block --captured 2026-10-29 \
 *        --device "Pixel, Android 17" --android 17
 *
 * A raw dump holds whatever was on screen: usernames, captions, messages. This tool keeps ONLY the
 * tree's structure and four attributes of each node: class, resource-id, bounds and selected. Every
 * text, content-desc and hint, and every other attribute (package, index, checkable, clickable and
 * the rest), is dropped. Each kept value must match a strict pattern (a Java class name, an Android
 * resource name, a bounds rectangle, true or false); if any value does not, the tool writes nothing
 * and stops, so a text that slipped into an odd place can never reach a fixture.
 *
 * It prints counts only, never a value from the raw file. Raw dumps stay outside the repository
 * (a private folder, mode 700) and are deleted after the fixtures are reviewed.
 * tests/fixtures.test.ts proves every committed fixture holds nothing but the allowlist.
 */
import { readFileSync, writeFileSync } from 'node:fs';

const KEEP = ['class', 'resource-id', 'bounds', 'selected'] as const;
const PATTERN: Record<(typeof KEEP)[number], RegExp> = {
  class: /^[A-Za-z0-9_.$]*$/,
  'resource-id': /^(?:[a-z][a-z0-9_.]*:id\/[A-Za-z0-9_]+)?$/,
  bounds: /^\[\d{1,5},\d{1,5}\]\[\d{1,5},\d{1,5}\]$/,
  selected: /^(?:true|false)$/,
};

function fail(message: string): never {
  console.error(`scrub-fixture: ${message}; nothing was written`);
  process.exit(1);
}

function option(args: string[], name: string, pattern: RegExp): string {
  const at = args.indexOf(name);
  const value = at >= 0 ? args[at + 1] : undefined;
  if (value === undefined || !pattern.test(value)) fail(`${name} is missing or not of the expected form`);
  return value;
}

const args = process.argv.slice(2);
const [rawPath, outPath] = args;
if (!rawPath || !outPath || rawPath.startsWith('--') || outPath.startsWith('--')) {
  fail('usage: scrub-fixture.ts RAW.xml OUT.xml --app PKG --label NAME --expect block|allow --captured YYYY-MM-DD --device TEXT --android N');
}
const app = option(args, '--app', /^[a-z][a-z0-9_]*(?:\.[a-z0-9_]+)+$/);
const label = option(args, '--label', /^[a-z0-9-]{1,48}$/);
const expect = option(args, '--expect', /^(?:block|allow)$/);
const captured = option(args, '--captured', /^\d{4}-\d{2}-\d{2}$/);
const device = option(args, '--device', /^[A-Za-z0-9 ,()]{1,64}$/);
const android = option(args, '--android', /^\d{1,2}$/);

const raw = readFileSync(rawPath, 'utf8');
const expectedNodes = raw.split('<node ').length - 1;
const TAG = /<(\/?)(node|hierarchy)\b((?:[^>"]|"[^"]*")*?)(\/?)>/g;
const ATTR = /\s([A-Za-z_:-]+)="([^"]*)"/g;

const lines: string[] = [];
let depth = 0;
let nodes = 0;
let rootPackageSeen = false;
for (const m of raw.matchAll(TAG)) {
  const [, closing, tag, body = '', selfClosing] = m;
  if (tag === 'hierarchy') continue;
  if (closing) {
    depth -= 1;
    if (depth < 0) fail('the tree closes more nodes than it opens');
    lines.push(`${'  '.repeat(depth + 1)}</node>`);
    continue;
  }
  const attrs = new Map<string, string>();
  for (const a of body.matchAll(ATTR)) attrs.set(a[1] ?? '', a[2] ?? '');
  if (!rootPackageSeen) {
    if (attrs.get('package') !== app) fail('the first node does not belong to the --app package');
    rootPackageSeen = true;
  }
  const kept = KEEP.map((k) => {
    const v = attrs.get(k);
    if (v === undefined) fail(`a node has no ${k} attribute`);
    if (!PATTERN[k].test(v)) fail(`a ${k} value is not of the expected form`);
    return `${k}="${v}"`;
  });
  nodes += 1;
  lines.push(`${'  '.repeat(depth + 1)}<node ${kept.join(' ')}${selfClosing ? ' /' : ''}>`);
  if (!selfClosing) depth += 1;
}
if (depth !== 0) fail('the tree is not closed');
if (nodes === 0 || nodes !== expectedNodes) fail(`parsed ${nodes} nodes but the file holds ${expectedNodes}`);

const header = [
  '<?xml version="1.0" encoding="UTF-8"?>',
  '<!-- StopScroll test fixture. SANITISED by tools/scrub-fixture.ts: every text, content-desc and hint',
  '     was removed, with every attribute except class, resource-id, bounds and selected. The tree',
  '     structure is kept as captured. The raw capture holds personal on-screen text and never enters',
  '     this repository. -->',
  `<fixture app="${app}" label="${label}" expect="${expect}" captured="${captured}" device="${device}" android="${android}" source="uiautomator">`,
];
writeFileSync(outPath, [...header, ...lines, '</fixture>', ''].join('\n'));
console.log(`scrub-fixture: ${label}: ${nodes} nodes kept, 4 attributes each`);
