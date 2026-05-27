import { NextResponse } from 'next/server';
import { getBackendUrlForServer } from '@/lib/backend-url';
import { getBrokerAccessToken } from '@/lib/broker-proxy';

type BackendProblemDetail = {
  detail?: string;
};

export async function GET() {
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

  try {
    const backendResponse = await fetch(`${backendUrl}/api/admin/quota/users`, {
      method: 'GET',
      headers: {
        Authorization: `Bearer ${accessTokenResult.accessToken}`,
      },
      cache: 'no-store',
    });

    if (!backendResponse.ok) {
      const problem = (await backendResponse.json().catch(() => null)) as BackendProblemDetail | null;
      return NextResponse.json(
        { error: problem?.detail ?? 'Failed to fetch users.' },
        { status: backendResponse.status },
      );
    }

    const data = (await backendResponse.json()) as unknown;
    return NextResponse.json(data, { status: 200 });
  } catch {
    return NextResponse.json({ error: 'Unexpected error. Please try again.' }, { status: 500 });
  }
}
