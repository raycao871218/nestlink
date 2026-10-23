// SPDX-License-Identifier: GPL-3.0-or-later
import Foundation
import Network
import NetworkExtension
import CoreLocation
import Darwin

struct LocalIPv4Address: Identifiable {
    let interface: String
    let address: String
    let mask: String
    var id: String { interface + "/" + address }
}

@MainActor
final class CurrentNetworkReader: NSObject, ObservableObject, CLLocationManagerDelegate {
    @Published private(set) var addresses: [LocalIPv4Address] = []
    @Published private(set) var gateways: [String] = []
    @Published private(set) var ssid: String?
    @Published private(set) var message: String?
    @Published private(set) var reading = false
    @Published private(set) var wifiAvailable = false
    @Published private(set) var canRequestLocation = false
    private let location = CLLocationManager()
    private var monitor: NWPathMonitor?
    private var generation = UUID()
    private var pendingAuthorization = false
    private var deadline: Task<Void, Never>?

    override init() {
        super.init()
        location.delegate = self
        canRequestLocation = location.authorizationStatus == .notDetermined
    }
    func read() {
        cancel()
        ssid = nil; addresses = []; gateways = []; message = nil; wifiAvailable = false; reading = true
        let token = generation
        let monitor = NWPathMonitor(requiredInterfaceType: .wifi)
        self.monitor = monitor
        monitor.pathUpdateHandler = { [weak self] path in
            Task { @MainActor [weak self] in
                guard let self, self.generation == token, self.monitor != nil else { return }
                self.monitor?.cancel(); self.monitor = nil
                guard path.status == .satisfied else {
                    self.message = "未检测到可用的 Wi-Fi，请连接家庭 Wi-Fi 后重试。"
                    self.finish(); return
                }
                self.wifiAvailable = true
                let names = Set(path.availableInterfaces.filter { $0.type == .wifi }.map(\.name))
                self.addresses = Self.localAddresses(interfaces: names)
                self.gateways = Array(Set(path.gateways.compactMap { endpoint -> String? in
                    guard case let .hostPort(host, _) = endpoint else { return nil }
                    let address = String(describing: host)
                    return HomeNetworkSettings.ipv4(address) != nil ? address : nil
                })).sorted()
                self.fetchSSID(token: token)
            }
        }
        monitor.start(queue: DispatchQueue(label: "app.nestlink.read-wifi"))
        armTimeout(token: token)
    }
    func authorizeSSID() {
        guard wifiAvailable, !reading else { return }
        guard location.authorizationStatus == .notDetermined else {
            message = "请在系统设置中允许精确位置以读取 Wi-Fi 名称，或手动填写。"; return
        }
        pendingAuthorization = true; reading = true
        location.requestWhenInUseAuthorization()
    }
    nonisolated func locationManagerDidChangeAuthorization(_ manager: CLLocationManager) {
        Task { @MainActor [weak self] in self?.authorizationChanged() }
    }
    private func authorizationChanged() {
        let manager = location
        canRequestLocation = manager.authorizationStatus == .notDetermined
        guard pendingAuthorization, manager.authorizationStatus != .notDetermined else { return }
        pendingAuthorization = false
        if (manager.authorizationStatus == .authorizedWhenInUse || manager.authorizationStatus == .authorizedAlways),
           manager.accuracyAuthorization == .fullAccuracy {
            let token = generation
            armTimeout(token: token); fetchSSID(token: token)
        } else {
            message = "请在系统设置中允许精确位置以读取 Wi-Fi 名称，或手动填写。"
            finish()
        }
    }
    private func fetchSSID(token: UUID) {
        NEHotspotNetwork.fetchCurrent { [weak self] network in
            Task { @MainActor [weak self] in
                guard let self, self.generation == token, self.reading else { return }
                if let name = network?.ssid, !name.isEmpty {
                    self.ssid = name; self.message = nil
                } else {
                    self.message = "Wi-Fi 名称暂不可用；可以授权读取，或保留手动填写。IP 和网关信息不受影响。"
                }
                self.finish()
            }
        }
    }
    private func armTimeout(token: UUID) {
        deadline?.cancel()
        deadline = Task { [weak self] in
            do { try await Task.sleep(for: .seconds(8)) } catch { return }
            guard let self, self.generation == token, self.reading else { return }
            self.generation = UUID()
            self.monitor?.cancel(); self.monitor = nil
            self.message = "读取网络信息超时，请重试；已填写的家庭配置不会改变。"
            self.finish()
        }
    }
    private func finish() { deadline?.cancel(); deadline = nil; reading = false }
    func cancel() {
        generation = UUID(); pendingAuthorization = false
        monitor?.cancel(); monitor = nil
        finish()
    }
    private static func localAddresses(interfaces: Set<String>) -> [LocalIPv4Address] {
        var first: UnsafeMutablePointer<ifaddrs>?
        guard getifaddrs(&first) == 0, let first else { return [] }
        defer { freeifaddrs(first) }
        var values: [LocalIPv4Address] = []
        var cursor: UnsafeMutablePointer<ifaddrs>? = first
        while let entry = cursor {
            defer { cursor = entry.pointee.ifa_next }
            let item = entry.pointee
            let name = String(cString: item.ifa_name)
            guard interfaces.contains(name), let address = item.ifa_addr,
                  address.pointee.sa_family == UInt8(AF_INET), let mask = item.ifa_netmask else { continue }
            func string(_ pointer: UnsafeMutablePointer<sockaddr>) -> String? {
                var buffer = [CChar](repeating: 0, count: Int(INET_ADDRSTRLEN))
                var ip = UnsafeRawPointer(pointer).assumingMemoryBound(to: sockaddr_in.self).pointee.sin_addr
                guard inet_ntop(AF_INET, &ip, &buffer, socklen_t(INET_ADDRSTRLEN)) != nil else { return nil }
                return String(cString: buffer)
            }
            if let ip = string(address), let subnet = string(mask) {
                values.append(LocalIPv4Address(interface: name, address: ip, mask: subnet))
            }
        }
        return values.sorted { $0.id < $1.id }
    }
}
