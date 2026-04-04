import { cookies } from 'next/headers';
import { NextResponse } from 'next/server';

type JobSubmissionBody = {
  dockerImage: string;
  executionCommand: string;
  reqCpuCores: number;
  reqRamGb: number;
  reqGpuCount: number;
};

type JobSubmissionResponse = {
  jobId: string;
};

type BrokerFieldError = {
  field?: string;
  message?: string;
};

type BrokerProblemDetail = {
  detail?: string;
  errors?: BrokerFieldError[];
};

type JobSubmissionFieldErrors = {
  dockerImage?: string[];
  executionCommand?: string[];
  reqCpuCores?: string[];
  reqRamGb?: string[];
  reqGpuCount?: string[];
};

type ApiErrorResponse = {
  error: string;
  fieldErrors?: JobSubmissionFieldErrors;
};

const BACKEND_URL = process.env.NEXT_PUBLIC_BACKEND_URL;

const validFields = new Set([
  'dockerImage',
  'executionCommand',
  'reqCpuCores',
  'reqRamGb',
  'reqGpuCount',
]);

function toNumber(value: unknown): number {
  if (typeof value === 'number') {
    return value;
  }
  if (typeof value === 'string') {
    return Number.parseInt(value, 10);
  }
  return Number.NaN;
}

function parseBody(rawBody: unknown): JobSubmissionBody | null {
  if (!rawBody || typeof rawBody !== 'object') {
    return null;
  }

  const body = rawBody as Record<string, unknown>;
  const reqCpuCores = toNumber(body.reqCpuCores);
  const reqRamGb = toNumber(body.reqRamGb);
  const reqGpuCount = toNumber(body.reqGpuCount);

  if (
    typeof body.dockerImage !== 'string' ||
    typeof body.executionCommand !== 'string' ||
    Number.isNaN(reqCpuCores) ||
    Number.isNaN(reqRamGb) ||
    Number.isNaN(reqGpuCount)
  ) {
    return null;
  }

  return {
    dockerImage: body.dockerImage,
    executionCommand: body.executionCommand,
    reqCpuCores,
    reqRamGb,
    reqGpuCount,
  };
}

function buildFieldErrors(errors: BrokerFieldError[] | undefined): JobSubmissionFieldErrors | undefined {
  if (!errors?.length) {
    return undefined;
  }

  const fieldErrors: JobSubmissionFieldErrors = {};

  for (const item of errors) {
    if (!item.field || !item.message || !validFields.has(item.field)) {
      continue;
    }

    const key = item.field as keyof JobSubmissionFieldErrors;
    const existingMessages = fieldErrors[key] ?? [];
    fieldErrors[key] = [...existingMessages, item.message];
  }

  return Object.keys(fieldErrors).length ? fieldErrors : undefined;
}

function mapErrorResponse(status: number, problem: BrokerProblemDetail | null): ApiErrorResponse {
  const fieldErrors = buildFieldErrors(problem?.errors);

  if (status === 401) {
    return { error: 'Session expired. Please log in again.' };
  }

  if (status === 402) {
    return { error: problem?.detail ?? 'Insufficient balance to start this job.' };
  }

  if (status === 503) {
    return { error: problem?.detail ?? 'Scheduler is currently unavailable. Please try again.' };
  }

  if (status === 400) {
    return {
      error: problem?.detail ?? 'Invalid job submission data.',
      fieldErrors,
    };
  }

  return {
    error: problem?.detail ?? 'Failed to submit job. Please try again.',
  };
}

export async function POST(request: Request) {
  if (!BACKEND_URL) {
    return NextResponse.json({ error: 'Backend URL is not configured.' }, { status: 500 });
  }

  const cookieStore = await cookies();
  const accessToken = cookieStore.get('access_token')?.value;

  if (!accessToken) {
    return NextResponse.json({ error: 'Session expired. Please log in again.' }, { status: 401 });
  }

  const parsedBody = parseBody(await request.json().catch(() => null));
  if (!parsedBody) {
    return NextResponse.json({ error: 'Invalid job submission data.' }, { status: 400 });
  }

  try {
    const backendResponse = await fetch(`${BACKEND_URL}/api/jobs`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${accessToken}`,
      },
      body: JSON.stringify(parsedBody),
    });

    if (!backendResponse.ok) {
      const problem = (await backendResponse.json().catch(() => null)) as BrokerProblemDetail | null;
      return NextResponse.json(mapErrorResponse(backendResponse.status, problem), {
        status: backendResponse.status,
      });
    }

    const data = (await backendResponse.json()) as JobSubmissionResponse;
    return NextResponse.json({ jobId: data.jobId }, { status: 201 });
  } catch {
    return NextResponse.json({ error: 'Unexpected error. Please try again.' }, { status: 500 });
  }
}
