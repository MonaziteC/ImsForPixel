# Delivery validation

## 1.2.1

- Version 1.2.1, versionCode 12; phone-side implementation retained from the optimized RC, with version metadata updated.
- Release build succeeded with SDK 36. Compilation used JDK 17 and the JVM tests used JDK 21; CI uses JDK 21.
- 37 JVM / Compose tests passed; 0 failed, errored or skipped.
- lintRelease: 0 errors, 35 warnings.
- Optimized APK: 6,754,356 bytes; 6,122 DEX classes; all 11 named-entry checks passed.
- APK signature and 16 KB alignment checks passed. The certificate below is unchanged from 1.2.0.
- The user reported normal functionality for 1.2.1-rc1 on the target phone and approved publication. This is a user report, not an exhaustive device/firmware compatibility test.
- The release's SHA256SUMS records checksums for the final APK and attached source archives.

## 1.2.0

- Version 1.2.0, versionCode 10.
- Release build succeeded with JDK 17 / SDK 36.
- 34 JVM / Compose semantics tests passed; 0 failed, errored or skipped.
- lintRelease: 0 errors, 112 warnings (including unused legacy resources and expected hidden-API warnings).
- APK alignment and signature verified. Same development certificate as preceding builds.
- APK labels checked for Pixel IMS; removed SA-policy key is absent from packaged DEX.
- New UI and restore paths are not a claim of completed physical-device testing.

APK SHA-256: 414ea85ac5badebce570918e2ec40fe1694249673f103ded0e679380222bf19f

Certificate SHA-256: 12502e3f71c40d0d0c968f3d0cf780bcb89a39a9f7e114baad05087f95810cf9
