import { cookies } from 'next/headers';
import { NextResponse } from 'next/server';
import { ACCESS_TOKEN_COOKIE } from '@/lib/auth';
import { getBackendUrlForServer } from '@/lib/backend-url';

type BackendProblem = {
  detail?: string;
};

type Params = {
  params: Promise<{ id: string }>;
};

export async function GET(_request: Request, { params }: Params) {
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

  try {
    const backendResponse = await fetch(`${BACKEND_URL}/api/jobs/${encodeURIComponent(id)}`, {
      method: 'GET',
      headers: {
        Authorization: `Bearer ${accessToken}`,
      },
      cache: 'no-store',
    });

    if (!backendResponse.ok) {
      const problem = (await backendResponse.json().catch(() => null)) as BackendProblem | null;
      return NextResponse.json(
        { error: problem?.detail ?? 'Failed to fetch job details.' },
        { status: backendResponse.status },
      );
    }

    const data = await backendResponse.json();
    return NextResponse.json(data, { status: 200 });
  } catch {
    return NextResponse.json({ error: 'Unexpected error. Please try again.' }, { status: 500 });
  }
}
