// SPDX-License-Identifier: GPL-3.0-or-later
import Combine
import Foundation
import NetworkExtension

@MainActor
final class VPNController: ObservableObject {
    @Published var settings = ConnectionSettings()
    @Published var status: NEVPNStatus = .invalid
    @Published var snapshot: [String: Any] = [:]
    @Published var error: String?
    @Published var busy = true
    private var manager: NETunnelProviderManager?
    private var observer: NSObjectProtocol?
    private var polling: Task<Void, Never>?
    private var requestID: UUID?
    private var lastSnapshot: Date?

    var active: Bool { [.connecting, .connected, .reasserting, .disconnecting].contains(status) }
    var node: String { snapshot["node"] as? String ?? "" }
    var address: String { snapshot["address"] as? String ?? "" }
    var fresh: Bool {
        status == .connected && snapshotFresh
    }
    var snapshotFresh: Bool { active && lastSnapshot.map { Date().timeIntervalSince($0) < 8 } == true }
    var verified: Bool { fresh && snapshot["phase"] as? String == "connected" }
    var title: String {
        if busy { return "正在准备" }
        switch status {
        case .connecting: return snapshot["phase"] as? String == "authorization" ? "等待设备授权" : "正在连接回家"
        case .connected: return verified ? "已经到家了" : "正在确认连接"
        case .reasserting: return "正在恢复连接"
        case .disconnecting: return "正在断开"
        default: return "随时，连接回家"
        }
    }
    var detail: String {
        if active { return snapshot["message"] as? String ?? "等待隧道状态；首次连接请在 Controller 授权设备" }
        return "通过家中的 NAS，访问家庭网络与互联网。"
    }
    var extensionID: String { (Bundle.main.bundleIdentifier ?? "com.naslink.ipad") + ".PacketTunnel" }

    init() {
        observer = NotificationCenter.default.addObserver(forName: .NEVPNStatusDidChange, object: nil, queue: .main) { [weak self] notification in
            Task { @MainActor in
                guard let self, let connection = notification.object as? NEVPNConnection,
                      connection === self.manager?.connection else { return }
                self.statusChanged()
            }
        }
        Task { await load() }
        polling = Task { [weak self] in
            while !Task.isCancelled {
                self?.refreshSnapshot()
                try? await Task.sleep(for: .seconds(2))
            }
        }
    }
    private func load() async {
        defer { busy = false }
        do {
            let all = try await NETunnelProviderManager.loadAllFromPreferences()
            manager = all.first { ($0.protocolConfiguration as? NETunnelProviderProtocol)?.providerBundleIdentifier == extensionID }
            if let saved = UserDefaults.standard.dictionary(forKey: "connection") {
                settings = ConnectionSettings(dictionary: saved)
            } else if let config = (manager?.protocolConfiguration as? NETunnelProviderProtocol)?.providerConfiguration {
                settings = ConnectionSettings(dictionary: config)
            }
            #if DEBUG
            // Inject development settings at launch; never embed private network values in source.
            if let text = ProcessInfo.processInfo.environment["NESTLINK_TEST_CONFIG"],
               let data = text.data(using: .utf8),
               let config = try JSONSerialization.jsonObject(with: data) as? [String: Any] {
                let supplied = ConnectionSettings(dictionary: config)
                try supplied.validate(); settings = supplied
                UserDefaults.standard.set(supplied.dictionary, forKey: "connection")
            }
            #endif
            statusChanged()
        } catch { self.error = "读取 VPN 配置失败：\(error.localizedDescription)" }
    }
    func connect() {
        guard !busy, !active else { return }
        do { try settings.validate() } catch { self.error = error.localizedDescription; return }
        busy = true; error = nil; snapshot = [:]; lastSnapshot = nil
        let config = settings.dictionary
        UserDefaults.standard.set(config, forKey: "connection")
        Task {
            defer { busy = false }
            do {
                let manager = self.manager ?? NETunnelProviderManager()
                let tunnel = NETunnelProviderProtocol()
                tunnel.providerBundleIdentifier = extensionID
                tunnel.providerConfiguration = config
                // ZeroTier has multiple physical peers. The NAS is inside the overlay,
                // not a physical tunnel server to exempt from VPN routing.
                tunnel.serverAddress = "127.0.0.1"
                // Use explicit default routes after configuration. Enforcing all networks
                // here also blocks ordinary traffic during joining and reconnecting.
                tunnel.includeAllNetworks = false
                manager.protocolConfiguration = tunnel
                manager.localizedDescription = "NestLink · 窝窝连"
                manager.isEnabled = true
                try await manager.saveToPreferences()
                try await manager.loadFromPreferences()
                self.manager = manager
                try manager.connection.startVPNTunnel()
                statusChanged()
            } catch { self.error = "启动失败：\(error.localizedDescription)" }
        }
    }
    func disconnect() { manager?.connection.stopVPNTunnel(); snapshot = [:]; lastSnapshot = nil; statusChanged() }
    private func statusChanged() {
        status = manager?.connection.status ?? .invalid
        if !active {
            requestID = nil; snapshot = [:]; lastSnapshot = nil
            manager?.connection.fetchLastDisconnectError { [weak self] error in
                Task { @MainActor in
                    if let error, self?.active == false { self?.error = error.localizedDescription; self?.writeDebugStatus() }
                }
            }
        } else { refreshSnapshot() }
        writeDebugStatus()
    }
    private func refreshSnapshot() {
        guard active, status != .disconnecting, let session = manager?.connection as? NETunnelProviderSession else { return }
        if let lastSnapshot, Date().timeIntervalSince(lastSnapshot) > 8 { snapshot = [:] }
        guard requestID == nil else { return }
        let id = UUID(); requestID = id
        do {
            let message = try JSONSerialization.data(withJSONObject: ["command": "status", "developer": UserDefaults.standard.bool(forKey: "developer")] as [String: Any])
            try session.sendProviderMessage(message) { [weak self] data in
                Task { @MainActor in
                    guard let self, self.requestID == id, self.active else { return }
                    self.requestID = nil
                    if let data, let result = try? JSONSerialization.jsonObject(with: data) as? [String: Any] {
                        self.snapshot = result; self.lastSnapshot = Date()
                        self.writeDebugStatus()
                    }
                }
            }
            Task { [weak self] in
                try? await Task.sleep(for: .seconds(4))
                if self?.requestID == id { self?.requestID = nil }
            }
        } catch { requestID = nil }
    }
    func recheck() {
        guard let session = manager?.connection as? NETunnelProviderSession, status == .connected else { return }
        requestID = nil; lastSnapshot = nil
        snapshot["phase"] = "validating"; snapshot["lanReady"] = false; snapshot["internetReady"] = false
        do {
            let message = try JSONSerialization.data(withJSONObject: ["command": "recheck", "developer": UserDefaults.standard.bool(forKey: "developer")] as [String: Any])
            try session.sendProviderMessage(message)
        } catch { self.error = error.localizedDescription }
    }
    private func writeDebugStatus() {
        #if DEBUG
        var diagnostic = snapshot
        diagnostic["vpnStatus"] = status.rawValue
        diagnostic["error"] = error ?? ""
        diagnostic["observedAt"] = Date().timeIntervalSince1970
        if let data = try? JSONSerialization.data(withJSONObject: diagnostic),
           let folder = FileManager.default.urls(for: .cachesDirectory, in: .userDomainMask).first {
            try? data.write(to: folder.appendingPathComponent("ConnectionStatus.json"), options: .atomic)
        }
        #endif
    }
}
