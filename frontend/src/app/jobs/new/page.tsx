import type { Metadata } from 'next';
import { requireSession } from '@/lib/server-auth';
import { JobSubmissionForm } from './_components/job-submission-form';

export const metadata: Metadata = {
  title: 'Submit New Job - Private Cloud Resource Manager',
};

export default async function NewJobPage() {
  await requireSession('/jobs/new');

  return (
    <div className="bg-background/50 flex flex-1 flex-col justify-center">
      <div className="container mx-auto max-w-4xl px-4 md:px-6">
        <JobSubmissionForm />
      </div>
    </div>
  );
}
