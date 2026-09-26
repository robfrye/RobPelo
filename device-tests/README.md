# On-device validation

Tested on 2026-09-20 against the connected `PLTN-RB1VQ`, Android 11/API 30,
build `RQ.260424.A`.

## Stage 1: Affernet telemetry probe

- Installed `com.robpelo.companion` as a normal user APK.
- Bound successfully to
  `com.onepeloton.affernetservice.IV1Interface`.
- Received approximately one frame per second.
- Stationary reading: cadence 0, resistance 36, raw power 0, output 0 W.
- Pedaling reading captured: cadence 50, resistance 0, raw power 700,
  output 7 W.
- The raw-power result confirms the `/100` conversion on this firmware.
- Returning HOME removed the Affernet binding.
- Relaunching restored the binding and telemetry stream.
- No crashes were observed.

Raw screenshots and command captures were retained locally during development
but are intentionally excluded from Git.

## Stage 2: Just Ride

- Foreground service started successfully with notification ID 1001.
- Just Ride received live telemetry.
- Elapsed time advanced.
- A test ride accumulated 0.12 mi and 1.4 kJ.
- Pause/resume was exercised by the user.
- End Ride removed the foreground service and Affernet binding.
- No crashes were observed.

## Stage 3: Netflix HUD

- Android's standard overlay settings UI successfully granted access.
- Netflix launched through its package launch intent.
- Netflix remained the focused application.
- WindowManager reported a visible `TYPE_APPLICATION_OVERLAY` owned by
  `com.robpelo.companion`.
- The HUD measured 683 x 119 pixels and did not take keyboard focus.
- The foreground service and Affernet binding remained active under Netflix.
- Netflix's secure surface prevented screenshot capture; the empty PNG is
  retained as evidence of that platform behavior.
- The HUD Close control removed the overlay, service, and Affernet binding
  while leaving Netflix running.

## Stage 4: HOME

- Installing the HOME-capable build caused Android to return its resolver until
  a HOME was selected; it did not silently select RobPelo.
- RobPelo was explicitly selected and launched as HOME.
- The stock Peloton HOME was restored successfully.
- RobPelo was selected again after rollback validation.
- Both Just Ride and Netflix + HUD tiles work from RobPelo HOME.
- The Peloton Home utility button opens
  `com.peloton.activity/com.peloton.activation.ActivationActivity`.
- Opening Peloton does not change the default HOME; pressing Home returns to
  RobPelo.
- The Settings utility button opens
  `com.android.settings/.homepage.SettingsHomepageActivity`.
- Opening Settings does not change the default HOME; pressing Home returns to
  RobPelo.
- Ending each experience removed its service and Affernet binding.
- The final corrected build `0.4.5-home` was installed and smoke-tested.
- Final default HOME: `com.robpelo.companion/.HomeActivity`.
- Final at-rest state has no HUD window, foreground ride service, or companion
  Affernet binding.
- Twelve unit tests pass: five ride-session tests, two power-scaling tests, and
  five Firefox-version/update-availability tests.

## Observed platform behavior

- Adding a second HOME-capable package caused Android's HOME resolver to become
  active until a default was explicitly selected. It did not silently select
  RobPelo.
- Netflix marks its content surface secure, so Android did not permit screenshot
  capture while Netflix was foreground. WindowManager and SurfaceFlinger state
  were used to verify the HUD instead.
- Peloton's own subscription/activation banner can appear over third-party
  activities. RobPelo does not disable or modify the Peloton component that
  owns that banner.

## Historical YouTube in Firefox

- Final tested RobPelo version: `0.6.2-firefox-tab-reuse`.
- The YouTube tile opened `https://m.youtube.com` explicitly in
  `org.mozilla.firefox`.
- Firefox became the foreground application.
- The RobPelo telemetry HUD remained visible and non-focusable above Firefox.
- Closing the HUD removed the overlay, foreground service, and Affernet
  binding.
- Pressing Home returned to RobPelo.
- Firefox's direct single-task activity and Android browser reuse hint were
  verified to keep the tab count unchanged across repeated YouTube launches.
- The earlier generic URL-intent approach was rejected because Firefox created
  one additional tab per launch.

## Firefox update

- Updated Firefox from 134.0 (`versionCode=2016064978`) to Mozilla's official
  arm64 Firefox 156.0 (`versionCode=2016183650`).
- The package remained `org.mozilla.firefox`.
- The installed and candidate certificate SHA-256 digests matched.
- The Firefox data inode remained unchanged, confirming an in-place update.
- Firefox launched without a crash after the update.
- YouTube and the RobPelo HUD continued to work.

## TV Bro Gecko browser

- Installed official TV Bro 2.1.6 arm64 Gecko-included APK.
- GitHub release SHA-256 matched:
  `210071cb2e728d6250635025d699c9c9c3ed3b71d74e7759a55c655d8911f285`.
- Selected TV Bro's bundled GeckoView engine; the setting persisted across
  reboot.
- External YouTube launches hid TV Bro's action bar.
- YouTube sign-in succeeded.
- Login persisted across force-stop/relaunch and a full bike reboot.
- Video, audio, fullscreen, and the RobPelo HUD all worked together.
- Camera, microphone, and location permissions remained denied.
- RobPelo remained default HOME and its service cleaned up normally.

## TV Bro System WebView

- Switched TV Bro 2.1.6 from GeckoView to its System WebView engine.
- Selected the **Chrome (Desktop)** user-agent preset.
- `https://httpbin.org/user-agent` confirmed that requests used TV Bro's
  Windows Chrome 145 desktop user agent rather than an Android mobile user
  agent.
- HBO Max no longer redirected to `/intercept/mobile` or attempted to launch
  the unavailable native Android app.
- The existing authenticated HBO session loaded the HBO Max Home page and was
  visible on the Peloton display.
- ADB screenshots of HBO Max were entirely black even while the physical
  display rendered the page.
- System WebView was noticeably more responsive than bundled GeckoView on the
  reference bike.
- The WebView browser-data store differs from GeckoView's. Netflix showed its
  sign-in form after the switch, so previously authenticated services may need
  a one-time sign-in again.
- Lightweight checks confirmed that Netflix, YouTube, Prime Video, and Apple
  TV all rendered under System WebView.
- HBO playback, DRM, fullscreen, process-restart persistence, and reboot
  persistence remain untested in this configuration.
- System WebView with the desktop Chrome user agent is now the selected
  configuration for all RobPelo streaming tiles.

## Historical on-device Firefox update checks

- Installed RobPelo version: `0.6.1-firefox-installer`.
- RobPelo fetched Mozilla's stable mobile-version feed from HOME.
- The UI correctly reported `Firefox 156.0 is current`.
- Periodic checks are limited to once every 24 hours while HOME is opened.
- No APK is downloaded unless an available-update button is tapped.
- The Android unknown-source app-op remained at its default value because no
  installation was needed.
- The future-update installer path is implemented but cannot be exercised until
  Mozilla publishes a version newer than 156.0.
- A missing Firefox installation is treated as an available install and is
  authenticated against Mozilla's pinned release certificate.
- The missing-Firefox decision path is unit tested; the full first-install
  package-installer flow was not destructively tested because Firefox was
  already installed on the reference bike.

These Firefox-specific UI paths were later replaced by the tested TV Bro
integration. The Mac-side Firefox updater remains available.

## Current TV Bro integration

- RobPelo version: `0.7.1-compact-tiles`.
- YouTube + HUD launches
  `com.phlox.tvwebbrowser/.activity.main.MainActivity` with
  `https://m.youtube.com`.
- TV Bro's external URL launch hides its action bar.
- System WebView with the desktop Chrome user agent is now the selected engine.
- YouTube login persists after process restart and full bike reboot.
- Video, audio, video fullscreen, and RobPelo HUD work together.
- RobPelo checks the official TV Bro GitHub release at most once daily while
  HOME is active.
- The installer verifies GitHub's digest, package ID, SDK support, version code,
  and pinned signing certificate.
- The first-install/update handoff is implemented but was not destructively
  exercised because uninstalling TV Bro would remove the tested signed-in
  profile and GeckoView selection.
- The launcher now uses smaller 300×170 dp tiles in a four-column grid.
- Visible streaming labels are `Netflix` and `YouTube`; both still start the
  HUD automatically.

## Netflix in TV Bro

- Peloton SystemUI force-stops the built-in Netflix APK on inactive-
  subscription bikes.
- The RobPelo Netflix tile opens `https://www.netflix.com/browse` in TV Bro.
- Netflix sign-in, video, audio, fullscreen, and HUD all work.
- Netflix login persisted after TV Bro force-stop/relaunch.
- Netflix login persisted after a full bike reboot.
- The vendor Netflix APK remains installed and unchanged.

## Additional streaming tiles

RobPelo `0.8.0-streaming-grid` adds:

- HBO Max: `https://play.hbomax.com/`
- Prime Video: `https://www.primevideo.com/region/na/`
- Apple TV: `https://tv.apple.com/`

Only the lightweight procedure was used:

- each URL rendered in TV Bro;
- TV Bro remained foreground;
- the HUD appeared;
- HUD cleanup stopped the ride service and Affernet binding;
- RobPelo remained default HOME.

No authentication, persistence, playback, fullscreen, DRM, or reboot tests were
run for these services.

## Brave compatibility validation

Brave Stage 1 was run on 2026-09-23 without changing RobPelo's production tile
routing:

- Downloaded official Brave `1.95.104` / Chromium `153.0.8010.53` from the
  Brave GitHub release.
- Verified `Bravearm64Universal.apk` SHA-256
  `c6cc741f38b2efa3fc3e18e0be93fa4bb25a28bb93530f3f04f9f64dee05a2d8`.
- Verified package `com.brave.browser`, minimum SDK 29, target SDK 36, and
  `arm64-v8a` native ABI.
- Verified signing-certificate SHA-256
  `9C:2D:B7:05:13:51:5F:DB:FB:BC:58:5B:3E:DF:3D:71:23:D4:DC:67:C9:4F:FD:30:63:61:C1:D7:9B:BF:18:AC`.
- Installed Brave as an additional user package. TV Bro and Firefox remained
  installed, and RobPelo remained the resolved HOME.
- Declined Brave's optional Web Discovery, crash-reporting, and product-insight
  onboarding choices.
- YouTube website sign-in succeeded.
- YouTube authentication survived a controlled Brave force-stop/relaunch after
  allowing the profile to flush. An immediate first retry returned signed out,
  so future test procedures should not force-stop Brave immediately after
  authentication.
- YouTube authentication survived a normal bike reboot.
- YouTube video playback and fullscreen succeeded.
- Prime Video's protected-content permission, protected playback, fullscreen,
  and visibly HD-or-better playback succeeded.
- HBO Max website sign-in succeeded. HBO Max required Brave's persistent
  per-site **Desktop site** setting; protected playback and fullscreen then
  succeeded.
- Brave used approximately 212 MB PSS / 310 MB RSS during onboarding and
  approximately 157 MB PSS / 230 MB RSS during YouTube playback.
- No Brave fatal crash was observed.

The installed Peloton app owned a visible `TYPE_APPLICATION_OVERLAY` that
caused Android to reject Brave's protected-content permission dialog with a
"close any bubbles or overlays" message. The RobPelo HUD service was not
running. After explicit approval, `com.peloton.activity` was temporarily
force-stopped, the overlay disappeared, and the permission prompt succeeded.
A normal reboot restored the Peloton process.

Brave's onboarding and later reboot temporarily changed the general browser
role. The pre-test holder, Firefox, was explicitly restored after testing.
RobPelo remained HOME throughout.

At the user's direction, the remaining Netflix and Apple TV protected-title
checks and the remaining Brave HUD/Back checks were skipped. They are not
recorded as passes.

## RobPelo Media Browser Debug build rejection

The first Brave-derived `com.robpelo.browser` APK was installed on 2026-09-25:

- package `com.robpelo.browser`;
- version `1.95.104` / version code `429510404`;
- minimum SDK 29, target SDK 36;
- arm64-v8a only;
- signature permission and signer matched RobPelo;
- the explicit media activity resolved correctly;
- no general HTTPS handler was exposed;
- RobPelo remained HOME;
- Firefox remained the general browser-role holder;
- TV Bro and Firefox remained installed;
- Prime Video opened without starting the HUD;
- Prime Video website sign-in succeeded.

The build was rejected before protected-title acceptance because it was too
laggy for service navigation. Official Brave, Firefox, and TV Bro were not
running. Under navigation load, the Debug build's browser, renderer,
privileged process, and zygote consumed roughly 740 MB total PSS and the device
was already using roughly 570 MB of swap. A cold browser restart did not make
the page usable.

The browser was force-stopped, RobPelo HOME was restored, and no ride service
or Affernet binding remained active. The browser package and authenticated
profile were retained for reversible diagnosis. Future device testing requires
an optimized non-official `Static` build; do not repeat acceptance testing with
the Debug artifact.

## RobPelo Media Browser Static build findings

The optimized non-official Static build was installed in place on 2026-09-25:

- APK SHA-256
  `76c95e476e079a83bd22c648b40d714af3c35ffdb646733ec4681b10171a4092`;
- package, version, SDK, ABI, signer, and restricted manifest contract passed
  independent Dev Box and Mac verification;
- `adb install -r` preserved browser profile inode `472393` and the existing
  Prime Video authentication;
- RobPelo remained HOME;
- Firefox remained the general browser-role holder;
- TV Bro and Firefox remained installed;
- no general HTTPS handler was exposed;
- the no-HUD Prime Video setup launch worked;
- Prime Video navigation reached a show page.

Static improved memory pressure but remained heavy. During the protected-title
attempt its browser, renderer, privileged process, and zygote used roughly
660 MB total PSS, compared with roughly 740 MB for Debug.

Two browser-integration failures blocked protected playback:

1. Chromium tab-modal prompts displayed their dimming scrim while
   `tab_modal_dialog_container` remained zero-sized in the frameless activity.
   Pressing Back dismissed the invisible modal and scrim, proving the renderer
   was not frozen, but also dismissed the required permission.
2. Prime's mobile presentation navigated to an
   `intent://app.primevideo.com/watch` URL targeting
   `com.amazon.avod.thirdpartyclient`. The installed native Prime app briefly
   became foreground and triggered Peloton's subscriber-only enforcement.
   The media browser rejected the top-level intent but did not prevent
   Chromium's earlier external-app handoff.

The approved temporary `com.peloton.activity` force-stop removed all Peloton
overlay windows, but Prime still attempted the native-app handoff and the
hidden modal returned. A normal reboot restored the Peloton process, RobPelo
HOME, Firefox's browser role, and the idle no-service state.

The first follow-up build displayed tab-modal prompts correctly and prevented
the native-app handoff by forcing Prime's desktop presentation, but Prime then
returned `Video Unavailable`. A controlled comparison proved:

- official Brave remained foreground while the same protected title played;
- `com.amazon.avod.thirdpartyclient` remained stopped;
- official Brave's **Desktop site** setting was off;
- official Brave logged the same unsupported VP8 probes and Widevine cleanup
  warnings while playback succeeded, so those messages were not the failure.

The next build must therefore:

- keep Prime Video on its normal user agent;
- disable external-app intent requests for the dedicated
  `com.robpelo.browser` Custom Tab so Prime cannot hand off to its native app;
- unwrap and validate Prime's embedded HTTPS watch target from the blocked
  `intent://` navigation, then load it in the existing tab;
- retain the full-size visible Chromium tab-modal fallback while ordinary
  browser chrome remains hidden.

### r3 Prime Video acceptance

The r3 Static build implemented those changes and passed the Prime Video
physical checks:

- APK SHA-256
  `d0f828a88a84c51d656eef66ef800a7a3be61e5b613680f18316ff268ecb9d0c`;
- installation preserved profile inode `472393`;
- the Widevine authorization dialog was visible and interactive;
- the one-time authorization restarted the browser process and opened
  `com.google.android.apps.chrome.Main` inside `com.robpelo.browser`;
- returning to RobPelo and relaunching the media activity preserved the
  authorization;
- Prime website authentication persisted;
- protected video and audio played smoothly;
- fullscreen and the RobPelo telemetry HUD worked together;
- `com.amazon.avod.thirdpartyclient` remained stopped during playback;
- closing the HUD removed the overlay, foreground ride service, and RobPelo
  Affernet binding while leaving the browser alive;
- Back returned from playback to Prime and then to RobPelo HOME without a
  general browser screen;
- force-stop/relaunch preserved the explicit media-browser route,
  authentication, protected playback, and HUD behavior;
- a normal reboot preserved HOME, Firefox's browser role, the media-browser
  route, authentication, protected playback, and HUD behavior;
- TV Bro and Firefox remained installed throughout.

Prime's Play/Pause controls were noticeably laggy, but an immediate controlled
comparison with official Brave showed similarly laggy controls on the same
title. This is therefore treated as a Prime/reference-device limitation rather
than a RobPelo regression. During r3 playback with the HUD, the browser
processes used roughly 786 MB total PSS and the companion used roughly 102 MB
PSS.

The one-time Widevine authorization restart exposing the package's ordinary
tabbed browser is retained as a setup-flow caveat. Normal tile launches,
playback, Back, Home, process restart, and reboot did not expose the general
browser surface.

## Reboot validation

A normal `adb reboot` was tested after RobPelo became the default HOME:

- Android completed boot successfully.
- RobPelo remained the resolved default HOME.
- `HomeActivity` became the foreground activity automatically.
- Overlay access remained granted.
- No ride service or Affernet connection started at boot.
- The yellow Peloton subscription banner was absent at 15 seconds, 75 seconds,
  and approximately three minutes after boot.
- The Peloton activity process had restarted by the 75-second check, but its
  visible subscription notification had not returned.
- The banner remained absent after opening Just Ride.

This shows that a normal reboot clears the current transient notification state.
It does not prove that Peloton will never post the notification again after a
later account or subscription refresh.
