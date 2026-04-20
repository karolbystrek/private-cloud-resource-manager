import type { Metadata } from 'next';
import { cookies } from 'next/headers';
import { notFound, redirect } from 'next/navigation';
import { JobDetailsPanel } from './_components/job-details-panel';
import type { JobDetails } from '@/app/jobs/_components/types';

const BACKEND_URL = process.env.NEXT_PUBLIC_BACKEND_URL;

export const metadata: Metadata = {
  title: 'Job Details - Private Cloud Resource Manager',
};

type JobDetailPageProps = {
  params: Promise<{ id: string }>;
};

export default async function JobDetailPage({ params }: JobDetailPageProps) {
  if (!BACKEND_URL) {
    throw new Error('Backend URL is not configured.');
  }

  const { id } = await params;
  const accessToken = (await cookies()).get('access_token')?.value;
  if (!accessToken) {
    redirect(`/login?next=${encodeURIComponent(`/jobs/${id}`)}`);
  }

  const response = await fetch(`${BACKEND_URL}/api/jobs/${encodeURIComponent(id)}`, {
    headers: {
      Authorization: `Bearer ${accessToken}`,
    },
    cache: 'no-store',
  });

  if (response.status === 401) {
    redirect(`/login?next=${encodeURIComponent(`/jobs/${id}`)}`);
  }

  if (response.status === 404) {
    notFound();
  }

  if (!response.ok) {
    throw new Error('Failed to load job details.');
  }

  const job = (await response.json()) as JobDetails;

  return (
    <div className="bg-background/50 min-h-[calc(100vh-3.5rem)] w-full py-8">
      <div className="container mx-auto max-w-6xl px-4 md:px-6">
        <JobDetailsPanel jobId={id} initialJob={job} />
      </div>
    </div>
  );
}
