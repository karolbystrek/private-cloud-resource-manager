import { NextRequest, NextResponse } from 'next/server';
import { buildLoginPath, clearAuthCookies } from '@/lib/auth';

export async function GET(request: NextRequest) {
  const nextPath = request.nextUrl.searchParams.get('next') ?? '/';
  const response = NextResponse.redirect(new URL(buildLoginPath(nextPath), request.url));
  clearAuthCookies(response);
  return response;
}
