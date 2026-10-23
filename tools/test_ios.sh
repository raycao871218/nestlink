#!/bin/sh
set -eu
task_root=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
task_tmp=$(mktemp -d)
trap 'rm -rf "$task_tmp"' EXIT HUP INT TERM
xcrun clang++ -std=c++17 -fsanitize=address,undefined -g \
  "$task_root/ios/Tests/IPv4BridgeTests.cpp" -o "$task_tmp/bridge-tests"
"$task_tmp/bridge-tests"
xcrun swiftc "$task_root/ios/Shared/ConnectionSettings.swift" \
  "$task_root/ios/Shared/HomeNetworkSettings.swift" \
  "$task_root/ios/App/OnDemandPolicy.swift" \
  "$task_root/ios/Shared/QRPayload.swift" \
  "$task_root/ios/App/QRImageCodec.swift" \
  "$task_root/ios/Tests/ConnectionSettingsTests.swift" -o "$task_tmp/settings-tests"
"$task_tmp/settings-tests"
