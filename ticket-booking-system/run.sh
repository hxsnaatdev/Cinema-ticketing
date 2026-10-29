#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")"

rm -rf out
mkdir -p out

find src -name '*.java' -print0 | xargs -0 javac --release 17 -d out
java -cp out ticketbooking.Main
