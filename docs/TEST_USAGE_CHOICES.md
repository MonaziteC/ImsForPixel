# 1.2.2-usage-test1

This test is based on stable 1.2.1 (`e393cf579547955cf1d960713459fabe6d30d9df`). It keeps the stable Material UI, pairing, activation, full-config guardian and Undo controls. It does not include the radio probe, bandwidth history, timed captures or developer-options detector from data-test builds.

## Changes

- Developer/USB/wireless-debugging reads and their UI messages are removed. Configuration recovery and independent read-back never depend on these switches.
- With an authorized guardian running, each active SIM's Status card offers **Default / Voice-centric / Data-centric**, writing the real subscription values **0 / 1 / 2** respectively. No fourth hidden meaning is assigned to Default: it is the device's default policy, not necessarily voice-centric and not the previously captured value.
- The per-slot choice is saved after an explicit tap and reapplied on later authorized guardian starts. An installation with no saved choice initially observes the current subscription preference without modifying it. This control requires the guardian; ordinary one-time activation otherwise keeps its stable behavior.
- The display separates the chosen target from subscription read-back. Read-back confirms a saved preference, not modem acceptance, a particular SA/NSA mode, faster data or VoNR-call operation. Device-declared supported modes are checked per choice; missing support/unknown reads do not trigger writes.
- Call state on every SIM and original subscription/profile identity are checked before each mutation. No radio/IMS reset is issued by preference switching. Actual carrier-configuration recovery keeps its compare-before-write and rolling retry limits.

## Original-value recovery

The first original value is atomically recorded before any preference mutation. Repeated 0/1/2 changes preserve that original. Each requested value is added to the receipt's owned-value history before dispatch, so process death or a partially failed switch can still recover a previously applied value. Old data-test1..5 integer receipts are interpreted as an original plus an app-owned data-centric value (2), then migrated when needed. Records store a hash of SIM identity, not its raw ICCID.

Stopping the guardian, editing carrier settings, undoing defaults or reauthorizing attempts settled restoration of the original. Original is deliberately distinct from Default (0). External changes are preserved instead of being repeatedly overwritten. Failed recovery retains the receipt; force-stop/process death cannot guarantee cleanup. Restore the original before uninstalling, clearing app data or returning to stable 1.2.1, which does not implement receipt recovery. The guardian directory remains excluded from backup.

The release keeps versionCode 12 and the established local signing identity for cover-installation. Only this test branch is updated; main and the latest stable release are unchanged.

## Verification

Tests cover explicit three-mode dispatch, switching/default-vs-original semantics, first-original retention, interrupted switches/process recreation, legacy receipt migration, support checks, unknown identities/settings, partial/ignored writes, external changes, and unchanged full-config recovery. Run release build, unit/UI tests, Lint and `scripts/check-release-apk.py`; retain the source archive and R8 mapping. Real modem adoption and network performance require device verification.
