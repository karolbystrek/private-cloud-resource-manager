import { cookies } from 'next/headers';
import { NextResponse } from 'next/server';

const BACKEND_URL = process.env.NEXT_PUBLIC_BACKEND_URL;

export async function POST() {
  try {
    const cookieStore = await cookies();
    const refreshToken = cookieStore.get('refresh_token')?.value;

    if (refreshToken) {
      // Notify Spring Boot to invalidate the token in the database/cache
      await fetch(`${BACKEND_URL}/api/auth/logout`, {
        method: 'POST',
        headers: {
          Cookie: `refresh_token=${refreshToken}`,
        },
      });
    }

    const nextResponse = NextResponse.json({ success: true }, { status: 200 });

    // Clear the Next.js access token cookie
    nextResponse.cookies.delete('access_token');
    nextResponse.cookies.delete('user_role');

    // Clear the Spring Boot refresh token cookie by setting it to expire immediately
    nextResponse.cookies.set('refresh_token', '', {
      httpOnly: true,
      secure: process.env.NODE_ENV === 'production',
      sameSite: 'lax',
      path: '/',
      maxAge: 0,
    });

    return nextResponse;
  } catch (error) {
    return NextResponse.json(
      { error: 'Internal Server Error' },
      { status: 500 },
    );
  }
}
