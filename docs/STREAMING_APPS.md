# Streaming App Compatibility

Investigation date: 2026-09-20.

No third-party APK was downloaded or installed during this investigation.

## Device capabilities

The tested Peloton provides:

- Android 11 / API 30
- `arm64-v8a`
- touchscreen and Wi-Fi
- Android WebView
- picture-in-picture framework support
- running DRM and MediaDrm system services
- 6.5 GB free under `/data` at the time of inspection

It does not contain:

- Google Play Store (`com.android.vending`)
- Google Play services (`com.google.android.gms`)
- Google Services Framework (`com.google.android.gsf`)
- a Google account authenticator
- Google framework or privileged-permission XML files

The bootloader is locked, Verified Boot reports `green`, and SELinux is
enforcing.

Android 11 and the CPU architecture are therefore not the main limitations.
The likely limitations are official app distribution, app ownership,
Play/device certification, complete split-APK delivery, and DRM provisioning.

## HUD compatibility

RobPelo's HUD is independent of Netflix. It is a standard
`TYPE_APPLICATION_OVERLAY` owned by RobPelo's foreground ride service.

It was tested successfully over the installed Prime Video application:

```text
Package: com.amazon.avod.thirdpartyclient
Foreground activity:
  com.amazon.avod.client.activity.HomeScreenActivity
HUD: visible, 683 x 119, non-focusable
```

This confirms that another video option can reuse the existing telemetry and
HUD implementation. Each app still needs an individual playback and
authentication test.

The HUD must not cover sign-in, purchase, PIN, consent, CAPTCHA, or update
controls. RobPelo should launch only explicitly allowlisted packages and should
not inspect protected video content.

## Prime Video

Prime Video is already installed and enabled:

```text
Package: com.amazon.avod.thirdpartyclient
Version: 3.0.391.157
Version code: 391000157
Minimum SDK: 21
Target SDK: 34
ABI: arm64-v8a
Launch activity: .LauncherActivity
```

It launches successfully, and RobPelo's HUD displays over it. The installed
build then presents this mandatory-update failure:

```text
FAILED_TO_START_IN_APP_UPDATE_FLOW
Go to Google Play to get the updated app.
```

This is consistent with a Google Play Core update flow on a device without Play
Store. Play Core updates depend on the Play Store, a Play-owned application,
matching signatures, and update eligibility:

- [Android in-app updates](https://developer.android.com/guide/playcore/in-app-updates)
- [Play Core InstallErrorCode](https://developer.android.com/reference/com/google/android/play/core/install/model/InstallErrorCode)

Amazon's current instructions direct Android users to their device app store
and provide no supported standalone Prime Video APK:

- [Install Prime Video](https://www.primevideo.com/help?nodeId=GQDJBTL3CFLLKPBV)
- [Supported mobile devices](https://www.primevideo.com/help?nodeId=G97NYNWZ7BB9CNDW)

Amazon ended Amazon Appstore support for ordinary Android devices on
2025-08-20; it remains available on Fire TV and Fire Tablet:

- [Amazon Appstore announcement](https://developer.amazon.com/apps-and-games/blogs/2025/02/upcoming-changes-to-amazon-appstore-for-android-devices-and-coins-program)

### Prime conclusion

The app and HUD are technically compatible with the tablet. The blocker is the
mandatory unsupported update path. A lawfully obtained, complete, consistently
signed newer build might run, but a newer sideload could encounter the same
store-ownership check. No reliable official sideload-only update path was
found.

## Netflix

Peloton's built-in Netflix APK is now blocked by Peloton's privileged SystemUI
when the bike subscription state is inactive. Direct launch starts Netflix,
then Peloton broadcasts `ACTION_3P_INVALID_ACCESS` and force-stops the Netflix
process. Peloton's official entertainment action redirects to activation.

RobPelo therefore launches Netflix web in TV Bro GeckoView. Sign-in, playback,
fullscreen, HUD coexistence, process-restart persistence, and full-reboot
persistence were verified.

Netflix does not officially list ordinary Android browsers as supported
netflix.com playback devices. Widevine security level and achieved resolution
were not measured. The result proves playback on the tested configuration, not
Netflix certification, L1, HD, Full HD, HDR, or future compatibility.

See [Peloton Netflix subscription gating](./NETFLIX_GATING.md).

## HBO Max

Current Android package:

```text
com.wbd.stream
```

HBO Max documents Android 7.1 or later, so Android 11 meets the OS floor.
However, HBO Max says Android installation outside the official Google Play
Store is unsupported. Its Android TV guidance additionally requires
Google Play- and Dolby-certified devices:

- [HBO Max supported devices](https://help.max.com/us-en/Answer/Detail/000002506)
- [Google Play listing](https://play.google.com/store/apps/details?id=com.wbd.stream)

HBO Max is not installed on this bike.

### HBO Max conclusion

A compatible APK or complete split set might install, but successful sign-in
and protected playback are uncertain because this is not a Play-certified
device and no supported standalone APK is available. Installation alone would
not prove DRM playback.

## YouTube

The official YouTube package is:

```text
com.google.android.youtube
```

It is not installed. Google distributes and updates it through Google Play, and
Google account, Cast, notification, and integrity-dependent behavior may fail
without Play services.

RobPelo now includes a **YouTube + HUD** tile that opens
`https://m.youtube.com` explicitly in Firefox. This path has been validated on
the reference bike:

- Firefox was updated in place from 134.0 to Mozilla's official 156.0 arm64
  build; package and signing certificates matched and app data was preserved.
- Firefox launched successfully.
- The YouTube mobile site loaded.
- The telemetry HUD remained visible above Firefox.
- Closing the HUD cleaned up the service and Affernet binding.

Other options:

1. NewPipe from its official GitHub releases or F-Droid for ordinary public
   YouTube playback without Google Play services.

NewPipe does not replace Google-account features, purchases, rentals, YouTube
Primetime Channels, or DRM-protected content:

- [NewPipe](https://github.com/TeamNewPipe/NewPipe)
- [YouTube app compatibility](https://support.google.com/youtube/answer/6098135)

## Google Play Services and Play Store

### Can the APKs be sideloaded?

Individual compatible APKs may install into `/data/app` as normal user
applications. That is not equivalent to installing a supported Google Mobile
Services environment.

A complete Google stack normally includes:

- Google Play services / GMSCore
- Google Play Store
- Google Services Framework
- Google account/authentication components
- OEM framework configuration
- privileged-permission allowlists
- Google certification and provisioning

Normal APK installation cannot:

- install components as privileged system applications;
- add system permission allowlists;
- add missing SELinux policy;
- change platform signatures;
- modify verified system/product/vendor partitions;
- make the device Play Protect certified;
- create Play ownership for sideloaded applications.

Relevant platform documentation:

- [Google Play services overview](https://developers.google.com/android/guides/overview)
- [Google Play services setup](https://developers.google.com/android/guides/setup)
- [Play Protect certification](https://support.google.com/googleplay/answer/7165974)
- [Android compatibility program](https://source.android.com/docs/compatibility/overview)
- [Privileged permission allowlisting](https://source.android.com/docs/core/permissions/perms-allowlist)
- [Verified Boot](https://source.android.com/docs/security/features/verifiedboot)
- [SELinux](https://source.android.com/docs/security/features/selinux)

### Would it fix Prime Video?

Installing only `com.google.android.gms` would not fix Prime's update flow.
That flow depends on the Play Store itself, Play ownership, package signature,
account state, and backend eligibility.

Even a user-installed Play Store plus GMSCore would not make this Peloton a
certified Google device or guarantee a valid Play Integrity verdict.

Play Integrity can separately report:

- whether the app is Play-recognized;
- whether the account is licensed for it;
- whether the device meets integrity requirements.

See [Play Integrity verdicts](https://developer.android.com/google/play/integrity/verdicts).

Installing GMS also does not upgrade or provision Widevine L1. DRM provisioning
is separate from Play Services:

- [Android DRM framework](https://source.android.com/docs/core/media/drm)
- [Media3 DRM support](https://developer.android.com/media/media3/exoplayer/drm)

### Recommendation

Do not install an unofficial GApps bundle or individually sourced proprietary
Google APK set. The expected benefit is low and the risks include:

- entering Google credentials into an unofficially assembled stack;
- mismatched component and signature versions;
- background crash loops and battery/network use;
- no coherent security-update path;
- Play Store appearing to work while certification, ownership, or DRM still
  fails;
- pressure to modify verified system partitions when user installation is not
  sufficient.

An official Peloton/OEM image with licensed GMS would be different, but no such
option was identified.

## Other practical video options

Most promising:

- YouTube through Firefox
- NewPipe for non-DRM YouTube playback
- Plex, Jellyfin, or VLC for personally controlled media
- other apps that provide a single arm64-compatible APK and do not require
  Google Play ownership, Play Integrity, or vendor DRM certification

Less promising:

- Prime Video until its update path is solved
- HBO Max
- Disney+, Hulu, and similar store- and DRM-dependent services

## Recommended RobPelo direction

Generalize the current Netflix launcher into an allowlisted video-app model:

```text
Video option
  - display label
  - package name
  - optional explicit activity
  - requires HUD
```

Add options only after the exact app is installed and successfully tested.
Prime Video can be added as an experimental tile immediately, but it should
show a warning that the currently installed version is blocked by a mandatory
Google Play update.

The browser-based YouTube tile is now implemented and is the recommended
non-GMS video option. An independently reviewed NewPipe installation could be
considered later, but an unofficial Google services stack should not be used.

Firefox does not have a working Play Store update path on this device. Use the
repository's [`scripts/update-firefox.sh`](../scripts/update-firefox.sh) helper
for manual updates from Mozilla's official stable archive.

### Firefox PWA/fullscreen investigation

Firefox 156 exposes these PWA-related entry points:

```text
mozilla.components.feature.pwa.VIEW_PWA
mozilla.components.feature.pwa.PWA_LAUNCHER
mozilla.components.feature.pwa.WebAppLauncherActivity
org.mozilla.fenix.IntentReceiverActivity
```

However, this does not provide a reliable chrome-free YouTube launch:

- Launching `VIEW_PWA` for `https://m.youtube.com` on the bike brought Firefox's
  ordinary `HomeActivity` forward with its tab strip and address bar intact.
- YouTube's current web manifest declares `"display": "minimal-ui"`, not
  `"standalone"` or `"fullscreen"`. A standards-compliant PWA host is therefore
  allowed to retain browser controls.
- `WebAppLauncherActivity` expects Firefox's internal record of a previously
  installed web app and shortcut metadata. There is no stable external API for
  RobPelo to create that record.
- Firefox's normal install/add-to-home flow expects launcher shortcut support.
  RobPelo does not currently implement Android pinned-shortcut hosting.
- A Trusted Web Activity cannot solve this because it requires Digital Asset
  Links proving that the Android application and `youtube.com` have the same
  owner.

Conclusion: Firefox PWA mode is not a dependable way to provide an immediately
fullscreen YouTube browsing experience. The practical options remain:

1. use Firefox normally and enter fullscreen from the selected video; or
2. build a dedicated RobPelo WebView experience and accept its sign-in and
   compatibility limitations.

### Dedicated embedded YouTube investigation

A dedicated RobPelo activity can remove browser tabs and address bars because
RobPelo would own the entire window. There are two possible rendering engines.

#### Android System WebView

The bike's only valid WebView provider is:

```text
Package: com.android.webview
Version: 127.0.6533.10
Minimum SDK: 26
Target SDK: 34
Provider mode: system/updated-system app
```

Android WebView does not include browser chrome, so it can render a page inside
an immersive RobPelo activity. RobPelo would need to supply navigation,
fullscreen video handling, permissions, lifecycle, downloads, and errors.

For HTML5 fullscreen, the activity must implement
`WebChromeClient.onShowCustomView()` and `onHideCustomView()`, place the custom
video view in its fullscreen container, and restore system bars and orientation
when playback exits:

- [Android WebView guide](https://developer.android.com/develop/ui/views/layout/webapps/webview)
- [WebChromeClient.onShowCustomView](https://developer.android.com/reference/android/webkit/WebChromeClient#onShowCustomView(android.view.View,android.webkit.WebChromeClient.CustomViewCallback))

The major blocker is security maintenance. Chromium/WebView 127 dates from July
2024 and is substantially behind current browser security fixes. This Peloton
has no Play Store update path and its firmware allowlists only the existing
system provider. A normal user APK cannot register itself as a replacement
WebView provider:

- [WebView provider requirements](https://chromium.googlesource.com/chromium/src/+/HEAD/android_webview/docs/webview-providers.md)
- [AOSP WebView integration](https://chromium.googlesource.com/chromium/src/+/HEAD/android_webview/docs/aosp-system-integration.md)

System WebView is suitable for a short proof of concept but should not be the
long-term Internet-facing engine unless Peloton supplies WebView updates.

#### Mozilla GeckoView

GeckoView embeds Mozilla's browser engine as an application library rather than
relying on Android's system WebView provider:

- [GeckoView quick start](https://firefox-source-docs.mozilla.org/mobile/android/geckoview/consumer/geckoview-quick-start.html)
- [GeckoView API documentation](https://mozilla.github.io/geckoview/javadoc/mozilla-central/)

Current Mozilla metadata provides GeckoView 155 with minimum API 26, so it is
compatible with this API 30 bike. The uncompressed multi-ABI AAR is about
241 MB. An arm64-focused build would reduce delivered size, but RobPelo would
still become responsible for frequent Gecko security updates and considerably
more browser lifecycle code.

GeckoView provides explicit navigation, permission, storage, and fullscreen
delegates. `GeckoSession.ContentDelegate.onFullScreen()` lets RobPelo hide
system bars and adapt its own layout without Firefox's tabs or toolbar.

Tradeoffs:

- current browser engine independent of Play Store and Peloton firmware;
- no Firefox chrome;
- separate cookies/profile from the installed Firefox application;
- larger APK and memory footprint;
- more process, lifecycle, crash, media, and update testing;
- GeckoView must be upgraded through RobPelo releases.

#### Authentication limitation

Google blocks OAuth authentication in embedded user agents. This applies to
both Android WebView and GeckoView:

- [Google native-app OAuth guidance](https://developers.google.com/identity/protocols/oauth2/native-app)
- [Embedded-WebView enforcement](https://developers.googleblog.com/upcoming-security-changes-to-googles-oauth-20-authorization-endpoint-in-embedded-webviews/)

An embedded YouTube experience should therefore be considered anonymous by
default. It must not collect Google credentials. Sending authentication to
Firefox or a Custom Tab does not automatically transfer its cookies into
RobPelo's independent WebView or GeckoView profile.

Supporting an authenticated account would require a separate supported OAuth
and API design, likely with Google developer registration and a YouTube Data API
integration. That conflicts with the current local/no-cloud simplicity goal.

#### Default browser and cookie sharing

Changing Android's default browser does not make its cookies available to an
embedded WebView.

The reference bike currently reports:

```text
Browser role holder: org.mozilla.firefox
HTTPS handlers: org.mozilla.firefox only

Firefox:
  UID 10001
  dataDir /data/user/0/org.mozilla.firefox

RobPelo:
  UID 10006
  dataDir /data/user/0/com.robpelo.companion

System WebView provider:
  package com.android.webview
  UID 10056
  dataDir /data/user/0/com.android.webview
```

The other minimal component, `com.android.htmlviewer`, is a local HTML/file
viewer and is not registered as an HTTPS browser.

`com.android.webview` supplies rendering code to applications; it is not a
shared browser profile. Each embedding application owns its own WebView cookie,
local-storage, database, and cache directories under that application's UID.
Selecting another default browser changes which activity receives external
links but does not merge browser profiles or move cookies into RobPelo.

Therefore:

- signing into Firefox does not sign RobPelo's WebView into YouTube;
- making an AOSP/WebView-based browser the default would not share its cookies
  either;
- RobPelo cannot read or copy another browser's HTTP-only/private cookies
  because Android sandboxing separates their UIDs;
- using a Custom Tab would share the selected browser's login, but would retain
  browser-controlled UI;
- a Trusted Web Activity would require ownership verification for
  `youtube.com`.

#### YouTube API and telemetry placement

The supported programmable player is the
[YouTube IFrame Player API](https://developers.google.com/youtube/iframe_api_reference).
YouTube's
[required minimum functionality](https://developers.google.com/youtube/terms/required-minimum-functionality)
prohibits obscuring any part of the embedded player, including controls, with
another visual element.

A compliant immersive design should therefore use:

```text
+-----------------------------------------------+
|              YouTube player/web UI            |
|                                               |
+-----------------------------------------------+
| CADENCE 87 | RESISTANCE 42 | OUTPUT 186 W     |
| SPEED 16.8 mph | DISTANCE 4.2 mi | TIME 24:10 |
+-----------------------------------------------+
```

The telemetry rail remains inside RobPelo's activity but outside the player
rectangle. During true video fullscreen, RobPelo should hide the telemetry rail
rather than place it over the player. This embedded mode would not need
`SYSTEM_ALERT_WINDOW`.

#### Security boundary

Any prototype must:

- allow only HTTPS;
- restrict top-level navigation to required YouTube hosts;
- send Google account/OAuth navigation to an external browser;
- disable file access, content access, mixed content, geolocation, and WebView
  debugging;
- reject SSL errors;
- avoid broad `addJavascriptInterface` bridges;
- avoid inspecting YouTube DOM, cookies, account identifiers, recommendations,
  or ads;
- never download or extract video/audio streams;
- never suppress YouTube controls, attribution, links, or advertising.

#### Recommendation

If anonymous playback is acceptable, prototype a dedicated GeckoView activity
with:

1. one pinned stable GeckoView release;
2. `m.youtube.com` as the only initial destination;
3. no JavaScript/native privilege bridge;
4. strict HTTPS/navigation controls;
5. telemetry in a reserved bottom rail outside video bounds;
6. telemetry hidden during true player fullscreen;
7. physical tests for H.264/AAC, VP9, audio focus, suspend/resume, and memory
   pressure.

Do not ship the same feature on System WebView 127. If Google-account sign-in
or telemetry visibly over true fullscreen video is required, retain the
external Firefox approach instead.

### Physical prototype results

Two separate, non-HOME experimental APKs were built and tested without
modifying the working RobPelo installation.

#### GeckoView prototype

Tested:

- GeckoView 155 and 156 arm64 artifacts from Mozilla Maven
- Android compile SDK 37.1 and AGP 9.4 in an isolated build
- default and legacy/extracted native-library packaging
- normal and clean-memory launches

Both GeckoView versions initialized their native libraries and child processes,
then terminated the main application process after several seconds. Android
recorded the main process exit as `SIGKILL`. The same result occurred with
approximately 1 GB of available memory, so ordinary memory pressure was ruled
out. Native-library extraction did not change the result.

The installed Firefox 156 application continues to run on the same hardware,
but the standalone GeckoView embedding configuration is not compatible enough
to use for RobPelo without substantially deeper platform-specific debugging.
The experimental GeckoView APK was removed.

#### System WebView prototype

A separate 16 KB prototype using the installed
`com.android.webview` 127 provider succeeded:

- YouTube rendered without Firefox's tab strip or address bar.
- Browsing and search rendered correctly.
- Video playback worked.
- YouTube's fullscreen control worked through
  `WebChromeClient.onShowCustomView()`.
- The prototype requested only Internet access.
- RobPelo remained the default HOME.

Authentication did not transfer. The prototype handed Google sign-in to
Firefox as required by Google's embedded-user-agent policy. After successful
sign-in in Firefox, the embedded YouTube view still displayed
`You're not signed in`, confirming that Firefox and WebView use separate cookie
stores.

#### Updated recommendation

The fullscreen System WebView experience is technically functional for
anonymous playback. It should not replace the Firefox path because:

- WebView 127 is stale and cannot be safely updated independently on this
  firmware;
- authenticated YouTube state cannot be transferred from Firefox;
- Google sign-in inside the embedded view is unsupported;
- integrating it would create a second browser profile with separate cookies
  and storage.

Keep the tested Firefox route as the production path. Reconsider an embedded
experience only if Peloton ships an updated WebView provider or a future
GeckoView version/configuration is proven stable on this hardware.

## Alternative standalone browsers

Several browsers can be sideloaded without Google Play and bring their own
current rendering engine. None can guarantee completely chrome-free YouTube
browsing because YouTube's manifest requests `minimal-ui`, not `fullscreen`.
Normal video fullscreen remains separate and generally hides browser controls.

### Cromite

- Official source: [Cromite GitHub releases](https://github.com/uazo/cromite/releases/latest)
- Artifact: `arm64_ChromePublic.apk`
- Android requirement: Android 10+
- Engine: bundled Chromium, not System WebView
- Distribution: single official APK; built-in update notification/install flow
- Google/YouTube website login: likely to work as normal first-party browser
  navigation without Play Services
- Cookies: persistent normal profile
- Immersive behavior: normal external URL launches retain browser UI; an
  installed YouTube PWA may reduce chrome but YouTube requests `minimal-ui`

Cromite is the strongest security/maintenance test candidate but does not
guarantee a frameless browsing experience.

### Brave

- Official source: [Brave GitHub releases](https://github.com/brave/brave-browser/releases)
- Artifact: official monolithic `Bravearm64Universal.apk` with checksums and
  signatures
- Android requirement: Android 10+
- Engine: current bundled Chromium
- Google/YouTube website login: likely
- Cookies: persistent
- Custom Tabs: supported, but Custom Tabs retain a toolbar
- Immersive behavior: video fullscreen works; ordinary browsing retains Brave
  UI

Brave is the conservative current-engine alternative to Cromite.

### Vivaldi

- Official source and update guidance:
  [Vivaldi Android APK installation](https://help.vivaldi.com/android/android-install/android-install-and-update-vivaldi-mobile/)
- Official architecture-specific arm64 APK
- Android requirement: Android 10+
- Engine: bundled Chromium
- Google/YouTube website login and persistent cookies: likely
- PWA support: documented, but YouTube's `minimal-ui` still prevents assuming a
  truly frameless window
- Updates: manual for APK installations

Vivaldi has the clearest vendor-hosted direct APK path.

### TV Bro

- Official source: [TV Bro GitHub releases](https://github.com/truefedex/tv-bro/releases)
- Candidate artifact: `tvbro-2.1.6-generic-geckoIncluded-arm64-v8a.apk`
- Android requirement: API 26+
- Engine: bundled GeckoView 147 when selected; its default mode is System
  WebView
- External URL behavior: TV Bro source explicitly hides its action bar for an
  externally supplied URL
- Cookies: persistent outside incognito mode
- Google/YouTube login: plausible but must be tested
- Updates: generic build includes the project's updater

TV Bro is the browser most closely aligned with hidden UI. Its bundled Gecko
147 is materially older than Firefox 156, and the maintainer currently defaults
to WebView because Gecko is considered less stable/performance-efficient. The
reference bike also failed to run standalone GeckoView 155 and 156 probes, so
TV Bro's separately integrated Gecko mode required its own physical test.

#### Physical TV Bro results

TV Bro 2.1.6 was installed from the official GitHub release:

```text
Artifact: tvbro-2.1.6-generic-geckoIncluded-arm64-v8a.apk
SHA-256: 210071cb2e728d6250635025d699c9c9c3ed3b71d74e7759a55c655d8911f285
Package: com.phlox.tvwebbrowser
Version code: 69
Minimum SDK: 26
Target SDK: 36
```

The digest matched GitHub's release asset metadata. Camera, microphone, and
location permissions remained denied.

TV Bro defaults to System WebView. After explicitly selecting its bundled
GeckoView engine and accepting the one-time restart:

- TV Bro remained stable on the RB1VQ.
- External `https://m.youtube.com` launches hid TV Bro's action bar.
- YouTube rendered in a chrome-free layout.
- Google/YouTube sign-in succeeded inside TV Bro.
- The authenticated session survived a complete TV Bro force-stop/relaunch.
- The authenticated session survived a full bike reboot.
- Video and audio playback worked.
- YouTube video fullscreen worked.
- RobPelo's `TYPE_APPLICATION_OVERLAY` HUD remained visible and functional over
  both browsing and fullscreen playback.
- Closing the HUD removed the ride service and Affernet binding normally.
- RobPelo remained the default HOME throughout.

TV Bro uses several Gecko child processes and has a materially larger memory
footprint than the System WebView path. It should remain an explicitly launched
video browser rather than a permanent background process.

The built-in generic-build updater was identified but not yet exercised.

RobPelo now uses TV Bro for its production **YouTube + HUD** tile and includes
its own TV Bro installer/updater:

- checks `truefedex/tv-bro` official GitHub releases at most once per day while
  HOME is active;
- selects only the generic Gecko-included arm64 asset;
- validates GitHub's SHA-256 asset digest;
- validates package ID, API compatibility, and version code;
- pins TV Bro's release signing certificate;
- hands the verified APK to Android's user-confirmed installer.

After first install, users must select **GeckoView** once in TV Bro Settings and
restart TV Bro. RobPelo displays that setup guidance after installation.

### Fully Kiosk Browser

- Official source: [Fully Kiosk](https://www.fully-kiosk.com/en/)
- Provides true immersive/kiosk browsing and can hide status, navigation,
  action, address, and tab bars
- Uses Android System WebView
- On this bike that means Chromium/WebView 127
- Google OAuth may reject the embedded WebView
- Cookie persistence is configurable

Fully Kiosk best matches the visual goal but inherits the two major rejected
constraints: stale WebView 127 and unreliable Google sign-in.

### Native Alpha and Hermit-style wrappers

These provide frameless or immersive WebView wrappers but also inherit System
WebView 127 and Google's embedded-user-agent login restrictions. Native Alpha
has an official GitHub APK; Hermit's supported distribution is Google Play.
Neither is preferable to the already tested WebView prototype.

### Firefox Focus / Fennec F-Droid

- Fennec F-Droid is a current, independently updated Gecko browser with
  persistent cookies, but ordinary browsing still displays Firefox UI.
- Firefox Focus has a smaller UI but intentionally emphasizes private sessions
  and erase behavior, making persistent YouTube login a poor fit.

### Chrome and Chromium snapshots

Google Chrome's supported Android distribution channel is Google Play. Generic
Chromium snapshots are developer artifacts without an appropriate stable,
signed update channel. They are not recommended for a shareable RobPelo setup.

### Recommended reversible test order

1. **TV Bro Gecko-included arm64** if hidden UI is the primary goal. Startup,
   Google login, reboot persistence, fullscreen, and HUD coexistence are now
   verified on the reference bike; updater behavior remains to be tested.
2. **Cromite arm64** if current security updates and login reliability are more
   important than completely hidden UI. Test its YouTube PWA/minimal-UI mode.
3. **Brave arm64** as the conservative Chromium alternative.

Install any candidate as an additional package and launch it explicitly from a
temporary test command. Do not replace Firefox or change the default browser
until the candidate passes login, reboot persistence, fullscreen, HUD, and
update tests.
