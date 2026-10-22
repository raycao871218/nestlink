// SPDX-License-Identifier: GPL-3.0-or-later
import Foundation
import Network
import NetworkExtension
import CryptoKit

final class PacketTunnelProvider: NEPacketTunnelProvider {
    private let control = DispatchQueue(label: "app.nestlink.tunnel")
    private var engine: ZTEngine?
    private var settings = ConnectionSettings()
    private var running = false
    private var reading = false
    private var startCompletion: ((Error?) -> Void)?
    private var startTimeout: DispatchWorkItem?
    private var healthTimer: DispatchSourceTimer?
    private var pathMonitor: NWPathMonitor?
    private var probes: [TunnelProbe] = []
    private var epoch = 0
    private var configurationGeneration = 0
    private var appliedConfiguration = ""
    private var phase = "joining"
    private var message = "正在连接 ZeroTier；首次连接需要在 Controller 授权设备"
    private var lanReady = false
    private var internetReady = false
    private var checkedAt: Double = 0
    private var checking = false
    private var hasValidated = false
    private var initialFailures = 0

    override func startTunnel(options: [String: NSObject]?, completionHandler: @escaping (Error?) -> Void) {
        control.async { self.start(completionHandler) }
    }
    private func start(_ completion: @escaping (Error?) -> Void) {
        do {
            guard let config = protocolConfiguration as? NETunnelProviderProtocol else {
                throw SettingsError.message("缺少 VPN 配置")
            }
            settings = ConnectionSettings(dictionary: config.providerConfiguration ?? [:]); try settings.validate()
            guard let planet = Bundle.main.url(forResource: "planet", withExtension: nil) else {
                throw SettingsError.message("缺少内置 Planet")
            }
            let hash = SHA256.hash(data: try Data(contentsOf: planet)).map { String(format: "%02x", $0) }.joined()
            guard hash == "ae6e33c4d3d6f5e599dfdfe72260210242a693dce2477578433972caa3694a72" else {
                throw SettingsError.message("内置 Planet 校验失败")
            }
            running = true; hasValidated = false; initialFailures = 0; startCompletion = completion
            let engine = ZTEngine(); self.engine = engine
            engine.configurationHandler = { [weak self] address, prefix, mtu, status in
                guard let self else { return }; self.control.async { self.configure(address, prefix: prefix, mtu: mtu, status: status) }
            }
            engine.packetHandler = { [weak self] packet in
                self?.packetFlow.writePackets([packet], withProtocols: [NSNumber(value: AF_INET)])
            }
            engine.failureHandler = { [weak self] text in
                guard let self else { return }; self.control.async { self.fail(SettingsError.message(text)) }
            }
            let store = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0].appendingPathComponent("ZeroTier", isDirectory: true)
            try engine.start(withNetwork: settings.dictionary["network"] as! String,
                             gateway: settings.dictionary["gateway"] as! String,
                             moons: settings.parsedMoons(), store: store, planet: planet)
            let timeout = DispatchWorkItem { [weak self] in
                guard let self, self.startCompletion != nil else { return }
                self.engine?.snapshot { snapshot in
                    self.control.async {
                        guard self.startCompletion != nil else { return }
                        self.fail(SettingsError.message("等待网络配置超时。请在 Controller 授权设备 \(snapshot["node"] ?? "")，并分配 IPv4 地址后重试。"))
                    }
                }
            }
            startTimeout = timeout; control.asyncAfter(deadline: .now() + 50, execute: timeout)
            let monitor = NWPathMonitor(); pathMonitor = monitor
            monitor.pathUpdateHandler = { [weak self] path in
                guard let self, self.running else { return }
                self.engine?.resetPath(); self.invalidateHealth()
                self.reasserting = path.status != .satisfied
                self.message = path.status == .satisfied ? "网络路径变化，正在重新验证" : "底层网络不可用，等待恢复"
                if !self.appliedConfiguration.isEmpty { self.checkHealth() }
            }
            monitor.start(queue: control)
        } catch {
            if startCompletion == nil { completion(error) } else { fail(error) }
        }
    }
    private func configure(_ address: String, prefix: Int, mtu: Int, status: Int) {
        guard running else { return }
        guard !address.isEmpty else {
            invalidateHealth()
            phase = status == 2 || status == 6 ? "authorization" : "joining"
            let reasons = [0: "正在等待 Controller 分配网络配置", 1: "尚未分配 IPv4 地址", 2: "请在 Controller 授权此设备", 3: "网络不存在，请检查网络 ID", 4: "ZeroTier 端口配置失败", 5: "Controller 要求更新 ZeroTier 核心", 6: "Controller 要求认证"]
            message = reasons[status] ?? "ZeroTier 网络已关闭"
            if !appliedConfiguration.isEmpty { fail(SettingsError.message(message)) }
            return
        }
        let key = "\(address)/\(prefix)/\(mtu)"
        guard appliedConfiguration != key else { return }
        invalidateHealth(); appliedConfiguration = key
        configurationGeneration += 1
        let token = configurationGeneration
        // The NAS is a virtual destination, not an outer transport endpoint.
        let config = NEPacketTunnelNetworkSettings(tunnelRemoteAddress: "127.0.0.1")
        let mask = UInt32.max << (32 - prefix)
        let maskString = [24, 16, 8, 0].map { String((mask >> $0) & 255) }.joined(separator: ".")
        let ipv4 = NEIPv4Settings(addresses: [address], subnetMasks: [maskString])
        ipv4.includedRoutes = [NEIPv4Route.default()]; config.ipv4Settings = ipv4
        // Capture and discard IPv6 while this IPv4-only tunnel is active.
        let ipv6 = NEIPv6Settings(addresses: ["fd4e:6573:744c::1"], networkPrefixLengths: [128])
        ipv6.includedRoutes = [NEIPv6Route.default()]; config.ipv6Settings = ipv6
        let dns = NEDNSSettings(servers: [settings.gateway.trimmingCharacters(in: .whitespacesAndNewlines)])
        dns.matchDomains = [""]; config.dnsSettings = dns; config.mtu = NSNumber(value: mtu)
        setTunnelNetworkSettings(config) { error in
            self.control.async {
                guard self.running, self.configurationGeneration == token else { return }
                if let error { self.fail(error); return }
                self.phase = "validating"; self.message = "隧道已建立，正在验证 NAS 与互联网"
                self.reasserting = false
                self.startTimeout?.cancel(); self.startTimeout = nil
                let done = self.startCompletion; self.startCompletion = nil; done?(nil)
                if !self.reading { self.reading = true; self.readPackets() }
                if self.healthTimer == nil {
                    let timer = DispatchSource.makeTimerSource(queue: self.control)
                    timer.schedule(deadline: .now() + 1, repeating: 20)
                    timer.setEventHandler { [weak provider = self] in provider?.checkHealth() }
                    self.healthTimer = timer; timer.resume()
                }
            }
        }
    }
    private func readPackets() {
        guard running else { reading = false; return }
        packetFlow.readPackets { [weak self] packets, protocols in
            guard let self else { return }
            self.control.async {
                guard self.running, let engine = self.engine else { self.reading = false; return }
                let ipv4 = zip(packets, protocols).compactMap { $0.1.int32Value == AF_INET ? $0.0 : nil }
                engine.processPackets(ipv4) { self.control.async { self.readPackets() } }
            }
        }
    }
    private func invalidateHealth() {
        epoch += 1; lanReady = false; internetReady = false; checkedAt = 0; checking = false
        let old = probes; probes.removeAll(); old.forEach { $0.cancel() }
        phase = appliedConfiguration.isEmpty ? "joining" : "validating"
    }
    private func checkHealth() {
        guard running, !appliedConfiguration.isEmpty, !checking else { return }
        checking = true; let token = epoch
        probes.removeAll()
        var pending = 2
        func result(_ success: Bool, lan: Bool) {
            guard self.running, self.epoch == token else { return }
            if lan { self.lanReady = success } else { self.internetReady = success }
            pending -= 1
            if pending == 0 {
                self.checking = false; self.checkedAt = Date().timeIntervalSince1970
                self.phase = self.lanReady && self.internetReady ? "connected" : "degraded"
                self.message = self.phase == "connected" ? "已连接 · NAS 与互联网检测通过" : "连接待恢复 · 将自动重新检测"
                if self.phase == "connected" {
                    self.hasValidated = true; self.initialFailures = 0
                } else if !self.hasValidated {
                    self.initialFailures += 1
                    if self.initialFailures >= 2 {
                        let reason = !self.lanReady ? "NAS 的 TCP 检测端口不可达" : "NAS 可达，但隧道 HTTPS 检测未通过（可能涉及 DNS 或出口转发）"
                        self.fail(SettingsError.message("\(reason)。已自动断开 VPN，恢复原网络；请检查 NAS 网关配置后重试。"))
                    }
                }
            }
        }
        probes = [
            TunnelProbe(provider: self, host: settings.gateway.trimmingCharacters(in: .whitespacesAndNewlines), port: settings.port, https: false, queue: control) { result($0, lan: true) },
            TunnelProbe(provider: self, host: "www.google.com", port: "443", https: true, queue: control) { result($0, lan: false) }
        ]
    }
    override func handleAppMessage(_ messageData: Data, completionHandler: ((Data?) -> Void)?) {
        control.async {
            guard let engine = self.engine else { completionHandler?(nil); return }
            let request = (try? JSONSerialization.jsonObject(with: messageData)) as? [String: Any] ?? [:]
            engine.diagnosticsEnabled = request["developer"] as? Bool ?? false
            if request["command"] as? String == "recheck" { self.invalidateHealth(); self.checkHealth() }
            engine.snapshot { snapshot in
                self.control.async {
                    var result = snapshot; result["phase"] = self.phase; result["message"] = self.message
                    let fresh = self.checkedAt > 0 && Date().timeIntervalSince1970 - self.checkedAt < 45
                    result["lanReady"] = fresh && self.lanReady; result["internetReady"] = fresh && self.internetReady
                    result["checkedAt"] = self.checkedAt
                    if self.phase == "connected" && !fresh { result["phase"] = "validating"; result["message"] = "检测结果已过期，等待重新验证" }
                    completionHandler?(try? JSONSerialization.data(withJSONObject: result))
                }
            }
        }
    }
    private func fail(_ error: Error) {
        guard running else { return }
        let done = startCompletion; startCompletion = nil
        cleanup { if let done { done(error) } else { self.cancelTunnelWithError(error) } }
    }
    private func cleanup(_ completion: @escaping () -> Void) {
        running = false; configurationGeneration += 1; invalidateHealth(); startTimeout?.cancel(); startTimeout = nil
        healthTimer?.cancel(); healthTimer = nil; pathMonitor?.cancel(); pathMonitor = nil
        appliedConfiguration = ""
        guard let engine else { completion(); return }
        engine.stop { self.control.async { self.engine = nil; completion() } }
    }
    override func stopTunnel(with reason: NEProviderStopReason, completionHandler: @escaping () -> Void) {
        control.async {
            let done = self.startCompletion; self.startCompletion = nil
            self.cleanup { done?(SettingsError.message("连接已取消")); completionHandler() }
        }
    }
}
