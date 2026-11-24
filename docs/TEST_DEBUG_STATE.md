> Historical build. Superseded by [test4](TEST_GUARDIAN_INDEPENDENT.md): debug-state readings never gate recovery or verification.

# 1.2.2-data-test2

Follow-up to the data-centric experiment. The user reported that developer options could still be shown as off while enabled, no reproducible speed benefit from the data-centric switch, and a slow sample with a reported bandwidth list of only 60000 kHz instead of 60000/100000/30000 kHz. These observations do not establish a VoNR or CA root cause.

## Debug-state correction

Previously the UI polled a keeper status file once per second, while the keeper could read actual settings only every 60 seconds. The file's old `debug_off` boolean was used as a live UI fact.

The UI now separately queries the named SettingsProvider URIs for developer options, USB debugging and wireless debugging while visible. It bypasses Settings.NameValueCache; if a named query is unavailable it tries a direct SettingsProvider GET_global call, also without client caching or a default-zero value. Missing, denied or invalid values are Unknown; a sample more than three seconds old cannot claim closed settings. Old keeper booleans are not used as a fallback. The keeper uses the same fresh reader before permitting a recovery write and subscribes to changes on all three setting URIs to wake promptly.

Copied snapshots include distinct UI and keeper flag values, timestamps and the read source. This distinguishes a stale keeper file from an actual provider reading. Hardware behavior still requires target-device verification.

## Capturing the intermittent slowdown

1. Apply/start the guardian as before. The data-centric experiment remains optional and off by default for a new session; its undo receipt and recovery behavior are unchanged.
2. Keep the Status page visible for approximately five-second radio observations, or tap **Capture for 2 minutes** before switching to a speed-test application. A manually requested capture expires automatically. After the visible-page lease or short capture expires, background sampling returns to the existing guardian interval.
3. At a slow sample, tap **Copy snapshot and recent changes**. The copy includes the latest sample and up to 64 changed sampled states for that SIM. Nothing is automatically uploaded.

Reported bandwidths are displayed in MHz with their sum. The last change in the normalized bandwidth list is also shown. Reordering the same list is not counted as a carrier change, and unknown values are not rendered as zero capacity. History includes data RAT, call state, exposed IMS registration technology, VoNR permission, NR RSRP/SINR, subscription usage preference and bandwidths.

The bandwidth source is still `ServiceState.getCellBandwidths`, whose values may be cached or ratcheted. This APK does not obtain an over-the-air trace, prove that every reported carrier is actively transporting data, identify specific NR CA combinations, or infer speed directly from bandwidth sum. Sampling can miss short transitions.

When permissions and cached cell information permit it, a session-salted hash allows comparison of the serving cell within one keeper session. Raw NCI/CI, channel numbers, the salt, phone numbers, ICCIDs and coordinates are not stored in snapshots. Tokens cannot be compared across keeper sessions. Missing cell information stays unknown; no cell scan is requested.

The test retains the stable signing identity and versionCode 12 for reversible side-loaded updates. Recover the data-centric original preference before uninstalling, clearing app data or returning to the stable APK, as described in TEST_DATA_CENTRIC.md.
