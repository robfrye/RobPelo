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
