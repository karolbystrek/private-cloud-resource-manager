import type { Metadata } from 'next';
import { cookies } from 'next/headers';
import { redirect } from 'next/navigation';
import { HomeDashboard } from '@/app/_components/home-dashboard';
import type { JobHistoryItem, JobsPageResponse } from '@/app/jobs/_components/types';

export const metadata: Metadata = {
  title: 'Dashboard - Private Cloud Resource Manager',
};

const BACKEND_URL = process.env.NEXT_PUBLIC_BACKEND_URL;
const RECENT_JOBS_SIZE = 6;

type JobsResult = {
  jobs: JobHistoryItem[];
  error: string | null;
};

async function fetchRecentJobs(accessToken: string): Promise<JobsResult> {
  const response = await fetch(`${BACKEND_URL}/api/jobs?page=0&size=${RECENT_JOBS_SIZE}&sort=desc`, {
    headers: { Authorization: `Bearer ${accessToken}` },
    cache: 'no-store',
  });

  if (response.status === 401) {
    redirect('/login?next=/');
  }
  if (!response.ok) {
    return { jobs: [], error: 'Recent jobs are currently unavailable.' };
  }

  const data = (await response.json()) as JobsPageResponse;
  return { jobs: data.jobs ?? [], error: null };
}

export default async function Home() {
  if (!BACKEND_URL) {
    throw new Error('Backend URL is not configured.');
  }

  const cookieStore = await cookies();
  const accessToken = cookieStore.get('access_token')?.value;
  if (!accessToken) {
    redirect('/login?next=/');
  }

  const { jobs, error: jobsError } = await fetchRecentJobs(accessToken);

  return <HomeDashboard jobs={jobs} jobsError={jobsError} />;
}
