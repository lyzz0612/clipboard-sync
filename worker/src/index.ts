import { hashPassword, verifyPassword, signJwt, verifyJwt } from './auth';
import type { AppSettings, ClipItem, Env, JwtPayload, UserRecord } from './types';

const CORS_HEADERS: Record<string, string> = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Methods': 'GET, POST, PUT, DELETE, OPTIONS',
  'Access-Control-Allow-Headers': 'Content-Type, Authorization',
};

/** D1 中每个用户最多保留的剪贴条数（新插入在前，超出则丢弃最旧的） */
const MAX_CLIPS_PER_USER = 10;

/** 扫码登录一次性码：D1 中的有效时间（秒），仅存储 userId/username，不含密码 */
const QR_LOGIN_TTL_SEC = 300;

const MAX_ADMIN_DELETE_USERS = 100;

type SettingsRow = {
  allow_registration: number;
};

type UserRow = {
  id: string;
  username: string;
  salt: string;
  password_hash: string;
};

type ClipRow = {
  id: string;
  text: string;
  created_at: string;
};

type QrLoginRow = {
  user_id: string;
  username: string;
  expires_at: string;
};

function json(data: unknown, status = 200): Response {
  return new Response(JSON.stringify(data), {
    status,
    headers: { 'Content-Type': 'application/json', ...CORS_HEADERS },
  });
}

function errorResponse(error: string, status: number): Response {
  return json({ error }, status);
}

function normalizeUsername(value: unknown): string {
  return typeof value === 'string' ? value.trim() : '';
}

async function readJsonBody<T>(request: Request): Promise<T> {
  return (await request.json()) as T;
}

async function queryFirst<T>(statement: D1PreparedStatement): Promise<T | null> {
  return (await statement.first<T>()) ?? null;
}

async function queryAll<T>(statement: D1PreparedStatement): Promise<T[]> {
  const result = await statement.all<T>();
  return result.results ?? [];
}

function toClipItem(row: ClipRow): ClipItem {
  return {
    id: row.id,
    text: row.text,
    createdAt: row.created_at,
  };
}

function nowIso(): string {
  return new Date().toISOString();
}

async function getSettings(env: Env): Promise<AppSettings> {
  const row = await queryFirst<SettingsRow>(
    env.DB.prepare('SELECT allow_registration FROM app_settings WHERE id = 1'),
  );
  if (!row) {
    return { allowRegistration: true };
  }
  return {
    allowRegistration: row.allow_registration !== 0,
  };
}

async function saveSettings(env: Env, settings: AppSettings): Promise<void> {
  await env.DB.prepare(
    `
      INSERT INTO app_settings (id, allow_registration)
      VALUES (1, ?1)
      ON CONFLICT(id) DO UPDATE SET allow_registration = excluded.allow_registration
    `,
  )
    .bind(settings.allowRegistration ? 1 : 0)
    .run();
}

async function authenticateToken(request: Request, env: Env): Promise<JwtPayload | Response> {
  const authHeader = request.headers.get('Authorization');
  if (!authHeader?.startsWith('Bearer ')) {
    return errorResponse('Missing or invalid Authorization header', 401);
  }

  const token = authHeader.slice(7);
  const payload = await verifyJwt(token, env.JWT_SECRET);
  if (!payload) {
    return errorResponse('Invalid or expired token', 401);
  }

  return payload;
}

async function authenticateUser(request: Request, env: Env): Promise<JwtPayload | Response> {
  const payload = await authenticateToken(request, env);
  if (payload instanceof Response) return payload;
  if (payload.role === 'admin') {
    return errorResponse('Admin token cannot access user API', 403);
  }
  return payload;
}

async function authenticateAdmin(request: Request, env: Env): Promise<JwtPayload | Response> {
  const payload = await authenticateToken(request, env);
  if (payload instanceof Response) return payload;
  if (payload.role !== 'admin') {
    return errorResponse('Admin access required', 403);
  }
  return payload;
}

async function handleRegisterStatus(env: Env): Promise<Response> {
  const settings = await getSettings(env);
  return json({ enabled: settings.allowRegistration });
}

async function handleRegister(request: Request, env: Env): Promise<Response> {
  const settings = await getSettings(env);
  if (!settings.allowRegistration) {
    return errorResponse('Registration is disabled', 403);
  }

  const body = await readJsonBody<{ username?: string; password?: string }>(request);
  const username = normalizeUsername(body.username);
  const password = typeof body.password === 'string' ? body.password : '';
  if (!username || !password) {
    return errorResponse('username and password are required', 400);
  }

  const existing = await queryFirst<Pick<UserRow, 'id'>>(
    env.DB.prepare('SELECT id FROM users WHERE username = ?1').bind(username),
  );
  if (existing) {
    return errorResponse('Username already taken', 409);
  }

  const { salt, hash } = await hashPassword(password);
  const id = crypto.randomUUID();
  await env.DB.prepare(
    `
      INSERT INTO users (id, username, salt, password_hash, created_at)
      VALUES (?1, ?2, ?3, ?4, ?5)
    `,
  )
    .bind(id, username, salt, hash, nowIso())
    .run();

  return json({ ok: true, message: 'registered' }, 201);
}

async function handleAdminLogin(request: Request, env: Env): Promise<Response> {
  const configuredPassword = env.ADMIN_PASSWORD?.trim();
  if (!configuredPassword) {
    return errorResponse('Admin password is not configured', 503);
  }

  const body = await readJsonBody<{ password?: string }>(request);
  const password = typeof body.password === 'string' ? body.password : '';
  if (!password) {
    return errorResponse('password is required', 400);
  }

  if (password !== configuredPassword) {
    return errorResponse('Invalid admin password', 401);
  }

  const token = await signJwt({ sub: 'admin', username: 'admin', role: 'admin' }, env.JWT_SECRET);
  return json({ token });
}

async function handleQrSession(env: Env, userId: string, username: string): Promise<Response> {
  const bytes = new Uint8Array(24);
  crypto.getRandomValues(bytes);
  const code = [...bytes].map((b) => b.toString(16).padStart(2, '0')).join('');
  const createdAt = nowIso();
  const expiresAt = new Date(Date.now() + QR_LOGIN_TTL_SEC * 1000).toISOString();
  await env.DB.batch([
    env.DB.prepare('DELETE FROM qr_login_sessions WHERE expires_at <= ?1').bind(createdAt),
    env.DB.prepare(
      `
        INSERT INTO qr_login_sessions (code, user_id, username, expires_at, created_at)
        VALUES (?1, ?2, ?3, ?4, ?5)
      `,
    ).bind(code, userId, username, expiresAt, createdAt),
  ]);
  return json({ code, expiresIn: QR_LOGIN_TTL_SEC });
}

async function handleQrRedeem(request: Request, env: Env): Promise<Response> {
  const body = await readJsonBody<{ code?: string }>(request);
  const rawCode = typeof body.code === 'string' ? body.code.trim().toLowerCase() : '';
  if (!rawCode || !/^[0-9a-f]{48}$/.test(rawCode)) {
    return errorResponse('invalid code', 400);
  }

  const session = await queryFirst<QrLoginRow>(
    env.DB.prepare(
      'SELECT user_id, username, expires_at FROM qr_login_sessions WHERE code = ?1',
    ).bind(rawCode),
  );
  if (!session) {
    return errorResponse('invalid or expired code', 401);
  }

  const currentTime = nowIso();
  await env.DB.prepare('DELETE FROM qr_login_sessions WHERE code = ?1').bind(rawCode).run();
  if (session.expires_at <= currentTime) {
    return errorResponse('invalid or expired code', 401);
  }

  const token = await signJwt(
    { sub: session.user_id, username: session.username, role: 'user' },
    env.JWT_SECRET,
  );
  return json({ token, username: session.username });
}

async function handleLogin(request: Request, env: Env): Promise<Response> {
  const body = await readJsonBody<{ username?: string; password?: string }>(request);
  const username = normalizeUsername(body.username);
  const password = typeof body.password === 'string' ? body.password : '';
  if (!username || !password) {
    return errorResponse('username and password are required', 400);
  }

  const user = await queryFirst<UserRow>(
    env.DB.prepare(
      'SELECT id, username, salt, password_hash FROM users WHERE username = ?1',
    ).bind(username),
  );
  if (!user) {
    return errorResponse('Invalid username or password', 401);
  }

  const valid = await verifyPassword(password, user.salt, user.password_hash);
  if (!valid) {
    return errorResponse('Invalid username or password', 401);
  }

  const token = await signJwt({ sub: user.id, username, role: 'user' }, env.JWT_SECRET);
  return json({ token });
}

async function handleMe(userId: string, username: string): Promise<Response> {
  return json({ id: userId, username });
}

async function handleGetClips(env: Env, userId: string): Promise<Response> {
  const rows = await queryAll<ClipRow>(
    env.DB.prepare(
      `
        SELECT id, text, created_at
        FROM clips
        WHERE user_id = ?1
        ORDER BY created_at DESC, id DESC
        LIMIT ?2
      `,
    ).bind(userId, MAX_CLIPS_PER_USER),
  );
  return json({ clips: rows.map(toClipItem) });
}

async function handleGetClipsDelta(
  request: Request,
  env: Env,
  userId: string,
): Promise<Response> {
  const url = new URL(request.url);
  const since = url.searchParams.get('since') ?? '';
  const statement = since
    ? env.DB.prepare(
        `
          SELECT id, text, created_at
          FROM clips
          WHERE user_id = ?1 AND created_at > ?2
          ORDER BY created_at DESC, id DESC
          LIMIT ?3
        `,
      ).bind(userId, since, MAX_CLIPS_PER_USER)
    : env.DB.prepare(
        `
          SELECT id, text, created_at
          FROM clips
          WHERE user_id = ?1
          ORDER BY created_at DESC, id DESC
          LIMIT ?2
        `,
      ).bind(userId, MAX_CLIPS_PER_USER);
  const rows = await queryAll<ClipRow>(statement);
  return json({ clips: rows.map(toClipItem), serverTime: nowIso() });
}

async function handlePostClip(
  request: Request,
  env: Env,
  userId: string,
): Promise<Response> {
  const body = await readJsonBody<{ text?: string }>(request);
  if (!body.text) {
    return errorResponse('text is required', 400);
  }

  const clip: ClipItem = {
    id: crypto.randomUUID(),
    text: body.text,
    createdAt: nowIso(),
  };

  await env.DB.batch([
    env.DB.prepare(
      `
        INSERT INTO clips (id, user_id, text, created_at)
        VALUES (?1, ?2, ?3, ?4)
      `,
    ).bind(clip.id, userId, clip.text, clip.createdAt),
    env.DB.prepare(
      `
        DELETE FROM clips
        WHERE user_id = ?1
          AND id NOT IN (
            SELECT id
            FROM clips
            WHERE user_id = ?2
            ORDER BY created_at DESC, id DESC
            LIMIT ?3
          )
      `,
    ).bind(userId, userId, MAX_CLIPS_PER_USER),
  ]);

  return json({ clip }, 201);
}

async function handleDeleteClip(
  env: Env,
  userId: string,
  clipId: string,
): Promise<Response> {
  const result = await env.DB.prepare(
    'DELETE FROM clips WHERE user_id = ?1 AND id = ?2',
  )
    .bind(userId, clipId)
    .run();
  if ((result.meta.changes ?? 0) < 1) {
    return errorResponse('Clip not found', 404);
  }
  return json({ ok: true });
}

async function handleAdminListUsers(env: Env): Promise<Response> {
  const users = await queryAll<Pick<UserRecord, 'id'> & { username: string }>(
    env.DB.prepare(
      `
        SELECT id, username
        FROM users
        ORDER BY username COLLATE NOCASE ASC, id ASC
      `,
    ),
  );
  return json({ users });
}

async function handleAdminDeleteUsers(request: Request, env: Env): Promise<Response> {
  const body = await readJsonBody<{ usernames?: unknown }>(request);
  if (!Array.isArray(body.usernames)) {
    return errorResponse('usernames must be an array', 400);
  }

  const usernames = [...new Set(body.usernames.map(normalizeUsername).filter(Boolean))];
  if (!usernames.length) {
    return errorResponse('At least one username is required', 400);
  }

  if (usernames.length > MAX_ADMIN_DELETE_USERS) {
    return errorResponse(`Cannot delete more than ${MAX_ADMIN_DELETE_USERS} users at once`, 400);
  }

  const usernamePlaceholders = usernames.map((_, index) => `?${index + 1}`).join(', ');
  const deleted = await queryAll<Pick<UserRecord, 'id'> & { username: string }>(
    env.DB.prepare(
      `
        SELECT id, username
        FROM users
        WHERE username IN (${usernamePlaceholders})
        ORDER BY username COLLATE NOCASE ASC, id ASC
      `,
    ).bind(...usernames),
  );
  const deletedSet = new Set(deleted.map((user) => user.username));
  const notFound = usernames.filter((username) => !deletedSet.has(username));

  if (deleted.length > 0) {
    const userIds = deleted.map((user) => user.id);
    const userIdPlaceholders = userIds.map((_, index) => `?${index + 1}`).join(', ');
    await env.DB.batch([
      env.DB.prepare(`DELETE FROM qr_login_sessions WHERE user_id IN (${userIdPlaceholders})`).bind(
        ...userIds,
      ),
      env.DB.prepare(`DELETE FROM clips WHERE user_id IN (${userIdPlaceholders})`).bind(...userIds),
      env.DB.prepare(`DELETE FROM users WHERE id IN (${userIdPlaceholders})`).bind(...userIds),
    ]);
  }

  return json({ ok: true, deleted, notFound });
}

async function handleAdminGetSettings(env: Env): Promise<Response> {
  const settings = await getSettings(env);
  return json(settings);
}

async function handleAdminUpdateSettings(request: Request, env: Env): Promise<Response> {
  const body = await readJsonBody<{ allowRegistration?: unknown }>(request);
  if (typeof body.allowRegistration !== 'boolean') {
    return errorResponse('allowRegistration must be a boolean', 400);
  }

  const settings: AppSettings = { allowRegistration: body.allowRegistration };
  await saveSettings(env, settings);
  return json({ ok: true, settings });
}

export default {
  async fetch(request: Request, env: Env): Promise<Response> {
    if (request.method === 'OPTIONS') {
      return new Response(null, { status: 204, headers: CORS_HEADERS });
    }

    const url = new URL(request.url);
    const path = url.pathname;

    if (!path.startsWith('/api')) {
      return new Response(null, { status: 404 });
    }

    try {
      if (path === '/api/auth/qr-redeem' && request.method === 'POST') {
        return await handleQrRedeem(request, env);
      }

      if (path === '/api/register-status' && request.method === 'GET') {
        return await handleRegisterStatus(env);
      }

      if (path === '/api/register' && request.method === 'POST') {
        return await handleRegister(request, env);
      }

      if (path === '/api/login' && request.method === 'POST') {
        return await handleLogin(request, env);
      }

      if (path === '/api/admin/login' && request.method === 'POST') {
        return await handleAdminLogin(request, env);
      }

      if (path.startsWith('/api/admin/')) {
        const adminAuth = await authenticateAdmin(request, env);
        if (adminAuth instanceof Response) return adminAuth;

        if (path === '/api/admin/users' && request.method === 'GET') {
          return await handleAdminListUsers(env);
        }

        if (path === '/api/admin/users' && request.method === 'DELETE') {
          return await handleAdminDeleteUsers(request, env);
        }

        if (path === '/api/admin/settings' && request.method === 'GET') {
          return await handleAdminGetSettings(env);
        }

        if (path === '/api/admin/settings' && request.method === 'PUT') {
          return await handleAdminUpdateSettings(request, env);
        }

        return errorResponse('Not found', 404);
      }

      const authResult = await authenticateUser(request, env);
      if (authResult instanceof Response) return authResult;
      const userId = authResult.sub;

      if (path === '/api/auth/qr-session' && request.method === 'POST') {
        return await handleQrSession(env, userId, authResult.username);
      }

      if (path === '/api/me' && request.method === 'GET') {
        return await handleMe(userId, authResult.username);
      }

      if (path === '/api/clips' && request.method === 'GET') {
        return await handleGetClips(env, userId);
      }

      if (path === '/api/clips/delta' && request.method === 'GET') {
        return await handleGetClipsDelta(request, env, userId);
      }

      if (path === '/api/clips' && request.method === 'POST') {
        return await handlePostClip(request, env, userId);
      }

      const deleteMatch = path.match(/^\/api\/clips\/([^/]+)$/);
      if (deleteMatch && request.method === 'DELETE') {
        return await handleDeleteClip(env, userId, deleteMatch[1]);
      }

      return errorResponse('Not found', 404);
    } catch (err) {
      const message = err instanceof Error ? err.message : 'Internal server error';
      return errorResponse(message, 500);
    }
  },
} satisfies ExportedHandler<Env>;
