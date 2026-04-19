import {
  RiArrowRightLine,
  RiCheckboxCircleLine,
  RiCloseCircleLine,
  RiFlashlightLine,
  RiPlayCircleLine,
  RiTimeLine,
} from '@remixicon/react';
import Link from 'next/link';
import type { JobHistoryItem } from '@/app/jobs/_components/types';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { formatUtcDateTime } from '@/lib/date-time';
import { formatMinutesAsHoursAndMinutes } from '@/lib/duration';

const FAILED_STATUSES = new Set(['FAILED', 'OOM_KILLED', 'LEASE_EXPIRED']);

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

export function HomeDashboard({ jobs, jobsError, quota, quotaError }: HomeDashboardProps) {
  const totalRecent = jobs.length;
  const runningCount = jobs.filter((job) => job.status === 'RUNNING').length;
  const completedCount = jobs.filter((job) => job.status === 'COMPLETED').length;
  const failedCount = jobs.filter((job) => FAILED_STATUSES.has(job.status)).length;
  const queuedCount = jobs.filter((job) => job.status === 'QUEUED').length;
  const quotaSegments = quota ? getQuotaBarSegments(quota) : null;

  return (
    <div className="bg-background/50 min-h-[calc(100vh-5.5rem)] w-full py-8">
      <div className="container mx-auto max-w-6xl space-y-6 px-4 md:px-6">
        <div>
          <h1 className="text-3xl font-semibold tracking-tight">Dashboard</h1>
          <p className="text-muted-foreground text-sm">Overview of your recent workload and shortcuts.</p>
        </div>

        <section className="grid gap-3 sm:grid-cols-2 xl:grid-cols-5">
          <Card>
            <CardHeader className="pb-2"><CardTitle>Total Recent</CardTitle></CardHeader>
            <CardContent className="flex items-center justify-between"><p className="text-2xl font-semibold">{totalRecent}</p><RiArrowRightLine size={18} /></CardContent>
          </Card>
          <Card>
            <CardHeader className="pb-2"><CardTitle>Queued</CardTitle></CardHeader>
            <CardContent className="flex items-center justify-between"><p className="text-2xl font-semibold">{queuedCount}</p><RiArrowRightLine size={18} /></CardContent>
          </Card>
          <Card>
            <CardHeader className="pb-2"><CardTitle>Running</CardTitle></CardHeader>
            <CardContent className="flex items-center justify-between"><p className="text-2xl font-semibold">{runningCount}</p><RiPlayCircleLine size={18} /></CardContent>
          </Card>
          <Card>
            <CardHeader className="pb-2"><CardTitle>Completed</CardTitle></CardHeader>
            <CardContent className="flex items-center justify-between"><p className="text-2xl font-semibold">{completedCount}</p><RiCheckboxCircleLine size={18} /></CardContent>
          </Card>
          <Card>
            <CardHeader className="pb-2"><CardTitle>Failed</CardTitle></CardHeader>
            <CardContent className="flex items-center justify-between"><p className="text-2xl font-semibold">{failedCount}</p><RiCloseCircleLine size={18} /></CardContent>
          </Card>
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

          <Card>
            <CardHeader>
              <CardTitle className="inline-flex items-center gap-2">
                <RiFlashlightLine size={16} />
                Quota
              </CardTitle>
              <CardDescription>Monthly prepaid runtime budget.</CardDescription>
            </CardHeader>
            <CardContent className="space-y-3">
              {quotaError ? <p className="text-destructive text-sm">{quotaError}</p> : null}
              {quota ? (
                <div className="space-y-3">
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
                        {formatUtcDateTime(quota.resetAt)}
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
              <Button asChild className="w-full justify-between"><Link href="/jobs/new">Submit New Job<RiArrowRightLine size={14} /></Link></Button>
              <Button asChild variant="outline" className="w-full justify-between"><Link href="/jobs">View Job History<RiArrowRightLine size={14} /></Link></Button>
            </CardContent>
          </Card>
        </section>
      </div>
    </div>
  );
}
