// SPDX-License-Identifier: GPL-3.0-or-later
import SwiftUI

struct SettingsView: View {
    @ObservedObject var vpn: VPNController
    @Environment(\.dismiss) private var dismiss
    @AppStorage("language") private var language = "en"
    @AppStorage("developer") private var developer = false
    @State private var draft = ConnectionSettings()
    @State private var error: String?
    @State private var scanning = false
    @State private var showingCode = false
    @State private var code = ""
    @State private var pending: QRPayload?
    @State private var scanned: QRPayload?
    @State private var loaded = false
    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 18) {
                    HStack {
                        Text(tr("语言", language)).font(.ink(20, relativeTo: .headline))
                        Spacer()
                        Picker(tr("语言", language), selection: $language) {
                            Text("English").tag("en"); Text("简体中文").tag("zh")
                        }.labelsHidden()
                    }.padding(14).stickerSurface(Color(red: 1, green: 0.993, blue: 0.965))
                    HStack(spacing: 12) {
                        Button { scanning = true } label: {
                            Label(tr("扫码入网", language), systemImage: "qrcode.viewfinder").frame(maxWidth: .infinity)
                        }.disabled(vpn.active)
                        Button {
                            do { code = try QRPayload.encode(draft); showingCode = true }
                            catch { self.error = error.localizedDescription }
                        } label: { Label(tr("显示入网码", language), systemImage: "qrcode").frame(maxWidth: .infinity) }
                    }
                    VStack(alignment: .leading, spacing: 8) {
                        Text(tr("16 位 ZeroTier 网络 ID", language)).font(.ink(17, relativeTo: .subheadline))
                        TextField(tr("16 位 ZeroTier 网络 ID", language), text: $draft.network)
                        Text(tr("NAS 虚拟 IPv4", language)).font(.ink(17, relativeTo: .subheadline)).padding(.top, 8)
                        TextField(tr("NAS 的 ZeroTier IPv4 地址", language), text: $draft.gateway).keyboardType(.decimalPad)
                        Text(tr("NAS 检测端口", language)).font(.ink(17, relativeTo: .subheadline)).padding(.top, 8)
                        TextField(tr("NAS 检测端口", language), text: $draft.port).keyboardType(.numberPad)
                    }.disabled(vpn.active)
                    NavigationLink {
                        MoonManagerView(text: $draft.moons, vpn: vpn)
                    } label: {
                        HStack {
                            Text(tr("Moon 辅助节点", language)); Spacer()
                            Text("\((try? MoonEntry.parse(draft.moons).count) ?? 0)")
                            Image(systemName: "chevron.right")
                        }.padding(18).stickerSurface(Color(red: 1, green: 0.993, blue: 0.965))
                    }.buttonStyle(.plain)
                    Toggle(tr("开发者模式", language), isOn: $developer)
                        .onChange(of: developer) { _, enabled in if !enabled { vpn.snapshot.removeValue(forKey: "peers") } }
                        .padding(18).stickerSurface(Color(red: 1, green: 0.993, blue: 0.965))
                    DisclosureGroup(tr("连接指南", language)) {
                        VStack(alignment: .leading, spacing: 10) {
                            Text(tr("1. NAS 加入同一 ZeroTier 网络，并启用 mihomo 网关。", language))
                            Text(tr("2. 扫码或填写网络 ID、NAS 虚拟 IPv4 和检测端口。", language))
                            Text(tr("3. 允许系统 VPN 配置，并在 ZeroTier 管理端授权此设备。", language))
                            Text(tr("二维码只填入设置；不会自动连接，也不替代 Controller 授权。", language))
                            Text(tr("Moon 帮助节点发现，不自动成为 NAS 出口。", language))
                        }.font(.subheadline).padding(.top, 12)
                    }.padding(18).stickerSurface(Color(red: 1, green: 0.993, blue: 0.965))
                    Text(tr("连接后 IPv4 流量和 DNS 经过 NAS，当前不转发 IPv6。首次出口检测持续失败会自动断开。", language)).font(.footnote).foregroundStyle(.secondary)
                    Text("NestLink 0.7.0 · GPL-3.0-or-later").font(.footnote).foregroundStyle(.secondary)
                    if let error { Text(tr(error, language)).foregroundStyle(.red) }
                }.padding(22).frame(maxWidth: 680).frame(maxWidth: .infinity)
            }
            .scrollContentBackground(.hidden).background(nestCream).buttonStyle(SketchSettingsButtonStyle()).textFieldStyle(SketchFieldStyle())
            .textInputAutocapitalization(.never).autocorrectionDisabled()
            .navigationTitle(tr("连接设置", language)).navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button(tr("取消", language)) { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button(tr("保存", language)) {
                        do {
                            try draft.validate(); draft.moons = try MoonEntry.canonical(draft.moons); vpn.settings = draft
                            UserDefaults.standard.set(draft.dictionary, forKey: "connection"); dismiss()
                        } catch { self.error = error.localizedDescription }
                    }.disabled(vpn.active)
                }
            }
            .onAppear { if !loaded { draft = vpn.settings; loaded = true } }
            .sheet(isPresented: $scanning, onDismiss: { pending = scanned; scanned = nil }) {
                ScanJoinView(language: language) { payload in scanned = payload; scanning = false }
            }
            .sheet(isPresented: $showingCode) { JoinCodeView(payload: code, language: language) }
            .alert(tr("确认入网信息", language), isPresented: Binding(get: { pending != nil }, set: { if !$0 { pending = nil } })) {
                Button(tr("填入设置", language)) { if let pending { draft = pending.applying(to: draft) }; pending = nil }
                Button(tr("取消", language), role: .cancel) { pending = nil }
            } message: {
                if let pending {
                    Text("\(tr("ZeroTier 网络", language)): \(pending.network)\n\(tr("NAS 网关", language)): \(pending.gateway ?? tr("保留现有设置", language))\n\(tr("Moon 辅助节点", language)): \(pending.moons.map { String((try? MoonEntry.parse($0).count) ?? 0) } ?? tr("保留现有设置", language))\n\n\(tr("请确认后填入设置，再保存和连接。", language))")
                }
            }
        }.tint(nestForest)
    }
}

struct MoonManagerView: View {
    @Binding var text: String
    @ObservedObject var vpn: VPNController
    @AppStorage("language") private var language = "en"
    @AppStorage("developer") private var developer = false
    @State private var world = ""
    @State private var seed = ""
    @State private var error: String?
    private var entries: [MoonEntry] { (try? MoonEntry.parse(text)) ?? [] }
    private func state(_ entry: MoonEntry) -> (String, Bool, Int) {
        guard vpn.active else { return ("未连接", false, -1) }
        guard vpn.snapshotFresh, let states = vpn.snapshot["moons"] as? [String: [String: Any]], let result = states[entry.world] else { return ("检测中", false, -1) }
        let key = result["state"] as? String ?? ""
        let label = ["online": "连接正常", "undiscovered": "尚未发现", "notMoon": "尚未确认为 Moon", "waiting": "等待当前网络回包", "unavailable": "暂无可用路径"][key] ?? "检测中"
        return (label, key == "online", result["latency"] as? Int ?? -1)
    }
    var body: some View {
        Form {
            Section(tr("已配置的节点", language)) {
                if entries.isEmpty { Text(tr("尚未配置 Moon", language)).foregroundStyle(.secondary) }
                ForEach(entries) { entry in
                    let status = state(entry)
                    HStack {
                        VStack(alignment: .leading, spacing: 7) {
                            Text(entry.display).font(.system(.body, design: .monospaced))
                            Label(tr(status.0, language), systemImage: status.1 ? "checkmark.circle.fill" : "circle.dotted")
                                .foregroundStyle(status.1 ? nestForest : .secondary).font(.caption)
                            if status.2 >= 0 { Text("\(status.2) ms").font(.caption).foregroundStyle(.secondary) }
                            if developer { Text("World: \(entry.world)\nSeed: \(entry.seed)").font(.system(.caption, design: .monospaced)).textSelection(.enabled) }
                        }
                        Spacer()
                        Button(role: .destructive) { text = entries.filter { $0.id != entry.id }.map(\.line).joined(separator: "\n") } label: {
                            Label(tr("移除", language), systemImage: "trash").labelStyle(.iconOnly)
                        }.disabled(vpn.active)
                    }
                }
            }
            Section {
                TextField("Moon ID", text: $world)
                if developer { TextField(tr("Seed ID（可选）", language), text: $seed) }
                Button(tr("添加辅助节点", language)) {
                    do {
                        let extra = world.trimmingCharacters(in: .whitespacesAndNewlines) + (developer && !seed.isEmpty ? " " + seed : "")
                        text = try MoonEntry.canonical(text + "\n" + extra); world = ""; seed = ""; error = nil
                    } catch { self.error = error.localizedDescription }
                }.disabled(world.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
            } header: { Text(tr("添加 Moon", language)) }
            footer: { Text(tr("最多 8 个节点。完整 World 与自定义 Seed 可在开发者模式中配置。", language)) }
            .disabled(vpn.active)
            if let error { Section { Text(tr(error, language)).foregroundStyle(.red) } }
        }.scrollContentBackground(.hidden).background(nestCream).buttonStyle(SketchSettingsButtonStyle()).textFieldStyle(SketchFieldStyle()).navigationTitle(tr("Moon 辅助节点", language))
            .scrollContentBackground(.hidden).background(nestCream).buttonStyle(SketchSettingsButtonStyle()).textFieldStyle(SketchFieldStyle())
            .textInputAutocapitalization(.never).autocorrectionDisabled()
    }
}
