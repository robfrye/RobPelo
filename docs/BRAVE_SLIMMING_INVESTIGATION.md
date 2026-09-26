# Brave media-browser slimming investigation

Investigation date: 2026-09-26.

The bike is not required for source/build optimization. Before disconnecting,
the reference bike was verified at RobPelo HOME with no media-browser process,
HUD, ride service, or RobPelo Affernet binding.

## Executive conclusion

The r3 build is dramatically larger than official Brave, but its active
playback memory is not dramatically worse. Official Brave showed similarly
laggy Prime Video Play/Pause controls on the same title. Feature removal should
therefore be expected to improve APK/install size, cold start, security surface,
and some browser/background overhead; it is unlikely to eliminate Prime's
service/device control latency.

The strongest source-backed runtime optimization is to move closer to
Chromium's official-optimized compiler configuration:

- disable DCHECKs and Java asserts;
- enable ThinLTO and its full optimizations;
- keep PGO off for the first experiment;
- retain Widevine and proprietary codecs.

The strongest low-risk feature trimming is:

- Brave Wallet;
- Rewards and Ads;
- VPN;
- Leo AI;
- Brave News;
- Talk;
- Web Discovery and stats;
- Request-OTR;
- Android XR/AR/VR.

Do not remove Playlist, Sync, WebAuthn, Bluetooth, the media stack, profile
storage, downloads, Custom Tabs, fullscreen, GPU/compositor, Widevine, or
proprietary codecs in the first slimming phase.

## Measured baseline

| Measurement | Official Brave 1.95.104 | RobPelo r3 Static |
|---|---:|---:|
| APK file | 207,050,612 bytes | 611,693,772 bytes |
| `libchrome.so`, uncompressed | 219,875,912 bytes | 418,582,568 bytes |
| Native libraries as stored in APK | 102,389,096 bytes | 442,027,920 bytes |
| Locales as stored in APK | 28,843,994 bytes | 66,218,172 bytes |
| Other assets as stored in APK | 24,277,664 bytes | 42,138,955 bytes |
| DEX as stored in APK | 7,580,263 bytes | 15,842,316 bytes |
| `resources.arsc` | 38,392,260 bytes | 39,429,268 bytes |

The custom package also contains XR libraries absent from the official
universal APK:

```text
libimpress_api_jni.so             18,641,032 bytes
libandroidx.xr.arcore.openxr.so      695,448 bytes
libandroidx.xr.runtime.openxr.so     559,672 bytes
libarcore_sdk_jni.so                 100,808 bytes
```

The custom package and official package both contain `libwg-go.so`
(3,366,160 bytes uncompressed).

## The 207 MB comparison is not fully like-for-like

The Brave v1.95.104 release provides different packaging formats:

- direct `BraveMonoarm64.apk`: approximately 357 MB;
- `BraveMonoarm64.aab`: approximately 160 MB;
- converted `Bravearm64Universal.apk`: approximately 207 MB.

Our r3 artifact is the direct Mono APK. AAB-to-universal conversion can reduce
the distributed file substantially without changing native compilation, but
that is primarily a transfer/installation packaging change.

Chromium deliberately stores modern Android native libraries uncompressed and
loads them directly from the APK. Compressing them can:

- reduce the APK download;
- require extraction during installation;
- increase total installed storage;
- slow install or startup;
- leave runtime PSS effectively unchanged.

For bike performance, optimize the native binary first. Use AAB conversion
later if distribution size remains important.

References:

- [Brave v1.95.104 release assets](https://github.com/brave/brave-browser/releases/tag/v1.95.104)
- [Chromium Android native-library packaging](https://chromium.googlesource.com/chromium/src/+/153.0.8010.53/docs/android_native_libraries.md)

## Why Static is not equivalent to official Brave

Brave `Static` sets `is_debug=false`, but it is not an official-optimized
build. Chromium documents that ordinary non-official builds retain DCHECKs and
that this can substantially reduce performance and increase memory use.

Brave also forces `chrome_pgo_phase=0` unless `is_brave_release_build` is
enabled. Chromium official ARM64 Android builds normally enable:

- DCHECK removal;
- ThinLTO;
- PGO phase 2 when the matching Android ARM64 profile is available.

The current Static build already has:

- R8/ProGuard;
- Android resource optimization;
- native symbol stripping;
- ARM64-only output.

Enabling those again will not close the gap.

References:

- [Brave build configuration selection](https://github.com/brave/brave-core/blob/v1.95.104/build/commands/lib/config.ts)
- [Brave-generated GN arguments](https://github.com/brave/brave-core/blob/v1.95.104/build/commands/lib/buildArgs.ts)
- [Chromium compiler configuration](https://chromium.googlesource.com/chromium/src/+/153.0.8010.53/build/config/compiler/compiler.gni)
- [Chromium PGO configuration](https://chromium.googlesource.com/chromium/src/+/153.0.8010.53/build/config/compiler/pgo/pgo.gni)
- [Chromium Android configuration](https://chromium.googlesource.com/chromium/src/+/153.0.8010.53/build/config/android/config.gni)

## Runtime interpretation

During accepted Prime playback, r3 used approximately:

```text
Browser + renderer + privileged process: roughly 786 MB total PSS
RobPelo companion with HUD:             roughly 102 MB PSS
```

Official Brave was not materially better in the same Prime test and had
similarly laggy Play/Pause controls. This means APK size and playback PSS are
only loosely coupled.

Expected effects:

| Change | APK/install size | Playback PSS | Latency/startup |
|---|---:|---:|---:|
| AAB/universal packaging | High | None | Neutral or small tradeoff |
| Locale pruning | Medium | Negligible | Negligible |
| Native-library compression | High APK reduction | None | Possible regression |
| Feature GN flags | Medium | Small/modest | Small/modest |
| XR removal | ~20 MB direct | Negligible in playback | Negligible |
| DCHECK removal | Small/medium | Possible improvement | Likely improvement |
| ThinLTO | Potentially large | Possible improvement | Likely improvement |
| PGO | Variable | Possible improvement | Best potential improvement |

## Supported Brave feature flags

These arguments exist in Brave v1.95.104:

```text
enable_brave_wallet=false
enable_brave_rewards=false
enable_brave_ads=false
enable_brave_vpn_v1=false
enable_brave_vpn_v2=false
enable_ai_chat=false
enable_brave_news=false
enable_brave_talk=false
enable_web_discovery_native=false
enable_web_discovery=false
enable_brave_stats_updater=false
enable_request_otr=false
```

Relevant sources:

- [Wallet](https://github.com/brave/brave-core/blob/v1.95.104/components/brave_wallet/common/buildflags/buildflags.gni)
- [Rewards](https://github.com/brave/brave-core/blob/v1.95.104/components/brave_rewards/core/buildflags/buildflags.gni)
- [Ads](https://github.com/brave/brave-core/blob/v1.95.104/components/brave_ads/buildflags/buildflags.gni)
- [VPN](https://github.com/brave/brave-core/blob/v1.95.104/components/brave_vpn/common/buildflags/buildflags.gni)
- [AI](https://github.com/brave/brave-core/blob/v1.95.104/components/ai_chat/core/common/buildflags/buildflags.gni)
- [News](https://github.com/brave/brave-core/blob/v1.95.104/components/brave_news/common/buildflags/buildflags.gni)

Disabling VPN alone does not remove `libwg-go.so`; Brave includes the Android
WireGuard dependency and loadable module unconditionally. Complete removal
requires a small downstream source patch and should be a later isolated change.

## Supported Chromium XR flags

WebXR is not needed for the five media destinations:

```text
enable_cardboard=false
enable_arcore=false
enable_openxr=false
enable_vr=false
```

Source:

- [Chromium VR build flags](https://raw.githubusercontent.com/chromium/chromium/153.0.8010.53/device/vr/buildflags/buildflags.gni)

## Required media invariants

Every variant must explicitly preserve:

```text
enable_widevine=true
proprietary_codecs=true
ffmpeg_branding="Chrome"
enable_platform_hevc=true
enable_hevc_parser_and_hw_decoder=true
```

Also preserve:

- cookies and profile storage;
- normal website authentication;
- password manager and WebAuthn/passkeys;
- downloads used by login/account flows;
- Custom Tabs and the RobPelo media activities;
- fullscreen, GPU, compositor, MediaCodec, and Android MediaDrm;
- the Prime external-intent suppression and allowlisted HTTPS unwrap.

## Features not suitable for first-stage removal

### Playlist

Brave explicitly asserts that `enable_playlist=false` is unsupported on
Android. Removal requires source-level dependency surgery.

### Sync

There is no supported Android `enable_brave_sync=false` argument. Sync JNI is
included in Android integration. Compile removal could damage profile and
preference behavior.

### WebAuthn and Bluetooth

Removal can break passkeys, security keys, or authentication. No clean
top-level Android removal flag was verified.

### IPFS

`deprecate_ipfs=true` is not a removal switch. Full removal requires
dependency-graph changes.

## Recommended staged experiments

### Stage A: r4 performance build

Keep all Brave product features unchanged. Add only:

```text
dcheck_always_on=false
enable_java_asserts=false
use_thin_lto=true
thin_lto_enable_optimizations=true
symbol_level=0
chrome_pgo_phase=0
debuggable_apks=false
android_static_analysis="off"
enable_cardboard=false
enable_arcore=false
enable_openxr=false
enable_vr=false
```

Purpose:

- isolate compiler/runtime improvements;
- avoid private Brave release keys;
- avoid PGO profile dependency;
- remove approximately 20 MB of irrelevant XR libraries.

Expected cost:

- full native recompile/relink;
- high RAM and multi-hour Dev Box build.

Acceptance comparison:

1. APK and `libchrome.so` size;
2. cold launch time;
3. HOME-to-Prime time;
4. Prime browser/renderer/utility PSS;
5. same-title Play/Pause latency;
6. protected playback, fullscreen, HUD, Back/Home;
7. process-restart and reboot persistence.

### Stage B: r5 feature-slim build

Only after Stage A passes, add feature groups one at a time:

1. Wallet + Rewards + Ads.
2. VPN + News + AI.
3. Web Discovery + stats + Talk + Request-OTR.

This isolates compile failures and behavioral regressions. Feature flags are
expected to improve binary size and background/startup overhead more than
active video PSS.

### Stage C: packaging

Once the runtime build is accepted, generate an AAB and universal APK:

```text
--target_android_output_format=aab
--android_aab_to_apk
```

Compare it with the direct APK. Do not infer runtime improvement from the
smaller universal APK alone.

### Stage D: PGO

Attempt only after the matching Android ARM64 PGO profile is synchronized.
Brave's release workflow uses `is_brave_release_build=1` to request profiles.
Do not force `chrome_pgo_phase=2` without a verified profile.

PGO offers the best remaining startup/latency opportunity but has the highest
build complexity and rebuild cost.

## Recommendation

Proceed with Stage A before removing large Brave subsystems. It gives a clean
answer to the most important question: whether official-style compiler
optimization improves the 2 GB bike.

If Stage A does not materially improve latency or PSS, broad feature removal is
unlikely to fix Prime controls, because official Brave already exhibits the
same latency. At that point, prioritize APK/storage/security slimming rather
than promising a playback-performance improvement.
