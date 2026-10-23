#!/bin/sh
set -eu
root=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
: "${DERIVED_FILE_DIR:?Run this script from the PacketTunnel build phase}"
: "${SDKROOT:?Missing SDKROOT}"
arch=${CURRENT_ARCH:-arm64}
case "$arch" in undefined_arch) arch=arm64 ;; esac
case "${PLATFORM_NAME:-iphoneos}" in
 iphonesimulator) target="$arch-apple-ios17.0-simulator" ;;
 iphoneos) target="$arch-apple-ios17.0" ;;
 *) echo 'Unsupported home-proxy platform' >&2; exit 1 ;;
esac
work="$DERIVED_FILE_DIR/home-proxy-$target"
mkdir -p "$work"
rsync -a --exclude build --exclude bin "$root/vendor/hev-socks5-tunnel/" "$work/"
make -C "$work" -j4 CC="$(xcrun -f clang)" PP="$(xcrun -f clang) -E" \
 CFLAGS="-target $target -isysroot $SDKROOT -Os" REV_ID=nestlink-pinned static
xcrun libtool -static -o "$DERIVED_FILE_DIR/libNestLinkHome.a" \
 "$work/bin/libhev-socks5-tunnel.a" "$work/third-part/lwip/bin/liblwip.a" \
 "$work/third-part/yaml/bin/libyaml.a" "$work/third-part/hev-task-system/bin/libhev-task-system.a"
