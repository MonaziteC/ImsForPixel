# APK size cleanup: 1.2.1-rc1

These measurements describe the optimized RC. The user subsequently reported normal functionality on the target phone and approved publication as 1.2.1. The final version changes version metadata and JVM test isolation; it retains the tested phone-side implementation.

## Measured APKs

All sizes below are bytes, measured from signed universal APKs. No architecture or language was removed to produce this reduction.

| Item | 1.2.0 | 1.2.1-rc1 |
| --- | ---: | ---: |
| Downloaded APK | 30,194,378 | 6,754,364 |
| Uncompressed DEX | 28,205,296 | 5,127,960 |
| Packaged DEX classes | 21,655 | 6,122 |
| Uncompressed Android resources (`res/` + `resources.arsc`) | 609,591 | 251,923 |
| Uncompressed native libraries | 37,392 | 37,392 |

APK reduction: **77.63%**, or 23,440,014 bytes. The optimized APK is also smaller than the 24,226,126-byte pre-redesign 1.1.7 APK. Most of the saving comes from shrinking and optimizing unused program code, rather than removing images or changing the UI framework. APK overhead includes alignment, signing and packaged Java library resources, so the rows do not add up to the download size.

## Changes

- Enable release R8 optimization/obfuscation, resource shrinking and AGP's optimized resource shrinking.
- Remove unused `com.tananaev:adblib`, Compose preview and redundant direct graphics/lifecycle dependencies. Used transitive libraries remain available.
- Delete the unused standalone `app_process` IMS query helper, obsolete query-only broker route, unused callback and duplicate status notification implementation.
- Reuse the existing shared IMS registration reader in activation polling.
- Remove 62 unused string keys from each of the English, Chinese and Russian resource sets. Current UI translations remain.
- Remove stale Shizuku keep rules. Keep shell entry names, small ART hidden-API helpers, and BC JCA providers that are loaded through dynamically assembled names. Preserve provider names and members conservatively rather than cutting algorithms without device evidence.
- Strip verbose/debug log calls in release builds; retain warnings and errors.

The Material 3 UI, system colors, pairing name, per-SIM configuration and retained-session guardian remain. The manifest differs from 1.2.0 only in version name/code. The removed Keep SA key is absent from DEX.

## Validation and limits

The release build, 37 JVM/Compose tests and release Lint passed. APK signature and 16 KB page alignment checks passed; the signing certificate matches 1.2.0. All 11 named-entry checks passed.

Run the commands in [BUILDING.md](BUILDING.md). The APK checker verifies actual DEX class/method tables for app components, instrumentation entry names, HiddenApiBypass and representative dynamically loaded crypto providers, and enforces the previous 1.1.7 APK size as a budget. CI retains the R8 mapping files.

Pairing-crypto smoke tests exercise persistent RSA key reuse, self-signed certificate verification with host and BC providers, and SPAKE2 agreement/rejection. The host RSA provider is explicitly selected and its original position restored so Robolectric's global security-provider ordering does not change this JVM-only test. These tests and static APK checks do **not** execute the optimized APK on a phone. The user's device confirmation is separate evidence; it is not a claim that every pairing, call, recovery and restore path has been exhaustively tested on every firmware.

The test APK uses the same development signing certificate as 1.2.0, so it can update that installation in place. Do not uninstall to update: uninstalling removes the saved pairing identity and preferences. A running permission session may need to be authorized again when updating/restarting processes.
