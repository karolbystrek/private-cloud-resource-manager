import { NextResponse } from 'next/server';
import { getBackendUrlForServer } from '@/lib/backend-url';
import { getBrokerAccessToken } from '@/lib/broker-proxy';

type Params = {
  params: Promise<{ id: string }>;
};

export const runtime = 'nodejs';
export const dynamic = 'force-dynamic';

export async function GET(_request: Request, { params }: Params) {
  let backendUrl: string;
  try {
    backendUrl = getBackendUrlForServer();
  } catch {
    return NextResponse.json({ error: 'Backend URL is not configured.' }, { status: 500 });
  }

  const accessTokenResult = await getBrokerAccessToken();
  if (!accessTokenResult.ok) {
    return accessTokenResult.response;
  }

  const { id } = await params;

  try {
    const backendResponse = await fetch(
      `${backendUrl}/api/jobs/${encodeURIComponent(id)}/events`,
      {
        method: 'GET',
        headers: {
          Authorization: `Bearer ${accessTokenResult.accessToken}`,
          Accept: 'text/event-stream',
        },
        cache: 'no-store',
      },
    );

    if (!backendResponse.ok || !backendResponse.body) {
      return NextResponse.json(
        { error: 'Failed to open job updates.' },
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
