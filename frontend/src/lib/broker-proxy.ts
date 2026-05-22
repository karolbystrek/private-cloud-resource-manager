import { cookies } from 'next/headers';
import { NextResponse } from 'next/server';
import { ACCESS_TOKEN_COOKIE } from '@/lib/auth';
import { getBackendUrlForServer } from '@/lib/backend-url';

type BackendProblem = {
  detail?: string;
};

type BrokerJsonProxyOptions = {
  path: string;
  fallbackError: string;
};

export async function proxyBrokerJsonGet<T = unknown>({
  path,
  fallbackError,
}: BrokerJsonProxyOptions): Promise<NextResponse> {
  let backendUrl: string;
  try {
    backendUrl = getBackendUrlForServer();
  } catch {
    return NextResponse.json({ error: 'Backend URL is not configured.' }, { status: 500 });
  }

  const accessToken = (await cookies()).get(ACCESS_TOKEN_COOKIE)?.value;
  if (!accessToken) {
    return NextResponse.json({ error: 'Session expired. Please log in again.' }, { status: 401 });
  }

  try {
    const backendResponse = await fetch(`${backendUrl}${path}`, {
      method: 'GET',
      headers: {
        Authorization: `Bearer ${accessToken}`,
      },
      cache: 'no-store',
    });

    if (!backendResponse.ok) {
      const problem = (await backendResponse.json().catch(() => null)) as BackendProblem | null;
      return NextResponse.json(
        { error: problem?.detail ?? fallbackError },
        { status: backendResponse.status },
      );
    }

    const data = (await backendResponse.json()) as T;
    return NextResponse.json(data, { status: 200 });
  } catch {
    return NextResponse.json({ error: 'Unexpected error. Please try again.' }, { status: 500 });
  }
}
