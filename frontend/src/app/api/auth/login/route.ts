import { NextResponse } from 'next/server';
import { isUserRole, type UserRole } from '@/lib/user-role';

const BACKEND_URL = process.env.NEXT_PUBLIC_BACKEND_URL;
const USER_ROLE_COOKIE_MAX_AGE_SECONDS = 60 * 60 * 24 * 7;

type AuthenticationResponse = {
  accessToken: string;
  role: UserRole;
};

export async function POST(request: Request) {
  try {
    const body = await request.json();

    const backendResponse = await fetch(`${BACKEND_URL}/api/auth/login`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(body),
    });

    if (!backendResponse.ok) {
      return NextResponse.json(
        { error: 'Authentication failed.' },
        { status: backendResponse.status },
      );
    }

    const data = (await backendResponse.json()) as AuthenticationResponse;
    if (!isUserRole(data.role) || typeof data.accessToken !== 'string') {
      return NextResponse.json({ error: 'Invalid authentication response.' }, { status: 502 });
    }

    const backendSetCookie = backendResponse.headers.get('set-cookie');
    const response = NextResponse.json({ success: true }, { status: 200 });

    if (backendSetCookie) {
      response.headers.append('Set-Cookie', backendSetCookie);
    }

    response.cookies.set('access_token', data.accessToken, {
      httpOnly: true,
      secure: process.env.NODE_ENV === 'production',
      sameSite: 'lax',
      path: '/',
      maxAge: 15 * 60, // match backend JWT expiration
    });

    response.cookies.set('user_role', data.role, {
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
