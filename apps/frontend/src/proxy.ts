import { NextResponse, type NextRequest } from 'next/server';

const LOGIN_PATH = '/login';
const SIGNUP_PATH = '/signup';
const AUTH_PATHS = new Set([LOGIN_PATH, SIGNUP_PATH]);

function isSafeInternalPath(value: string | null): value is string {
  return Boolean(value && value.startsWith('/') && !value.startsWith('//'));
}

function isAuthenticated(request: NextRequest): boolean {
  const accessToken = request.cookies.get('access_token')?.value;
  const refreshToken = request.cookies.get('refresh_token')?.value;
  return Boolean(accessToken || refreshToken);
}

export function proxy(request: NextRequest) {
  const { pathname, search } = request.nextUrl;
  const hasSession = isAuthenticated(request);
  const isAuthPath = AUTH_PATHS.has(pathname);

  if (isAuthPath && hasSession) {
    const nextParam = request.nextUrl.searchParams.get('next');
    const redirectTarget
      = isSafeInternalPath(nextParam) && !AUTH_PATHS.has(nextParam)
        ? nextParam
        : '/';
    return NextResponse.redirect(new URL(redirectTarget, request.url));
  }

  if (!isAuthPath && !hasSession) {
    const loginUrl = request.nextUrl.clone();
    loginUrl.pathname = LOGIN_PATH;
    loginUrl.searchParams.set('next', `${pathname}${search}`);
    return NextResponse.redirect(loginUrl);
  }

  return NextResponse.next();
}

export const config = {
  matcher: ['/((?!api|_next/static|_next/image|favicon.ico).*)'],
};
