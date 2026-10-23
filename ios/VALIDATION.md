# iPad 原型验证记录

日期：2026-10-22。此记录区分编译／安装与实际网络验收。

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

后续 0.7.1 / Build 6：主页面改用用户提供的 `design/approved-home-reference.png` 中的原始黄羽鸡、中文静态字形、图标与可伸缩边框。保留原生交互、真实状态及英文／辅助功能字号文字布局。AppIcon 同步该参考图中的鸡。本机未编译 SwiftUI，仍待 Mac 构建和 iPad 实机验收。

- 用户已确认此前 iPad 版本可以联网；此为用户反馈，不替代专项验收记录。
- 版本号调整为 0.7.0 / Build 5。主应用同步暖白／蛋黄／深棕手绘控件、站酷快乐体、大号黄羽鸡品牌区、状态面板与折叠指南；设置页按设计稿重排。
- AppIcon 是无透明通道的 1024×1024 RGB 图，使用已确认的黄羽鸡原图。字体许可随应用打包。
- 新增界面文案的中英文覆盖检查通过。本机是 Windows，未编译本次 SwiftUI 改动；仍需 Mac 构建以及 iPad 竖横屏、动态字体和实际交互验收。
