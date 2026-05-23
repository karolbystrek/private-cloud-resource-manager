import { NextResponse } from 'next/server';
import { getBackendUrlForServer } from '@/lib/backend-url';
import { getBrokerAccessToken } from '@/lib/broker-proxy';

type Params = {
  params: Promise<{ id: string }>;
};

type BackendProblem = {
  detail?: string;
};

function normalizeStream(rawValue: string | null): 'stdout' | 'stderr' {
  return rawValue === 'stderr' ? 'stderr' : 'stdout';
}

function normalizeLimitBytes(rawValue: string | null): number {
  if (rawValue === null) {
    return 1_048_576;
  }
  const parsed = Number.parseInt(rawValue, 10);
  if (!Number.isFinite(parsed) || parsed < 1) {
    return 1_048_576;
  }
  return Math.min(parsed, 10_485_760);
}

export async function GET(request: Request, { params }: Params) {
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
  const searchParams = new URL(request.url).searchParams;
  const stream = normalizeStream(searchParams.get('stream'));
  const limitBytes = normalizeLimitBytes(searchParams.get('limitBytes'));

  try {
    const backendResponse = await fetch(
      `${backendUrl}/api/jobs/${encodeURIComponent(id)}/logs?stream=${stream}&limitBytes=${limitBytes}`,
      {
        method: 'GET',
        headers: {
          Authorization: `Bearer ${accessTokenResult.accessToken}`,
        },
        cache: 'no-store',
      },
    );

    if (!backendResponse.ok) {
      const problem = (await backendResponse.json().catch(() => null)) as BackendProblem | null;
      return NextResponse.json(
        { error: problem?.detail ?? 'Failed to fetch job logs.' },
        { status: backendResponse.status },
      );
    }

    return NextResponse.json(await backendResponse.json(), { status: 200 });
  } catch {
    return NextResponse.json({ error: 'Unexpected error. Please try again.' }, { status: 500 });
  }
}
