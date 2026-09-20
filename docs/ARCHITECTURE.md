# Peloton Companion Architecture Proposal

Status: implemented through Stage 4, including Firefox-based YouTube + HUD, and
validated on the target device.

This design is based on the connected `PLTN-RB1VQ` running Android 11/API 30
and Peloton build `RQ.260424.A`. It deliberately defers launcher replacement
until telemetry, Just Ride, and overlay behavior have been validated
independently.

Related evidence:

- [Device baseline](../device-baseline/README.md)
- [Existing-project evaluation](./EXISTING_PROJECTS.md)
- [Rollback procedure](./ROLLBACK.md)

## Decision summary

Build one native Android application with:

- Kotlin
- Gradle Wrapper
- standard Android services, activities, views, and `WindowManager`
- a small telemetry abstraction
- no cloud dependency
- no root or privileged installation
- no modification to Peloton packages

Use OpenRide's Apache-2.0 Affernet telemetry implementation as the reference
and permitted source for the telemetry boundary. Reimplement the system HUD
using standard Android APIs; do not copy Grupetto/Peloton Overlay because no
license grant was found.

The implementation sequence is:

1. Read-only telemetry probe
2. Standalone Just Ride
3. System HUD and Netflix launch
4. Optional HOME/launcher role

Each stage must pass on the bike before starting the next.

## Proposed application shape

```text
Peloton Companion APK
|
+-- DiagnosticActivity              Stage 1
|     Shows connection state and raw metrics
|
+-- JustRideActivity                Stage 2
|     Full-screen bike-oriented ride UI
|
+-- HomeActivity                    Stage 4
|     Optional HOME/launcher entry point
|
+-- RideTelemetryService            Stages 2-3
|     Foreground service while a ride/HUD is active
|     |
|     +-- BikeTelemetrySource
|     |     |
|     |     +-- AffernetTelemetrySource
|     |
|     +-- RideSession
|     |     Calculates elapsed time, speed, distance,
|     |     total output, and connection health
|     |
|     +-- HudController
|           Adds/removes the Android overlay window
|
+-- ExternalAppLauncher
      Launches Netflix and future allowlisted applications
|
+-- FirefoxUpdater
      Checks Mozilla daily while HOME is active
      Downloads only after user action
      Verifies package, SDK, version, and signing certificate
      Hands the APK to Android's confirmation UI
```

This remains one application and one process. The internal boundaries make
telemetry reusable by Just Ride and the HUD without introducing multiple
modules, dependency injection frameworks, or inter-process IPC of our own.

## Package identity

The permanent application ID selected for Stage 1 and later builds is:

```text
com.robpelo.companion
```

The final ID must be written into
[ROLLBACK.md](./ROLLBACK.md) before the first installation. Changing it later
would make Android treat the app as a separate installation and complicate
rollback.

## Build configuration

Recommended baseline:

| Setting | Proposal |
|---|---|
| Language | Kotlin |
| Build system | Gradle with checked-in Gradle Wrapper |
| Minimum SDK | 30 |
| Compile SDK | Current stable SDK available during implementation |
| Target SDK | 34 |
| Java/Kotlin bytecode | JVM 17 |
| UI | Standard Android Views and custom views |
| Async/state | In-process listeners and main-thread `Handler` |
| Persistence | None initially |

`minSdk 30` intentionally targets this Android 11 device rather than carrying
compatibility branches for older Android releases.

Standard Views are preferred over Compose for the initial version because the
HUD needs only a small, fixed metrics layout. This avoids Compose-in-Service
lifecycle scaffolding and keeps dependency count and APK complexity lower.
Compose can be reconsidered later if the launcher or ride-history UI grows.

The project must build without Android Studio:

```bash
./gradlew assembleDebug
```

## Telemetry layer

### Public application interface

UI code depends only on a small callback/state contract:

```kotlin
interface BikeTelemetryClient {
    fun start()
    fun stop()
}

sealed interface TelemetryState {
    data object Disconnected : TelemetryState
    data object Connecting : TelemetryState
    data class Connected(val sample: RawBikeSample) : TelemetryState
    data class Failed(val reason: TelemetryFailure) : TelemetryState
}

data class RawBikeSample(
    val cadenceRpm: Int,
    val resistancePercent: Int,
    val outputWatts: Int,
    val receivedAtElapsedRealtimeMs: Long,
)
```

The exact names may change during implementation, but these behaviors should
not:

- connection state is explicit;
- failures are surfaced rather than replaced with zeros;
- samples include a monotonic receipt timestamp;
- service-specific Binder types do not leak into UI code;
- connecting and disconnecting are idempotent;
- Binder death and service disconnection produce an observable failure.

### Affernet implementation

`AffernetTelemetrySource` should adapt the Apache-2.0 OpenRide implementation:

```text
Package: com.onepeloton.affernetservice
Service: com.onepeloton.affernetservice/.AffernetService
Primary action: com.onepeloton.affernetservice.IV1Interface
Primary callback: com.onepeloton.affernetservice.IV1Callback
```

Required interoperability source files include reconstructed AIDL and
Parcelable definitions. `BikeData` must remain in package
`com.onepeloton.affernetservice` so Android can unmarshal the vendor Parcel.

For the RB1VQ, use `IV1Interface` first. Do not initially race both Affernet
interfaces; selecting the known model-specific path is simpler and avoids
unnecessary Binder interactions. An `IBikeInterface` read-only fallback may be
added only if Stage 1 evidence shows that IV1 is unavailable or incompatible.

Allowed Binder operations:

- bind and unbind;
- register and unregister the callback;
- request a conservative report interval;
- receive and decode `BikeData`;
- link and unlink a Binder death recipient if needed.

Prohibited operations:

- fake-data mode;
- resistance writes;
- calibration, motor-control, or firmware operations;
- undocumented transactions not required for read-only metrics;
- interaction with the signature-protected SensorData service.

### Raw metric validation

Do not assume scaling merely because OpenRide works on older firmware.

Stage 1 must record raw fields and mapped values while the user:

1. leaves the pedals stationary;
2. pedals slowly;
3. pedals faster;
4. changes resistance through several known positions.

Acceptance criteria:

- cadence changes promptly and plausibly with pedal speed;
- resistance tracks knob changes and remains within 0-100;
- output returns to or near zero when stationary;
- output increases plausibly with cadence/resistance;
- no integer overflow, negative values, or parcel exceptions occur;
- the connection survives at least ten minutes and reconnects after the
  diagnostic activity is closed and reopened.

The `/100` power mapping must be retained only if observed raw data confirms it
for AffernetService `2.4.500`.

## Derived ride metrics

`RideSession` consumes raw samples and owns values not directly supplied by the
bike:

- elapsed ride time from `SystemClock.elapsedRealtime()`;
- speed estimated from output using a documented, testable function;
- distance integrated from estimated speed and monotonic sample intervals;
- total output integrated from power over time;
- moving/stopped state;
- last-sample age and connection health.

Elapsed time must never use wall-clock time, because wall-clock changes can
make rides jump backward or forward.

Distance and total output accumulation must:

- ignore negative or implausibly large time deltas;
- pause accumulation while telemetry is stale;
- retain the last known display value while marking live readings unavailable;
- avoid counting time before the first valid sample.

Local ride history is outside the first version. Keep `RideSession` independent
from storage so persistence can be added later without changing Binder code.

## RideTelemetryService

Stages 2 and 3 introduce one foreground service:

```text
RideTelemetryService
```

Responsibilities:

- own the single Affernet connection;
- own one active `RideSession`;
- expose immutable state to in-process activities;
- create the required foreground notification;
- show or remove the HUD through `HudController`;
- stop cleanly and unbind when the ride/HUD ends.

The service should not start at boot and should not be sticky initially. A user
action from Just Ride or the Netflix tile starts it. If Android kills it, the
HUD should disappear and the session should end rather than silently restarting
with incorrect elapsed time.

Notification actions should include:

- Stop HUD / End ride
- Return to Companion

No wake lock should be added unless testing proves the display or CPU sleeps
during an active foreground session.

## Just Ride

`JustRideActivity` binds to `RideTelemetryService` and renders:

- cadence
- resistance
- output
- estimated speed
- elapsed time
- estimated distance
- telemetry connection state

Initial controls:

- Start
- Pause/resume local accumulation
- End ride

The activity should be landscape-only and use an immersive full-screen layout
appropriate for 1920 x 1080 at 240 dpi. Metrics should remain readable at
normal riding distance and show `--` when unavailable, not fabricated zeros.

The first version stores no ride history.

## Netflix and HUD flow

The Netflix tile uses this sequence:

```text
User taps Netflix
    |
    +-- Is overlay access granted?
    |      |
    |      +-- No: open standard Android overlay settings
    |      |       and wait for the user to return
    |      |
    |      +-- Yes
    |
    +-- Start RideTelemetryService from the foreground
    |
    +-- Request HUD display
    |
    +-- Launch com.netflix.mediaclient
```

Netflix must be launched through `PackageManager.getLaunchIntentForPackage()`
instead of hard-coding its current activity. The current resolved
`UIWebViewActivity` is an implementation detail that may change during an app
update.

If Netflix is missing, disabled, or cannot be launched, show an explicit error,
remove the HUD, and stop the session unless the user intentionally entered Just
Ride.

## HUD implementation

`HudController` uses:

- `Settings.canDrawOverlays()`;
- `WindowManager`;
- `TYPE_APPLICATION_OVERLAY`;
- `FLAG_NOT_FOCUSABLE`;
- `FLAG_NOT_TOUCH_MODAL`;
- a small standard Android `View`.

Initial layout:

```text
CADENCE  87    RESISTANCE  42    OUTPUT  186 W
SPEED  16.8 mph             DISTANCE  4.2 mi
```

Design constraints:

- fixed to a safe screen edge;
- high-contrast text on a translucent background;
- no full-screen transparent touch surface;
- no interception of Netflix controls outside the HUD;
- one small touch target for minimize/restore or close;
- `WindowManager.removeView()` always called during stop/destruction;
- overlay hidden immediately when permission is revoked;
- no accessibility service.

The service must catch and surface specific window-add/remove failures. It must
not broadly suppress overlay errors.

## HOME / launcher

`HomeActivity` is the final stage, not an initial requirement.

It will declare:

```text
android.intent.action.MAIN
android.intent.category.HOME
android.intent.category.DEFAULT
```

Initial tiles:

- Just Ride
- Netflix
- YouTube in Firefox

Utility actions:

- Diagnostics
- Peloton Home, which temporarily opens the stock
  `com.peloton.activation.ActivationActivity` without changing the default HOME
- Settings, which opens Android's standard settings homepage without changing
  the default HOME

Future apps can be added through a small compile-time allowlist. Avoid a
general-purpose app drawer until there is a concrete need.

Installation must not automatically change HOME. After validation, selecting
the companion as HOME is a separate, explicit, reversible step. The stock
launcher must remain installed and enabled.

Rollback is:

```bash
adb shell cmd package set-home-activity --user 0 \
  com.peloton.launcher/.LauncherActivity
```

See [ROLLBACK.md](./ROLLBACK.md) for the complete sequence.

## Manifest surface

Add permissions only when the corresponding stage needs them.

### Stage 1

- No dangerous runtime permissions
- Package visibility for `com.onepeloton.affernetservice`

Do not request `onepeloton.permission.ACCESS_SENSOR_SERVICE`.

### Stage 2

- `android.permission.FOREGROUND_SERVICE`

### Stage 3

- `android.permission.SYSTEM_ALERT_WINDOW`
- Package visibility for `com.netflix.mediaclient`

If a modern target SDK requires notification permission on newer Android
versions, declare it appropriately, but do not request irrelevant permissions
on this API 30 device.

Do not request:

- root or shell privileges;
- accessibility-service access;
- device-admin access;
- package uninstall permission;
- write-settings or secure-settings permission;
- storage permission;
- Bluetooth/location permission until heart-rate support is actually added;
- Peloton subscription or signature permissions.

Firefox updating requires `INTERNET` and `REQUEST_INSTALL_PACKAGES`. The latter
is used only to hand a verified Firefox APK to Android's user-confirmed package
installer; RobPelo cannot install silently.

## Firefox update checks

`FirefoxUpdater` checks Mozilla's official stable-version JSON feed when HOME
is visible and the previous successful check is at least 24 hours old. It does
not register a boot receiver, schedule background work, wake the device, or
download an APK during a periodic check.

If a newer version is available, HOME displays a notice and changes the update
button label. Download begins only after a user tap.

Before opening Android's installer, the updater verifies:

- package name is exactly `org.mozilla.firefox`;
- candidate version code is greater than the installed version;
- candidate minimum SDK is supported;
- candidate signer SHA-256 set exactly matches installed Firefox;
- download size does not exceed 250 MB.

The APK is fetched only over HTTPS from Mozilla's official archive and stored
under RobPelo's private cache. A narrow `FileProvider` grants the Android
package installer temporary read access. Temporary files are deleted after
failed verification and can be cleared with RobPelo's normal app data.

## Dependency policy

Initial dependencies should be limited to:

- Kotlin standard library
- the Android Gradle plugin and Kotlin Gradle plugin

Do not include networking, analytics, crash reporting, database, dependency
injection, navigation, or image-loading libraries in the first version.

When OpenRide interoperability files are adapted:

- preserve applicable copyright notices;
- include Apache License 2.0;
- include and review OpenRide's `NOTICE`;
- mark modified files;
- document the exact upstream commit used;
- do not import optional updater, launcher-management, or OTA-blocking code.

## Failure behavior

Failures must be visible and actionable:

| Failure | Required behavior |
|---|---|
| Affernet service not found | Show unsupported-firmware error; do not try privileged services automatically |
| Bind rejected | Show permission/bind failure and stop |
| Binder dies | Mark telemetry disconnected, stop accumulation, attempt one bounded reconnect |
| Parcel decode fails | Stop the telemetry source and report protocol incompatibility |
| Samples become stale | Show `--`, stop distance/output accumulation |
| Overlay permission absent | Open the standard settings screen only after user action |
| Overlay window add fails | Show an error in the companion and stop HUD mode |
| Netflix launch fails | Remove HUD and report the failure |

No synthetic telemetry or success-shaped fallback should be used on the real
device.

## Staged implementation and approval gates

### Stage 1: telemetry probe

Deliver:

- command-line-buildable APK;
- diagnostic activity;
- Affernet IV1 binding;
- raw and mapped values on-screen and in filtered logs;
- connection and failure states;
- unit tests for parcel mapping and metric scaling where possible.

Excluded:

- HOME intent filter;
- overlay permission;
- foreground ride service;
- Netflix launch;
- local history.

Gate: proceed only after live metrics are validated on `RQ.260424.A`.

### Stage 2: Just Ride

Deliver:

- foreground telemetry service;
- session calculations;
- full-screen Just Ride activity;
- pause/end behavior;
- lifecycle and reconnect tests.

Gate: proceed only after a sustained ride test and clean service shutdown.

### Stage 3: Netflix HUD

Deliver:

- user-approved overlay flow;
- compact HUD;
- Netflix launch;
- stop/minimize controls;
- permission-revocation and process-death handling.

Gate: proceed only after Netflix playback, touch behavior, and rollback are
verified.

### Stage 4: HOME

Deliver:

- two-tile launcher;
- explicit opt-in HOME selection;
- stock-launcher restoration verification.

Gate: run the documented rollback immediately after the first HOME test, then
repeat installation and restoration to prove reversibility.

## Validation policy

Every device-changing test must:

1. confirm `adb devices -l` reports exactly the intended device;
2. record the current HOME before the operation;
3. affect only the companion package or its granted permissions;
4. avoid commands targeting Peloton package state or data;
5. verify the expected result;
6. retain a tested rollback command.

The first APK installation completed after its source, manifest, build output,
and exact `adb install` command were reviewed. Results are recorded in
[device-tests/README.md](../device-tests/README.md).

## Explicit non-goals for the first version

- ride history or database
- accounts or cloud synchronization
- automatic resistance
- Peloton service modification
- heart-rate monitor support
- OTA blocking
- package manager or app-store features
- firmware reset
- root, bootloader unlock, or system partition changes
