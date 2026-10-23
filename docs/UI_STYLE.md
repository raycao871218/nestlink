# Yellow-chicken UI (0.7.0)

Reference: `design/yellow-chicken-ui-concept.png`. This is the approved visual direction, not a runtime screenshot.

- Hand-drawn dark cocoa outlines (#4C2718), warm ivory (#FFFBED), custard-yellow primary actions (#FFD34C), pale sage success states (#DCE9BC).
- ZCOOL KuaiLe for handwritten UI labels. Keep network IDs and diagnostics readable with system/monospace text.
- The chicken is a prominent header mascot: 72×82dp on Android, with 34sp brand lettering; do not shrink it to a small toolbar icon. iPad uses a larger adaptive header.
- Center the power symbol and action text together. Status rows use circular indicators, not extra outlined pill boxes.
- Settings use a compact language row, paired QR actions, labeled fields, Moon, developer mode and a collapsed connection guide.
- Only show verified success after real health checks. Authorization and degraded connectivity reveal actionable guidance.
- Preserve native text/input accessibility and larger-font scrolling. Do not flatten the screens into images.

Assets: `branding/nestlink-icon.png` is the approved opaque chicken source. Run `python tools/generate_app_icons.py` (Pillow required) to package both clients. Generation provenance is in `branding/icon-yellow-chicken-candidate.md`; the UI prompt is in `design/yellow-chicken-ui-concept.md`.

Android build, lint, core/QR tests and translation checks are recorded in VALIDATION.md. iOS changes require Mac compilation and device verification.
