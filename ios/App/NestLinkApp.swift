// SPDX-License-Identifier: GPL-3.0-or-later
import SwiftUI

@main
struct NestLinkApp: App {
    var body: some Scene { WindowGroup { HomeView() } }
}

let nestCream = Color(red: 1, green: 0.984, blue: 0.929)
let nestForest = Color(red: 0.349, green: 0.416, blue: 0.188)

struct HomeView: View {
    @StateObject private var vpn = VPNController()
    @State private var showSettings = false
    @State private var showGuide = false
    @AppStorage("language") private var language = "en"
    @AppStorage("developer") private var developer = false
    @Environment(\.horizontalSizeClass) private var sizeClass
    private var detail: String {
        guard vpn.active else { return tr("通过 NAS 访问家庭网络与互联网。", language) }
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
                    HStack(spacing: 12) {
                        Image("ChickenBadge").resizable().scaledToFit().frame(width: sizeClass == .regular ? 104 : 72, height: sizeClass == .regular ? 114 : 82).accessibilityHidden(true)
                        Text(language == "zh" ? "窝窝连" : "NestLink").font(.ink(sizeClass == .regular ? 42 : 34, relativeTo: .title))
                        Spacer()
                        Button { showSettings = true } label: {
                            Image(systemName: "gearshape").font(.system(size: 28, weight: .medium)).frame(width: 48, height: 48)
                        }.accessibilityLabel(tr("连接设置", language)).disabled(vpn.busy)
                    }
                    Text(tr("连接你的家庭网络", language)).font(.ink(42, relativeTo: .title)).lineLimit(2).minimumScaleFactor(0.8).frame(maxWidth: .infinity, alignment: .leading)
                    SketchUnderline().stroke(sketchAccent, style: StrokeStyle(lineWidth: 5, lineCap: .round)).frame(width: 300, height: 12).accessibilityHidden(true)
                    VStack(spacing: 14) {
                        Text((vpn.verified ? "✓  " : "")+tr(vpn.title, language)).font(.ink(22, relativeTo: .title2)).multilineTextAlignment(.center).padding(.horizontal, 20).padding(.vertical, 10).stickerSurface(vpn.verified ? sketchGreen : Color(red: 0.933, green: 0.910, blue: 0.855))
                        if vpn.active && !vpn.verified { Text(detail).foregroundStyle(.secondary).multilineTextAlignment(.center).frame(maxWidth: 480) }
                    }.padding(.top, 4)
                    Button {
                        if vpn.active { vpn.disconnect() }
                        else if vpn.settings.network.isEmpty || vpn.settings.gateway.isEmpty { showSettings = true }
                        else { vpn.connect() }
                    } label: {
                        Label(tr(vpn.active ? (vpn.status == .connecting ? "取消连接" : "断开连接") : "连接", language), systemImage: vpn.active ? "power" : "network")
                            .font(.ink(24, relativeTo: .title3)).foregroundStyle(sketchInk).frame(maxWidth: .infinity).padding(.vertical, 26)
                    }
                    .buttonStyle(SketchPressStyle()).stickerSurface(sketchAccent)
                    .disabled(vpn.busy || vpn.status == .disconnecting).frame(maxWidth: 500)
                    if let error = vpn.error {
                        VStack(alignment: .leading, spacing: 10) {
                            Text(tr("连接未完成，请检查设置后重试。", language)).font(.callout)
                            DisclosureGroup(tr("查看原因", language)) { Text(tr(error, language)).font(.footnote).textSelection(.enabled) }
                        }.foregroundStyle(.red).padding().frame(maxWidth: .infinity, alignment: .leading)
                            .background(.red.opacity(0.05), in: RoundedRectangle(cornerRadius: 12))
                    }
                    VStack(spacing: 10) {
                        statusCard("家庭网络", icon: "externaldrive", key: "lanReady")
                        Divider().overlay(sketchInk.opacity(0.15))
                        statusCard("互联网", icon: "globe", key: "internetReady")
                    }.padding(18).stickerSurface(Color(red: 1, green: 0.993, blue: 0.965))
                    if vpn.snapshot["phase"] as? String == "authorization", !vpn.node.isEmpty {
                        VStack(spacing: 10) {
                            Text(tr("请授权此设备 ID", language)).font(.ink(20, relativeTo: .headline))
                            Text(vpn.node).font(.system(.title2, design: .monospaced)).textSelection(.enabled)
                            Button(tr("复制设备 ID", language)) { UIPasteboard.general.string = vpn.node }
                        }.padding(20).frame(maxWidth: .infinity).stickerSurface(Color(red: 1, green: 0.993, blue: 0.965))
                    }
                    Button { showSettings = true } label: {
                        VStack(alignment: .leading, spacing: 12) {
                            Text(tr("当前网络", language)).font(.ink(15)).foregroundStyle(.secondary)
                            HStack(spacing: 16) {
                                Image(systemName: "externaldrive").font(.system(size: 30))
                                VStack(alignment: .leading, spacing: 6) {
                                    Text(tr("家庭 NAS", language)).font(.ink(21))
                                    Text(tr(vpn.settings.network.isEmpty ? "尚未设置" : "通过 ZeroTier 安全连接", language)).font(.ink(15)).foregroundStyle(.secondary)
                                }
                                Spacer()
                                Image(systemName: "chevron.right")
                            }
                        }.padding(22).frame(maxWidth: .infinity, alignment: .leading).stickerSurface(Color(red: 1, green: 0.993, blue: 0.965))
                    }.buttonStyle(SketchPressStyle())
                    DisclosureGroup(isExpanded: $showGuide) {
                        connectionGuide
                    } label: {
                        Label(tr("连接指南", language), systemImage: "book").font(.ink(20, relativeTo: .headline))
                    }.padding(18).stickerSurface(Color(red: 1, green: 0.993, blue: 0.965))
                    .onChange(of: vpn.snapshot["phase"] as? String) { _, phase in
                        if phase == "authorization" || phase == "degraded" { showGuide = true }
                    }
                    if developer { DeveloperPanel(vpn: vpn, language: language) }
                    if developer { Text(tr("开发者模式 · NestLink iPad", language)).font(.footnote).foregroundStyle(.secondary) }
                }.padding(24).frame(maxWidth: 780).frame(maxWidth: .infinity)
            }
            .background(nestCream).foregroundStyle(sketchInk)
             .navigationTitle("").navigationBarTitleDisplayMode(.inline)
            .toolbar(.hidden, for: .navigationBar)
            .sheet(isPresented: $showSettings) { SettingsView(vpn: vpn) }
        }.preferredColorScheme(.light).tint(nestForest).environment(\.font, .ink(17)).environment(\.locale, Locale(identifier: language == "zh" ? "zh-Hans" : "en"))
    }
    private var connectionGuide: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text(tr("配置、授权与故障排查", language)).font(.subheadline)
            let phase = vpn.snapshot["phase"] as? String
            if phase == "authorization" {
                Text(tr("在 ZeroTier 管理端的成员列表找到此设备 ID，勾选授权，并确认已分配 IPv4。", language))
            } else if phase == "degraded" {
                if vpn.snapshot["lanReady"] as? Bool != true {
                    Text(tr("NAS 不可达：确认 NAS 在线、处于同一 ZeroTier 网络，网关 IPv4 和检测端口正确。", language))
                }
                if vpn.snapshot["internetReady"] as? Bool != true {
                    Text(tr("互联网检测失败：检查 NAS 的 mihomo、DNS 和出口配置。可先在浏览器测试内网地址。", language))
                }
                Text(tr("网络变化后会自动重试。持续失败时，断开后检查设置，再重新连接。", language))
            } else {
                Text(tr("1. NAS 加入同一 ZeroTier 网络，并启用 mihomo 网关。", language))
                Text(tr("2. 扫码或填写网络 ID、NAS 虚拟 IPv4 和检测端口。", language))
                Text(tr("3. 允许系统 VPN 配置，并在 ZeroTier 管理端授权此设备。", language))
                if vpn.error != nil { Text(tr("连接失败时，先检查网络 ID 和设备授权，再检查 NAS 网关与检测端口。", language)) }
            }
            if !vpn.active { Button(tr("配置网络", language)) { showSettings = true } }
        }.font(.subheadline).foregroundStyle(sketchInk).frame(maxWidth: .infinity, alignment: .leading)
            .padding(20).stickerSurface(Color(red: 1, green: 0.993, blue: 0.965))
    }
    private func statusCard(_ title: String, icon: String, key: String) -> some View {
        let ready = vpn.fresh && vpn.snapshot[key] as? Bool == true
        let phase = vpn.snapshot["phase"] as? String
        let label = ready ? "可用" : !vpn.active ? "未连接" : phase == "degraded" ? "连接待恢复" : "检测中"
        return HStack(spacing: 14) {
            Text(tr(title, language)).font(.ink(20, relativeTo: .headline))
            Spacer()
            Image(systemName: ready ? "checkmark.circle.fill" : "minus.circle").font(.system(size: 24)).foregroundStyle(ready ? nestForest : .secondary)
            Text(tr(label, language)).font(.ink(18))
        }.padding(.vertical, 10)
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
            Text(tr("开发者工具", language)).font(.ink(20, relativeTo: .headline))
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
        }.textSelection(.enabled).padding(22).frame(maxWidth: .infinity, alignment: .leading).stickerSurface(Color(red: 1, green: 0.993, blue: 0.965))
    }
}
