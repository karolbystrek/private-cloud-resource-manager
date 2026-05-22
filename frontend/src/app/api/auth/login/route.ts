import { createClient } from '@supabase/supabase-js';
import { NextResponse } from 'next/server';
import { setAuthCookies } from '@/lib/auth';
import { getBackendUrlForServer } from '@/lib/backend-url';
import { isUserRole, type UserRole } from '@/lib/user-role';

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

    const response = NextResponse.json({ success: true }, { status: 200 });
    setAuthCookies(response, accessToken, refreshToken, data.session.expires_in, role);

    return response;
  } catch {
    return NextResponse.json({ error: 'Internal Server Error' }, { status: 500 });
  }
}
