import { NextResponse, type NextRequest } from 'next/server';
import {
  ACCESS_TOKEN_COOKIE,
  buildLoginPath,
  buildRefreshPath,
  isSafeRedirectTarget,
  REFRESH_TOKEN_COOKIE,
} from '@/lib/auth';

const LOGIN_PATH = '/login';
const SIGNUP_PATH = '/signup';
const AUTH_PATHS = new Set([LOGIN_PATH, SIGNUP_PATH]);

function isAuthRedirectTarget(target: string, requestUrl: string): boolean {
  return AUTH_PATHS.has(new URL(target, requestUrl).pathname);
}

export function proxy(request: NextRequest) {
  const { pathname, search } = request.nextUrl;
  const hasAccessToken = Boolean(request.cookies.get(ACCESS_TOKEN_COOKIE)?.value);
  const hasRefreshToken = Boolean(request.cookies.get(REFRESH_TOKEN_COOKIE)?.value);
  const isAuthPath = AUTH_PATHS.has(pathname);

  if (isAuthPath && hasAccessToken) {
    const nextParam = request.nextUrl.searchParams.get('next');
    const redirectTarget
      = isSafeRedirectTarget(nextParam) && !isAuthRedirectTarget(nextParam, request.url)
        ? nextParam
        : '/';
    return NextResponse.redirect(new URL(redirectTarget, request.url));
  }

  if (hasRefreshToken && !hasAccessToken) {
    const nextParam = request.nextUrl.searchParams.get('next');
    const nextPath
      = isAuthPath
        ? isSafeRedirectTarget(nextParam) && !isAuthRedirectTarget(nextParam, request.url)
          ? nextParam
          : '/'
        : `${pathname}${search}`;
    return NextResponse.redirect(new URL(buildRefreshPath(nextPath), request.url));
  }

  if (!isAuthPath && !hasAccessToken) {
    const loginUrl = request.nextUrl.clone();
    const loginPath = buildLoginPath(`${pathname}${search}`);
    loginUrl.pathname = LOGIN_PATH;
    loginUrl.search = new URL(loginPath, request.url).search;
    return NextResponse.redirect(loginUrl);
  }

  return NextResponse.next();
}

export const config = {
  matcher: ['/((?!api|_next/static|_next/image|favicon.ico).*)'],
};
