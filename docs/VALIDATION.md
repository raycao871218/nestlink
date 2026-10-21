# Validation

The 0.4.0 UI and service changes require these acceptance checks:

- A clean install requires user-supplied network settings; an upgrade retains settings.
- User mode contains no diagnostic logs; developer mode is explicit and reversible.
- A tunnel address alone cannot produce “已连接”. NAS TCP and VPN-bound, certificate-verified HTTPS 204 must both succeed.
- Switching physical network or closing/recreating the tunnel invalidates in-flight results.
- Failed verification produces a recovery state and is retried by the foreground service, independently of the activity lifecycle.

Automated pure-Java tests cover stale success, stale failure, partial connectivity, recovery and invalidation, plus existing packet and configuration logic. Build/lint and device results are recorded after execution below.

## 0.4.0 results — 2026-10-21

- Built all three native ABIs from this trimmed public source tree; Android assembleDebug and lintDebug passed (0 lint errors).
- All four pure-Java suites passed, including health-result invalidation.
- Upgraded the existing physical Android device without clearing settings or ZeroTier identity.
- On mobile data, connecting automatically reached the NAS TCP endpoint and performed VPN-bound HTTPS with certificate verification / HTTP 204. The UI then showed 已连接 and both paths verified, without a manual test action.
- An unavailable NAS test port produced 连接待恢复, false health flags and automatic retry; it did not produce a connected state. Restored the original port and reverified success afterward.
- Enabled developer mode and observed diagnostic tools / peer paths. Disabled it and confirmed all developer controls and logs disappeared. The final installed app was left in user mode.
- APK signature verification passed. NAS configuration was not changed by this UI update.

No reboot endurance, battery soak, or universal site/protocol coverage is claimed. CI is provided for reproducible build/lint/core tests; hosted CI status is separate from these local results.

## 0.5.0 QR onboarding and Moon presentation

- Added offline QR scanning from the rear camera and the system image picker, with a shared ZXing decoder. Camera permission is requested only when entering scanning; photo import remains available after denial.
- Payload tests cover plain IDs, supported ZeroTier links and HarborLink full configuration roundtrips, plus invalid IDs, duplicate fields, arbitrary URLs, invalid gateways/ports/Moons and oversized input.
- Decoder tests cover camera luminance and image pixels, 0/90/180/270 degree rotations, inverted codes, absent and truncated input.
- On the physical Android device, the rear camera opened successfully. Picking a generated test QR from Downloads produced the expected configuration confirmation. Confirming filled draft fields; saved settings were unchanged until explicitly saved. Dismissing those drafts preserved the original configuration. Network QR display also worked.
- Moon settings now use a compact count/management entry and one short ID per node; full World/Seed details are restricted to developer mode. Existing saved node definitions and orbit behavior are preserved.
- Live camera-to-physical-QR decoding was not independently exercised; the shared camera-frame decoder and actual camera lifecycle were tested separately.

- Final 0.5.0 build/lint and APK signature verification passed. On-device Moon settings showed one compact summary; its manager displayed one short ID without World/Seed duplication. Adding the existing ID kept one entry. Original saved connection settings were retained.

## 0.5.1 Moon connectivity

- Added per-configured-Seed path status and measured latency outside developer mode. Discovery and cached latency alone never count as live connectivity.
- Freshness requires a receive timestamp from the current physical-network observation period and within 120 seconds; unknown/zero latency is not displayed as 0 ms.
- Service snapshots refresh every five seconds and the UI expires them after fifteen seconds. Network changes, service startup/loss/shutdown invalidate snapshots.
- Pure-Java tests cover discovery, non-Moon peers, absent/stale replies, valid replies, physical-network invalidation and clock boundaries.

- Build/lint, core tests and APK signature verification passed. Installed 0.5.1 on the physical phone without clearing settings. In user mode, the configured Moon showed a live path and a measured 15 ms latency; the summary showed one reachable node. VPN NAS/Internet health also returned connected.
