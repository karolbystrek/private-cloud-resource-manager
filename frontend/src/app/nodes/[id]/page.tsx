import type { Metadata } from 'next';
import { cookies } from 'next/headers';
import { notFound, redirect } from 'next/navigation';

import { NodeDetailsPanel } from './_components/node-details';
import type { NodeDetails } from '@/app/nodes/_components/types';

const BACKEND_URL = process.env.NEXT_PUBLIC_BACKEND_URL;
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
  if (!BACKEND_URL) {
    throw new Error('Backend URL is not configured.');
  }

  const { id } = await params;
  const accessToken = (await cookies()).get('access_token')?.value;
  if (!accessToken) {
    redirect(`/login?next=${encodeURIComponent(`/nodes/${id}`)}`);
  }

  const response = await fetch(`${BACKEND_URL}/api/nodes/${encodeURIComponent(id)}`, {
    headers: {
      Authorization: `Bearer ${accessToken}`,
    },
    cache: 'no-store',
  });

  if (response.status === 401) {
    redirect(`/login?next=${encodeURIComponent(`/nodes/${id}`)}`);
  }

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
