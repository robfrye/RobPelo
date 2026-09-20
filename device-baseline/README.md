# Peloton Device Baseline

Captured through authorized ADB on 2026-09-20. All device-side discovery commands
were read-only. The unique device serial is redacted from repository artifacts.
Raw command captures are intentionally excluded from Git because future captures
may contain device identifiers. This sanitized summary is the shareable record.

## Development environment

- Host: macOS on arm64
- ADB: Android Debug Bridge 1.0.41, platform-tools 37.0.1
- ADB path: `/opt/homebrew/bin/adb`
- Connection: USB, authorized, device state `device`

## Device and software

| Property | Observed value |
|---|---|
| Manufacturer | Peloton Interactive LLC |
| Model | PLTN-RB1VQ |
| Product/device | RB1VQ |
| Android | 11 |
| API level | 30 |
| Peloton/build ID | RQ.260424.A |
| Build fingerprint | `Peloton/RB1VQ/RB1VQ:11/RQ.260424.A/76:user/release-keys` |
| Build date | 2026-04-29 |
| Security patch | 2022-10-05 |
| Hardware platform | MediaTek MT8173 (`mt8173`) |
| CPU ABI | `arm64-v8a` only |

## Display

- Physical and logical resolution: 1920 x 1080
- Application area observed by DisplayManager: 1920 x 1008
- Density: 240 dpi (`1.5` scale)
- Refresh rate: 60 Hz
- Internal touch display
- Secure/protected buffers are supported
- No HDR modes reported

## Packages and enabled state

- 93 packages are installed.
- All 93 appear in `pm list packages -e`.
- No packages appear in `pm list packages -d`.
- 25 package names contain `peloton` (including two distinct factory packages
  whose names differ only by namespace).

## HOME and current activity

The resolved default HOME activity is:

```text
com.peloton.launcher/.LauncherActivity
```

The only other HOME handler is Android's low-priority fallback:

```text
com.android.settings/.FallbackHome
```

At capture time, the resumed foreground activity was:

```text
com.peloton.activity/com.peloton.activation.ActivationActivity
```

The launcher package reports version `6.1.37` for the installed data package.
Its package dump also includes the older hidden system package version `6.1.16`.

## Netflix

- Package: `com.netflix.mediaclient`
- Version: `9.40.0 build 7 63705`
- Version code: `63705`
- Minimum SDK: 28
- Target SDK: 36
- Installed and enabled for user 0
- Resolved launcher activity:
  `com.netflix.mediaclient/.ui.launch.UIWebViewActivity`

## Ride and telemetry surfaces

### Low-level sensor service

The device has a dedicated package:

```text
com.peloton.service.SensorData
```

Observed version: `2.8.3211` (`versionCode=2083211`).

It exposes this intent-resolved service:

```text
Action: android.intent.action.peloton.SensorData
Component: com.peloton.service.SensorData/com.peloton.sensor.SensorService
Categories:
  com.peloton.sensor.category.FAKE_DATA
  com.peloton.sensor.category.BIKE
  com.peloton.sensor.category.AURORA
```

The package declares `onepeloton.permission.ACCESS_SENSOR_SERVICE` with
`signature` protection. The Peloton activity package requests and has this
permission. A normal independently signed APK cannot be granted a signature
permission through the standard runtime permission flow.

The SensorData process was running when captured. The service itself was not
shown among active ActivityManager service records at that instant, which may
mean it had not been bound or started yet.

### Workout metrics service

The device also has:

```text
com.onepeloton.workoutservices.app
```

Observed version: `1.4.1104` (`versionCode=1041104`).

It exposes:

```text
Action: com.onepeloton.workoutservices.metrics.IMetricsServiceInterface
Component: com.onepeloton.workoutservices.app/com.onepeloton.workoutservices.metrics.MetricsService
```

It declares `com.onepeloton.permission.METRICS_SERVICE` with `normal`
protection. A separate, misspelled permission,
`com.oneploton.permission.ACCESS_METRICS`, is owned by
`com.onepeloton.affernetservice` and has `signature` protection. Package-manager
output alone does not establish which permission guards each bind path, so
client accessibility remains an explicit Phase 3 validation item.

The same package exposes heart-rate and heart-rate-connection binder services.
Both were active when captured; MetricsService was not active at that instant.

### Affernet bike service

Phase 3 research identified another telemetry surface used by OpenRide and
Switchback:

```text
com.onepeloton.affernetservice/.AffernetService
```

The installed data package is version `2.4.500` (`versionCode=2040500`); its
package dump also includes hidden system version `2.4.480`.

On this exact firmware, all three actions resolve to the same service:

```text
com.onepeloton.affernetservice.IV1Interface
com.onepeloton.affernetservice.IBikeInterface
com.onepeloton.affernetservice.IAffernetService
```

The service was active, and ActivityManager showed existing `IV1Interface` and
`IAffernetService` connections. Package-manager resolution does not by itself
prove that an ordinary sideloaded APK may bind successfully, so that remains a
required on-device validation.

### Existing Peloton Just Ride entry point

The Peloton activity package contains a `peloton://activation/justride` deep
link routed to:

```text
com.peloton.activity/com.peloton.activation.ActivationActivity
```

This is evidence of a route, not evidence that it is usable without activation
or subscription. No activity was launched during inspection.

## Overlay capability

- Android API 30 supports `TYPE_APPLICATION_OVERLAY`.
- The system resolves `android.settings.action.MANAGE_OVERLAY_PERMISSION` to
  `com.android.settings/.Settings$OverlaySettingsActivity`.
- No package was returned as explicitly allowed by
  `cmd appops query-op SYSTEM_ALERT_WINDOW allow`.
- Netflix has no recorded `SYSTEM_ALERT_WINDOW` operation.
- The Peloton activity package declares `SYSTEM_ALERT_WINDOW`, but its current
  app-op mode was reported as `default`.

This indicates that the standard Android overlay settings surface exists.
Whether a newly installed third-party APK can obtain and retain overlay access
must be tested later through the normal user-approved settings flow. No overlay
permission was requested or changed during this phase.

## Validation outcome

Subsequent implementation testing established that:

- an independently signed APK can bind to Affernet `IV1Interface`;
- the OpenRide callback and `BikeData` layout work on `RQ.260424.A`;
- live cadence, resistance, and output are available without a stock ride;
- Android's standard overlay grant works and permits a HUD over Netflix.

WorkoutServices MetricsService and the stock Just Ride deep link remain
uninvestigated because the Affernet path satisfies the current requirements
without privileged access.

No package was installed on the bike, no package or component was enabled or
disabled, no application data was changed, and no device setting was modified
apart from the USB-debugging trust authorization explicitly approved by the
user.
