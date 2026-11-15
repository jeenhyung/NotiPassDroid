#!/usr/bin/env bash
# NotiPassDroid APK Web Server Launcher
cd "$(dirname "$0")"

# Check if APK exists, if not build it
APK_FILE="app/build/outputs/apk/debug/app-debug.apk"
if [ ! -f "$APK_FILE" ]; then
    echo "⚠️ APK 파일이 없습니다. 빌드를 시작합니다..."
    export JAVA_HOME="/opt/homebrew/opt/openjdk@17"
    export PATH="$JAVA_HOME/bin:/opt/homebrew/bin:$PATH"
    ./gradlew assembleDebug
fi

python3 server.py
