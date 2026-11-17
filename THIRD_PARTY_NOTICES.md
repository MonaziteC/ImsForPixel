# Attribution and licensing

- Main upstream: **svenuks/ImsForPixel**, https://github.com/svenuks/ImsForPixel, base commit `9599cd779aed9e0b6649505435290922417379d4`. Its README declares MIT; the base snapshot did not include a separate LICENSE file. The original README is preserved in docs/UPSTREAM_README.md.
- NR / NR Advanced parameter adaptation: **ryfineZ/carrier-ims-for-pixel**, https://github.com/ryfineZ/carrier-ims-for-pixel, inspected commit `4312b5a9244a20444cccf5c4a5f763f6fd9d2ab5`. Apache License 2.0. The complete license and adaptation notice remain in app/src/main/assets/LICENSE-carrier-ims.txt and NOTICE-nr-port.txt.
- New Pixel IMS UI, shared configuration, session guardian, tests and documentation are distributed under MIT. Copyright and license notices in individual adapted files take precedence for those portions.
- AndroidX Compose / Material 3 and the Android SDK are third-party dependencies, retrieved by Gradle; their authors retain their licenses. Source archives do not bundle dependency caches.
- Kadb (com.flyfishxu:kadb) provides the local ADB transport and pairing implementation. Its artifacts are fetched by Gradle and are not copied into the source archive.

This fork is independent of the upstream maintainers and Google. Retained-session behavior and the Phone information V2 cleanup were investigated against public AOSP android17-release sources; no proprietary system source is included.
