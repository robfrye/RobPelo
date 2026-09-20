# On-device validation

Tested on 2026-09-20 against the connected `PLTN-RB1VQ`, Android 11/API 30,
build `RQ.260424.A`.

## Stage 1: Affernet telemetry probe

- Installed `com.robpelo.companion` as a normal user APK.
- Bound successfully to
  `com.onepeloton.affernetservice.IV1Interface`.
- Received approximately one frame per second.
- Stationary reading: cadence 0, resistance 36, raw power 0, output 0 W.
- Pedaling reading captured: cadence 50, resistance 0, raw power 700,
  output 7 W.
- The raw-power result confirms the `/100` conversion on this firmware.
- Returning HOME removed the Affernet binding.
- Relaunching restored the binding and telemetry stream.
- No crashes were observed.

Raw screenshots and command captures were retained locally during development
but are intentionally excluded from Git.

## Stage 2: Just Ride

- Foreground service started successfully with notification ID 1001.
- Just Ride received live telemetry.
- Elapsed time advanced.
- A test ride accumulated 0.12 mi and 1.4 kJ.
- Pause/resume was exercised by the user.
- End Ride removed the foreground service and Affernet binding.
- No crashes were observed.

## Stage 3: Netflix HUD

- Android's standard overlay settings UI successfully granted access.
- Netflix launched through its package launch intent.
- Netflix remained the focused application.
- WindowManager reported a visible `TYPE_APPLICATION_OVERLAY` owned by
  `com.robpelo.companion`.
- The HUD measured 683 x 119 pixels and did not take keyboard focus.
- The foreground service and Affernet binding remained active under Netflix.
- Netflix's secure surface prevented screenshot capture; the empty PNG is
  retained as evidence of that platform behavior.
- The HUD Close control removed the overlay, service, and Affernet binding
  while leaving Netflix running.

## Stage 4: HOME

- Installing the HOME-capable build caused Android to return its resolver until
  a HOME was selected; it did not silently select RobPelo.
- RobPelo was explicitly selected and launched as HOME.
- The stock Peloton HOME was restored successfully.
- RobPelo was selected again after rollback validation.
- Both Just Ride and Netflix + HUD tiles work from RobPelo HOME.
- Ending each experience removed its service and Affernet binding.
- The final corrected build `0.4.2-home` was installed and smoke-tested.
- Final default HOME: `com.robpelo.companion/.HomeActivity`.
- Final at-rest state has no HUD window, foreground ride service, or companion
  Affernet binding.
- Seven unit tests pass: five ride-session tests and two power-scaling tests.

## Observed platform behavior

- Adding a second HOME-capable package caused Android's HOME resolver to become
  active until a default was explicitly selected. It did not silently select
  RobPelo.
- Netflix marks its content surface secure, so Android did not permit screenshot
  capture while Netflix was foreground. WindowManager and SurfaceFlinger state
  were used to verify the HUD instead.
- Peloton's own subscription/activation banner can appear over third-party
  activities. RobPelo does not disable or modify the Peloton component that
  owns that banner.
