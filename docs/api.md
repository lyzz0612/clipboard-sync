# HTTP API 契约

## 基础地址

- **生产环境**：`https://<your-worker>.workers.dev`
- **本地开发**：`http://localhost:8787`

## 认证方式

在需要认证的请求中，在 HTTP 头中携带 JWT：

```
Authorization: Bearer <token>
```

Token 通过 `POST /api/login` 获取。

## 接口列表

| 方法 | 路径 | 认证 | 请求体 | 响应 | 状态码 |
|------|------|------|--------|------|--------|
| POST | `/api/register` | 否 | `{ "username": string, "password": string }` | `{ "ok": true, "message": "registered" }` | 201, 409（用户已存在）, 400 |
| POST | `/api/login` | 否 | `{ "username": string, "password": string }` | `{ "token": string }` | 200, 401, 400 |
| GET | `/api/clips` | 是 | - | `{ "clips": ClipItem[] }` | 200, 401 |
| GET | `/api/clips/delta?since=ISO8601` | 是 | - | `{ "clips": ClipItem[], "serverTime": string }` | 200, 401 |
| POST | `/api/clips` | 是 | `{ "text": string }` | `{ "clip": ClipItem }` | 201, 401, 400 |
| DELETE | `/api/clips/:id` | 是 | - | `{ "ok": true }` | 200, 404, 401 |

## ClipItem 类型

```json
{
  "id": "string",
  "text": "string",
  "createdAt": "string"
}
```

`createdAt` 与 `serverTime` 均为 ISO 8601 时间字符串。

## 增量查询语义

- 查询参数 `since` 为 ISO 8601 时间戳。
- 响应包含所有满足 `createdAt > since` 的剪贴板项。
- 响应中的 `serverTime` 供客户端持久化，作为下一次请求的 `since` 值。

## 错误响应

错误时统一返回：

```json
{ "error": "string" }
```

## JWT 载荷

```json
{
  "sub": "userId",
  "username": "string",
  "iat": 0,
  "exp": 0
}
```

- `sub`：用户 ID  
- `iat` / `exp`：签发时间与过期时间（Unix 秒）  
- **有效期**：7 天  

## 限流

v1 未实现限流。
