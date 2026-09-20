# Peloton Subscription Upsell Overlay

Investigation date: 2026-09-20.

No RobPelo code or Peloton package state was changed during this investigation.
The visible close glyph was tapped once as a normal UI interaction; it did not
dismiss the overlay.

## Finding

The yellow message:

```text
Your subscription was removed from this Bike. Please activate using an
existing subscription.
```

is not part of RobPelo and is not a conventional Android app overlay.

WindowManager identifies it as:

```text
Owner package: com.peloton.activity
Owner UID: 10017
Window type: SECURE_SYSTEM_OVERLAY
App-op: NONE
Size: 1920 x 1008
Flags:
  NOT_FOCUSABLE
  NOT_TOUCHABLE
  NOT_TOUCH_MODAL
```

The owning Peloton process is kept at an important-foreground adjustment
because it has overlay UI. The window is above ordinary application windows,
including RobPelo's HOME.

## Trigger

The installed Peloton APK contains the exact string as resource:

```text
global_subscription_detached_error
```

The subscription controller maps a non-active/detached subscription status to
an internal notification state using that resource. Android's global settings
currently report:

```text
subscription_mode=0
subscription_type_id=inactive
```

The APK also contains an internal notification key:

```text
subscription_error_notification
```

Peloton code dismisses that key after successful subscription activation. No
exported activity, receiver, provider, or public Binder interface for
dismissing this notification was found.

## Related Peloton components

The Peloton application contains internal overlay components:

```text
com.onepeloton.home.library.hardwarecontrol.HomeAuroraMetricsOverlay
com.onepeloton.home.library.hardwarecontrol.SystemOverlayService
com.peloton.overlay.service.BacchusMetricPillService
```

`SystemOverlayService` is explicitly `exported=false`. The overlay also
interacts with the privileged Peloton SystemUI plugin:

```text
com.onepeloton.systempluginui/.overlay.SystemPluginUIOverlayService
```

That plugin is bound by Android SystemUI and has privileged permissions. It
should not be disabled or modified.

## Why common approaches will not work

### Revoking `SYSTEM_ALERT_WINDOW`

This will not remove the bubble. WindowManager reports the Peloton window with
`appop=NONE`, and `com.peloton.activity` has the privileged
`INTERNAL_SYSTEM_WINDOW` permission. It is not relying on the user-controlled
draw-over-other-apps grant.

### Drawing a RobPelo overlay above it

RobPelo's `TYPE_APPLICATION_OVERLAY` windows are below
`SECURE_SYSTEM_OVERLAY` in WindowManager's layer ordering. A normal application
cannot place a window above this Peloton window.

### Simulating a tap on the close glyph

WindowManager reports the full Peloton overlay as `NOT_TOUCHABLE`. A test tap on
the visible glyph did not remove it, and the overlay remained after navigating
to Just Ride and back HOME.

### Calling Peloton's notification controller

The notification controller is internal to the Peloton process. No exported
dismiss API was found, so RobPelo cannot legitimately invoke it.

### Changing subscription settings

Changing `subscription_type_id`, `subscription_mode`, Peloton app data, or
internal databases would spoof or corrupt Peloton-managed state. This is not a
safe or supported solution and conflicts with the project's reversibility
policy.

## Viable options

### 1. Perform a normal reboot

This is the safest tested workaround. With RobPelo already selected as HOME, a
normal reboot produced these results:

- RobPelo opened automatically after Android completed booting.
- The HOME selection and RobPelo overlay grant persisted.
- The yellow subscription message was absent after 15 seconds.
- It remained absent after 75 seconds, even though the
  `com.peloton.activity` process and its transparent secure-overlay
  infrastructure had restarted.
- It remained absent after approximately three minutes and after opening Just
  Ride.

This indicates that the visible message is transient notification state rather
than an unavoidable part of the overlay window. A future subscription refresh
or Peloton workflow may post it again, so reboot is a workaround rather than a
guaranteed permanent suppression mechanism.

### 2. Accommodate the banner in RobPelo

Reserve the upper portion of the launcher and Just Ride layouts so the Peloton
banner does not cover important content if it returns. The current controls
already remain usable beneath it.

Advantages:

- no Peloton modification;
- no dependency on undocumented dismissal behavior;
- survives reboot and Peloton software updates;
- safe for friends to install.

Disadvantage: the banner remains visible.

### 3. Test a temporary force-stop of `com.peloton.activity`

A controlled `am force-stop com.peloton.activity` experiment would likely
remove the window because that process owns it. This has **not** been executed.

Risks and limitations:

- it changes Peloton package runtime state;
- it may interrupt stock Peloton activities and in-process services;
- the package may restart after boot, an update, or another system event;
- opening the stock Peloton application would start it again;
- it is not suitable for automatic use without proving all side effects.

This experiment requires explicit approval under the project's safety rules.
If tested, the stock application can ordinarily be restarted by launching its
documented activity or rebooting normally, but exact recovery steps must be
captured first.

### 4. Disable an internal Peloton overlay component

Not recommended. The candidate components are internal and some participate in
hardware metrics or SystemUI integration. Component disabling would modify a
Peloton package's enabled state, violates the current safety constraints, and
may not target the subscription notification path.

### 5. Modify or patch Peloton software

Out of scope. Patching the APK, changing its private data, altering subscription
state, using root, or modifying the system image would be fragile and violate
the project's safety requirements.

## Recommendation

Use a normal reboot first. For a shareable and reversible RobPelo release,
continue designing around the banner in case Peloton posts it again.

If removal is still important, the only reasonable next experiment is a
manually approved, temporary force-stop test with pre-recorded recovery steps.
Do not automate that behavior in RobPelo unless repeated testing proves it safe
and acceptable.
