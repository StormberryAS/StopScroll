// The app screen's logic (ts/core/flow.ts, as compiled into core/flow.js).
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { DISCLOSURE_VERSION } from '#core/copy.js';
import {
  appList, appRows, COMING_SOON, disclosure, healthLine, longDate, parseState, restrictedHelpOpen, screenFor, showAdvancedProtection, statusLine,
  type State,
} from '#core/flow.js';

const youtube = {
  id: 'youtube', package: 'com.google.android.youtube', name: 'YouTube', feedName: 'YouTube Shorts', mode: 'always',
  installed: true, versionName: '21.42.36', closed: 0, loopPauses: 0, lastClosed: null,
};
const instagram = {
  id: 'instagram', package: 'com.instagram.android', name: 'Instagram', feedName: 'Instagram Reels', mode: 'always',
  installed: true, versionName: '453.0.0.57.79', closed: 0, loopPauses: 0, lastClosed: null,
};
const base = {
  consentGiven: false, consentDate: null, serviceEnabled: false, serviceRunning: false, sdk: 36, installedFromFile: false,
  returnedFromSettings: false, appVersion: '0.0.2', rulesVersion: '2026-10-30.1', disclosureVersion: DISCLOSURE_VERSION, apps: [youtube, instagram],
};
const state = (over: Record<string, unknown> = {}): State => {
  const s = parseState({ ...base, ...over });
  assert.ok(s, 'a valid state');
  return s;
};

test('parseState takes the app\'s state and refuses anything malformed', () => {
  assert.equal(state().apps[0]?.name, 'YouTube');
  assert.equal(parseState(null), null);
  assert.equal(parseState({ ...base, apps: [] }), null);
  assert.equal(parseState({ ...base, sdk: '36' }), null);
  assert.equal(parseState({ ...base, consentDate: 'yesterday' }), null);
  assert.equal(parseState({ ...base, apps: [{ ...youtube, mode: 'strict' }] }), null);
  assert.equal(parseState({ ...base, apps: [{ ...youtube, closed: -1 }] }), null);
});

test('the disclosure comes before Android\'s settings: no consent, no setup screen', () => {
  assert.equal(screenFor(null, { disclosureOpen: false }), 'web');
  assert.equal(screenFor(state(), { disclosureOpen: false }), 'intro');
  assert.equal(screenFor(state(), { disclosureOpen: true }), 'disclosure');
  assert.equal(screenFor(state({ serviceEnabled: true }), { disclosureOpen: false }), 'intro', 'service on without consent stays inert');
  assert.equal(screenFor(state({ consentGiven: true, consentDate: '2026-10-29' }), { disclosureOpen: false }), 'setup');
  assert.equal(screenFor(state({ consentGiven: true, serviceEnabled: true }), { disclosureOpen: false }), 'home');
  assert.equal(screenFor(state({ consentGiven: true, disclosureVersion: DISCLOSURE_VERSION + 1 }), { disclosureOpen: false }), 'intro');
});

test('restricted-settings help opens for a file install, or back from settings with the service still off', () => {
  assert.equal(restrictedHelpOpen(state()), false);
  assert.equal(restrictedHelpOpen(state({ installedFromFile: true })), true);
  assert.equal(restrictedHelpOpen(state({ returnedFromSettings: true })), true);
  assert.equal(restrictedHelpOpen(state({ returnedFromSettings: true, serviceEnabled: true })), false);
});

test('the Advanced Protection line shows on Android 17 and later while the service is not running', () => {
  assert.equal(showAdvancedProtection(state({ sdk: 36 })), false);
  assert.equal(showAdvancedProtection(state({ sdk: 37 })), true);
  assert.equal(showAdvancedProtection(state({ sdk: 37, serviceRunning: true })), false);
});

test('the app list: YouTube and Instagram selectable; Facebook, Snapchat, LinkedIn and X coming soon, not selectable', () => {
  const rows = appRows(state());
  assert.deepEqual(rows.map((r) => r.name), ['YouTube', 'Instagram', 'Facebook', 'Snapchat', 'LinkedIn', 'X']);
  assert.equal(rows[0]?.app?.package, 'com.google.android.youtube');
  assert.equal(rows[1]?.app?.package, 'com.instagram.android');
  assert.equal(rows[1]?.app?.mode, 'always');
  assert.equal(rows[1]?.note, 'Instagram 453.0.0.57.79 is installed.');
  for (const r of rows.slice(2)) {
    assert.equal(r.app, null, `${r.name} is not selectable`);
    assert.equal(r.note, 'Coming soon');
  }
  assert.deepEqual(COMING_SOON.map((c) => c.key), ['facebook', 'snapchat', 'linkedin', 'x']);
  assert.match(appRows(state({ apps: [youtube, { ...instagram, installed: false, versionName: null }] }))[1]?.note ?? '', /^Instagram is not installed/);
  // An app the rules cover is never listed twice, and is never a coming-soon row.
  assert.equal(rows.filter((r) => r.key === 'instagram').length, 1);
  assert.ok(!COMING_SOON.some((c) => c.key === 'instagram'));
  // Off for one app leaves the other on: the status line says on.
  assert.equal(statusLine(state({ consentGiven: true, serviceEnabled: true, apps: [{ ...youtube, mode: 'off' }, instagram] })), 'StopScroll is on.');
});

test('the disclosure names exactly the apps this build watches, and no Facebook sentence', () => {
  const d = disclosure(state());
  const all = d.paragraphs.map((p) => `${p.lead ?? ''} ${p.body}`).join(' ');
  assert.equal(d.title, 'StopScroll needs Accessibility access');
  assert.match(all, /Only while you are in the YouTube or Instagram app\./);
  assert.match(all, /whether a Shorts or Reels player is open/);
  assert.doesNotMatch(all, /Facebook|Snapchat|LinkedIn/);
  assert.equal(DISCLOSURE_VERSION, 2, '0.0.2 changed the disclosure, so a 0.0.1 consent no longer counts');
  assert.deepEqual(d.paragraphs.map((p) => p.lead), [null, 'What it looks at.', 'What it does.', 'What it keeps.', 'Other apps.', null]);
  assert.equal(appList(['YouTube', 'Instagram']), 'the YouTube or Instagram app');
  assert.equal(appList(['YouTube', 'Instagram', 'Facebook']), 'the YouTube, Instagram or Facebook app');
  assert.throws(() => appList([]));
});

test('status line, dates and health line', () => {
  assert.equal(statusLine(state()), 'StopScroll is not on yet.');
  assert.match(statusLine(state({ consentGiven: true })), /Accessibility settings/);
  assert.equal(statusLine(state({ consentGiven: true, serviceEnabled: true })), 'StopScroll is on.');
  assert.match(statusLine(state({ consentGiven: true, serviceEnabled: true, apps: [{ ...youtube, mode: 'off' }, { ...instagram, mode: 'off' }] })), /set to Off/);
  assert.equal(longDate('2026-10-29'), '29 October 2026');
  assert.equal(longDate('2026-13-01'), '2026-13-01');
  assert.equal(healthLine(state().apps[0]!), 'No feed closed yet.');
  assert.equal(healthLine({ ...state().apps[0]!, lastClosed: '2026-11-02' }), 'Last closed a feed: 2 November 2026.');
});
