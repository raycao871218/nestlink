# 安卓在家与外出自动切换

设置中开启“在家与外出：自动切换”，填写准确的家庭 Wi-Fi 名称、NAS 局域网 IPv4、SOCKS5 端口。ZeroTier 配置继续保留供外出使用。保存不启动连接；首页连接／断开按钮控制整个自动切换服务。

- 家庭 SSID 匹配：本地 VPN → NAS SOCKS5；不创建 ZeroTier 节点。
- 离家、SSID 不匹配或首次无法读取：使用原 ZeroTier 通路。系统回调及 3 秒周期重新评估可用物理网络。已有可用通路时，候选变化需持续 5 秒才切换；物理网络真正丢失时立即恢复处理。已确认家庭 SSID 后，同一个存活的系统 Network 上即使名称被系统隐藏也保留家庭通路；网络断开、网络标识变化或明确读到其他名称时重新判断，新的物理网络不沿用该判断。
- 家庭 NAS 限同一 IPv4 子网；SOCKS5 服务需允许局域网连接并支持 TCP 和 UDP（包括 DNS）。家庭 VPN 使用 1.1.1.1 DNS，经 SOCKS5 转发；未启用 IPv6，Android 阻止该地址族。
- NAS TCP 与 VPN 内 HTTPS 204 均通过才显示可用；故障显示异常或重试，不将普通网络直连报告为代理成功。
- 家庭代理与 ZeroTier 模式互换时，先停止旧引擎并清理 TUN。ZeroTier 仅更换底层网络时，重绑受保护 UDP 套接字，保留节点和 TUN；断网等待恢复也保留该节点。旧节点配置回调、旧连通性结果不会更新新通路。
- DNS、网络验证状态及网络信息的显示顺序等变化不会重建 VPN；家庭通路只关注物理网络、模式和本地 IPv4 地址／前缀。内部计数器 `routeSwitches`、`homeStarts`、`nodeStarts` 用于诊断不必要的重建。
- 家庭 SSID、NAS 和端口只存本设备，不加入二维码。连接期间禁止编辑；中英文切换保留草稿。

“读取当前 Wi-Fi”显示 SSID、IPv4、子网掩码及 IPv4 默认网关，仅自动填写空白 SSID；替换已有名称需点击确认。不会把当前网关自动填作 NAS，也不修改系统网络设置。

## 系统权限与网关

读取 Wi-Fi 名称需要精确位置权限和系统定位开启；仅授予大致位置可能拿不到 SSID。连接时按需请求权限；拒绝后仍可使用 ZeroTier。应用不请求后台定位，不持续请求位置坐标。后台／锁屏能否持续读取 SSID取决于系统权限和厂商限制，无法读取时按外出策略处理，需在目标机型验收。

用户通常可以在 Android 系统的 Wi-Fi 高级设置里手动设静态 IP 和网关；普通应用不能通过公开 API 任意修改现有 Wi-Fi 的网关。NestLink 使用 VpnService 自身的路由，不依赖该能力，不需要 root，也不改路由器。

## 验证记录与待验收项

已执行：Android 三种 ABI（arm64-v8a、armeabi-v7a、x86_64）Debug 构建、lint、Java 核心测试、中英文资源检查。新测试覆盖 SSID 字节长度／精确匹配、私有 IPv4、同子网与本机地址排除、端口及隐藏 SSID。连接的 arm64 安卓设备上，APK 同源原生库通过 20 轮启停测试，包含初始化前取消；该测试无需安装应用或改变网络。

仍需真机端到端验收：家庭 NAS TCP／DNS／HTTPS、代理关闭与 UDP 不支持、家庭 Wi-Fi ↔ 移动网络 ↔ 其他 Wi-Fi、Wi-Fi DHCP 地址变化、SSID 权限拒绝／撤销、定位关闭、锁屏唤醒和长时间后台、断开后普通网络恢复、平板横竖屏及分屏。原生启停测试不代表这些场景已通过。

原生冒烟测试源码：`tests/home_proxy_smoke.c`。用 NDK 为设备 ABI 编译并链接 `-ldl`，将测试程序与构建的 `libNestLinkHome.so` 放到设备临时目录，传入库的绝对路径运行。它只测试原生初始化／停止，不启动系统 VPN，也不访问 NAS。

稳定性修复后的手机回归：`-e connect true -e soakSeconds 45`，45/45 次采样保持 `connected`，使用家庭 NAS 通路，通路和引擎无重启。测试期间重复请求连通性复检，NAS TCP 和 VPN HTTPS 204 检测通过。测试前的版本在相同测试中因 SSID 被隐藏而错误切到 ZeroTier，触发重启断言失败。当前结果只覆盖该手机静止网络下的 45 秒观察，不能代替长时间及跨网络验收；测试结束会断开 VPN。

## Java 加载入口回归

发现并修复了上游示例 `hev-jni.c` 被一起编译的问题：其 `JNI_OnLoad` 尝试注册不存在的 `hev.htproxy.TProxyService`，导致点击连接时 ART 中止进程。CMake 明确排除此文件，只使用 NestLink 自己的 JNI 适配器。单纯 `dlopen` 的 C 冒烟测试不会调用这个 Java 加载入口，因此新增仅存在于 Debug 包中的 `HomeProxyLoadInstrumentation`。

安装 Debug APK 后运行 `adb shell am instrument -w com.naslink.android/com.naslink.android.HomeProxyLoadInstrumentation`，验证 ART 正常加载代理类。增加 `-e connect true` 可在已有 VPN 授权和配置下启动连接服务，等待一轮健康检查，并在测试后断开。不会修改配置。连接手机上的加载及服务启动检查已通过；本次健康检查为 `degraded`，走 ZeroTier，不能据此宣称家庭 NAS 转发验收通过。Release 包不包含该测试入口。
