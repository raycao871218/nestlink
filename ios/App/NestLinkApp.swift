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
    @State private var showDiagnostics = false
    @State private var showError = false
    @AppStorage("language") private var language = "en"
    @AppStorage("developer") private var developer = false
    @Environment(\.scenePhase) private var scenePhase
    @Environment(\.dynamicTypeSize) private var typeSize
    private var detail: String {
        if vpn.homeReady { return tr("家庭直连 · 通过局域网 NAS 代理上网，ZeroTier 未运行。", language) }
        if vpn.automaticEnabled && vpn.verified {
            return tr("外出模式 · 通过 ZeroTier 访问家庭 NAS。", language)
        }
        if vpn.automaticEnabled && !vpn.active {
            return tr("系统会在家庭 Wi-Fi 直连 NAS，在其他网络使用 ZeroTier。", language)
        }
        if vpn.settings.home.enabled && !vpn.serviceEnabled {
            return tr("开启后，在家直连 NAS 代理，外出通过 ZeroTier 回家。", language)
        }
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
                let wide = geometry.size.width >= 900 && geometry.size.width > geometry.size.height
                let compact = geometry.size.height < 650 || typeSize.isAccessibilitySize
                VStack(spacing: compact ? 16 : 32) {
                    header
                    if wide && typeSize.isAccessibilitySize {
                        HStack(spacing: 32) {
                            connection(compact: true).frame(maxWidth: .infinity, maxHeight: .infinity)
                            VStack(spacing: 16) {
                                statusCard("家庭网络", icon: "externaldrive", key: "lanReady")
                                statusCard("互联网", icon: "globe", key: "internetReady")
                            }.frame(maxWidth: .infinity)
                        }
                    } else if wide {
                        HStack(spacing: 56) {
                            hero(compact: compact).frame(maxWidth: .infinity, maxHeight: .infinity)
                            connection(compact: compact).frame(maxWidth: 440, maxHeight: .infinity)
                        }
                    } else {
                        VStack(spacing: compact ? 16 : 32) {
                            Spacer(minLength: 0)
                            if !typeSize.isAccessibilitySize { hero(compact: compact) }
                            connection(compact: compact).frame(maxWidth: 560)
                            Spacer(minLength: 0)
                        }.frame(maxWidth: .infinity, maxHeight: .infinity)
                    }
                    if !(wide && typeSize.isAccessibilitySize) { statusCards }
                }
                .padding(geometry.size.width < 600 ? 24 : 40)
                .frame(maxWidth: 1160, maxHeight: 1040)
                .background(Image("ApprovedFrame").resizable(capInsets: EdgeInsets(top: 22, leading: 22, bottom: 22, trailing: 22)).allowsHitTesting(false))
                .padding(geometry.size.width < 600 ? 12 : 24)
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .background(nestCream).foregroundStyle(sketchInk)
            }
            .onAppear { vpn.setForeground(true) }
            .onChange(of: scenePhase) { _, phase in
                if phase == .active { vpn.setForeground(true) }
                else if phase == .background { vpn.setForeground(false) }
            }
            .toolbar(.hidden, for: .navigationBar)
            .sheet(isPresented: $showSettings) { SettingsView(vpn: vpn) }
            .sheet(isPresented: $showDiagnostics) {
                NavigationStack {
                    ScrollView { DeveloperPanel(vpn: vpn, language: language).padding(24) }.background(nestCream)
                        .navigationTitle(tr("开发者工具", language)).navigationBarTitleDisplayMode(.inline)
                        .toolbar { ToolbarItem(placement: .confirmationAction) { Button(tr("关闭", language)) { showDiagnostics = false } } }
                }
            }
            .alert(tr("查看原因", language), isPresented: $showError) {
                Button(tr("关闭", language), role: .cancel) { }
            } message: { Text(tr(vpn.error ?? "", language)) }
        }.preferredColorScheme(.light).tint(nestForest)
            .environment(\.font, .ink(17))
            .environment(\.locale, Locale(identifier: language == "zh" ? "zh-Hans" : "en"))
    }
    private var header: some View {
        HStack(spacing: 12) {
            Image("ApprovedChicken").resizable().scaledToFit().frame(width: 44, height: 48).accessibilityHidden(true)
            if language == "zh" {
                Image("ApprovedBrand").resizable().scaledToFit().frame(width: 112, height: 40).accessibilityLabel("窝窝连")
            } else { Text("NestLink").font(.ink(28)).dynamicTypeSize(.large) }
            Spacer(minLength: 12)
            if developer {
                Button { showDiagnostics = true } label: { Image(systemName: "stethoscope").font(.title2).frame(width: 48, height: 48) }
                    .accessibilityLabel(tr("开发者工具", language)).accessibilityIdentifier("home.diagnostics")
            }
            Button { showSettings = true } label: { ApprovedSymbol(name: "Gear", size: 32).frame(width: 48, height: 48) }
                .accessibilityLabel(tr("连接设置", language)).accessibilityIdentifier("home.settings").disabled(vpn.busy)
        }.buttonStyle(.plain)
    }
    private func hero(compact: Bool) -> some View {
        VStack(spacing: compact ? 12 : 24) {
            if !compact {
                Image("ApprovedChicken").resizable().scaledToFit().frame(height: 170).accessibilityHidden(true)
            }
            if language == "zh" && !typeSize.isAccessibilitySize {
                Image("ApprovedHeading").resizable().scaledToFit().frame(maxWidth: 420).frame(height: compact ? 64 : 86).accessibilityLabel("连接你的家庭网络")
            } else { Text(tr("连接你的家庭网络", language)).font(.ink(compact ? 26 : 34, relativeTo: .title)).multilineTextAlignment(.center).fixedSize(horizontal: false, vertical: true) }
        }
    }
    private func connection(compact: Bool) -> some View {
        VStack(spacing: compact ? 12 : 20) {
            HStack(spacing: 12) {
                if vpn.active && !vpn.verified && vpn.status != .disconnecting && vpn.snapshot["phase"] as? String != "authorization" {
                    ProgressView().tint(sketchInk).accessibilityHidden(true)
                }
                Text(tr(vpn.title, language)).font(.ink(vpn.connectionVerified ? 28 : 24, relativeTo: .title2))
            }
            .padding(.horizontal, vpn.connectionVerified ? 32 : 20)
            .padding(.vertical, vpn.connectionVerified ? 18 : 10)
            .frame(minWidth: vpn.connectionVerified ? 240 : nil, minHeight: vpn.connectionVerified ? 80 : nil)
            .background {
                if vpn.connectionVerified {
                    Image("ApprovedSuccess")
                        .resizable(capInsets: EdgeInsets(top: 30, leading: 46, bottom: 30, trailing: 46))
                        .allowsHitTesting(false)
                }
            }
            Text(detail).font(.body).foregroundStyle(.secondary).multilineTextAlignment(.center).fixedSize(horizontal: false, vertical: true)
            Button {
                if vpn.serviceEnabled { vpn.disconnect() }
                else if vpn.settings.network.isEmpty || vpn.settings.gateway.isEmpty { showSettings = true }
                else { vpn.connect() }
            } label: {
                HStack(spacing: 14) {
                    ApprovedSymbol(name: "Power", size: 36)
                    ApprovedLabel(text: vpn.automaticEnabled ? "暂停自动切换" : vpn.active ? (vpn.status == .connecting ? "取消连接" : "断开连接") : vpn.settings.home.enabled ? "开启自动切换" : "连接", language: language, height: 32)
                }.frame(maxWidth: .infinity).padding(.vertical, 22).foregroundStyle(sketchInk)
            }.buttonStyle(SketchPressStyle()).stickerSurface(sketchAccent)
                .accessibilityIdentifier("home.connect").disabled(vpn.busy || vpn.status == .disconnecting)
            if vpn.snapshot["phase"] as? String == "authorization", !vpn.node.isEmpty {
                Button { UIPasteboard.general.string = vpn.node } label: {
                    Label(tr("复制设备 ID", language) + " · " + vpn.node, systemImage: "doc.on.doc").font(.callout).padding(12)
                }.buttonStyle(.plain).stickerSurface(nestCream)
            }
            if vpn.error != nil {
                Button { showError = true } label: {
                    Label(tr("连接未完成，请检查设置后重试。", language), systemImage: "exclamationmark.circle").font(.callout).multilineTextAlignment(.center)
                }.buttonStyle(.plain).foregroundStyle(.red).frame(minHeight: 44)
            }
        }
    }
    private var statusCards: some View {
        ViewThatFits(in: .horizontal) {
            HStack(spacing: 20) {
                statusCard("家庭网络", icon: "externaldrive", key: "lanReady")
                statusCard("互联网", icon: "globe", key: "internetReady")
            }
            VStack(spacing: 12) {
                statusCard("家庭网络", icon: "externaldrive", key: "lanReady")
                statusCard("互联网", icon: "globe", key: "internetReady")
            }
        }
    }
    private func statusCard(_ title: String, icon: String, key: String) -> some View {
        let ready = vpn.ready(key)
        let phase = vpn.snapshot["phase"] as? String
        let label = ready ? "可用" : vpn.automaticEnabled && !vpn.active ? "尚未确认" : !vpn.active ? "未连接" : phase == "degraded" ? "连接待恢复" : "检测中"
        return HStack(spacing: 14) {
            Image(systemName: icon).font(.title2).foregroundStyle(nestForest).accessibilityHidden(true)
            VStack(alignment: .leading, spacing: 6) {
                Text(key == "lanReady" && !vpn.networkName.isEmpty ? vpn.networkName : tr(title, language)).font(.headline).lineLimit(2)
                Text(tr(label, language)).font(.subheadline).foregroundStyle(.secondary)
            }
            Spacer(minLength: 0)
            if !ready && vpn.active { ProgressView().tint(sketchInk).accessibilityHidden(true) }
            if ready { Image(systemName: "checkmark.circle.fill").foregroundStyle(nestForest).accessibilityHidden(true) }
        }.padding(20).frame(maxWidth: .infinity, alignment: .leading).stickerSurface(nestCream)
            .accessibilityElement(children: .combine).accessibilityIdentifier("home." + key)
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
