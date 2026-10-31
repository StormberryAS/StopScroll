// The guard tests (spec 6.3, 6.5, 8, 14.6): what the code may touch, press and ask for, checked
// in the source, so a change that widens StopScroll's reach fails the build instead of shipping.
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { existsSync } from 'node:fs';
import { join } from 'node:path';
import { kotlinMain, read, ROOT, stripComments, tracked } from './helpers.ts';

const kotlin = kotlinMain();
const only = (re: RegExp): string[] => kotlin.filter((f) => re.test(f.code)).map((f) => f.path.split('/').pop() ?? f.path);

test('no hand-written JavaScript: every .js is compiled from TypeScript and marked linguist-generated', () => {
  const attrs = read('.gitattributes');
  const js = tracked().filter((p) => /\.(?:js|mjs|cjs)$/.test(p) && !p.startsWith('node_modules/'));
  assert.ok(js.length >= 4, 'the compiled files are there');
  for (const p of js) {
    assert.ok(p.endsWith('.js'), `${p}: no .mjs or .cjs`);
    const source = p === 'android/web-overlay/android-bridge.js' ? 'android/web-overlay/android-bridge.ts' : `ts/${p.replace(/\.js$/, '.ts')}`;
    assert.ok(existsSync(join(ROOT, source)), `${p} has no TypeScript source (${source})`);
    const dir = p.includes('/') ? `/${p.slice(0, p.lastIndexOf('/'))}/*.js` : `/${p}`;
    assert.ok(attrs.includes(`/${p} linguist-generated=true`) || attrs.includes(`${dir} linguist-generated=true`), `${p} is not marked linguist-generated`);
  }
});

// Every way to read text from a node, an event or a window: the Kotlin property form (.text) and the
// Java getter form (getText()), in any case, plus the lookups and types that carry text.
const TEXT_STEMS = 'text|contentDescription|hintText|stateDescription|tooltipText|paneTitle|error|containerTitle|beforeText|extras|parcelableData|title|windows|uniqueId';
const bannedText = new RegExp(`(?:\\.|\\bget)(?:${TEXT_STEMS})\\b|findAccessibilityNodeInfosByText|AccessibilityWindowInfo|AccessibilityRecord|\\bgetRecord\\b`, 'i');
// Printing a node or an event prints its text: no toString() and no string template on one.
const printsNode = /\.toString\(\)|\$\{?(?:node|root|child|parent|target|event|look|it)\b/;
const ALLOWED_TO_STRING = /(?:packageName|className)\?\.toString\(\)/g;

test('the text guard catches every spelling it is meant to catch', () => {
  const mustCatch = [
    'node.text', 'node.getText()', 'node.contentDescription', 'node.getContentDescription()', 'node.hintText', 'node.getHintText()',
    'node.stateDescription', 'node.getStateDescription()', 'node.tooltipText', 'node.getTooltipText()', 'node.paneTitle',
    'node.getPaneTitle()', 'node.error', 'node.getError()', 'node.containerTitle', 'node.getContainerTitle()',
    'root.findAccessibilityNodeInfosByText("x")', 'event.beforeText', 'event.getBeforeText()', 'event.text', 'event.getText()',
    'event.parcelableData', 'event.getParcelableData()', 'node.extras', 'node.getExtras()', 'window.title', 'window.getTitle()',
    'windows', 'getWindows()', 'AccessibilityWindowInfo', 'event.getRecord(0)', 'node.uniqueId',
  ];
  for (const sample of mustCatch) {
    const code = sample === 'windows' ? 'service.windows' : sample;
    assert.match(code, bannedText, `the guard misses ${sample}`);
  }
  for (const sample of ['"$node"', '"${event.packageName}"', 'node.toString()', '"$root"']) assert.match(sample, printsNode, `the guard misses ${sample}`);
  for (const fine of [
    'Toast.makeText(applicationContext, text, Toast.LENGTH_SHORT)', 'private fun toast(text: String)', 'context', 'error("bad")',
    'label.setTextSize(TypedValue.COMPLEX_UNIT_SP, NoticeStyle.TEXT_SP)', 'label.setTextColor(palette.textColour)',
  ]) {
    assert.doesNotMatch(fine, bannedText, `false alarm on ${fine}`);
  }
});

test('no screen-text API anywhere in the Kotlin code (stage 1 reads no text at all)', () => {
  // NoticeWindow.kt is pinned on its own below: it writes StopScroll's own message and reads nothing.
  assert.deepEqual(only(bannedText).filter((f) => f !== 'NoticeWindow.kt'), []);
  for (const f of kotlin.filter((k) => /\/(?:NodeFacts|FeedService)\.kt$/.test(k.path))) {
    assert.doesNotMatch(f.code.replace(ALLOWED_TO_STRING, ''), printsNode, `${f.path}: prints a node or an event`);
  }
});

// The on-screen message (decision D2, 30 October 2026; it replaced the Toast, which Android drops for an
// app without notifications). NoticeWindow.kt is the one file that puts words on the screen: it may assign
// text to its own view and nothing else, it never sees another app's screen, and its window is an
// Accessibility overlay (no permission) that can never take the focus or a tap.
test('the on-screen message writes its own words, reads none, and can never take a tap', () => {
  const nw = kotlin.find((f) => f.path.endsWith('/NoticeWindow.kt'))?.code ?? '';
  assert.ok(nw.length > 0, 'NoticeWindow.kt is there');
  const hits = [...nw.matchAll(new RegExp(bannedText.source, 'gi'))].map((m) => nw.slice(m.index).split('\n')[0]);
  assert.deepEqual(hits, ['.text = message'], 'the only text API is the assignment of its own message');
  assert.doesNotMatch(nw, /AccessibilityEvent|AccessibilityNodeInfo|AccessibilityRecord|announceForAccessibility|sendAccessibilityEvent/);
  assert.match(nw, /WindowManager\.LayoutParams\.TYPE_ACCESSIBILITY_OVERLAY,/);
  assert.deepEqual([...nw.matchAll(/LayoutParams\.FLAG_[A-Z_]+/g)].map((m) => m[0]), ['LayoutParams.FLAG_NOT_FOCUSABLE', 'LayoutParams.FLAG_NOT_TOUCHABLE']);
  assert.deepEqual(only(/WindowManager|TYPE_ACCESSIBILITY_OVERLAY/), ['NoticeWindow.kt'], 'only NoticeWindow.kt adds a window');
  assert.deepEqual(only(/TYPE_APPLICATION_OVERLAY|TYPE_SYSTEM_[A-Z_]+|TYPE_TOAST|SYSTEM_ALERT_WINDOW|canDrawOverlays|POST_NOTIFICATIONS/), []);
  assert.deepEqual(only(/\bToast\b/), ['MainActivity.kt'], 'Toasts only from the app screen in front, never from the service');
});

test('the Accessibility cache is kept off, so other apps\' events never reach the process', () => {
  assert.deepEqual(only(/setCacheEnabled\(\s*true/), []);
  const nf = kotlin.find((f) => f.path.endsWith('NodeFacts.kt'))?.code ?? '';
  for (const fn of ['look', 'pressOtherTab']) {
    assert.match(nf, new RegExp(`fun ${fn}\\([^)]*\\)[^=]*=\\s*try \\{[^}]*\\} finally \\{\\s*service\\.setCacheEnabled\\(false\\)\\s*\\}`), `${fn} clears the cache flag in a finally block`);
  }
  // Every other function that fetches a node runs only through those two wrappers.
  const fetchers = [...nf.matchAll(/fun (\w+)\([^)]*\)[^{=]*[{=]/g)].map((m) => m[1]);
  assert.deepEqual(fetchers.sort(), ['firstClickable', 'fact', 'look', 'lookOnce', 'pressOnce', 'pressOtherTab'].sort());
  assert.match(nf, /private fun lookOnce/);
  assert.match(nf, /private fun pressOnce/);
  const svc = kotlin.find((f) => f.path.endsWith('FeedService.kt'))?.code ?? '';
  assert.match(svc, /override fun onServiceConnected\(\) \{[\s\S]*?setCacheEnabled\(false\)[\s\S]*?applyPlan\(\)\s*\}/, 'off at connect');
  assert.match(svc, /if \(!next\.active\) \{\n(?:[^\n]*\n){0,3}?\s*setCacheEnabled\(false\)\n\s*\}/, 'off whenever the plan is not active');
});

test('only NodeFacts.kt touches another app\'s screen, and always without prefetching', () => {
  assert.deepEqual(only(/AccessibilityNodeInfo|getRootInActiveWindow|rootInActiveWindow|findAccessibilityNodeInfos|findFocus|getSource\b|\.source\b/), ['NodeFacts.kt']);
  const nf = kotlin.find((f) => f.path.endsWith('NodeFacts.kt'))?.code ?? '';
  const roots = [...nf.matchAll(/getRootInActiveWindow\(([^)]*)\)/g)].map((m) => m[1]);
  assert.ok(roots.length >= 1);
  assert.deepEqual([...new Set(roots)], ['0'], 'getRootInActiveWindow(0): no prefetching');
  for (const m of nf.matchAll(/getChild\(([^)]*)\)/g)) assert.match(m[1] ?? '', /,\s*0$/, 'getChild without prefetching');
});

test('the service can press only Back, Home and the one tab click', () => {
  const globals = kotlin.flatMap((f) => [...f.code.matchAll(/performGlobalAction\(([^)]*)\)/g)].map((m) => `${f.path.split('/').pop()}:${m[1]}`));
  assert.deepEqual(globals.sort(), ['FeedService.kt:GLOBAL_ACTION_BACK', 'FeedService.kt:GLOBAL_ACTION_HOME']);
  const actions = kotlin.flatMap((f) => [...f.code.matchAll(/performAction\(([^)]*)\)/g)].map((m) => `${f.path.split('/').pop()}:${m[1]}`));
  assert.deepEqual(actions, ['NodeFacts.kt:AccessibilityNodeInfo.ACTION_CLICK']);
  assert.deepEqual(only(/dispatchGesture|ACTION_SET_TEXT|ACTION_PASTE|ACTION_SCROLL|ACTION_LONG_CLICK|ACTION_SET_SELECTION|ACTION_CUT|ACTION_COPY|takeScreenshot|GestureDescription|setText\(/), []);
  const otherGlobals = kotlin.flatMap((f) => [...f.code.matchAll(/GLOBAL_ACTION_[A-Z_]+/g)].map((m) => m[0])).filter((g) => g !== 'GLOBAL_ACTION_BACK' && g !== 'GLOBAL_ACTION_HOME');
  assert.deepEqual(otherGlobals, []);
  assert.deepEqual(only(/startActivity\(/), ['MainActivity.kt'], 'only the activity starts anything (settings and the fixed links)');
});

test('the package list is never rebuilt: no new AccessibilityServiceInfo, no empty or null list', () => {
  assert.deepEqual(only(/AccessibilityServiceInfo\s*\(/), []);
  assert.deepEqual(only(/packageNames\s*=\s*(?:null|emptyArray|arrayOf\(\s*\))/), []);
  const svc = kotlin.find((f) => f.path.endsWith('FeedService.kt'))?.code ?? '';
  assert.match(svc, /val info = serviceInfo \?: return/, 'the plan is applied to the current info');
  assert.match(svc, /info\.packageNames = next\.packages\.toTypedArray\(\)/);
});

test('no logging, no network code, no JavaScript interface', () => {
  assert.deepEqual(only(/android\.util\.Log\b|\bLog\.[a-z]\(|println\(|printStackTrace/), []);
  assert.deepEqual(only(/HttpURLConnection|java\.net\.URL\b|\bURL\(|Socket\b|okhttp|openConnection/), []);
  assert.deepEqual(only(/addJavascriptInterface/), []);
});

test('the manifest asks for nothing; expected-permissions.txt holds the package line only', () => {
  assert.equal(read('android/expected-permissions.txt'), 'package: no.stormberry.stopscroll\n');
  const m = read('android/app/src/main/AndroidManifest.xml').replace(/<!--[\s\S]*?-->/g, '');
  for (const tag of m.match(/<uses-permission\b[^>]*>/g) ?? []) assert.match(tag, /tools:node="remove"/, tag);
  assert.match(m, /android\.permission\.INTERNET" tools:node="remove"/);
  assert.match(m, /android:allowBackup="false"/);
  assert.match(m, /android:permission="android\.permission\.BIND_ACCESSIBILITY_SERVICE"/);
  assert.doesNotMatch(m, /<permission\b(?![^>]*tools:node="remove")/);
});

test('the service configuration: YouTube and Instagram only, notificationTimeout 0, window content, not a tool', () => {
  const x = read('android/app/src/main/res/xml/accessibility_service_config.xml').replace(/<!--[\s\S]*?-->/g, '');
  assert.match(x, /android:packageNames="com\.google\.android\.youtube,com\.instagram\.android"/);
  assert.match(x, /android:notificationTimeout="0"/);
  assert.match(x, /android:canRetrieveWindowContent="true"/);
  assert.match(x, /android:isAccessibilityTool="false"/);
  assert.match(x, /android:accessibilityFlags="flagReportViewIds"/);
  assert.doesNotMatch(x, /canPerformGestures|canTakeScreenshot|flagRetrieveInteractiveWindows|flagIncludeNotImportantViews|flagRequestFilterKeyEvents/);
  const rules = JSON.parse(read('rules/screen-rules.json')) as { apps: { package: string }[] };
  assert.deepEqual(rules.apps.map((a) => a.package), ['com.google.android.youtube', 'com.instagram.android'], 'the rules and the service list agree');
});

test('the disclosure version is the same on both sides', () => {
  const ts = /DISCLOSURE_VERSION = (\d+);/.exec(read('ts/core/copy.ts'))?.[1];
  const kt = /const val DISCLOSURE_VERSION = (\d+)/.exec(read('android/app/src/main/kotlin/no/stormberry/stopscroll/Consent.kt'))?.[1];
  assert.ok(ts && kt);
  assert.equal(ts, kt);
});

test('the bridge passes link IDs, never addresses', () => {
  const bridge = stripComments(read('android/web-overlay/android-bridge.ts'));
  assert.match(bridge, /openLink: \(id\) => ask\('openLink', \{ link: id \}\)/);
  assert.doesNotMatch(stripComments(read('ts/app.ts')), /openLink\([^)]*(?:href|https?:|mailto:)/);
});

test('the report email reads the package name at run time and carries no screen content', () => {
  const link = stripComments(read('android/app/src/main/kotlin/no/stormberry/stopscroll/LinkList.kt'));
  const email = link.slice(link.indexOf('fun email('), link.indexOf('private fun enc('));
  assert.ok(email.length > 0);
  assert.doesNotMatch(email, /no\.stormberry/, 'LinkList.email never names a package: the caller passes Context.packageName');
  assert.doesNotMatch(email, /Node|getText|contentDescription|rootInActiveWindow/);
  const main = stripComments(read('android/app/src/main/kotlin/no/stormberry/stopscroll/MainActivity.kt'));
  assert.match(main, /LinkList\.email\(appVersion\(\), appVersionCode\(\), packageName, Build\.VERSION\.RELEASE, rules\.apps\.map \{ it\.name to installedVersion\(it\.packageName\) \}\)/);
});

test('the name is never shortened to SS (spec 1)', () => {
  for (const p of tracked()) {
    for (const seg of p.split('/')) assert.doesNotMatch(seg, /^ss(?:[-_.]|$)|(?:^|[-_.])SS(?:[-_.]|$)/, p);
  }
  for (const p of ['ts/app.ts', 'ts/app-host.d.ts', 'android/web-overlay/android-bridge.ts', ...kotlin.map((f) => f.path)]) {
    assert.doesNotMatch(stripComments(read(p)), /\bSS[A-Z]?[a-z]*\b|\bSs[A-Z]/, p);
  }
});

test('the page loads only its compiled module, under a policy that forbids connections', () => {
  for (const page of ['index.html', 'privacy.html']) {
    const html = read(page);
    assert.match(html, /<head>\s*<!--\s*\n\s+\S.* - Sixto-Clementine Vulgate \/ Douay-Rheims\n/, `${page}: the verse comes first in the head`);
    assert.match(html, /connect-src 'none'/);
    assert.doesNotMatch(html.replace(/<!--[\s\S]*?-->/g, ''), /<script(?![^>]*\bsrc=)|\sstyle=|\son[a-z]+=/, `${page}: no inline script, style or handler`);
    assert.match(html, /<html lang="en-GB">/);
  }
  const scripts = read('index.html').match(/<script\b[^>]*>/g) ?? [];
  assert.deepEqual(scripts, ['<script type="module" src="app.js">']);
  assert.equal((read('privacy.html').match(/<script\b/g) ?? []).length, 0);
});
