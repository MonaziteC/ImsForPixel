# Architecture and maintenance

## UI

`MainActivity` owns only activity initialization, saved ADB identity and the optional notification pairing entry. `ui/PixelImsTheme` uses Material 3 dynamic system colors on API 31+, with official fallback palettes and system dark mode. `ui/PixelImsApp` owns the two destinations and short-lived authorization sheet. `ui/AdbConnection` discovers only local service addresses and opens loopback ADB sockets only while the sheet is in use.

Busy state is distinct from a running guardian. A running session neither spins the activation button nor displays disconnected ADB as an application failure. Read-only switches retain their chosen appearance. IMS status is read in the privileged session; unavailable reads are null/unknown, never automatically false. Old state is not reported as a live successful registration.

## Activation and ownership

`GuardianController` freezes both SIMs' desired preferences in a versioned private request. `ImsCarrierConfig` is the one source of truth shared by ordinary activation and recovery. `NrCarrierConfig` adds optional NR/icon values; Keep SA is removed entirely. Initial activation clears the old runtime layer before applying the new bundle, so a previous SA override is not retained by omission.

The detached validated shell launcher runs the original activation broker, then a retained instrumentation targeting only `com.svenuks.imsforpixel:keeper`. Its service and private command receiver share that process. Ordinary activity recreation is not part of instrumentation. The authorization owner connection remains another required lifecycle participant; a plain service restart cannot recreate authority.

Only app-owned settings are merged during recovery. No runtime clear, IMS reset, flight-mode toggle or persistent write occurs during automatic recovery. The app does not automatically set developer/ADB settings. Each initial active slot is frozen to its subscription; a replacement SIM is not silently adopted. All writes pause during calls.

## State / commands

Private AtomicFile snapshots contain small latest state, boot count, PID, per-SIM read status and recovery counters. UI checks both boot and a matching live keeper PID. There is no diagnostic history or export. Commands `stop`, `edit`, `restore` are validated and committed atomically before waking the private receiver.

On edit/stop, authorization is released normally. Android's instrumentation finish may close all app processes. The existing detached launcher captures a fixed `ui_action` result and reopens the intended page only after an explicit user command; crashes do not trigger an unconditional activity restart.

## Restore

With a running permission session, a restore command stops automatic repair before clearing temporary runtime overrides on the originally bound, still-present SIMs. Calls delay this operation. A CarrierConfig change callback after the clear confirms that the system processed a configuration change; this is not an inspection of the private override layer. If no callback is observed, UI reports only a submitted request. No persistent=true call is used.

Without a session, restore uses the normal wireless authorization/broker route. User-selected presets are retained for possible later application. UI distinguishes a restored/stopped session from a running guardian.

## Limits

A typed specialUse foreground service improves survival but cannot guarantee permanence. The user requested no drawer notification: initial authorization revokes only this app's POST_NOTIFICATIONS permission on supported Android versions. The service still submits the required valid notification. Active-apps UI can still show it.

The guardian has rolling write rate limits and bounded backoff rather than a lifetime cap. Doze may delay callbacks/checks. Force-stop, reboot, keeper death or loss of the UiAutomation owner can end authorization. Real stock Pixel firmware behavior needs device tests beyond JVM/Compose UI tests.

## Tests

Tests cover configuration completeness, omitted SA policy, false options, NR values, array comparison, rolling retry behavior, frozen SIM targets, command validation, shell syntax and state freshness. Compose semantics tests ensure a healthy guardian is not shown as a loading operation, restore is reachable, and unknown IMS reads are not shown as unregistered.
