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
            GeometryReader { geometry in
            ScrollView {
                VStack(spacing: 24) {
                    HStack(spacing: 12) {
                        Image("ApprovedChicken").resizable().scaledToFit().frame(width: sizeClass == .regular ? 104 : 74, height: sizeClass == .regular ? 114 : 80).accessibilityHidden(true)
                        if language == "zh" {
                            Image("ApprovedBrand").resizable().scaledToFit().frame(width: sizeClass == .regular ? 150 : 112, height: 48).accessibilityLabel("窝窝连")
                        } else { Text("NestLink").font(.ink(30)) }
                        Spacer()
                        Button { showSettings = true } label: {
                            ApprovedSymbol(name: "Gear", size: 38).frame(width: 48, height: 48)
                        }.accessibilityLabel(tr("连接设置", language)).disabled(vpn.busy)
                    }
                    Spacer(minLength: 24)
                    if language == "zh" {
                        Image("ApprovedHeading").resizable().scaledToFit().frame(maxWidth: 480).accessibilityLabel("连接你的家庭网络")
                    } else { Text(tr("连接你的家庭网络", language)).font(.ink(30)).frame(maxWidth: .infinity, alignment: .leading) }
                    VStack(spacing: 12) {
                        HStack(spacing: 12) {
                            if vpn.active && !vpn.verified && vpn.status != .disconnecting && vpn.snapshot["phase"] as? String != "authorization" {
                                ProgressView().tint(sketchInk).accessibilityHidden(true)
                            }
                            Text(tr(vpn.title, language)).font(.ink(22)).padding(.horizontal, 24).padding(.vertical, 16)
                                .background { if vpn.verified { SketchPaper(fill: sketchGreen) } }
                        }
                        if vpn.active && !vpn.verified { Text(detail).foregroundStyle(.secondary).multilineTextAlignment(.center) }
                    }
                    Button {
                        if vpn.active { vpn.disconnect() }
                        else if vpn.settings.network.isEmpty || vpn.settings.gateway.isEmpty { showSettings = true }
                        else { vpn.connect() }
                    } label: {
                        HStack(spacing: 14) {
                            ApprovedSymbol(name: "Power", size: 48)
                            ApprovedLabel(text: vpn.active ? (vpn.status == .connecting ? "取消连接" : "断开连接") : "连接", language: language, height: 40)
                        }.frame(maxWidth: .infinity).padding(.vertical, 26).foregroundStyle(sketchInk)
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
                    Spacer(minLength: 24)
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
                    if developer { DeveloperPanel(vpn: vpn, language: language) }
                    if developer { Text(tr("开发者模式 · NestLink iPad", language)).font(.footnote).foregroundStyle(.secondary) }
                }.padding(20).frame(minHeight: max(0, geometry.size.height - 28)).frame(maxWidth: 780).frame(maxWidth: .infinity)
                    .background(Image("ApprovedFrame").resizable(capInsets: EdgeInsets(top: 22, leading: 22, bottom: 22, trailing: 22)))
                    .padding(14)
            }
            .background(nestCream).foregroundStyle(sketchInk)
             .navigationTitle("").navigationBarTitleDisplayMode(.inline)
            .toolbar(.hidden, for: .navigationBar)
            .sheet(isPresented: $showSettings) { SettingsView(vpn: vpn) }
            }
        }.preferredColorScheme(.light).tint(nestForest).environment(\.font, .ink(17)).environment(\.locale, Locale(identifier: language == "zh" ? "zh-Hans" : "en"))
    }
    private func statusCard(_ title: String, icon: String, key: String) -> some View {
        let ready = vpn.fresh && vpn.snapshot[key] as? Bool == true
        let phase = vpn.snapshot["phase"] as? String
        let label = ready ? "可用" : !vpn.active ? "未连接" : phase == "degraded" ? "连接待恢复" : "检测中"
        let rowFont: Font = language == "zh" ? .custom("Resource-Han-Rounded-CN-Regular", size: 20, relativeTo: .body) : .ink(20)
        return HStack(spacing: 14) {
            Text(key == "lanReady" && !vpn.networkName.isEmpty ? vpn.networkName : tr(title, language)).font(rowFont).lineLimit(2)
            Spacer()
            if !ready && vpn.active { ProgressView().tint(sketchInk).accessibilityHidden(true) }
            Text(tr(label, language)).font(rowFont)
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
