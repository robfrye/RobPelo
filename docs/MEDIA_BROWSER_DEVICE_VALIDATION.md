# Media-browser device installation and validation

Use this procedure only after the Dev Box produces an APK and
`scripts/verify-media-browser-apk.sh` passes. It preserves TV Bro and Firefox as
rollback while the replacement browser is tested.

Only an optimized `Static` browser build is eligible for physical acceptance.
The first Debug build installed and launched successfully but consumed roughly
740 MB PSS across its browser processes under navigation load and was too laggy
to use on the approximately 2 GB reference bike.

Do not uninstall a browser, clear browser data, change HOME, change the general
browser role, disable a Peloton package, or modify a Peloton package during
this procedure.

## 1. Authenticate both APKs offline

Place the Dev Box artifact somewhere outside Git. From the RobPelo repository:

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
export ANDROID_HOME=/opt/homebrew/share/android-commandlinetools

MEDIA_APK='/absolute/path/to/RobPeloMediaBrowser-v1.95.104-arm64.apk'
COMPANION_APK='app/build/outputs/apk/debug/app-debug.apk'

scripts/verify-media-browser-apk.sh "$MEDIA_APK" "$COMPANION_APK" |
  tee device-tests/media-browser-apk-verification.txt
```

Review the generated text file before committing it. It must contain no local
username or private path if it will be retained in Git.

Stop if verification fails. Do not install an artifact by filename or source
alone.

## 2. Reconnect and capture the pre-install state

Connect exactly one intended bike:

```bash
adb devices -l
```

Record:

```bash
adb shell cmd package resolve-activity --brief \
  -a android.intent.action.MAIN \
  -c android.intent.category.HOME
adb shell dumpsys role |
  grep -A 3 -m 1 'name=android.app.role.BROWSER'
adb shell pm list packages |
  grep -E \
    'com\.robpelo\.companion|com\.robpelo\.browser|com\.phlox\.tvwebbrowser|org\.mozilla\.firefox'
adb shell dumpsys activity services com.robpelo.companion
adb shell getprop ro.build.version.sdk
adb shell getprop ro.product.cpu.abilist
```

Expected:

- RobPelo is HOME;
- Firefox remains the general browser-role holder;
- TV Bro and Firefox remain installed;
- no RobPelo ride/HUD service is active;
- device SDK is 30;
- ABI includes `arm64-v8a`.

Stop and investigate any unexpected state.

## 3. Install without changing routing

Install the media browser first, then update RobPelo:

```bash
adb install "$MEDIA_APK"
adb install -r "$COMPANION_APK"
```

Do not use downgrade flags, signature-bypass tools, package-data clearing, or
`pm uninstall --user`.

Verify:

```bash
adb shell dumpsys package com.robpelo.browser |
  grep -E -m 10 \
    'versionCode=|versionName=|minSdk=|targetSdk=|codePath=|signatures='
adb shell cmd package resolve-activity --brief \
  -a com.robpelo.browser.action.OPEN_MEDIA \
  -c android.intent.category.DEFAULT \
  -p com.robpelo.browser
adb shell cmd package resolve-activity --brief \
  -a android.intent.action.MAIN \
  -c android.intent.category.HOME
adb shell dumpsys role |
  grep -A 3 -m 1 'name=android.app.role.BROWSER'
```

Required:

- the media action resolves only to
  `com.robpelo.browser/.MediaViewerActivity`;
- RobPelo remains HOME;
- Firefox remains the general browser-role holder.

TV Bro remains the initial streaming route. Installing the new APK must not
silently change it.

## 4. Protected-content setup without the HUD

Open RobPelo's settings menu and choose **Open media browser setup (no HUD)**.
This opens Prime Video in the media browser without starting the RobPelo
overlay.

Only the user enters credentials or verification codes. Do not capture or
record account identifiers.

Start a protected title and allow Brave/Android protected-content permission
when prompted. If Android says that another bubble or overlay is blocking the
permission:

1. verify no RobPelo HUD service is active;
2. inspect the active overlay owner;
3. do not disable or alter any Peloton package;
4. use the separately approved temporary `com.peloton.activity` force-stop
   procedure from the Stage 1 record only with explicit approval;
5. recover the Peloton process with a normal reboot immediately afterward.

Record only whether permission and protected playback succeeded.

## 5. Select the replacement route

In RobPelo settings choose **Use RobPelo Media Browser**. This is an explicit,
persisted selection. There is no automatic fallback.

Launch each tile:

- Netflix
- YouTube
- HBO Max
- Prime Video
- Apple TV

For each destination verify:

- only `com.robpelo.browser` becomes foreground;
- no ordinary browser home screen or tab strip appears;
- login/account pages show origin/TLS browser chrome;
- playback pages return to the frameless presentation;
- navigation outside the service allowlist is rejected visibly;
- fullscreen video works;
- the RobPelo HUD remains visible and interactive;
- Back navigates within the service, then returns to RobPelo;
- Home returns directly to RobPelo.

HBO Max must use desktop UA only on HBO Max media origins. Authentication
origins and Prime Video must retain normal current-Chromium behavior. The
dedicated media package disables external-app intent requests so Prime's
`intent://` native-app link remains in browser instead of triggering Peloton
subscription enforcement. The browser unwraps and validates the intent's
allowlisted HTTPS target before loading it in the same tab. Forcing desktop UA
on Prime produces its `Video Unavailable` browser path on the reference bike.

Do not capture protected video frames.

## 6. Failure and cleanup checks

Test a launch failure while the HUD is requested, then verify:

```bash
adb shell dumpsys activity services com.robpelo.companion
adb shell dumpsys activity services com.onepeloton.affernetservice
```

A failed browser launch must stop `RideTelemetryService` and remove the
RobPelo Affernet binding immediately.

During normal playback, use the HUD **Close** control and verify the same idle
state.

If the replacement route is unusable, return to RobPelo settings and choose
**Use TV Bro rollback**. Do not uninstall the media browser; retaining its
profile makes diagnosis and retry reversible.

## 7. Persistence and recovery

After successful sign-in and playback:

1. return HOME and allow at least 60 seconds for profile state to flush;
2. force-stop only `com.robpelo.browser`;
3. relaunch each service and confirm authentication persists;
4. confirm HOME and the general browser role are unchanged.

A normal reboot requires explicit approval:

```bash
adb reboot
adb wait-for-device
```

Wait for `sys.boot_completed=1`, then repeat the lightweight five-service
check. Verify:

- authentication persists;
- RobPelo remains HOME;
- Firefox remains the general browser-role holder;
- TV Bro and Firefox remain installed;
- no ride service or Affernet binding remains active at rest.

## 8. Acceptance boundary

Keep TV Bro and Firefox installed until every required item in
`docs/BRAVE_MEDIA_BROWSER_PROPOSAL.md` passes and the user explicitly approves
decommissioning.

An APK build and successful installation are not enough to remove rollback.
Protected playback, fullscreen, HUD behavior, authentication persistence,
Back/Home behavior, crash recovery, and update behavior must all be physically
accepted first.
