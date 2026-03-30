# 本地开发

本文档汇总 `Clipboard Sync` 的本地开发方式，包含 Worker 本地启动、环境变量、Android 联调和常用调试命令。

## Worker 本地开发

### 前置要求

- Node.js >= 18
- npm

### 安装依赖

在 `worker/` 目录执行：

```bash
npm install
```

### 启动开发服务

```bash
npm run dev
```

- 默认地址：`http://localhost:8787`
- 支持热重载
- `worker/public/` 下的 Web 页面资源会一并提供

如果需要让局域网内的手机访问本机服务，可使用 `wrangler dev --ip 0.0.0.0`，再通过 `http://<你的本机 IP>:8787` 联调。

### 本地环境变量

将 `worker/.dev.vars.example` 复制为 `worker/.dev.vars`，至少配置：

- `JWT_SECRET`

### KV 行为

- 本地开发默认使用 Miniflare 模拟 KV，不需要先创建云端命名空间
- 如果使用 `wrangler dev --remote`，会连接真实 Cloudflare KV，可能读写生产数据，请谨慎

### 冒烟测试

在 `worker/` 目录执行：

```bash
npm test
```

会运行 `scripts/smoke-test.mjs`，针对本机服务执行基础冒烟测试。

### 常用调试命令

部署：

```bash
npm run deploy
```

查看生产日志：

```bash
npx wrangler tail
```

## Android 本地联调

### 构建

在 `android/` 目录执行：

```bash
./gradlew assembleDebug
```

### 连接本地 Worker

调试时，将 Android 端 API Base URL 指向：

```text
http://<开发机局域网 IP>:8787
```

如果使用明文 HTTP，可能需要在 `AndroidManifest.xml` 中允许明文流量，通常只建议在调试环境启用。

### 真机联调注意事项

在部分 Android ROM，尤其是 MIUI 上，后台、自启动和无障碍能力会影响同步效果。联调时建议重点确认：

- 自启动是否开启
- 电池优化是否关闭或加入白名单
- 无障碍服务是否已正常开启

## 相关文档

- [部署说明](deploy.md)
- [HTTP API 契约](api.md)
- [Worker 开发指南](../worker/README.md)
- [Android 开发指南](../android/README.md)
