// SPDX-License-Identifier: GPL-3.0-or-later
import Foundation
import Darwin

// Public packetFlow APIs are bridged with a datagram socket pair. No private
// utun file descriptor lookup, subprocess, or ZeroTier node is used here.
final class HomeProxyEngine {
    private let queue: DispatchQueue
    private var bridge: Int32 = -1
    private var core: Int32 = -1
    private var source: DispatchSourceRead?
    private var stopped: [() -> Void] = []
    private var stopping = false
    private var started = false
    var packetHandler: ((Data, NSNumber) -> Void)?
    var failureHandler: (() -> Void)?

    init(queue: DispatchQueue) { self.queue = queue }
    func start(_ home: HomeNetworkSettings) throws {
        guard !started else { throw SettingsError.message("家庭代理已经启动") }
        var sockets: [Int32] = [-1, -1]
        guard socketpair(AF_UNIX, SOCK_DGRAM, 0, &sockets) == 0 else {
            throw SettingsError.message("无法创建家庭代理数据通道")
        }
        core = sockets[0]; bridge = sockets[1]; started = true
        for fd in sockets {
            _ = fcntl(fd, F_SETFL, O_NONBLOCK)
            var enabled: Int32 = 1
            _ = setsockopt(fd, SOL_SOCKET, SO_NOSIGPIPE, &enabled, socklen_t(MemoryLayout.size(ofValue: enabled)))
        }
        let source = DispatchSource.makeReadSource(fileDescriptor: bridge, queue: queue)
        source.setEventHandler { [weak self] in self?.receive() }
        self.source = source; source.resume()
        let configuration = """
        tunnel:
          mtu: 1500
          ipv4: 198.18.0.1
        socks5:
          address: '\(home.nas.trimmingCharacters(in: .whitespacesAndNewlines))'
          port: \(UInt16(home.port)!)
          udp: udp
        misc:
          task-stack-size: 24576
          tcp-buffer-size: 32768
          max-session-count: 192
          connect-timeout: 10000
          log-level: error
        """
        let fd = core
        DispatchQueue(label: "app.nestlink.home-proxy.core", qos: .userInitiated).async {
            let bytes = Array(configuration.utf8)
            _ = bytes.withUnsafeBufferPointer { hev_socks5_tunnel_main_from_str($0.baseAddress, UInt32($0.count), fd) }
            self.queue.async {
                let unexpected = !self.stopping
                self.source?.cancel(); self.source = nil
                close(self.core); close(self.bridge)
                self.core = -1; self.bridge = -1; self.started = false
                let callbacks = self.stopped; self.stopped.removeAll()
                callbacks.forEach { $0() }
                if unexpected { self.failureHandler?() }
            }
        }
    }
    func process(_ packets: [Data], protocols: [NSNumber]) {
        guard started, !stopping else { return }
        for (packet, family) in zip(packets, protocols) where family.int32Value == AF_INET {
            var header = UInt32(AF_INET).bigEndian
            var frame = withUnsafeBytes(of: &header) { Data($0) }; frame.append(packet)
            _ = frame.withUnsafeBytes { send(bridge, $0.baseAddress, $0.count, 0) }
        }
    }
    private func receive() {
        var bytes = [UInt8](repeating: 0, count: 65539)
        while bridge >= 0 {
            let count = recv(bridge, &bytes, bytes.count, 0)
            guard count > 4 else { return }
            let family = bytes[3]
            guard family == UInt8(AF_INET) else { continue }
            packetHandler?(Data(bytes[4..<count]), NSNumber(value: AF_INET))
        }
    }
    func stop(_ completion: @escaping () -> Void) {
        guard started else { completion(); return }
        stopped.append(completion)
        guard !stopping else { return }; stopping = true
        // The pinned core synchronizes an early stop with initialization.
        hev_socks5_tunnel_quit()
    }
}
