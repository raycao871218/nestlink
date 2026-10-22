// SPDX-License-Identifier: GPL-3.0-or-later
#pragma once
#include <cstdint>

inline const char *moonConnectivity(bool found, bool moon, int64_t received, int64_t now, int64_t observedSince) {
    if (!found) return "undiscovered";
    if (!moon) return "notMoon";
    if (received <= 0) return "unavailable";
    if (received < observedSince) return "waiting";
    const auto age = now - received;
    return age >= 0 && age <= 120000 ? "online" : "unavailable";
}
