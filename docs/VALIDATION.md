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
