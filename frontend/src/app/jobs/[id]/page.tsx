import type { Metadata } from 'next';
import { notFound } from 'next/navigation';
import { JobDetailsPanel } from './_components/job-details-panel';
import type { JobDetails } from '@/app/jobs/_components/types';
import { brokerFetch } from '@/lib/server-auth';

export const metadata: Metadata = {
  title: 'Job Details - Private Cloud Resource Manager',
};

type JobDetailPageProps = {
  params: Promise<{ id: string }>;
};

export default async function JobDetailPage({ params }: JobDetailPageProps) {
  const { id } = await params;
  const nextPath = `/jobs/${id}`;

  const response = await brokerFetch(`/api/jobs/${encodeURIComponent(id)}`, {
    cache: 'no-store',
  }, nextPath);

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
