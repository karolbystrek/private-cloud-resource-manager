import { createClient } from '@supabase/supabase-js';
import { cookies } from 'next/headers';
import { NextRequest, NextResponse } from 'next/server';
import {
  buildLoginPath,
  clearAuthCookies,
  isSafeRedirectTarget,
  REFRESH_TOKEN_COOKIE,
  setAuthCookies,
} from '@/lib/auth';
import { getBackendUrlForServer } from '@/lib/backend-url';
import { isUserRole, type UserRole } from '@/lib/user-role';

type QuotaMeResponse = {
  role: string;
};

function refreshFailedResponse() {
  const response = NextResponse.json({ error: 'Refresh failed' }, { status: 401 });
  clearAuthCookies(response);
  return response;
}

function getSafeNextPath(request: NextRequest): string {
  const nextPath = request.nextUrl.searchParams.get('next');
  return isSafeRedirectTarget(nextPath) ? nextPath : '/';
}

function refreshFailedRedirect(request: NextRequest) {
  const nextPath = getSafeNextPath(request);
  const response = NextResponse.redirect(new URL(buildLoginPath(nextPath), request.url));
  clearAuthCookies(response);
  return response;
}

async function refreshSession() {
  try {
    const cookieStore = await cookies();
    const refreshToken = cookieStore.get(REFRESH_TOKEN_COOKIE)?.value;

    if (!refreshToken) {
      return { ok: false as const, status: 401 as const };
    }

    const url = process.env.SUPABASE_URL;
    const anonKey = process.env.NEXT_PUBLIC_SUPABASE_ANON_KEY;
    if (!url || !anonKey) {
      return { ok: false as const, status: 500 as const };
    }

    const supabase = createClient(url, anonKey);
    const { data, error } = await supabase.auth.refreshSession({ refresh_token: refreshToken });
    if (error || !data.session) {
      return { ok: false as const, status: 401 as const };
    }

    const accessToken = data.session.access_token;
    const nextRefresh = data.session.refresh_token ?? refreshToken;

    const brokerBaseUrl = getBackendUrlForServer();
    const quotaResponse = await fetch(`${brokerBaseUrl}/api/quota/me`, {
      headers: { Authorization: `Bearer ${accessToken}` },
    });

    let role: UserRole = 'STUDENT';
    if (quotaResponse.ok) {
      const quota = (await quotaResponse.json()) as QuotaMeResponse;
      if (isUserRole(quota.role)) {
        role = quota.role;
      }
    }

    return {
      ok: true as const,
      accessToken,
      refreshToken: nextRefresh,
      expiresIn: data.session.expires_in,
      role,
    };
  } catch {
    return { ok: false as const, status: 500 as const };
  }
}

export async function GET(request: NextRequest) {
  const result = await refreshSession();

  if (!result.ok) {
    return refreshFailedRedirect(request);
  }

  const response = NextResponse.redirect(new URL(getSafeNextPath(request), request.url));
  setAuthCookies(response, result.accessToken, result.refreshToken, result.expiresIn, result.role);

  return response;
}

export async function POST() {
  const result = await refreshSession();

  if (!result.ok) {
    if (result.status === 500) {
      return NextResponse.json({ error: 'Internal Server Error' }, { status: 500 });
    }
    return refreshFailedResponse();
  }

  try {
    const response = NextResponse.json({ success: true }, { status: 200 });
    setAuthCookies(response, result.accessToken, result.refreshToken, result.expiresIn, result.role);

    return response;
  } catch {
    return NextResponse.json({ error: 'Internal Server Error' }, { status: 500 });
  }
}
