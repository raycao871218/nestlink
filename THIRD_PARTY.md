# Third-party components

This application embeds the ZeroTier One core and Java JNI SDK from upstream tag
1.16.0: https://github.com/zerotier/ZeroTierOne/tree/1.16.0

Source and notices are retained in vendor/ZeroTierOne. Core files carry MPL-2.0
notices; the Java/JNI wrapper carries GPLv3 notices. NestLink original application
code is supplied under GPL-3.0-or-later in LICENSE. This source package accompanies
the debug APK.

The application CMakeLists builds node/*.cpp, osdep/OSUtils.cpp, and java/jni/*.cpp.
The iPad Xcode target builds node/*.cpp and osdep/OSUtils.cpp with a new
Objective-C++ adapter; it does not compile or link the Java/JNI wrapper.
It does not build the nonfree controller or desktop service. Referenced Prometheus
and OpenTelemetry headers retain their notices under vendor/ZeroTierOne/ext.

Upstream Java, JNI, and core sources are not edited. Integration CMakeLists is in
app/src/main/cpp, using NDK 27.2.12479018, CMake 3.22.1, C++17, static libc++,
optimization level O2, and 16 KB ELF segment alignment.

Gradle wrapper tooling retains its embedded Apache-2.0 notices.

The nlohmann/json header dependency is retained under its MIT license in vendor/ZeroTierOne/ext/nlohmann/LICENSE.MIT.

Android QR scanning and generation use ZXing core 3.5.3 (Apache-2.0), resolved from Maven Central as com.google.zxing:core:3.5.3. Upstream source and license: https://github.com/zxing/zxing/tree/zxing-3.5.3 and https://github.com/zxing/zxing/blob/zxing-3.5.3/LICENSE . No external scanner or Play Services component is used.

The iPad QR implementation uses Apple's system AVFoundation, PhotosUI, Vision,
ImageIO, and CoreImage frameworks. It does not bundle ZXing or upload images.

The complete ZXing Apache-2.0 license is bundled in app/src/main/assets/licenses/ZXing-LICENSE.txt and included in the APK.

## ZCOOL KuaiLe font

Unmodified ZCOOL KuaiLe Regular from Google Fonts, Copyright 2018 The ZCOOL KuaiLe Project Authors, under SIL Open Font License 1.1. Source: https://github.com/googlefonts/zcool-kuaile . License is bundled in Android assets/licenses and iOS Fonts/OFL.txt.

## iPad local SOCKS5 forwarding

HevSocks5Tunnel and its pinned SOCKS5 core, task system, lwIP and libyaml dependencies are included as source in `vendor/hev-socks5-tunnel`. Commit hashes are recorded in `SOURCE.json`; upstream is https://github.com/heiher/hev-socks5-tunnel. The MIT, BSD and other upstream license notices are retained alongside each component and bundled in the iPad app. The local packet bridge uses public NetworkExtension packet APIs.
