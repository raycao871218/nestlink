// SPDX-License-Identifier: GPL-3.0-or-later
import Foundation

enum PlanetSettings {
    static let maxSize = 16384
    static func read(_ url: URL) throws -> Data {
        let file = try FileHandle(forReadingFrom: url)
        defer { try? file.close() }
        let data = try file.read(upToCount: maxSize + 1) ?? Data()
        try validate(data)
        return data
    }
    // C25519 World serialization, matching the bundled ZeroTier engine.
    static func validate(_ data: Data) throws {
        let bytes = [UInt8](data)
        let invalid = SettingsError.message("无法读取有效的 Planet 文件（最多 16 KiB），不支持 Moon 文件。")
        guard bytes.count >= 178, bytes.count <= maxSize, bytes[0] == 1 else { throw invalid }
        var offset = 177
        func take(_ count: Int) throws {
            guard offset + count <= bytes.count else { throw invalid }
            offset += count
        }
        func byte() throws -> UInt8 {
            try take(1); return bytes[offset - 1]
        }
        let roots = try byte()
        guard (1...4).contains(roots) else { throw invalid }
        for _ in 0..<roots {
            try take(5)
            guard try byte() == 0 else { throw invalid }
            try take(64)
            guard try byte() == 0 else { throw invalid }
            let endpoints = try byte()
            guard endpoints <= 32 else { throw invalid }
            for _ in 0..<endpoints {
                let type = try byte()
                guard type == 4 || type == 6 else { throw invalid }
                try take(type == 4 ? 6 : 18)
            }
        }
        guard offset == bytes.count else { throw invalid }
    }
    static func shouldReplace(applied: String?, selected: String, custom: Bool, exists: Bool) -> Bool {
        guard exists else { return true }
        if let applied { return applied != selected }
        return custom || selected.contains(":")
    }
}
