#!/usr/bin/env bash
# Compile StopScroll's TypeScript into the JavaScript the page and the Android app load.
# EclipseTracker's tools/build-ts.sh (16 and 17 October 2026), cut down for stage 1: no ?v= cache
# stamps (bump_assets.py arrives with the website in stage 4) and no generated data.
#
#   tools/build-ts.sh           install the pinned compiler (npm ci, scripts off), type-check
#                               ts/core/ without the DOM and the tests, compile ts/**/*.ts into the
#                               .js of the same path at the repository root (ts/app.ts -> app.js,
#                               ts/core/x.ts -> core/x.js) and android-bridge.ts into
#                               android/web-overlay/android-bridge.js
#   tools/build-ts.sh --force   the same, but overwrite a compiled .js even when it holds a hand edit
#   tools/build-ts.sh --check   change nothing: fail when a compiled .js is missing, is not tracked by
#                               git, is not byte for byte what its TypeScript compiles to, or when
#                               app.js's folder or core/ holds a .js that no source compiles to
#
# The "in TS" rule (Marcos, 2026-10-17): no hand-written JavaScript anywhere. A build never
# overwrites a compiled .js silently: it writes only when the .js now there is what the committed
# TypeScript (HEAD) or the current TypeScript compiles to; anything else is a hand edit, and the
# build stops. Port the edit into ts/, or rerun with --force to drop it.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

case "$#:${1:-}" in
  0:)        MODE=build ;;
  1:--force) MODE=force ;;
  1:--check) MODE=check ;;
  *)         echo "usage: $0 [--check | --force]" >&2; exit 2 ;;
esac

die() { echo "build-ts: $*" >&2; exit 1; }
command -v node >/dev/null || die "node not found"
command -v npm >/dev/null || die "npm not found"
GIT=0
if command -v git >/dev/null && [ "$(git rev-parse --show-toplevel 2>/dev/null)" = "$ROOT" ]; then GIT=1; fi

npm ci --ignore-scripts --no-audit --no-fund --prefer-offline --loglevel=error >/dev/null \
  || die "npm ci failed (package.json and package-lock.json must agree; nothing was compiled)"
TSC=(node "$ROOT/node_modules/typescript/bin/tsc")
echo "build-ts: TypeScript $("${TSC[@]}" --version | sed 's/^Version //')"

tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT
sha() { sha256sum "$1" | cut -d' ' -f1; }

"${TSC[@]}" -p "$ROOT/tsconfig.core.json" || die "ts/core/ uses something only a browser has; nothing was written"
"${TSC[@]}" -p "$ROOT/tests/tsconfig.json" || die "the tests (tests/*.ts) do not type-check; nothing was written"

compile() { # $1: a checkout root, $2: the output folder
  "${TSC[@]}" -p "$1/tsconfig.json" --outDir "$2" >/dev/null || return 1
  "${TSC[@]}" -p "$1/android/web-overlay/tsconfig.json" --noEmit false --outDir "$2.bridge" >/dev/null || return 1
  mkdir -p "$2/android/web-overlay"
  cp "$2.bridge/android/web-overlay/android-bridge.js" "$2/android/web-overlay/android-bridge.js"
}
compile "$ROOT" "$tmp/out" || { "${TSC[@]}" -p "$ROOT/tsconfig.json" --noEmit; die "the TypeScript does not compile; nothing was written"; }

BRIDGE=android/web-overlay/android-bridge.js
mapfile -t OUTPUTS < <(cd "$tmp/out" && find . -type f -name '*.js' -printf '%P\n' | LC_ALL=C sort)
mapfile -t EXPECTED < <( { cd ts && find . -type f -name '*.ts' ! -name '*.d.ts' -printf '%P\n' | sed 's/\.ts$/.js/'; echo "$BRIDGE"; } | LC_ALL=C sort)
[ "${OUTPUTS[*]}" = "${EXPECTED[*]}" ] || die "tsc emitted [${OUTPUTS[*]}] but the sources are [${EXPECTED[*]}]"

tracked() { [ "$GIT" = 1 ] && git ls-files --error-unmatch -- "$1" >/dev/null 2>&1; }

if [ "$MODE" = check ]; then
  bad=0
  for f in "${OUTPUTS[@]}"; do
    if [ ! -f "$f" ]; then echo "build-ts: $f is missing: run tools/build-ts.sh" >&2; bad=1
    elif [ "$(sha "$f")" != "$(sha "$tmp/out/$f")" ]; then echo "build-ts: $f is NOT what its TypeScript compiles to" >&2; bad=1
    elif [ "$GIT" = 1 ] && ! tracked "$f"; then echo "build-ts: $f is not tracked by git: git add it" >&2; bad=1
    else echo "build-ts: $f matches its TypeScript"; fi
  done
  mapfile -t PRESENT < <({ find . -maxdepth 1 -type f -name '*.js' -printf '%P\n'; [ -d core ] && find core -type f -name '*.js'; } | LC_ALL=C sort)
  for f in "${PRESENT[@]}"; do
    case " ${OUTPUTS[*]} " in *" $f "*) ;; *) echo "build-ts: $f has no TypeScript source: delete it" >&2; bad=1 ;; esac
  done
  [ "$bad" = 0 ] || exit 1
  echo "build-ts: check passed"
  exit 0
fi

if [ "$MODE" = build ] && [ "$GIT" = 1 ] && git cat-file -e HEAD:tsconfig.json 2>/dev/null; then
  mkdir -p "$tmp/head"
  git archive HEAD tsconfig.json ts android/web-overlay/tsconfig.json android/web-overlay/android-bridge.ts 2>/dev/null | tar -x -C "$tmp/head" || true
  ln -s "$ROOT/node_modules" "$tmp/head/node_modules"
  compile "$tmp/head" "$tmp/head-out" 2>/dev/null || true
  hand=()
  for f in "${OUTPUTS[@]}"; do
    [ -f "$f" ] || continue
    cur="$(sha "$f")"
    [ "$cur" = "$(sha "$tmp/out/$f")" ] && continue
    [ -f "$tmp/head-out/$f" ] && [ "$cur" = "$(sha "$tmp/head-out/$f")" ] && continue
    hand+=("$f")
  done
  if [ "${#hand[@]}" -gt 0 ]; then
    printf 'build-ts: %s holds changes no TypeScript source produced (edited by hand?)\n' "${hand[@]}" >&2
    die "nothing was written. Port the change into its TypeScript, or run tools/build-ts.sh --force."
  fi
fi

wrote=0
for f in "${OUTPUTS[@]}"; do
  if [ -f "$f" ] && [ "$(sha "$f")" = "$(sha "$tmp/out/$f")" ]; then continue; fi
  mkdir -p "$(dirname "$f")"
  cp "$tmp/out/$f" "$f"
  echo "build-ts: wrote $f"
  wrote=1
done
[ "$wrote" = 1 ] || echo "build-ts: compiled output unchanged"
