# RobPelo

RobPelo is an unofficial, subscription-free companion and home-screen
experience for compatible Peloton Bikes.

It provides:

- a touch-friendly HOME screen;
- a standalone Just Ride experience;
- live cadence, resistance, and output;
- locally calculated speed, elapsed time, distance, and total output;
- Netflix launching with a compact telemetry HUD.

RobPelo runs as a normal Android application. It does not require root,
bootloader unlocking, firmware modification, or replacement of Peloton system
applications.

> [!IMPORTANT]
> RobPelo is an independent project and is not affiliated with or endorsed by
> Peloton Interactive, Inc. It relies on an undocumented on-device telemetry
> interface that Peloton may change in a future software update.

## Compatibility

Verified configuration:

| Property | Tested value |
|---|---|
| Bike model | `PLTN-RB1VQ` |
| Android | 11 / API 30 |
| Peloton build | `RQ.260424.A` |
| CPU ABI | `arm64-v8a` |
| AffernetService | `2.4.500` |
| Netflix | `com.netflix.mediaclient` |

Other models and firmware versions are not yet verified. In particular, do not
assume Bike+ compatibility even though its software exposes some similarly
named interfaces.

See the [device baseline](./device-baseline/README.md) and
[on-device validation results](./device-tests/README.md).

## Safety model

RobPelo:

- installs as the ordinary user package `com.robpelo.companion`;
- binds read-only to Peloton's Affernet `IV1Interface`;
- never enables fake telemetry;
- never writes resistance or invokes calibration or motor controls;
- never disables, uninstalls, replaces, patches, or clears a Peloton package;
- does not modify the system partition;
- does not start automatically at boot;
- can be removed with normal ADB commands.

The source contains a `setFakeDataMode` declaration only because its position
is required to preserve Binder transaction numbering. RobPelo never calls it.

Before installing, read [the rollback procedure](./docs/ROLLBACK.md).

## What you need

- A compatible Peloton connected by USB
- USB debugging enabled on the bike
- ADB
- JDK 17
- Android SDK Platform 34
- Android SDK Build Tools 34.0.0

Android Studio is not required.

### macOS setup with Homebrew

```bash
brew install openjdk@17
brew install --cask android-platform-tools android-commandlinetools

export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
export ANDROID_HOME=/opt/homebrew/share/android-commandlinetools

yes | sdkmanager --licenses
sdkmanager 'platforms;android-34' 'build-tools;34.0.0'
```

On Intel Macs, Homebrew may use `/usr/local` rather than `/opt/homebrew`.
Use `brew --prefix openjdk@17` and `brew --prefix android-commandlinetools` if
the paths above do not match your installation.

On Linux or Windows, install JDK 17 and Google's Android SDK Platform Tools and
Command-line Tools, then set `JAVA_HOME` and `ANDROID_HOME` for those
installations.

## Clone and build

```bash
git clone https://github.com/robfrye/RobPelo.git
cd RobPelo

export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
export ANDROID_HOME=/opt/homebrew/share/android-commandlinetools

./gradlew testDebugUnitTest lintDebug assembleDebug
```

The APK will be written to:

```text
app/build/outputs/apk/debug/app-debug.apk
```

The Gradle Wrapper is included, so a separate Gradle installation is not
required.

## Check your bike before installing

Connect exactly one intended Android device and run:

```bash
adb devices -l
```

The bike must appear with state `device`. If it says `unauthorized`, wake the
display and approve the USB-debugging prompt. Do not proceed if the listed
device is not the bike you intend to modify.

Collect the compatibility values:

```bash
adb shell getprop ro.product.model
adb shell getprop ro.build.version.release
adb shell getprop ro.build.display.id
adb shell getprop ro.product.cpu.abilist
adb shell cmd package query-services --brief \
  -a com.onepeloton.affernetservice.IV1Interface
adb shell pm list packages -e com.netflix.mediaclient
```

For the tested configuration, the service query resolves to:

```text
com.onepeloton.affernetservice/.AffernetService
```

If the service does not resolve, RobPelo telemetry is not expected to work.
Do not try to fix that by rooting the bike or modifying Peloton packages.

Record the original HOME before continuing:

```bash
adb shell cmd package resolve-activity --brief \
  -a android.intent.action.MAIN \
  -c android.intent.category.HOME
```

On the tested bike, the stock result is:

```text
com.peloton.launcher/.LauncherActivity
```

## Install safely

First install RobPelo without selecting it as HOME:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.robpelo.companion/.HomeActivity
```

Test **Diagnostics** and **Just Ride** before changing HOME. Confirm that
cadence, resistance, and output respond while pedaling.

### Enable the Netflix HUD

1. Open RobPelo.
2. Tap **Netflix + HUD**.
3. Android opens **Display over other apps**.
4. Select **RobPelo** and enable the permission.
5. Press Back until RobPelo launches Netflix.

The HUD's **Close** control stops telemetry and removes the overlay without
closing Netflix.

## Make RobPelo the HOME screen

Only do this after Diagnostics, Just Ride, and rollback have been reviewed:

```bash
adb shell cmd package set-home-activity --user 0 \
  com.robpelo.companion/.HomeActivity
adb shell input keyevent KEYCODE_HOME
```

Verify:

```bash
adb shell cmd package resolve-activity --brief \
  -a android.intent.action.MAIN \
  -c android.intent.category.HOME
```

Expected:

```text
com.robpelo.companion/.HomeActivity
```

Adding a new HOME-capable APK may temporarily show Android's launcher chooser
until a default is selected. This is normal.

## Restore the stock Peloton HOME

Keep ADB connected until this has been tested:

```bash
adb shell cmd package set-home-activity --user 0 \
  com.peloton.launcher/.LauncherActivity
adb shell input keyevent KEYCODE_HOME
```

Verify that the resolver returns:

```text
com.peloton.launcher/.LauncherActivity
```

To remove RobPelo completely, restore the stock HOME first and then run:

```bash
adb uninstall com.robpelo.companion
```

Never uninstall, disable, or clear `com.peloton.launcher` or another Peloton
package. More recovery details are in [docs/ROLLBACK.md](./docs/ROLLBACK.md).

## Updating

Build the new revision and use:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Android may show the HOME chooser again after an update. Reselect RobPelo or
run the HOME command above.

Debug APKs are signed with each developer machine's debug key. An APK built on
one machine cannot update an APK signed with a different machine's key. For
friend-friendly binary releases, publish APKs signed consistently with a
dedicated release key and never commit that key or its passwords.

If an update reports `INSTALL_FAILED_UPDATE_INCOMPATIBLE`, restore the stock
HOME before uninstalling the existing RobPelo package.

## Troubleshooting

### ADB reports `unauthorized`

Wake the bike, reconnect USB, and accept the debugging authorization prompt.

### More than one Android device is connected

Use the serial shown by `adb devices -l`:

```bash
adb -s DEVICE_SERIAL install -r app/build/outputs/apk/debug/app-debug.apk
```

Add `-s DEVICE_SERIAL` immediately after `adb` in every subsequent command.

### Telemetry remains disconnected

Confirm that the Affernet action resolves using the preflight command above.
If it resolves but no frames arrive, record the model, Peloton build, and
AffernetService version. Do not attempt privileged installation or SensorData
permission workarounds.

### Netflix opens without a HUD

Return to RobPelo, tap **Netflix + HUD**, and verify that Android reports
RobPelo as allowed under **Display over other apps**.

### Peloton subscription banner appears

Peloton's own system software may display its subscription/activation banner
over third-party activities. RobPelo intentionally does not disable or modify
the Peloton component responsible for that banner.

## Development

Run the complete local validation:

```bash
./gradlew clean testDebugUnitTest lintDebug assembleDebug
```

Architecture and implementation details:

- [Architecture](./docs/ARCHITECTURE.md)
- [Existing-project evaluation](./docs/EXISTING_PROJECTS.md)
- [Rollback procedure](./docs/ROLLBACK.md)
- [OpenRide attribution](./third_party/openride/UPSTREAM.md)

## Licensing and attribution

RobPelo is available under the
[Apache License 2.0](./LICENSE). See [NOTICE](./NOTICE) for attribution.

The Affernet AIDL, Parcelable interoperability implementation, and speed curve
are adapted from OpenRide commit
`a8b2aceae722c3fb2d5159f0a6389ba3cadd4725` under Apache License 2.0.
See [third_party/openride/](./third_party/openride/).

No Grupetto/Peloton Overlay code is included because that repository has no
identified license grant.
