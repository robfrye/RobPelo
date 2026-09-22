# Brave Media Browser Proposal

## Purpose

This document is the handoff for the next RobPelo streaming-browser phase.

The current TV Bro configuration is useful but is not a satisfactory final
solution:

- TV Bro GeckoView 147 ignores its configured desktop user agent, causing HBO
  Max to identify the bike as Android mobile and redirect to its native app.
- TV Bro System WebView honors a desktop user agent and is responsive, but the
  Peloton-provided WebView is Chromium 127.
- Google rejects YouTube login in that embedded WebView environment.
- Prime Video browser playback is limited to standard definition, and a newer
  WebView cannot be installed without Peloton's private signing certificate.

The goal is one current, Widevine-capable Chromium browser that can support:

- Netflix
- YouTube
- HBO Max
- Prime Video
- Apple TV
- persistent authentication
- fullscreen playback
- the RobPelo telemetry HUD
- an app-like presentation without ordinary browser tabs or controls

## Decision

Do not adopt AndroidX Custom Tabs as the final streaming architecture.

Custom Tabs could be used as a disposable compatibility probe, but they cannot
provide the requested final experience:

- the browser-controlled toolbar is present initially;
- the origin/address surface cannot be permanently hidden;
- URL-bar hiding only occurs after scrolling;
- RobPelo cannot set a per-launch user agent;
- RobPelo cannot force Brave's external activity into landscape;
- there is no authoritative "viewer closed" result;
- a true chrome-free Trusted Web Activity requires Digital Asset Links from
  each streaming provider, and none authorizes RobPelo.

Build-tool compatibility is not a product constraint. If AndroidX Browser is
used for an experiment, upgrade AGP and compile SDK as needed and use the
current stable library. The reason not to choose CCT for production is its user
experience and control limitations, not Gradle work.

## Recommended two-stage approach

### Stage 1: Validate unmodified Brave

Install the official Brave arm64 APK alongside RobPelo, TV Bro, and Firefox.
Do not change the default browser or production tile routing.

Current reviewed release at the time of this proposal:

```text
Brave version: 1.95.104
Chromium: 153.0.8010.53
Package: com.brave.browser
Artifact: Bravearm64Universal.apk
Minimum Android: API 29 / Android 10
Peloton: API 30 / Android 11
SHA-256:
c6cc741f38b2efa3fc3e18e0be93fa4bb25a28bb93530f3f04f9f64dee05a2d8
Signing certificate SHA-256:
9C:2D:B7:05:13:51:5F:DB:FB:BC:58:5B:3E:DF:3D:71:
23:D4:DC:67:C9:4F:FD:30:63:61:C1:D7:9B:BF:18:AC
```

Official source:

- <https://github.com/brave/brave-browser/releases>

Validate Brave before designing or building a fork. A custom browser shell
cannot repair device-level Widevine, codec, certification, or service-policy
failures.

#### Stage 1 test order

1. Verify the downloaded APK digest, package ID, minimum SDK, ABI, and signer.
2. Install Brave as an additional package.
3. Confirm startup stability and memory behavior on the roughly 2 GB bike.
4. Test ordinary Google and YouTube website login.
5. Confirm cookie persistence after process restart.
6. Confirm cookie persistence after a normal bike reboot.
7. Inspect Widevine availability and reported security level where possible.
8. Test an actual protected title in each service:
   - YouTube paid/protected content if available
   - Netflix
   - HBO Max
   - Prime Video
   - Apple TV
9. Record:
   - successful license acquisition;
   - maximum observed resolution;
   - video and audio codecs;
   - fullscreen behavior;
   - desktop-site behavior;
   - whether service navigation attempts a native-app handoff;
   - whether the RobPelo HUD remains visible and interactive;
   - whether Back and Home return cleanly to RobPelo.
10. Test HBO Max with Brave's persistent per-site desktop setting because
    RobPelo cannot override a Custom Tab user agent.

Use the lightweight service-test policy initially, escalating only when a
service-specific failure requires deeper authentication, DRM, or reboot
testing.

### Stage 1 success criteria

Continue to Stage 2 only if Brave proves that:

- YouTube website login works;
- normal cookies persist;
- the commercial services acquire licenses and play;
- playback quality is acceptable;
- Brave remains stable under video load;
- fullscreen and the telemetry HUD coexist;
- service-specific desktop/mobile behavior is manageable.

If Brave fails because of Widevine provisioning, device certification, secure
decoding, HDCP, codec support, or provider policy, stop. Do not build a browser
fork on the assumption that changing its UI will fix those failures.

## Stage 2: Separate RobPelo media-browser APK

If unmodified Brave passes Stage 1, build a separate maintained browser APK
derived from current Brave/Chromium.

Do not embed the browser engine in the RobPelo HOME APK. The browser should be
isolated so:

- browser crashes do not take down HOME or telemetry;
- browser updates do not require changing the launcher;
- the large Chromium payload stays separate;
- the browser has its own persistent profile and cookies;
- rollback remains installing or uninstalling an independent package.

### Proposed package boundary

```text
RobPelo HOME APK
  package: com.robpelo.companion
  responsibilities:
    - HOME screen
    - Just Ride
    - Affernet telemetry
    - system HUD
    - streaming destination selection

RobPelo Media Browser APK
  proposed package: com.robpelo.browser
  responsibilities:
    - current Chromium/Brave engine
    - persistent browser profile
    - authentication cookies
    - protected media
    - allowlisted navigation
    - app-like media presentation
```

### Proposed launch contract

Expose one explicit media-viewer activity owned by the browser APK.

RobPelo should launch a service identifier rather than accepting arbitrary
untrusted URLs:

```text
Action:
  com.robpelo.browser.action.OPEN_MEDIA

Explicit component:
  com.robpelo.browser/.MediaViewerActivity

Extra:
  service = netflix | youtube | hbo_max | prime_video | apple_tv
```

The browser maps the identifier to a compile-time destination and policy:

```text
Netflix
  start URL: https://www.netflix.com/browse
  user agent: validated service policy

YouTube
  start URL: https://m.youtube.com/
  user agent: normal current Chromium

HBO Max
  start URL: https://play.hbomax.com/
  user agent: desktop if required by physical testing

Prime Video
  start URL: https://www.primevideo.com/region/na/
  user agent: validated service policy

Apple TV
  start URL: https://tv.apple.com/
  user agent: validated service policy
```

Do not expose a general-purpose arbitrary-URL browser intent unless a concrete
need justifies it.

### Browser presentation

The media activity should provide:

- no tab strip;
- no ordinary address bar;
- no browser home/start page;
- no browser menu during normal playback navigation;
- fullscreen media support;
- landscape-first presentation;
- Back behavior that navigates within the service, then returns to RobPelo;
- Home behavior that returns to the selected HOME normally;
- one persistent profile shared across the five service destinations;
- a controlled recovery screen for renderer crashes or unsupported pages.

Security-sensitive navigation must not blindly hide origin identity. During
login, account-management, or payment flows, show enough origin and TLS
identity for the user to verify where credentials are being entered. Restore
the chrome-free presentation after returning to an allowlisted media origin.

### Navigation policy

Use a compile-time allowlist per service, including only domains required for:

- the service itself;
- authentication;
- account selection;
- DRM/license acquisition;
- documented payment/account management when intentionally supported;
- required static/CDN resources.

Cross-origin top-level navigation outside the allowlist should:

1. show an explicit origin confirmation;
2. open in a normal trusted browser; or
3. be rejected with a clear message.

Do not silently suppress navigation failures or spoof success.

### User-agent policy

User-agent behavior must be explicit and service-specific.

- Default to the engine's normal current user agent.
- Apply a desktop user agent only where physical testing proves it is required.
- Keep UA overrides narrow to the affected top-level service.
- Verify that redirects and authentication do not unexpectedly revert or
  propagate the override.
- Do not claim a desktop platform to bypass security or DRM policy; use it only
  to select the service's supported web presentation when lawful and working.

### HUD integration

Keep the current orchestration:

```text
HomeActivity
  -> validate browser package/component
  -> start RideTelemetryService with ACTION_START_HUD
  -> explicitly launch MediaViewerActivity
  -> stop the HUD immediately if launch fails
```

The existing system overlay can remain independent of the browser process.

Refactor toward:

```text
StreamingDestination
StreamingBrowserLauncher
  - BraveCompatibilityLauncher (temporary test only)
  - RobPeloMediaBrowserLauncher
  - TvBroLauncher (rollback during migration)
BrowserAvailability
BrowserLaunchResult
```

Use typed results for:

- missing package;
- disabled package;
- signature mismatch;
- incompatible version;
- missing activity;
- launch denied;
- launch failure.

Do not silently fall back to an arbitrary default browser because browser
profiles, authentication state, DRM, and user experience differ.

## Why the other investigated browsers are not the primary route

### Cromite

- Current and arm64 compatible.
- Supports chromeless home shortcuts.
- Deliberately disables Android DRM/Widevine.
- Rejected for Netflix, HBO Max, Prime Video, and Apple TV.

### Kiwi

- Widevine integration was compiled in.
- Explicitly abandoned after January 2025.
- Effective engine is Chromium 132-era despite a later version string.
- Rejected for credentials and long-term streaming.

### Vivaldi

- Current, official arm64 APK.
- Exposes protected-content support.
- Strong second compatibility-test candidate if Brave fails.
- Does not provide a documented arbitrary-URL chrome-free mode.

### TV Bro

- Best current chrome-free external-URL behavior.
- GeckoView mode ignores the desktop-UA selection.
- System WebView mode depends on stale Peloton WebView 127.
- Keep installed as a reversible fallback until a replacement is validated.

## TWA and PWA findings

None of the five providers authorizes RobPelo as a Trusted Web Activity through
Digital Asset Links.

Web manifests:

```text
Apple TV: display = standalone
YouTube: display = minimal-ui
Netflix: no installable manifest found
HBO Max: no installable manifest found
Prime Video: no installable manifest found
```

Therefore PWAs cannot provide one consistent chrome-free route for all five
services.

## Final migration and legacy-browser cleanup

If Stage 1 and the replacement media browser succeed, remove TV Bro, bundled
GeckoView, and Firefox from both the product architecture and the bike.

This cleanup is intentionally deferred. Do not remove a working fallback while
Brave compatibility or the replacement browser is still experimental.

### Cleanup acceptance gate

Begin decommissioning only after the replacement browser passes all of these:

- all five RobPelo tiles route to the replacement browser;
- Google/YouTube login succeeds;
- Netflix, HBO Max, Prime Video, and Apple TV acquire DRM licenses and play
  actual protected titles;
- achieved playback quality is acceptable to the user;
- cookies survive browser process restart;
- cookies survive a normal bike reboot;
- fullscreen playback works for every service;
- HBO Max no longer requires TV Bro's System WebView/desktop-UA workaround;
- the telemetry HUD remains visible and interactive;
- closing the HUD reliably stops the ride service and Affernet binding;
- Back returns to RobPelo without exposing a general browser screen;
- Home still resolves to RobPelo;
- browser crash/recovery behavior is acceptable;
- browser and RobPelo update paths have both been exercised;
- at least one normal reboot and one forced browser-process restart pass the
  complete lightweight service check.

Require explicit user approval immediately before uninstalling either browser.
Uninstallation permanently deletes that browser's cookies, profiles, saved
sessions, site permissions, and local storage. Reinstalling the APK can restore
the application, but cannot restore those authenticated sessions without a
separate supported backup.

### Phase A: Route away but retain rollback

First ship a migration build that:

- makes the replacement media browser the only default route for all five
  streaming tiles;
- retains `TvBroLauncher` as an explicit developer/rollback option, not a
  silent fallback;
- stops offering new TV Bro or Firefox installation;
- stops sending new authentication or browsing state to TV Bro and Firefox;
- leaves both packages installed temporarily;
- includes a clear way to test every destination and restore TV Bro routing if
  the replacement fails.

Keep this state through the acceptance gate. The purpose is to discover
service, authentication, DRM, update, reboot, and memory regressions before
destroying the old browser profiles.

Create a source-control checkpoint before beginning repository deletion so the
last working TV Bro implementation remains recoverable through Git history.

### Phase B: Capture rollback facts before uninstall

Immediately before device cleanup, record:

```bash
adb shell pm path com.phlox.tvwebbrowser
adb shell dumpsys package com.phlox.tvwebbrowser
adb shell pm path org.mozilla.firefox
adb shell dumpsys package org.mozilla.firefox
adb shell cmd role get-role-holders android.app.role.BROWSER
adb shell cmd package resolve-activity --brief \
  -a android.intent.action.MAIN \
  -c android.intent.category.HOME
```

Record at minimum:

- installed package versions and version codes;
- installer/source package;
- signing-certificate digests;
- current browser-role holder;
- current HOME;
- official APK download URLs;
- official APK SHA-256 digests;
- whether any account still requires a recovery code or reauthentication
  method before its browser profile is deleted.

Known package identities:

```text
TV Bro:  com.phlox.tvwebbrowser
Firefox: org.mozilla.firefox
Brave:   com.brave.browser
RobPelo: com.robpelo.companion
```

TV Bro's GeckoView is bundled inside the TV Bro APK; it is not expected to
appear as an independent Android package. Firefox contains its own Gecko
engine. Removing both user packages removes both Gecko runtimes from the active
device stack.

Before uninstalling, verify there is no separate experimental GeckoView probe
or old browser-test package:

```bash
adb shell pm list packages | grep -Ei \
  'gecko|tvwebbrowser|firefox|robpelo.*browser|cromite|vivaldi|kiwi'
```

Remove only exact, reviewed user-package names. Do not use wildcards and do not
uninstall or disable any Peloton package.

### Phase C: Browser role and default-link behavior

Firefox is currently the general browser-role holder on the reference bike.
Resolve that role before Firefox removal.

Choose one intentional policy:

1. make official Brave the general-purpose browser-role holder; or
2. leave no default general browser and allow Android to prompt when an
   unrelated link is opened.

The dedicated RobPelo media browser should not claim the general browser role
unless it intentionally implements safe arbitrary browsing. Its exported media
activity should accept only RobPelo's explicit allowlisted launch contract.

If Brave is retained as the general browser, verify it qualifies for the role
and then use Android's role manager rather than changing unrelated secure
settings:

```bash
adb shell cmd role add-role-holder \
  android.app.role.BROWSER \
  com.brave.browser
```

Verify:

```bash
adb shell cmd role get-role-holders android.app.role.BROWSER
```

Do not assume uninstalling Firefox automatically produces the desired default.
Test an ordinary HTTPS link after role migration.

### Phase D: Device package removal

Only after explicit approval and the preceding checks:

1. stop any active RobPelo HUD/ride session;
2. return to RobPelo HOME;
3. confirm TV Bro and Firefox are not foreground;
4. confirm the replacement browser profile contains the required authenticated
   sessions;
5. uninstall the two user packages with exact package names:

```bash
adb uninstall com.phlox.tvwebbrowser
adb uninstall org.mozilla.firefox
```

Do not use `pm uninstall --user 0` against an unreviewed system package, package
disabling, application-data clearing, or wildcard removal.

After uninstall:

```bash
adb shell pm list packages | grep -E \
  'com\.phlox\.tvwebbrowser|org\.mozilla\.firefox'
adb shell cmd role get-role-holders android.app.role.BROWSER
adb shell cmd package resolve-activity --brief \
  -a android.intent.action.MAIN \
  -c android.intent.category.HOME
```

Expected results:

- TV Bro absent;
- Firefox absent;
- no standalone experimental Gecko package;
- intentional browser-role state;
- RobPelo remains default HOME;
- no active ride service or Affernet binding at rest.

### Phase E: RobPelo repository cleanup

Once device migration is accepted, remove every active TV Bro/Firefox surface
instead of leaving dead compatibility code.

#### Launcher and HOME

- Replace the TV Bro constants and explicit activity in
  `ExternalAppLauncher.kt`.
- Remove `launchTvBroSetup` and every service-specific TV Bro launch method.
- Route `VideoHudLauncher` through the typed
  `StreamingBrowserLauncher`/`StreamingDestination` abstraction.
- Remove TV Bro installation/result state from `HomeActivity`:
  - `pendingTvBroInstall`;
  - `awaitingTvBroInstallResult`;
  - `tvBroUpdateState`;
  - `tvBroUpdater`;
  - TV Bro update button/notice rendering;
  - unknown-source permission continuation;
  - TV Bro setup toast and setup launch.
- Retarget **Check for Updates** to the replacement media-browser updater or an
  explicit browser update activity. If the browser owns its own authenticated
  updater, RobPelo should launch that narrow update surface rather than
  duplicate installation logic.
- Remove fallback UI and code only after the rollback-retention phase ends.

#### Updater and installer

- Delete `app/src/main/java/com/robpelo/companion/update/TvBroUpdater.kt`.
- Remove TV Bro GitHub API parsing, asset-name selection, package verification,
  certificate pin, cached-release preferences, and installer state.
- Delete `AppVersion.kt` and `AppVersionTest.kt` if the replacement updater
  does not reuse them. Otherwise rename and generalize them rather than keeping
  Firefox/TV Bro terminology.
- Remove obsolete update APKs from RobPelo's private cache through
  application-owned cleanup.
- Remove the `REQUEST_INSTALL_PACKAGES` permission if RobPelo no longer
  downloads and hands APKs to Android's installer.
- Remove the RobPelo `FileProvider` and `file_paths.xml` if no remaining feature
  shares verified APKs.
- Remove the `com.phlox.tvwebbrowser` manifest query.
- Add only the narrow package/activity queries required by the replacement
  media browser.

#### Resources and naming

- Delete all TV Bro setup, unavailable, update, install, download, and error
  strings.
- Remove legacy Firefox-named resource identifiers such as
  `check_firefox_updates`, `firefox_current`, and
  `firefox_update_failed`; do not merely change their displayed text.
- Replace browser-specific unavailable messages with typed destination/browser
  errors.
- Remove any obsolete TV Bro or Firefox icons/assets if later added.

#### Firefox maintenance

- Delete `scripts/update-firefox.sh`.
- Remove Firefox updater/install instructions from the active README.
- Remove Firefox from required setup, preflight, rollback, and troubleshooting
  steps.
- Preserve only clearly labeled historical test findings where they remain
  useful; historical documentation must not imply Firefox is still part of the
  supported stack.

#### GeckoView references

RobPelo currently has no direct GeckoView dependency. GeckoView exists in the
stack through TV Bro's Gecko-included APK and historical experimental
documentation.

- Remove GeckoView from current installation and production-architecture
  instructions.
- Remove TV Bro Gecko asset selection from all updater documentation.
- Keep the isolated GeckoView 155/156 failure results only as historical
  rationale, clearly marked as no longer active.
- Verify the final Gradle dependency graph contains no Mozilla/Gecko artifact.
- Verify the final device package/process list contains no experimental Gecko
  probe.

#### Documentation and tests

Update all active surfaces:

- root `README.md`;
- `docs/ARCHITECTURE.md`;
- `docs/STREAMING_APPS.md`;
- `docs/STREAMING_SERVICE_TEST_PROCEDURE.md`;
- `docs/ROLLBACK.md`;
- `device-tests/README.md`;
- install, update, troubleshooting, and friend-facing setup procedures.

Document the replacement browser's:

- package and explicit component;
- release source;
- signer and digest verification;
- update process;
- supported destination list;
- authentication/profile behavior;
- service-specific UA policies;
- uninstall and rollback procedures.

Add or update tests for:

- destination-to-policy mapping;
- browser package/component validation;
- signer/version validation;
- typed launch failures;
- HUD cleanup after launch failure;
- update version comparison if retained;
- navigation allowlists in the media-browser project.

Run:

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug
```

Also inspect the final dependency graph and repository:

```bash
./gradlew :app:dependencies --configuration debugRuntimeClasspath
rg -n -i \
  'tv bro|tvbro|tvwebbrowser|firefox|mozilla|geckoview|check_firefox' \
  app README.md docs scripts device-tests
```

Every remaining match must be either:

- a deliberately retained historical finding;
- a migration/rollback record; or
- removed.

### Phase F: Final physical validation after cleanup

After repository and device cleanup:

1. install the final RobPelo and media-browser builds in place;
2. confirm RobPelo remains default HOME;
3. confirm the intended general browser-role state;
4. launch all five services from RobPelo;
5. confirm each opens only the replacement media browser;
6. verify authentication, protected playback, fullscreen, and HUD;
7. close each service and verify HUD cleanup;
8. confirm no ride service or Affernet binding remains at rest;
9. force-stop and relaunch the media browser;
10. reboot the bike;
11. repeat the lightweight five-service check;
12. verify TV Bro, Firefox, and experimental Gecko packages remain absent;
13. verify no RobPelo UI offers TV Bro or Firefox install/update actions.

### Rollback after decommission

Code rollback remains available through the pre-cleanup Git checkpoint.

Device rollback can reinstall the exact official TV Bro and Firefox APKs using
their recorded URLs, hashes, and signing certificates. Reinstallation does not
restore deleted cookies or sessions; users must sign in again.

If the replacement fails after cleanup:

1. stop the HUD;
2. reinstall the previously authenticated official TV Bro build;
3. select the documented TV Bro engine and UA settings;
4. sign in to required services again;
5. install the last known-good RobPelo APK or restore the pre-cleanup source
   checkpoint;
6. restore the intended general browser role if Firefox is reinstalled;
7. verify HOME remains RobPelo.

Never restore browser functionality by modifying Peloton packages or the system
WebView.

## Safety and rollback

Maintain the existing reversible constraints:

- no root;
- no bootloader unlock;
- no system-partition changes;
- no Peloton APK changes;
- no Peloton package disabling or uninstalling;
- no Peloton app-data clearing;
- no default-browser change during initial testing;
- no HOME change during browser testing;
- no LockTask/device-owner changes during browser testing.

Install candidates only as independent user APKs. Verify package, ABI, SDK,
digest, and signer before installation.

Keep TV Bro and current RobPelo routing intact until Brave passes and the
replacement browser is independently validated.

After the replacement passes the cleanup acceptance gate, follow the staged
decommission procedure above. Browser uninstallation remains a separately
approved destructive step because it deletes local profiles and authentication
state.

## Fresh-chat starting point

Suggested prompt:

> Continue the RobPelo Brave media-browser investigation using
> `docs/BRAVE_MEDIA_BROWSER_PROPOSAL.md` as the authoritative handoff. The
> Peloton may or may not be connected. Do not implement a Custom Tabs
> production architecture. First verify device connectivity. If connected,
> perform Stage 1 only: review and authenticate the official Brave arm64 APK,
> install it as an additional reversible package, and test the documented
> compatibility matrix without changing HOME, default browser, TV Bro, or
> Peloton packages. Do not begin a Brave/Chromium fork until the Stage 1 success
> criteria are met. If the final replacement later passes the cleanup acceptance
> gate, follow the staged decommission plan; ask for explicit confirmation
> immediately before uninstalling TV Bro or Firefox.

## Related documentation

- [Streaming-app research](./STREAMING_APPS.md)
- [Streaming-service test procedure](./STREAMING_SERVICE_TEST_PROCEDURE.md)
- [Architecture](./ARCHITECTURE.md)
- [Rollback](./ROLLBACK.md)
- [Physical-device results](../device-tests/README.md)
