// StopScroll's settings screen. Draws what ts/core/flow.ts decides, with the words of
// ts/core/copy.ts, and talks to the Android app only through window.StopScrollApp (the bridge
// script, android/web-overlay/android-bridge.ts). Elements are built with DOM methods, never
// innerHTML. The page keeps no settings and no storage: the app keeps them.

import type { AppHost, HostResult } from './app-host.js';
import { DISCLOSURE_VERSION, text } from './core/copy.js';
import {
  appRows,
  disclosure,
  healthLine,
  longDate,
  parseState,
  restrictedHelpOpen,
  screenFor,
  showAdvancedProtection,
  statusLine,
  type AppInfo,
  type State,
  type UiMemory,
} from './core/flow.js';

type Child = Node | string | null | false;

function h<K extends keyof HTMLElementTagNameMap>(tag: K, attrs: Record<string, string> = {}, ...children: Child[]): HTMLElementTagNameMap[K] {
  const el = document.createElement(tag);
  for (const [k, v] of Object.entries(attrs)) el.setAttribute(k, v);
  for (const c of children) if (c !== null && c !== false) el.append(c);
  return el;
}

function button(label: string, cls: string, onClick: () => void): HTMLButtonElement {
  const b = h('button', { type: 'button', class: cls }, label);
  b.addEventListener('click', onClick);
  return b;
}

const root = document.getElementById('app');
const notice = document.getElementById('notice');
const host: AppHost | undefined = window.StopScrollApp;
let state: State | null = null;
let ui: UiMemory = { disclosureOpen: false };

function say(message: string | null): void {
  if (!notice) return;
  notice.textContent = message ?? '';
  notice.hidden = message === null;
}

async function act(call: () => Promise<HostResult>): Promise<void> {
  say(null);
  const r = await call();
  if (!r.ok) say(r.error === 'no-consent' ? text.errorNoConsent : text.errorGeneric);
  await refresh();
}

async function refresh(): Promise<void> {
  if (!host) return draw();
  const r = await host.getState();
  const next = parseState(r.state);
  if (next) state = next;
  draw();
}

function card(title: string | null, ...children: Child[]): HTMLElement {
  return h('section', { class: 'card' }, title ? h('h2', {}, title) : null, ...children);
}

function introScreen(): HTMLElement[] {
  return [
    card(text.introTitle, ...text.introBody.map((p) => h('p', {}, p)), h('p', { class: 'muted' }, text.introNext),
      h('div', { class: 'actions' }, button(text.turnOnButton, 'primary', () => {
        ui = { disclosureOpen: true };
        draw();
      }))),
  ];
}

function disclosureScreen(s: State): HTMLElement[] {
  const d = disclosure(s);
  const paragraphs = d.paragraphs.map((p) => h('p', {}, p.lead ? h('strong', {}, p.lead) : null, p.lead ? ' ' : null, p.body));
  return [
    h('section', { class: 'card disclosure', 'aria-labelledby': 'disclosure-title' },
      h('h2', { id: 'disclosure-title', tabindex: '-1' }, d.title),
      ...paragraphs,
      h('div', { class: 'actions' },
        button(text.agree, 'primary', () => {
          ui = { disclosureOpen: false };
          void act(() => (host as AppHost).recordConsent(DISCLOSURE_VERSION));
        }),
        button(text.notNow, 'secondary', () => {
          ui = { disclosureOpen: false };
          draw();
        }))),
  ];
}

function restrictedHelp(s: State): HTMLElement {
  const details = h('details', { class: 'help' },
    h('summary', {}, text.restrictedLink),
    h('p', {}, text.restrictedIntro),
    h('ol', {}, ...text.restrictedSteps.map((step) => h('li', {}, step))),
    h('p', { class: 'muted' }, text.restrictedOutro),
    h('div', { class: 'actions' }, button(text.openAppInfo, 'secondary', () => void act(() => (host as AppHost).openAppInfo()))));
  details.open = restrictedHelpOpen(s);
  return details;
}

function setupScreen(s: State): HTMLElement[] {
  return [
    card(text.setupTitle,
      h('p', {}, text.setupBody),
      s.consentDate ? h('p', { class: 'muted' }, text.agreedOn(longDate(s.consentDate))) : null,
      h('div', { class: 'actions' }, button(text.openSettings, 'primary', () => void act(() => (host as AppHost).openAccessibilitySettings()))),
      restrictedHelp(s),
      showAdvancedProtection(s) ? h('p', { class: 'hint' }, text.advancedProtection) : null),
    appsCard(s),
  ];
}

function modeChoice(a: AppInfo): HTMLElement {
  const group = h('div', { class: 'modes', role: 'radiogroup', 'aria-label': a.name });
  for (const [mode, label] of [['off', text.modeOff], ['always', text.modeAlways]] as const) {
    const id = `mode-${a.id}-${mode}`;
    const input = h('input', { type: 'radio', name: `mode-${a.id}`, id, value: mode });
    input.checked = a.mode === mode;
    input.addEventListener('change', () => {
      if (input.checked) void act(() => (host as AppHost).setMode(a.package, mode));
    });
    group.append(h('label', { for: id, class: 'mode' }, input, ' ', label));
  }
  return group;
}

function appsCard(s: State): HTMLElement {
  const all = appRows(s);
  const rows = all.map((row) =>
    row.app
      ? h('div', { class: 'app-row' },
        h('h3', {}, row.name),
        modeChoice(row.app),
        h('p', { class: 'muted' }, row.note),
        h('p', { class: 'muted' }, healthLine(row.app)),
        row.app.closed + row.app.loopPauses > 0 ? h('p', { class: 'muted' }, text.counts(row.app.closed, row.app.loopPauses)) : null)
      // A coming-soon app: its name and "Coming soon", nothing to choose (0.0.1 kit).
      : h('div', { class: 'app-row disabled', 'aria-disabled': 'true' },
        h('h3', {}, row.name, ' ', h('span', { class: 'soon' }, row.note))));
  const soon = all.some((row) => row.app === null) ? h('p', { class: 'muted soon-note' }, text.comingSoonNote) : null;
  return card(text.appsTitle, ...rows, soon);
}

function homeScreen(s: State): HTMLElement[] {
  return [
    appsCard(s),
    card(null,
      h('p', {}, h('a', { href: 'mailto:info@stormberry.as', 'data-link': 'email' }, text.missedFeed))),
    card(text.turnOffTitle, h('p', {}, text.turnOffBody),
      h('div', { class: 'actions' }, button(text.turnOff, 'secondary', () => void act(() => (host as AppHost).turnOff())))),
  ];
}

function webScreen(): HTMLElement[] {
  return [card(text.webTitle, h('p', {}, text.webBody), h('p', {}, text.tagline))];
}

function common(): HTMLElement[] {
  return [
    card(text.teachTitle, h('p', {}, text.teachBody)),
    card(text.privacyTitle, ...text.privacyBody.map((p) => h('p', {}, p)), h('p', {}, h('a', { href: 'privacy.html' }, text.privacyMore))),
  ];
}

function draw(): void {
  if (!root) return;
  const screen = screenFor(host ? state : null, ui);
  const status = document.getElementById('status');
  if (status) status.textContent = state && host ? statusLine(state) : '';
  let parts: HTMLElement[];
  switch (screen) {
    case 'web': parts = webScreen(); break;
    case 'intro': parts = introScreen(); break;
    case 'disclosure': parts = disclosureScreen(state as State); break;
    case 'setup': parts = setupScreen(state as State); break;
    case 'home': parts = homeScreen(state as State); break;
  }
  root.replaceChildren(...parts, ...(screen === 'disclosure' ? [] : common()));
  root.setAttribute('data-screen', screen);
  if (screen === 'disclosure') document.getElementById('disclosure-title')?.focus();
}

// Footer and in-page links: inside the app every link is an ID of the app's fixed list, never an
// address; the website follows the address as usual.
document.addEventListener('click', (e) => {
  if (!host || !(e.target instanceof Element)) return;
  const a = e.target.closest('a[data-link]');
  if (!a) return;
  e.preventDefault();
  const id = a.getAttribute('data-link');
  if (id) void act(() => host.openLink(id));
});

if (host) host.onState((raw) => {
  const next = parseState(raw);
  if (next) {
    state = next;
    draw();
  }
});
const tagline = document.getElementById('tagline');
if (tagline) tagline.textContent = text.tagline;
void refresh();
