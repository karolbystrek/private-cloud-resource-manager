'use client';

import { useEffect, useMemo, useState, useSyncExternalStore } from 'react';
import { RiRefreshLine } from '@remixicon/react';
import Link from 'next/link';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { formatLocalDateTime, formatUtcDateTime } from '@/lib/date-time';
import type { NodeSummary } from './types';

type NodesListProps = {
  initialNodes: NodeSummary[];
  pollIntervalMs: number;
  initialUpdatedAt: string;
};

function formatHeartbeat(value: string | null, isClient: boolean): string {
  if (!value) {
    return 'No heartbeat';
  }

  const formatted = formatDateForUser(value, isClient);
  if (formatted === '-') {
    return 'Unknown';
  }

  return formatted;
}

function formatDateForUser(value: Date | string | null | undefined, isClient: boolean): string {
  if (!isClient) {
    return formatUtcDateTime(value);
  }

  return formatLocalDateTime(value);
}

function formatRamGb(totalRamMb: number): string {
  return (totalRamMb / 1024).toFixed(1);
}

function formatCountdown(totalSeconds: number): string {
  const seconds = Math.max(0, totalSeconds);
  const minutesPart = Math.floor(seconds / 60)
    .toString()
    .padStart(2, '0');
  const secondsPart = (seconds % 60).toString().padStart(2, '0');
  return `${minutesPart}:${secondsPart}`;
}

export function NodesList({ initialNodes, pollIntervalMs, initialUpdatedAt }: NodesListProps) {
  const [nodes, setNodes] = useState<NodeSummary[]>(initialNodes);
  const [lastUpdatedAt, setLastUpdatedAt] = useState<Date>(new Date(initialUpdatedAt));
  const [errorMessage, setErrorMessage] = useState<string>('');
  const [secondsUntilRefresh, setSecondsUntilRefresh] = useState<number>(
    Math.max(1, Math.floor(pollIntervalMs / 1000)),
  );
  const isClient = useSyncExternalStore(
    () => () => {},
    () => true,
    () => false,
  );

  useEffect(() => {
    let isActive = true;
    const fullIntervalSeconds = Math.max(1, Math.floor(pollIntervalMs / 1000));

    const refreshNodes = async () => {
      try {
        const response = await fetch('/api/nodes', { cache: 'no-store' });

        if (response.status === 401) {
          window.location.href = '/login?next=/nodes';
          return;
        }

        if (response.status === 403) {
          window.location.href = '/';
          return;
        }

        if (!response.ok) {
          setErrorMessage('Failed to refresh nodes.');
          return;
        }

        const payload = (await response.json()) as NodeSummary[];
        if (!isActive) {
          return;
        }

        setNodes(payload);
        setLastUpdatedAt(new Date());
        setSecondsUntilRefresh(fullIntervalSeconds);
        setErrorMessage('');
      } catch {
        if (isActive) {
          setErrorMessage('Failed to refresh nodes.');
        }
      }
    };

    const timerId = window.setInterval(() => {
      void refreshNodes();
    }, pollIntervalMs);

    const countdownId = window.setInterval(() => {
      setSecondsUntilRefresh((current) => (current <= 1 ? fullIntervalSeconds : current - 1));
    }, 1000);

    return () => {
      isActive = false;
      window.clearInterval(timerId);
      window.clearInterval(countdownId);
    };
  }, [pollIntervalMs]);

  const statusCounts = useMemo(() => {
    const map = new Map<string, number>();
    for (const node of nodes) {
      const key = node.status;
      map.set(key, (map.get(key) ?? 0) + 1);
    }
    return map;
  }, [nodes]);

  return (
    <section className="space-y-6">
      <div className="flex flex-col gap-3 md:flex-row md:items-center md:justify-between">
        <div>
          <h1 className="text-3xl font-semibold tracking-tight">Nodes</h1>
          <p className="text-muted-foreground inline-flex items-center gap-1 text-sm">
            <RiRefreshLine aria-hidden="true" size={16} />
            {formatCountdown(secondsUntilRefresh)}
          </p>
        </div>
        <p className="text-muted-foreground text-xs">
          Last update: {formatDateForUser(lastUpdatedAt, isClient)}
        </p>
      </div>

      <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
        {[...statusCounts.entries()].map(([status, count]) => (
          <Card key={status}>
            <CardHeader className="pb-2">
              <CardTitle className="text-sm font-medium">{status}</CardTitle>
            </CardHeader>
            <CardContent>
              <p className="text-2xl font-semibold">{count}</p>
            </CardContent>
          </Card>
        ))}
      </div>

      {errorMessage ? <p className="text-destructive text-sm">{errorMessage}</p> : null}

      <div className="grid gap-4 md:grid-cols-2">
        {nodes.map((node) => (
          <Link key={node.id} href={`/nodes/${encodeURIComponent(node.id)}`}>
            <Card className="hover:border-primary/60 transition-colors">
              <CardHeader>
                <CardTitle className="text-lg">{node.hostname}</CardTitle>
              </CardHeader>
              <CardContent className="space-y-2 text-sm">
                <p>
                  <span className="text-muted-foreground">ID:</span> {node.id}
                </p>
                <p>
                  <span className="text-muted-foreground">IP:</span> {node.ipAddress}
                </p>
                <p>
                  <span className="text-muted-foreground">Status:</span> {node.status}
                </p>
                <p>
                  <span className="text-muted-foreground">CPU:</span> {node.totalCpuCores} cores
                </p>
                <p>
                  <span className="text-muted-foreground">RAM:</span> {formatRamGb(node.totalRamMb)}{' '}
                  GB
                </p>
                <p>
                  <span className="text-muted-foreground">Heartbeat:</span>{' '}
                  {formatHeartbeat(node.lastHeartbeat, isClient)}
                </p>
              </CardContent>
            </Card>
          </Link>
        ))}
      </div>
    </section>
  );
}
