'use client';

import { useCallback, useEffect, useRef, useState } from 'react';
import { RiPulseLine, RiRefreshLine, RiTerminalBoxLine } from '@remixicon/react';
import type { JobStatus } from '@/app/jobs/_components/types';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';

const MAX_LOG_LINES = 2000;
const WAITING_STATUSES = new Set<JobStatus>(['QUEUED', 'PENDING']);

type LogStream = 'stdout' | 'stderr';
type ConnectionState = 'connecting' | 'streaming' | 'disconnected' | 'idle' | 'unavailable' | 'waiting';

type JobLogsPanelProps = {
  jobId: string;
  isJobActive: boolean;
  jobStatus: JobStatus;
};

type ChunkEventPayload = {
  stream?: string;
  data?: string;
  offset?: number;
};

type StatusEventPayload = {
  state?: string;
  retryable?: boolean;
};

function parsePayload<T>(rawPayload: string): T | null {
  try {
    return JSON.parse(rawPayload) as T;
  } catch {
    return null;
  }
}

function getConnectionBadgeLabel(connectionState: ConnectionState): string {
  switch (connectionState) {
    case 'connecting':
      return 'Connecting';
    case 'streaming':
      return 'Streaming';
    case 'disconnected':
      return 'Disconnected';
    case 'idle':
      return 'Idle';
    case 'unavailable':
      return 'Unavailable';
    case 'waiting':
      return 'Waiting';
    default:
      return 'Unknown';
  }
}

function getConnectionBadgeClassName(connectionState: ConnectionState): string {
  switch (connectionState) {
    case 'streaming':
      return 'bg-emerald-500/20 text-emerald-700 border-emerald-500/40';
    case 'connecting':
      return 'bg-amber-500/20 text-amber-700 border-amber-500/40';
    case 'disconnected':
      return 'bg-destructive/15 text-destructive border-destructive/30';
    case 'unavailable':
      return 'bg-orange-500/20 text-orange-700 border-orange-500/40';
    case 'waiting':
      return 'bg-muted text-muted-foreground border-border';
    case 'idle':
      return 'bg-muted text-muted-foreground border-border';
    default:
      return 'bg-muted text-muted-foreground border-border';
  }
}

function trimLogTextByLineCount(logText: string): string {
  const lines = logText.split('\n');
  if (lines.length <= MAX_LOG_LINES) {
    return logText;
  }
  return lines.slice(lines.length - MAX_LOG_LINES).join('\n');
}

export function JobLogsPanel({ jobId, isJobActive, jobStatus }: JobLogsPanelProps) {
  const [selectedStream, setSelectedStream] = useState<LogStream>('stdout');
  const [logText, setLogText] = useState('');
  const [connectionState, setConnectionState] = useState<ConnectionState>('connecting');

  const eventSourceRef = useRef<EventSource | null>(null);
  const shouldReconnectRef = useRef(true);
  const activeStateRef = useRef(isJobActive);
  const lastOffsetRef = useRef(0);
  const openStreamRef = useRef<() => void>(() => {});

  const closeSource = useCallback(() => {
    const source = eventSourceRef.current;
    if (source) {
      source.close();
      eventSourceRef.current = null;
    }
  }, []);

  const appendChunk = useCallback((chunk: string) => {
    if (!chunk) {
      return;
    }
    const normalized = chunk.replaceAll('\r\n', '\n').replaceAll('\r', '\n');
    setLogText((current) => trimLogTextByLineCount(`${current}${normalized}`));
  }, []);

  const openStream = useCallback(() => {
    closeSource();
    if (!shouldReconnectRef.current && connectionState === 'unavailable') {
      return;
    }

    const stream = selectedStream;
    const offset = lastOffsetRef.current;
    shouldReconnectRef.current = true;
    setConnectionState('connecting');

    const source = new EventSource(
      `/api/jobs/${encodeURIComponent(jobId)}/logs/stream?stream=${stream}&offset=${offset}`,
    );
    eventSourceRef.current = source;

    source.addEventListener('meta', () => setConnectionState('connecting'));

    source.addEventListener('chunk', (event) => {
      const payload = parsePayload<ChunkEventPayload>(event.data);
      if (!payload || typeof payload.data !== 'string') {
        return;
      }
      appendChunk(payload.data);
      if (
        typeof payload.offset === 'number' &&
        Number.isFinite(payload.offset) &&
        payload.offset >= 0
      ) {
        lastOffsetRef.current = Math.max(lastOffsetRef.current, payload.offset);
      }
      setConnectionState('streaming');
    });

    source.addEventListener('status', (event) => {
      const payload = parsePayload<StatusEventPayload>(event.data);
      if (!payload) {
        return;
      }
      if (payload.state === 'streaming') {
        setConnectionState('streaming');
      }
      if (payload.state === 'waiting_allocation' || payload.state === 'stream_unavailable') {
        if (payload.retryable === false) {
          shouldReconnectRef.current = false;
          closeSource();
          setConnectionState('unavailable');
          return;
        }
        setConnectionState('connecting');
      }
    });

    source.addEventListener('heartbeat', () => {
      setConnectionState('streaming');
    });

    source.addEventListener('unavailable', () => {
      shouldReconnectRef.current = false;
      closeSource();
      setConnectionState('unavailable');
    });

    source.addEventListener('end', () => {
      closeSource();
      if (shouldReconnectRef.current && activeStateRef.current) {
        setConnectionState('disconnected');
        return;
      }
      setConnectionState('idle');
    });

    source.onerror = () => {
      closeSource();
      if (!shouldReconnectRef.current) {
        setConnectionState('unavailable');
        return;
      }
      setConnectionState(activeStateRef.current ? 'disconnected' : 'idle');
    };
  }, [appendChunk, closeSource, jobId, selectedStream]);

  useEffect(() => {
    openStreamRef.current = openStream;
  }, [openStream]);

  useEffect(() => {
    activeStateRef.current = isJobActive;
  }, [isJobActive]);

  useEffect(() => {
    shouldReconnectRef.current = true;
    lastOffsetRef.current = 0;
    setLogText('');
    if (WAITING_STATUSES.has(jobStatus)) {
      closeSource();
      shouldReconnectRef.current = false;
      setConnectionState('waiting');
    } else {
      setConnectionState('connecting');
      openStreamRef.current();
    }

    return () => {
      shouldReconnectRef.current = false;
      closeSource();
    };
  }, [closeSource, jobId, jobStatus, openStream, selectedStream]);

  return (
    <Card>
      <CardHeader className="gap-3">
        <div className="flex flex-col gap-3 md:flex-row md:items-center md:justify-between">
          <CardTitle className="inline-flex items-center gap-2">
            <RiTerminalBoxLine aria-hidden="true" size={16} />
            Live Logs
          </CardTitle>
        </div>
        <div className="flex w-full flex-wrap items-center justify-between gap-2 text-xs">
          <div className="flex items-center gap-2">
            <span
              className={`inline-flex items-center gap-1 border px-2 py-1 ${getConnectionBadgeClassName(connectionState)}`}
            >
              {connectionState === 'streaming' ? (
                <RiPulseLine aria-hidden="true" size={12} />
              ) : (
                <RiRefreshLine aria-hidden="true" size={12} />
              )}
              {getConnectionBadgeLabel(connectionState)}
            </span>
            {connectionState === 'disconnected' ? (
              <Button
                type="button"
                size="sm"
                variant="outline"
                aria-label="Retry log stream connection"
                onClick={() => openStreamRef.current()}
              >
                Try again
              </Button>
            ) : null}
          </div>
          <div className="ml-auto flex items-center gap-2">
            <Button
              type="button"
              size="sm"
              variant={selectedStream === 'stdout' ? 'default' : 'outline'}
              onClick={() => setSelectedStream('stdout')}
            >
              stdout
            </Button>
            <Button
              type="button"
              size="sm"
              variant={selectedStream === 'stderr' ? 'default' : 'outline'}
              onClick={() => setSelectedStream('stderr')}
            >
              stderr
            </Button>
          </div>
        </div>
      </CardHeader>
      <CardContent className="space-y-3">
        <div className="bg-muted rounded-none border">
          <pre className="h-[420px] overflow-auto px-3 py-2 font-mono text-xs leading-5 whitespace-pre-wrap">
            {logText}
          </pre>
        </div>
      </CardContent>
    </Card>
  );
}
