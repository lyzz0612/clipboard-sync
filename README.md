# Clipboard Sync — 剪贴板跨端共享

跨设备剪贴板共享：基于 Cloudflare Worker 与 Android 应用。

## 目录结构

| 目录 | 说明 |
|------|------|
| `worker/` | Cloudflare Worker 后端 |
| `android/` | Android 客户端（Kotlin / Compose） |
| `docs/` | 项目文档 |

## Quick start

1. 启动本地 Worker：`cd worker && npm install && npm run dev`，开发服务默认在 `http://localhost:8787`。
2. 在浏览器中打开上述地址，测试 Web 界面。
3. Android 端调试时，将服务端地址填为 `http://<你的本机 IP>:8787`（手机与电脑需在同一局域网）。

## 文档索引

- [HTTP API 契约](docs/api.md)
- [部署说明](docs/deploy.md)
- [Worker 开发指南](worker/README.md)
- [Android 开发指南](android/README.md)

## Deployment

推送到 `main` 分支时，GitHub Actions 会自动部署 Worker。详细步骤与密钥配置见 [docs/deploy.md](docs/deploy.md)。
