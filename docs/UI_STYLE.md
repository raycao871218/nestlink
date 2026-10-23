# Approved home artwork (0.7.1)

Source of truth: `design/approved-home-reference.png`, supplied by the user. The earlier concept and approximate 0.7.0 implementation are historical references.

- Hand-drawn dark cocoa outlines (#4C2718), warm ivory (#FFFBED), custard-yellow primary actions (#FFD34C), pale sage success states (#DCE9BC).
- Preserve the exact short, round chicken, brush lettering, underline, symbols and rough borders from the approved reference.
- Chinese static labels use extracted artwork inside native controls. English, arbitrary values and larger accessibility text retain native text layout.
- Center the power symbol and action text together. Status rows use circular indicators, not extra outlined pill boxes.
- Settings use a compact language row, paired QR actions, labeled fields, Moon, developer mode and a collapsed connection guide.
- Only show verified success after real health checks. Authorization and degraded connectivity reveal actionable guidance.
- Preserve native text/input accessibility and larger-font scrolling. Do not flatten the screens into images.

Run `python tools/extract_approved_artwork.py` and then `python tools/generate_app_icons.py` (Pillow required). Coordinates are recorded in `design/approved-artwork-regions.json`. Cleaned border textures contain no screenshot labels. The source resolution limits enlarged artwork sharpness. Provenance is recorded in `branding/PROMPT.md`.

Android build, lint, core/QR tests and translation checks are recorded in VALIDATION.md. iOS changes require Mac compilation and device verification.
