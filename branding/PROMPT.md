# NestLink icon provenance

The current `nestlink-icon.png` is the approved yellow-feathered chicken mascot, generated using the built-in image_gen tool on 2026-10-22. It is an opaque square image. The exact prompt is retained in [icon-yellow-chicken-candidate.md](icon-yellow-chicken-candidate.md).

Android launcher densities, the adaptive foreground, header thumbnail and iOS AppIcon are mechanical size derivatives. Generate them with `python tools/generate_app_icons.py`. Android notifications use a separate monochrome chicken silhouette.

The warm hand-drawn UI concept and exact image-generation prompt are in [the design folder](../design/yellow-chicken-ui-concept.md). Runtime controls remain native code, not images of screens.
