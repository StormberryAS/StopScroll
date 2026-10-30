#!/usr/bin/env bash
# Checks a built APK against android/expected-permissions.txt, byte for byte, in both directions,
# and prints its SHA-256 (EclipseTracker's tool). A debug APK's package line carries the .debug
# suffix, which is allowed. The Gradle task check<Variant>ApkPermissions runs the same comparison
# after every assemble; this script is for an APK from anywhere.
#
#   android/tools/check-apk.sh android/app/build/outputs/apk/release/app-release-unsigned.apk
set -euo pipefail
here="$(cd "$(dirname "$0")/.." && pwd)"
apk="$1"
sdk="${ANDROID_HOME:-$(sed -n 's/^sdk.dir=//p' "$here/local.properties" 2>/dev/null)}"
aapt="$(ls -d "$sdk"/build-tools/*/aapt 2>/dev/null | sort -V | tail -1)"
[ -x "$aapt" ] || { echo "check-apk: no aapt under $sdk/build-tools" >&2; exit 2; }
got="$("$aapt" dump permissions "$apk" | sed 's/^package: no\.stormberry\.stopscroll\.debug$/package: no.stormberry.stopscroll/')"
if ! diff <(printf '%s\n' "$got") "$here/expected-permissions.txt"; then
  echo "check-apk: $apk does not ask for exactly the permissions in expected-permissions.txt" >&2
  exit 1
fi
echo "check-apk: no permissions, exactly as expected-permissions.txt"
sha256sum "$apk"
