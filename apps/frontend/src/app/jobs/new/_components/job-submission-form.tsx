'use client';

import { useRouter } from 'next/navigation';
import { useState } from 'react';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';

type JobSubmissionPayload = {
  dockerImage: string;
  executionCommand: string;
  reqCpuCores: string;
  reqRamGb: string;
  reqGpuCount: string;
};

type JobSubmissionFieldErrors = {
  dockerImage?: string[];
  executionCommand?: string[];
  reqCpuCores?: string[];
  reqRamGb?: string[];
  reqGpuCount?: string[];
};

type JobSubmissionResponse = {
  jobId?: string;
  error?: string;
  fieldErrors?: JobSubmissionFieldErrors;
};

const initialFormData: JobSubmissionPayload = {
  dockerImage: '',
  executionCommand: '',
  reqCpuCores: '1',
  reqRamGb: '1',
  reqGpuCount: '0',
};

export function JobSubmissionForm() {
  const router = useRouter();
  const [formData, setFormData] = useState<JobSubmissionPayload>(initialFormData);
  const [fieldErrors, setFieldErrors] = useState<JobSubmissionFieldErrors>({});
  const [errorMessage, setErrorMessage] = useState('');
  const [isSubmitting, setIsSubmitting] = useState(false);

  function handleInputChange(name: keyof JobSubmissionPayload, value: string) {
    setFormData((previous) => ({
      ...previous,
      [name]: value,
    }));
  }

  async function submitJob() {
    setFieldErrors({});
    setErrorMessage('');
    setIsSubmitting(true);

    try {
      const response = await fetch('/api/jobs', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({
          dockerImage: formData.dockerImage,
          executionCommand: formData.executionCommand,
          reqCpuCores: Number.parseInt(formData.reqCpuCores, 10),
          reqRamGb: Number.parseInt(formData.reqRamGb, 10),
          reqGpuCount: Number.parseInt(formData.reqGpuCount, 10),
        }),
      });

      const responseData = (await response.json().catch(() => null)) as JobSubmissionResponse | null;

      if (!response.ok) {
        setFieldErrors(responseData?.fieldErrors ?? {});
        setErrorMessage(responseData?.error ?? 'Failed to submit job. Please try again.');
        return;
      }

      if (!responseData?.jobId) {
        setErrorMessage('Failed to submit job. Please try again.');
        return;
      }

      router.replace(`/jobs/${responseData.jobId}`);
      router.refresh();
    } catch {
      setErrorMessage('Unexpected error. Please try again.');
    } finally {
      setIsSubmitting(false);
    }
  }

  return (
    <div className="mx-auto max-w-xl space-y-8 py-8">
      <div className="space-y-2">
        <h2 className="text-3xl font-semibold tracking-tight">Deploy Compute Container</h2>
        <p className="text-muted-foreground text-sm">
          Submit a new job to the distributed laboratory cluster.
        </p>
      </div>
      <form
        className="space-y-8"
        onSubmit={(event) => {
          event.preventDefault();
          void submitJob();
        }}
      >
        {errorMessage ? <div className="text-destructive text-sm font-medium">{errorMessage}</div> : null}

        <div className="space-y-8">
          <div className="space-y-2">
            <Label htmlFor="dockerImage" className="text-sm font-medium">
              Docker Image
            </Label>
            <Input
              id="dockerImage"
              name="dockerImage"
              placeholder="e.g. nvidia/cuda:11.8.0-base-ubuntu22.04"
              required
              className="font-mono"
              value={formData.dockerImage}
              onChange={(event) => handleInputChange('dockerImage', event.target.value)}
            />
            {fieldErrors.dockerImage?.map((message) => (
              <p key={message} className="text-destructive text-sm font-medium">
                {message}
              </p>
            ))}
          </div>

          <div className="space-y-2">
            <Label htmlFor="executionCommand" className="text-sm font-medium">
              Execution Command
            </Label>
            <Input
              id="executionCommand"
              name="executionCommand"
              placeholder="e.g. python train_model.py --epochs 10"
              required
              className="font-mono"
              value={formData.executionCommand}
              onChange={(event) => handleInputChange('executionCommand', event.target.value)}
            />
            {fieldErrors.executionCommand?.map((message) => (
              <p key={message} className="text-destructive text-sm font-medium">
                {message}
              </p>
            ))}
          </div>
        </div>

        <div className="grid gap-8 sm:grid-cols-3">
          <div className="space-y-2">
            <Label htmlFor="reqCpuCores" className="text-sm font-medium">
              CPU Cores
            </Label>
            <Input
              id="reqCpuCores"
              name="reqCpuCores"
              type="number"
              min="1"
              required
              value={formData.reqCpuCores}
              onChange={(event) => handleInputChange('reqCpuCores', event.target.value)}
            />
            {fieldErrors.reqCpuCores?.map((message) => (
              <p key={message} className="text-destructive text-sm font-medium">
                {message}
              </p>
            ))}
          </div>
          <div className="space-y-2">
            <Label htmlFor="reqRamGb" className="text-sm font-medium">
              RAM (GB)
            </Label>
            <Input
              id="reqRamGb"
              name="reqRamGb"
              type="number"
              min="1"
              required
              value={formData.reqRamGb}
              onChange={(event) => handleInputChange('reqRamGb', event.target.value)}
            />
            {fieldErrors.reqRamGb?.map((message) => (
              <p key={message} className="text-destructive text-sm font-medium">
                {message}
              </p>
            ))}
          </div>
          <div className="space-y-2">
            <Label htmlFor="reqGpuCount" className="text-sm font-medium">
              GPU Count
            </Label>
            <Input
              id="reqGpuCount"
              name="reqGpuCount"
              type="number"
              min="0"
              required
              value={formData.reqGpuCount}
              onChange={(event) => handleInputChange('reqGpuCount', event.target.value)}
            />
            {fieldErrors.reqGpuCount?.map((message) => (
              <p key={message} className="text-destructive text-sm font-medium">
                {message}
              </p>
            ))}
          </div>
        </div>

        <div className="pt-4">
          <Button type="submit" className="w-full rounded-none" size="lg" disabled={isSubmitting}>
            {isSubmitting ? 'Submitting...' : 'Submit Job'}
          </Button>
        </div>
      </form>
    </div>
  );
}
