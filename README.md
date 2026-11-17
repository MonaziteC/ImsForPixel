# Pixel IMS

[English](README.en.md) · [下载 v1.2.0](https://github.com/MonaziteC/ImsForPixel/releases/tag/v1.2.0) · [使用指南](docs/USAGE.md) · [构建说明](docs/BUILDING.md)

**为 Pixel 自定义 IMS 与 NR 配置，并在关闭开发者选项后持续守护。**

Pixel IMS 基于 [svenuks/ImsForPixel](https://github.com/svenuks/ImsForPixel)，保留手机本机无线 ADB 授权方式，无需 Root、电脑或额外安装 Shizuku。首次授权后，可关闭开发者选项，由独立守护进程检查并恢复丢失的配置。

> 守护依赖仍然存活的授权会话。重启、强行停止或会话失效后，需要重新授权；它不是向系统写入永久配置。

## 主要功能

| 功能 | 说明 |
| --- | --- |
| IMS 配置 | VoLTE、VoNR、Wi-Fi 通话、漫游 Wi-Fi 通话与补充业务等 |
| NR 与图标 | 配置 5G NR（NSA / SA）可用性及 5GA / 5G+ 图标判定策略 |
| 完整配置守护 | 守护全部由应用写入的自定义配置，而不仅是 NR 选项 |
| 双 SIM | 分别保存两张 SIM 的设置，并独立维护启动时已启用的 SIM |
| 关闭调试后运行 | 保留授权会话，独立进程监听配置变化，按需补写并回读确认 |
| 撤销配置 | 在状态页停止守护并恢复运营商默认配置 |
| Material 3 | 状态 / 配置两页，跟随系统明暗模式；Android 12+ 使用系统动态配色 |

5G+ 图标和实际载波聚合由网络、基带与系统共同决定。应用调整配置可用性和图标策略，**不能强制建立 5G-Advanced 连接，也不保证始终显示 5G+**。

## 下载与开始使用

从 [Releases](https://github.com/MonaziteC/ImsForPixel/releases) 下载 APK。当前版本为 [v1.2.0](https://github.com/MonaziteC/ImsForPixel/releases/tag/v1.2.0)。

1. 在“配置”页选择 SIM，并设置所需功能。
2. 点击“应用并守护”，临时连接 Wi-Fi、开启无线调试并完成配对。
3. 返回应用并应用配置。等待出现“守护已就绪，可以关闭开发者选项”，再关闭开发者选项总开关。
4. 后续无需保持无线 ADB 连接。状态页可查看守护状态、配置检查结果及已确认恢复次数。

“激活后自动守护”默认启用。修改配置时先点击“修改配置”暂停守护，再重新应用。详情见 [使用指南](docs/USAGE.md)。

## 恢复默认

在状态页点击 **“撤销配置并恢复默认”**：

- 有效守护会话仍在时，使用现有授权撤销配置，通常无需重新打开调试。
- 没有有效会话时，应用会引导重新连接无线调试。
- 撤销会停止守护，并清除对应 SIM 的临时运营商覆盖层；其他工具写入同一临时层的配置也可能被清除。
- 不清除系统专属持久覆盖，不删除已保存的配置选择或 ADB 配对密钥。

## 使用前了解

- **守护不等于永久授权。** 界面退出或划掉最近任务与强行停止不同；守护进程、授权连接死亡或重启后，不能保证自行恢复权限。
- **实际网络能力需要运营商支持。** IMS 注册、VoNR、Wi-Fi 通话和聚合能力仍取决于套餐、网络、基带与系统。
- **不是持续重置网络。** 正常守护先检查、仅在配置不匹配时补写；通话期间暂停写入，并有限速与退避。首次明确执行普通激活保留上游 IMS 重置流程。
- **没有常驻通知栏条目不代表没有前台服务。** 守护保留系统要求的前台服务；启动授权阶段仅关闭本应用的通知权限以隐藏通知栏展示。系统“活动应用”列表仍可能显示它。配对时可按需申请通知权限。
- **范围有限的验证。** 当前用户在 Pixel / Android 17 上反馈正常；最低安装版本为 Android 10（API 29），这不代表已覆盖验证所有 Android 版本或其他厂商设备。

常见问题见 [FAQ](docs/FAQ.md)。

## 隐私

应用的网络用途为本机 `127.0.0.1` ADB 连接和局域网 mDNS 发现。项目不包含分析 SDK、遥测或自动日志上传；配对密钥保存在应用私有目录。源码与 Release 附件不包含设备日志、ADB 私钥或发布签名私钥。

## 开发与构建

使用 JDK 17、Android SDK Platform 36：

```sh
./gradlew assembleRelease testDebugUnitTest lintRelease
```

UI 使用官方 Material 3 1.4.0，当前构建通过 34 项 JVM / Compose 界面语义测试，Lint 为零错误。完整工具链、签名配置和验证边界见 [构建说明](docs/BUILDING.md) 与 [验证记录](docs/VALIDATION.md)。

## 反馈

请在 [Issues](https://github.com/MonaziteC/ImsForPixel/issues) 中描述机型、Android 版本、复现步骤、当前守护状态及预期行为。发布截图或日志前，请自行遮盖手机号、设备标识、IP 和其他个人信息；不要上传配对私钥或签名密钥。

## 来源与许可

- 基础项目：[svenuks/ImsForPixel](https://github.com/svenuks/ImsForPixel)。
- NR / NR Advanced 参数参考：[ryfineZ/carrier-ims-for-pixel](https://github.com/ryfineZ/carrier-ims-for-pixel)，保留 Apache 2.0 许可与归属说明。
- Material 3 与 Compose：Google / AndroidX 官方组件。

本项目为独立 fork，不代表原作者或 Google。新改动采用 MIT；改编部分及上游来源见 [LICENSE](LICENSE)、[第三方归属](THIRD_PARTY_NOTICES.md) 和保留的 [上游 README](docs/UPSTREAM_README.md)。
