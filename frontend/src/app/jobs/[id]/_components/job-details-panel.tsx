'use client';

import { type ReactNode, useEffect, useState, useSyncExternalStore } from 'react';
import { RiArrowLeftLine, RiDownloadLine, RiFileCopyLine, RiRefreshLine } from '@remixicon/react';
import Link from 'next/link';
import type { JobDetails, JobStatus } from '@/app/jobs/_components/types';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from '@/components/ui/tooltip';
import { formatLocalDateTime, formatUtcDateTime } from '@/lib/date-time';

const ACTIVE_POLL_INTERVAL_MS = 15000;
const ACTIVE_STATUSES = new Set<JobStatus>(['PENDING', 'RUNNING']);

type JobDetailsPanelProps = {
  jobId: string;
  initialJob: JobDetails;
  initialUpdatedAtIso: string;
};

type FieldRowProps = {
  label: string;
  value: ReactNode;
};

type ArtifactDownloadPayload = {
  url: string;
};

function formatCountdown(totalSeconds: number): string {
  const seconds = Math.max(0, totalSeconds);
  const minutesPart = Math.floor(seconds / 60)
    .toString()
    .padStart(2, '0');
  const secondsPart = (seconds % 60).toString().padStart(2, '0');
  return `${minutesPart}:${secondsPart}`;
}

function formatDateForUser(value: Date | string | null | undefined, isClient: boolean): string {
  if (!isClient) {
    return formatUtcDateTime(value);
  }
  return formatLocalDateTime(value);
}

function FieldRow({ label, value }: FieldRowProps) {
  return (
    <div className="grid grid-cols-[160px_1fr] gap-3 border-b py-2 text-sm last:border-b-0">
      <span className="text-muted-foreground">{label}</span>
      <div className="font-medium break-all">{value ?? '-'}</div>
    </div>
  );
}

export function JobDetailsPanel({ jobId, initialJob, initialUpdatedAtIso }: JobDetailsPanelProps) {
  const [job, setJob] = useState<JobDetails>(initialJob);
  const [lastUpdatedAt, setLastUpdatedAt] = useState<Date>(new Date(initialUpdatedAtIso));
  const [artifactDownloadUrl, setArtifactDownloadUrl] = useState<string | null>(null);
  const [isCheckingArtifact, setIsCheckingArtifact] = useState(false);
  const [errorMessage, setErrorMessage] = useState('');
  const isAutoRefreshEnabled = ACTIVE_STATUSES.has(job.status);
  const fullIntervalSeconds = Math.max(1, Math.floor(ACTIVE_POLL_INTERVAL_MS / 1000));
  const [secondsUntilRefresh, setSecondsUntilRefresh] = useState<number>(fullIntervalSeconds);
  const isClient = useSyncExternalStore(
    () => () => {},
    () => true,
    () => false,
  );

  async function handleCopyOwnerId() {
    try {
      await navigator.clipboard.writeText(job.userId);
    } catch {
      setErrorMessage('Failed to copy owner ID.');
    }
  }

  async function loadArtifactDownloadUrl() {
    setIsCheckingArtifact(true);
    try {
      const response = await fetch(`/api/jobs/${encodeURIComponent(jobId)}/artifact-download-url`, {
        cache: 'no-store',
      });

      if (response.status === 401) {
        window.location.href = `/login?next=${encodeURIComponent(`/jobs/${jobId}`)}`;
        return;
      }

      if (!response.ok) {
        setArtifactDownloadUrl(null);
        return;
      }

      const payload = (await response.json()) as ArtifactDownloadPayload;
      setArtifactDownloadUrl(payload.url ?? null);
    } catch {
      setArtifactDownloadUrl(null);
    } finally {
      setIsCheckingArtifact(false);
    }
  }

  useEffect(() => {
    if (!isAutoRefreshEnabled) {
      return;
    }

    let isActive = true;

    const refreshJob = async () => {
      try {
        const response = await fetch(`/api/jobs/${encodeURIComponent(jobId)}`, {
          cache: 'no-store',
        });

        if (response.status === 401) {
          window.location.href = `/login?next=${encodeURIComponent(`/jobs/${jobId}`)}`;
          return;
        }

        if (response.status === 404) {
          window.location.href = '/jobs';
          return;
        }

        if (!response.ok) {
          if (isActive) {
            setErrorMessage('Failed to refresh job details.');
          }
          return;
        }

        const payload = (await response.json()) as JobDetails;
        if (!isActive) {
          return;
        }

        setJob(payload);
        setLastUpdatedAt(new Date());
        setSecondsUntilRefresh(fullIntervalSeconds);
        setErrorMessage('');
      } catch {
        if (isActive) {
          setErrorMessage('Failed to refresh job details.');
        }
      }
    };

    const timerId = window.setInterval(() => {
      void refreshJob();
    }, ACTIVE_POLL_INTERVAL_MS);

    const countdownId = window.setInterval(() => {
      setSecondsUntilRefresh((current) =>
        current <= 1 || current > fullIntervalSeconds ? fullIntervalSeconds : current - 1,
      );
    }, 1000);

    return () => {
      isActive = false;
      window.clearInterval(timerId);
      window.clearInterval(countdownId);
    };
  }, [fullIntervalSeconds, isAutoRefreshEnabled, jobId]);

  useEffect(() => {
    if (isAutoRefreshEnabled) {
      setArtifactDownloadUrl(null);
      setIsCheckingArtifact(false);
      return;
    }

    void loadArtifactDownloadUrl();
  }, [isAutoRefreshEnabled, jobId, job.status]);

  return (
    <section className="space-y-6">
      <div className="flex flex-col gap-3 md:flex-row md:items-center md:justify-between">
        <div className="space-y-1">
          <Link
            href="/jobs"
            className="text-muted-foreground hover:text-foreground inline-flex items-center gap-1 text-sm"
          >
            <RiArrowLeftLine aria-hidden="true" size={16} />
            Back to jobs
          </Link>
          <h1 className="text-3xl font-semibold tracking-tight">Job Details</h1>
        </div>
        <div className="flex flex-col gap-2 md:items-end">
          {!isAutoRefreshEnabled && artifactDownloadUrl ? (
            <Button asChild variant="outline" size="sm">
              <a href={`/api/jobs/${encodeURIComponent(jobId)}/artifact-download`}>
                <RiDownloadLine aria-hidden="true" size={14} />
                Download Output
              </a>
            </Button>
          ) : null}
          <div className="text-muted-foreground flex flex-col gap-1 text-xs md:items-end">
            {isAutoRefreshEnabled ? (
              <p className="inline-flex items-center gap-1">
                <RiRefreshLine aria-hidden="true" size={14} />
                {formatCountdown(secondsUntilRefresh)}
              </p>
            ) : isCheckingArtifact ? (
              <p className="inline-flex items-center gap-1">
                <RiRefreshLine aria-hidden="true" size={14} />
                Checking output...
              </p>
            ) : null}
            <p>Last update: {formatDateForUser(lastUpdatedAt, isClient)}</p>
          </div>
        </div>
      </div>

      {errorMessage ? <p className="text-destructive text-sm">{errorMessage}</p> : null}

      <div className="grid gap-4 lg:grid-cols-2">
        <Card>
          <CardHeader>
            <CardTitle>Runtime</CardTitle>
          </CardHeader>
          <CardContent>
            <FieldRow label="Status" value={job.status} />
            <FieldRow label="Docker Image" value={job.dockerImage} />
            <FieldRow label="Node ID" value={job.nodeId} />
            <FieldRow label="Created At" value={formatDateForUser(job.createdAt, isClient)} />
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle>Resources and Billing</CardTitle>
          </CardHeader>
          <CardContent>
            <FieldRow label="CPU (Cores)" value={job.reqCpuCores} />
            <FieldRow label="RAM (GB)" value={job.reqRamGb} />
            <FieldRow label="Total Cost" value={job.totalCostCredits} />
            <FieldRow
              label="Owner Username"
              value={
                <span className="inline-flex items-center gap-2">
                  <span>{job.username}</span>
                  <TooltipProvider>
                    <Tooltip>
                      <TooltipTrigger asChild>
                        <Button
                          type="button"
                          variant="outline"
                          size="icon-xs"
                          aria-label="Copy user ID"
                          onClick={() => void handleCopyOwnerId()}
                        >
                          <RiFileCopyLine aria-hidden="true" size={14} />
                        </Button>
                      </TooltipTrigger>
                      <TooltipContent side="top" sideOffset={6}>
                        Copy user ID
                      </TooltipContent>
                    </Tooltip>
                  </TooltipProvider>
                </span>
              }
            />
          </CardContent>
        </Card>
      </div>

      <Card>
        <CardHeader>
          <CardTitle>Execution Command</CardTitle>
        </CardHeader>
        <CardContent>
          <p className="bg-muted rounded-none border p-3 font-mono text-sm break-all">
            {job.executionCommand}
          </p>
        </CardContent>
      </Card>
    </section>
  );
}
