import { DISCLOSURE_VERSION, text } from './copy.js';
const ISO_DAY = /^\d{4}-\d{2}-\d{2}$/;
const isObj = (x) => typeof x === 'object' && x !== null && !Array.isArray(x);
const isStr = (x) => typeof x === 'string';
const isBool = (x) => typeof x === 'boolean';
const isInt = (x) => typeof x === 'number' && Number.isInteger(x) && x >= 0;
const strOrNull = (x) => x === null || isStr(x);
const dayOrNull = (x) => x === null || (isStr(x) && ISO_DAY.test(x));
function parseApp(x) {
    if (!isObj(x))
        return null;
    const { id, name, feedName, mode, agreed, installed, versionName, closed, loopPauses, lastClosed } = x;
    const pkg = x['package'];
    if (!isStr(id) || !isStr(pkg) || !isStr(name) || !isStr(feedName))
        return null;
    if (mode !== 'off' && mode !== 'always')
        return null;
    if (!isBool(agreed) || !isBool(installed) || !strOrNull(versionName) || !isInt(closed) || !isInt(loopPauses) || !dayOrNull(lastClosed))
        return null;
    return { id, package: pkg, name, feedName, mode, agreed, installed, versionName, closed, loopPauses, lastClosed };
}
export function parseState(x) {
    if (!isObj(x))
        return null;
    const s = x;
    if (!isBool(s['consentGiven']) || !dayOrNull(s['consentDate']) || !isBool(s['serviceEnabled']) || !isBool(s['serviceRunning']))
        return null;
    if (!isInt(s['sdk']) || !isBool(s['installedFromFile']) || !isBool(s['returnedFromSettings']))
        return null;
    if (!isStr(s['appVersion']) || !isStr(s['rulesVersion']) || !isInt(s['disclosureVersion']))
        return null;
    const appsRaw = s['apps'];
    if (!Array.isArray(appsRaw) || appsRaw.length === 0)
        return null;
    const apps = appsRaw.map(parseApp);
    if (apps.some((a) => a === null))
        return null;
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
        apps: apps,
    };
}
export function screenFor(state, ui) {
    if (state === null)
        return 'web';
    if (!state.consentGiven || state.disclosureVersion !== DISCLOSURE_VERSION)
        return ui.disclosureOpen ? 'disclosure' : 'intro';
    if (ui.disclosureOpen && pendingApps(state).length > 0)
        return 'disclosure';
    return state.serviceEnabled ? 'home' : 'setup';
}
export function pendingApps(state) {
    return state.consentGiven ? state.apps.filter((a) => !a.agreed) : [];
}
export function andList(names) {
    if (names.length === 0)
        throw new Error('no app named');
    if (names.length === 1)
        return names[0];
    return `${names.slice(0, -1).join(', ')} and ${names[names.length - 1]}`;
}
export function pendingCard(state) {
    const waiting = pendingApps(state);
    if (waiting.length === 0)
        return null;
    const names = andList(waiting.map((a) => a.name));
    return { title: text.pendingTitle(names), body: text.pendingBody(names), button: text.pendingButton };
}
export function restrictedHelpOpen(state) {
    return state.installedFromFile || (state.returnedFromSettings && !state.serviceEnabled);
}
export function showAdvancedProtection(state) {
    return state.sdk >= 37 && !state.serviceRunning;
}
export const COMING_SOON = [
    { key: 'facebook', name: 'Facebook' },
    { key: 'snapchat', name: 'Snapchat' },
    { key: 'linkedin', name: 'LinkedIn' },
];
export function appRows(state) {
    const rows = state.apps.map((a) => {
        const waiting = state.consentGiven && !a.agreed;
        return {
            key: a.id,
            name: a.name,
            app: a,
            waiting,
            note: waiting ? text.appWaiting(a.name) : a.installed ? text.appInstalled(a.name, a.versionName) : text.appNotInstalled(a.name),
        };
    });
    for (const c of COMING_SOON) {
        if (!state.apps.some((a) => a.id === c.key))
            rows.push({ key: c.key, name: c.name, app: null, waiting: false, note: text.comingSoonLabel });
    }
    return rows;
}
export function appList(names) {
    if (names.length === 0)
        throw new Error('no app named');
    if (names.length === 1)
        return `the ${names[0]} app`;
    return `the ${names.slice(0, -1).join(', ')} or ${names[names.length - 1]} app`;
}
export function disclosure(state) {
    return { title: text.disclosureTitle, paragraphs: text.disclosure(appList(state.apps.map((a) => a.name))) };
}
export function statusLine(state) {
    if (!state.consentGiven)
        return text.statusNoConsent;
    if (!state.serviceEnabled)
        return text.statusOff;
    if (state.apps.filter((a) => a.agreed).every((a) => a.mode === 'off'))
        return text.statusAllOff;
    return text.statusOn;
}
const MONTHS = ['January', 'February', 'March', 'April', 'May', 'June', 'July', 'August', 'September', 'October', 'November', 'December'];
export function longDate(iso) {
    const m = /^(\d{4})-(\d{2})-(\d{2})$/.exec(iso);
    if (!m)
        return iso;
    const month = MONTHS[Number(m[2]) - 1];
    return month === undefined ? iso : `${Number(m[3])} ${month} ${m[1]}`;
}
export function healthLine(a) {
    return a.lastClosed === null ? text.neverClosed : text.lastClosed(longDate(a.lastClosed));
}
