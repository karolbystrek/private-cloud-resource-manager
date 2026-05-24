import { NextResponse } from 'next/server';
import { getBackendUrlForServer } from '@/lib/backend-url';
import { getBrokerAccessToken, proxyBrokerJsonGet } from '@/lib/broker-proxy';

const backendPath = '/admin/quota/resource-weights';

type BackendProblem = {
  detail?: string;
};

export async function GET(): Promise<NextResponse> {
  return proxyBrokerJsonGet({
    path: backendPath,
    fallbackError: 'Resource weights are currently unavailable.',
  });
}

export async function PUT(request: Request): Promise<NextResponse> {
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

  const body = await request.json().catch(() => null);
  if (!body || typeof body !== 'object') {
    return NextResponse.json({ error: 'Invalid resource weight payload.' }, { status: 400 });
  }

  try {
    const backendResponse = await fetch(`${backendUrl}${backendPath}`, {
      method: 'PUT',
      headers: {
        Authorization: `Bearer ${accessTokenResult.accessToken}`,
        'Content-Type': 'application/json',
      },
      body: JSON.stringify(body),
      cache: 'no-store',
    });

    const payload = await backendResponse.json().catch(() => null);
    if (!backendResponse.ok) {
      const problem = payload as BackendProblem | null;
      return NextResponse.json(
        { error: problem?.detail ?? 'Failed to update resource weights.' },
        { status: backendResponse.status },
      );
    }

    return NextResponse.json(payload, { status: 200 });
  } catch {
    return NextResponse.json({ error: 'Unexpected error. Please try again.' }, { status: 500 });
  }
}
