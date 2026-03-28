import { hashPassword, verifyPassword, signJwt, verifyJwt } from './auth';
import type { Env, UserRecord, ClipItem, JwtPayload } from './types';

const CORS_HEADERS: Record<string, string> = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Methods': 'GET, POST, PUT, DELETE, OPTIONS',
  'Access-Control-Allow-Headers': 'Content-Type, Authorization',
};

/** KV 中每个用户最多保留的剪贴条数（新插入在前，超出则丢弃最旧的） */
const MAX_CLIPS_PER_USER = 10;

/** 扫码登录一次性码：KV 存活时间（秒），仅存储 userId/username，不含密码 */
const QR_LOGIN_TTL_SEC = 300;

function json(data: unknown, status = 200): Response {
  return new Response(JSON.stringify(data), {
    status,
    headers: { 'Content-Type': 'application/json', ...CORS_HEADERS },
  });
}

function errorResponse(error: string, status: number): Response {
  return json({ error }, status);
}

async function authenticate(request: Request, env: Env): Promise<JwtPayload | Response> {
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

async function handleRegister(request: Request, env: Env): Promise<Response> {
  const body = (await request.json()) as { username?: string; password?: string };
  const { username, password } = body;
  if (!username || !password) {
    return errorResponse('username and password are required', 400);
  }

  const existing = await env.KV.get(`USER:${username}`);
  if (existing) {
    return errorResponse('Username already taken', 409);
  }

  const { salt, hash } = await hashPassword(password);
  const id = crypto.randomUUID();
  const record: UserRecord = { id, salt, hash };
  await env.KV.put(`USER:${username}`, JSON.stringify(record));

  return json({ ok: true, message: 'registered' }, 201);
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
  const body = (await request.json()) as { code?: string };
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
  const token = await signJwt({ sub, username }, env.JWT_SECRET);
  return json({ token, username });
}

async function handleLogin(request: Request, env: Env): Promise<Response> {
  const body = (await request.json()) as { username?: string; password?: string };
  const { username, password } = body;
  if (!username || !password) {
    return errorResponse('username and password are required', 400);
  }

  const raw = await env.KV.get(`USER:${username}`);
  if (!raw) {
    return errorResponse('Invalid username or password', 401);
  }

  const user = JSON.parse(raw) as UserRecord;
  const valid = await verifyPassword(password, user.salt, user.hash);
  if (!valid) {
    return errorResponse('Invalid username or password', 401);
  }

  const token = await signJwt({ sub: user.id, username }, env.JWT_SECRET);
  return json({ token });
}

async function handleGetClips(env: Env, userId: string): Promise<Response> {
  const raw = await env.KV.get(`CLIPS:${userId}`);
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
  const raw = await env.KV.get(`CLIPS:${userId}`);
  const clips: ClipItem[] = raw ? JSON.parse(raw) : [];
  const newest = clips.slice(0, MAX_CLIPS_PER_USER);
  const filtered = since ? newest.filter((c) => c.createdAt > since) : newest;
  return json({ clips: filtered, serverTime: new Date().toISOString() });
}

async function handlePostClip(
  request: Request,
  env: Env,
  userId: string,
): Promise<Response> {
  const body = (await request.json()) as { text?: string };
  if (!body.text) {
    return errorResponse('text is required', 400);
  }

  const clip: ClipItem = {
    id: crypto.randomUUID(),
    text: body.text,
    createdAt: new Date().toISOString(),
  };

  const raw = await env.KV.get(`CLIPS:${userId}`);
  const clips: ClipItem[] = raw ? JSON.parse(raw) : [];
  clips.unshift(clip);
  if (clips.length > MAX_CLIPS_PER_USER) clips.length = MAX_CLIPS_PER_USER;
  await env.KV.put(`CLIPS:${userId}`, JSON.stringify(clips));

  return json({ clip }, 201);
}

async function handleDeleteClip(
  env: Env,
  userId: string,
  clipId: string,
): Promise<Response> {
  const raw = await env.KV.get(`CLIPS:${userId}`);
  const clips: ClipItem[] = raw ? JSON.parse(raw) : [];
  const idx = clips.findIndex((c) => c.id === clipId);
  if (idx === -1) {
    return errorResponse('Clip not found', 404);
  }
  clips.splice(idx, 1);
  await env.KV.put(`CLIPS:${userId}`, JSON.stringify(clips));
  return json({ ok: true });
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

      if (path === '/api/register' && request.method === 'POST') {
        return await handleRegister(request, env);
      }

      if (path === '/api/login' && request.method === 'POST') {
        return await handleLogin(request, env);
      }

      const authResult = await authenticate(request, env);
      if (authResult instanceof Response) return authResult;
      const userId = authResult.sub;

      if (path === '/api/auth/qr-session' && request.method === 'POST') {
        return await handleQrSession(env, userId, authResult.username);
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
