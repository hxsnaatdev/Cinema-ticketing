#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")"

JAVA="java"
JAVAC="javac"
case "$(uname -s)" in
    MINGW*|MSYS*|CYGWIN*) JAVA="java.exe"; JAVAC="javac.exe" ;;
esac

if ! command -v "$JAVAC" >/dev/null 2>&1; then
    echo "Error: $JAVAC not found. Install JDK 17 or newer and add it to PATH." >&2
    exit 1
fi

rm -rf out
mkdir -p out

find src -name '*.java' -print0 | xargs -0 "$JAVAC" --release 17 -d out
"$JAVA" -cp out ticketbooking.Main
