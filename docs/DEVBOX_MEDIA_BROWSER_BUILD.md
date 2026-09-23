# Dev Box media-browser build runbook

Use this procedure to build the ARM64 `com.robpelo.browser` APK on a Microsoft
Dev Box. Chromium's Android build requires x86-64 Linux; run all build commands
inside Ubuntu on WSL2, not native Windows.

## Before using this runbook

The media-browser implementation must be committed and pushed before cloning
RobPelo on the Dev Box. A fresh clone is not ready unless these paths exist:

```text
media-browser/
scripts/apply-media-browser-overlay.sh
scripts/test-media-browser-policy.sh
app/src/main/java/com/robpelo/companion/browser/
```

Do not commit an Android keystore, passwords, API keys, downloaded Chromium
sources, or built APKs.

## 1. Verify the Dev Box and WSL2

In Windows PowerShell:

```powershell
$env:PROCESSOR_ARCHITECTURE
wsl --status
wsl --list --verbose
```

Required:

- architecture is `AMD64`;
- Ubuntu is installed;
- Ubuntu reports WSL version `2`.

If Ubuntu is missing and organizational policy permits installation:

```powershell
wsl --install -d Ubuntu
```

Restart if Windows requests it. Open Ubuntu and create the Linux user when
prompted.

In Ubuntu:

```bash
uname -m
free -h
df -h ~
```

Stop if `uname -m` is not `x86_64` or if the Linux filesystem has less than
200 GB free. Ensure the Dev Box auto-stop schedule will not interrupt a
multi-hour checkout or build.

## 2. Prepare Ubuntu

Keep all sources under the WSL Linux filesystem. Do not build under `/mnt/c`.

```bash
sudo apt-get update
sudo apt-get install -y \
  build-essential \
  ca-certificates \
  curl \
  git \
  pkg-config \
  python3 \
  python3-setuptools \
  python-is-python3 \
  unzip \
  zip
```

Install Node.js 24 with `nvm`, then enable the package manager required by the
pinned Brave release:

```bash
curl -o- https://raw.githubusercontent.com/nvm-sh/nvm/v0.40.3/install.sh | bash
export NVM_DIR="$HOME/.nvm"
[ -s "$NVM_DIR/nvm.sh" ] && . "$NVM_DIR/nvm.sh"
nvm install 24
nvm alias default 24
corepack enable
corepack prepare pnpm@11.11.0 --activate
```

Verify:

```bash
git --version
python3 --version
node --version
pnpm --version
```

Required minimums for the pinned source are Git 2.41, Node 24, and pnpm
11.11.0.

## 3. Clone and validate RobPelo

```bash
mkdir -p ~/src
cd ~/src
git clone https://github.com/robfrye/RobPelo.git
cd RobPelo
```

Confirm that the media-browser work is present:

```bash
test -f media-browser/overlay/brave/android/java/com/robpelo/browser/MediaViewerActivity.java
test -f media-browser/patches/brave-core-v1.95.104.patch
test -f media-browser/patches/chromium-153.0.8010.53.patch
test -x scripts/apply-media-browser-overlay.sh
test -x scripts/test-media-browser-policy.sh
```

Run the source-independent policy test:

```bash
scripts/test-media-browser-policy.sh
```

Stop if any check fails.

## 4. Initialize the pinned Brave checkout

Brave's build scripts expect brave-core at `<checkout>/src/brave`.

```bash
mkdir -p ~/src/brave-browser/src
git clone \
  --branch v1.95.104 \
  --depth 1 \
  https://github.com/brave/brave-core.git \
  ~/src/brave-browser/src/brave

cd ~/src/brave-browser/src/brave
git describe --tags --exact-match
```

The last command must print:

```text
v1.95.104
```

Initialize the Android ARM64 checkout. This downloads Chromium and its
dependencies and can take hours:

```bash
export GIT_CACHE_PATH="$HOME/.cache/brave-git"
export JAVA_OPTS="-Xmx10G -Xms1G"
pnpm run init \
  --target_os=android \
  --target_arch=arm64 \
  --no-history
```

After initialization, install Linux and Android build dependencies:

```bash
cd ~/src/brave-browser
sudo ./src/build/install-build-deps.sh --android
```

Verify the synchronized versions:

```bash
git -C src/brave describe --tags --exact-match
cat src/chrome/VERSION
```

Required:

```text
brave-core: v1.95.104
Chromium:   153.0.8010.53
```

Stop if either version differs.

## 5. Apply the RobPelo downstream overlay

The overlay must be applied only after Brave's normal initialization and patch
process has completed:

```bash
cd ~/src/RobPelo
scripts/apply-media-browser-overlay.sh ~/src/brave-browser/src
```

Review the resulting source state:

```bash
git -C ~/src/brave-browser/src/brave status --short
git -C ~/src/brave-browser/src status --short
git -C ~/src/brave-browser/src/brave diff --check
git -C ~/src/brave-browser/src diff --check
```

Expected changes are limited to:

- three `com.robpelo.browser` Java source files;
- `src/brave/android/brave_java_sources.gni`;
- `src/brave/android/java/AndroidManifest.xml`;
- `src/chrome/android/java/AndroidManifest.xml`.

Stop and review any unrelated modification.

## 6. Build the ARM64 APK

Run the build from brave-core:

```bash
cd ~/src/brave-browser/src/brave
export JAVA_OPTS="-Xmx10G -Xms1G"
pnpm run build Debug \
  --target_os=android \
  --target_arch=arm64 \
  --target_android_output_format=apk \
  --use_remoteexec=false \
  --gn 'chrome_public_manifest_package:"com.robpelo.browser"'
```

Do not add `is_official_build=true`; that requires Brave's private release
secrets and is not needed for this device-validation build.

When the build completes, list candidate APKs:

```bash
find ~/src/brave-browser/src/out \
  -type f \
  -path '*/apks/*.apk' \
  -printf '%T@ %p\n' |
  sort -n
```

Do not assume an APK is correct from its filename. Authenticate its manifest in
the next step.

## 7. Use the same signing identity as RobPelo

The exported media activity is protected by an Android signature permission.
The media browser and RobPelo therefore must have the same signing
certificate.

For the current development installation, securely copy the Mac development
keystore from:

```text
~/.android/debug.keystore
```

to a private Dev Box path such as:

```text
~/secrets/robpelo-debug.keystore
```

Do not put the keystore in either Git repository. Restrict access:

```bash
chmod 600 ~/secrets/robpelo-debug.keystore
```

Locate Brave's bundled Android tools:

```bash
APKSIGNER="$(
  find ~/src/brave-browser/src/third_party/android_sdk \
    -type f -name apksigner |
  sort -V |
  tail -1
)"
AAPT2="$(
  find ~/src/brave-browser/src/third_party/android_sdk \
    -type f -name aapt2 |
  sort -V |
  tail -1
)"
test -x "$APKSIGNER"
test -x "$AAPT2"
```

Copy the correct candidate to a private artifact directory, then sign a new
output. Replace `CANDIDATE_APK` with the path found above:

```bash
mkdir -p ~/artifacts
CANDIDATE_APK='/replace/with/the/candidate.apk'

"$APKSIGNER" sign \
  --ks ~/secrets/robpelo-debug.keystore \
  --ks-key-alias androiddebugkey \
  --out ~/artifacts/RobPeloMediaBrowser-v1.95.104-arm64.apk \
  "$CANDIDATE_APK"
```

Allow `apksigner` to prompt for the keystore and key passwords. Do not put
passwords in shell history or the repository.

## 8. Authenticate the built artifact

```bash
MEDIA_APK=~/artifacts/RobPeloMediaBrowser-v1.95.104-arm64.apk

sha256sum "$MEDIA_APK"
"$AAPT2" dump badging "$MEDIA_APK" |
  grep -E "^(package:|sdkVersion:|targetSdkVersion:|native-code:)"
"$APKSIGNER" verify --verbose --print-certs "$MEDIA_APK"
```

Required:

- package is `com.robpelo.browser`;
- version is `1.95.104`;
- minimum SDK is no greater than 30;
- native ABI includes `arm64-v8a`;
- signature verification succeeds.

The current RobPelo development APK is signed by:

```text
Certificate DN: C=US, O=Android, CN=Android Debug
Certificate SHA-256:
bcf1ccee28e04a3efd8a0b85468db5fc62ccd2371c709d61d7accd06a5f0f9b9
```

Require the media APK to match that exact certificate:

```bash
EXPECTED_SIGNER='bcf1ccee28e04a3efd8a0b85468db5fc62ccd2371c709d61d7accd06a5f0f9b9'
MEDIA_SIGNER="$(
  "$APKSIGNER" verify --print-certs "$MEDIA_APK" |
  awk -F': ' '/Signer #1 certificate SHA-256 digest/ { print $2; exit }'
)"
test "$MEDIA_SIGNER" = "$EXPECTED_SIGNER"
```

Stop if the comparison fails.

Inspect the merged manifest with the available Android manifest tool and
verify:

- `com.robpelo.browser.MediaViewerActivity` is exported;
- it requires `com.robpelo.browser.permission.OPEN_MEDIA`;
- `MediaViewerCustomTabActivity` is not exported;
- the primary browser launcher, tabbed-browser activity, and URL dispatcher are
  not exported;
- the package does not qualify for the general browser role;
- no arbitrary-URL media intent is exported.

## 9. Preserve the artifacts

Keep these outside Git:

```text
~/artifacts/RobPeloMediaBrowser-v1.95.104-arm64.apk
```

Record:

- both SHA-256 digests;
- package names and versions;
- signer certificate SHA-256;
- minimum and target SDK;
- native ABI;
- Brave and Chromium revisions;
- build command and completion date.

Do not connect or install on the Peloton yet. Return to the original RobPelo
session with the authenticated APKs and recorded metadata. Device installation
and physical validation are a separate gated step.

## Handoff prompt for the Dev Box

After cloning RobPelo in WSL, start a new assistant session in that clone and
use:

> Continue the RobPelo media-browser build using
> `docs/DEVBOX_MEDIA_BROWSER_BUILD.md` as the authoritative runbook. Work only
> inside the WSL Linux filesystem. Verify each gate before continuing. Build
> official Brave v1.95.104 / Chromium 153.0.8010.53 for Android arm64, apply
> the checked-in downstream overlay, sign the result with the user-provided
> private development keystore without exposing or committing it, and
> authenticate the resulting APK. Do not install anything on the Peloton.
