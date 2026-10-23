// SPDX-License-Identifier: GPL-3.0-or-later
import SwiftUI

struct HomeRoutingSettingsView: View {
    @Binding var home: HomeNetworkSettings
    @ObservedObject var vpn: VPNController
    @AppStorage("language") private var language = "en"
    @StateObject private var networkReader = CurrentNetworkReader()
    var body: some View {
        Form {
            Section {
                Toggle(tr("在家直连，外出自动连接", language), isOn: $home.enabled)
                    .accessibilityIdentifier("homeRouting.enabled").disabled(vpn.serviceEnabled)
            } footer: {
                Text(tr("开启后，首页按钮用于开启或暂停自动切换。保存设置不会立即连接。", language))
            }
            if home.enabled {
                Section {
                    Button { networkReader.read() } label: {
                        HStack {
                            Label(tr("读取当前网络", language), systemImage: "wifi")
                            Spacer()
                            if networkReader.reading { ProgressView() }
                        }
                    }.accessibilityIdentifier("homeRouting.readNetwork")
                        .disabled(networkReader.reading || vpn.serviceEnabled)
                    if let ssid = networkReader.ssid {
                        LabeledContent(tr("当前 Wi-Fi", language), value: ssid).textSelection(.enabled)
                        if home.ssid != ssid {
                            Button(tr("使用此 Wi-Fi 名称", language)) {
                                home.ssid = ssid; home.routerConfigured = false
                            }.disabled(vpn.serviceEnabled)
                        }
                    }
                    ForEach(networkReader.addresses) { address in
                        VStack(alignment: .leading, spacing: 8) {
                            LabeledContent(tr("iPad 局域网 IPv4", language), value: address.address)
                            LabeledContent(tr("子网掩码", language), value: address.mask)
                        }.textSelection(.enabled)
                    }
                    if networkReader.wifiAvailable {
                        LabeledContent(tr("当前 IPv4 网关", language), value: networkReader.gateways.isEmpty ? tr("未获取到", language) : networkReader.gateways.joined(separator: ", "))
                            .textSelection(.enabled)
                    }
                    if let message = networkReader.message {
                        Text(tr(message, language)).font(.callout).foregroundStyle(.secondary)
                            .accessibilityIdentifier("homeRouting.networkReadMessage")
                    }
                    if networkReader.wifiAvailable && networkReader.ssid == nil && networkReader.canRequestLocation {
                        Button(tr("授权读取 Wi-Fi 名称", language)) { networkReader.authorizeSSID() }
                            .disabled(networkReader.reading || vpn.serviceEnabled)
                    }
                } header: { Text(tr("自动填写", language)) }
                footer: {
                    Text(tr("空白的家庭 Wi-Fi 名称会自动填入，已有名称需确认替换。当前网关不一定是 NAS；不会自动修改 NAS 地址或网关设置，也不会持续定位。", language))
                }
                Section {
                    VStack(alignment: .leading, spacing: 8) {
                        Text(tr("家庭 Wi-Fi 名称（SSID）", language)).font(.headline)
                        TextField(tr("与系统 Wi-Fi 列表中的名称完全一致", language), text: $home.ssid)
                            .accessibilityIdentifier("homeRouting.ssid")
                    }.padding(.vertical, 4)
                    VStack(alignment: .leading, spacing: 8) {
                        Text(tr("NAS 局域网 IPv4", language)).font(.headline)
                        TextField("192.168.1.66", text: $home.nas).keyboardType(.decimalPad)
                            .accessibilityIdentifier("homeRouting.nas")
                    }.padding(.vertical, 4)
                    LabeledContent(tr("NAS SOCKS5 代理端口", language)) {
                        TextField("7890", text: $home.port).keyboardType(.numberPad).multilineTextAlignment(.trailing)
                            .accessibilityIdentifier("homeRouting.port")
                    }
                } header: { Text(tr("家庭网络", language)) }
                footer: {
                    Text(tr("Wi-Fi 名称区分大小写。NAS 地址填写家中局域网地址，不是 ZeroTier 地址。此版本支持同一子网的 NAS 检测。", language))
                }.disabled(vpn.serviceEnabled)
                Section {
                    Text(tr("连接后会自动检测 NAS 代理和互联网；在家不启动 ZeroTier，无需修改 Wi-Fi 网关或 DNS。", language))
                } header: { Text(tr("连通性检测", language)) }

            }
            Section(tr("连接方式", language)) {
                Text(tr("家庭 Wi-Fi 名称匹配时，通过本地 VPN 直连 NAS 的 SOCKS5 代理，保留系统 VPN 图标，不启动 ZeroTier。", language))
                Text(tr("离开家庭 Wi-Fi 后自动切换到原来的 ZeroTier 通路；回家后恢复 NAS 直连。", language))
                Text(tr("NAS 需要提供可供局域网访问的 SOCKS5 或混合代理端口。无需固定 iPad 的 IP，也无需修改路由器。", language))
                Text(tr("无法读取或匹配 Wi-Fi 名称时使用 ZeroTier。家庭代理故障会提示错误，不会把普通直连误报为代理可用。", language))
                Text(tr("家庭 Wi-Fi 配置仅保存在本设备，不随入网码分享。", language)).foregroundStyle(.secondary)
            }.font(.callout)
        }
        .font(.body).foregroundStyle(sketchInk).scrollContentBackground(.hidden).background(nestCream)
        .scrollDismissesKeyboard(.immediately).textInputAutocapitalization(.never).autocorrectionDisabled()
        .navigationTitle(tr("在家与外出", language)).navigationBarTitleDisplayMode(.inline)
        .onChange(of: networkReader.ssid) { _, name in
            if let name, home.ssid.isEmpty, !vpn.serviceEnabled {
                home.ssid = name; home.routerConfigured = false
            }
        }
        .onChange(of: home.enabled) { _, enabled in if !enabled { networkReader.cancel() } }
        .onDisappear { networkReader.cancel() }
    }
}
