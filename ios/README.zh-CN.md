# NestLink iPad 原型

独立 SwiftUI 应用 + Packet Tunnel 扩展，复用仓库中未经修改的 ZeroTier One 1.16.0 C++ 核心。Android 构建不依赖此目录。

## 当前功能

- 单一 ZeroTier 网络，IPv4，同子网 NAS 网关；最多 8 个 Moon。
- 连接、取消、断开，显示公开设备 ID、虚拟 IPv4 与 UDP 收发计数。
- `/0` IPv4 路由及 NAS DNS；IPv6 路由进入隧道后丢弃，不转发 IPv6。
- 经 Packet Tunnel 虚拟接口执行 NAS TCP 检测，以及使用系统 TLS 验证的 HTTPS 204 检测。两项通过才显示已连接。
- 每 20 秒检测，路径变化使旧结果失效；扩展检测结果超过 45 秒停止显示成功，应用超过 8 秒收不到快照停止显示成功。
- SwiftUI 支持 iPad 横竖屏和紧凑宽度；默认英语，可切换简体中文，切换保留连接和设置草稿。
- 默认用户模式；开发者模式显示设备、虚拟地址、UDP 计数、节点物理路径，并提供手动复检。
- Moon 列表添加／删除、数量摘要和实时状态；仅 Moon 角色且当前网络下有最近 120 秒内真实回包才显示正常。
- 相机扫码与本地图片识别；确认后填入设置，不自动连接。支持与 Android 相同的网络 ID、ZeroTier 链接、官方管理页链接和 harborlink://join 格式。
- 生成入网码、分享入网信息；仅包含网络、NAS、端口与 Moon 参数，不包含身份私钥或管理 Token。

当前仍是开发原型，不是发行版。功能对照与未完成的验收见 [Android 对齐清单](ANDROID_PARITY.md)。自动启动、按需连接和系统级断线保护均未实现，也不是当前 Android 版已有的能力。

## 构建与签名

要求 Xcode（含 iOS SDK）、XcodeGen、一台已配对并开启开发者模式的 iPad，以及加入 Apple Developer Program、支持 Network Extensions 的开发者 Team。免费 Personal Team 无法签署这个 VPN 扩展。

在仓库根目录执行：

```sh
xcodegen generate --spec ios/project.yml
open ios/NestLink.xcodeproj
```

在 Xcode 中为 `NestLink` 和 `PacketTunnel` 两个 Target 都选择正确 Team，保持自动签名，并确认 Network Extensions / Packet Tunnel 能力启用。如果 Bundle ID 被其他 Team 占用，同时更改主应用及扩展 ID；扩展 ID 应为主应用 ID 加 `.PacketTunnel`。

生成的 `.xcodeproj` 不纳入版本管理，`project.yml` 是工程配置来源。重新生成会覆盖工程内的手工修改，团队设置可通过构建参数提供：

也可在本机创建被 Git 忽略的 `ios/Signing.local.xcconfig`，写入 `DEVELOPMENT_TEAM = YOUR_PAID_TEAM_ID`。工程的 `Config.xcconfig` 会自动读取它。

```sh
xcodebuild -project ios/NestLink.xcodeproj -scheme NestLink \
  -configuration Debug -destination 'id=YOUR_IPAD_UDID' \
  -derivedDataPath ios/build DEVELOPMENT_TEAM=YOUR_PAID_TEAM_ID \
  -allowProvisioningUpdates -allowProvisioningDeviceRegistration build
```

不需要共享 Apple 密码、证书私钥或 ZeroTier 身份私钥。

当前实测情况见 [验证记录](VALIDATION.md)。

仅编译、不签名（不能将产物直接安装到真机）：

```sh
xcodebuild -project ios/NestLink.xcodeproj -scheme NestLink \
  -sdk iphoneos -configuration Debug -derivedDataPath ios/build \
  CODE_SIGNING_ALLOWED=NO build
sh tools/test_ios.sh
```

## 真机验收

1. 运行 `NestLink`，填写自己的网络 ID、NAS 的 ZeroTier IPv4、检测端口（默认 7890），可选 Moon。
2. 点击“连接回家”，允许系统添加 VPN 配置。配置等待阶段可查看并复制设备 ID，在自己的 Controller 授权并分配 IPv4。
3. 50 秒内未取得网络配置会停止启动；错误中包含公开设备 ID。完成 Controller 授权后重新连接。
4. 确认界面显示虚拟 IPv4；NAS 与互联网均检测通过。再用 Safari 访问 NAS 和其他内网设备，确认 NAS 转发与互联网出口。
5. 锁屏后再唤醒、切换 Wi-Fi／热点，确认重新验证后恢复；断开后确认系统恢复原网络。
6. 断开、重启应用并再次连接，确认公开设备 ID 不变。

NAS 检测只证明指定 TCP 端口可达；HTTPS 检测只证明测试站点可用，不代表所有网站与协议可用。NAS 侧仍需现有 [透明网关配置](../nas/README.md)。

应用使用显式默认路由，不启用 `includeAllNetworks`，避免在尚未取得隧道配置时锁住普通网络。首次连接连续两轮健康检测失败会自动断开 VPN，恢复原网络；已经验证成功后的连接仍在后台重试。尚未验收断线保护、所有后台场景或各种网络切换条件，不承诺系统级 kill switch。

## 身份与通信

ZeroTier 身份保存在扩展自身的 Application Support 中，目录排除备份，文件权限 0600，使用首次解锁后可用的数据保护。不会从 Android 复制身份；iPad 是一个需要独立授权的新节点。卸载应用可能删除身份。

App 使用系统 `NETunnelProviderManager` 保存连接参数，并通过 provider message 读取公开状态；不共享身份文件，不需要 App Group。所有核心 API、UDP 接收和 IPv4／ARP 操作在同一串行队列执行；主界面的轮询不驱动隧道生命周期。

Planet 使用与 Android 相同的资源，启动时验证 SHA-256；仅在扩展存储中不存在时安装，不覆盖已有定义。

许可沿用仓库 [LICENSE](../LICENSE) 和 [THIRD_PARTY.md](../THIRD_PARTY.md)。上架前另行核对 VPN 审核、许可与加密出口申报要求。
