// The contract between the page (ts/app.ts) and the Android app's bridge script
// (android/web-overlay/android-bridge.ts), in one place so both sides are type-checked against it.
// Types only: a declaration file, so tsc emits nothing for it.

/** The answer to a request: ok, an error word from the app, and for getState the raw state. */
export interface HostResult {
  readonly ok: boolean;
  readonly error?: string;
  readonly state?: unknown;
}

/**
 * What the Android app's bridge script offers the page, as window.StopScrollApp. Absent on the
 * website. Every value the page passes is checked again in Kotlin (Bridge.kt); the page passes IDs
 * and fixed words only, never an address.
 */
export interface AppHost {
  getState(): Promise<HostResult>;
  setMode(appPackage: string, mode: 'off' | 'always'): Promise<HostResult>;
  recordConsent(version: number): Promise<HostResult>;
  openAccessibilitySettings(): Promise<HostResult>;
  openAppInfo(): Promise<HostResult>;
  turnOff(): Promise<HostResult>;
  openLink(id: string): Promise<HostResult>;
  /** Called with every state the app sends unasked (when the app comes back to the front, or a setting changed). */
  onState(listener: (state: unknown) => void): void;
}

declare global {
  interface Window {
    StopScrollApp?: AppHost;
  }
}
