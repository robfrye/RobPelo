# Peloton Companion Rollback Procedure

This procedure restores the Peloton's stock HOME experience after a future
Peloton Companion installation. It is based on the device state captured on
2026-09-20 and does not require root, a bootloader unlock, a factory reset, or
changes to any Peloton package.

No device modification was made while creating this document.

## Known-good stock state

| Setting | Baseline value |
|---|---|
| Android user | `0` |
| Stock launcher package | `com.peloton.launcher` |
| Stock HOME component | `com.peloton.launcher/.LauncherActivity` |
| HOME RoleManager holder | `com.peloton.launcher` |
| Stock launcher package state | Installed and enabled |
| Stock launcher component state | Default/enabled; not explicitly disabled |
| Other HOME handler | `com.android.settings/.FallbackHome` |
| Packages allowed `SYSTEM_ALERT_WINDOW` | None reported |
| Stock launcher `SYSTEM_ALERT_WINDOW` app-op | `default` |

The original values are preserved in the table above. Raw per-device captures
are intentionally excluded from Git.

## Before the first device-changing test

The companion application's package ID is `com.robpelo.companion`. Retain this
same ID for all future builds.

Set these variables in each new Terminal session:

```bash
COMPANION_PACKAGE='com.robpelo.companion'
MEDIA_BROWSER_PACKAGE='com.robpelo.browser'
STOCK_HOME='com.peloton.launcher/.LauncherActivity'
ANDROID_USER='0'
```

Confirm the package variable before running device-changing commands:

```bash
test "$COMPANION_PACKAGE" = 'com.robpelo.companion'
test "$MEDIA_BROWSER_PACKAGE" = 'com.robpelo.browser'
```

Before changing HOME or granting special access, append the companion's
pre-change state to a local log:

```bash
adb devices -l
adb shell cmd package resolve-activity --brief \
  -a android.intent.action.MAIN \
  -c android.intent.category.HOME
adb shell cmd appops get --user "$ANDROID_USER" \
  "$COMPANION_PACKAGE" SYSTEM_ALERT_WINDOW
```

The companion package will normally have no overlay app-op before the user
grants special access.

## Fast rollback: restore the stock HOME

Use this if the companion launcher fails, loops, crashes, or leaves the display
unusable. These commands stop only the companion application and restore the
known stock launcher:

```bash
adb devices -l
adb shell am force-stop --user "$ANDROID_USER" "$COMPANION_PACKAGE"
adb shell cmd package set-home-activity --user "$ANDROID_USER" "$STOCK_HOME"
adb shell am start --user "$ANDROID_USER" \
  -a android.intent.action.MAIN \
  -c android.intent.category.HOME
```

Verify that HOME resolves to the stock component:

```bash
adb shell cmd package resolve-activity --brief \
  -a android.intent.action.MAIN \
  -c android.intent.category.HOME
```

Expected component:

```text
com.peloton.launcher/.LauncherActivity
```

Also verify the role holder:

```bash
adb shell dumpsys role |
  grep -A 3 -B 1 'name=android.app.role.HOME'
```

Expected holder:

```text
holders=com.peloton.launcher
```

## Remove only the companion's overlay access

If the companion should remain installed but its HUD must be disabled, stop it
and return only its overlay app-op to the default state:

```bash
adb shell am force-stop --user "$ANDROID_USER" "$COMPANION_PACKAGE"
adb shell cmd appops set --user "$ANDROID_USER" \
  "$COMPANION_PACKAGE" SYSTEM_ALERT_WINDOW default
```

Verify:

```bash
adb shell cmd appops get --user "$ANDROID_USER" \
  "$COMPANION_PACKAGE" SYSTEM_ALERT_WINDOW
```

This changes only the companion package's app-op. It does not change Netflix,
the Peloton application, or the stock launcher.

## Complete rollback: uninstall the companion

Restore HOME before uninstalling the launcher package:

```bash
adb shell am force-stop --user "$ANDROID_USER" "$COMPANION_PACKAGE"
adb shell cmd package set-home-activity --user "$ANDROID_USER" "$STOCK_HOME"
adb shell am start --user "$ANDROID_USER" \
  -a android.intent.action.MAIN \
  -c android.intent.category.HOME
adb uninstall "$COMPANION_PACKAGE"
```

Then verify:

```bash
adb shell cmd package resolve-activity --brief \
  -a android.intent.action.MAIN \
  -c android.intent.category.HOME
adb shell pm list packages "$COMPANION_PACKAGE"
adb shell pm list packages -e com.peloton.launcher
adb shell pm list packages -d com.peloton.launcher
```

Expected results:

- HOME resolves to `com.peloton.launcher/.LauncherActivity`.
- The companion package query returns no package.
- The enabled query returns `package:com.peloton.launcher`.
- The disabled query returns nothing.

Uninstalling removes only the companion APK, its private data, and its granted
permissions.

The media browser is a separate package and is not removed by that command.
Keep it installed when rolling HOME back unless its profile is no longer
needed. Uninstalling it permanently deletes its cookies, authenticated
sessions, permissions, and local storage. If that destructive cleanup is
explicitly approved:

```bash
adb shell am force-stop --user "$ANDROID_USER" "$MEDIA_BROWSER_PACKAGE"
adb uninstall "$MEDIA_BROWSER_PACKAGE"
```

## Narrow fallback if the normal HOME restore fails

First confirm the stock launcher still exists and remains enabled:

```bash
adb shell pm list packages com.peloton.launcher
adb shell pm list packages -e com.peloton.launcher
adb shell pm list packages -d com.peloton.launcher
```

If it is installed and enabled but `set-home-activity` did not restore the
RoleManager holder, re-add only the known stock package as HOME and retry:

```bash
adb shell cmd role add-role-holder --user "$ANDROID_USER" \
  android.app.role.HOME com.peloton.launcher 0
adb shell cmd package set-home-activity --user "$ANDROID_USER" "$STOCK_HOME"
adb shell am start --user "$ANDROID_USER" \
  -a android.intent.action.MAIN \
  -c android.intent.category.HOME
```

Do not run `pm enable`, `pm disable`, `pm clear`, or uninstall commands against
`com.peloton.launcher` or any other Peloton package. If the stock launcher is
unexpectedly missing or disabled, stop and investigate instead of changing its
state.

## Commands that are not part of rollback

Do not use any of the following:

- `pm clear` against a Peloton package
- `pm disable` or `pm disable-user` against a Peloton package
- `pm uninstall` against a Peloton package
- `adb reboot bootloader`
- `fastboot` commands
- Factory-reset or recovery-wipe commands
- System-partition remounting
- APK replacement or patching

The rollback design depends only on restoring the stock HOME preference,
stopping or uninstalling our own APK, and resetting permissions granted only to
our own APK.
