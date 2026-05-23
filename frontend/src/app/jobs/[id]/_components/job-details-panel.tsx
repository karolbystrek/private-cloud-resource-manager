'use client';

import { type ReactNode, useEffect, useState, useSyncExternalStore } from 'react';
import { RiArrowLeftLine, RiDownloadLine, RiFileCopyLine, RiRefreshLine } from '@remixicon/react';
import Link from 'next/link';
import type { JobDetails, JobStatus } from '@/app/jobs/_components/types';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from '@/components/ui/tooltip';
import { redirectToLoginAfterAuthFailure } from '@/lib/client-auth';
import { formatLocalDateTime } from '@/lib/date-time';
import { formatMinutesAsHoursAndMinutes } from '@/lib/duration';
import { JobLogsPanel } from './job-logs-panel';

const ACTIVE_STATUSES = new Set<JobStatus>([
  'SUBMITTED',
  'QUEUED',
  'DISPATCHING',
  'SCHEDULING',
  'RUNNING',
  'FINALIZING',
]);
const MAX_LOG_LINES = 2000;
type LogStream = 'stdout' | 'stderr';

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

type LogEventPayload = {
  stream?: LogStream;
  data?: string;
};

function parsePayload<T>(rawPayload: string): T | null {
  try {
    return JSON.parse(rawPayload) as T;
  } catch {
    return null;
  }
}

function trimLogTextByLineCount(logText: string): string {
  const lines = logText.split('\n');
  if (lines.length <= MAX_LOG_LINES) {
    return logText;
  }
  return lines.slice(lines.length - MAX_LOG_LINES).join('\n');
}

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
  const [job, setJob] = useState<JobDetails>(initialJob);
  const [artifactDownloadUrl, setArtifactDownloadUrl] = useState<string | null>(null);
  const [isCheckingArtifact, setIsCheckingArtifact] = useState(false);
  const [errorMessage, setErrorMessage] = useState('');
  const [streamedLogs, setStreamedLogs] = useState<Record<LogStream, string>>({
    stdout: '',
    stderr: '',
  });
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
    if (!ACTIVE_STATUSES.has(initialJob.status)) {
      return;
    }

    const source = new EventSource(`/api/jobs/${encodeURIComponent(jobId)}/events`);

    source.addEventListener('job', (event) => {
      const nextJob = parsePayload<JobDetails>(event.data);
      if (!nextJob) {
        return;
      }

      setJob((current) => {
        if (Date.parse(nextJob.updatedAt) < Date.parse(current.updatedAt)) {
          return current;
        }
        return nextJob;
      });

      if (!ACTIVE_STATUSES.has(nextJob.status)) {
        source.close();
      }
    });

    source.addEventListener('log', (event) => {
      const payload = parsePayload<LogEventPayload>(event.data);
      if (!payload?.stream || typeof payload.data !== 'string') {
        return;
      }

      const normalized = payload.data.replaceAll('\r\n', '\n').replaceAll('\r', '\n');
      setStreamedLogs((current) => ({
        ...current,
        [payload.stream as LogStream]: trimLogTextByLineCount(
          `${current[payload.stream as LogStream]}${normalized}`,
        ),
      }));
    });

    source.addEventListener('end', () => {
      source.close();
    });

    source.onerror = () => {
      source.close();
      setErrorMessage('Live job updates were interrupted. Refresh the page to reconnect.');
    };

    return () => {
      source.close();
    };
  }, [initialJob.status, jobId]);

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
          redirectToLoginAfterAuthFailure(`/jobs/${jobId}`);
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
              label="Owner email"
              value={
                <span className="inline-flex items-center gap-2">
                  <span>{job.userEmail}</span>
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
                <TooltipContent side="top">Copy execution command</TooltipContent>
              </Tooltip>
            </TooltipProvider>
          </div>
        </CardContent>
      </Card>

      <JobLogsPanel
        key={jobId}
        jobId={jobId}
        jobStatus={job.status}
        isJobActive={isJobActive}
        streamedLogs={streamedLogs}
      />
    </section>
  );
}
