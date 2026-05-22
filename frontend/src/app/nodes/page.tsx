import type { Metadata } from 'next';
import { redirect } from 'next/navigation';
import { NodesList } from './_components/nodes-list';
import type { NodeSummary } from './_components/types';
import { brokerFetch } from '@/lib/server-auth';

export const metadata: Metadata = {
  title: 'Nodes - Private Cloud Resource Manager',
};

const DEFAULT_POLL_INTERVAL_MS = 30000;

function resolvePollIntervalMs(): number {
  const parsed = Number.parseInt(process.env.NEXT_PUBLIC_NOMAD_SYNC_INTERVAL_MS ?? '', 10);
  if (Number.isNaN(parsed) || parsed <= 0) {
    return DEFAULT_POLL_INTERVAL_MS;
  }
  return parsed;
}

export default async function NodesPage() {
  const response = await brokerFetch('/api/nodes', {
    cache: 'no-store',
  }, '/nodes');

  if (response.status === 403) {
    redirect('/');
  }

  if (!response.ok) {
    throw new Error('Failed to load nodes.');
  }

  const nodes = (await response.json()) as NodeSummary[];

  return (
    <div className="bg-background/50 min-h-[calc(100vh-3.5rem)] w-full py-8">
      <div className="container mx-auto max-w-6xl px-4 md:px-6">
        <NodesList
          initialNodes={nodes}
          pollIntervalMs={resolvePollIntervalMs()}
          initialUpdatedAt={new Date().toISOString()}
        />
      </div>
    </div>
  );
}
