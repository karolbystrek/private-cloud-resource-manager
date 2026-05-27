'use client';

import { useEffect, useMemo, useState } from 'react';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';

type AdminQuotaGrantResponse = {
  grantId: string;
  userId?: string;
  minutes: number;
  remainingMinutes: number;
};

type AdminUserOption = {
  userId: string;
  email: string;
};

type FieldErrors = Record<string, string[] | undefined>;

type AdminQuotaGrantApiErrorResponse = {
  error?: string;
  fieldErrors?: FieldErrors;
};

function createIdempotencyKey() {
  return crypto.randomUUID();
}

function formatMinutesAsHours(minutes: number) {
  return minutes / 60;
}

function toMonthIntervalIso(monthValue: string): { intervalStart: string; intervalEnd: string } | null {
  // HTML input[type=month] returns: YYYY-MM
  const match = /^(\d{4})-(\d{2})$/.exec(monthValue);
  if (!match) return null;

  const year = Number.parseInt(match[1], 10);
  const month = Number.parseInt(match[2], 10); // 1-12
  if (!Number.isFinite(year) || !Number.isFinite(month) || month < 1 || month > 12) return null;

  // Backend expects intervalStart/intervalEnd to align to UTC month boundaries (00:00:00Z).
  const start = new Date(Date.UTC(year, month - 1, 1, 0, 0, 0, 0));
  const end = new Date(Date.UTC(year, month, 1, 0, 0, 0, 0));

  return {
    intervalStart: start.toISOString(),
    intervalEnd: end.toISOString(),
  };
}

export function AdminQuotaGrantForm() {
  const [userId, setUserId] = useState('');
  const [users, setUsers] = useState<AdminUserOption[]>([]);
  const [isLoadingUsers, setIsLoadingUsers] = useState(true);
  const [usersError, setUsersError] = useState('');
  const [hours, setHours] = useState('1');
  const [quotaMonth, setQuotaMonth] = useState(''); // optional; if empty, backend uses "now"
  const [reason, setReason] = useState('');

  const [idempotencyKey, setIdempotencyKey] = useState(() => createIdempotencyKey());
  const [isSubmitting, setIsSubmitting] = useState(false);

  const [errorMessage, setErrorMessage] = useState('');
  const [fieldErrors, setFieldErrors] = useState<FieldErrors>({});

  const [result, setResult] = useState<AdminQuotaGrantResponse | null>(null);

  function rotateIdempotencyKey() {
    setIdempotencyKey(createIdempotencyKey());
  }

  const parsedHours = useMemo(() => Number.parseFloat(hours), [hours]);
  const computedMinutes = useMemo(() => {
    if (!Number.isFinite(parsedHours)) return NaN;
    return Math.round(parsedHours * 60);
  }, [parsedHours]);

  const intervalIso = useMemo(() => {
    if (!quotaMonth) return null;
    return toMonthIntervalIso(quotaMonth);
  }, [quotaMonth]);

  useEffect(() => {
    let isCancelled = false;

    async function loadUsers() {
      setIsLoadingUsers(true);
      setUsersError('');
      try {
        const response = await fetch('/api/admin/quota/users', {
          method: 'GET',
          cache: 'no-store',
        });
        const body = (await response.json().catch(() => null)) as { error?: string } | AdminUserOption[] | null;
        if (!response.ok) {
          if (!isCancelled) {
            const errorBody = body as { error?: string } | null;
            setUsersError(errorBody?.error ?? 'Failed to load users.');
          }
          return;
        }

        if (!isCancelled) {
          const loadedUsers = Array.isArray(body) ? body : [];
          setUsers(loadedUsers);
          if (loadedUsers.length > 0) {
            setUserId((previous) => previous || loadedUsers[0].userId);
          }
        }
      } catch {
        if (!isCancelled) {
          setUsersError('Failed to load users.');
        }
      } finally {
        if (!isCancelled) {
          setIsLoadingUsers(false);
        }
      }
    }

    void loadUsers();
    return () => {
      isCancelled = true;
    };
  }, []);

  async function submit() {
    setIsSubmitting(true);
    setErrorMessage('');
    setFieldErrors({});
    setResult(null);

    try {
      if (!userId.trim()) {
        setErrorMessage('User ID is required.');
        return;
      }

      const minutes = computedMinutes;
      if (!Number.isFinite(minutes) || minutes < 1) {
        setErrorMessage('Hours must result in at least 1 minute.');
        return;
      }

      if (!reason.trim()) {
        setErrorMessage('Reason is required.');
        return;
      }

      if (quotaMonth && !intervalIso) {
        setErrorMessage('Quota month must be in format YYYY-MM.');
        return;
      }

      const payload: Record<string, unknown> = {
        userId: userId.trim() || undefined,
        minutes,
        reason: reason.trim() || undefined,
      };

      if (intervalIso) {
        payload.intervalStart = intervalIso.intervalStart;
        payload.intervalEnd = intervalIso.intervalEnd;
      }

      const response = await fetch('/api/admin/quota/grants', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'Idempotency-Key': idempotencyKey,
        },
        body: JSON.stringify(payload),
      });

      const data = (await response.json().catch(() => null)) as AdminQuotaGrantResponse | AdminQuotaGrantApiErrorResponse | null;

      if (!response.ok) {
        const errorBody = data as AdminQuotaGrantApiErrorResponse | null;
        setErrorMessage(errorBody?.error ?? 'Failed to grant quota.');
        setFieldErrors(errorBody?.fieldErrors ?? {});
        return;
      }

      // Backend returns AdminQuotaGrantResponse; we keep only a few fields for UI.
      const grant = data as AdminQuotaGrantResponse;
      setResult({
        grantId: grant.grantId,
        // UI doesn't need everything, but keep a friendly hint.
        minutes: (grant as unknown as { minutes?: number })?.minutes ?? minutes,
        remainingMinutes: (grant as unknown as { remainingMinutes?: number })?.remainingMinutes ?? 0,
      });
    } catch {
      setErrorMessage('Unexpected error. Please try again.');
    } finally {
      setIsSubmitting(false);
    }
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle>Grant Quota (Admin)</CardTitle>
        <CardDescription>Add additional quota minutes for a specific user in a selected month.</CardDescription>
      </CardHeader>
      <CardContent className="space-y-4">
        {errorMessage ? <p className="text-destructive text-sm font-medium">{errorMessage}</p> : null}

        {result ? (
          <div className="rounded-md border p-3">
            <p className="text-sm font-medium">Grant created</p>
            <p className="text-muted-foreground text-xs break-all">grantId: {result.grantId}</p>
            <p className="text-sm mt-2">
              Remaining quota: {formatMinutesAsHours(result.remainingMinutes).toFixed(2)} hours
            </p>
          </div>
        ) : null}

        <form
          className="space-y-6"
          onSubmit={(event) => {
            event.preventDefault();
            void submit();
          }}
        >
          <div className="space-y-2">
            <Label htmlFor="admin-grant-user-select">Select student (email)</Label>
            <select
              id="admin-grant-user-select"
              name="userId"
              className="border-input bg-background w-full rounded-none border px-3 py-2 text-sm"
              value={userId}
              onChange={(e) => {
                rotateIdempotencyKey();
                setUserId(e.target.value);
              }}
              disabled={isLoadingUsers || users.length === 0}
            >
              {isLoadingUsers ? <option>Loading users...</option> : null}
              {!isLoadingUsers && users.length === 0 ? <option>No students available</option> : null}
              {!isLoadingUsers
                ? users.map((user) => (
                  <option key={user.userId} value={user.userId}>
                    {user.email}
                  </option>
                ))
                : null}
            </select>
            {usersError ? (
              <p className="text-destructive text-sm font-medium">{usersError}</p>
            ) : (
              <p className="text-muted-foreground text-xs break-all">Selected userId: {userId || '—'}</p>
            )}
            {fieldErrors.userId?.map((message) => (
              <p key={message} className="text-destructive text-sm font-medium">
                {message}
              </p>
            ))}
          </div>

          <div className="grid gap-4 sm:grid-cols-2">
            <div className="space-y-2">
              <Label htmlFor="admin-grant-hours">Hours to grant</Label>
              <Input
                id="admin-grant-hours"
                name="hours"
                type="number"
                min="0"
                step="0.5"
                value={hours}
                onChange={(e) => {
                  rotateIdempotencyKey();
                  setHours(e.target.value);
                }}
              />
              <p className="text-muted-foreground text-xs">
                Will be sent as minutes: {Number.isFinite(computedMinutes) ? computedMinutes : '—'}
              </p>
              {fieldErrors.minutes?.map((message) => (
                <p key={message} className="text-destructive text-sm font-medium">
                  {message}
                </p>
              ))}
            </div>

            <div className="space-y-2">
              <Label htmlFor="admin-grant-month">Quota month (UTC)</Label>
              <Input
                id="admin-grant-month"
                name="quotaMonth"
                type="month"
                value={quotaMonth}
                onChange={(e) => {
                  rotateIdempotencyKey();
                  setQuotaMonth(e.target.value);
                }}
              />
              <p className="text-muted-foreground text-xs">Leave empty to grant for the current month.</p>
            </div>
          </div>

          <div className="space-y-2">
            <Label htmlFor="admin-grant-reason">Reason</Label>
            <Input
              id="admin-grant-reason"
              name="reason"
              placeholder="e.g. Admin adjustment for project delay"
              value={reason}
              onChange={(e) => {
                rotateIdempotencyKey();
                setReason(e.target.value);
              }}
            />
            {fieldErrors.reason?.map((message) => (
              <p key={message} className="text-destructive text-sm font-medium">
                {message}
              </p>
            ))}
          </div>

          <div className="flex gap-3">
            <Button type="submit" className="w-full" disabled={isSubmitting}>
              {isSubmitting ? 'Granting...' : 'Grant quota'}
            </Button>
          </div>
        </form>
      </CardContent>
    </Card>
  );
}

