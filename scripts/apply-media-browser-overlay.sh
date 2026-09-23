#!/usr/bin/env bash

set -euo pipefail

if [[ $# -ne 1 ]]; then
    echo "usage: $0 /path/to/brave-browser/src" >&2
    exit 2
fi

chromium_src="$(cd "$1" && pwd)"
brave_src="$chromium_src/brave"
repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
overlay_root="$repo_root/media-browser/overlay/brave"
brave_patch="$repo_root/media-browser/patches/brave-core-v1.95.104.patch"
chromium_patch="$repo_root/media-browser/patches/chromium-153.0.8010.53.patch"

if [[ ! -d "$brave_src/.git" ]]; then
    echo "expected a Brave checkout at $brave_src" >&2
    exit 1
fi

expected_tag="v1.95.104"
actual_tag="$(git -C "$brave_src" describe --tags --exact-match 2>/dev/null || true)"
if [[ "$actual_tag" != "$expected_tag" ]]; then
    echo "expected brave-core $expected_tag, found ${actual_tag:-an untagged revision}" >&2
    exit 1
fi

version_file="$chromium_src/chrome/VERSION"
if [[ ! -f "$version_file" ]]; then
    echo "expected Chromium version metadata at $version_file" >&2
    exit 1
fi
actual_chromium_version="$(
    awk -F= '
        /^(MAJOR|MINOR|BUILD|PATCH)=/ { values[$1]=$2 }
        END { print values["MAJOR"] "." values["MINOR"] "." values["BUILD"] "." values["PATCH"] }
    ' "$version_file"
)"
if [[ "$actual_chromium_version" != "153.0.8010.53" ]]; then
    echo "expected Chromium 153.0.8010.53, found $actual_chromium_version" >&2
    exit 1
fi

if git -C "$brave_src" apply --reverse --check "$brave_patch" 2>/dev/null &&
    git -C "$chromium_src" apply --reverse --check "$chromium_patch" 2>/dev/null; then
    mkdir -p "$brave_src/android/java/com/robpelo/browser"
    cp "$overlay_root/android/java/com/robpelo/browser/"*.java \
        "$brave_src/android/java/com/robpelo/browser/"
    echo "The RobPelo media-browser overlay is already applied."
    exit 0
fi

git -C "$brave_src" apply --check "$brave_patch"
git -C "$chromium_src" apply --check "$chromium_patch"

mkdir -p "$brave_src/android/java/com/robpelo/browser"
cp "$overlay_root/android/java/com/robpelo/browser/"*.java \
    "$brave_src/android/java/com/robpelo/browser/"

git -C "$brave_src" apply "$brave_patch"
git -C "$chromium_src" apply "$chromium_patch"
echo "Applied the RobPelo media-browser overlay."
echo "Build with chrome_public_manifest_package=\"com.robpelo.browser\"."
