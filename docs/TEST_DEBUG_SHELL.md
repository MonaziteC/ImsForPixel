> Historical build. Superseded by [test4](TEST_GUARDIAN_INDEPENDENT.md): debug-state readings never gate recovery or verification.

# 1.2.2-data-test3: Android 17 developer-state redaction

## Confirmed cause

The previous test2 incorrectly assumed a fresh SettingsProvider query returned an actual setting. The user's test2 snapshot shows that both app processes received developer=0/USB=0 even while the developer master was enabled. This is not explained by the old 60-second status-file lag.

Android 17 AOSP explicitly marks `Settings.Global.ADB_ENABLED` and `DEVELOPMENT_SETTINGS_ENABLED` with `@Readable(redactedValue = "0")` and documents that third-party apps always receive zero. `Settings.NameValueCache` substitutes these values in the client, and `SettingsProvider.getEffectiveValue` also substitutes them for application UIDs in query/call paths. Bypassing the client cache does not bypass the provider. Permission delegation does not change the Binder caller UID.

Sources (android17-release, inspected 2026-11-24):

- https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android17-release/core/java/android/provider/Settings.java#14759
- https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android17-release/core/java/android/provider/Settings.java#15045
- https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android17-release/packages/SettingsProvider/src/com/android/providers/settings/SettingsProvider.java#2459
- https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android17-release/core/java/android/app/UiAutomationConnection.java#534

## Change

The existing authorized retained UiAutomation session executes a fixed, read-only shell query for `id -u` and the three global keys. Shell/root UIDs are exempt from this provider redaction. The app does not use an app-UID provider value, enable debugging, start a new ADB connection, grant another permission, or install a helper. Configuration writes continue through the existing guarded app-UID Binder path.

The query output must have the expected framing, an actual shell/root reader UID, and individual 0/1 values. Missing/invalid values, query failure, an unavailable session, or a query straddling a reported settings change are unknown. Snapshot fields include `source=retained_ui_automation_shell`, `reader_uid`, start timestamps and a bounded failure reason. Old `settings_provider_uncached` snapshots are untrusted even with fresh timestamps and zeros.

One worker bounds pipe reads and output size. Duplicate refresh requests share one in-flight job; no unbounded task queue or retry loop is created. Visible UI requests are coalesced to approximately every two seconds. A separate private atomic status file is tied to the current guardian ID and boot; reads do not trigger radio observations or carrier writes. Background operation keeps the existing settings observer and guardian fallback interval. Immediately before recovery writes, the keeper requests a fresh shell sample and still requires all three confirmed off, all SIM calls idle, the same SIM, and valid permission. A write needs a sample at most three seconds old; UI samples expire after six seconds. No authorized reader means Unknown rather than a fabricated Off.

The shell owner is the same retained session already used by the guardian. If that owner is lost, this code cannot manufacture new authority. Master-off operation therefore still needs target-device verification. No claim of a completed device fix is made from host tests alone.

## Device check

1. Cover-install test3 and reauthorize/start the guardian. With the developer master still on, confirm Developer=On even if USB and wireless toggles are off. A copied snapshot should show source `retained_ui_automation_shell` and `reader_uid=2000` on stock Pixel.
2. Turn the master off. Return to Pixel IMS, wait several seconds, confirm all three Off, and copy the snapshot. Reopening/enabling the master must show Developer=On again. Do not use only the wireless toggle as a proxy for the master.
3. Leave the master off and check that the previously working full-config recovery still works after leaving Phone information V2. Never turn debugging back on merely to make the read succeed. Unknown plus its reason is the useful failure result if the retained shell reader does not survive.

The data-centric experiment is unchanged and unproven as a speed improvement. Turn it off and wait for original preference recovery before removing app data or returning to stable. Carrier settings, VoNR, band selection and radio history capture are unchanged.
