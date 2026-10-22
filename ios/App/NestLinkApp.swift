// SPDX-License-Identifier: GPL-3.0-or-later
import SwiftUI

@main
struct NestLinkApp: App {
    var body: some Scene { WindowGroup { HomeView() } }
}

let nestCream = Color(red: 0.99, green: 0.97, blue: 0.94)
let nestForest = Color(red: 0.23, green: 0.40, blue: 0.33)

struct HomeView: View {
    @StateObject private var vpn = VPNController()
    @State private var showSettings = false
    @AppStorage("language") private var language = "en"
    @AppStorage("developer") private var developer = false
    @Environment(\.horizontalSizeClass) private var sizeClass
    private var detail: String {
        guard vpn.active else { return tr("一个连接，回到家中的网络。", language) }
        switch vpn.snapshot["phase"] as? String {
        case "authorization": return tr("请在 Controller 授权此设备，然后重新连接。", language)
        case "connected" where vpn.verified: return tr("家庭网络与互联网检测通过。", language)
        case "degraded": return tr("连接待恢复，将自动重新检测。", language)
        case "validating", "connected": return tr("正在确认家庭网络与互联网是否可用。", language)
        default: return tr("正在连接家庭网络，首次连接需要授权设备。", language)
        }
    }
    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 24) {
                    VStack(spacing: 14) {
                        Image("Mascot").resizable().scaledToFit().frame(width: 170, height: 170)
                            .scaleEffect(vpn.verified ? 1.04 : 1).animation(.easeInOut(duration: 1), value: vpn.verified).accessibilityHidden(true)
                        Text(tr(vpn.title, language)).font(.system(size: 32, weight: .semibold, design: .rounded)).multilineTextAlignment(.center)
                        Text(detail).foregroundStyle(.secondary).multilineTextAlignment(.center).frame(maxWidth: 480)
                    }.padding(.top, 24)
                    Button {
                        if vpn.active { vpn.disconnect() }
                        else if vpn.settings.network.isEmpty || vpn.settings.gateway.isEmpty { showSettings = true }
                        else { vpn.connect() }
                    } label: {
                        Label(tr(vpn.active ? (vpn.status == .connecting ? "取消连接" : "断开连接") : "连接回家", language), systemImage: vpn.active ? "power" : "house.fill")
                            .font(.title3.weight(.semibold)).foregroundStyle(.white).frame(maxWidth: .infinity).padding(.vertical, 15)
                    }
                    .buttonStyle(.borderedProminent).tint(nestForest).clipShape(RoundedRectangle(cornerRadius: 20))
                    .disabled(vpn.busy || vpn.status == .disconnecting).frame(maxWidth: 340)
                    if let error = vpn.error {
                        VStack(alignment: .leading, spacing: 10) {
                            Text(tr("连接未完成，请检查设置后重试。", language)).font(.callout)
                            DisclosureGroup(tr("查看原因", language)) { Text(tr(error, language)).font(.footnote).textSelection(.enabled) }
                        }.foregroundStyle(.red).padding().frame(maxWidth: .infinity, alignment: .leading)
                            .background(.red.opacity(0.05), in: RoundedRectangle(cornerRadius: 18))
                    }
                    if sizeClass == .regular {
                        HStack(spacing: 16) { statusCard("家庭网络", icon: "externaldrive.connected.to.line.below", key: "lanReady"); statusCard("互联网", icon: "globe", key: "internetReady") }
                    } else {
                        VStack(spacing: 12) { statusCard("家庭网络", icon: "externaldrive.connected.to.line.below", key: "lanReady"); statusCard("互联网", icon: "globe", key: "internetReady") }
                    }
                    if vpn.snapshot["phase"] as? String == "authorization", !vpn.node.isEmpty {
                        VStack(spacing: 10) {
                            Text(tr("请授权此设备 ID", language)).font(.headline)
                            Text(vpn.node).font(.system(.title2, design: .monospaced)).textSelection(.enabled)
                            Button(tr("复制设备 ID", language)) { UIPasteboard.general.string = vpn.node }
                        }.padding(20).frame(maxWidth: .infinity).background(.white, in: RoundedRectangle(cornerRadius: 22))
                    }
                    VStack(spacing: 14) {
                        infoRow("家庭网络", vpn.settings.network.isEmpty ? tr("尚未设置", language) : vpn.settings.network)
                        infoRow("NAS 网关", vpn.settings.gateway.isEmpty ? tr("尚未设置", language) : vpn.settings.gateway)
                        let count = (try? MoonEntry.parse(vpn.settings.moons).count) ?? 0
                        infoRow("Moon 辅助节点", String(count))
                    }.padding(22).background(.white.opacity(0.8), in: RoundedRectangle(cornerRadius: 24))
                    if developer { DeveloperPanel(vpn: vpn, language: language) }
                    Text(tr(developer ? "开发者模式 · NestLink iPad" : "一个连接，回到家中的网络。", language)).font(.footnote).foregroundStyle(.secondary)
                }.padding(24).frame(maxWidth: 780).frame(maxWidth: .infinity)
            }
            .background(nestCream).foregroundStyle(nestForest)
            .navigationTitle(language == "zh" ? "NestLink · 窝窝连" : "NestLink")
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button { showSettings = true } label: { Label(tr("连接设置", language), systemImage: "slider.horizontal.3") }.disabled(vpn.busy)
                }
            }
            .sheet(isPresented: $showSettings) { SettingsView(vpn: vpn) }
        }.tint(nestForest).environment(\.locale, Locale(identifier: language == "zh" ? "zh-Hans" : "en"))
    }
    private func statusCard(_ title: String, icon: String, key: String) -> some View {
        let ready = vpn.fresh && vpn.snapshot[key] as? Bool == true
        let phase = vpn.snapshot["phase"] as? String
        let label = ready ? "检测通过" : !vpn.active ? "未连接" : phase == "degraded" ? "连接待恢复" : "检测中"
        return HStack(spacing: 16) {
            Image(systemName: icon).font(.title2).frame(width: 36)
            VStack(alignment: .leading, spacing: 6) {
                Text(tr(title, language)).font(.headline)
                Text(tr(label, language)).font(.subheadline).foregroundStyle(.secondary)
            }
            Spacer()
            Image(systemName: ready ? "checkmark.circle.fill" : "circle.dotted").foregroundStyle(ready ? nestForest : .secondary)
        }.padding(20).background(ready ? Color.green.opacity(0.09) : .white.opacity(0.8), in: RoundedRectangle(cornerRadius: 22))
    }
    private func infoRow(_ title: String, _ value: String) -> some View {
        LabeledContent(tr(title, language)) { Text(value).font(.system(.callout, design: .monospaced)).textSelection(.enabled) }
    }
}

struct DeveloperPanel: View {
    @ObservedObject var vpn: VPNController
    let language: String
    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            Text(tr("开发者工具", language)).font(.headline)
            LabeledContent(tr("设备 ID", language), value: vpn.node.isEmpty ? "—" : vpn.node)
            LabeledContent(tr("虚拟 IPv4", language), value: vpn.address.isEmpty ? "—" : vpn.address)
            LabeledContent(tr("底层 UDP 发 / 收", language), value: "\(vpn.snapshot["tx"] ?? 0) / \(vpn.snapshot["rx"] ?? 0)")
            LabeledContent(tr("发送错误", language), value: "\(vpn.snapshot["sendErrors"] ?? 0)")
            Button(tr("重新检测连接", language)) { vpn.recheck() }.disabled(vpn.status != .connected)
            if !vpn.node.isEmpty { Button(tr("复制设备 ID", language)) { UIPasteboard.general.string = vpn.node } }
            if vpn.snapshotFresh, let peers = vpn.snapshot["peers"] as? [[String: Any]] {
                ForEach(Array(peers.enumerated()), id: \.offset) { _, peer in
                    VStack(alignment: .leading, spacing: 4) {
                        Text("\(peer["id"] ?? "") · role=\(peer["role"] ?? 0) · \(peer["latency"] ?? -1) ms")
                        ForEach(Array(((peer["paths"] as? [[String: Any]]) ?? []).enumerated()), id: \.offset) { _, path in
                            Text("\(path["endpoint"] ?? "") · rxAge=\(path["rxAge"] ?? -1)s").foregroundStyle(.secondary)
                        }
                    }.font(.system(.caption, design: .monospaced))
                }
            }
            Text(vpn.snapshot["message"] as? String ?? "").font(.caption)
            Text(tr("检测通过只证明指定 NAS 端口和 HTTPS 测试站点当时可用。", language)).font(.caption).foregroundStyle(.secondary)
        }.textSelection(.enabled).padding(22).frame(maxWidth: .infinity, alignment: .leading).background(.white, in: RoundedRectangle(cornerRadius: 24))
    }
}
