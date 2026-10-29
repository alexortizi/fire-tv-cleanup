#!/usr/bin/env bash
# Build the launcher APK, install it on the TV, and make it the home screen.
#
# Branches on manufacturer:
#   Amazon (Fire TV / Fire OS): never disable com.amazon.tv.launcher (it hosts Settings).
#     Enable HomeRedirectService + PhotoDream screensaver instead.
#   Everyone else (Android TV / Google TV): set-home-activity, then disable the stock HOME
#     package (auto-detected, or STOCK_LAUNCHER=... / STOCK_LAUNCHER=none).
set -euo pipefail
cd "$(dirname "$0")"
: "${TV_HOST:?set TV_HOST to the TV IP address, e.g. TV_HOST=192.168.1.50 $0}"
export JAVA_HOME="${JAVA_HOME:-/opt/homebrew/opt/openjdk@17}"
# Prefer Android Studio JBR on macOS when present.
if [ -z "${JAVA_HOME:-}" ] || [ ! -x "${JAVA_HOME}/bin/java" ]; then
  if [ -x "/Applications/Android Studio.app/Contents/jbr/Contents/Home/bin/java" ]; then
    JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
  fi
fi
export ANDROID_HOME="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
export ANDROID_HOME="${ANDROID_HOME:-/opt/homebrew/share/android-commandlinetools}"
export PATH="$JAVA_HOME/bin:$PATH"
[ -f local.properties ] || echo "sdk.dir=$ANDROID_HOME" > local.properties
./gradlew assembleDebug -q --console=plain
PY=../tools/venv/bin/python
APK=app/build/outputs/apk/debug/app-debug.apk
"$PY" -I ../tools/b64push.py "$APK" /data/local/tmp/tvhome.apk
MAKER=$("$PY" -I ../tools/tv.py 'getprop ro.product.manufacturer' | tr -d '\r\n')
MODEL=$("$PY" -I ../tools/tv.py 'getprop ro.product.model' | tr -d '\r\n')
echo "Device: manufacturer=$MAKER model=$MODEL"

if [ "$MAKER" = "Amazon" ]; then
  printf '%s\n' \
    "pm install -r /data/local/tmp/tvhome.apk" \
    "rm -f /data/local/tmp/tvhome.apk" \
    "pm grant com.gathin.tvhome android.permission.WRITE_SECURE_SETTINGS" \
    "settings put secure enabled_accessibility_services com.gathin.tvhome/com.gathin.tvhome.HomeRedirectService" \
    "settings put secure accessibility_enabled 1" \
    "settings put secure screensaver_components com.gathin.tvhome/com.gathin.tvhome.PhotoDream" \
    "am start -n com.gathin.tvhome/.MainActivity" \
    | "$PY" -I ../tools/tv.py --batch
  echo "Installed on Fire TV. Amazon Home stays enabled; HomeRedirectService catches Home."
  echo "Expect a short flash of Amazon Home — that is normal. Gear: Show Hidden / Switch home."
  exit 0
fi

# Android TV / Google TV
KNOWN="com.google.android.tvlauncher com.google.android.apps.tv.launcherx com.google.android.leanbacklauncher"
if [ -z "${STOCK_LAUNCHER:-}" ]; then
  HOME_PKG=$("$PY" -I ../tools/tv.py \
    'cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME' \
    | tail -n 1 | tr -d '\r' | cut -d/ -f1) || HOME_PKG=""
  if [ "$HOME_PKG" = "com.gathin.tvhome" ]; then
    STOCK_LAUNCHER=none
  elif case " $KNOWN " in *" $HOME_PKG "*) true;; *) false;; esac; then
    STOCK_LAUNCHER=$HOME_PKG
  else
    CLAIMANTS=$("$PY" -I ../tools/tv.py \
      'cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.HOME' \
      | tr -d '\r' || true)
    STOCK_LAUNCHER=""
    for cand in $KNOWN; do
      echo "$CLAIMANTS" | grep -q "$cand/" && STOCK_LAUNCHER=$cand && break
    done
    STOCK_LAUNCHER="${STOCK_LAUNCHER:-com.google.android.tvlauncher}"
    echo "note: resolved HOME was '${HOME_PKG:-unknown}'; will disable $STOCK_LAUNCHER (override with STOCK_LAUNCHER=...)"
  fi
fi
echo "Stock launcher: $STOCK_LAUNCHER"

printf '%s\n' \
  "pm install -r /data/local/tmp/tvhome.apk" \
  "rm -f /data/local/tmp/tvhome.apk" \
  "am start -W -n com.gathin.tvhome/.MainActivity" \
  "cmd package set-home-activity --user 0 com.gathin.tvhome/.MainActivity" \
  | "$PY" -I ../tools/tv.py --batch

if [ "$STOCK_LAUNCHER" != "none" ]; then
  "$PY" -I ../tools/tv.py "pm disable-user --user 0 $STOCK_LAUNCHER"
  "$PY" -I ../tools/tv.py "cmd package set-home-activity --user 0 com.gathin.tvhome/.MainActivity"
fi

HOME_NOW=$("$PY" -I ../tools/tv.py \
  'cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME' \
  | tail -n 1 | tr -d '\r')
echo "Home now: $HOME_NOW"
case "$HOME_NOW" in
  com.gathin.tvhome/*)
    echo "Installed. Press Home on the remote to see it."
    ;;
  *)
    echo "WARNING: Home is not our launcher ($HOME_NOW)."
    echo "On Google TV, com.google.android.tungsten.setupwraith/.RecoveryActivity often steals Home"
    echo "after the stock launcher is disabled. Forget+rejoin Wi-Fi if setupwraith owned the network,"
    echo "then: TV_HOST=$TV_HOST ../tools/tv.py 'pm disable-user --user 0 com.google.android.tungsten.setupwraith'"
    echo "and re-run set-home-activity. See README (Android TV / Google TV)."
    ;;
esac
