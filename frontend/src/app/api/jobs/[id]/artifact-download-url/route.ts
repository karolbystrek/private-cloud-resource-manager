import { proxyBrokerJsonGet } from '@/lib/broker-proxy';

type ArtifactDownloadResponse = {
  url: string;
  objectKey: string;
  expiresInSec: number;
};

type Params = {
  params: Promise<{ id: string }>;
};

export async function GET(_request: Request, { params }: Params) {
  const { id } = await params;
  return proxyBrokerJsonGet<ArtifactDownloadResponse>({
    path: `/api/jobs/${encodeURIComponent(id)}/artifact-download-url`,
    fallbackError: 'Artifact is not available yet.',
  });
}
