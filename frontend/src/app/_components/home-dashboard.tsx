import {
  RiArrowRightLine,
  RiCheckboxCircleLine,
  RiCloseCircleLine,
  RiFlashlightLine,
  RiPlayCircleLine,
  RiTimeLine,
} from '@remixicon/react';
import Link from 'next/link';
import type { JobHistoryItem, JobStatus } from '@/app/jobs/_components/types';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { formatLocalMonthDay, formatUtcDateTime } from '@/lib/date-time';
import { formatMinutesAsHoursAndMinutes } from '@/lib/duration';

const DEFAULT_HISTORY_PAGE_SIZE = 5;

type StatusCounts = {
  queued: number;
  running: number;
  completed: number;
  failed: number;
};

type HomeDashboardProps = {
  jobs: JobHistoryItem[];
  jobsError: string | null;
  quota: {
    allocatedMinutes: number;
    reservedMinutes: number;
    consumedMinutes: number;
    remainingMinutes: number;
    unlimited: boolean;
    resetAt: string;
  } | null;
  quotaError: string | null;
  statusCounts: StatusCounts;
};

function clampPercentage(value: number): number {
  if (!Number.isFinite(value)) {
    return 0;
  }
  return Math.max(0, Math.min(100, value));
}

function getQuotaBarSegments(quota: NonNullable<HomeDashboardProps['quota']>) {
  if (quota.unlimited || quota.allocatedMinutes <= 0) {
    return {
      consumedPercentage: 0,
      reservedPercentage: 0,
      remainingPercentage: 100,
    };
  }

  const consumedPercentage = clampPercentage((quota.consumedMinutes / quota.allocatedMinutes) * 100);
  const reservedPercentage = clampPercentage((quota.reservedMinutes / quota.allocatedMinutes) * 100);
  const remainingPercentage = Math.max(0, 100 - consumedPercentage - reservedPercentage);

  return {
    consumedPercentage,
    reservedPercentage,
    remainingPercentage,
  };
}

function buildJobsHistoryHref(statuses: JobStatus[] = []): string {
  const params = new URLSearchParams({
    page: '0',
    size: String(DEFAULT_HISTORY_PAGE_SIZE),
    sort: 'desc',
  });

  for (const status of statuses) {
    params.append('status', status);
  }

  return `/jobs?${params.toString()}`;
}

export function HomeDashboard({ jobs, jobsError, quota, quotaError, statusCounts }: HomeDashboardProps) {
  const { queued: queuedCount, running: runningCount, completed: completedCount, failed: failedCount } = statusCounts;
  const quotaSegments = quota ? getQuotaBarSegments(quota) : null;

  return (
    <div className="bg-background/50 min-h-[calc(100vh-5.5rem)] w-full py-8">
      <div className="container mx-auto max-w-6xl space-y-6 px-4 md:px-6">
        <div>
          <h1 className="text-3xl font-semibold tracking-tight">Dashboard</h1>
          <p className="text-muted-foreground text-sm">Overview of your recent workload and shortcuts.</p>
        </div>

        <section className="grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
          <Link href={buildJobsHistoryHref(['SUBMITTED', 'QUEUED'])}>
            <Card className="hover:border-primary/60 transition-colors">
              <CardHeader className="pb-2"><CardTitle>Queued</CardTitle></CardHeader>
              <CardContent className="flex items-center justify-between"><p className="text-2xl font-semibold">{queuedCount}</p><RiArrowRightLine size={18} /></CardContent>
            </Card>
          </Link>
          <Link href={buildJobsHistoryHref(['DISPATCHING', 'SCHEDULING', 'RUNNING'])}>
            <Card className="hover:border-primary/60 transition-colors">
              <CardHeader className="pb-2"><CardTitle>Running</CardTitle></CardHeader>
              <CardContent className="flex items-center justify-between"><p className="text-2xl font-semibold">{runningCount}</p><RiPlayCircleLine size={18} /></CardContent>
            </Card>
          </Link>
          <Link href={buildJobsHistoryHref(['SUCCEEDED'])}>
            <Card className="hover:border-primary/60 transition-colors">
              <CardHeader className="pb-2"><CardTitle>Completed</CardTitle></CardHeader>
              <CardContent className="flex items-center justify-between"><p className="text-2xl font-semibold">{completedCount}</p><RiCheckboxCircleLine size={18} /></CardContent>
            </Card>
          </Link>
          <Link href={buildJobsHistoryHref(['FAILED', 'TIMED_OUT', 'INFRA_FAILED', 'CANCELED'])}>
            <Card className="hover:border-primary/60 transition-colors">
              <CardHeader className="pb-2"><CardTitle>Failed</CardTitle></CardHeader>
              <CardContent className="flex items-center justify-between"><p className="text-2xl font-semibold">{failedCount}</p><RiCloseCircleLine size={18} /></CardContent>
            </Card>
          </Link>
        </section>

        <section className="grid gap-4 lg:grid-cols-3">
          <Card className="lg:col-span-2">
            <CardHeader>
              <CardTitle>Recent Jobs</CardTitle>
              <CardDescription>Latest submitted jobs with status and resources.</CardDescription>
            </CardHeader>
            <CardContent className="space-y-3">
              {jobsError ? <p className="text-destructive text-sm">{jobsError}</p> : null}
              {!jobsError && jobs.length === 0 ? <p className="text-muted-foreground text-sm">No jobs yet. Submit your first workload.</p> : null}
              {!jobsError && jobs.length > 0 ? (
                <div className="divide-border divide-y">
                  {jobs.map((job) => (
                    <Link
                      key={job.id}
                      href={`/jobs/${encodeURIComponent(job.id)}`}
                      className="hover:bg-muted/50 flex flex-col gap-2 px-2 py-3 transition-colors md:flex-row md:items-center md:justify-between"
                    >
                      <div className="space-y-1">
                        <p className="font-mono text-xs">{job.dockerImage}</p>
                        <p className="text-muted-foreground text-xs">{formatUtcDateTime(job.createdAt)}</p>
                      </div>
                      <div className="flex items-center gap-3 text-xs">
                        <span className="border-input bg-muted rounded-none border px-2 py-1">{job.status}</span>
                        <span>{job.reqCpuCores} CPU</span>
                        <span>{job.reqRamGb} GB RAM</span>
                      </div>
                    </Link>
                  ))}
                </div>
              ) : null}
            </CardContent>
          </Card>

          <Card className="h-full">
            <CardHeader>
              <CardTitle className="inline-flex items-center gap-2">
                <RiFlashlightLine size={16} />
                Quota
              </CardTitle>
              <CardDescription>Monthly prepaid runtime budget.</CardDescription>
            </CardHeader>
            <CardContent className="flex h-full flex-col gap-4">
              {quotaError ? <p className="text-destructive text-sm">{quotaError}</p> : null}
              {quota ? (
                <div className="flex flex-1 flex-col justify-between gap-3">
                  <div className="rounded-none border bg-muted/40 p-3">
                    <p className="text-muted-foreground text-xs uppercase tracking-wide">Remaining Runtime</p>
                    <p className="mt-1 text-2xl font-semibold">
                      {quota.unlimited ? 'Unlimited' : formatMinutesAsHoursAndMinutes(quota.remainingMinutes)}
                    </p>
                    <p className="text-muted-foreground mt-1 text-xs">
                      of
                      {' '}
                      {formatMinutesAsHoursAndMinutes(quota.allocatedMinutes)}
                      {' '}
                      monthly allocation
                    </p>
                  </div>

                  <div className="space-y-1">
                    <div className="flex items-center justify-between text-xs">
                      <span className="text-muted-foreground">Usage split</span>
                      <div className="text-muted-foreground inline-flex items-center gap-1">
                        <RiTimeLine size={12} />
                        Resets
                        {' '}
                        {formatLocalMonthDay(quota.resetAt)}
                      </div>
                    </div>
                    <div className="h-2.5 w-full overflow-hidden rounded-none border bg-muted">
                      <div className="flex h-full w-full">
                        <div
                          className="bg-primary/85"
                          style={{ width: `${quotaSegments?.consumedPercentage ?? 0}%` }}
                          title="Consumed"
                        />
                        <div
                          className="bg-amber-500/90"
                          style={{ width: `${quotaSegments?.reservedPercentage ?? 0}%` }}
                          title="Reserved"
                        />
                        <div
                          className="bg-emerald-500/85"
                          style={{ width: `${quotaSegments?.remainingPercentage ?? 100}%` }}
                          title="Remaining"
                        />
                      </div>
                    </div>
                  </div>

                  <div className="grid grid-cols-1 gap-2 text-xs">
                    <div className="flex items-center justify-between rounded-none border bg-primary/5 px-2 py-1.5">
                      <span className="text-muted-foreground">Consumed</span>
                      <span className="font-semibold">{formatMinutesAsHoursAndMinutes(quota.consumedMinutes)}</span>
                    </div>
                    <div className="flex items-center justify-between rounded-none border bg-amber-500/10 px-2 py-1.5">
                      <span className="text-muted-foreground">Reserved</span>
                      <span className="font-semibold">{formatMinutesAsHoursAndMinutes(quota.reservedMinutes)}</span>
                    </div>
                  </div>
                </div>
              ) : null}
              <div className="mt-auto space-y-2">
                <Button asChild className="w-full justify-between"><Link href="/jobs/new">Submit New Job<RiArrowRightLine size={14} /></Link></Button>
                <Button asChild variant="outline" className="w-full justify-between"><Link href="/jobs">View Job History<RiArrowRightLine size={14} /></Link></Button>
              </div>
            </CardContent>
          </Card>
        </section>
      </div>
    </div>
  );
}
