import { proxyBrokerJsonGet } from '@/lib/broker-proxy';

export async function GET() {
  return proxyBrokerJsonGet({
    path: '/api/nodes',
    fallbackError: 'Failed to fetch nodes.',
  });
}
