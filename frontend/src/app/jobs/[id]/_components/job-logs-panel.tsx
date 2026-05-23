'use client';

/* eslint-disable react-hooks/set-state-in-effect */
import { useCallback, useEffect, useState } from 'react';
import { RiTerminalBoxLine } from '@remixicon/react';
import type { JobStatus } from '@/app/jobs/_components/types';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { redirectToLoginAfterAuthFailure } from '@/lib/client-auth';

type LogStream = 'stdout' | 'stderr';
type LoadState = 'loading' | 'ready' | 'error';

type JobLogsPanelProps = {
  jobId: string;
  jobStatus: JobStatus;
  isJobActive: boolean;
  streamedLogs: Record<LogStream, string>;
};

type JobLogsResponse = {
  jobId: string;
  stream: LogStream;
  content: string;
  capturedBytes: number;
  truncated: boolean;
  captureComplete: boolean;
  updatedAt: string;
};

export function JobLogsPanel({ jobId, jobStatus, isJobActive, streamedLogs }: JobLogsPanelProps) {
  const [selectedStream, setSelectedStream] = useState<LogStream>('stdout');
  const [logs, setLogs] = useState<JobLogsResponse | null>(null);
  const [loadState, setLoadState] = useState<LoadState>(isJobActive ? 'ready' : 'loading');
  const [errorMessage, setErrorMessage] = useState('');

  const loadLogs = useCallback(async () => {
    if (isJobActive) {
      setLoadState('ready');
      setErrorMessage('');
      return;
    }

    setLoadState('loading');
    setErrorMessage('');
    try {
      const response = await fetch(
        `/api/jobs/${encodeURIComponent(jobId)}/logs?stream=${selectedStream}`,
        { cache: 'no-store' },
      );

      if (response.status === 401) {
        redirectToLoginAfterAuthFailure(`/jobs/${jobId}`);
        return;
      }

      if (!response.ok) {
        setLoadState('error');
        setErrorMessage('Failed to load logs.');
        return;
      }

      setLogs((await response.json()) as JobLogsResponse);
      setLoadState('ready');
    } catch {
      setLoadState('error');
      setErrorMessage('Failed to load logs.');
    }
  }, [isJobActive, jobId, selectedStream]);

  useEffect(() => {
    void loadLogs();
  }, [jobStatus, loadLogs]);

  function handleSelectStream(stream: LogStream) {
    if (selectedStream === stream) {
      return;
    }
    setSelectedStream(stream);
    setLogs(null);
  }

  const logContent = isJobActive ? streamedLogs[selectedStream] : logs?.content ?? '';
  const isTruncated = !isJobActive && Boolean(logs?.truncated);

  return (
    <Card>
      <CardHeader className="gap-3">
        <div className="flex flex-col gap-3 md:flex-row md:items-center md:justify-between">
          <CardTitle className="inline-flex items-center gap-2">
            <RiTerminalBoxLine aria-hidden="true" size={16} />
            Logs
          </CardTitle>
          <div className="flex flex-wrap items-center gap-2">
            <Button
              type="button"
              size="sm"
              variant={selectedStream === 'stdout' ? 'default' : 'outline'}
              onClick={() => handleSelectStream('stdout')}
            >
              stdout
            </Button>
            <Button
              type="button"
              size="sm"
              variant={selectedStream === 'stderr' ? 'default' : 'outline'}
              onClick={() => handleSelectStream('stderr')}
            >
              stderr
            </Button>
          </div>
        </div>
        {isTruncated ? (
          <p className="text-muted-foreground text-xs">
            Showing the latest captured log output.
          </p>
        ) : null}
      </CardHeader>
      <CardContent className="space-y-3">
        {errorMessage ? <p className="text-destructive text-sm">{errorMessage}</p> : null}
        <div className="bg-muted rounded-none border">
          <pre className="h-[420px] overflow-auto px-3 py-2 font-mono text-xs leading-5 whitespace-pre-wrap">
            {loadState === 'loading'
              ? 'Loading logs...'
              : logContent || 'No logs captured yet.'}
          </pre>
        </div>
      </CardContent>
    </Card>
  );
}
