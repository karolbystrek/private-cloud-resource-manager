import { cookies } from 'next/headers';
import { NextResponse } from 'next/server';
import { ACCESS_TOKEN_COOKIE } from '@/lib/auth';
import { getBackendUrlForServer } from '@/lib/backend-url';

type Params = {
  params: Promise<{ id: string }>;
};

function normalizeStream(rawValue: string | null): 'stdout' | 'stderr' {
  return rawValue === 'stderr' ? 'stderr' : 'stdout';
}

function normalizeOffset(rawValue: string | null): number {
  if (rawValue === null) {
    return 0;
  }
  const parsed = Number.parseInt(rawValue, 10);
  if (!Number.isFinite(parsed) || parsed < 0) {
    return 0;
  }
  return parsed;
}

export const runtime = 'nodejs';
export const dynamic = 'force-dynamic';

export async function GET(request: Request, { params }: Params) {
  let BACKEND_URL: string;
  try {
    BACKEND_URL = getBackendUrlForServer();
  } catch {
    return NextResponse.json({ error: 'Backend URL is not configured.' }, { status: 500 });
  }

  const accessToken = (await cookies()).get(ACCESS_TOKEN_COOKIE)?.value;
  if (!accessToken) {
    return NextResponse.json({ error: 'Session expired. Please log in again.' }, { status: 401 });
  }

  const { id } = await params;
  const searchParams = new URL(request.url).searchParams;
  const stream = normalizeStream(searchParams.get('stream'));
  const offset = normalizeOffset(searchParams.get('offset'));

  try {
    const backendResponse = await fetch(
      `${BACKEND_URL}/api/jobs/${encodeURIComponent(id)}/logs/stream?stream=${stream}&offset=${offset}`,
      {
        method: 'GET',
        headers: {
          Authorization: `Bearer ${accessToken}`,
          Accept: 'text/event-stream',
        },
        cache: 'no-store',
      },
    );

    if (!backendResponse.ok || !backendResponse.body) {
      return NextResponse.json(
        { error: 'Failed to open log stream.' },
        { status: backendResponse.status || 502 },
      );
    }

    return new NextResponse(backendResponse.body, {
      status: 200,
      headers: {
        'Content-Type': 'text/event-stream; charset=utf-8',
        'Cache-Control': 'no-cache, no-transform',
        Connection: 'keep-alive',
        'X-Accel-Buffering': 'no',
      },
    });
  } catch {
    return NextResponse.json({ error: 'Unexpected error. Please try again.' }, { status: 500 });
  }
}
