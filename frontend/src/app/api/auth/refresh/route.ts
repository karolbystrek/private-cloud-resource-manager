import { createClient } from '@supabase/supabase-js';
import { cookies } from 'next/headers';
import { NextResponse } from 'next/server';
import { clearAuthCookies, REFRESH_TOKEN_COOKIE, setAuthCookies } from '@/lib/auth';
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

export async function POST() {
  try {
    const cookieStore = await cookies();
    const refreshToken = cookieStore.get(REFRESH_TOKEN_COOKIE)?.value;

    if (!refreshToken) {
      return refreshFailedResponse();
    }

    const url = process.env.SUPABASE_URL;
    const anonKey = process.env.NEXT_PUBLIC_SUPABASE_ANON_KEY;
    if (!url || !anonKey) {
      return NextResponse.json({ error: 'Server misconfiguration.' }, { status: 500 });
    }

    const supabase = createClient(url, anonKey);
    const { data, error } = await supabase.auth.refreshSession({ refresh_token: refreshToken });
    if (error || !data.session) {
      return refreshFailedResponse();
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

    const response = NextResponse.json({ success: true }, { status: 200 });
    setAuthCookies(response, accessToken, nextRefresh, data.session.expires_in, role);

    return response;
  } catch {
    return NextResponse.json({ error: 'Internal Server Error' }, { status: 500 });
  }
}
