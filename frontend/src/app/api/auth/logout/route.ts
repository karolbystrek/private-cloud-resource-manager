import { NextResponse } from 'next/server';

export async function POST() {
  const nextResponse = NextResponse.json({ success: true }, { status: 200 });

  nextResponse.cookies.delete('access_token');
  nextResponse.cookies.delete('user_role');
  nextResponse.cookies.delete('refresh_token');

  return nextResponse;
}
