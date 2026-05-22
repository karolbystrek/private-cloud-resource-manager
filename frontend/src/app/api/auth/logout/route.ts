import { NextResponse } from 'next/server';
import { clearAuthCookies } from '@/lib/auth';

export async function POST() {
  const nextResponse = NextResponse.json({ success: true }, { status: 200 });
  clearAuthCookies(nextResponse);

  return nextResponse;
}
