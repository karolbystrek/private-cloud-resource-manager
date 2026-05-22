import type { Metadata } from 'next';
import { HomeDashboard } from '@/app/_components/home-dashboard';
import type { JobHistoryItem, JobsPageResponse, JobStatus } from '@/app/jobs/_components/types';
import { brokerFetch } from '@/lib/server-auth';

export const metadata: Metadata = {
  title: 'Dashboard - Private Cloud Resource Manager',
};

const RECENT_JOBS_SIZE = 6;

type JobsResult = {
  jobs: JobHistoryItem[];
  error: string | null;
};

type QuotaSummary = {
  allocatedMinutes: number;
  reservedMinutes: number;
  consumedMinutes: number;
  remainingMinutes: number;
  unlimited: boolean;
  resetAt: string;
};

type QuotaResult = {
  quota: QuotaSummary | null;
  error: string | null;
};

async function fetchRecentJobs(): Promise<JobsResult> {
  const response = await brokerFetch(`/api/jobs?page=0&size=${RECENT_JOBS_SIZE}&sort=desc`, {
    cache: 'no-store',
  }, '/');
  if (!response.ok) {
    return { jobs: [], error: 'Recent jobs are currently unavailable.' };
  }

  const data = (await response.json()) as JobsPageResponse;
  return { jobs: data.jobs ?? [], error: null };
}

const RUNNING_STATUSES: JobStatus[] = ['DISPATCHING', 'SCHEDULING', 'RUNNING'];
const FAILED_STATUSES: JobStatus[] = ['FAILED', 'TIMED_OUT', 'INFRA_FAILED', 'CANCELED'];

async function fetchStatusCount(statuses: JobStatus[]): Promise<number> {
  const params = new URLSearchParams({ page: '0', size: '1', sort: 'desc' });
  for (const status of statuses) {
    params.append('status', status);
  }

  const response = await brokerFetch(`/api/jobs?${params.toString()}`, {
    cache: 'no-store',
  }, '/');
  if (!response.ok) {
    return 0;
  }

  const data = (await response.json()) as JobsPageResponse;
  return data.totalElements ?? 0;
}

async function fetchStatusCounts() {
  const [queued, running, completed, failed] = await Promise.all([
    fetchStatusCount(['SUBMITTED', 'QUEUED']),
    fetchStatusCount(RUNNING_STATUSES),
    fetchStatusCount(['SUCCEEDED']),
    fetchStatusCount(FAILED_STATUSES),
  ]);
  return { queued, running, completed, failed };
}

async function fetchQuotaSummary(): Promise<QuotaResult> {
  const response = await brokerFetch('/api/quota/me', {
    cache: 'no-store',
  }, '/');

  if (!response.ok) {
    return { quota: null, error: 'Quota summary is currently unavailable.' };
  }

  const quota = (await response.json()) as QuotaSummary;
  return { quota, error: null };
}

export default async function Home() {
  const [{ jobs, error: jobsError }, { quota, error: quotaError }, statusCounts] = await Promise.all([
    fetchRecentJobs(),
    fetchQuotaSummary(),
    fetchStatusCounts(),
  ]);

  return (
    <HomeDashboard
      jobs={jobs}
      jobsError={jobsError}
      quota={quota}
      quotaError={quotaError}
      statusCounts={statusCounts}
    />
  );
}
