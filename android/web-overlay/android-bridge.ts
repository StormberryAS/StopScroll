/*! android-bridge.js is compiled from android-bridge.ts beside it by tools/build-ts.sh. Edit the
    .ts, never the .js. */

/* StopScroll for Android: the bridge between the page and the app (spec 5, "Bridge").
 *
 * This file exists ONLY inside the APK: the build copies the web app byte for byte and adds one line
 * to index.html that loads this script before the app module. It sets window.StopScrollApp (the
 * contract is ts/app-host.d.ts), which the page uses for everything that needs the app.
 *
 * The channel is window.StopScrollBridge, injected by the app through
 * WebViewCompat.addWebMessageListener ONLY into pages from https://appassets.androidplatform.net.
 * It carries JSON text only, and the app checks every message again (Bridge.kt). The page passes
 * IDs and fixed words, never an address: openLink takes an ID of the app's fixed link list.
 *
 *   page to app   { v: 1, type, id, ...fields }
 *   app to page   { v: 1, type: 'result', id, ok, error? } | { v: 1, type: 'state', id?, state }
 *
 * A classic script, not a module: no import or export. The types it shares with the page come in
 * as import() types, which leave nothing behind.
 */

type AppHost = import('../../ts/app-host.js').AppHost;
type HostResult = import('../../ts/app-host.js').HostResult;

interface WebMessagePort {
  postMessage(message: string): void;
  addEventListener(type: 'message', listener: (event: { readonly data: string }) => void): void;
}

interface Window {
  readonly StopScrollBridge?: WebMessagePort;
}

(function () {
  'use strict';

  const bridge = window.StopScrollBridge;
  if (!bridge || typeof bridge.postMessage !== 'function') return;
  const channel: WebMessagePort = bridge;

  let nextId = 1;
  const waiting = new Map<number, (r: HostResult) => void>();
  const listeners: ((state: unknown) => void)[] = [];
  const TIMEOUT_MS = 10_000;

  channel.addEventListener('message', (event) => {
    let msg: unknown;
    try {
      msg = JSON.parse(String(event.data));
    } catch {
      return;
    }
    if (typeof msg !== 'object' || msg === null) return;
    const m = msg as { v?: unknown; type?: unknown; id?: unknown; ok?: unknown; error?: unknown; state?: unknown };
    if (m.v !== 1) return;
    const id = typeof m.id === 'number' ? m.id : null;
    const done = id !== null ? waiting.get(id) : undefined;
    if (m.type === 'state') {
      if (done && id !== null) {
        waiting.delete(id);
        done({ ok: true, state: m.state });
      } else {
        listeners.forEach((l) => l(m.state));
      }
    } else if (m.type === 'result' && done && id !== null) {
      waiting.delete(id);
      done(typeof m.error === 'string' ? { ok: m.ok === true, error: m.error } : { ok: m.ok === true });
    }
  });

  function ask(type: string, fields: Record<string, string | number> = {}): Promise<HostResult> {
    const id = nextId++;
    return new Promise((resolve) => {
      waiting.set(id, resolve);
      setTimeout(() => {
        if (waiting.delete(id)) resolve({ ok: false, error: 'timeout' });
      }, TIMEOUT_MS);
      channel.postMessage(JSON.stringify({ ...fields, v: 1, type, id }));
    });
  }

  const host: AppHost = {
    getState: () => ask('getState'),
    setMode: (appPackage, mode) => ask('setMode', { app: appPackage, mode }),
    recordConsent: (version) => ask('recordConsent', { version }),
    openAccessibilitySettings: () => ask('openAccessibilitySettings'),
    openAppInfo: () => ask('openAppInfo'),
    turnOff: () => ask('turnOff'),
    openLink: (id) => ask('openLink', { link: id }),
    onState: (listener) => {
      listeners.push(listener);
    },
  };
  window.StopScrollApp = host;
})();
