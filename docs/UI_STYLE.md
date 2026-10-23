# Approved home artwork (0.7.4)

Source of truth: `design/approved-home-reference.png`, supplied by the user. The earlier concept and approximate 0.7.0 implementation are historical references.

- Hand-drawn dark cocoa outlines (#4C2718), warm ivory (#FFFBED), custard-yellow primary actions (#FFD34C), pale sage success states (#DCE9BC).
- Preserve the exact short, round chicken, brush lettering, underline, symbols and rough borders from the approved reference.
- Chinese hero labels use extracted artwork inside native controls. English, arbitrary values and larger accessibility text retain native text layout.
- Both status rows use the same rounded font, size and color for network names, internet labels and state values. Do not mix screenshot lettering with dynamic network-name fonts in this panel.
- Center the power symbol and action text together. Verified success retains the green brush border and localized text, without a check illustration.
- Keep the header at the top, central connection controls together and status panel lower down using flexible spacing. Home has no placeholder network card or connection guide; guidance remains in settings.
- Network names come from ZeroTier configuration and are cached by network ID for disconnected/reconnecting display. Settings append the cached name to the network-ID field heading, in the same font, size and color; there is no separate name row.
- Settings use a compact language row, paired QR actions, labeled fields, Moon, developer mode and a collapsed connection guide.
- Only show verified success after real health checks. Authorization and degraded connectivity retain state feedback; troubleshooting is available in settings.
- Preserve native text/input accessibility and larger-font scrolling. Do not flatten the screens into images.

Run `python tools/extract_approved_artwork.py` and then `python tools/generate_app_icons.py` (Pillow required). Coordinates are recorded in `design/approved-artwork-regions.json`. Cleaned border textures contain no screenshot labels. The source resolution limits enlarged artwork sharpness. Provenance is recorded in `branding/PROMPT.md`.

Android build, lint, core/QR tests and translation checks are recorded in VALIDATION.md. iOS changes require Mac compilation and device verification.

Status font: unmodified Resource Han Rounded CN Regular, v0.990, from https://github.com/CyanoHao/Resource-Han-Rounded/releases/tag/v0.990. The original SIL OFL license is packaged on both clients. English remains regular sans-serif. Earlier experimental name fonts are not packaged.
