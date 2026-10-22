// SPDX-License-Identifier: GPL-3.0-or-later
import Foundation
import NetworkExtension

// Explicitly uses the provider's virtual interface, never a physical-network fallback.
// These APIs also support our iPadOS 17 deployment target.
final class TunnelProbe {
    private let queue: DispatchQueue
    private var connection: NWTCPConnection?
    private var observation: NSKeyValueObservation?
    private var timeout: DispatchWorkItem?
    private var completion: ((Bool) -> Void)?
    private let https: Bool
    private var response = Data()
    private var sent = false

    init(provider: NEPacketTunnelProvider, host: String, port: String, https: Bool,
         queue: DispatchQueue, completion: @escaping (Bool) -> Void) {
        self.queue = queue; self.https = https; self.completion = completion
        let endpoint = NWHostEndpoint(hostname: host, port: port)
        let connection = provider.createTCPConnectionThroughTunnel(to: endpoint, enableTLS: https, tlsParameters: nil, delegate: nil)
        self.connection = connection
        observation = connection.observe(\.state, options: [.initial, .new]) { [weak self] _, _ in
            guard let self else { return }; self.queue.async { self.stateChanged() }
        }
        let deadline = DispatchWorkItem { [weak self] in self?.finish(false) }
        timeout = deadline; queue.asyncAfter(deadline: .now() + 12, execute: deadline)
    }
    private func stateChanged() {
        guard let connection, completion != nil else { return }
        switch connection.state {
        case .connected:
            if !https { finish(true); return }
            guard !sent else { return }; sent = true
            let request = Data("GET /generate_204 HTTP/1.1\r\nHost: www.google.com\r\nConnection: close\r\n\r\n".utf8)
            connection.write(request) { [weak self] error in
                guard let self else { return }; self.queue.async {
                    if error != nil { self.finish(false) } else { self.readResponse() }
                }
            }
        case .disconnected, .cancelled: finish(false)
        default: break
        }
    }
    private func readResponse() {
        connection?.readMinimumLength(1, maximumLength: 4096) { [weak self] data, error in
            guard let self else { return }; self.queue.async {
                guard self.completion != nil else { return }
                guard error == nil, let data, !data.isEmpty else { self.finish(false); return }
                self.response.append(data)
                if let text = String(data: self.response, encoding: .utf8), let end = text.range(of: "\r\n") {
                    let parts = text[..<end.lowerBound].split(separator: " ")
                    self.finish(parts.count >= 2 && parts[0].hasPrefix("HTTP/") && parts[1] == "204")
                } else if self.response.count >= 8192 { self.finish(false) }
                else { self.readResponse() }
            }
        }
    }
    func cancel() { finish(false) }
    private func finish(_ success: Bool) {
        guard let done = completion else { return }; completion = nil
        timeout?.cancel(); timeout = nil; observation = nil
        connection?.cancel(); connection = nil; done(success)
    }
}
