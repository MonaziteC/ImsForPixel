# 发布为 fork

1. 在 GitHub 上 fork https://github.com/svenuks/ImsForPixel 。本次只准备本地源码和构建产物，没有创建或推送远程仓库。
2. 将源码 ZIP 解压到你的 fork 工作区，保留自己的 `.git`；或从提供的 Git bundle 克隆，它包含上游历史和本次改动提交。
3. 设置你自己的 origin，检查差异、README、版本和签名，再推送一个分支。无需 force push。
4. GitHub Actions 会编译调试 APK，并执行单元 / Compose 界面语义测试与 Lint。正式发布请使用你自己的签名。

```sh
# 可选：从 Git bundle 还原完整工作区
 git clone pixel-ims-1.2.0.bundle PixelIMS
 cd PixelIMS
 git remote set-url origin https://github.com/YOUR_ACCOUNT/ImsForPixel.git
 git remote add upstream https://github.com/svenuks/ImsForPixel.git
 git push -u origin HEAD:pixel-ims
```

## 包名

当前 `applicationId` / namespace 为 `com.svenuks.imsforpixel`。这使测试 APK 能覆盖前几版，并继续读取已保存设置与 ADB 密钥。

若你准备更换 application ID，需要同步检查 `GuardianStore`、`GuardianController`、instrumentation targetPackage / targetProcesses、shell 组件路径和私有广播 action；不能只改 Gradle 中的一个值。仅修改显示名称不需要改包名。

## 不应提交

`local.properties`、SDK / Gradle 缓存、构建目录、设备日志、ADB 私钥、发布密钥、签名密码。提供的源码归档和 bundle 不含这些文件。

## 发布描述

请保留上游和 NR 参数来源，明确这是独立 fork。不要把配置回读通过描述为已证实的物理载波聚合，也不要承诺重启后自动保留免 Root 授权。
