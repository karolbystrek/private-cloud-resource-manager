import type { NextResponse } from 'next/server';
import type { UserRole } from '@/lib/user-role';

export const ACCESS_TOKEN_COOKIE = 'access_token';
export const REFRESH_TOKEN_COOKIE = 'refresh_token';
export const USER_ROLE_COOKIE = 'user_role';

const USER_ROLE_COOKIE_MAX_AGE_SECONDS = 60 * 60 * 24 * 7;
const REFRESH_TOKEN_COOKIE_MAX_AGE_SECONDS = 60 * 60 * 24 * 30;

const secureCookie = process.env.NODE_ENV === 'production';

const baseCookieOptions = {
  httpOnly: true,
  secure: secureCookie,
  sameSite: 'lax',
  path: '/',
} as const;

export function isSafeRedirectTarget(value: string | null): value is string {
  return Boolean(value && value.startsWith('/') && !value.startsWith('//'));
}

export function buildLoginPath(nextPath: string): string {
  const loginPath = '/login';
  if (!isSafeRedirectTarget(nextPath) || nextPath === loginPath) {
    return loginPath;
  }

  const params = new URLSearchParams({ next: nextPath });
  return `${loginPath}?${params.toString()}`;
}

export function buildClearSessionPath(nextPath: string): string {
  const params = new URLSearchParams();
  if (isSafeRedirectTarget(nextPath)) {
    params.set('next', nextPath);
  }
  const query = params.toString();
  return `/api/auth/clear-session${query ? `?${query}` : ''}`;
}

export function clearAuthCookies(response: NextResponse): void {
  response.cookies.delete(ACCESS_TOKEN_COOKIE);
  response.cookies.delete(REFRESH_TOKEN_COOKIE);
  response.cookies.delete(USER_ROLE_COOKIE);
}

export function setAuthCookies(
  response: NextResponse,
  accessToken: string,
  refreshToken: string | null | undefined,
  expiresInSeconds: number | null | undefined,
  role: UserRole,
): void {
  const accessMaxAge = Math.max(60, (expiresInSeconds ?? 3600) - 30);

  response.cookies.set(ACCESS_TOKEN_COOKIE, accessToken, {
    ...baseCookieOptions,
    maxAge: accessMaxAge,
  });

  if (refreshToken) {
    response.cookies.set(REFRESH_TOKEN_COOKIE, refreshToken, {
      ...baseCookieOptions,
      maxAge: REFRESH_TOKEN_COOKIE_MAX_AGE_SECONDS,
    });
  }

  response.cookies.set(USER_ROLE_COOKIE, role, {
    ...baseCookieOptions,
    maxAge: USER_ROLE_COOKIE_MAX_AGE_SECONDS,
  });
}
