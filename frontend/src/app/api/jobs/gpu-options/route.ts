import { proxyBrokerJsonGet } from '@/lib/broker-proxy';

export async function GET() {
  return proxyBrokerJsonGet({
    path: '/api/jobs/gpu-options',
    fallbackError: 'Failed to fetch GPU options.',
  });
}
