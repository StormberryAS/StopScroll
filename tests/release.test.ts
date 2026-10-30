// The release files agree with each other and with the build (0.0.1 release kit, 29 October 2026):
// the workflow's file list and the build's include list, the pinned certificate, the version and its
// release note, the store texts and images, and the watched app list.
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { existsSync, statSync } from 'node:fs';
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

test('version 0.0.1, versionCode 1, below the Play offset; Play is 1000 plus it', () => {
  assert.equal(versionName, '0.0.1');
  assert.equal(versionCode, 1);
  assert.ok(versionCode < 1000);
  assert.match(gradle, /val PLAY_VERSION_CODE_OFFSET = 1000\n/);
  assert.match(gradle, /applicationId = if \(playBuild\) "no\.stormberry\.stopscroll\.play" else "no\.stormberry\.stopscroll"/);
  assert.match(read('README.md'), /\| `android-v0\.0\.1` \| 1 \| 0\.0\.1 \| 1001 \|/);
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
  assert.match(all, /Not affiliated with or endorsed by Google, YouTube, Meta, Snap, LinkedIn or X\./);
  assert.match(all, /functioning prototype/);
  assert.doesNotMatch(all, /reproducible/i, 'not claimed before a release matched');
});

test('the coming-soon apps are names on the screen only: never watched, never queried, never in the rules', () => {
  const svc = read('android/app/src/main/res/xml/accessibility_service_config.xml');
  const manifest = read('android/app/src/main/AndroidManifest.xml').replace(/<!--[\s\S]*?-->/g, '');
  const rules = read('rules/screen-rules.json');
  for (const pkg of ['com.instagram.android', 'com.facebook.katana', 'com.snapchat.android', 'com.linkedin.android', 'com.twitter.android']) {
    assert.ok(!svc.includes(pkg) && !manifest.includes(pkg) && !rules.includes(pkg), `${pkg} is not watched`);
  }
  assert.deepEqual([...manifest.matchAll(/<package android:name="([^"]+)"/g)].map((m) => m[1]), ['com.google.android.youtube']);
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
