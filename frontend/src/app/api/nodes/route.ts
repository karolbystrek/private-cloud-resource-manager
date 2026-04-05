import { cookies } from 'next/headers';
import { NextResponse } from 'next/server';

const BACKEND_URL = process.env.NEXT_PUBLIC_BACKEND_URL;

type BackendProblem = {
  detail?: string;
};

export async function GET() {
  if (!BACKEND_URL) {
    return NextResponse.json({ error: 'Backend URL is not configured.' }, { status: 500 });
  }

  const accessToken = (await cookies()).get('access_token')?.value;
  if (!accessToken) {
    return NextResponse.json({ error: 'Session expired. Please log in again.' }, { status: 401 });
  }

  try {
    const backendResponse = await fetch(`${BACKEND_URL}/api/nodes`, {
      method: 'GET',
      headers: {
        Authorization: `Bearer ${accessToken}`,
      },
      cache: 'no-store',
    });

    if (!backendResponse.ok) {
      const problem = (await backendResponse.json().catch(() => null)) as BackendProblem | null;
      return NextResponse.json(
        { error: problem?.detail ?? 'Failed to fetch nodes.' },
        { status: backendResponse.status },
      );
    }

    const data = await backendResponse.json();
    return NextResponse.json(data, { status: 200 });
  } catch {
    return NextResponse.json({ error: 'Unexpected error. Please try again.' }, { status: 500 });
  }
}
