# Existing Peloton Project Evaluation

Evaluation date: 2026-09-20.

This review is source-only. None of the projects or their APKs was installed,
built, or executed. Device comparisons use the read-only baseline captured
from the connected Peloton:

- Model `PLTN-RB1VQ`, device `RB1VQ`
- Android 11, API 30, `arm64-v8a`
- Peloton build `RQ.260424.A`
- SensorData `2.8.3211`
- WorkoutServices `1.4.1104`
- AffernetService `2.4.500`

See [device-baseline/README.md](../device-baseline/README.md) for the full
device baseline.

## Executive conclusion

**OpenRide is the best telemetry implementation to adapt.** It is Apache-2.0
licensed, written in Kotlin/AIDL, explicitly documents and tests the exact
`PLTN-RB1VQ` model on Android 11, and isolates telemetry behind a data-source
interface. It avoids the signature-protected SensorData permission by binding
to Peloton's Affernet `IV1Interface`.

The current bike strongly corroborates that design:

- `com.onepeloton.affernetservice/.AffernetService` is installed, enabled, and
  active.
- `com.onepeloton.affernetservice.IV1Interface` resolves to that service.
- ActivityManager reports an active `IV1Interface` connection.

The remaining critical uncertainty is whether an independently signed,
sideloaded APK can bind on build `RQ.260424.A` and whether its Binder
transactions and `BikeData` parcel layout still match OpenRide's tested older
firmware. Resolution and an existing Peloton client connection do not prove
third-party access.

Grupetto/Peloton Overlay closely matches the installed SensorData service and
provides a useful system-overlay design reference, but its required SensorData
permission is signature-protected on this bike and its repository has no
license. Its code should not be copied.

Switchback contains a newer Affernet `IV1Interface` path and a system overlay,
but explicitly targets Bike+, does not verify `PLTN-RB1VQ`, and uses the
source-restrictive Business Source License 1.1 until 2030. It is a secondary
reference, not the preferred code source.

OpenPelo is useful for ADB discovery and sideloading workflows but contains no
bike telemetry implementation.

## Comparison matrix

| Project | Exact RB1VQ support | Telemetry source | Android 11 | System overlay | Root required | License | Reuse assessment |
|---|---|---|---|---|---|---|---|
| OpenRide | Yes; tested on older RB1VQ firmware | Affernet `IV1Interface` callback, with `IBikeInterface` fallback | Yes | No; overlay is in-app over its own video | No | Apache-2.0 | Best telemetry candidate |
| Grupetto / Peloton Overlay | Described as Gen 2; exact model not named | SensorData Messenger protocol | Yes by SDK/API design | Yes | No runtime root, but signature permission may block normal sideload | No license found | Concepts only; do not copy code |
| Switchback | No; generic non-Bike+ path may apply | Affernet `IV1Interface`; Bike+ uses `IBikeInterface` | Yes by SDK/API design | Yes | No | BSL 1.1 until 2030, then MIT | Secondary reference with restrictions |
| OpenPelo | Generic ADB device handling only | None | Yes | No | No | MIT | Useful tooling reference, not telemetry |

## Current-device interface comparison

### SensorData

The current device exposes:

```text
Action: android.intent.action.peloton.SensorData
Component: com.peloton.service.SensorData/com.peloton.sensor.SensorService
Permission declared by package:
  onepeloton.permission.ACCESS_SENSOR_SERVICE (signature)
```

This exactly matches Grupetto's original-bike service action and package.
However, an independently signed APK cannot normally receive a
signature-protected permission. Declaring it in a manifest is insufficient.
The package-manager capture does not conclusively show whether the service
enforces that permission at bind time, but it is a substantial blocker.

Device evidence is summarized in
[device-baseline/README.md](../device-baseline/README.md).

### WorkoutServices MetricsService

The current device exposes:

```text
Action: com.onepeloton.workoutservices.metrics.IMetricsServiceInterface
Component:
  com.onepeloton.workoutservices.app/
  com.onepeloton.workoutservices.metrics.MetricsService
```

The service package declares a normal
`com.onepeloton.permission.METRICS_SERVICE` permission. A separate misspelled
`com.oneploton.permission.ACCESS_METRICS` permission is signature-protected.
The package-manager output does not establish which permission guards binding.

None of the four reviewed projects implements this metrics interface. Using it
would therefore require new protocol discovery rather than reuse and is not
the preferred first path.

Device evidence is summarized in
[device-baseline/README.md](../device-baseline/README.md).

### AffernetService

The current device exposes all of the following through
`com.onepeloton.affernetservice/.AffernetService`:

```text
com.onepeloton.affernetservice.IV1Interface
com.onepeloton.affernetservice.IBikeInterface
com.onepeloton.affernetservice.IAffernetService
```

The installed version is `2.4.500`. The package is enabled and the service was
active during capture. This is the same component and `IV1Interface` action
used by OpenRide for the RB1VQ.

The Affernet package defines signature permissions for debug, packet logging,
and metrics access, but the resolver output does not identify a permission on
the bike-interface bind action. OpenRide documents the service as exported and
unguarded on its tested older build. Access from a normal app on the current
build remains unverified until a minimal bind test is performed.

Device evidence is summarized in
[device-baseline/README.md](../device-baseline/README.md).

## OpenRide

Repository: [digitalducktape/OpenRide](https://github.com/digitalducktape/OpenRide)

Examined commit:
[`a8b2aceae722c3fb2d5159f0a6389ba3cadd4725`](https://github.com/digitalducktape/OpenRide/commit/a8b2aceae722c3fb2d5159f0a6389ba3cadd4725),
dated 2026-08-21. The release at that commit is `v0.4.0`.

### Hardware and Android support

OpenRide explicitly documents:

- Bike Gen 2: model `PLTN-RB1VQ`, device `RB1VQ`, Android 11/API 30,
  tested on build `RQ.250113.A`
- Bike+: model `PLTN-TTR01`, Android 10/API 29

Source:
[DEVICE.md](https://github.com/digitalducktape/OpenRide/blob/a8b2aceae722c3fb2d5159f0a6389ba3cadd4725/docs/DEVICE.md#L1-L31).

Its current build uses `minSdk 29`, `targetSdk 34`, `compileSdk 34`, and
Kotlin/JVM 17:
[app/build.gradle.kts](https://github.com/digitalducktape/OpenRide/blob/a8b2aceae722c3fb2d5159f0a6389ba3cadd4725/app/build.gradle.kts#L9-L21).
There is no native telemetry code, so the target's `arm64-v8a` ABI is not an
obvious blocker.

The exact current Peloton build `RQ.260424.A` is newer than the documented
tested build. OpenRide warns that private Binder compatibility must be
rechecked after firmware changes.

### Telemetry implementation

OpenRide does not use SensorData or WorkoutServices. It binds:

```text
Package: com.onepeloton.affernetservice
Service: AffernetService
Gen 2 action/interface: com.onepeloton.affernetservice.IV1Interface
Bike+ action/interface: com.onepeloton.affernetservice.IBikeInterface
```

For Gen 2, `PelotonBikeDataSource`:

1. Binds `IV1Interface`.
2. Registers an `IV1Callback`.
3. Requests approximately one report per second.
4. Marks the connection live only after receiving a real frame.

Source:
[PelotonBikeDataSource.kt](https://github.com/digitalducktape/OpenRide/blob/a8b2aceae722c3fb2d5159f0a6389ba3cadd4725/app/src/main/java/dev/digitalducktape/openride/core/sensor/PelotonBikeDataSource.kt#L17-L56).

The reconstructed AIDL defines register, unregister, fake-data, and reporting
rate transactions:

- [IV1Interface.aidl](https://github.com/digitalducktape/OpenRide/blob/a8b2aceae722c3fb2d5159f0a6389ba3cadd4725/app/src/main/aidl/com/onepeloton/affernetservice/IV1Interface.aidl#L1-L38)
- [IV1Callback.aidl](https://github.com/digitalducktape/OpenRide/blob/a8b2aceae722c3fb2d5159f0a6389ba3cadd4725/app/src/main/aidl/com/onepeloton/affernetservice/IV1Callback.aidl#L1-L27)

`BikeData` must retain the exact package
`com.onepeloton.affernetservice` for Parcelable unmarshalling. The consumed
fields include raw RPM, power, current resistance, and target resistance:
[BikeData.kt](https://github.com/digitalducktape/OpenRide/blob/a8b2aceae722c3fb2d5159f0a6389ba3cadd4725/app/src/main/java/com/onepeloton/affernetservice/BikeData.kt#L6-L54).

Mapping behavior:

- cadence = RPM
- output = raw power divided by 100
- resistance = current resistance clamped to 0-100
- speed = locally estimated from output
- elapsed time and distance = locally accumulated

Source:
[BikeDataMapping.kt](https://github.com/digitalducktape/OpenRide/blob/a8b2aceae722c3fb2d5159f0a6389ba3cadd4725/app/src/main/java/dev/digitalducktape/openride/core/sensor/BikeDataMapping.kt#L5-L32).

The combined `AffernetBikeDataSource` attempts both callback-capable binders,
uses the first valid decoded frame, and adds a polling fallback after five
seconds:
[AffernetBikeDataSource.kt](https://github.com/digitalducktape/OpenRide/blob/a8b2aceae722c3fb2d5159f0a6389ba3cadd4725/app/src/main/java/dev/digitalducktape/openride/core/sensor/AffernetBikeDataSource.kt#L14-L50).

### UI and overlay behavior

OpenRide contains a polished Compose Just Ride screen with:

- elapsed time
- output and output zones
- cadence, resistance, speed, and distance
- pause/resume and auto-pause
- sensor failure handling

Source:
[InRideScreen.kt](https://github.com/digitalducktape/OpenRide/blob/a8b2aceae722c3fb2d5159f0a6389ba3cadd4725/app/src/main/java/dev/digitalducktape/openride/ui/ride/InRideScreen.kt#L40-L154).

Its video metrics display is not a system overlay. It embeds YouTube in the
same application and layers the metrics UI over its own WebView:
[VideoRideScreen.kt](https://github.com/digitalducktape/OpenRide/blob/a8b2aceae722c3fb2d5159f0a6389ba3cadd4725/app/src/main/java/dev/digitalducktape/openride/ui/ride/VideoRideScreen.kt#L48-L145).
It cannot overlay Netflix without adding a separate Android overlay service.

### Permissions and system changes

OpenRide requests network and Bluetooth/location permissions plus
`REQUEST_INSTALL_PACKAGES` for its optional updater. It does not request:

- `onepeloton.permission.ACCESS_SENSOR_SERVICE`
- a Peloton signature permission
- `SYSTEM_ALERT_WINDOW`

Source:
[AndroidManifest.xml](https://github.com/digitalducktape/OpenRide/blob/a8b2aceae722c3fb2d5159f0a6389ba3cadd4725/app/src/main/AndroidManifest.xml#L5-L42).

Root, bootloader unlocking, and firmware modification are not required for
telemetry. Its optional appliance/launcher instructions include enabling a
HOME alias and may recommend disabling or removing stock components or
blocking updates. Those optional actions conflict with this project's safety
constraints and must not be copied.

### License and reuse

OpenRide uses Apache License 2.0:
[LICENSE](https://github.com/digitalducktape/OpenRide/blob/a8b2aceae722c3fb2d5159f0a6389ba3cadd4725/LICENSE).

Reuse and modification are permitted if license, attribution, modification,
and NOTICE requirements are preserved:
[NOTICE](https://github.com/digitalducktape/OpenRide/blob/a8b2aceae722c3fb2d5159f0a6389ba3cadd4725/NOTICE).

The cleanest reusable portion is its sensor boundary and Affernet
implementation:

- AIDL/Parcelable definitions
- `BikeDataSource` abstraction and state types
- `PelotonBikeDataSource`
- `PelotonBikeInterfaceDataSource`
- `AffernetBikeDataSource`
- metric mapping and speed estimation

The UI can remain independent of this layer, matching the proposed companion
architecture.

## Grupetto / Peloton Overlay

Repository:
[pselvana/peloton-overlay](https://github.com/pselvana/peloton-overlay), a fork
of [selalipop/grupetto](https://github.com/selalipop/grupetto).

Examined commit:
[`f8d54d8f942bcf7140742efc31366f87c02e49b3`](https://github.com/pselvana/peloton-overlay/commit/f8d54d8f942bcf7140742efc31366f87c02e49b3),
dated 2026-04-05. Default branch: `develop`.

### Hardware and Android support

The README describes Gen 2 bikes and says the protocol was reverse-engineered
from a production Gen 2 bike:
[README.md](https://github.com/pselvana/peloton-overlay/blob/f8d54d8f942bcf7140742efc31366f87c02e49b3/README.md#L1-L6).

Runtime selection treats exact model `PLTN-TTR01` as Bike+ and every other
Peloton-branded device as the original-bike path:
[Peloton.kt](https://github.com/pselvana/peloton-overlay/blob/f8d54d8f942bcf7140742efc31366f87c02e49b3/app/src/main/java/com/spop/poverlay/util/Peloton.kt#L7-L16).
The exact `PLTN-RB1VQ` model is not named, although it would select the expected
original-bike path.

Build settings are `minSdk 24`, `targetSdk 33`, and `compileSdk 33`, so API 30
is within range:
[app/build.gradle](https://github.com/pselvana/peloton-overlay/blob/f8d54d8f942bcf7140742efc31366f87c02e49b3/app/build.gradle#L6-L21).

### Telemetry implementation

For a non-Bike+ Peloton, it binds:

```text
Action: android.intent.action.peloton.SensorData
Package: com.peloton.service.SensorData
```

Source:
[Binder.kt](https://github.com/pselvana/peloton-overlay/blob/f8d54d8f942bcf7140742efc31366f87c02e49b3/app/src/main/java/com/spop/poverlay/sensor/v1/Binder.kt#L13-L39).

It wraps the service binder in Android `Messenger` rather than AIDL and sends:

| `Message.what` | Metric |
|---:|---|
| 1 | Cadence |
| 2 | Power |
| 3 | Resistance |

Replies are expected to carry `data`, `time`, and `responseHexString` bundle
keys:
[Sensor.kt](https://github.com/pselvana/peloton-overlay/blob/f8d54d8f942bcf7140742efc31366f87c02e49b3/app/src/main/java/com/spop/poverlay/sensor/v1/Sensor.kt#L50-L105).

Speed is estimated locally from power. The examined fork no longer divides
SensorData power by 100, and that scaling decision is not firmware-version
gated.

The service action and package exactly match this device, but the app requests
the signature-protected `onepeloton.permission.ACCESS_SENSOR_SERVICE`.
Successful binding by an ordinary sideloaded APK is therefore doubtful and
must not be assumed from source alone.

### Overlay implementation

The project provides a real system overlay:

1. Check `Settings.canDrawOverlays()`.
2. Open `android.settings.action.MANAGE_OVERLAY_PERMISSION` for user approval.
3. Start a foreground service.
4. Add a non-focusable, non-touch-modal `ComposeView` through `WindowManager`.
5. Use `TYPE_APPLICATION_OVERLAY` on API 26 and later.

Sources:

- [ConfigurationViewModel.kt](https://github.com/pselvana/peloton-overlay/blob/f8d54d8f942bcf7140742efc31366f87c02e49b3/app/src/main/java/com/spop/poverlay/ConfigurationViewModel.kt#L51-L95)
- [OverlayService.kt](https://github.com/pselvana/peloton-overlay/blob/f8d54d8f942bcf7140742efc31366f87c02e49b3/app/src/main/java/com/spop/poverlay/overlay/OverlayService.kt#L67-L84)
- [OverlayService.kt](https://github.com/pselvana/peloton-overlay/blob/f8d54d8f942bcf7140742efc31366f87c02e49b3/app/src/main/java/com/spop/poverlay/overlay/OverlayService.kt#L131-L193)

The general API pattern is appropriate for Android 11 and matches the overlay
settings activity observed on this bike. The implementation lacks an explicit
`WindowManager.removeView()` cleanup path and is coupled to project-specific
ViewModels, preferences, resources, and websocket state. It should be
reimplemented narrowly rather than transplanted.

### Permissions and system changes

The manifest requests:

- `onepeloton.permission.ACCESS_SENSOR_SERVICE`
- `onepeloton.permission.SUBSCRIPTION_TYPE_ACCESS`
- `SYSTEM_ALERT_WINDOW`
- `FOREGROUND_SERVICE`
- `INTERNET`

Source:
[AndroidManifest.xml](https://github.com/pselvana/peloton-overlay/blob/f8d54d8f942bcf7140742efc31366f87c02e49b3/app/src/main/AndroidManifest.xml#L5-L10).

The code contains no root, bootloader, system-partition, or APK-patching logic.
It requires sideloading and user-approved overlay access. A privileged install
could be needed for SensorData access, but that would violate this project's
constraints and is not proposed.

### License and reuse

No `LICENSE`, `COPYING`, or `NOTICE` file and no license grant were found at
the examined commit. GitHub reports no detected license.

Without an explicit license, default copyright applies. The code must not be
copied, redistributed, or used to create a derivative implementation without
permission. It may be studied for behavior; any implementation here should use
standard Android overlay APIs and independently verified interoperability
facts rather than copied source.

GitHub guidance:
[Licensing a repository](https://docs.github.com/en/repositories/managing-your-repositorys-settings-and-features/customizing-your-repository/licensing-a-repository).

## Switchback

Repository:
[orbitalmutiny/switchback](https://github.com/orbitalmutiny/switchback).

Examined commit:
[`39c7fa23661f7cd0b9af81f855bebc4ca4c8d407`](https://github.com/orbitalmutiny/switchback/commit/39c7fa23661f7cd0b9af81f855bebc4ca4c8d407),
dated 2026-06-05. Latest examined release: `v0.3-beta1`.

### Hardware relevance

Switchback explicitly targets Peloton Bike+:
[README.md](https://github.com/orbitalmutiny/switchback/blob/39c7fa23661f7cd0b9af81f855bebc4ca4c8d407/README.md#L1-L5).

It identifies Bike+ only as exact model `PLTN-TTR01`. For any other
Peloton-branded model, including this RB1VQ, it selects a newer V1 Affernet
implementation:
[OverlayService.kt](https://github.com/orbitalmutiny/switchback/blob/39c7fa23661f7cd0b9af81f855bebc4ca4c8d407/app/src/main/java/com/spop/poverlay/overlay/OverlayService.kt#L134-L160).

That path binds `com.onepeloton.affernetservice.IV1Interface`, registers a raw
Binder callback, and extracts RPM, power, and resistance from `BikeData`:

- [Binder.kt](https://github.com/orbitalmutiny/switchback/blob/39c7fa23661f7cd0b9af81f855bebc4ca4c8d407/app/src/main/java/com/spop/poverlay/sensor/v1new/Binder.kt#L13-L22)
- [CallbackSensor.kt](https://github.com/orbitalmutiny/switchback/blob/39c7fa23661f7cd0b9af81f855bebc4ca4c8d407/app/src/main/java/com/spop/poverlay/sensor/v1new/CallbackSensor.kt#L67-L143)

The device-side action exists, but Switchback does not name or document testing
on `PLTN-RB1VQ`.

Its Bike+ path uses `IBikeInterface` and includes resistance writes. Those
writes are gated to `PLTN-TTR01` and are not relevant or appropriate for this
project.

### Android, overlay, and permissions

Switchback uses `minSdk 21`, `targetSdk 33`, and `compileSdk 33`; API 30 is in
range:
[app/build.gradle](https://github.com/orbitalmutiny/switchback/blob/39c7fa23661f7cd0b9af81f855bebc4ca4c8d407/app/build.gradle#L6-L16).

It uses a foreground `TYPE_APPLICATION_OVERLAY` service and requires the user
to grant draw-over-apps access. It also contains WorkoutServices and BLE heart
rate integrations. Its manifest requests Peloton sensor/subscription
permissions, overlay, foreground-service, network, and version-dependent
Bluetooth/location permissions:
[AndroidManifest.xml](https://github.com/orbitalmutiny/switchback/blob/39c7fa23661f7cd0b9af81f855bebc4ca4c8d407/app/src/main/AndroidManifest.xml#L5-L18).

No runtime root, bootloader unlock, system-image modification, or direct serial
control is required by the examined code or normal installation instructions.

### License and reuse

Switchback uses Business Source License 1.1:
[LICENSE](https://github.com/orbitalmutiny/switchback/blob/39c7fa23661f7cd0b9af81f855bebc4ca4c8d407/LICENSE#L1-L22).

Its Additional Use Grant permits non-commercial personal use on the user's own
Peloton, but restricts redistribution, service use, and competing commercial
products. The stated Change Date is 2030-05-22, after which the Change License
is MIT.

For this project, Switchback can be studied as a secondary implementation and
may be usable under the personal-use grant. OpenRide remains preferable because
its Apache-2.0 license is clearer for adaptation and it explicitly supports the
target model.

## OpenPelo

Repository: [doudar/Openpelo](https://github.com/doudar/Openpelo).

Examined commit:
[`1e99684e7c47b83743d29da1828eee7b4935b349`](https://github.com/doudar/Openpelo/commit/1e99684e7c47b83743d29da1828eee7b4935b349),
dated 2026-09-17. Version `1.0.77`.

OpenPelo is a Flutter desktop/mobile ADB management and APK installation tool.
It discovers devices through `adb devices` and `getprop`, inspects Android/API
and ABI compatibility, installs applications, and can set HOME:

- [adb_service.dart](https://github.com/doudar/Openpelo/blob/1e99684e7c47b83743d29da1828eee7b4935b349/lib/services/adb_service.dart#L269-L375)
- [adb_service.dart](https://github.com/doudar/Openpelo/blob/1e99684e7c47b83743d29da1828eee7b4935b349/lib/services/adb_service.dart#L1368-L1390)

It contains no Peloton sensor binding, AIDL, Messenger telemetry client,
Affernet client, or cadence/resistance/output parser. It is not a telemetry
source.

The project is MIT licensed:
[LICENSE](https://github.com/doudar/Openpelo/blob/1e99684e7c47b83743d29da1828eee7b4935b349/LICENSE).

OpenPelo's optional workflows include package uninstallation, firmware reset
guidance, force-stopping Device Management, changing HOME, and disabling an OEM
cleanup component. Those actions are outside this project's safety policy and
must not be executed. Its generic discovery ideas are useful, but the Android
companion should not depend on OpenPelo at runtime.

## Recommended reuse boundary

Subject to a successful on-device bind probe, reuse or adapt only the
Apache-2.0 OpenRide telemetry boundary:

```text
BikeDataSource
  -> AffernetBikeDataSource
      -> PelotonBikeDataSource (IV1Interface)
      -> PelotonBikeInterfaceDataSource (fallback)
  -> BikeMetrics / ConnectionState
```

Keep the following as independently implemented application code:

- HOME/launcher UI
- Just Ride UI
- elapsed-time and local session state
- Android foreground service
- system overlay/HUD
- Netflix/external application launch orchestration

Do not copy Grupetto code because no license grant was found. Avoid depending
on SensorData unless ordinary-app access is proven despite its signature
permission. Do not include any Bike+ resistance-write operation.

## Required validation before architecture commitment

The smallest safe next validation is a normal, independently signed,
non-launcher probe APK that:

1. Declares only the minimum package visibility needed for AffernetService.
2. Binds read-only to `com.onepeloton.affernetservice.IV1Interface`.
3. Registers the reconstructed callback.
4. Logs connection state and raw cadence, power, and resistance frames.
5. Performs no service writes other than callback registration/report-rate
   requests required by the read protocol.
6. Includes no HOME intent filter and requests no overlay permission.
7. Can be removed with a normal `adb uninstall`.

This probe should be proposed as part of Phase 4 and implemented only after
approval. If Affernet binding fails because of permissions or protocol drift,
the next candidate is WorkoutServices MetricsService investigation—not
privileged installation, signature bypass, package patching, or system
modification.

## Remaining uncertainties

1. Whether an ordinary APK can bind Affernet `IV1Interface` on `RQ.260424.A`.
2. Whether IV1 transactions and callback parcel layout remain compatible with
   OpenRide's `RQ.250113.A` implementation.
3. Whether power still requires division by 100 on Affernet version `2.4.500`.
4. Whether Affernet supplies stable frames without a stock Peloton ride active.
5. Whether the Android overlay grant persists under Peloton device-management
   policy.
6. Whether Netflix remains in the foreground while the HUD foreground service
   starts and updates.
