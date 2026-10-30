"use strict";
/*! android-bridge.js is compiled from android-bridge.ts beside it by tools/build-ts.sh. Edit the
    .ts, never the .js. */
(function () {
    'use strict';
    const bridge = window.StopScrollBridge;
    if (!bridge || typeof bridge.postMessage !== 'function')
        return;
    const channel = bridge;
    let nextId = 1;
    const waiting = new Map();
    const listeners = [];
    const TIMEOUT_MS = 10000;
    channel.addEventListener('message', (event) => {
        let msg;
        try {
            msg = JSON.parse(String(event.data));
        }
        catch {
            return;
        }
        if (typeof msg !== 'object' || msg === null)
            return;
        const m = msg;
        if (m.v !== 1)
            return;
        const id = typeof m.id === 'number' ? m.id : null;
        const done = id !== null ? waiting.get(id) : undefined;
        if (m.type === 'state') {
            if (done && id !== null) {
                waiting.delete(id);
                done({ ok: true, state: m.state });
            }
            else {
                listeners.forEach((l) => l(m.state));
            }
        }
        else if (m.type === 'result' && done && id !== null) {
            waiting.delete(id);
            done(typeof m.error === 'string' ? { ok: m.ok === true, error: m.error } : { ok: m.ok === true });
        }
    });
    function ask(type, fields = {}) {
        const id = nextId++;
        return new Promise((resolve) => {
            waiting.set(id, resolve);
            setTimeout(() => {
                if (waiting.delete(id))
                    resolve({ ok: false, error: 'timeout' });
            }, TIMEOUT_MS);
            channel.postMessage(JSON.stringify({ ...fields, v: 1, type, id }));
        });
    }
    const host = {
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
