# iPad 原型验证记录

## Build 15：家庭本地 VPN 直连

替代 Build 13 的手动家庭网关方案。家庭 SSID 匹配时直接使用 NAS SOCKS5，保留 VPN 图标但不创建 ZeroTier 节点；外出沿用原 ZeroTier 引擎。无需固定 iPad IP、修改 Wi-Fi 网关或 DNS。先前临时增加的 NAS 单客户端规则已撤销，原 ZeroTier 规则保留。

- NAS 显式 SOCKS5 的 Google HTTPS 204 与 UDP/DNS 中继实测通过。
- iPad 真机家庭通路：`transport=home`、`node` 为空，NAS 与 VPN 内 HTTPS 检测通过，首页真实显示“家庭直连”。
- 原通路回归：`transport=zerotier`、原节点身份及虚拟地址保留，NAS 与 VPN 内 HTTPS 检测通过。使用同一家庭物理网络主动选择原通路回归，不将其当作真实离家切网验收。
- 配置／QR／Moon／IPv4 桥接测试通过；更新后的家庭设置草稿 XCTest 通过，不再要求“已配置家庭网关”确认。
- arm64 真机签名构建通过，扩展的 Wi-Fi Information 描述文件已更新；静态转发依赖及全部许可证随源码保留。
- 仍待：真实离家/回家跨网络自动切换、长时间锁屏、会话容量和电量消耗。IPv6 仍不转发，不宣称已经验证省电比例。

下文为历史构建记录，其中路由器接管和手动网关步骤已被 Build 15 替代。


日期：2026-10-22。此记录区分编译／安装与实际网络验收。

## 2026-10-23：读取当前网络（0.7.5 / Build 14）

- “在家与外出”新增读取当前网络入口：限定 Wi-Fi 接口读取 IPv4／子网，显示系统提供的 IPv4 网关，尝试读取 SSID。SSID 仅填入空白字段，替换已有名称需显式操作；NAS 和路由器配置不修改。
- 新增主应用 Access Wi-Fi Information 签名权限。SSID 不可用时提供可选的使用期间精确位置授权入口；拒绝授权或读取超时仍可手填，不启用持续定位。中英文权限用途说明均已配置。
- 模拟器及真机构建通过；在 Xcode 中刷新权限描述文件后，严格签名核验通过，签名包含 Wi-Fi 信息权限。
- `NetworkReaderAcceptance.xcresult` 定向界面测试通过：读取结果、缺失 SSID 的提示、保留手动 SSID／NAS、返回草稿、未确认路由时阻止保存。截图已复核；模拟器实际读出 IPv4 与子网。真机 SSID、网关结果与定位允许／拒绝流程仍需设备授权后验收。
- Build 14 覆盖安装；本轮未启动 VPN、未开启自动切换、未代用户授予定位权限。

## 2026-10-23：家庭直连与外出按需切换（0.7.5 / Build 13）

- 新增可选家庭 SSID、NAS 局域网 IPv4、端口及路由配置确认。旧配置迁移后默认关闭自动模式；保存草稿不启用 VPN。
- 首页开启自动模式时保存系统按需规则：精确匹配家庭 Wi-Fi 的 Disconnect 规则在前，其他网络的 Connect 规则在后。自动模式不显式调用 startVPNTunnel，避免在家强制开启 ZeroTier。暂停时先保存关闭按需连接，再停止 VPN；暂停失败保留开启状态并显示错误。
- 本地 NAS／HTTPS 检测仅在前台、路径变化或手动触发时执行，绑定 Wi-Fi；NAS 必须在 Wi-Fi 同一 IPv4 子网。后台切换由系统规则负责，不运行持续本地检测。成功只表示端口及 HTTPS 可达，不证明代理覆盖所有流量，也不用探测结果代替系统 SSID 匹配规则。
- QR 分享不含家庭设置，QR 导入保留本机家庭设置。App 不修改路由器或 Wi-Fi 网关；启用前仍需完成家庭网关、DNS、IPv6 策略配置。
- `sh tools/test_ios.sh` 通过：旧配置迁移、家庭配置往返、SSID 字节长度／控制字符、私网地址／子网边界、显式路由确认、系统规则顺序与类型、QR 隔离与保留，以及既有桥接、Moon、QR 生成识别回归。
- 最终界面结果包 `ios/build/validation/HomeRoutingAcceptance.xcresult`：3 项通过、1 项跳过、0 失败。覆盖新页面输入和返回草稿、路由未确认时阻止保存、语言切换、保存错误、Moon／诊断导航、竖屏最大辅助字号和首页无滚动。当前模拟器报告横屏方向但应用窗口保持 834×1210，横屏项明确跳过，不将其记作通过。
- 模拟器和真机 Debug 构建成功，签名严格核验通过，Build 13 已覆盖安装到 iPad。启动被设备锁屏拒绝；未开启自动模式、未改动家庭路由、未发起真机 VPN 连接。
- 待验收：填写真实家庭 SSID／NAS 局域网地址后，在已配置的家庭网络测试本地网络权限、NAS／HTTPS、SSID 按需规则、离家／回家、锁屏切换、失败重试与暂停行为，并实测能耗。未宣称真实切换或节电幅度已经通过。

## 2026-10-23：iPad 布局调整（0.7.5 / Build 11）

- 首页移除 ScrollView，横屏使用左右布局，竖屏将连接操作居中，家庭网络与互联网使用独立状态卡。开发者诊断改为独立可滚动页面，错误详情改为弹窗。
- 设置改用原生分组 Form，移除全局按钮／输入框外框及 Moon 页重复样式。语言使用独立的双选分段控件；连接字段、二维码、偏好和指南分组。
- 设置滚动支持收起键盘；验证失败直接弹窗；切换语言保留尚未保存的连接草稿。已连接时保留 Moon 页面浏览，禁止编辑参数。
- 新增 `NestLinkUITests`：11 英寸 iPad 模拟器横竖屏、中英文切换、输入草稿保留、保存错误提示、Moon 和开发者页面导航；验证首页没有滚动容器、上滑后连接按钮位置不变。两个测试最终全部通过。
- 最大辅助字号的未连接首页通过横竖屏边界检查与截图复核；横屏改为左操作、右状态卡，避免状态卡出屏。此结果不代表所有连接状态和多任务窗口尺寸均已完成验收。
- 模拟器及真机 Debug 构建成功，`codesign --verify --deep --strict` 通过，Build 11 已覆盖安装到 iPad Pro 12.9 英寸第六代。启动请求因设备锁屏被拒绝，待用户解锁打开确认真机画面；本轮未启动 VPN。
- 截图与 XCTest 结果保存在忽略的 `ios/build/validation/`，最终结果包为 `LayoutFinal.xcresult`。可重新运行：

  ```sh
  xcodegen generate --spec ios/project.yml
  xcodebuild -project ios/NestLink.xcodeproj -scheme NestLink -destination 'platform=iOS Simulator,name=iPad Pro 11-inch (M5)' CODE_SIGNING_ALLOWED=NO test
  ```

## 已通过

- Xcode 27.1 / iOS SDK 27.1，iPadOS 17.0 deployment target。
- 真机 arm64 与模拟器构建。
- Build 2 补充桌面 AppIcon：复用现有小窝图案，生成 1024×1024 无透明通道 PNG，配置资产目录并重新签名覆盖安装；产物包含 iPad 主图标声明。
- Build 3 修正连接阶段断网处理：关闭协议级 `includeAllNetworks`，保留显式默认路由；外层端点占位地址不再使用隧道内 NAS 地址；首次连续两轮检测失败时自动断开并显示原因。签名构建、签名完整性及 iPad 逻辑测试通过，实际联网恢复仍待真机重连确认。
- Build 3 已覆盖安装，但远程启动被设备以 Security 原因拒绝；本机核验两个签名完整、描述文件未过期且包含该 iPad。仍需用户解锁并手动打开，确认设备端信任／验证状态；未将安装成功视为启动成功。
- Debug 构建会在应用自己的 Caches/ConnectionStatus.json 保存最近公开状态快照，方便真机诊断；不含身份私钥。
- 使用本机付费开发者团队自动签名，主应用及 Packet Tunnel 扩展均生成有效开发描述文件；`codesign --verify --deep --strict` 通过。
- 在 iPad Pro 12.9 英寸第六代（iPadOS 26.6.1）安装并启动应用。
- 真机 Packet Tunnel 扩展已启动，ZeroTier 核心已生成并持久化公开身份；仅读取 `identity.public` 确认节点 ID，未导出私钥。
- 通过仅 Debug 构建识别的 `NESTLINK_TEST_CONFIG` 启动环境注入测试配置；读取本应用偏好文件，确认参数已完整保存。私人参数不写入源码。
- iPad Pro 11 英寸模拟器启动及竖屏截图检查；修复资源加载和按钮文字颜色问题。
- `sh tools/test_ios.sh`：IPv4 网关／同子网路由、ARP 请求与应答、伪造来源与错误目标拒绝、64 包队列上限、缓存清理、畸形包及 IPv6 拒绝；C++ 测试开启 AddressSanitizer、UndefinedBehaviorSanitizer。
- Swift 参数测试：网络 ID、IPv4、端口、配置字典往返、Moon 去重、冲突与数量限制。
- `python3 tools/test_core.py`：既有 Android 纯逻辑测试通过。

## 尚待真机验收

Build 4 新增用户／开发者模式、Moon 列表与实时路径判断、中英文、手动复检、相机／图片扫码及入网码分享。真机和模拟器编译通过；QR 格式与 Android 互通、参数可选值保留与清空、畸形 URL 拒绝、CoreImage 生成 PNG 后由 Vision 真实识别的往返测试通过。Moon 角色／120 秒边界／新网络／未来时间戳测试通过。新增功能仍需下列实际交互验收，不自动连接 VPN。

Build 4 已签名覆盖安装到用户 iPad。远程启动因设备锁屏被拒绝；未启动 VPN。模拟器英语普通首页、中文开发者首页已启动并截图检查。

- Controller 授权及虚拟 IPv4 分配。
- 实际 NAS TCP、隧道 HTTPS 204、Safari 内网与互联网访问。
- Moon 实际发现、锁屏／唤醒、底层网络切换、重连身份稳定性。
- 横屏、分屏、动态字体及更广泛的无障碍验收。

不能将以上“已通过”的编译、安装和单元测试视为实际 VPN 已连通。

## 其他检查

`python3 tools/test_qr.py` 因本机尚未解析 Android ZXing 3.5.3 依赖，未执行二维码解码测试；该旧记录早于 Build 4 的二维码实现。本次未修改 Android 功能代码。

初次签名误选 Personal Team 后已切换到用户现有付费组织团队。机器专用团队配置保存在忽略的 `Signing.local.xcconfig` 中。

## 0.7.0 黄羽鸡界面 — 2026-10-23

0.7.4 / Build 9 后续调整：简化首页并重新分布留白；状态面板统一圆体；网络名称通过引擎快照传回并按网络 ID 缓存，在首页与设置展示；连接中允许扫码，确认断开后才填入草稿。本机仍未编译这些 SwiftUI／原生桥接改动，待 Mac/iPad 验证。

后续 0.7.1 / Build 6：主页面改用用户提供的 `design/approved-home-reference.png` 中的原始黄羽鸡、中文静态字形、图标与可伸缩边框。保留原生交互、真实状态及英文／辅助功能字号文字布局。AppIcon 同步该参考图中的鸡。本机未编译 SwiftUI，仍待 Mac 构建和 iPad 实机验收。

- 用户已确认此前 iPad 版本可以联网；此为用户反馈，不替代专项验收记录。
- 版本号调整为 0.7.0 / Build 5。主应用同步暖白／蛋黄／深棕手绘控件、站酷快乐体、大号黄羽鸡品牌区、状态面板与折叠指南；设置页按设计稿重排。
- AppIcon 是无透明通道的 1024×1024 RGB 图，使用已确认的黄羽鸡原图。字体许可随应用打包。
- 新增界面文案的中英文覆盖检查通过。本机是 Windows，未编译本次 SwiftUI 改动；仍需 Mac 构建以及 iPad 竖横屏、动态字体和实际交互验收。
