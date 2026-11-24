# 1.2.2-data-test1

This is a private-device test APK, not a stable release or a proven throughput fix. It tests whether data-centric cellular selection can coexist with the user's enabled VoNR configuration and improve the reported indoor performance.

## Use

1. Update over 1.2.1 without uninstalling. Keep VoLTE, VoNR, NR and the 5G+ display policy as desired, and apply/start the guardian using the existing authorization flow.
2. Wait for authorization to be ready and close developer options. The new per-SIM switch is on the **Status** page, below IMS registration, and starts off for each guardian session.
3. In the slow indoor location, leave VoNR enabled. Refresh and copy a baseline radio snapshot; measure speed using the same test server.
4. Enable **Data-centric selection (experiment)**. Wait for preference read-back, then wait around a minute for the network to settle. Repeat the speed test and make/receive a call. Refresh/copy another snapshot during the call if practical.
5. Turn the experimental switch off, wait for **Original selection setting restored**, and repeat the baseline measurement. The switch can be changed while the authorized keeper remains alive, with developer options off.

The diagnostic display distinguishes subscription preference read-back from modem acceptance. If the device does not declare data-centric support, the original preference cannot be read, or a stable SIM identity cannot be established, no new preference is written. An already data-centric baseline is reported as an unchanged setting.

## Scope and recovery

- VoNR and the existing carrier configuration are not silently toggled. There is no signal-based mode switching, call-triggered VoNR enablement, band lock, speed-test traffic, flight-mode toggle or experimental IMS reset.
- The dedicated experiment changes the subscription usage preference through the framework subscription service. It deliberately does not add `cellular_usage_setting_int` to the guarded CarrierConfig bundle: that key can also change the subscription database, so merely removing a temporary override would not undo it.
- The original preference is atomically recorded before mutation. Records are associated with a hash of the privileged SIM ICCID so a reused subscription ID cannot cause an undo on another profile. Raw ICCIDs are not stored or copied into snapshots.
- Turning the experiment off, editing settings, stopping the guardian or restoring defaults attempts to restore the captured original. All preference writes wait for all SIM calls to be idle. Independent settled reads are required before an undo receipt is removed. External setting changes are preserved instead of continually overwritten.
- A killed process cannot execute cleanup. Permission loss, an ongoing call, a missing SIM or a failed operation can leave recovery pending. The app retains the receipt and shows a recovery notice. Reauthorize and start the guardian (or restore defaults), with the original SIM present, to retry cleanup.
- This preference is a system-database setting and can outlive the guardian or a reboot. **Finish recovery before uninstalling, clearing app data or returning to another APK**, otherwise the saved original could be lost. The guardian directory is excluded from cloud/device-transfer backups.
- The test APK uses the same signature and versionCode 12 as stable 1.2.1 so both side-loaded APKs can replace each other without uninstalling. The version name identifies the test. Restore the experimental preference before returning to stable 1.2.1.

## Observations

The status panel shows cached data RAT, IMS registration technology if exposed, call state, the VoNR permission switch, NR RSRP/SINR, registered NR bands if location access permits them, and the system's reported bandwidth list. Missing values remain unknown. Snapshots do not include phone numbers, ICCIDs, cell IDs or location coordinates, and are copied only when the user taps Copy. No logs are automatically uploaded.

Normal sampling is throttled to at most once per ten seconds while the experiment is active; explicit refresh requests a new observation. Without an active experiment, the existing guardian polling interval applies. No network/cell scan is requested. IMS registration, icons and a combined bandwidth list alone cannot establish the active voice bearer or a particular carrier-aggregation combination.

## Build validation

Run `./gradlew assembleRelease testDebugUnitTest lintRelease` and `scripts/check-release-apk.py` as described in BUILDING.md. Tests cover durable capture-before-write, process recreation recovery, unsupported/unknown modes, partial failures, external changes, bounded writes, repeated A/B cycles and truthful UI read-back labels. Real Pixel throughput, modem adoption and VoNR calling must still be tested on the phone.
