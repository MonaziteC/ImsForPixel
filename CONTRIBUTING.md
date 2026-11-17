# Contributing

欢迎提交可复现问题、翻译、文档和代码改进。提交代码前请执行：

```sh
./gradlew assembleRelease testDebugUnitTest lintRelease
```

请在 PR 中说明具体触发条件、改变后的行为以及验证范围。涉及守护生命周期或 CarrierConfig 写入的改动，应区分配置回读、权限仍然有效与真实网络能力；JVM 测试不能代替设备验证。

请保留上游归属和改编文件的许可。不要提交签名密钥、ADB 密钥、设备诊断文件、local.properties、构建目录或缓存。

发现错误时，优先使用仓库的问题反馈模板；提供截图和日志前自行遮盖个人信息。
