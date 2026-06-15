import { proxyBrokerJsonGet } from '@/lib/broker-proxy';

export async function GET() {
  return proxyBrokerJsonGet({
    path: '/api/nodes/gpu-available',
    fallbackError: 'Failed to check GPU availability.',
  });
}
