// SPDX-License-Identifier: GPL-3.0-or-later
import Foundation
import Darwin

struct HomeNetworkSettings: Equatable {
    var enabled = false
    var ssid = ""
    var nas = ""
    var port = "7890"
    // Legacy router-mode flag is retained only for configuration migration.
    var routerConfigured = false

    var dictionary: [String: Any] {
        ["enabled": enabled, "ssid": ssid, "nas": nas.trimmingCharacters(in: .whitespacesAndNewlines),
         "port": Int(port) ?? 0, "routerConfigured": routerConfigured]
    }
    init() {}
    init(dictionary: [String: Any]) {
        enabled = dictionary["enabled"] as? Bool ?? false
        ssid = dictionary["ssid"] as? String ?? ""
        nas = dictionary["nas"] as? String ?? ""
        port = String(dictionary["port"] as? Int ?? 7890)
        routerConfigured = dictionary["routerConfigured"] as? Bool ?? false
    }
    func validate() throws {
        guard enabled else { return }
        guard !ssid.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty,
              ssid.utf8.count <= 32, !ssid.unicodeScalars.contains(where: CharacterSet.controlCharacters.contains) else {
            throw SettingsError.message("请填写准确的家庭 Wi-Fi 名称（SSID，最多 32 字节）")
        }
        guard Self.isPrivateIPv4(nas.trimmingCharacters(in: .whitespacesAndNewlines)) else {
            throw SettingsError.message("请填写 NAS 的局域网 IPv4 地址，例如 192.168.1.66")
        }
        guard let number = Int(port), (1...65535).contains(number) else {
            throw SettingsError.message("家庭 NAS 代理端口必须在 1–65535 之间")
        }
    }
    static func ipv4(_ text: String) -> UInt32? {
        var value = in_addr()
        guard inet_pton(AF_INET, text, &value) == 1 else { return nil }
        return UInt32(bigEndian: value.s_addr)
    }
    static func isPrivateIPv4(_ text: String) -> Bool {
        guard let ip = ipv4(text) else { return false }
        return ip >> 24 == 10 || ip >> 20 == 0xac1 || ip >> 16 == 0xc0a8
    }
    static func sameSubnet(_ host: String, address: String, mask: String) -> Bool {
        guard let host = ipv4(host), let address = ipv4(address), let mask = ipv4(mask), mask != 0 else { return false }
        return host & mask == address & mask && host != address
    }
}
