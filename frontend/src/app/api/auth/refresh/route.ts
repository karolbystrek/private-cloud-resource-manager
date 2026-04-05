import { cookies } from 'next/headers';
import { NextResponse } from 'next/server';

const BACKEND_URL = process.env.NEXT_PUBLIC_BACKEND_URL;

export async function POST() {
  try {
    const cookieStore = await cookies();
    const refreshToken = cookieStore.get('refresh_token')?.value;
    console.log('Refresh token from cookie:', refreshToken);

    if (!refreshToken) {
      return NextResponse.json({ error: 'No refresh token.' }, { status: 401 });
    }

    const backendResponse = await fetch(`${BACKEND_URL}/api/auth/refresh`, {
      method: 'POST',
      headers: {
        Cookie: `refresh_token=${refreshToken}`,
      },
    });

    if (!backendResponse.ok) {
      return NextResponse.json(
        { error: 'Refresh failed' },
        { status: backendResponse.status },
      );
    }

    const data = await backendResponse.json();
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
      maxAge: 15 * 60,
    });

    return response;
  } catch (error) {
    return NextResponse.json(
      { error: 'Internal Server Error' },
      { status: 500 },
    );
  }
}
