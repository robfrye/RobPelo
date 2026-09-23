# RobPelo Media Browser downstream

The media browser is a pinned downstream overlay for official Brave
`v1.95.104` / Chromium `153.0.8010.53`. It produces a separate Android package:

```text
Package: com.robpelo.browser
Public activity: com.robpelo.browser/.MediaViewerActivity
Action: com.robpelo.browser.action.OPEN_MEDIA
Extra: service
Values: netflix, youtube, hbo_max, prime_video, apple_tv
Permission: com.robpelo.browser.permission.OPEN_MEDIA (signature)
```

The public activity accepts no URL. It maps the service identifier to a
compile-time destination and starts a non-exported activity derived from
Brave's `FullScreenCustomTabActivity`. The internal activity:

- uses Brave's normal persistent profile, cookies, Widevine, and media stack;
- hides the toolbar on allowlisted playback pages;
- shows Brave's origin/TLS toolbar for login, account, and payment pages;
- rejects top-level navigation outside the destination allowlist;
- applies desktop UA mode only on HBO Max service origins;
- returns to RobPelo when its close control or terminal Back action finishes;
- exits with a visible error after a renderer crash.

The Chromium manifest patch removes the primary exported launcher, tabbed
browser, and URL-dispatcher entry points. The merged manifest must still be
audited before release to confirm that no other exported component makes the
dedicated package qualify for or claim the general Android browser role.

## Apply to a Brave checkout

Use Brave's supported Linux Android build environment, initialize the exact
`v1.95.104` tag according to Brave's official build documentation, and finish
Brave's normal `init`/sync step first. The script verifies both the brave-core
tag and Chromium version before changing the checkout. From this repository:

```bash
scripts/apply-media-browser-overlay.sh /path/to/brave-browser/src
```

The complete Microsoft Dev Box/WSL checkout, build, signing, and authentication
procedure is in
[the Dev Box build runbook](../docs/DEVBOX_MEDIA_BROWSER_BUILD.md).

Set this GN argument for the Android build:

```text
chrome_public_manifest_package = "com.robpelo.browser"
```

Sign the browser and RobPelo APK with the same release certificate. Android's
signature permission then prevents unrelated apps from launching the exported
media activity. A conflicting preinstalled declaration of
`com.robpelo.browser.permission.OPEN_MEDIA` must be investigated rather than
bypassed.

The full Brave/Chromium checkout and Android build are intentionally not
vendored here. They are large upstream sources with their own build,
dependency, and license processes.

## Policy test

The pure Java destination and navigation policy can be tested without a
Chromium checkout:

```bash
scripts/test-media-browser-policy.sh
```

An APK is not accepted for device testing until the upstream checkout compiles,
the merged manifest contains only the intended exported media contract, and
the APK package, signer, ABI, SDK, and digest are recorded.

Before the first protected-content test, use RobPelo's **Open media browser
setup (no HUD)** action. Android rejects protected-content permission prompts
while another application's overlay is visible. After the prompt succeeds,
return to RobPelo and test the normal tile with the HUD.
