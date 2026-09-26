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
chromium_manifest_patch="$repo_root/media-browser/patches/chromium-153.0.8010.53.patch"
chromium_external_intents_patch="$repo_root/media-browser/patches/chromium-external-intents-153.0.8010.53.patch"

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

version_file="$brave_src/package.json"
if [[ ! -f "$version_file" ]]; then
    echo "expected Brave version metadata at $version_file" >&2
    exit 1
fi
actual_chromium_version="$(
    python3 -c \
        'import json, sys; print(json.load(open(sys.argv[1]))["config"]["projects"]["chrome"]["tag"])' \
        "$version_file"
)"
if [[ "$actual_chromium_version" != "153.0.8010.53" ]]; then
    echo "expected Chromium 153.0.8010.53, found $actual_chromium_version" >&2
    exit 1
fi

apply_patch_if_needed() {
    local source_root="$1"
    local patch_file="$2"
    if git -C "$source_root" apply --reverse --check "$patch_file" 2>/dev/null; then
        echo "Already applied: $(basename "$patch_file")"
        return
    fi
    if ! git -C "$source_root" apply --check "$patch_file"; then
        echo "Patch cannot be applied cleanly: $patch_file" >&2
        exit 1
    fi
    git -C "$source_root" apply "$patch_file"
    echo "Applied: $(basename "$patch_file")"
}

mkdir -p "$brave_src/android/java/com/robpelo/browser"
cp "$overlay_root/android/java/com/robpelo/browser/"*.java \
    "$brave_src/android/java/com/robpelo/browser/"

apply_patch_if_needed "$brave_src" "$brave_patch"
apply_patch_if_needed "$chromium_src" "$chromium_manifest_patch"
apply_patch_if_needed "$chromium_src" "$chromium_external_intents_patch"
echo "Applied the RobPelo media-browser overlay."
echo "Build with chrome_public_manifest_package=\"com.robpelo.browser\"."
