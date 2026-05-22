import { proxyBrokerJsonGet } from '@/lib/broker-proxy';

type Params = {
  params: Promise<{ id: string }>;
};

export async function GET(_request: Request, { params }: Params) {
  const { id } = await params;
  return proxyBrokerJsonGet({
    path: `/api/nodes/${encodeURIComponent(id)}`,
    fallbackError: 'Failed to fetch node details.',
  });
}
