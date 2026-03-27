# Clipboard Sync — 剪贴板跨端共享

跨设备剪贴板共享：基于 Cloudflare Worker 与 Android 应用。

## 目录结构

| 目录 | 说明 |
|------|------|
| `worker/` | Cloudflare Worker 后端 |
| `android/` | Android 客户端（Kotlin / Compose） |
| `docs/` | 项目文档 |

## GitHub Actions Workflows

Workflow 定义在仓库的 `.github/workflows/` 下，在 **`master`** 分支上推送对应路径变更时触发；也可在 Actions 里用 `workflow_dispatch` 手动运行。检出代码为**触发本次运行**的提交（默认行为）。

### Deploy Worker（部署 Cloudflare Worker）

- **文件**：`.github/workflows/deploy-worker.yml`
- **触发条件**：
  - 推送到 `master`，且 `worker/**` 有变更
  - 在 Actions 页面手动触发（`workflow_dispatch`）
- **作用**：将 `worker/` 部署为 Cloudflare Worker

### Build Android（构建 Android APK）

- **文件**：`.github/workflows/build-android.yml`
- **触发条件**：
  - 推送到 `master`，且 `android/**` 有变更
  - 在 Actions 页面手动触发（`workflow_dispatch`）
- **作用**：构建 debug APK 并上传到 Actions Artifacts（保留 30 天）

### 配置步骤

使用 Workflow 前，需要在 GitHub 仓库中配置以下 Secrets（Settings → Secrets and variables → Actions）：

| Secret 名称 | 用途 | 获取方式 |
|-------------|------|---------|
| `CLOUDFLARE_API_TOKEN` | Wrangler 部署 Worker 的认证令牌 | [Cloudflare Dashboard → API Tokens](https://dash.cloudflare.com/profile/api-tokens)，创建 Token 时选择 "Edit Cloudflare Workers" 模板 |
| `CLOUDFLARE_ACCOUNT_ID` | Cloudflare 账户 ID | Cloudflare Dashboard 首页右侧栏，或任意 Workers 页面 URL 中的 ID |
| `KV_NAMESPACE_ID` | KV 命名空间 ID，部署时注入 `wrangler.toml` | 在 Cloudflare 后台创建 KV 命名空间后复制 ID（见下方步骤） |

### 首次部署前的准备

#### 1. 获取 `CLOUDFLARE_ACCOUNT_ID`

登录 [Cloudflare Dashboard](https://dash.cloudflare.com/) → 左侧选择你的账户 → 右侧栏 **Account ID** 即是，复制保存。

#### 2. 创建 `CLOUDFLARE_API_TOKEN`

[Cloudflare Dashboard → My Profile → API Tokens](https://dash.cloudflare.com/profile/api-tokens) → **Create Token** → 选择 **Edit Cloudflare Workers** 模板 → 按需调整权限范围 → **Continue to summary** → **Create Token** → 复制保存（只显示一次）。

#### 3. 创建 KV 命名空间，获取 `KV_NAMESPACE_ID`

[Cloudflare Dashboard](https://dash.cloudflare.com/) → 左侧菜单 **Workers 和 Pages** → **KV** → **Create a namespace** → 名称填 `CLIPBOARD_KV`（或任意名称） → **Add** → 创建完成后，在列表中点击该命名空间 → 页面 URL 或详情中可以看到 **Namespace ID**，复制保存。

#### 4. 设置 Worker 运行时密钥 `JWT_SECRET`

首次部署成功后，在 Cloudflare Dashboard → **Workers 和 Pages** → 点击 `clipboard-sync` Worker → **Settings** → **Variables and Secrets** → **Add** → 名称填 `JWT_SECRET`，值填一个随机字符串（建议 32 位以上），类型选 **Secret** → 保存。

> 也可以通过命令行 `npx wrangler secret put JWT_SECRET` 完成，效果一样。

#### 5. 配置 GitHub Secrets

GitHub 仓库页面 → **Settings** → **Secrets and variables** → **Actions** → **New repository secret**，依次添加以上三个：`CLOUDFLARE_API_TOKEN`、`CLOUDFLARE_ACCOUNT_ID`、`KV_NAMESPACE_ID`。

## 快速开始

1. 启动本地 Worker：`cd worker && npm install && npm run dev`，开发服务默认在 `http://localhost:8787`。
2. 在浏览器中打开上述地址，测试 Web 界面。
3. Android 端调试时，将服务端地址填为 `http://<你的本机 IP>:8787`（手机与电脑需在同一局域网）。

## 文档索引

- [HTTP API 契约](docs/api.md)
- [部署说明](docs/deploy.md)
- [Worker 开发指南](worker/README.md)
- [Android 开发指南](android/README.md)

## 部署

向 `master` 推送时，若变更落在 `worker/**` 会部署 Worker，落在 `android/**` 会构建 debug APK；也可在 Actions 中通过 `workflow_dispatch` 手动触发。详细步骤与密钥配置见上文「GitHub Actions Workflows」与 [docs/deploy.md](docs/deploy.md)。
