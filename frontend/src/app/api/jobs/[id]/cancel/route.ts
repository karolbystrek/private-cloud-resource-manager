import { NextResponse } from 'next/server';
import { getBackendUrlForServer } from '@/lib/backend-url';
import { getBrokerAccessToken } from '@/lib/broker-proxy';

type Params = {
  params: Promise<{ id: string }>;
};

type BrokerProblemDetail = {
  title?: string;
  detail?: string;
};

function mapErrorMessage(status: number, problem: BrokerProblemDetail | null): string {
  if (status === 401) return 'Session expired. Please log in again.';
  if (status === 403) return 'You do not have access to cancel this job.';
  if (status === 404) return 'Job not found.';
  if (status === 409) return problem?.detail ?? 'This job can no longer be canceled.';
  if (status === 502) return problem?.detail ?? 'Failed to cancel running job.';
  return problem?.detail ?? 'Failed to cancel job.';
}

export async function POST(_request: Request, { params }: Params) {
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
    const backendResponse = await fetch(`${backendUrl}/api/jobs/${encodeURIComponent(id)}/cancel`, {
      method: 'POST',
      headers: {
        Authorization: `Bearer ${accessTokenResult.accessToken}`,
      },
      cache: 'no-store',
    });

    if (!backendResponse.ok) {
      const problem = (await backendResponse.json().catch(() => null)) as BrokerProblemDetail | null;
      return NextResponse.json(
        { error: mapErrorMessage(backendResponse.status, problem) },
        { status: backendResponse.status },
      );
    }

    const data = (await backendResponse.json()) as unknown;
    return NextResponse.json(data, { status: 200 });
  } catch {
    return NextResponse.json({ error: 'Unexpected error. Please try again.' }, { status: 500 });
  }
}

