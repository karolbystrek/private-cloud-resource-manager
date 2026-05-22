import type { Metadata } from 'next';
import { JobsHistoryView } from './_components/jobs-history-view';
import type { JobHistorySortDirection, JobsPageResponse, JobStatus } from './_components/types';
import { brokerFetch } from '@/lib/server-auth';

export const metadata: Metadata = {
  title: 'Jobs - Private Cloud Resource Manager',
};

const DEFAULT_PAGE = 0;
const DEFAULT_SIZE = 5;
const MAX_SIZE = 50;
const STATUS_OPTIONS: JobStatus[] = [
  'SUBMITTED',
  'QUEUED',
  'DISPATCHING',
  'SCHEDULING',
  'RUNNING',
  'FINALIZING',
  'SUCCEEDED',
  'FAILED',
  'CANCELED',
  'TIMED_OUT',
  'INFRA_FAILED',
];

type JobsPageSearchParams = Promise<{
  page?: string | string[];
  size?: string | string[];
  sort?: string | string[];
  status?: string | string[];
}>;

function toSingleValue(value: string | string[] | undefined): string | undefined {
  if (Array.isArray(value)) {
    return value[0];
  }
  return value;
}

function parsePage(value: string | undefined): number {
  const parsed = Number.parseInt(value ?? '', 10);
  if (Number.isNaN(parsed) || parsed < 0) {
    return DEFAULT_PAGE;
  }
  return parsed;
}

function parseSize(value: string | undefined): number {
  const parsed = Number.parseInt(value ?? '', 10);
  if (Number.isNaN(parsed) || parsed < 1) {
    return DEFAULT_SIZE;
  }
  return Math.min(parsed, MAX_SIZE);
}

function parseSort(value: string | undefined): JobHistorySortDirection {
  if (value?.toLowerCase() === 'asc') {
    return 'asc';
  }
  return 'desc';
}

function parseStatuses(value: string | string[] | undefined): JobStatus[] {
  if (!value) {
    return [];
  }

  const allowedStatuses = new Set<JobStatus>(STATUS_OPTIONS);
  const rawValues = Array.isArray(value) ? value : [value];
  const normalized = rawValues
    .flatMap(entry => entry.split(','))
    .map(entry => entry.trim().toUpperCase())
    .filter(entry => allowedStatuses.has(entry as JobStatus)) as JobStatus[];

  const uniqueStatuses = new Set<JobStatus>(normalized);
  return STATUS_OPTIONS.filter(status => uniqueStatuses.has(status));
}

function buildJobsPath(
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

export default async function JobsPage({ searchParams }: { searchParams: JobsPageSearchParams }) {
  const resolvedSearchParams = await searchParams;
  const page = parsePage(toSingleValue(resolvedSearchParams.page));
  const size = parseSize(toSingleValue(resolvedSearchParams.size));
  const sort = parseSort(toSingleValue(resolvedSearchParams.sort));
  const statusFilters = parseStatuses(resolvedSearchParams.status);
  const jobsPath = buildJobsPath(page, size, sort, statusFilters);

  const backendParams = new URLSearchParams({
    page: String(page),
    size: String(size),
    sort,
  });
  for (const status of statusFilters) {
    backendParams.append('status', status);
  }

  const response = await brokerFetch(`/api/jobs?${backendParams.toString()}`, {
    cache: 'no-store',
  }, jobsPath);

  if (!response.ok) {
    throw new Error('Failed to load jobs.');
  }

  const jobsPage = (await response.json()) as JobsPageResponse;

  return (
    <div className="bg-background/50 min-h-[calc(100vh-3.5rem)] w-full py-8">
      <div className="container mx-auto max-w-6xl px-4 md:px-6">
        <JobsHistoryView jobsPage={jobsPage} statusFilters={statusFilters} />
      </div>
    </div>
  );
}
