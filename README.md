**English** | [简体中文](README.zh-CN.md)

# NestLink · 窝窝连

Access your home network and the internet through your NAS running mihomo. NestLink embeds ZeroTier and uses one VPN, with no separate ZeroTier client required.

- **Android 8.0+**: one-tap connection, QR setup, Moon helpers and connectivity checks.
- **iPad prototype**: SwiftUI + Packet Tunnel; connectivity and some device interactions still await validation. See the [iPad guide](ios/README.zh-CN.md) and [validation record](ios/VALIDATION.md).
- English and Simplified Chinese, with optional developer diagnostics.

<img src="docs/nestlink-en.png" alt="NestLink connection screen" width="320" />

## Quick start (Android)

1. Join your NAS to ZeroTier and configure its mihomo gateway. See the [NAS guide](nas/README.md).
2. Scan a setup QR code or enter the network ID, NAS ZeroTier IPv4 address and check port. Moon helpers are optional.
3. Connect, allow the VPN and authorize the new device in your ZeroTier controller.

Connected status requires both NAS TCP and HTTPS checks to pass. These checks confirm only the configured service and test site at that moment.

```text
Device → NestLink VPN → ZeroTier → NAS → Home LAN / Internet
```

## Build Android

Requires JDK 17, Gradle 8.8, Android SDK / Build Tools 34, NDK 27.2.12479018 and CMake 3.22.1.
Set `ANDROID_HOME` or configure `sdk.dir` in `local.properties`.

```sh
./gradlew :app:assembleDebug :app:lintDebug
python3 tools/test_core.py
python3 tools/test_qr.py
python3 tools/test_i18n.py
```

APK: `app/build/outputs/apk/debug/app-debug.apk`. On Windows, use a local or mapped drive for native builds.
For iPad builds and signing, see the [iPad guide](ios/README.zh-CN.md).

## Current limits

- One network and a same-subnet IPv4 NAS gateway. IPv6 is blocked inside the VPN; IPv6-only physical networks are not supported.
- Android has no always-on VPN, boot connection or system-level lockdown. Disconnecting restores the original network.
- Moon helpers assist discovery; they do not replace controller authorization or the NAS gateway.
- Upgrades retain settings and device identity. Uninstalling or clearing data requires authorization again.

## License

Application code is **GPL-3.0-or-later**. Third-party components retain their licenses; the upstream nonfree controller is not built. Not affiliated with ZeroTier Inc.

[Android validation](docs/VALIDATION.md) · [Third-party notices](THIRD_PARTY.md) · [Icon provenance](branding/PROMPT.md)
