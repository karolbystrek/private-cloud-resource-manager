import type { Metadata } from 'next';
import { RiAlertLine, RiArrowRightLine, RiServerLine } from '@remixicon/react';
import Link from 'next/link';
import { redirect } from 'next/navigation';
import type { NodeSummary } from '@/app/nodes/_components/types';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { formatUtcDateTime } from '@/lib/date-time';
import { brokerFetch } from '@/lib/server-auth';
import { isUserRole } from '@/lib/user-role';

export const metadata: Metadata = {
  title: 'Admin - Private Cloud Resource Manager',
};

type NodesResult = {
  nodes: NodeSummary[];
  error: string | null;
};

type QuotaMeResponse = {
  role: string;
};

function getCountByStatus(nodes: NodeSummary[], status: string): number {
  return nodes.filter((node) => node.status === status).length;
}

async function fetchCurrentUserRole(): Promise<string | null> {
  const response = await brokerFetch('/api/quota/me', {
    cache: 'no-store',
  }, '/admin');

  if (!response.ok) {
    return null;
  }

  const quota = (await response.json()) as QuotaMeResponse;
  return isUserRole(quota.role) ? quota.role : null;
}

async function fetchNodes(): Promise<NodesResult> {
  const response = await brokerFetch('/api/nodes', {
    cache: 'no-store',
  }, '/admin');

  if (response.status === 403) {
    redirect('/');
  }

  if (!response.ok) {
    return { nodes: [], error: 'Node status is currently unavailable.' };
  }

  const nodes = (await response.json()) as NodeSummary[];
  return { nodes, error: null };
}

export default async function AdminDashboardPage() {
  const userRole = await fetchCurrentUserRole();
  if (userRole !== 'ADMIN') {
    redirect('/');
  }

  const { nodes, error } = await fetchNodes();
  const totalNodes = nodes.length;
  const activeNodes = getCountByStatus(nodes, 'AVAILABLE');
  const unavailableNodes = totalNodes - activeNodes;
  const maintenanceNodes = getCountByStatus(nodes, 'MAINTENANCE');
  const downNodes = getCountByStatus(nodes, 'DOWN');
  const offlineNodes = getCountByStatus(nodes, 'OFFLINE');
  const nodesNeedingAttention = nodes.filter((node) => node.status !== 'AVAILABLE');

  return (
    <div className="bg-background/50 min-h-[calc(100vh-5.5rem)] w-full py-8">
      <div className="container mx-auto max-w-6xl space-y-6 px-4 md:px-6">
        <div>
          <h1 className="text-3xl font-semibold tracking-tight">Admin Dashboard</h1>
          <p className="text-muted-foreground text-sm">Operational status overview and infrastructure shortcuts.</p>
        </div>

        <section className="grid gap-3 sm:grid-cols-2 lg:grid-cols-5">
          <Card>
            <CardHeader className="pb-2"><CardTitle>Total Nodes</CardTitle></CardHeader>
            <CardContent><p className="text-2xl font-semibold">{totalNodes}</p></CardContent>
          </Card>
          <Card>
            <CardHeader className="pb-2"><CardTitle>Active</CardTitle></CardHeader>
            <CardContent><p className="text-2xl font-semibold">{activeNodes}</p></CardContent>
          </Card>
          <Card>
            <CardHeader className="pb-2"><CardTitle>Unavailable</CardTitle></CardHeader>
            <CardContent><p className="text-2xl font-semibold">{unavailableNodes}</p></CardContent>
          </Card>
          <Card>
            <CardHeader className="pb-2"><CardTitle>Maintenance</CardTitle></CardHeader>
            <CardContent><p className="text-2xl font-semibold">{maintenanceNodes}</p></CardContent>
          </Card>
          <Card>
            <CardHeader className="pb-2"><CardTitle>Down/Offline</CardTitle></CardHeader>
            <CardContent><p className="text-2xl font-semibold">{downNodes + offlineNodes}</p></CardContent>
          </Card>
        </section>

        <section className="grid gap-4 lg:grid-cols-3">
          <Card className="lg:col-span-2">
            <CardHeader>
              <CardTitle className="inline-flex items-center gap-2"><RiAlertLine size={16} />Attention Required</CardTitle>
              <CardDescription>Nodes that are not currently AVAILABLE.</CardDescription>
            </CardHeader>
            <CardContent className="space-y-3">
              {error ? <p className="text-destructive text-sm">{error}</p> : null}
              {!error && nodesNeedingAttention.length === 0 ? <p className="text-muted-foreground text-sm">No critical node states right now.</p> : null}
              {!error && nodesNeedingAttention.length > 0 ? (
                <div className="divide-border divide-y">
                  {nodesNeedingAttention.map((node) => (
                    <Link
                      key={node.id}
                      href={`/nodes/${encodeURIComponent(node.id)}`}
                      className="hover:bg-muted/50 flex flex-col gap-1 px-2 py-3 transition-colors md:flex-row md:items-center md:justify-between"
                    >
                      <div>
                        <p className="font-medium">{node.hostname}</p>
                        <p className="text-muted-foreground text-xs">{node.ipAddress}</p>
                      </div>
                      <div className="flex items-center gap-3 text-xs">
                        <span className="border-input bg-muted rounded-none border px-2 py-1">{node.status}</span>
                        <span>{formatUtcDateTime(node.lastHeartbeat)}</span>
                      </div>
                    </Link>
                  ))}
                </div>
              ) : null}
            </CardContent>
          </Card>

          <Card>
            <CardHeader>
              <CardTitle className="inline-flex items-center gap-2"><RiServerLine size={16} />Actions</CardTitle>
            </CardHeader>
            <CardContent className="space-y-2">
              <Button asChild className="w-full justify-between"><Link href="/nodes">Inspect All Nodes<RiArrowRightLine size={14} /></Link></Button>
              <Button asChild variant="outline" className="w-full justify-between"><Link href="/jobs">Review Jobs<RiArrowRightLine size={14} /></Link></Button>
            </CardContent>
          </Card>
        </section>
      </div>
    </div>
  );
}
