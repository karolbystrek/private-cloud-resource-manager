'use client';

import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { useSyncExternalStore } from 'react';
import { RiSortAsc, RiSortDesc } from '@remixicon/react';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import {
  DropdownMenuCheckboxItem,
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu';
import { formatLocalDateTime } from '@/lib/date-time';
import { formatMinutesAsHoursAndMinutes } from '@/lib/duration';
import type { JobHistorySortDirection, JobsPageResponse, JobStatus } from './types';

type JobsHistoryViewProps = {
  jobsPage: JobsPageResponse;
  statusFilters: JobStatus[];
};

const STATUS_OPTIONS: JobStatus[] = [
  'QUEUED',
  'PENDING',
  'RUNNING',
  'COMPLETED',
  'FAILED',
  'OOM_KILLED',
  'LEASE_EXPIRED',
  'STOPPED',
];

function buildJobsHref(
  page: number,
  size: number,
  sort: JobHistorySortDirection,
  statusFilters: JobStatus[],
): string {
  const params = new URLSearchParams({
    page: String(page),
    size: String(size),
    sort,
  });
  for (const status of statusFilters) {
    params.append('status', status);
  }
  return `/jobs?${params.toString()}`;
}

function formatCommandPreview(command: string): string {
  if (command.length <= 80) {
    return command;
  }
  return `${command.slice(0, 77)}...`;
}

function toggleStatus(current: JobStatus[], status: JobStatus): JobStatus[] {
  if (current.includes(status)) {
    return current.filter(item => item !== status);
  }
  const selected = new Set([...current, status]);
  return STATUS_OPTIONS.filter(item => selected.has(item));
}

export function JobsHistoryView({ jobsPage, statusFilters }: JobsHistoryViewProps) {
  const router = useRouter();
  const isClient = useSyncExternalStore(
    () => () => {},
    () => true,
    () => false,
  );
  const selectedStatusLabel = statusFilters.length === 0
    ? 'All statuses'
    : statusFilters.length === 1
      ? statusFilters[0]
      : `${statusFilters.length} statuses`;
  const isNewestFirst = jobsPage.sort === 'desc';
  const nextSort = isNewestFirst ? 'asc' : 'desc';
  const sortButtonTitle = isNewestFirst ? 'Newest first' : 'Oldest first';
  const currentPage = jobsPage.totalPages === 0 ? 0 : jobsPage.page + 1;
  const perPageOptions = [5, 10, 20, 50] as const;
  const sortHref = buildJobsHref(0, jobsPage.size, nextSort, statusFilters);
  const previousPageHref = buildJobsHref(jobsPage.page - 1, jobsPage.size, jobsPage.sort, statusFilters);
  const nextPageHref = buildJobsHref(jobsPage.page + 1, jobsPage.size, jobsPage.sort, statusFilters);
  const SortIcon = isNewestFirst ? RiSortDesc : RiSortAsc;

  const applyStatusFilters = (nextStatuses: JobStatus[]) => {
    router.push(buildJobsHref(0, jobsPage.size, jobsPage.sort, nextStatuses));
  };

  let jobsList = (
    <Card>
      <CardContent className="py-8">
        <p className="text-muted-foreground text-sm">No jobs yet.</p>
      </CardContent>
    </Card>
  );

  if (jobsPage.jobs.length > 0) {
    jobsList = (
      <div className="grid gap-4">
        {jobsPage.jobs.map(job => (
          <Link key={job.id} href={`/jobs/${encodeURIComponent(job.id)}`}>
            <Card className="hover:border-primary/60 transition-colors">
              <CardHeader className="space-y-3">
                <div className="flex flex-col gap-2 md:flex-row md:items-center md:justify-between">
                  <CardTitle className="text-lg font-medium">{job.dockerImage}</CardTitle>
                  <span className="border-input bg-muted rounded-none border px-2 py-1 text-xs">
                    {job.status}
                  </span>
                </div>
                <p className="text-muted-foreground text-sm">
                  {isClient ? formatLocalDateTime(job.createdAt) : '-'}
                </p>
              </CardHeader>
              <CardContent className="space-y-2 text-sm">
                <p className="font-mono">{formatCommandPreview(job.executionCommand)}</p>
                <div className="grid gap-1 md:grid-cols-2">
                  <p>
                    <span className="text-muted-foreground">CPU:</span>
                    {' '}
                    {job.reqCpuCores}
                  </p>
                  <p>
                    <span className="text-muted-foreground">RAM:</span>
                    {' '}
                    {job.reqRamGb}
                    {' '}
                    GB
                  </p>
                  <p>
                    <span className="text-muted-foreground">Consumed:</span>
                    {' '}
                    {formatMinutesAsHoursAndMinutes(job.totalConsumedMinutes)}
                  </p>
                  <p>
                    <span className="text-muted-foreground">Node:</span>
                    {' '}
                    {job.nodeId ?? '-'}
                  </p>
                </div>
              </CardContent>
            </Card>
          </Link>
        ))}
      </div>
    );
  }

  let previousButton = (
    <Button variant="outline" size="sm" disabled>
      Previous
    </Button>
  );

  if (jobsPage.hasPrevious) {
    previousButton = (
      <Button asChild variant="outline" size="sm">
        <Link href={previousPageHref}>Previous</Link>
      </Button>
    );
  }

  let nextButton = (
    <Button size="sm" disabled>
      Next
    </Button>
  );

  if (jobsPage.hasNext) {
    nextButton = (
      <Button asChild size="sm">
        <Link href={nextPageHref}>Next</Link>
      </Button>
    );
  }

  return (
    <section className="space-y-6">
      <div className="flex flex-col gap-3 md:flex-row md:items-center md:justify-between">
        <div>
          <h1 className="text-3xl font-semibold tracking-tight">Jobs</h1>
          <p className="text-muted-foreground text-sm">History of your submitted jobs.</p>
        </div>
        <div className="flex flex-col gap-2 md:items-end">
          <p className="text-muted-foreground text-sm">
            Jobs:
            {' '}
            {jobsPage.totalElements}
            {' | '}
            Page
            {' '}
            {currentPage}
            {' '}
            of
            {' '}
            {jobsPage.totalPages}
          </p>
          <div className="flex items-center gap-2">
            <Button asChild variant="outline" size="icon-sm">
              <Link href={sortHref} aria-label={sortButtonTitle} title={sortButtonTitle}>
                <SortIcon size={16} />
              </Link>
            </Button>
            <DropdownMenu>
              <DropdownMenuTrigger asChild>
                <Button variant="outline" size="sm">
                  {selectedStatusLabel}
                </Button>
              </DropdownMenuTrigger>
              <DropdownMenuContent align="end">
                <DropdownMenuItem
                  onSelect={(event) => {
                    event.preventDefault();
                    applyStatusFilters([]);
                  }}
                >
                  All statuses
                </DropdownMenuItem>
                {STATUS_OPTIONS.map(status => (
                  <DropdownMenuCheckboxItem
                    key={status}
                    checked={statusFilters.includes(status)}
                    onSelect={(event) => event.preventDefault()}
                    onCheckedChange={() => applyStatusFilters(toggleStatus(statusFilters, status))}
                  >
                    {status}
                  </DropdownMenuCheckboxItem>
                ))}
              </DropdownMenuContent>
            </DropdownMenu>
            <DropdownMenu>
              <DropdownMenuTrigger asChild>
                <Button variant="outline" size="sm">
                  {jobsPage.size}
                  {' '}
                  / page
                </Button>
              </DropdownMenuTrigger>
              <DropdownMenuContent align="end">
                {perPageOptions.map(option => (
                  <DropdownMenuItem key={option} asChild>
                    <Link href={buildJobsHref(0, option, jobsPage.sort, statusFilters)}>
                      {option}
                      {' '}
                      / page
                    </Link>
                  </DropdownMenuItem>
                ))}
              </DropdownMenuContent>
            </DropdownMenu>
          </div>
        </div>
      </div>

      {jobsList}

      <div className="flex items-center justify-end gap-2">
        {previousButton}
        {nextButton}
      </div>
    </section>
  );
}
