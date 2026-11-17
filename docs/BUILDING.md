# Build and signing

## Toolchain

- JDK 17
- Android SDK Platform 36
- Gradle wrapper 8.13 / Android Gradle plugin 8.13.0
- Kotlin and Compose compiler plugin 2.2.20
- Material 3 1.4.0, using Compose BOM 2026.03.00
- minSdk 29 / targetSdk 36

The chosen BOM is compatible with the retained toolchain. Newer Compose foundation/runtime releases require AGP 9.1 and SDK 37; changing the BOM alone is not a complete upgrade. No preview Material dependency is used.

Configure your SDK through Android Studio / local.properties or ANDROID_HOME, then run:

```sh
./gradlew assembleRelease testDebugUnitTest lintRelease
```

Output: `app/build/outputs/apk/release/app-release.apk`.

Local UI semantics tests run under Robolectric. They do not prove radio behavior, deep-sleep timing or indefinite session survival on a physical phone. CI builds a debug APK and runs tests and Lint; downloaded CI artifacts are not automatically signed release assets.

## Signing

By default, a release build uses the local development signing identity. For a separately managed release key set all four environment variables:

```text
PIXEL_IMS_KEYSTORE_FILE
PIXEL_IMS_KEYSTORE_PASSWORD
PIXEL_IMS_KEY_ALIAS
PIXEL_IMS_KEY_PASSWORD
```

Do not commit the keystore, passwords, local.properties or device keys. Source exports deliberately omit them.

The published v1.2.0 APK retains the development signature used by the preceding test builds, allowing in-place updates. Its certificate SHA-256 is recorded in docs/VALIDATION.md. The private key is not included with source. A build signed by another identity cannot update that installation in place.

## Publishing a release

1. Run build, tests and Lint. Inspect the APK signature and version.
2. Create a version tag on the intended source commit.
3. Attach the verified APK, source archive and SHA256SUMS to a GitHub Release.
4. Explain any development-signature or test-scope limitations in the notes. Do not upload private keys or phone diagnostics.

## Source provenance

The original implementation changes are in commit `ddbdc524d5d8bbbf9ee4352628b583e24e057d53`. A later publication commit updates documentation only. The v1.2.0 APK was built from the same application code; publication documents do not change the binary.
