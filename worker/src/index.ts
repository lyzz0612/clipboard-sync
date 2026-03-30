import { hashPassword, verifyPassword, signJwt, verifyJwt } from './auth';
import type { AppSettings, ClipItem, Env, JwtPayload, UserRecord } from './types';

const CORS_HEADERS: Record<string, string> = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Methods': 'GET, POST, PUT, DELETE, OPTIONS',
  'Access-Control-Allow-Headers': 'Content-Type, Authorization',
};

/** KV 中每个用户最多保留的剪贴条数（新插入在前，超出则丢弃最旧的） */
const MAX_CLIPS_PER_USER = 10;

/** 扫码登录一次性码：KV 存活时间（秒），仅存储 userId/username，不含密码 */
const QR_LOGIN_TTL_SEC = 300;

const SETTINGS_KEY = 'SYS:SETTINGS';
const USER_KEY_PREFIX = 'USER:';
const CLIPS_KEY_PREFIX = 'CLIPS:';
const MAX_ADMIN_DELETE_USERS = 100;

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

async function getSettings(env: Env): Promise<AppSettings> {
  const raw = await env.KV.get(SETTINGS_KEY);
  if (!raw) {
    return { allowRegistration: true };
  }

  const parsed = JSON.parse(raw) as Partial<AppSettings>;
  return {
    allowRegistration: parsed.allowRegistration !== false,
  };
}

async function saveSettings(env: Env, settings: AppSettings): Promise<void> {
  await env.KV.put(SETTINGS_KEY, JSON.stringify(settings));
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

  const existing = await env.KV.get(`${USER_KEY_PREFIX}${username}`);
  if (existing) {
    return errorResponse('Username already taken', 409);
  }

  const { salt, hash } = await hashPassword(password);
  const id = crypto.randomUUID();
  const record: UserRecord = { id, salt, hash };
  await env.KV.put(`${USER_KEY_PREFIX}${username}`, JSON.stringify(record));

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
  const key = `QRLOGIN:${code}`;
  await env.KV.put(key, JSON.stringify({ sub: userId, username }), {
    expirationTtl: QR_LOGIN_TTL_SEC,
  });
  return json({ code, expiresIn: QR_LOGIN_TTL_SEC });
}

async function handleQrRedeem(request: Request, env: Env): Promise<Response> {
  const body = await readJsonBody<{ code?: string }>(request);
  const rawCode = typeof body.code === 'string' ? body.code.trim().toLowerCase() : '';
  if (!rawCode || !/^[0-9a-f]{48}$/.test(rawCode)) {
    return errorResponse('invalid code', 400);
  }

  const key = `QRLOGIN:${rawCode}`;
  const raw = await env.KV.get(key);
  if (!raw) {
    return errorResponse('invalid or expired code', 401);
  }

  await env.KV.delete(key);
  const { sub, username } = JSON.parse(raw) as { sub: string; username: string };
  const token = await signJwt({ sub, username, role: 'user' }, env.JWT_SECRET);
  return json({ token, username });
}

async function handleLogin(request: Request, env: Env): Promise<Response> {
  const body = await readJsonBody<{ username?: string; password?: string }>(request);
  const username = normalizeUsername(body.username);
  const password = typeof body.password === 'string' ? body.password : '';
  if (!username || !password) {
    return errorResponse('username and password are required', 400);
  }

  const raw = await env.KV.get(`${USER_KEY_PREFIX}${username}`);
  if (!raw) {
    return errorResponse('Invalid username or password', 401);
  }

  const user = JSON.parse(raw) as UserRecord;
  const valid = await verifyPassword(password, user.salt, user.hash);
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
  const raw = await env.KV.get(`${CLIPS_KEY_PREFIX}${userId}`);
  const clips: ClipItem[] = raw ? JSON.parse(raw) : [];
  return json({ clips: clips.slice(0, MAX_CLIPS_PER_USER) });
}

async function handleGetClipsDelta(
  request: Request,
  env: Env,
  userId: string,
): Promise<Response> {
  const url = new URL(request.url);
  const since = url.searchParams.get('since') ?? '';
  const raw = await env.KV.get(`${CLIPS_KEY_PREFIX}${userId}`);
  const clips: ClipItem[] = raw ? JSON.parse(raw) : [];
  const newest = clips.slice(0, MAX_CLIPS_PER_USER);
  const filtered = since ? newest.filter((clip) => clip.createdAt > since) : newest;
  return json({ clips: filtered, serverTime: new Date().toISOString() });
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
    createdAt: new Date().toISOString(),
  };

  const raw = await env.KV.get(`${CLIPS_KEY_PREFIX}${userId}`);
  const clips: ClipItem[] = raw ? JSON.parse(raw) : [];
  clips.unshift(clip);
  if (clips.length > MAX_CLIPS_PER_USER) clips.length = MAX_CLIPS_PER_USER;
  await env.KV.put(`${CLIPS_KEY_PREFIX}${userId}`, JSON.stringify(clips));

  return json({ clip }, 201);
}

async function handleDeleteClip(
  env: Env,
  userId: string,
  clipId: string,
): Promise<Response> {
  const raw = await env.KV.get(`${CLIPS_KEY_PREFIX}${userId}`);
  const clips: ClipItem[] = raw ? JSON.parse(raw) : [];
  const index = clips.findIndex((clip) => clip.id === clipId);
  if (index === -1) {
    return errorResponse('Clip not found', 404);
  }

  clips.splice(index, 1);
  await env.KV.put(`${CLIPS_KEY_PREFIX}${userId}`, JSON.stringify(clips));
  return json({ ok: true });
}

async function listAllUserKeys(env: Env): Promise<string[]> {
  const keys: string[] = [];
  let cursor: string | undefined;

  do {
    const page = await env.KV.list({ prefix: USER_KEY_PREFIX, cursor });
    keys.push(...page.keys.map((entry) => entry.name));
    cursor = page.list_complete ? undefined : page.cursor;
  } while (cursor);

  return keys;
}

async function handleAdminListUsers(env: Env): Promise<Response> {
  const keys = await listAllUserKeys(env);
  const users = (
    await Promise.all(
      keys.map(async (keyName) => {
        const raw = await env.KV.get(keyName);
        if (!raw) return null;
        const record = JSON.parse(raw) as UserRecord;
        return {
          id: record.id,
          username: keyName.slice(USER_KEY_PREFIX.length),
        };
      }),
    )
  )
    .filter((user): user is { id: string; username: string } => Boolean(user))
    .sort((left, right) => left.username.localeCompare(right.username));

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

  const deleted: Array<{ id: string; username: string }> = [];
  const notFound: string[] = [];

  for (const username of usernames) {
    const userKey = `${USER_KEY_PREFIX}${username}`;
    const raw = await env.KV.get(userKey);
    if (!raw) {
      notFound.push(username);
      continue;
    }

    const record = JSON.parse(raw) as UserRecord;
    await Promise.all([env.KV.delete(userKey), env.KV.delete(`${CLIPS_KEY_PREFIX}${record.id}`)]);
    deleted.push({ id: record.id, username });
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
