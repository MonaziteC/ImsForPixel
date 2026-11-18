# Build and signing

## Toolchain

- JDK 21 (Java/Kotlin Android bytecode target stays 17)
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

Local UI semantics tests run under Robolectric; pairing-crypto tests use the JVM's real JCA providers. They do not prove radio behavior, actual wireless pairing, deep-sleep timing or indefinite session survival on a physical phone. CI builds the optimized release APK, runs tests and release Lint, checks required entry names and the size budget, and saves the R8 mapping. CI uses an ephemeral development key; downloaded CI artifacts are not official update packages.

Kadb 2.1.1's certificate classes use Java class-file version 65, so exercising them in JVM tests requires JDK 21. Android builds transform the dependency into DEX; this does not increase minSdk or change the phone's runtime requirement.

## Release shrinking

Release builds enable R8 code shrinking/optimization and resource shrinking. Keep rules in `app/proguard-rules.pro` protect the names used by detached instrumentation commands, ART hidden-API helpers and Bouncy Castle's dynamically registered JCA providers. Do not replace these with blanket obfuscation rules or remove crypto providers solely because there is no direct source reference. The narrowly scoped JNDI warning suppression covers BC's unused desktop LDAP/CRL paths; Android does not provide that API.

Check the produced APK with:

```sh
python3 scripts/check-release-apk.py app/build/outputs/apk/release/app-release.apk --max-bytes 24226126
```

The budget is the previous 1.1.7 APK size. This static check reads actual DEX class/method tables; it cannot prove pairing or privileged operations on a phone. Retain `app/build/outputs/mapping/release/` for each distributed APK to decode obfuscated crash traces. Debug APKs intentionally remain unshrunk for development and will be much larger.

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

The v1.2.0 implementation changes are in commit `ddbdc524d5d8bbbf9ee4352628b583e24e057d53`; its subsequent publication commits updated documentation and CI. The size cleanup is in `7efc7835959203b2aea85b45932e50448a33318b`. Version 1.2.1 promotes that implementation with updated version metadata and JVM-test isolation. Release tags identify the complete corresponding source, and APKs embed their build-source revision in `META-INF/version-control-info.textproto`.

## Uploading an already tested APK

The manually triggered `Upload verified release assets` workflow can attach a prepared APK to an existing draft release. Supply an HTTPS URL and the exact SHA-256. It checks the hash, packages the current source and Git history, uploads using the repository-scoped Actions token, and downloads the assets again to verify their checksums. It never executes the downloaded APK and does not publish the draft automatically. No signing key is required or uploaded.
