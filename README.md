# Pixel IMS

基于 [svenuks/ImsForPixel](https://github.com/svenuks/ImsForPixel) 的独立改进版本。无需 Root 或额外安装 Shizuku，通过手机本机无线 ADB 完成一次授权，应用自定义 IMS / NR 配置，并在关闭开发者选项后保留授权会话、检测并恢复丢失的配置。

## 1.2.0

- 官方 **Material 3 1.4.0** 组件；Android 12+ 跟随系统动态配色，明暗模式跟随系统。
- “状态 / 配置”两页：精简守护状态、按用途分组的 SIM 设置、按需打开的连接授权面板。
- 默认激活后自动守护。普通激活和守护共用完整的应用自定义配置定义，双 SIM 独立维护。
- 状态页支持撤销配置、恢复运营商默认。现有授权有效时可直接撤销；无有效授权时引导连接无线调试。
- 配对设备名称固定为 **Pixel IMS**，复用本地保存的配对密钥。
- 移除“保持 SA”开关及 `ims.sa_disable_policy_int` 写入；旧选项不会进入新配置。
- 区分“正在操作”和“正在守护”，不再把正常守护显示成一直加载。

## 使用

1. 安装后在“配置”页选择 SIM，设置 VoLTE、VoNR、Wi-Fi 通话、NR 和图标等选项。
2. 点击“应用并守护”，按面板提示临时连接 Wi-Fi、开启无线调试并完成配对。可使用通知栏配对，也可展开手动端口 / 配对码输入。
3. 返回应用并应用配置。显示“守护已就绪，可以关闭开发者选项”后关闭开发者选项总开关。
4. 正常守护不需要持续的 ADB 连接。修改配置时先使用“修改配置”暂停守护；保存选择后再次应用。
5. 状态页的“撤销配置并恢复默认”会停止守护、清除临时运营商覆盖；它不删除配对密钥。没有有效会话时需要重新授权。

原来的 `/data/user/0/.../files` 配对记录不会自动改名。以后重新配对时可手动遗忘旧条目。

## 边界

- 守护依赖存活的 Instrumentation / UiAutomation 权限会话；重启、强行停止、守护进程或授权连接终止后不能保证自动恢复授权。
- NR / NR Advanced 参数影响模式可用性和图标判定，不能强制物理载波聚合，也不能保证始终出现 5G+ 图标。
- 守护不重置 IMS 或切换飞行模式；通话期间暂停写入。初次明确执行普通激活仍保留上游 IMS 重置流程。
- 恢复默认使用 `overrideConfig(subId, null, false)`，会清除对应 SIM 的临时覆盖层，包括其他工具写入该层的值；不会清除系统专属持久覆盖。
- 前台服务仍按系统要求提交有效通知。在用户要求的静默守护模式下，启动授权阶段仅撤销**本应用**的通知权限；系统“活动应用”列表仍可能显示服务。配对通知仅在用户主动配对时申请权限。
- 网络用途是本机 `127.0.0.1` ADB 和局域网 mDNS 发现；不包含分析 SDK 或日志上传功能。

## 构建

JDK 17、Android SDK Platform 36，使用仓库提供的 Gradle wrapper：

```sh
./gradlew assembleRelease testDebugUnitTest lintRelease
```

工具链：AGP 8.13.0、Gradle 8.13、Kotlin / Compose compiler 2.2.20。Material 3 1.4.0 由 Compose BOM 2026.03.00 管理。更新的 BOM 包含需要 AGP 9.1 / SDK 37 的基础库，当前选择保留兼容的工具链；Material 3 组件本身仍为检索时的最新稳定版。

最低 Android API 29。Gradle 会下载依赖，首次构建需要网络。界面语义测试在 Robolectric 上运行；这不替代真实 Pixel 固件的长时间后台测试。

默认 release 使用本地开发签名，便于测试。正式发布可设置以下环境变量，私钥不应提交到仓库：

```text
PIXEL_IMS_KEYSTORE_FILE
PIXEL_IMS_KEYSTORE_PASSWORD
PIXEL_IMS_KEY_ALIAS
PIXEL_IMS_KEY_PASSWORD
```

提供的测试 APK 沿用前几版的开发签名；源码包不包含该私钥。使用你自己的发布签名后，无法直接覆盖不同签名的测试安装。

## Fork 与来源

参见 [FORK_GUIDE.md](FORK_GUIDE.md)、[第三方归属](THIRD_PARTY_NOTICES.md) 和 [架构说明](docs/ARCHITECTURE.md)。仓库保留原 Kotlin 命名空间和 application ID，以便覆盖升级现有测试版本。

上游原 README 保存在 [docs/UPSTREAM_README.md](docs/UPSTREAM_README.md)，其中的历史能力声明不代表本 fork 的验证范围。
