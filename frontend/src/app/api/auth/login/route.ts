import { createClient } from '@supabase/supabase-js';
import { NextResponse } from 'next/server';
import { getBackendUrlForServer } from '@/lib/backend-url';
import { isUserRole, type UserRole } from '@/lib/user-role';

const brokerBaseUrl = getBackendUrlForServer();
const USER_ROLE_COOKIE_MAX_AGE_SECONDS = 60 * 60 * 24 * 7;

type QuotaMeResponse = {
  role: string;
};

export async function POST(request: Request) {
  try {
    const body = (await request.json()) as { email?: string; password?: string };
    const email = body.email?.trim();
    const password = body.password;
    if (!email || !password) {
      return NextResponse.json({ error: 'Email and password are required.' }, { status: 400 });
    }

    const url = process.env.SUPABASE_URL;
    const anonKey = process.env.NEXT_PUBLIC_SUPABASE_ANON_KEY;
    if (!url || !anonKey) {
      return NextResponse.json({ error: 'Server misconfiguration.' }, { status: 500 });
    }

    const supabase = createClient(url, anonKey);
    const { data, error } = await supabase.auth.signInWithPassword({ email, password });
    if (error || !data.session) {
      return NextResponse.json({ error: 'Authentication failed.' }, { status: 401 });
    }

    const accessToken = data.session.access_token;
    const refreshToken = data.session.refresh_token;

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

    const response = NextResponse.json({ success: true }, { status: 200 });

    const accessMaxAge = Math.max(60, (data.session.expires_in ?? 3600) - 30);
    response.cookies.set('access_token', accessToken, {
      httpOnly: true,
      secure: process.env.NODE_ENV === 'production',
      sameSite: 'lax',
      path: '/',
      maxAge: accessMaxAge,
    });

    if (refreshToken) {
      response.cookies.set('refresh_token', refreshToken, {
        httpOnly: true,
        secure: process.env.NODE_ENV === 'production',
        sameSite: 'lax',
        path: '/',
        maxAge: 60 * 60 * 24 * 30,
      });
    }

    response.cookies.set('user_role', role, {
      httpOnly: true,
      secure: process.env.NODE_ENV === 'production',
      sameSite: 'lax',
      path: '/',
      maxAge: USER_ROLE_COOKIE_MAX_AGE_SECONDS,
    });

    return response;
  } catch {
    return NextResponse.json({ error: 'Internal Server Error' }, { status: 500 });
  }
}
