// SPDX-License-Identifier: GPL-3.0-or-later
import NetworkExtension

// The OS evaluates these rules even while the app is suspended. Do not replace
// SSID matching with a successful TCP probe: private addresses can repeat on other LANs.
enum OnDemandPolicy {
    static func rules(for home: HomeNetworkSettings) throws -> [NEOnDemandRule] {
        guard home.enabled else { return [] }
        try home.validate()
        let atHome = NEOnDemandRuleConnect()
        atHome.interfaceTypeMatch = .wiFi
        atHome.ssidMatch = [home.ssid]
        let away = NEOnDemandRuleConnect()
        away.interfaceTypeMatch = .any
        return [atHome, away]
    }
}
