# 部署说明

## Cloudflare Worker

### 所需密钥与变量

- `CLOUDFLARE_API_TOKEN`：用于 Wrangler / CI 部署  
- `CLOUDFLARE_ACCOUNT_ID`：Cloudflare 账户 ID  

### 首次部署前

1. 创建 KV 命名空间：

   ```bash
   npx wrangler kv:namespace create CLIPBOARD_KV
   ```

   将返回的 ID 配置到 `wrangler.toml`。

2. 设置 Worker 密钥（JWT 签名用）：

   ```bash
   npx wrangler secret put JWT_SECRET
   ```

### GitHub Actions

- 当 `worker/**` 有变更并推送到 `main` 时，自动部署 Worker。  
- 支持 `workflow_dispatch` 手动触发。

## Android 构建

- 当 `android/**` 有变更并推送时，GitHub Actions 构建 debug APK。  
- 构建产物上传到 GitHub Actions Artifacts，供下载。  
- **签名正式版 release**：计划在后续版本中提供。
