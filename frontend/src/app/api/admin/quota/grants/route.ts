import { NextResponse } from 'next/server';
import { getBackendUrlForServer } from '@/lib/backend-url';
import { getBrokerAccessToken } from '@/lib/broker-proxy';

type AdminQuotaGrantPayload = {
  userId: string;
  minutes: number;
  intervalStart?: string;
  intervalEnd?: string;
  reason: string;
};

type BrokerFieldError = {
  field?: string;
  message?: string;
};

type BrokerProblemDetail = {
  title?: string;
  detail?: string;
  errors?: BrokerFieldError[];
};

type FieldErrors = Record<string, string[] | undefined>;

type ApiErrorResponse = {
  error: string;
  fieldErrors?: FieldErrors;
};

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}

function toStringOrUndefined(value: unknown): string | undefined {
  if (typeof value !== 'string') return undefined;
  const trimmed = value.trim();
  return trimmed.length ? trimmed : undefined;
}

function toNumberOrNaN(value: unknown): number {
  if (typeof value === 'number') return value;
  if (typeof value === 'string') return Number.parseInt(value, 10);
  return Number.NaN;
}

function parseBody(rawBody: unknown): AdminQuotaGrantPayload | null {
  if (!isRecord(rawBody)) return null;

  const userId = toStringOrUndefined(rawBody.userId);
  const reason = toStringOrUndefined(rawBody.reason);
  const minutes = toNumberOrNaN(rawBody.minutes);

  const intervalStart = typeof rawBody.intervalStart === 'string' ? rawBody.intervalStart : undefined;
  const intervalEnd = typeof rawBody.intervalEnd === 'string' ? rawBody.intervalEnd : undefined;

  if (!userId || !reason || !Number.isFinite(minutes)) return null;

  return {
    userId,
    minutes,
    intervalStart,
    intervalEnd,
    reason,
  };
}

function buildFieldErrors(errors: BrokerFieldError[] | undefined): FieldErrors | undefined {
  if (!errors?.length) return undefined;

  const fieldErrors: FieldErrors = {};
  for (const item of errors) {
    const field = item.field;
    const message = item.message;
    if (!field || !message) continue;
    const existing = fieldErrors[field] ?? [];
    fieldErrors[field] = [...existing, message];
  }

  return Object.keys(fieldErrors).length ? fieldErrors : undefined;
}

function mapErrorResponse(status: number, problem: BrokerProblemDetail | null): ApiErrorResponse {
  const fieldErrors = buildFieldErrors(problem?.errors);

  if (status === 401) {
    return { error: 'Session expired. Please log in again.' };
  }

  if (status === 403) {
    return { error: problem?.detail ?? problem?.title ?? 'Access denied.' };
  }

  if (status === 404) {
    return { error: problem?.detail ?? 'Resource not found.' };
  }

  if (status === 400) {
    return { error: problem?.detail ?? problem?.title ?? 'Invalid request.', fieldErrors };
  }

  if (status === 402) {
    return { error: problem?.detail ?? 'Insufficient quota.' };
  }

  if (status === 409) {
    return { error: problem?.detail ?? problem?.title ?? 'Conflict.' };
  }

  return { error: problem?.detail ?? problem?.title ?? 'Failed to grant quota.', fieldErrors };
}

export async function POST(request: Request) {
  let BACKEND_URL: string;
  try {
    BACKEND_URL = getBackendUrlForServer();
  } catch {
    return NextResponse.json({ error: 'Backend URL is not configured.' }, { status: 500 });
  }

  const accessTokenResult = await getBrokerAccessToken();
  if (!accessTokenResult.ok) {
    return accessTokenResult.response;
  }

  const idempotencyKey = request.headers.get('Idempotency-Key')?.trim();
  if (!idempotencyKey) {
    return NextResponse.json({ error: 'Missing Idempotency-Key header.' }, { status: 400 });
  }

  const parsedBody = parseBody(await request.json().catch(() => null));
  if (!parsedBody) {
    return NextResponse.json({ error: 'Invalid quota grant data.' }, { status: 400 });
  }

  try {
    const backendResponse = await fetch(`${BACKEND_URL}/api/admin/quota/grants`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${accessTokenResult.accessToken}`,
        'Idempotency-Key': idempotencyKey,
      },
      body: JSON.stringify(parsedBody),
    });

    if (!backendResponse.ok) {
      const problem = (await backendResponse.json().catch(() => null)) as BrokerProblemDetail | null;
      return NextResponse.json(mapErrorResponse(backendResponse.status, problem), {
        status: backendResponse.status,
      });
    }

    const data = (await backendResponse.json()) as unknown;
    // Pass the backend response through; the client can pick what it needs.
    return NextResponse.json(data, { status: backendResponse.status });
  } catch {
    return NextResponse.json({ error: 'Unexpected error. Please try again.' }, { status: 500 });
  }
}

