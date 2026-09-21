# Streaming Service Test Procedure

This document preserves the full validation procedure used for YouTube and
Netflix in TV Bro. It is a reference checklist, not the default test plan for
every new streaming tile.

## Default policy for future services

TV Bro 2.1.6 with its bundled GeckoView engine has already demonstrated:

- stable operation on the RB1VQ;
- persistent cookies and authentication;
- persistence across app force-stop/relaunch;
- persistence across full bike reboot;
- compatible video and audio playback;
- compatible video fullscreen;
- compatible RobPelo HUD overlays;
- correct cleanup of the HUD service and Affernet binding.

For a new TV Bro-based service, perform only these checks by default:

1. Confirm the service URL renders.
2. Confirm RobPelo launches the URL in TV Bro rather than another browser.
3. Confirm the HUD appears.
4. Confirm closing the HUD stops the ride service and Affernet binding.
5. Confirm RobPelo remains the default HOME.

Do **not** repeat login, cookie-persistence, force-stop, reboot, fullscreen,
audio, DRM, or long-duration tests unless the user explicitly requests them or
the service exhibits a service-specific problem.

## Full test procedure

Use this procedure only when explicitly requested.

### 1. Pre-test state

Record:

```bash
adb devices -l
adb shell cmd package resolve-activity --brief \
  -a android.intent.action.MAIN \
  -c android.intent.category.HOME
adb shell dumpsys role |
  grep -A 3 'name=android.app.role.BROWSER'
adb shell dumpsys package com.phlox.tvwebbrowser |
  grep -E -m 2 'versionCode=|versionName='
```

Expected:

- exactly one intended authorized bike;
- RobPelo is HOME;
- Firefox may remain default browser;
- TV Bro is installed.

### 2. TV Bro configuration

In TV Bro Settings:

1. Verify **Web browser engine** is **GeckoView**.
2. Verify incognito/private mode is off.
3. Avoid granting camera, microphone, or location unless the service genuinely
   requires one and the user explicitly approves it.

### 3. Basic service launch

Launch the service URL explicitly in TV Bro:

```bash
adb shell am start -W \
  -a android.intent.action.VIEW \
  -c android.intent.category.BROWSABLE \
  -d 'https://SERVICE_URL' \
  -p com.phlox.tvwebbrowser
```

Verify:

- TV Bro becomes foreground;
- its action bar is hidden for the external URL;
- the site renders without a crash;
- RobPelo remains the resolved HOME.

### 4. Authentication

Only the user should enter credentials or verification codes.

The tester records only:

- sign-in succeeded;
- sign-in was blocked;
- sign-in failed with a non-sensitive error.

Do not capture screenshots or logs containing account identifiers, email
addresses, credentials, profile names, or verification codes.

### 5. Process-restart persistence

After successful sign-in:

```bash
adb shell am force-stop com.phlox.tvwebbrowser
adb shell am start -W \
  -a android.intent.action.VIEW \
  -c android.intent.category.BROWSABLE \
  -d 'https://SERVICE_URL' \
  -p com.phlox.tvwebbrowser
```

Ask the user whether the service remains signed in. Do not inspect or export TV
Bro's private data.

### 6. Reboot persistence

Requires explicit user approval:

```bash
adb reboot
adb wait-for-device
```

Wait for:

```bash
adb shell getprop sys.boot_completed
```

to return `1`, then relaunch the service in TV Bro and ask the user whether the
session remains authenticated.

### 7. Playback and fullscreen

Ask the user to test:

- video playback;
- audio playback;
- seeking;
- pause/resume;
- service-provided fullscreen.

Record outcomes only. Do not capture protected video frames or attempt to
bypass secure surfaces.

### 8. HUD compatibility

From RobPelo, start the service tile and verify:

```bash
adb shell dumpsys window windows |
  grep -E -m 1 -A 5 \
  'package=com.robpelo.companion appop=SYSTEM_ALERT_WINDOW'
```

Verify:

- TV Bro remains foreground;
- HUD is visible and non-focusable;
- telemetry updates;
- playback continues;
- fullscreen behavior remains usable.

### 9. Cleanup

Use the HUD **Close** control, then verify:

```bash
adb shell dumpsys activity services com.robpelo.companion
adb shell dumpsys activity services com.onepeloton.affernetservice
adb shell cmd package resolve-activity --brief \
  -a android.intent.action.MAIN \
  -c android.intent.category.HOME
```

Expected:

- no `RideTelemetryService`;
- no RobPelo Affernet binding;
- RobPelo remains HOME.

### 10. Documentation

Record:

- service name and URL;
- date;
- RobPelo, TV Bro, Peloton build, and Android versions;
- whether the lightweight or full procedure was used;
- pass/fail results;
- any service-specific caveats.

Keep raw device logs and screenshots out of Git unless they are reviewed and
sanitized.

## When to escalate automatically

Even without an explicit request, stop and propose the full procedure if a new
service:

- redirects authentication to another browser;
- repeatedly loses cookies;
- fails only after process restart;
- uses DRM or protected playback that behaves differently from Netflix;
- hides or blocks the HUD;
- crashes TV Bro;
- requests sensitive permissions;
- requires changing browser, HOME, package, or security settings.
