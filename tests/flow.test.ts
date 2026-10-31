// The app screen's logic (ts/core/flow.ts, as compiled into core/flow.js).
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { DISCLOSURE_VERSION } from '#core/copy.js';
import {
  andList, appList, appRows, COMING_SOON, disclosure, healthLine, longDate, parseState, pendingApps, pendingCard, restrictedHelpOpen, screenFor,
  showAdvancedProtection, statusLine, type State,
} from '#core/flow.js';

// As the app reports them before any agreement (agreed: false); the per-app tests set agreed.
const youtube = {
  id: 'youtube', package: 'com.google.android.youtube', name: 'YouTube', feedName: 'YouTube Shorts', mode: 'always', agreed: false,
  installed: true, versionName: '21.42.36', closed: 0, loopPauses: 0, lastClosed: null,
};
const instagram = {
  id: 'instagram', package: 'com.instagram.android', name: 'Instagram', feedName: 'Instagram Reels', mode: 'always', agreed: false,
  installed: true, versionName: '453.0.0.57.79', closed: 0, loopPauses: 0, lastClosed: null,
};
const x = {
  id: 'x', package: 'com.twitter.android', name: 'X', feedName: 'the X video feed', mode: 'always', agreed: false,
  installed: true, versionName: '12.38.1-prod.01', closed: 0, loopPauses: 0, lastClosed: null,
};
const ytmusic = {
  id: 'ytmusic', package: 'com.google.android.apps.youtube.music', name: 'YouTube Music', feedName: 'YouTube Music Samples', mode: 'always', agreed: false,
  installed: true, versionName: '9.43.51', closed: 0, loopPauses: 0, lastClosed: null,
};
const base = {
  consentGiven: false, consentDate: null, serviceEnabled: false, serviceRunning: false, sdk: 36, installedFromFile: false,
  returnedFromSettings: false, appVersion: '0.0.4', rulesVersion: '2026-10-31.2', disclosureVersion: DISCLOSURE_VERSION, apps: [youtube, instagram, x, ytmusic],
};
const agreed = <T extends object>(a: T): T & { agreed: true } => ({ ...a, agreed: true });
/** Every app agreed to: a fresh 0.0.4 install after "I agree". */
const allAgreed = { consentGiven: true, consentDate: '2026-10-31', apps: [agreed(youtube), agreed(instagram), agreed(x), agreed(ytmusic)] };
/** The 0.0.2 to 0.0.3 update, as 0.0.3 saw it (three apps): YouTube and Instagram agreed, X waiting. */
const upgraded = { consentGiven: true, consentDate: '2026-10-30', apps: [agreed(youtube), agreed(instagram), x] };
/** The 0.0.3 to 0.0.4 update: YouTube, Instagram and X agreed, YouTube Music waiting. */
const from003 = { consentGiven: true, consentDate: '2026-10-31', apps: [agreed(youtube), agreed(instagram), agreed(x), ytmusic] };
/** The 0.0.2 to 0.0.4 update: YouTube and Instagram agreed, X and YouTube Music waiting. */
const from002 = { consentGiven: true, consentDate: '2026-10-30', apps: [agreed(youtube), agreed(instagram), x, ytmusic] };
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
  // Since 0.0.3 every app row says whether it is agreed to.
  const { agreed: _dropped, ...noAgreed } = youtube;
  assert.equal(parseState({ ...base, apps: [noAgreed] }), null);
  assert.equal(parseState({ ...base, apps: [{ ...youtube, agreed: 'yes' }] }), null);
});

test('the disclosure comes before Android\'s settings: no consent, no setup screen', () => {
  assert.equal(screenFor(null, { disclosureOpen: false }), 'web');
  assert.equal(screenFor(state(), { disclosureOpen: false }), 'intro');
  assert.equal(screenFor(state(), { disclosureOpen: true }), 'disclosure');
  assert.equal(screenFor(state({ serviceEnabled: true }), { disclosureOpen: false }), 'intro', 'service on without consent stays inert');
  assert.equal(screenFor(state({ ...allAgreed }), { disclosureOpen: false }), 'setup');
  assert.equal(screenFor(state({ ...allAgreed, serviceEnabled: true }), { disclosureOpen: false }), 'home');
  assert.equal(screenFor(state({ ...allAgreed, disclosureVersion: DISCLOSURE_VERSION + 1 }), { disclosureOpen: false }), 'intro');
  // Every app agreed: nothing is pending, so a stale disclosureOpen never shows the disclosure again.
  assert.equal(screenFor(state({ ...allAgreed, serviceEnabled: true }), { disclosureOpen: true }), 'home');
});

test('per-app consent: after the 0.0.2 update YouTube and Instagram stay agreed, X waits behind the pending card', () => {
  const s = state({ ...upgraded, serviceEnabled: true });
  assert.equal(screenFor(s, { disclosureOpen: false }), 'home', 'no intro: the user agreed before');
  assert.equal(screenFor(state({ ...upgraded }), { disclosureOpen: false }), 'setup');
  assert.equal(screenFor(s, { disclosureOpen: true }), 'disclosure', '"Read and agree" opens the full disclosure');
  assert.deepEqual(pendingApps(s).map((a) => a.name), ['X']);
  assert.deepEqual(pendingCard(s), {
    title: 'StopScroll now works with X too.',
    body: 'StopScroll does not watch X until you read the updated disclosure and agree. The apps you agreed to before keep working as they are.',
    button: 'Read and agree',
  });
  // The disclosure it opens names all three apps, not only X.
  assert.match(disclosure(s).paragraphs.map((p) => p.body).join(' '), /Only while you are in the YouTube, Instagram or X app\./);
  const rows = appRows(s);
  assert.deepEqual(rows.map((r) => [r.name, r.waiting]), [['YouTube', false], ['Instagram', false], ['X', true], ['Facebook', false], ['Snapchat', false]]);
  assert.equal(rows[2]?.note, 'Waiting for your agreement: StopScroll does not watch X yet.');
  assert.equal(rows[0]?.note, 'YouTube 21.42.36 is installed.');
  // The status line counts only agreed apps: X waiting with Always block does not make it "on".
  assert.equal(statusLine(state({ ...upgraded, serviceEnabled: true, apps: [{ ...agreed(youtube), mode: 'off' }, { ...agreed(instagram), mode: 'off' }, x] })), 'StopScroll is on, but every app is set to Off.');
  assert.equal(statusLine(s), 'StopScroll is on.');
  // Nothing pending before any agreement (the intro asks for every app at once) or after agreeing to all.
  assert.deepEqual(pendingApps(state()), []);
  assert.equal(pendingCard(state()), null);
  assert.equal(pendingCard(state({ ...allAgreed })), null);
  assert.ok(appRows(state()).every((r) => !r.waiting), 'no waiting rows before any agreement');
  // Two or three apps waiting (a later release).
  const two = state({ ...upgraded, apps: [agreed(youtube), { ...instagram }, x] });
  assert.equal(pendingCard(two)?.title, 'StopScroll now works with Instagram and X too.');
  assert.equal(andList(['X']), 'X');
  assert.equal(andList(['X', 'Facebook']), 'X and Facebook');
  assert.equal(andList(['X', 'Facebook', 'Snapchat']), 'X, Facebook and Snapchat');
  assert.throws(() => andList([]));
});

test('per-app consent: after the 0.0.3 update YouTube, Instagram and X stay agreed, YouTube Music waits (CONSENT-4)', () => {
  const s = state({ ...from003, serviceEnabled: true });
  assert.equal(screenFor(s, { disclosureOpen: false }), 'home', 'no intro and no new agreement for the three apps');
  assert.equal(screenFor(s, { disclosureOpen: true }), 'disclosure');
  assert.deepEqual(pendingApps(s).map((a) => a.name), ['YouTube Music']);
  assert.deepEqual(pendingCard(s), {
    title: 'StopScroll now works with YouTube Music too.',
    body: 'StopScroll does not watch YouTube Music until you read the updated disclosure and agree. The apps you agreed to before keep working as they are.',
    button: 'Read and agree',
  });
  // "Read and agree" opens the disclosure that names all four apps.
  assert.match(disclosure(s).paragraphs.map((p) => p.body).join(' '), /Only while you are in the YouTube, Instagram, X or YouTube Music app\./);
  const rows = appRows(s);
  assert.deepEqual(rows.map((r) => [r.name, r.waiting]), [['YouTube', false], ['Instagram', false], ['X', false], ['YouTube Music', true], ['Facebook', false], ['Snapchat', false]]);
  assert.equal(rows[3]?.note, 'Waiting for your agreement: StopScroll does not watch YouTube Music yet.');
  // YouTube Music waiting with Always block does not make StopScroll "on".
  const allOff = state({ ...from003, serviceEnabled: true, apps: [{ ...agreed(youtube), mode: 'off' }, { ...agreed(instagram), mode: 'off' }, { ...agreed(x), mode: 'off' }, ytmusic] });
  assert.equal(statusLine(allOff), 'StopScroll is on, but every app is set to Off.');
  // From 0.0.2: X and YouTube Music wait together.
  const two = state({ ...from002, serviceEnabled: true });
  assert.deepEqual(pendingApps(two).map((a) => a.name), ['X', 'YouTube Music']);
  assert.equal(pendingCard(two)?.title, 'StopScroll now works with X and YouTube Music too.');
  assert.equal(pendingCard(two)?.body, 'StopScroll does not watch X and YouTube Music until you read the updated disclosure and agree. The apps you agreed to before keep working as they are.');
  // From 0.0.1 (no agreement that counts): the intro, as on a fresh install, and nothing pending.
  assert.equal(screenFor(state({ serviceEnabled: true }), { disclosureOpen: false }), 'intro');
  assert.equal(pendingCard(state()), null);
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

test('the app list: YouTube, Instagram, X and YouTube Music selectable; Facebook and Snapchat coming soon, not selectable', () => {
  const rows = appRows(state({ ...allAgreed }));
  assert.deepEqual(rows.map((r) => r.name), ['YouTube', 'Instagram', 'X', 'YouTube Music', 'Facebook', 'Snapchat']);
  assert.equal(rows[0]?.app?.package, 'com.google.android.youtube');
  assert.equal(rows[1]?.app?.package, 'com.instagram.android');
  assert.equal(rows[2]?.app?.package, 'com.twitter.android');
  assert.equal(rows[2]?.app?.mode, 'always', 'X is Always block once agreed (spec 6.6)');
  assert.equal(rows[2]?.waiting, false);
  assert.equal(rows[1]?.note, 'Instagram 453.0.0.57.79 is installed.');
  assert.equal(rows[2]?.note, 'X 12.38.1-prod.01 is installed.');
  assert.equal(rows[3]?.app?.package, 'com.google.android.apps.youtube.music');
  assert.equal(rows[3]?.app?.mode, 'always', 'YouTube Music is Always block once agreed (spec 6.6)');
  assert.equal(rows[3]?.note, 'YouTube Music 9.43.51 is installed.');
  for (const r of rows.slice(4)) {
    assert.equal(r.app, null, `${r.name} is not selectable`);
    assert.equal(r.note, 'Coming soon');
  }
  assert.deepEqual(COMING_SOON.map((c) => c.key), ['facebook', 'snapchat']);
  assert.match(appRows(state({ ...allAgreed, apps: [agreed(youtube), agreed(instagram), { ...agreed(x), installed: false, versionName: null }, agreed(ytmusic)] }))[2]?.note ?? '', /^X is not installed on this phone, so there is nothing to close\.$/);
  assert.equal(appRows(state({ ...allAgreed, apps: [agreed(youtube), agreed(instagram), agreed(x), { ...agreed(ytmusic), installed: false, versionName: null }] }))[3]?.note, 'YouTube Music is not installed on this phone, so there is nothing to close.');
  // An app the rules cover is never listed twice, and is never a coming-soon row.
  for (const key of ['instagram', 'x', 'ytmusic']) {
    assert.equal(rows.filter((r) => r.key === key).length, 1);
    assert.ok(!COMING_SOON.some((c) => c.key === key));
  }
  // Off for one app leaves the others on: the status line says on.
  assert.equal(statusLine(state({ ...allAgreed, serviceEnabled: true, apps: [{ ...agreed(youtube), mode: 'off' }, agreed(instagram), agreed(x), agreed(ytmusic)] })), 'StopScroll is on.');
});

test('the disclosure names exactly the apps this build watches, and no Facebook sentence', () => {
  const d = disclosure(state());
  const all = d.paragraphs.map((p) => `${p.lead ?? ''} ${p.body}`).join(' ');
  assert.equal(d.title, 'StopScroll needs Accessibility access');
  assert.match(all, /Only while you are in the YouTube, Instagram, X or YouTube Music app\./);
  assert.match(all, /whether a short-video feed is open: the Shorts or Reels player, X\u2019s full-screen video viewer or YouTube Music\u2019s Samples\./);
  assert.match(all, /If a later version adds an app, StopScroll does not watch it until you agree again\./);
  assert.doesNotMatch(all, /Facebook|Snapchat|LinkedIn/);
  assert.equal(DISCLOSURE_VERSION, 2, 'adding X or YouTube Music does not bump it (UPG-2, CONSENT-4): a 0.0.3 agreement still counts for YouTube, Instagram and X; a 0.0.1 one still does not');
  assert.deepEqual(d.paragraphs.map((p) => p.lead), [null, 'What it looks at.', 'What it does.', 'What it keeps.', 'Other apps.', null]);
  assert.equal(appList(['YouTube', 'Instagram']), 'the YouTube or Instagram app');
  assert.equal(appList(['YouTube', 'Instagram', 'X']), 'the YouTube, Instagram or X app');
  assert.equal(appList(['YouTube', 'Instagram', 'X', 'YouTube Music']), 'the YouTube, Instagram, X or YouTube Music app');
  assert.equal(appList(['YouTube', 'Instagram', 'Facebook']), 'the YouTube, Instagram or Facebook app');
  assert.throws(() => appList([]));
});

test('status line, dates and health line', () => {
  assert.equal(statusLine(state()), 'StopScroll is not on yet.');
  assert.match(statusLine(state({ ...allAgreed })), /Accessibility settings/);
  assert.equal(statusLine(state({ ...allAgreed, serviceEnabled: true })), 'StopScroll is on.');
  assert.match(statusLine(state({ ...allAgreed, serviceEnabled: true, apps: [{ ...agreed(youtube), mode: 'off' }, { ...agreed(instagram), mode: 'off' }, { ...agreed(x), mode: 'off' }, { ...agreed(ytmusic), mode: 'off' }] })), /set to Off/);
  assert.equal(longDate('2026-10-29'), '29 October 2026');
  assert.equal(longDate('2026-13-01'), '2026-13-01');
  assert.equal(healthLine(state().apps[0]!), 'No feed closed yet.');
  assert.equal(healthLine({ ...state().apps[0]!, lastClosed: '2026-11-02' }), 'Last closed a feed: 2 November 2026.');
});
