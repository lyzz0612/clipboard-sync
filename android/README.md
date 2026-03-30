# Android 开发指南

> 说明：Android 工程尚未创建，以下为规划与占位说明。

## 前置要求

- Android Studio（推荐最新稳定版）  
- JDK 17  

## 构建

```bash
./gradlew assembleDebug
```

发布构建会读取以下环境变量或 Gradle 属性：

- `VERSION_NAME`
- `ANDROID_KEYSTORE_PATH`
- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEY_PASSWORD`

例如：

```bash
VERSION_NAME=1.0.0 ./gradlew assembleRelease
```

在 GitHub Actions 中，版本号来自推送的 tag（如 `v1.0.0`），签名文件会通过 Secrets 注入，避免将密钥明文提交到仓库。

## 开发服务器连接

调试时将 API Base URL 设为 `http://<开发机局域网 IP>:8787`。  
若使用明文 HTTP，可能需要在 `AndroidManifest.xml` 中配置 `android:usesCleartextTraffic="true"`（仅调试环境建议）。

## 权限

计划涉及权限包括但不限于：

- `INTERNET`  
- `RECEIVE_BOOT_COMPLETED`（若需开机后后台同步）  
- 无障碍服务相关权限（若通过无障碍读取系统剪贴板，以实现与具体 ROM 策略的配合）  

以最终实现为准。

## MIUI 等设备注意事项

在小米 MIUI 等系统上，后台与自启动常被限制，建议在真机上逐项确认：

- **自启动**：设置 → 应用设置 → 应用管理 → 本应用 → 自启动（路径因 MIUI 版本可能略有差异）  
- **省电 / 电池优化**：设置 → 省电与电池 → 应用智能省电 / 电池优化，将本应用设为无限制或加入白名单  
- **后台弹出界面**（若需要）：在应用详情中允许后台活动  

## 当前状态

Android 工程已可通过 GitHub Actions 作为发布流程中的一个内部步骤，被 `Publish Release` 工作流调用来构建正式 APK，并以带版本号的文件名上传到 Actions Artifacts。

如果是 `v*` 标签触发，真正响应 tag 的是 `Publish Release` 工作流；它会先调用 `Build Android` 工作流构建正式 APK，再统一把 Android APK 上传到对应 GitHub Release 页面。

手动触发 `Build Android` 工作流时，可以在 `build_mode` 中选择 `release` 或 `debug`：

- `release`：生成签名后的正式 APK，文件名类似 `clipboard-sync-1.2.3.apk`
- `debug`：生成调试 APK，文件名类似 `clipboard-sync-1.2.3-debug.apk`，并自动开启 `FileLogger`，把调试日志写入 `clipboard_sync_debug.log`

手动触发只会上传 APK 到 Actions Artifacts，不会创建 GitHub Release，也不会写入 `CHANGELOG.md`。

`CHANGELOG.md` 与 GitHub Release 描述现由独立的 `Publish Release` 工作流在推送 `v*` 标签时生成并更新。
