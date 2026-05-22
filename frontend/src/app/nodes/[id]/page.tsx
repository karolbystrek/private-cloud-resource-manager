import type { Metadata } from 'next';
import { notFound, redirect } from 'next/navigation';

import { NodeDetailsPanel } from './_components/node-details';
import type { NodeDetails } from '@/app/nodes/_components/types';
import { brokerFetch } from '@/lib/server-auth';

const DEFAULT_POLL_INTERVAL_MS = 30000;

function resolvePollIntervalMs(): number {
  const parsed = Number.parseInt(process.env.NEXT_PUBLIC_NOMAD_SYNC_INTERVAL_MS ?? '', 10);
  if (Number.isNaN(parsed) || parsed <= 0) {
    return DEFAULT_POLL_INTERVAL_MS;
  }
  return parsed;
}

export const metadata: Metadata = {
  title: 'Node Details - Private Cloud Resource Manager',
};

type NodeDetailPageProps = {
  params: Promise<{ id: string }>;
};

export default async function NodeDetailPage({ params }: NodeDetailPageProps) {
  const { id } = await params;
  const nextPath = `/nodes/${id}`;

  const response = await brokerFetch(`/api/nodes/${encodeURIComponent(id)}`, {
    cache: 'no-store',
  }, nextPath);

  if (response.status === 403) {
    redirect('/');
  }

  if (response.status === 404) {
    notFound();
  }

  if (!response.ok) {
    throw new Error('Failed to load node details.');
  }

  const node = (await response.json()) as NodeDetails;

  return (
    <div className="bg-background/50 min-h-[calc(100vh-3.5rem)] w-full py-8">
      <div className="container mx-auto max-w-6xl px-4 md:px-6">
        <NodeDetailsPanel
          nodeId={id}
          initialNode={node}
          pollIntervalMs={resolvePollIntervalMs()}
          initialUpdatedAtIso={new Date().toISOString()}
        />
      </div>
    </div>
  );
}
