'use client';

import { buildClearSessionPath } from '@/lib/auth';

export function redirectToLoginAfterAuthFailure(nextPath: string): void {
  window.location.href = buildClearSessionPath(nextPath);
}
