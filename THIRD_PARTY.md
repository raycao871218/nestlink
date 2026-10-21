# Third-party components

This application embeds the ZeroTier One core and Java JNI SDK from upstream tag
1.16.0: https://github.com/zerotier/ZeroTierOne/tree/1.16.0

Source and notices are retained in vendor/ZeroTierOne. Core files carry MPL-2.0
notices; the Java/JNI wrapper carries GPLv3 notices. HarborLink original application
code is supplied under GPL-3.0-or-later in LICENSE. This source package accompanies
the debug APK.

The application CMakeLists builds node/*.cpp, osdep/OSUtils.cpp, and java/jni/*.cpp.
It does not build the nonfree controller or desktop service. Referenced Prometheus
and OpenTelemetry headers retain their notices under vendor/ZeroTierOne/ext.

Upstream Java, JNI, and core sources are not edited. Integration CMakeLists is in
app/src/main/cpp, using NDK 27.2.12479018, CMake 3.22.1, C++17, static libc++,
optimization level O2, and 16 KB ELF segment alignment.

Gradle wrapper tooling retains its embedded Apache-2.0 notices.

The nlohmann/json header dependency is retained under its MIT license in vendor/ZeroTierOne/ext/nlohmann/LICENSE.MIT.
