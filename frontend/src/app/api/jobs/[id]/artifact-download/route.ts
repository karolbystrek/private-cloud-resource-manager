import { cookies } from 'next/headers';
import { NextResponse } from 'next/server';

const BACKEND_URL = process.env.NEXT_PUBLIC_BACKEND_URL;

type BackendProblem = {
  detail?: string;
};

type ArtifactDownloadResponse = {
  url: string;
  objectKey: string;
};

type Params = {
  params: Promise<{ id: string }>;
};

function buildFileName(objectKey: string | undefined): string {
  if (!objectKey) {
    return 'output.zip';
  }
  const normalized = objectKey.trim();
  if (!normalized) {
    return 'output.zip';
  }
  const lastSlash = normalized.lastIndexOf('/');
  return lastSlash >= 0 ? normalized.slice(lastSlash + 1) || 'output.zip' : normalized;
}

export async function GET(_request: Request, { params }: Params) {
  if (!BACKEND_URL) {
    return NextResponse.json({ error: 'Backend URL is not configured.' }, { status: 500 });
  }

  const accessToken = (await cookies()).get('access_token')?.value;
  if (!accessToken) {
    return NextResponse.json({ error: 'Session expired. Please log in again.' }, { status: 401 });
  }

  const { id } = await params;

  try {
    const downloadUrlResponse = await fetch(
      `${BACKEND_URL}/api/jobs/${encodeURIComponent(id)}/artifact-download-url`,
      {
        method: 'GET',
        headers: {
          Authorization: `Bearer ${accessToken}`,
        },
        cache: 'no-store',
      },
    );

    if (!downloadUrlResponse.ok) {
      const problem = (await downloadUrlResponse.json().catch(() => null)) as BackendProblem | null;
      return NextResponse.json(
        { error: problem?.detail ?? 'Artifact is not available yet.' },
        { status: downloadUrlResponse.status },
      );
    }

    const payload = (await downloadUrlResponse.json()) as ArtifactDownloadResponse;
    const objectResponse = await fetch(payload.url, {
      method: 'GET',
      cache: 'no-store',
    });

    if (!objectResponse.ok || !objectResponse.body) {
      return NextResponse.json(
        { error: 'Failed to download artifact from object storage.' },
        { status: 502 },
      );
    }

    const contentType = objectResponse.headers.get('content-type') ?? 'application/zip';
    const fileName = buildFileName(payload.objectKey);

    return new NextResponse(objectResponse.body, {
      status: 200,
      headers: {
        'Content-Type': contentType,
        'Content-Disposition': `attachment; filename="${fileName}"`,
        'Cache-Control': 'no-store',
      },
    });
  } catch {
    return NextResponse.json({ error: 'Unexpected error. Please try again.' }, { status: 500 });
  }
}
