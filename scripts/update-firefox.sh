#!/usr/bin/env bash

set -euo pipefail

FIREFOX_PACKAGE="org.mozilla.firefox"
MOZILLA_VERSION_FEED="https://product-details.mozilla.org/1.0/mobile_versions.json"
MOZILLA_RELEASE_CERT_SHA256="a78b62a5165b4494b2fead9e76a280d22d937fee6251aece599446b2ea319b04"

for command_name in adb curl python3 apkanalyzer; do
    if ! command -v "$command_name" >/dev/null 2>&1; then
        echo "Required command not found: $command_name" >&2
        exit 1
    fi
done

if [[ -n "${ANDROID_SERIAL:-}" ]]; then
    ADB=(adb -s "$ANDROID_SERIAL")
else
    device_count="$(adb devices | awk '$2 == "device" { count++ } END { print count + 0 }')"
    if [[ "$device_count" -ne 1 ]]; then
        echo "Expected exactly one authorized ADB device; found $device_count." >&2
        echo "Set ANDROID_SERIAL when more than one device is connected." >&2
        exit 1
    fi
    ADB=(adb)
fi

if [[ "$("${ADB[@]}" get-state)" != "device" ]]; then
    echo "The selected ADB device is not ready." >&2
    exit 1
fi

firefox_installed=false
if "${ADB[@]}" shell pm list packages "$FIREFOX_PACKAGE" |
    grep -qx "package:$FIREFOX_PACKAGE"; then
    firefox_installed=true
fi

sdk_root="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}"
if [[ -z "$sdk_root" || ! -d "$sdk_root/build-tools" ]]; then
    echo "Set ANDROID_HOME or ANDROID_SDK_ROOT to an Android SDK installation." >&2
    exit 1
fi

apksigner="$(find "$sdk_root/build-tools" -type f -name apksigner | sort | tail -1)"
if [[ -z "$apksigner" ]]; then
    echo "apksigner was not found under $sdk_root/build-tools." >&2
    exit 1
fi

device_abis="$("${ADB[@]}" shell getprop ro.product.cpu.abilist | tr -d '\r')"
if [[ ",$device_abis," != *",arm64-v8a,"* ]]; then
    echo "This updater currently supports only arm64-v8a; device reports: $device_abis" >&2
    exit 1
fi

device_sdk="$("${ADB[@]}" shell getprop ro.build.version.sdk | tr -d '\r')"
latest_version="$(
    curl --proto '=https' --tlsv1.2 --fail --silent --show-error \
        "$MOZILLA_VERSION_FEED" |
        python3 -c 'import json, sys; print(json.load(sys.stdin)["version"])'
)"
if [[ ! "$latest_version" =~ ^[0-9]+([.][0-9]+)*$ ]]; then
    echo "Mozilla returned an unexpected Firefox version: $latest_version" >&2
    exit 1
fi

installed_path=""
installed_version=""
if [[ "$firefox_installed" == true ]]; then
    installed_path="$(
        "${ADB[@]}" shell pm path "$FIREFOX_PACKAGE" |
            sed -n '1s/^package://p' |
            tr -d '\r'
    )"
    if [[ -z "$installed_path" ]]; then
        echo "Could not determine the installed Firefox APK path." >&2
        exit 1
    fi

    installed_version="$(
        "${ADB[@]}" shell dumpsys package "$FIREFOX_PACKAGE" |
            sed -n 's/^[[:space:]]*versionName=//p' |
            head -1 |
            tr -d '\r'
    )"
    if [[ "$installed_version" == "$latest_version" ]]; then
        echo "Firefox $installed_version is already current."
        exit 0
    fi
fi

temp_dir="$(mktemp -d "${TMPDIR:-/tmp}/robpelo-firefox.XXXXXX")"
installed_apk="$temp_dir/installed-firefox.apk"
candidate_apk="$temp_dir/firefox-$latest_version-arm64-v8a.apk"
cleanup() {
    rm -f "$installed_apk" "$candidate_apk"
    rmdir "$temp_dir"
}
trap cleanup EXIT

if [[ "$firefox_installed" == true ]]; then
    "${ADB[@]}" pull "$installed_path" "$installed_apk" >/dev/null
fi

download_url="https://archive.mozilla.org/pub/fenix/releases/$latest_version/android/fenix-$latest_version-android-arm64-v8a/fenix-$latest_version.multi.android-arm64-v8a.apk"
echo "Downloading Firefox $latest_version from Mozilla..."
curl --proto '=https' --tlsv1.2 --fail --location --retry 3 \
    --output "$candidate_apk" "$download_url"

candidate_package="$(apkanalyzer manifest application-id "$candidate_apk")"
candidate_min_sdk="$(apkanalyzer manifest min-sdk "$candidate_apk")"
candidate_version_code="$(apkanalyzer manifest version-code "$candidate_apk")"

if [[ "$candidate_package" != "$FIREFOX_PACKAGE" ]]; then
    echo "Downloaded APK has unexpected package: $candidate_package" >&2
    exit 1
fi
if [[ "$candidate_min_sdk" -gt "$device_sdk" ]]; then
    echo "Firefox requires API $candidate_min_sdk; device is API $device_sdk." >&2
    exit 1
fi
candidate_signer="$(
    "$apksigner" verify --print-certs "$candidate_apk" |
        sed -n 's/^Signer #1 certificate SHA-256 digest: //p'
)"
if [[ "$candidate_signer" != "$MOZILLA_RELEASE_CERT_SHA256" ]]; then
    echo "Downloaded APK is not signed by the expected Mozilla key." >&2
    exit 1
fi

if [[ "$firefox_installed" == true ]]; then
    installed_version_code="$(apkanalyzer manifest version-code "$installed_apk")"
    if [[ "$candidate_version_code" -le "$installed_version_code" ]]; then
        echo "Refusing non-upgrade: installed=$installed_version_code candidate=$candidate_version_code" >&2
        exit 1
    fi
    installed_signer="$(
        "$apksigner" verify --print-certs "$installed_apk" |
            sed -n 's/^Signer #1 certificate SHA-256 digest: //p'
    )"
    if [[ "$candidate_signer" != "$installed_signer" ]]; then
        echo "Firefox signing certificate mismatch; refusing installation." >&2
        exit 1
    fi
    echo "Installing Firefox $latest_version over $installed_version..."
else
    echo "Installing Firefox $latest_version..."
fi
"${ADB[@]}" install -r "$candidate_apk"

updated_version="$(
    "${ADB[@]}" shell dumpsys package "$FIREFOX_PACKAGE" |
        sed -n 's/^[[:space:]]*versionName=//p' |
        head -1 |
        tr -d '\r'
)"
if [[ "$updated_version" != "$latest_version" ]]; then
    echo "Post-install version mismatch: expected $latest_version, found $updated_version" >&2
    exit 1
fi

echo "Firefox is now $updated_version."
