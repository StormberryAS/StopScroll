// The release files agree with each other and with the build (0.0.1 release kit, 29 October 2026):
// the workflow's file list and the build's include list, the pinned certificate, the version and its
// release note, the store texts and images, and the watched app list.
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';
import { existsSync, readFileSync, statSync } from 'node:fs';
import { join } from 'node:path';
import { read, ROOT } from './helpers.ts';

const RELEASE_CERT = '1e4aa9ee4d927009dbe8ba17cfd05f496213e6001adf79afdb2d5b90dbc5591a';
const UPLOAD_CERT = '08f36e5e3accd91a64d7e945c477f24124ea7eecc573925fca0c59462995e2fd';
const FASTLANE = 'fastlane/metadata/android/en-US';
const DRAFT = /^DRAFT, not approved: .*\n/;

const gradle = read('android/app/build.gradle.kts');
const workflow = read('.github/workflows/android-release.yml');
const zapstore = read('zapstore.yaml');
const versionCode = Number(/^\s*versionCode = (\d+)$/m.exec(gradle)?.[1]);
const versionName = /^\s*versionName = "([^"]+)"$/m.exec(gradle)?.[1];

/** A store text without its DRAFT line (the line goes when Marcos approves the text). */
const store = (f: string): string => read(`${FASTLANE}/${f}`).replace(DRAFT, '').replace(/\n$/, '');

test('version 0.0.4, versionCode 4, below the Play offset; Play is 1000 plus it', () => {
  assert.equal(versionName, '0.0.4');
  assert.equal(versionCode, 4);
  assert.ok(versionCode < 1000);
  assert.match(gradle, /val PLAY_VERSION_CODE_OFFSET = 1000\n/);
  assert.match(gradle, /applicationId = if \(playBuild\) "no\.stormberry\.stopscroll\.play" else "no\.stormberry\.stopscroll"/);
  assert.match(read('README.md'), /\| `android-v0\.0\.1` \| 1 \| 0\.0\.1 \| 1001 \|/);
  assert.match(read('README.md'), /\| `android-v0\.0\.2` \| 2 \| 0\.0\.2 \| 1002 \(built, never uploaded\) \|/);
  assert.match(read('README.md'), /\| `android-v0\.0\.3` \| 3 \| 0\.0\.3 \| 1003 \(never uploaded\) \|/);
  assert.match(read('README.md'), /\| `android-v0\.0\.4` \| 4 \| 0\.0\.4 \| 1004 \|/);
  for (const v of ['sha256sum -c stopscroll-v0.0.4.apk.sha256', 'git checkout android-v0.0.4', 'gh attestation verify stopscroll-v0.0.4.apk']) assert.ok(read('README.md').includes(v), v);
  assert.match(workflow, /gh attestation verify stopscroll-v0\.0\.4\.apk/);
});

test('the workflow bundles exactly the build\'s include list, plus the bridge script', () => {
  const includes = /val INCLUDES = listOf\(([\s\S]*?)\n\s*\)/.exec(gradle)?.[1] ?? '';
  const fromGradle = [...includes.matchAll(/"([^"]+)"/g)].map((m) => m[1]).sort();
  const loop = /EXPECTED_WEB=\$\(cd \.\. && for f in ([\s\S]*?); do/.exec(workflow)?.[1] ?? '';
  const fromWorkflow = loop.replace(/\\\n/g, ' ').split(/\s+/).filter((x) => x.length > 0).sort();
  assert.ok(fromGradle.length >= 10);
  assert.deepEqual(fromWorkflow, fromGradle);
  assert.match(workflow, /done; echo android-bridge\.js\)/);
});

test('the release certificate is pinned in the workflow and named in the README; the upload key never is the release key', () => {
  assert.match(workflow, new RegExp(`EXPECTED_CERT=${RELEASE_CERT}\\n`));
  assert.match(read('README.md'), new RegExp(`\\*\\*Signing certificate SHA-256:\\*\\* \`${RELEASE_CERT}\``));
  assert.match(read('README.md'), new RegExp(UPLOAD_CERT));
  assert.notEqual(RELEASE_CERT, UPLOAD_CERT);
  assert.doesNotMatch(workflow, new RegExp(UPLOAD_CERT));
});

test('the workflow: android-v* tags only, the release environment, unsigned build, one signer, attestation, .sha256', () => {
  assert.match(workflow, /^on:\n {2}push:\n {4}tags: \["android-v\*"\]\n/m);
  assert.doesNotMatch(workflow, /workflow_dispatch|pull_request|branches:/);
  assert.match(workflow, /environment: release/);
  assert.match(workflow, /-PunsignedRelease=true :app:assembleRelease/);
  assert.match(workflow, /Number of signers: 1/);
  assert.match(workflow, /actions\/attest-build-provenance@[0-9a-f]{40}/);
  assert.match(workflow, /stopscroll-\$\{VERSION\}\.apk\.sha256/);
  for (const m of workflow.matchAll(/uses: ([^\s]+)/g)) assert.match(m[1] ?? '', /@[0-9a-f]{40}$/, `${m[1]} is pinned to a commit`);
  assert.match(workflow, /tools\/build-ts\.sh --check/);
  assert.match(workflow, /node --test tests\/\*\.test\.ts/);
  assert.match(workflow, /--notes-file "\.\.\/fastlane\/metadata\/android\/en-US\/changelogs\/\$\{VERSION_CODE\}\.txt"/);
});

test('zapstore.yaml: the repository, the Stormberry key, the release note of this versionCode, the pattern, the images', () => {
  assert.match(zapstore, /^repository: https:\/\/github\.com\/StormberryAS\/StopScroll$/m);
  assert.match(zapstore, /^pubkey: npub1zz9w77p2jkn95t0st9tcuea8zdn2vknf0ctrwyd4g46w3kswdccqesr0pa$/m);
  assert.match(zapstore, new RegExp(`^release_notes: \\./${FASTLANE}/changelogs/${versionCode}\\.txt$`, 'm'));
  assert.match(zapstore, /^match: "stopscroll-\.\*\\\\\.apk\$"$/m);
  assert.match(zapstore, /^website: https:\/\/stormberry\.as\/labs$/m);
  assert.match(zapstore, /^license: MIT$/m);
  const images = [...zapstore.matchAll(/^ {2}- \.\/(fastlane\/[^\n]+\.png)$/gm)].map((m) => m[1] ?? '');
  assert.ok(images.length >= 3);
  for (const img of [...images, `${FASTLANE}/images/icon.png`]) {
    assert.ok(existsSync(join(ROOT, img)), `${img} exists`);
    assert.ok(statSync(join(ROOT, img)).size < 250_000, `${img} is under 250 KB (Blossom refuses about 700 KB)`);
  }
});

test('store texts: within the limits, no dashes, the title is the name alone', () => {
  assert.equal(store('title.txt'), 'StopScroll');
  assert.ok(store('short_description.txt').length <= 80);
  assert.ok(store('full_description.txt').length <= 4000);
  const note = store(`changelogs/${versionCode}.txt`);
  assert.ok(note.length <= 500, `the release note is ${note.length} characters`);
  assert.match(note, /https:\/\/github\.com\/StormberryAS\/StopScroll\/blob\/main\/CHANGELOG\.md$/);
  const summary = /^summary: "([^"]+)"$/m.exec(zapstore)?.[1] ?? '';
  assert.equal(summary, store('short_description.txt'));
  const all = [store('title.txt'), store('short_description.txt'), store('full_description.txt'), note, zapstore.replace(/^#.*$/gm, '')].join('\n');
  assert.doesNotMatch(all, /[–—]/);
  assert.doesNotMatch(all, /(?<![\w-])--(?![\w-])/);
  assert.doesNotMatch(all, /\b(?:safe|safer|verified|guaranteed)\b/i);
  // What the stores say about permissions and apps must match the build.
  assert.match(all, /no internet permission/i);
  assert.match(all, /Not affiliated with or endorsed by Google, YouTube, Meta, Snap or X\./);
  assert.doesNotMatch(all, /LinkedIn/, 'LINKEDIN-1');
  assert.match(all, /functioning prototype/);
  assert.doesNotMatch(all, /reproducible/i, 'not claimed before a release matched');
});

const WATCHED = ['com.google.android.youtube', 'com.instagram.android', 'com.twitter.android', 'com.google.android.apps.youtube.music'];

test('the watched list is exactly YouTube, Instagram, X and YouTube Music, everywhere: service, queries, rules and the release check', () => {
  const svc = read('android/app/src/main/res/xml/accessibility_service_config.xml').replace(/<!--[\s\S]*?-->/g, '');
  const manifest = read('android/app/src/main/AndroidManifest.xml').replace(/<!--[\s\S]*?-->/g, '');
  const rules = JSON.parse(read('rules/screen-rules.json')) as { apps: { package: string }[] };
  assert.deepEqual(/android:packageNames="([^"]+)"/.exec(svc)?.[1]?.split(','), WATCHED);
  assert.deepEqual([...manifest.matchAll(/<package android:name="([^"]+)"/g)].map((m) => m[1]), WATCHED);
  assert.deepEqual(rules.apps.map((a) => a.package), WATCHED);
  assert.match(workflow, new RegExp(`\\n {10}PINNED_APPS=${WATCHED.join(',').replace(/\./g, '\\.')}\\n`));
  assert.match(workflow, /\[ "\$WANT" = "\$PINNED_APPS" \] \|\| \{ echo "::error::the rules cover/);
  assert.match(workflow, /\[ -n "\$GOT" \] && \[ "\$GOT" = "\$WANT" \] \|\|/);
});

test('the coming-soon apps are names on the screen only: never watched, never queried, never in the rules', () => {
  const svc = read('android/app/src/main/res/xml/accessibility_service_config.xml');
  const manifest = read('android/app/src/main/AndroidManifest.xml').replace(/<!--[\s\S]*?-->/g, '');
  const rules = read('rules/screen-rules.json');
  for (const pkg of ['com.facebook.katana', 'com.snapchat.android', 'com.linkedin.android']) {
    assert.ok(!svc.includes(pkg) && !manifest.includes(pkg) && !rules.includes(pkg), `${pkg} is not watched`);
  }
});

// PARITY-3 (0.0.3): one store description for Zapstore and Google Play. zapstore.yaml's description is
// fastlane's full description wrapped at 78 columns, word for word; the Play kit copies fastlane's file.
test('zapstore.yaml\'s description is the fastlane full description, word for word', () => {
  const block = /^description: \|\n([\s\S]*?)\n(?=[a-z_]+:)/m.exec(zapstore)?.[1] ?? '';
  const lines = block.split('\n').map((l) => l.replace(/^ {2}/, ''));
  const out: string[] = [];
  let blank = true;
  for (const l of lines) {
    if (l.trim() === '') {
      if (!blank) out.push('');
      blank = true;
    } else if (blank || l.startsWith('- ')) {
      out.push(l);
      blank = false;
    } else {
      out[out.length - 1] += ` ${l.trimStart()}`;
    }
  }
  while (out[out.length - 1] === '') out.pop();
  assert.ok(out.length > 10);
  assert.equal(out.join('\n'), store('full_description.txt'));
  for (const l of block.split('\n')) assert.ok(l.length <= 78, `a description line is ${l.length} columns`);
  // Long-lived store texts name no version (the release note does).
  assert.doesNotMatch(store('full_description.txt'), /\b0\.0\.\d/);
});

test('the release note of 0.0.3 names X and the per-app agreement, under 500 characters', () => {
  const note = store('changelogs/3.txt');
  assert.equal(note.length, 494);
  assert.match(note, /^StopScroll 0\.0\.3 adds X\./);
  assert.match(note, /X waits until you open StopScroll and agree; YouTube and Instagram keep working as you set them\./);
});

test('the release note of 0.0.4 names YouTube Music, how it leaves Samples and the per-app agreement, under 500 characters', () => {
  const note = store('changelogs/4.txt');
  assert.equal(note.length, 484);
  assert.match(note, /^StopScroll 0\.0\.4 adds YouTube Music\./);
  assert.match(note, /It leaves Samples inside the app, so your music can carry on\./);
  assert.match(note, /YouTube Music waits until you open StopScroll and agree; the apps you agreed to before keep working\./);
  assert.match(note, /Facebook and Snapchat stay coming soon\./);
  assert.equal(store('full_description.txt').length, 3898);
  // The Home tab is pressed only while it is not the selected tab (TabPick "first"): the texts say so.
  assert.match(store('full_description.txt'), /It leaves Samples by pressing the app's Home tab, or Back when the Home tab is already selected, so you stay in YouTube Music/);
  assert.match(store('full_description.txt'), /In YouTube Music the app's Home tab comes first \(or Back, when the Home tab is already selected\), then Back, the tab again and Home only if needed\./);
  assert.match(store('full_description.txt'), /This version works with YouTube, Instagram, X and YouTube Music\./);
  assert.match(read('CHANGELOG.md'), /^## 0\.0\.4 \(versionCode 4\)\n\nStopScroll now closes YouTube Music's Samples too/m);
  assert.ok(read('CHANGELOG.md').indexOf('## 0.0.4') < read('CHANGELOG.md').indexOf('## 0.0.3'), 'newest first');
});

// SCHEMA-3 (0.0.4): the rules file is schema 3, and the one surface that leaves with its tab first is
// YouTube Music's Samples, which has a tab to press.
test('the rules: schema 3, rulesVersion of the day, YouTube Music leaves with its tab first and the others as before', () => {
  type Surface = { id: string; otherTab?: { parentId: string; child: string }; leave?: string };
  const rules = JSON.parse(read('rules/screen-rules.json')) as { schema: number; rulesVersion: string; date: string; apps: { id: string; package: string; name: string; feedName: string; surfaces: Surface[] }[] };
  assert.equal(rules.schema, 3);
  assert.match(rules.rulesVersion, /^\d{4}-\d{2}-\d{2}\.\d+$/);
  assert.ok(rules.rulesVersion.startsWith(`${rules.date}.`));
  const ytm = rules.apps.find((a) => a.id === 'ytmusic');
  assert.equal(ytm?.name, 'YouTube Music');
  assert.equal(ytm?.feedName, 'YouTube Music Samples');
  assert.deepEqual(ytm?.surfaces.map((s) => [s.id, s.leave, s.otherTab !== undefined]), [['samples', 'otherTabFirst', true]]);
  for (const a of rules.apps.filter((x) => x.id !== 'ytmusic')) for (const s of a.surfaces) assert.equal(s.leave, undefined, `${a.id}: Back first, as before`);
  // YouTube Music's selected tab is not clickable, so its tab is "first" (Home, and none while Home is the
  // selected tab); YouTube and Instagram keep "firstClickable" (review of 31 October 2026, defect 1).
  assert.deepEqual(ytm?.surfaces[0]?.otherTab, { parentId: 'pivot_bar', child: 'first' });
  assert.deepEqual(rules.apps.flatMap((a) => a.surfaces.map((s) => [a.id, s.otherTab?.child ?? null])), [['youtube', 'firstClickable'], ['instagram', 'firstClickable'], ['x', null], ['ytmusic', 'first']]);
});

// No text claims more than the rules cover (review of 31 October 2026, defect 3): Samples is closed on the
// Samples tab, from an album's or artist's Samples button and from a link; never "wherever it opens".
test('no text says the feed is closed wherever it opens', () => {
  for (const p of ['ts/core/copy.ts', 'privacy.html', 'README.md', 'CHANGELOG.md', 'zapstore.yaml', `${FASTLANE}/full_description.txt`, `${FASTLANE}/changelogs/4.txt`]) {
    assert.doesNotMatch(read(p).replace(/\n {2}/g, ' '), /wherever it opens/, p);
  }
});

// SHOT-4 (review of 31 October 2026, defect 2): zsp and Play publish the screenshots as they are, and no
// DRAFT line covers images. These are the 0.0.3 screenshots (three apps, LinkedIn coming soon): the test
// fails while any of them is still in place, so a release cannot ship them by mistake.
test('the store screenshots are not the 0.0.3 ones', () => {
  const old003: Record<string, string> = {
    '01-what-it-does.png': '58fc076816b5ec658ec3ed51f3d238c63fd91acf822cff457cb8a52df7e010b0',
    '02-disclosure.png': 'a13f52b5a7fb6d95910671a0e1da5e806d81611fad456f82f2d4533078233829',
    '03-apps.png': 'bd1742c2be47d5e50298703738df14b15f2997c30c0f15050fe7812fb8de0e8a',
  };
  for (const [name, sha] of Object.entries(old003)) {
    const got = createHash('sha256').update(readFileSync(join(ROOT, `${FASTLANE}/images/phoneScreenshots/${name}`))).digest('hex');
    assert.notEqual(got, sha, `${name} is still the 0.0.3 screenshot: retake it (SHOT-4)`);
  }
});

test('the shared disclaimer is present and linked from both pages, and nothing names a private device', () => {
  assert.match(read('DISCLAIMER.md'), /^## Specific to StopScroll$/m);
  assert.match(read('disclaimer.html'), /<link rel="stylesheet" href="disclaimer\.css\?v=[0-9a-f]{8}" \/>/);
  assert.doesNotMatch(read('disclaimer.html'), /<style>|<script/);
  for (const p of ['index.html', 'privacy.html']) assert.match(read(p), /<a href="disclaimer\.html">Disclaimer<\/a>/);
  for (const p of ['rules/screen-rules.json', 'tools/scrub-fixture.ts', 'README.md']) {
    assert.doesNotMatch(read(p), /Pixel 10|Google profile|user 10|GrapheneOS/, p);
  }
});
