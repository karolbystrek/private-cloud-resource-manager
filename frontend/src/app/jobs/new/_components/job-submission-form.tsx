'use client';

import { useState } from 'react';
import { useRouter } from 'next/navigation';
import { Button } from '@/components/ui/button';
import {
  collectEnvVarErrorMessages,
  createEnvVarRow,
  type EnvVarRow,
  toEnvVarFieldErrors,
  toEnvVarsMap,
} from './env-vars';
import { EnvVarsEditor } from './env-vars-editor';
import { JobCommandFields } from './job-command-fields';
import { JobResourceFields } from './job-resource-fields';

type JobSubmissionPayload = {
  dockerImage: string;
  executionCommand: string;
  reqCpuCores: string;
  reqRamGb: string;
};

type JobSubmissionFieldErrors = Record<string, string[] | undefined>;

type JobSubmissionResponse = {
  jobId?: string;
  runId?: string;
  error?: string;
  fieldErrors?: JobSubmissionFieldErrors;
};

const initialFormData: JobSubmissionPayload = {
  dockerImage: '',
  executionCommand: '',
  reqCpuCores: '1',
  reqRamGb: '1',
};

function createIdempotencyKey() {
  return crypto.randomUUID();
}

export function JobSubmissionForm() {
  const router = useRouter();
  const [formData, setFormData] = useState<JobSubmissionPayload>(initialFormData);
  const [fieldErrors, setFieldErrors] = useState<JobSubmissionFieldErrors>({});
  const [envVarRows, setEnvVarRows] = useState<EnvVarRow[]>([createEnvVarRow()]);
  const [errorMessage, setErrorMessage] = useState('');
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [idempotencyKey, setIdempotencyKey] = useState<string>(() => createIdempotencyKey());

  function rotateIdempotencyKey() {
    setIdempotencyKey(createIdempotencyKey());
  }

  function handleInputChange(name: keyof JobSubmissionPayload, value: string) {
    rotateIdempotencyKey();
    setFormData((previous) => ({
      ...previous,
      [name]: value,
    }));
  }

  function handleEnvVarChange(id: string, field: 'key' | 'value', value: string) {
    rotateIdempotencyKey();
    setEnvVarRows((previous) =>
      previous.map((row) => {
        if (row.id !== id) {
          return row;
        }
        return {
          ...row,
          [field]: value,
        };
      }),
    );
  }

  function addEnvVarRow() {
    rotateIdempotencyKey();
    setEnvVarRows((previous) => [createEnvVarRow(), ...previous]);
  }

  function removeEnvVarRow(id: string) {
    rotateIdempotencyKey();
    setEnvVarRows((previous) => {
      const nextRows = previous.filter((row) => row.id !== id);
      if (nextRows.length > 0) {
        return nextRows;
      }
      return [createEnvVarRow()];
    });
  }

  async function submitJob() {
    setFieldErrors({});
    setErrorMessage('');

    const envVarErrors = toEnvVarFieldErrors(envVarRows);
    if (Object.keys(envVarErrors).length > 0) {
      setFieldErrors(envVarErrors);
      setErrorMessage('Please fix environment variable errors.');
      return;
    }

    setIsSubmitting(true);

    try {
      const response = await fetch('/api/jobs', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'Idempotency-Key': idempotencyKey,
        },
        body: JSON.stringify({
          dockerImage: formData.dockerImage,
          executionCommand: formData.executionCommand,
          reqCpuCores: Number.parseInt(formData.reqCpuCores, 10),
          reqRamGb: Number.parseInt(formData.reqRamGb, 10),
          envVars: toEnvVarsMap(envVarRows),
        }),
      });

      const responseData = (await response
        .json()
        .catch(() => null)) as JobSubmissionResponse | null;

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
        {errorMessage ? (
          <div className="text-destructive text-sm font-medium">{errorMessage}</div>
        ) : null}

        <JobCommandFields
          dockerImage={formData.dockerImage}
          executionCommand={formData.executionCommand}
          fieldErrors={fieldErrors}
          onChange={handleInputChange}
        />

        <EnvVarsEditor
          rows={envVarRows}
          onAdd={addEnvVarRow}
          onRemove={removeEnvVarRow}
          onChange={handleEnvVarChange}
          errorMessages={collectEnvVarErrorMessages(fieldErrors)}
        />

        <JobResourceFields
          reqCpuCores={formData.reqCpuCores}
          reqRamGb={formData.reqRamGb}
          fieldErrors={fieldErrors}
          onChange={handleInputChange}
        />

        <div className="pt-4">
          <Button type="submit" className="w-full rounded-none" size="lg" disabled={isSubmitting}>
            {isSubmitting ? 'Submitting...' : 'Submit Job'}
          </Button>
        </div>
      </form>
    </div>
  );
}
