#!/usr/bin/env bash

set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
classes_dir="$(mktemp -d)"
trap 'rm -rf "$classes_dir"' EXIT

javac \
    -d "$classes_dir" \
    "$repo_root/media-browser/overlay/brave/android/java/com/robpelo/browser/MediaDestination.java" \
    "$repo_root/media-browser/tests/com/robpelo/browser/MediaDestinationTestMain.java"

java -cp "$classes_dir" com.robpelo.browser.MediaDestinationTestMain
