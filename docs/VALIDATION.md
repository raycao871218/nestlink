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

## 0.6.0 NestLink redesign and localization

- Assemble and Android lint passed for the debug build. Core, QR decoder and English/Chinese translation checks passed.
- Installed on the physical phone without clearing settings. English was the default on a Chinese-language Android system.
- Switched to Chinese and back to English during an active VPN session. Both NAS and internet checks returned ready, and the Moon summary reported a reachable helper. No disconnect was triggered by language changes.
- Captured both localized home screens after connectivity verification; public screenshots omit phone status bars and connection identifiers.

## 0.7.0 yellow-chicken UI — 2026-10-23

- Implemented the approved warm hand-drawn direction: chicken launcher/header artwork, larger brand/title, licensed ZCOOL KuaiLe text, inked surfaces and utility glyphs, yellow power action, circular connectivity indicators and compact settings.
- Added collapsible setup/troubleshooting guidance, controller authorization instructions and device-ID copying. Authorization or degraded connectivity reveals the relevant guidance.
- Android versionCode 10, versionName 0.7.0. All native ABIs build; assembleDebug and lintDebug passed, as did pure-Java core, QR decoder and translation checks.
- Covered the existing PHP110 installation using adb install -r and confirmed version 0.7.0. Existing network and Moon configuration remained present; no data clearing or automatic VPN start was performed.
- Inspected the enlarged 72dp chicken header, 34sp brand, 42sp heading, centered power action and compact handwritten settings on PHP110. No clipping was observed at its current font size; the guide remains collapsed until opened or attention is needed.
- This UI release does not claim a new VPN connectivity, battery, reboot or large-font endurance test.

## 0.7.1 approved-reference correction — 2026-10-23

- Replaced approximate home artwork with mechanically extracted chicken, Chinese lettering, symbols and cleaned stretchable borders from `design/approved-home-reference.png`. Updated launcher icons to the same chicken.
- Android versionCode 11, versionName 0.7.1. assembleDebug and lintDebug passed after final icon generation. Translation parity passed. Networking code is unchanged.
- Native controls retain real state, accessibility text and large-font/English fallbacks. No success state is manufactured to match the connected reference.
- Physical-device installation and screenshot verification are pending: adb and Windows USB enumeration currently report no phone despite reconnect attempts. This build has not yet been visually verified on the phone.

Later device check: installed 0.7.1 on PHP110 and confirmed the reference chicken in the home screen. Network checks were degraded at capture time.

## 0.7.2 readability and state feedback — 2026-10-23

- English uses a regular sans-serif typeface; verified success is plain `Connected`, without the extracted check badge. Ready path rows no longer show the check artwork.
- Native indeterminate progress appears while joining, checking, reconnecting or retrying, and hides when inactive, authorized-waiting or verified.
- Language selection uses a custom brush-border dialog with ivory paper, yellow selection and explicit close. Draft-preserving language switching behavior remains in place.
- Android assembleDebug, lintDebug and translation checks passed. Installed versionCode 12 / 0.7.2 on PHP110; screenshots verified English readability and the custom language dialog. The phone was disconnected during inspection, so the retry animation and successful connection state were not exercised live.
- iOS changes remain uncompiled on Windows and require Mac/iPad validation.

## 0.7.3 connected badge correction — 2026-10-23

- Restored the approved pale-green brush border for verified success, removing only the check artwork. Native label is localized: Chinese `已连接`, English `Connected`.
- Android assembleDebug, lintDebug and translation parity passed. Installed versionCode 13 / 0.7.3 on PHP110; successful VPN state was not forced for visual testing. iOS remains pending Mac verification.

## 0.7.4 home cleanup and network identity — 2026-10-23

- Enlarged launcher chicken by reducing adaptive foreground inset and increasing artwork occupancy. Removed the placeholder current-network card and home guide; flexible spacing distributes central controls and the lower status panel.
- The first status row displays the actual ZeroTier config name. Non-empty names are cached by normalized network ID, retained across disconnect/restart, and displayed below the matching network ID in settings. Switching to an unknown ID shows a pending-name hint instead of another network's cached name.
- Both status rows and settings network-name hints use Resource Han Rounded CN Regular in Chinese; English uses regular sans-serif. Hero lettering remains the approved artwork.
- Scan opens during an active tunnel. A decoded QR requires explicit confirmation to disconnect before filling draft settings; saving and connecting remain explicit. Existing saved settings are not silently replaced.
- Final Android assembleDebug/lintDebug and translation checks passed. Core and QR decoder tests passed during this change; iOS literal translation coverage passed.
- Installed versionCode 14 / 0.7.4 on PHP110. Real ZeroTier name and both successful health checks were observed. Later reinstall/relaunch showed the retained name while disconnected; settings UI hierarchy showed the same cached name. Inspected the redistributed home layout. Verified the scan entry enabled and opening ScanActivity while a tunnel was active. The final rounded font build installed successfully, but its final screenshot was unavailable after the display went dark. The explicit disconnect-and-fill QR branch was not exercised on the phone; the QR decoder was tested separately.
- iOS/PacketTunnel updates require Mac compilation and iPad verification. No iOS build is claimed here.

## 0.7.5 inline settings network name — 2026-10-23

- Appended the cached network name to the network-ID field heading using one native label and identical typography; removed the separate name/hint row. Unknown IDs show the ordinary heading. Draft-ID changes still select the corresponding cached name.
- Android assembleDebug, lintDebug and translation parity passed. Installed 0.7.5 / versionCode 15 on PHP110. UI hierarchy and screenshot confirmed the combined heading on one line with the name, matching font/color, and no separate name row.
- iOS Build 10 mirrors the layout; Mac compilation and iPad validation remain pending.
