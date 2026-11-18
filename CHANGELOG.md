# Changelog

## 1.2.1-rc1 (size optimization test build)

- Enable R8 and resource shrinking for release APKs; preserve shell entry names, hidden-API helpers and dynamically loaded crypto providers.
- Remove the unused legacy ADB library, UI preview dependency, standalone IMS query tool, obsolete query-only mode, duplicate status notification and unused callback.
- Reuse the shared IMS registration reader for activation polling; remove 62 unused translation keys per locale.
- Add pairing-identity and SPAKE2 smoke tests; check the optimized APK's entry names and size in CI, and retain the R8 mapping.
- Keep the Material 3 interface and existing guardian/configuration features. This RC still needs physical-device pairing, activation, developer-options-off recovery and restore regression checks before replacing 1.2.0.

## 1.2.0

- Rename to Pixel IMS; official Material 3 1.4.0, dynamic system color, light/dark mode.
- Separate status and SIM configuration, move connection/pairing into an on-demand sheet.
- Add undo/restore on the status page, including a retained-session route with developer options off.
- Fix normal guardian state appearing as endless activation loading.
- Read-only IMS state distinguishes unknown from unregistered.
- Fix paired ADB device name to Pixel IMS instead of an internal files path.
- Remove Keep SA option and its configuration key; ignore old saved values.
- Add source provenance, fork/build documentation, CI and optional external release signing.

## 1.1.7 (experimental development build)

- Guard the complete app-customized IMS/NR bundle for both initial active SIMs.
- Start protection by default after ordinary activation.
- Keep required foreground-service plumbing while suppressing drawer notifications using this app's own notification permission.

## 1.1.6 (experimental development build)

- Move retained instrumentation and foreground service into a dedicated keeper process.
- Replace the two-hour / lifetime-six-write cap with rolling rate limiting and retries.

Earlier investigative builds and device logs motivated this implementation. See README and architecture notes for current verification scope; historical claims of permanence are not carried forward.
