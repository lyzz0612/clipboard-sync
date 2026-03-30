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

将 `.dev.vars.example` 复制为 `.dev.vars`，至少设置：

- `JWT_SECRET`
- `ADMIN_PASSWORD`（用于 `admin.html` 和 `/api/admin/*` 管理接口）

## D1 存储

- **本地开发**：使用 Miniflare 模拟 D1，无需先创建云端数据库。  
- **远程 D1**：可使用 `wrangler dev --remote`（会连接真实 Cloudflare 资源，**可能读写生产数据**，请谨慎）。

## 冒烟测试

```bash
npm test
```

会运行 `scripts/smoke-test.mjs`，针对本机服务做冒烟测试。

## 部署

```bash
npm run deploy
```

或通过 GitHub Actions 部署。需要配置 `CLOUDFLARE_API_TOKEN`，并提供 `D1_DATABASE_ID`。

如果你在本地直接部署，需要先设置环境变量 `D1_DATABASE_ID`，例如：

```powershell
$env:D1_DATABASE_ID="your-d1-database-id"
npm run deploy
```

## 首次 D1 配置

```bash
npx wrangler d1 create clipboard-sync
```

创建后可执行：

```bash
npm run db:migrate:remote
```

将返回的 `database_id` 配置到：

- GitHub Actions Secret：`D1_DATABASE_ID`
- 或本地 shell 环境变量：`D1_DATABASE_ID`

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
