// SPDX-License-Identifier: GPL-3.0-or-later
import Foundation
import Darwin

struct ConnectionSettings: Equatable {
    var network = ""
    var gateway = ""
    var port = "7890"
    var moons = ""

    var dictionary: [String: Any] {
        ["network": network.trimmingCharacters(in: .whitespacesAndNewlines).lowercased(),
         "gateway": gateway.trimmingCharacters(in: .whitespacesAndNewlines),
         "port": Int(port) ?? 0, "moons": moons]
    }

    init(network: String = "", gateway: String = "", port: String = "7890", moons: String = "") {
        self.network = network; self.gateway = gateway; self.port = port; self.moons = moons
    }
    init(dictionary: [String: Any]) {
        network = dictionary["network"] as? String ?? ""
        gateway = dictionary["gateway"] as? String ?? ""
        port = String(dictionary["port"] as? Int ?? 7890)
        moons = dictionary["moons"] as? String ?? ""
    }
    func validate() throws {
        let id = network.trimmingCharacters(in: .whitespacesAndNewlines)
        guard id.count == 16, UInt64(id, radix: 16).map({ $0 != 0 }) == true,
              id.allSatisfy({ $0.isASCII && $0.isHexDigit }) else {
            throw SettingsError.message("网络 ID 必须是非零的 16 位十六进制数字")
        }
        var address = in_addr()
        let host = gateway.trimmingCharacters(in: .whitespacesAndNewlines)
        guard inet_pton(AF_INET, host, &address) == 1,
              let first = host.split(separator: ".").first.flatMap({ Int($0) }),
              first > 0, first < 224, first != 127 else {
            throw SettingsError.message("请填写 NAS 的 ZeroTier IPv4 地址")
        }
        guard let p = Int(port), (1...65535).contains(p) else {
            throw SettingsError.message("NAS 检测端口必须在 1–65535 之间")
        }
        _ = try parsedMoons()
    }
    func parsedMoons() throws -> [[String: String]] {
        var entries: [UInt64: UInt64] = [:]
        for line in moons.split(whereSeparator: \.isNewline) {
            let parts = line.split(whereSeparator: { $0.isWhitespace || $0 == "," }).map(String.init)
            if parts.isEmpty { continue }
            func hex(_ text: String, max: Int) -> UInt64? {
                let raw = text.lowercased().hasPrefix("0x") ? String(text.dropFirst(2)) : text
                guard !raw.isEmpty, raw.count <= max, raw.allSatisfy({ $0.isASCII && $0.isHexDigit }),
                      let n = UInt64(raw, radix: 16), n > 0 else { return nil }
                return n
            }
            guard parts.count <= 2, let world = hex(parts[0], max: 16),
                  let seed = hex(parts.count == 2 ? parts[1] : parts[0], max: 10) else {
                throw SettingsError.message("Moon 每行填写 World ID 和 Seed ID；Seed 最多 10 位十六进制")
            }
            if let old = entries[world], old != seed { throw SettingsError.message("同一个 Moon 不能填写不同 Seed") }
            entries[world] = seed
            guard entries.count <= 8 else { throw SettingsError.message("最多配置 8 个 Moon") }
        }
        return entries.sorted(by: { $0.key < $1.key }).map { ["world": String($0.key, radix: 16), "seed": String($0.value, radix: 16)] }
    }
}

enum SettingsError: LocalizedError {
    case message(String)
    var errorDescription: String? { if case let .message(text) = self { return text }; return nil }
}
