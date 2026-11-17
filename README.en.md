# Pixel IMS

[简体中文](README.md) · [Download v1.2.0](https://github.com/MonaziteC/ImsForPixel/releases/tag/v1.2.0)

**Customize IMS and NR carrier settings on Pixel, with a guardian that can keep running after developer options are turned off.**

Pixel IMS is an independent fork of [svenuks/ImsForPixel](https://github.com/svenuks/ImsForPixel). Initial authorization uses the phone's local wireless ADB connection; root, a computer and Shizuku are not required.

## Features

- VoLTE, VoNR, Wi-Fi calling, roaming and supplementary-service settings.
- Optional NSA / SA NR availability and NR Advanced / 5G+ display policy.
- A guardian for the complete app-owned configuration, with separate initial active SIM targets.
- A dedicated keeper process, carrier-config change monitoring, verified read-back and rate-limited retries.
- Undo and restore carrier defaults from the status page.
- Official Material 3 components, system light/dark mode and Android 12+ dynamic colors.
- Short-lived connection/pairing UI. New paired-device entries are named **Pixel IMS**.

NR and icon settings do not force physical carrier aggregation or a 5G-Advanced connection. Modem, subscription, carrier and network support still determine actual behavior.

## Getting started

1. Download an APK from [Releases](https://github.com/MonaziteC/ImsForPixel/releases).
2. Choose the desired settings for each SIM.
3. Tap **Apply and protect**. Temporarily connect to Wi-Fi, enable wireless debugging and pair.
4. Apply the settings and wait until the guardian reports that authorization is ready. Then turn developer options off.

The guardian does not need an ongoing ADB connection. Pause it before editing settings. **Reboot, force-stop, keeper death or loss of the authorization session may require authorization again.** Saved preferences are not permanent system overrides.

Restore stops protection and clears the temporary carrier-override layer for the affected SIMs. This can also clear another tool's temporary overrides on those SIMs; system-only persistent overrides are not touched. Without a live authorized session, restore requires wireless authorization again.

The foreground service remains active even when its drawer notification is hidden. Initial authorization changes only this app's notification permission; Android's active-apps UI may still show the service.

## Build

JDK 17 and Android SDK Platform 36:

```sh
./gradlew assembleRelease testDebugUnitTest lintRelease
```

See [build and signing instructions](docs/BUILDING.md), [architecture](docs/ARCHITECTURE.md), [validation](docs/VALIDATION.md) and [fork guide](FORK_GUIDE.md). The minimum installation API is 29; this is not a claim of device testing across every supported API. The current user has reported working behavior on Pixel / Android 17.

## Privacy and attribution

Network use is local-loopback ADB and LAN mDNS discovery. There are no analytics SDKs, telemetry or automatic log uploads. Pairing keys remain in private app storage. Release source archives do not include keys or device logs.

Thanks to [svenuks/ImsForPixel](https://github.com/svenuks/ImsForPixel) for the base application and [ryfineZ/carrier-ims-for-pixel](https://github.com/ryfineZ/carrier-ims-for-pixel) for the NR parameter adaptation. See [LICENSE](LICENSE) and [third-party notices](THIRD_PARTY_NOTICES.md). This fork is independent of the upstream maintainers and Google.
