// SPDX-License-Identifier: GPL-3.0-or-later
import Foundation

struct QRPayload {
    let network: String
    let gateway: String?
    let port: Int?
    let moons: String?

    static func parse(_ raw: String) throws -> QRPayload {
        func invalid() -> SettingsError { .message("二维码中没有有效的 ZeroTier 入网信息。") }
        func networkID(_ text: String?) throws -> String {
            guard let text, text.count == 16, text.allSatisfy({ $0.isASCII && $0.isHexDigit }),
                  let number = UInt64(text, radix: 16), number != 0 else { throw invalid() }
            return text.lowercased()
        }
        guard raw.count <= 4096 else { throw invalid() }
        let value = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        if let id = try? networkID(value) { return QRPayload(network: id, gateway: nil, port: nil, moons: nil) }
        // Android's URLEncoder encodes spaces as +. Decode that only in the query.
        let pieces = value.split(separator: "?", maxSplits: 1, omittingEmptySubsequences: false)
        let normalized = pieces.count == 2 ? String(pieces[0]) + "?" + pieces[1].replacingOccurrences(of: "+", with: "%20") : value
        guard let url = URLComponents(string: normalized), url.user == nil, url.password == nil,
              url.port == nil, url.fragment == nil else { throw invalid() }
        let scheme = url.scheme?.lowercased(), host = url.host?.lowercased()
        let path = url.path.split(separator: "/").map(String.init)
        if scheme == "zerotier", url.query == nil {
            if url.path.isEmpty || url.path == "/" {
                return QRPayload(network: try networkID(host), gateway: nil, port: nil, moons: nil)
            }
            if host == "network", path.count == 1, url.path == "/\(path[0])" || url.path == "/\(path[0])/" {
                return QRPayload(network: try networkID(path[0]), gateway: nil, port: nil, moons: nil)
            }
        }
        if scheme == "https", ["my.zerotier.com", "central.zerotier.com"].contains(host ?? ""),
           url.query == nil, path.count == 2, ["network", "networks"].contains(path[0]),
           url.path == "/\(path[0])/\(path[1])" || url.path == "/\(path[0])/\(path[1])/" {
            return QRPayload(network: try networkID(path[1]), gateway: nil, port: nil, moons: nil)
        }
        guard scheme == "harborlink", host == "join", url.path.isEmpty || url.path == "/",
              let items = url.queryItems, !items.isEmpty else { throw invalid() }
        var fields: [String: String] = [:]
        for item in items {
            guard ["network", "gateway", "port", "moons"].contains(item.name), fields[item.name] == nil,
                  let value = item.value else { throw invalid() }
            fields[item.name] = value
        }
        let id = try networkID(fields["network"])
        var port: Int?
        if let text = fields["port"] {
            guard !text.isEmpty, text.count <= 5, text.allSatisfy({ $0.isASCII && $0.isNumber }),
                  let number = Int(text), (1...65535).contains(number) else { throw invalid() }
            port = number
        }
        if let gateway = fields["gateway"] {
            try ConnectionSettings(network: id, gateway: gateway, port: String(port ?? 7890)).validate()
        }
        let moons = try fields["moons"].map { try MoonEntry.canonical($0) }
        return QRPayload(network: id, gateway: fields["gateway"], port: port, moons: moons)
    }
    func applying(to original: ConnectionSettings) -> ConnectionSettings {
        ConnectionSettings(network: network, gateway: gateway ?? original.gateway,
                           port: port.map(String.init) ?? original.port, moons: moons ?? original.moons)
    }
    static func encode(_ settings: ConnectionSettings) throws -> String {
        try settings.validate()
        var parts = URLComponents(); parts.scheme = "harborlink"; parts.host = "join"
        parts.queryItems = [URLQueryItem(name: "network", value: settings.dictionary["network"] as? String),
                            URLQueryItem(name: "gateway", value: settings.dictionary["gateway"] as? String),
                            URLQueryItem(name: "port", value: settings.port),
                            URLQueryItem(name: "moons", value: try MoonEntry.canonical(settings.moons))]
        guard let value = parts.string, value.count <= 4096 else { throw SettingsError.message("入网信息过长") }
        _ = try parse(value); return value
    }
}

struct MoonEntry: Identifiable {
    let world: String
    let seed: String
    var id: String { world }
    var display: String { world == seed ? seed : world }
    var line: String { String(repeating: "0", count: 16-world.count) + world + " " + String(repeating: "0", count: 10-seed.count) + seed }
    static func parse(_ text: String) throws -> [MoonEntry] {
        try ConnectionSettings(moons: text).parsedMoons().map { MoonEntry(world: $0["world"]!, seed: $0["seed"]!) }
    }
    static func canonical(_ text: String) throws -> String { try parse(text).map(\.line).joined(separator: "\n") }
}
