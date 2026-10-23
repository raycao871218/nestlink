# NestLink icon provenance

The current `nestlink-icon.png` is a mechanical crop and resize of the exact short, round yellow chicken in the user-supplied `design/approved-home-reference.png`. It is an opaque square image. The earlier generated candidate and its prompt remain in [icon-yellow-chicken-candidate.md](icon-yellow-chicken-candidate.md).

Android launcher densities, the adaptive foreground, header thumbnail and iOS AppIcon are mechanical size derivatives. Run `python tools/extract_approved_artwork.py` and then `python tools/generate_app_icons.py`. Android notifications use a separate monochrome chicken silhouette.

The warm hand-drawn UI concept and exact image-generation prompt are in [the design folder](../design/yellow-chicken-ui-concept.md). Runtime controls remain native code, not images of screens.
