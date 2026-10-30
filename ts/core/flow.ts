// The app screen's logic, pure: which screen to show, which help to open, which apps to list, what
// the disclosure says. No DOM here (tsconfig.core.json type-checks ts/core/ with ES2020 only), so the
// Node tests run exactly what the page runs. ts/app.ts draws what this module decides.

import { DISCLOSURE_VERSION, text } from './copy.js';

export type Mode = 'off' | 'always';

/** One watched app, as the Android app reports it (Bridge.kt, state). */
export interface AppInfo {
  readonly id: string;
  readonly package: string;
  readonly name: string;
  readonly feedName: string;
  readonly mode: Mode;
  readonly installed: boolean;
  readonly versionName: string | null;
  readonly closed: number;
  readonly loopPauses: number;
  readonly lastClosed: string | null;
}

/** What the Android app reports. Absent on the website. */
export interface State {
  readonly consentGiven: boolean;
  readonly consentDate: string | null;
  readonly serviceEnabled: boolean;
  readonly serviceRunning: boolean;
  readonly sdk: number;
  readonly installedFromFile: boolean;
  readonly returnedFromSettings: boolean;
  readonly appVersion: string;
  readonly rulesVersion: string;
  readonly disclosureVersion: number;
  readonly apps: readonly AppInfo[];
}

/** What the page itself remembers between draws (never stored: the app keeps the settings). */
export interface UiMemory {
  /** The user asked to turn blocking on and has not answered the disclosure yet. */
  readonly disclosureOpen: boolean;
}

export type Screen = 'web' | 'intro' | 'disclosure' | 'setup' | 'home';

const ISO_DAY = /^\d{4}-\d{2}-\d{2}$/;
const isObj = (x: unknown): x is Record<string, unknown> => typeof x === 'object' && x !== null && !Array.isArray(x);
const isStr = (x: unknown): x is string => typeof x === 'string';
const isBool = (x: unknown): x is boolean => typeof x === 'boolean';
const isInt = (x: unknown): x is number => typeof x === 'number' && Number.isInteger(x) && x >= 0;
const strOrNull = (x: unknown): x is string | null => x === null || isStr(x);
const dayOrNull = (x: unknown): x is string | null => x === null || (isStr(x) && ISO_DAY.test(x));

function parseApp(x: unknown): AppInfo | null {
  if (!isObj(x)) return null;
  const { id, name, feedName, mode, installed, versionName, closed, loopPauses, lastClosed } = x;
  const pkg = x['package'];
  if (!isStr(id) || !isStr(pkg) || !isStr(name) || !isStr(feedName)) return null;
  if (mode !== 'off' && mode !== 'always') return null;
  if (!isBool(installed) || !strOrNull(versionName) || !isInt(closed) || !isInt(loopPauses) || !dayOrNull(lastClosed)) return null;
  return { id, package: pkg, name, feedName, mode, installed, versionName, closed, loopPauses, lastClosed };
}

/** Checks a state message from the app field by field; anything unexpected gives null. */
export function parseState(x: unknown): State | null {
  if (!isObj(x)) return null;
  const s = x;
  if (!isBool(s['consentGiven']) || !dayOrNull(s['consentDate']) || !isBool(s['serviceEnabled']) || !isBool(s['serviceRunning'])) return null;
  if (!isInt(s['sdk']) || !isBool(s['installedFromFile']) || !isBool(s['returnedFromSettings'])) return null;
  if (!isStr(s['appVersion']) || !isStr(s['rulesVersion']) || !isInt(s['disclosureVersion'])) return null;
  const appsRaw = s['apps'];
  if (!Array.isArray(appsRaw) || appsRaw.length === 0) return null;
  const apps = appsRaw.map(parseApp);
  if (apps.some((a) => a === null)) return null;
  return {
    consentGiven: s['consentGiven'],
    consentDate: s['consentDate'],
    serviceEnabled: s['serviceEnabled'],
    serviceRunning: s['serviceRunning'],
    sdk: s['sdk'],
    installedFromFile: s['installedFromFile'],
    returnedFromSettings: s['returnedFromSettings'],
    appVersion: s['appVersion'],
    rulesVersion: s['rulesVersion'],
    disclosureVersion: s['disclosureVersion'],
    apps: apps as AppInfo[],
  };
}

/**
 * The screen to show. The disclosure always comes BEFORE Android's Accessibility settings: without
 * a consent the page never offers the settings button (and the app refuses to open them anyway).
 */
export function screenFor(state: State | null, ui: UiMemory): Screen {
  if (state === null) return 'web';
  if (!state.consentGiven || state.disclosureVersion !== DISCLOSURE_VERSION) return ui.disclosureOpen ? 'disclosure' : 'intro';
  return state.serviceEnabled ? 'home' : 'setup';
}

/** Open the restricted-settings steps at once: a downloaded or local install, or back from settings with the service still off (spec 10.2). */
export function restrictedHelpOpen(state: State): boolean {
  return state.installedFromFile || (state.returnedFromSettings && !state.serviceEnabled);
}

/** Android 17 (API 37) and later, whenever the service is not running (spec 10.4, AP-2 b). */
export function showAdvancedProtection(state: State): boolean {
  return state.sdk >= 37 && !state.serviceRunning;
}

export interface AppRow {
  readonly key: string;
  readonly name: string;
  /** null: not selectable in this build ("coming soon"). */
  readonly app: AppInfo | null;
  readonly note: string;
}

/**
 * The apps 0.0.1 lists as "Coming soon" (Marcos, 2026-10-29), in his order. They are names on the
 * screen and nothing else: none of them is in the service's packageNames, the <queries> block or the
 * disclosure, so StopScroll receives nothing from them (tests/guards.test.ts pins that). A later
 * release that adds one moves it into rules/screen-rules.json, and the row becomes a real one.
 */
export const COMING_SOON: readonly { readonly key: string; readonly name: string }[] = [
  { key: 'instagram', name: 'Instagram' },
  { key: 'facebook', name: 'Facebook' },
  { key: 'snapchat', name: 'Snapchat' },
  { key: 'linkedin', name: 'LinkedIn' },
  { key: 'x', name: 'X' },
];

/**
 * The apps the settings list shows: every app the rules cover (YouTube in 0.0.1), then the coming-soon
 * apps the rules do not cover yet, disabled.
 */
export function appRows(state: State): AppRow[] {
  const rows: AppRow[] = state.apps.map((a) => ({
    key: a.id,
    name: a.name,
    app: a,
    note: a.installed ? text.appInstalled(a.name, a.versionName) : text.appNotInstalled(a.name),
  }));
  for (const c of COMING_SOON) {
    if (!state.apps.some((a) => a.id === c.key)) rows.push({ key: c.key, name: c.name, app: null, note: text.comingSoonLabel });
  }
  return rows;
}

/** "the YouTube app", "the YouTube or Instagram app", "the YouTube, Instagram or Facebook app". */
export function appList(names: readonly string[]): string {
  if (names.length === 0) throw new Error('no app named');
  if (names.length === 1) return `the ${names[0]} app`;
  return `the ${names.slice(0, -1).join(', ')} or ${names[names.length - 1]} app`;
}

export interface Paragraph {
  readonly lead: string | null;
  readonly body: string;
}

/** The in-app prominent disclosure (spec 12.3), naming exactly the apps this build watches. */
export function disclosure(state: State): { readonly title: string; readonly paragraphs: readonly Paragraph[] } {
  return { title: text.disclosureTitle, paragraphs: text.disclosure(appList(state.apps.map((a) => a.name))) };
}

/** The line at the top of the app screen. */
export function statusLine(state: State): string {
  if (!state.consentGiven) return text.statusNoConsent;
  if (!state.serviceEnabled) return text.statusOff;
  if (state.apps.every((a) => a.mode === 'off')) return text.statusAllOff;
  return text.statusOn;
}

const MONTHS = ['January', 'February', 'March', 'April', 'May', 'June', 'July', 'August', 'September', 'October', 'November', 'December'];

/** "2026-10-29" to "29 October 2026" (British prose dates); anything else is returned unchanged. */
export function longDate(iso: string): string {
  const m = /^(\d{4})-(\d{2})-(\d{2})$/.exec(iso);
  if (!m) return iso;
  const month = MONTHS[Number(m[2]) - 1];
  return month === undefined ? iso : `${Number(m[3])} ${month} ${m[1]}`;
}

/** The health line of one app (spec 6.8). */
export function healthLine(a: AppInfo): string {
  return a.lastClosed === null ? text.neverClosed : text.lastClosed(longDate(a.lastClosed));
}
