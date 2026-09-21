# Peloton Netflix Subscription Gating

Investigation date: 2026-09-21.

## Finding

The current Peloton software actively prevents the built-in Netflix package
from running when the bike's subscription state is inactive.

Device state:

```text
subscription_type_id=inactive
subscription_mode=0

Netflix package: com.netflix.mediaclient
Netflix version: 9.40.0 build 7
Netflix launch activity: .ui.launch.UIWebViewActivity
```

Launching Netflix's explicit activity caused this sequence:

1. Netflix process started and briefly became foreground.
2. Android SystemUI broadcast:
   `com.onepeloton.systempluginui.ACTION_3P_INVALID_ACCESS`.
3. Both `com.peloton.activity` and `com.onepeloton.systempluginui` received the
   broadcast under permission
   `onepeloton.permission.ACTION_3P_INVALID_ACCESS`.
4. Netflix was force-stopped by PID 2260,
   `com.onepeloton.systempluginui`.
5. Android recorded Netflix's exit as:

```text
reason=USER REQUESTED
description=stop com.netflix.mediaclient due to from pid 2260
```

This is Peloton access control, not a Netflix crash.

## Supported Peloton path

The documented internal entertainment action:

```text
android.intent.action.peloton.entertainment
```

resolves to Peloton's `BrowseActivity`, but on this inactive bike it redirects
to `ActivationActivity` and displays the subscription/activation requirement.

Therefore, neither direct Netflix launch nor Peloton's own entertainment entry
provides a subscription-free path.

## Why RobPelo does not bypass it

Potential bypasses would require one or more of:

- disabling or modifying Peloton SystemUI components;
- spoofing Peloton subscription state;
- patching Netflix or Peloton APKs;
- interfering with Peloton's privileged broadcasts or force-stop logic;
- changing Peloton package data or permissions.

Those approaches are fragile and violate RobPelo's reversible/no-Peloton-
modification safety policy.

## TV Bro replacement

Netflix web was tested in TV Bro 2.1.6 with its bundled GeckoView engine:

- Netflix website rendered.
- Sign-in succeeded.
- Video and audio playback worked.
- Video fullscreen worked.
- RobPelo HUD remained visible and functional.
- Netflix login survived a TV Bro force-stop/relaunch.
- Netflix login survived a full bike reboot.
- RobPelo remained default HOME.
- The vendor Netflix package remained installed and unchanged.

RobPelo `0.7.2-tvbro-streaming` now launches:

```text
https://www.netflix.com/browse
```

in:

```text
com.phlox.tvwebbrowser/.activity.main.MainActivity
```

The built-in Netflix APK is retained for reversibility but is no longer used by
the RobPelo Netflix tile.

## Support and DRM caveat

Netflix's official browser-support documentation lists computers, Chromebook,
iPad, Meta Quest, and selected GNU/Linux configurations; it does not list
ordinary Android browsers as a supported netflix.com playback platform:

- [Netflix supported devices](https://help.netflix.com/en/node/14361)
- [Netflix browser requirements](https://help.netflix.com/en/node/30081)

The physical TV Bro test proves usable playback on this specific RB1VQ,
`RQ.260424.A`, TV Bro 2.1.6 combination. It does not establish:

- Widevine L1;
- Netflix device certification;
- HD, Full HD, HDR, or a specific bitrate;
- future compatibility after Netflix, TV Bro, or Peloton updates.

RobPelo should treat Netflix web as a tested but officially unsupported
fallback. The built-in Netflix package remains installed in case Peloton later
restores access.
