'use client';

import Link from 'next/link';
import { RiArrowLeftLine } from '@remixicon/react';
import { useEffect, useState, useSyncExternalStore } from 'react';

import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { formatLocalDateTime, formatUtcDateTime } from '@/lib/date-time';

import type { NodeDetails } from '@/app/nodes/_components/types';

type NodeDetailsProps = {
  nodeId: string;
  initialNode: NodeDetails;
  pollIntervalMs: number;
  initialUpdatedAtIso: string;
};

function formatDateForUser(value: Date | string | null | undefined, isClient: boolean): string {
  if (!isClient) {
    return formatUtcDateTime(value);
  }

  return formatLocalDateTime(value);
}

function formatRamGb(totalRamMb: number): string {
  return (totalRamMb / 1024).toFixed(1);
}

type FieldRowProps = {
  label: string;
  value: string | number | boolean | null;
};

function FieldRow({ label, value }: FieldRowProps) {
  return (
    <div className="grid grid-cols-[180px_1fr] gap-3 border-b py-2 text-sm last:border-b-0">
      <span className="text-muted-foreground">{label}</span>
      <span className="break-all font-medium">{value ?? '-'}</span>
    </div>
  );
}

export function NodeDetailsPanel({
  nodeId,
  initialNode,
  pollIntervalMs,
  initialUpdatedAtIso,
}: NodeDetailsProps) {
  const [node, setNode] = useState<NodeDetails>(initialNode);
  const [lastUpdatedAt, setLastUpdatedAt] = useState<Date>(new Date(initialUpdatedAtIso));
  const [errorMessage, setErrorMessage] = useState<string>('');
  const isClient = useSyncExternalStore(
    () => () => {},
    () => true,
    () => false,
  );

  useEffect(() => {
    let isActive = true;

    const refreshNode = async () => {
      try {
        const response = await fetch(`/api/nodes/${encodeURIComponent(nodeId)}`, { cache: 'no-store' });

        if (response.status === 401) {
          window.location.href = `/login?next=${encodeURIComponent(`/nodes/${nodeId}`)}`;
          return;
        }

        if (response.status === 403) {
          window.location.href = '/';
          return;
        }

        if (response.status === 404) {
          window.location.href = '/nodes';
          return;
        }

        if (!response.ok) {
          setErrorMessage('Failed to refresh node details.');
          return;
        }

        const payload = (await response.json()) as NodeDetails;
        if (!isActive) {
          return;
        }

        setNode(payload);
        setLastUpdatedAt(new Date());
        setErrorMessage('');
      } catch {
        if (isActive) {
          setErrorMessage('Failed to refresh node details.');
        }
      }
    };

    const timerId = window.setInterval(() => {
      void refreshNode();
    }, pollIntervalMs);

    return () => {
      isActive = false;
      window.clearInterval(timerId);
    };
  }, [nodeId, pollIntervalMs]);

  return (
    <section className="space-y-6">
      <div className="flex flex-col gap-3 md:flex-row md:items-center md:justify-between">
        <div className="space-y-1">
          <Link href="/nodes" className="text-muted-foreground hover:text-foreground inline-flex items-center gap-1 text-sm">
            <RiArrowLeftLine aria-hidden="true" size={16} />
            Back to nodes
          </Link>
          <h1 className="text-3xl font-semibold tracking-tight">{node.hostname}</h1>
          <p className="text-muted-foreground text-sm">
            Node ID:
            {' '}
            {node.id}
          </p>
        </div>
        <p className="text-muted-foreground text-xs">
          Last update:
          {' '}
          {formatDateForUser(lastUpdatedAt, isClient)}
        </p>
      </div>

      {errorMessage ? <p className="text-destructive text-sm">{errorMessage}</p> : null}

      <div className="grid gap-4 xl:grid-cols-2">
        <Card>
          <CardHeader>
            <CardTitle>Runtime</CardTitle>
          </CardHeader>
          <CardContent>
            <FieldRow label="Status" value={node.status} />
            <FieldRow label="Nomad Status" value={node.nomadStatus} />
            <FieldRow label="Nomad Description" value={node.nomadStatusDescription} />
            <FieldRow label="Eligibility" value={node.schedulingEligibility} />
            <FieldRow label="Draining" value={node.draining ? 'true' : 'false'} />
            <FieldRow label="Last Heartbeat" value={formatDateForUser(node.lastHeartbeat, isClient)} />
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle>Capacity</CardTitle>
          </CardHeader>
          <CardContent>
            <FieldRow label="CPU Cores" value={node.totalCpuCores} />
            <FieldRow label="RAM" value={`${formatRamGb(node.totalRamMb)} GB`} />
            <FieldRow label="Agent Version" value={node.agentVersion} />
            <FieldRow label="Nomad Version" value={node.nomadVersion} />
            <FieldRow label="Docker Version" value={node.dockerVersion} />
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle>Placement</CardTitle>
          </CardHeader>
          <CardContent>
            <FieldRow label="IP Address" value={node.ipAddress} />
            <FieldRow label="Datacenter" value={node.datacenter} />
            <FieldRow label="Node Pool" value={node.nodePool} />
            <FieldRow label="Node Class" value={node.nodeClass} />
            <FieldRow label="Nomad Node ID" value={node.nomadNodeId} />
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle>Nomad Metadata</CardTitle>
          </CardHeader>
          <CardContent>
            <FieldRow label="Create Index" value={node.nomadCreateIndex} />
            <FieldRow label="Modify Index" value={node.nomadModifyIndex} />
            <FieldRow label="Created At" value={formatDateForUser(node.createdAt, isClient)} />
          </CardContent>
        </Card>
      </div>
    </section>
  );
}
