export interface Env {
  KV: KVNamespace;
  JWT_SECRET: string;
}

export interface UserRecord {
  id: string;
  salt: string;
  hash: string;
}

export interface ClipItem {
  id: string;
  text: string;
  createdAt: string;
}

export interface JwtPayload {
  sub: string;
  username: string;
  iat: number;
  exp: number;
}
