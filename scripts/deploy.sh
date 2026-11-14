#!/usr/bin/env bash
# SilentPaper - one-click deploy on WSL / Linux / macOS:
# build, install, grant usage access, launch.
set -euo pipefail
cd "$(dirname "$0")/.."

SDK_DIR="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"

if [ -z "$SDK_DIR" ]; then
    for candidate in "$HOME/Android/Sdk" "$HOME/Library/Android/sdk" "/opt/android-sdk"; do
        if [ -d "$candidate" ]; then
            SDK_DIR="$candidate"
            break
        fi
    done
fi

if [ -z "$SDK_DIR" ]; then
    echo "[ERROR] Android SDK not found. Please set ANDROID_HOME and retry."
    exit 1
fi

echo "[INFO] Android SDK: $SDK_DIR"
export ANDROID_HOME="$SDK_DIR"
export ANDROID_SDK_ROOT="$SDK_DIR"

./gradlew assembleDebug --no-daemon

APK="app/build/outputs/apk/debug/app-debug.apk"
ADB="$SDK_DIR/platform-tools/adb"
if [ ! -x "$ADB" ]; then
    ADB="adb"
fi

echo "[INFO] Installing $APK ..."
"$ADB" install -r "$APK"

echo "[INFO] Granting usage-stats permission ..."
"$ADB" shell appops set com.silent.paper GET_USAGE_STATS allow

echo "[INFO] Launching SilentPaper ..."
"$ADB" shell am start -n com.silent.paper/.LauncherActivity

echo "[OK] Deployed."
