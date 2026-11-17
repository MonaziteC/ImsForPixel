# Pixel IMS 1.2.0 delivery notes

See README.md, CHANGELOG.md and docs/ARCHITECTURE.md for the current implementation and test instructions.

This source includes all work from the original NR port, runtime-only writer, retained permission-session guardian and Material 3 redesign. Historical diagnostic/probe collectors and their exports are removed. The ineffective Keep SA configuration is removed from the UI and write path; legacy preference removal is migration-only.

Package remains com.svenuks.imsforpixel. versionCode 10, versionName 1.2.0. Same development signing identity as the preceding delivered APKs, so they can be updated in place. Fork sources never include the signing key or device ADB keys.

The user confirmed functional behavior for the prior guardian build. New UI, direct restore and activity reopening require the user's next device test. JVM/Compose tests and build checks do not establish indefinite survival on a particular Pixel firmware.
