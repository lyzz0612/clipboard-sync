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

Android 工程已可通过 GitHub Actions 在版本 tag 创建时自动构建 signed release APK / AAB，并同步生成 `CHANGELOG.md`。
