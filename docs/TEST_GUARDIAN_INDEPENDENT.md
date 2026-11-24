# 1.2.2-data-test4: recovery independent of developer options

The previous builds incorrectly made full-config recovery and its verification depend on confirmed developer/USB/wireless toggles being off. Android 17's redacted or unavailable readings could therefore block the guardian. This was an incorrect dependency, independently of whether a particular debug-state reader works.

## Behavior

- `RetainedConfigRecovery.tick` no longer accepts a debug-state parameter or a debug-state callback. A readable, mismatching app-owned configuration is eligible for repair immediately; there is no wait for the switches to close or stabilize.
- The immediate pre-dispatch check tests only valid authorization, all active SIMs idle, and the original target SIM still present. A late change defers the write and retries later. Missing permissions, unavailable configuration and rate limits still prevent writes.
- A later independently read matching configuration confirms the repair regardless of developer options, USB debugging, wireless debugging or their visibility. A matching cached value cannot conceal revoked permission.
- The existing compare-before-write, all-custom-keys merge, rolling six-write/five-minute limit, minimum five-second write spacing, independent read-back and failure backoff remain. No radio/flight-mode reset or new persistent override is added.
- The data-centric experiment's mutation callback also no longer reads debug state. Its original-value journal, identity checks, idle-call requirement and undo recovery remain unchanged.

## Display-only diagnostics

Debug-state queries run asynchronously on their own worker. The guardian does not wait for their result, and changed debug readings do not wake the configuration-recovery loop. The reader may display On, Off or Unknown; none of them permits, blocks, verifies or changes a carrier/preference write. Query failures cannot stop the recovery loop. UI text explicitly states that an unknown reading does not block recovery. Exported `debug_off` is display metadata and is null for unknown/stale observations.

The test3 shell reader is retained only as optional status information. No additional authorization is required solely for that display. Before authorization it may show Unknown.

## Verification

Host tests cover immediate repair, independent read-back, late call/SIM/permission deferral, revoked permissions, repeated loss, rolling rate limits, persistent write rejection, and recovery when only Wi-Fi-calling keys are lost. UI tests retain a healthy guardian status even when debug readings are unknown or redacted. Release checks reject the obsolete `waiting_debug_off` state in DEX.

On the phone, cover-install and start the authorized guardian. Check configuration recovery after leaving Phone information V2 with developer options left on, then with them off. Debug-state display accuracy is a separate observation and is not a prerequisite. Reboot/session loss still requires normal reauthorization.

This remains a test build. Stable releases are unchanged. Data-centric selection remains unproven as a performance improvement; recover its recorded original preference before uninstalling or returning to stable.
