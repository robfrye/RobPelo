#!/usr/bin/env bash

set -euo pipefail

if [[ $# -lt 1 || $# -gt 2 ]]; then
    echo "usage: $0 /path/to/media-browser.apk [/path/to/robpelo.apk]" >&2
    exit 2
fi

media_apk="$(cd "$(dirname "$1")" && pwd)/$(basename "$1")"
companion_apk=""
if [[ $# -eq 2 ]]; then
    companion_apk="$(cd "$(dirname "$2")" && pwd)/$(basename "$2")"
fi

expected_package="com.robpelo.browser"
expected_version_name="1.95.104"
minimum_version_code=429510404
maximum_min_sdk=30
expected_abi="arm64-v8a"
expected_signer="bcf1ccee28e04a3efd8a0b85468db5fc62ccd2371c709d61d7accd06a5f0f9b9"

if [[ ! -f "$media_apk" ]]; then
    echo "Media-browser APK not found: $media_apk" >&2
    exit 1
fi
if [[ -n "$companion_apk" && ! -f "$companion_apk" ]]; then
    echo "RobPelo APK not found: $companion_apk" >&2
    exit 1
fi

for command_name in java python3 shasum; do
    if ! command -v "$command_name" >/dev/null 2>&1; then
        echo "Required command not found: $command_name" >&2
        exit 1
    fi
done

sdk_root="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}"
if [[ -z "$sdk_root" ]]; then
    echo "Set ANDROID_HOME or ANDROID_SDK_ROOT to an Android SDK installation." >&2
    exit 1
fi

latest_tool() {
    local root="$1"
    local name="$2"
    find "$root" -type f -name "$name" -perm -111 2>/dev/null |
        sort -V |
        tail -1
}

apksigner="$(latest_tool "$sdk_root/build-tools" apksigner)"
aapt2="$(latest_tool "$sdk_root/build-tools" aapt2)"
apkanalyzer="$(latest_tool "$sdk_root/cmdline-tools" apkanalyzer)"

for tool_path in "$apksigner" "$aapt2" "$apkanalyzer"; do
    if [[ -z "$tool_path" || ! -x "$tool_path" ]]; then
        echo "Required Android APK tool was not found under $sdk_root." >&2
        exit 1
    fi
done

temp_dir="$(mktemp -d "${TMPDIR:-/tmp}/robpelo-media-verify.XXXXXX")"
cleanup() {
    rm -f "$temp_dir/badging.txt" "$temp_dir/manifest-tree.txt" "$temp_dir/manifest.xml"
    rmdir "$temp_dir"
}
trap cleanup EXIT

"$apksigner" verify --verbose --print-certs "$media_apk" >/dev/null
"$aapt2" dump badging "$media_apk" >"$temp_dir/badging.txt"
"$aapt2" dump xmltree "$media_apk" --file AndroidManifest.xml \
    >"$temp_dir/manifest-tree.txt"
"$apkanalyzer" manifest print "$media_apk" >"$temp_dir/manifest.xml"

package_line="$(grep -m1 '^package:' "$temp_dir/badging.txt")"
package_name="$(sed -n "s/^package: name='\\([^']*\\)'.*/\\1/p" <<<"$package_line")"
version_code="$(
    sed -n "s/^package: name='[^']*' versionCode='\\([^']*\\)'.*/\\1/p" \
        <<<"$package_line"
)"
version_name="$(
    sed -n \
        "s/^package: name='[^']*' versionCode='[^']*' versionName='\\([^']*\\)'.*/\\1/p" \
        <<<"$package_line"
)"
min_sdk="$(
    sed -n 's/.*minSdkVersion[^=]*=\([0-9][0-9]*\)$/\1/p' \
        "$temp_dir/manifest-tree.txt" |
        head -1
)"
target_sdk="$(
    sed -n 's/.*targetSdkVersion[^=]*=\([0-9][0-9]*\)$/\1/p' \
        "$temp_dir/manifest-tree.txt" |
        head -1
)"
signer="$(
    "$apksigner" verify --print-certs "$media_apk" |
        sed -n 's/^.*certificate SHA-256 digest: //p' |
        head -1
)"

if [[ "$package_name" != "$expected_package" ]]; then
    echo "Unexpected package: $package_name" >&2
    exit 1
fi
if [[ "$version_name" != "$expected_version_name" ]]; then
    echo "Unexpected version name: $version_name" >&2
    exit 1
fi
if [[ ! "$version_code" =~ ^[0-9]+$ || "$version_code" -lt "$minimum_version_code" ]]; then
    echo "Version code $version_code is below required $minimum_version_code." >&2
    exit 1
fi
if [[ ! "$min_sdk" =~ ^[0-9]+$ || "$min_sdk" -gt "$maximum_min_sdk" ]]; then
    echo "Minimum SDK $min_sdk is incompatible with Peloton API $maximum_min_sdk." >&2
    exit 1
fi
if ! grep -Eq "^native-code: '$expected_abi'$" "$temp_dir/badging.txt"; then
    echo "APK does not contain exactly the expected native ABI: $expected_abi" >&2
    grep -E '^native-code:' "$temp_dir/badging.txt" >&2 || true
    exit 1
fi
if [[ "$signer" != "$expected_signer" ]]; then
    echo "Media-browser signer mismatch." >&2
    echo "Expected: $expected_signer" >&2
    echo "Actual:   $signer" >&2
    exit 1
fi

if [[ -n "$companion_apk" ]]; then
    "$apksigner" verify --verbose "$companion_apk" >/dev/null
    companion_package="$(
        "$aapt2" dump badging "$companion_apk" |
            sed -n "s/^package: name='\\([^']*\\)'.*/\\1/p" |
            head -1
    )"
    companion_signer="$(
        "$apksigner" verify --print-certs "$companion_apk" |
            sed -n 's/^.*certificate SHA-256 digest: //p' |
            head -1
    )"
    if [[ "$companion_package" != "com.robpelo.companion" ]]; then
        echo "Unexpected companion package: $companion_package" >&2
        exit 1
    fi
    if [[ "$companion_signer" != "$signer" ]]; then
        echo "RobPelo and media-browser signer certificates differ." >&2
        exit 1
    fi
fi

python3 - "$temp_dir/manifest.xml" <<'PY'
import sys
import xml.etree.ElementTree as ET

ANDROID = "{http://schemas.android.com/apk/res/android}"
manifest = ET.parse(sys.argv[1]).getroot()


def attr(element, name):
    return element.get(ANDROID + name) or element.get(name)


def require(condition, message):
    if not condition:
        raise SystemExit(message)


permissions = {
    attr(element, "name"): attr(element, "protectionLevel")
    for element in manifest.findall("permission")
}
require(
    permissions.get("com.robpelo.browser.permission.OPEN_MEDIA")
    in {"signature", "0x2", "2"},
    "OPEN_MEDIA is missing or is not signature-protected.",
)

application = manifest.find("application")
require(application is not None, "Manifest has no application element.")

components = {}
for tag in ("activity", "activity-alias"):
    for element in application.findall(tag):
        name = attr(element, "name")
        if name:
            components[name] = element


def component(name):
    require(name in components, f"Required component is missing: {name}")
    return components[name]


viewer = component("com.robpelo.browser.MediaViewerActivity")
require(attr(viewer, "exported") == "true", "MediaViewerActivity is not exported.")
require(
    attr(viewer, "permission") == "com.robpelo.browser.permission.OPEN_MEDIA",
    "MediaViewerActivity does not require OPEN_MEDIA.",
)
viewer_actions = {
    attr(action, "name")
    for intent_filter in viewer.findall("intent-filter")
    for action in intent_filter.findall("action")
}
require(
    "com.robpelo.browser.action.OPEN_MEDIA" in viewer_actions,
    "MediaViewerActivity does not expose OPEN_MEDIA.",
)

internal_viewer = component("com.robpelo.browser.MediaViewerCustomTabActivity")
require(
    attr(internal_viewer, "exported") == "false",
    "MediaViewerCustomTabActivity must not be exported.",
)

require(
    "com.google.android.apps.chrome.IntentDispatcher" not in components,
    "The general-purpose URL dispatcher must be absent.",
)

for name in (
    "com.google.android.apps.chrome.Main",
    "org.chromium.chrome.browser.ChromeTabbedActivity",
):
    element = component(name)
    require(attr(element, "exported") == "false", f"{name} must not be exported.")

for name, element in components.items():
    if attr(element, "exported") != "true":
        continue
    for intent_filter in element.findall("intent-filter"):
        actions = {attr(item, "name") for item in intent_filter.findall("action")}
        categories = {attr(item, "name") for item in intent_filter.findall("category")}
        schemes = {attr(item, "scheme") for item in intent_filter.findall("data")}
        if (
            "android.intent.action.VIEW" in actions
            and "android.intent.category.BROWSABLE" in categories
            and schemes.intersection({"http", "https"})
        ):
            raise SystemExit(
                f"Exported arbitrary web URL handler remains in manifest: {name}"
            )
        if (
            "android.intent.action.MAIN" in actions
            and categories.intersection(
                {
                    "android.intent.category.APP_BROWSER",
                    "android.intent.category.LAUNCHER",
                }
            )
        ):
            raise SystemExit(f"Exported browser/launcher entry remains: {name}")
PY

digest="$(shasum -a 256 "$media_apk" | awk '{print $1}')"
size_bytes="$(wc -c <"$media_apk" | tr -d ' ')"

cat <<EOF
Media-browser APK verification passed.
Path:         $media_apk
SHA-256:      $digest
Size:         $size_bytes bytes
Package:      $package_name
Version:      $version_name ($version_code)
SDK:          min=$min_sdk target=$target_sdk
ABI:          $expected_abi
Signer:       $signer
Companion:    ${companion_apk:-not supplied}
EOF
