'use client';

import { useEffect, useState } from 'react';
import { useRouter } from 'next/navigation';
import type { GpuOption } from '@/app/jobs/_components/types';
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
  gpuEnabled: boolean;
  gpuCount: string;
  gpuMinMemoryGb: string;
  gpuModel: string;
};

type JobSubmissionFieldErrors = Record<string, string[] | undefined>;

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
  gpuEnabled: false,
  gpuCount: '1',
  gpuMinMemoryGb: '',
  gpuModel: '',
};

function createIdempotencyKey() {
  return crypto.randomUUID();
}

export function JobSubmissionForm() {
  const router = useRouter();
  const [formData, setFormData] = useState<JobSubmissionPayload>(initialFormData);
  const [fieldErrors, setFieldErrors] = useState<JobSubmissionFieldErrors>({});
  const [envVarRows, setEnvVarRows] = useState<EnvVarRow[]>([createEnvVarRow()]);
  const [gpuOptions, setGpuOptions] = useState<GpuOption[]>([]);
  const [selectedGpuOptionKey, setSelectedGpuOptionKey] = useState('');
  const [errorMessage, setErrorMessage] = useState('');
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [idempotencyKey, setIdempotencyKey] = useState<string>(() => createIdempotencyKey());

  function rotateIdempotencyKey() {
    setIdempotencyKey(createIdempotencyKey());
  }

  useEffect(() => {
    let isActive = true;

    async function loadGpuOptions() {
      try {
        const response = await fetch('/api/jobs/gpu-options', { cache: 'no-store' });
        if (!response.ok) {
          return;
        }
        const options = (await response.json()) as GpuOption[];
        if (isActive) {
          setGpuOptions(options);
        }
      } catch {
      }
    }

    void loadGpuOptions();
    return () => {
      isActive = false;
    };
  }, []);

  function handleInputChange(name: keyof JobSubmissionPayload, value: string | boolean) {
    rotateIdempotencyKey();
    setFormData((previous) => ({
      ...previous,
      [name]: value,
    }));
  }

  function handleGpuOptionChange(optionKey: string) {
    rotateIdempotencyKey();
    setSelectedGpuOptionKey(optionKey);
    const option = gpuOptions.find((item) => gpuOptionKey(item) === optionKey);
    setFormData((previous) => ({
      ...previous,
      gpuModel: option?.model ?? '',
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
    const gpuErrors: JobSubmissionFieldErrors = {};
    if (formData.gpuEnabled && !formData.gpuModel) {
      gpuErrors['gpuRequirement.model'] = ['Select an available GPU model.'];
    }

    const nextFieldErrors = { ...envVarErrors, ...gpuErrors };
    if (Object.keys(nextFieldErrors).length > 0) {
      setFieldErrors(nextFieldErrors);
      setErrorMessage('Please fix highlighted fields.');
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
          gpuRequirement: formData.gpuEnabled
            ? {
                enabled: true,
                count: Number.parseInt(formData.gpuCount, 10),
                vendor: 'nvidia',
                minMemoryGb: formData.gpuMinMemoryGb.trim()
                  ? Number.parseInt(formData.gpuMinMemoryGb, 10)
                  : null,
                model: formData.gpuModel.trim() || null,
              }
            : { enabled: false },
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
          gpuEnabled={formData.gpuEnabled}
          gpuOptions={gpuOptions}
          selectedGpuOptionKey={selectedGpuOptionKey}
          gpuCount={formData.gpuCount}
          gpuMinMemoryGb={formData.gpuMinMemoryGb}
          gpuModel={formData.gpuModel}
          fieldErrors={fieldErrors}
          onChange={handleInputChange}
          onGpuOptionChange={handleGpuOptionChange}
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

function gpuOptionKey(option: GpuOption): string {
  return `${option.nodeId}:${option.model}`;
}
