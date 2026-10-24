import Foundation
import ImageIO
import NetworkExtension

@main
struct SettingsTests {
    static func main() throws {
        func rejected(_ settings: ConnectionSettings) {
            do { try settings.validate(); fatalError("Invalid settings accepted") } catch {}
        }
        let planet = try Data(contentsOf: URL(fileURLWithPath: CommandLine.arguments[1]))
        try PlanetSettings.validate(planet)
        func rejectPlanet(_ data: Data) {
            do { try PlanetSettings.validate(data); fatalError("Invalid Planet accepted") } catch {}
        }
        for size in 0..<planet.count { rejectPlanet(Data(planet.prefix(size))) }
        rejectPlanet(Data(repeating: 0, count: PlanetSettings.maxSize + 1))
        var moonFile = planet; moonFile[0] = 2; rejectPlanet(moonFile)
        var trailing = planet; trailing.append(0); rejectPlanet(trailing)
        var wrongRoots = planet; wrongRoots[177] = 5; rejectPlanet(wrongRoots)
        assert(PlanetSettings.shouldReplace(applied: nil, selected: "custom", custom: true, exists: true))
        assert(!PlanetSettings.shouldReplace(applied: "custom", selected: "custom", custom: true, exists: true))
        assert(PlanetSettings.shouldReplace(applied: "custom", selected: "builtin", custom: false, exists: true))
        assert(PlanetSettings.shouldReplace(applied: "builtin", selected: "builtin", custom: false, exists: false))
        assert(!PlanetSettings.shouldReplace(applied: nil, selected: "builtin", custom: false, exists: true))
        assert(PlanetSettings.shouldReplace(applied: nil, selected: "builtin:reset", custom: false, exists: true))
        assert(PlanetSettings.shouldReplace(applied: "builtin", selected: "builtin:reset", custom: false, exists: true))
        let valid = ConnectionSettings(network: "0123456789abcdef", gateway: "10.1.2.1")
        try valid.validate()
        var custom = valid; custom.customPlanet = planet.base64EncodedString(); custom.planetRevision = "test-import"
        try custom.validate()
        assert(ConnectionSettings(dictionary: custom.dictionary) == custom)
        let customQR = try QRPayload.encode(custom)
        assert(!customQR.contains(custom.customPlanet))
        let reapplied = try QRPayload.parse(customQR).applying(to: custom)
        assert(reapplied.customPlanet == custom.customPlanet)
        var invalidPlanet = valid; invalidPlanet.customPlanet = "invalid"; rejected(invalidPlanet)
        print("Planet validation, import/reset selection, persistence and QR exclusion: PASS")
        assert(ConnectionSettings(dictionary: valid.dictionary) == valid)
        rejected(ConnectionSettings(network: "0000000000000000", gateway: "10.1.2.1"))
        rejected(ConnectionSettings(network: "+123456789abcdef", gateway: "10.1.2.1"))
        rejected(ConnectionSettings(network: valid.network, gateway: "127.0.0.1"))
        rejected(ConnectionSettings(network: valid.network, gateway: "224.0.0.1"))
        rejected(ConnectionSettings(network: valid.network, gateway: "10.0.0.999"))
        rejected(ConnectionSettings(network: valid.network, gateway: "::1"))
        rejected(ConnectionSettings(network: valid.network, gateway: valid.gateway, port: "65536"))
        rejected(ConnectionSettings(network: valid.network, gateway: valid.gateway, moons: "abcdef0123 12345678901"))
        rejected(ConnectionSettings(network: valid.network, gateway: valid.gateway, moons: "abcdef0123 123\nabcdef0123 124"))
        let moon = ConnectionSettings(moons: "0xabcdef0123\n000000abcdef0123 abcdef0123")
        let parsed = try moon.parsedMoons()
        assert(parsed.count == 1 && parsed[0]["seed"] == "abcdef0123")
        rejected(ConnectionSettings(network: valid.network, gateway: valid.gateway, moons: (1...9).map { String($0, radix: 16) }.joined(separator: "\n")))
        print("Connection validation, config round-trip, Moon limits and conflicts: PASS")
        let legacy = ConnectionSettings(dictionary: ["network": valid.network, "gateway": valid.gateway])
        assert(!legacy.home.enabled && !legacy.home.routerConfigured)
        var home = HomeNetworkSettings()
        home.enabled = true; home.ssid = "My Home WiFi"; home.nas = "192.168.1.66"; home.routerConfigured = true
        try home.validate()
        var withHome = valid; withHome.home = home
        assert(ConnectionSettings(dictionary: withHome.dictionary) == withHome)
        let disabledRules = try OnDemandPolicy.rules(for: HomeNetworkSettings())
        assert(disabledRules.isEmpty)
        let rules = try OnDemandPolicy.rules(for: home)
        assert(rules.count == 2)
        assert(rules[0] is NEOnDemandRuleConnect && rules[0].interfaceTypeMatch == .wiFi)
        assert(rules[0].ssidMatch == ["My Home WiFi"])
        assert(rules[1] is NEOnDemandRuleConnect && rules[1].interfaceTypeMatch == .any && rules[1].ssidMatch == nil)
        var invalidHome = withHome
        invalidHome.home.routerConfigured = false; try invalidHome.validate()
        invalidHome = withHome; invalidHome.home.ssid = ""; rejected(invalidHome)
        invalidHome = withHome; invalidHome.home.ssid = String(repeating: "家", count: 11); rejected(invalidHome)
        invalidHome = withHome; invalidHome.home.ssid = "home\nother"; rejected(invalidHome)
        invalidHome = withHome; invalidHome.home.nas = "8.8.8.8"; rejected(invalidHome)
        invalidHome = withHome; invalidHome.home.nas = "127.0.0.1"; rejected(invalidHome)
        invalidHome = withHome; invalidHome.home.port = "0"; rejected(invalidHome)
        for ip in ["10.0.0.1", "172.16.0.1", "172.31.255.254", "192.168.1.66"] { assert(HomeNetworkSettings.isPrivateIPv4(ip)) }
        for ip in ["172.15.0.1", "172.32.0.1", "192.169.1.1", "169.254.1.2", "::1", "999.1.2.3"] { assert(!HomeNetworkSettings.isPrivateIPv4(ip)) }
        assert(HomeNetworkSettings.sameSubnet("192.168.1.66", address: "192.168.1.10", mask: "255.255.255.0"))
        assert(!HomeNetworkSettings.sameSubnet("10.12.0.66", address: "192.168.1.10", mask: "255.255.255.0"))
        assert(!HomeNetworkSettings.sameSubnet("192.168.1.10", address: "192.168.1.10", mask: "255.255.255.0"))
        assert(!HomeNetworkSettings.sameSubnet("192.168.1.66", address: "192.168.1.10", mask: "0.0.0.0"))
        let imported = try QRPayload.parse(valid.network).applying(to: withHome)
        assert(imported.home == home)
        let shared = try QRPayload.encode(withHome)
        assert(!shared.contains("My%20Home") && !shared.contains("192.168.1.66"))
        print("Home settings migration, no manual router setup, exact SSID rule order, local subnet checks and QR isolation: PASS")

        for text in [valid.network, "zerotier://\(valid.network)", "zerotier://network/\(valid.network)",
                     "https://my.zerotier.com/network/\(valid.network)", "https://central.zerotier.com/networks/\(valid.network)/"] {
            let decoded = try QRPayload.parse(text)
            assert(decoded.network == valid.network && decoded.gateway == nil && decoded.moons == nil)
            assert(decoded.applying(to: valid) == valid)
        }
        let complete = ConnectionSettings(network: valid.network, gateway: valid.gateway, port: "7890", moons: "abcdef0123")
        let roundtrip = try QRPayload.parse(QRPayload.encode(complete)).applying(to: valid)
        assert(roundtrip.network == complete.network && roundtrip.gateway == complete.gateway && roundtrip.port == complete.port)
        let actualMoons = try MoonEntry.canonical(roundtrip.moons)
        let expectedMoons = try MoonEntry.canonical(complete.moons)
        assert(actualMoons == expectedMoons)
        let android = try QRPayload.parse("harborlink://join?network=\(valid.network)&moons=000000abcdef0123+abcdef0123")
        let androidMoons = try MoonEntry.parse(android.moons ?? "")
        assert(androidMoons.count == 1)
        let clear = try QRPayload.parse("harborlink://join?network=\(valid.network)&moons=").applying(to: complete)
        assert(clear.moons.isEmpty && clear.gateway == complete.gateway)
        let invalid = ["harborlink://join?network=\(valid.network)&network=\(valid.network)",
                       "harborlink://join?network=\(valid.network)&token=secret",
                       "harborlink://join?network=\(valid.network)&port=0",
                       "harborlink://join?network=\(valid.network)&port=+7890",
                       "harborlink://join?network=\(valid.network)&gateway=999.1.1.1",
                       "harborlink://join?network=\(valid.network)&moons=bad+bad+bad",
                       "https://example.com/network/\(valid.network)",
                       "https://user@my.zerotier.com/network/\(valid.network)",
                       "zerotier://\(valid.network)?token=x", "zerotier://\(valid.network)#x",
                       "zerotier://network//\(valid.network)", String(repeating: "a", count: 4097)]
        for text in invalid {
            do { _ = try QRPayload.parse(text); fatalError("Invalid QR accepted: \(text)") } catch {}
        }
        print("Android-compatible QR formats, optional preservation, explicit clearing, URL safety and round-trip: PASS")
        let encoded = try QRPayload.encode(complete)
        let image = try QRImageCodec.render(encoded)
        let png = NSMutableData()
        let writer = CGImageDestinationCreateWithData(png, "public.png" as CFString, 1, nil)!
        CGImageDestinationAddImage(writer, image, nil)
        assert(CGImageDestinationFinalize(writer))
        let decoded = try QRImageCodec.decode(png as Data)
        assert(decoded == [encoded])
        do { _ = try QRImageCodec.decode(Data()); fatalError("Empty image accepted") } catch {}
        print("CoreImage QR generation → PNG → Vision local recognition: PASS")
    }
}
