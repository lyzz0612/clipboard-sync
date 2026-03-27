# Worker 开发指南

## 前置要求

- Node.js >= 18  
- npm  

## 安装依赖

```bash
npm install
```

## 本地开发

```bash
npm run dev
```

- 默认地址：`http://localhost:8787`，支持热重载。  
- 使用 `--ip 0.0.0.0` 时，局域网内设备可通过 `http://<你的本机 IP>:8787` 访问（便于手机调试）。

## 环境变量

将 `.dev.vars.example` 复制为 `.dev.vars`，并设置 `JWT_SECRET`。

## KV 存储

- **本地开发**：使用 Miniflare 模拟 KV，无需在云端创建命名空间。  
- **远程 KV**：可使用 `wrangler dev --remote`（会连接真实 KV，**可能读写生产数据**，请谨慎）。

## 冒烟测试

```bash
npm test
```

会运行 `scripts/smoke-test.mjs`，针对本机服务做冒烟测试。

## 部署

```bash
npm run deploy
```

或通过 GitHub Actions 部署。需要配置 `CLOUDFLARE_API_TOKEN`，并在 `wrangler.toml` 中配置 KV 命名空间 ID。

## 首次 KV 配置

```bash
npx wrangler kv:namespace create CLIPBOARD_KV
```

将返回的命名空间 ID 写入 `wrangler.toml`。

## 生产日志

```bash
npx wrangler tail
```

## 项目结构

| 路径 | 说明 |
|------|------|
| `src/index.ts` | Worker 入口与路由 |
| `src/auth.ts` | 认证与 JWT |
| `src/types.ts` | 类型定义 |
| `public/` | 静态资源（如 Web UI） |
