'use client';

import { type ReactNode, useEffect, useState, useSyncExternalStore } from 'react';
import { RiArrowLeftLine, RiDownloadLine, RiFileCopyLine, RiRefreshLine } from '@remixicon/react';
import Link from 'next/link';
import type { JobDetails, JobStatus } from '@/app/jobs/_components/types';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from '@/components/ui/tooltip';
import { formatLocalDateTime } from '@/lib/date-time';
import { formatMinutesAsHoursAndMinutes } from '@/lib/duration';
import { JobLogsPanel } from './job-logs-panel';

const ACTIVE_STATUSES = new Set<JobStatus>(['QUEUED', 'PENDING', 'RUNNING']);

type JobDetailsPanelProps = {
  jobId: string;
  initialJob: JobDetails;
};

type FieldRowProps = {
  label: string;
  value: ReactNode;
};

type ArtifactDownloadPayload = {
  url: string;
};

function formatDateForUser(value: Date | string | null | undefined, isClient: boolean): string {
  if (!isClient) {
    return '-';
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

export function JobDetailsPanel({ jobId, initialJob }: JobDetailsPanelProps) {
  const [job] = useState<JobDetails>(initialJob);
  const [artifactDownloadUrl, setArtifactDownloadUrl] = useState<string | null>(null);
  const [isCheckingArtifact, setIsCheckingArtifact] = useState(false);
  const [errorMessage, setErrorMessage] = useState('');
  const isJobActive = ACTIVE_STATUSES.has(job.status);
  const isClient = useSyncExternalStore(
    () => () => {},
    () => true,
    () => false,
  );

  async function handleCopyExecutionCommand() {
    try {
      await navigator.clipboard.writeText(job.executionCommand);
    } catch {
      setErrorMessage('Failed to copy execution command.');
    }
  }

  useEffect(() => {
    if (isJobActive) {
      setArtifactDownloadUrl(null);
      setIsCheckingArtifact(false);
      return;
    }

    let isActive = true;

    const loadArtifactDownloadUrl = async () => {
      setIsCheckingArtifact(true);
      try {
        const response = await fetch(
          `/api/jobs/${encodeURIComponent(jobId)}/artifact-download-url`,
          { cache: 'no-store' },
        );

        if (response.status === 401) {
          window.location.href = `/login?next=${encodeURIComponent(`/jobs/${jobId}`)}`;
          return;
        }

        if (!isActive) {
          return;
        }

        if (!response.ok) {
          setArtifactDownloadUrl(null);
          return;
        }

        const payload = (await response.json()) as ArtifactDownloadPayload;
        setArtifactDownloadUrl(payload.url ?? null);
      } catch {
        if (isActive) {
          setArtifactDownloadUrl(null);
        }
      } finally {
        if (isActive) {
          setIsCheckingArtifact(false);
        }
      }
    };

    void loadArtifactDownloadUrl();

    return () => {
      isActive = false;
    };
  }, [isJobActive, jobId, job.status]);

  return (
    <section className="space-y-6">
      <div className="flex flex-col gap-4 md:flex-row md:items-center md:justify-between">
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
        <div className="flex items-center md:justify-end">
          {!isJobActive && artifactDownloadUrl ? (
            <Button asChild variant="default" size="lg" className="h-11 px-5 text-base">
              <a href={`/api/jobs/${encodeURIComponent(jobId)}/artifact-download`}>
                <RiDownloadLine aria-hidden="true" size={18} />
                Download Output
              </a>
            </Button>
          ) : !isJobActive && isCheckingArtifact ? (
            <p className="text-muted-foreground inline-flex items-center gap-1 text-xs">
              <RiRefreshLine aria-hidden="true" size={14} />
              Checking output...
            </p>
          ) : null}
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
            <CardTitle>Resources and Quota</CardTitle>
          </CardHeader>
          <CardContent>
            <FieldRow label="CPU (Cores)" value={job.reqCpuCores} />
            <FieldRow label="RAM (GB)" value={job.reqRamGb} />
            <FieldRow
              label="Consumed Time"
              value={formatMinutesAsHoursAndMinutes(job.totalConsumedMinutes)}
            />
            <FieldRow
              label="Owner Username"
              value={
                <span className="inline-flex items-center gap-2">
                  <span>{job.username}</span>
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
          <div className="bg-muted flex items-start gap-2 rounded-none border p-3">
            <p className="min-w-0 flex-1 font-mono text-sm break-all">{job.executionCommand}</p>
            <TooltipProvider>
              <Tooltip>
                <TooltipTrigger asChild>
                  <Button
                    type="button"
                    variant="outline"
                    size="icon-xs"
                    aria-label="Copy execution command"
                    onClick={() => void handleCopyExecutionCommand()}
                  >
                    <RiFileCopyLine aria-hidden="true" size={14} />
                  </Button>
                </TooltipTrigger>
                <TooltipContent side="top" sideOffset={6}>
                  Copy execution command
                </TooltipContent>
              </Tooltip>
            </TooltipProvider>
          </div>
        </CardContent>
      </Card>

      <JobLogsPanel key={jobId} jobId={jobId} isJobActive={isJobActive} jobStatus={job.status} />
    </section>
  );
}
