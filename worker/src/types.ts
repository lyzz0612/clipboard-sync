export interface Env {
  DB: D1Database;
  JWT_SECRET: string;
  ADMIN_PASSWORD?: string;
}

export interface UserRecord {
  id: string;
  username: string;
  salt: string;
  passwordHash: string;
}

export interface ClipItem {
  id: string;
  text: string;
  createdAt: string;
}

export interface JwtPayload {
  sub: string;
  username: string;
  role?: 'user' | 'admin';
  iat: number;
  exp: number;
}

export interface AppSettings {
  allowRegistration: boolean;
}
