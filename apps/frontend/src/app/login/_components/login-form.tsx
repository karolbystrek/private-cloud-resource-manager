'use client';

import { ChangeEvent, FormEvent, useMemo, useState } from 'react';
import Link from 'next/link';
import { useRouter, useSearchParams } from 'next/navigation';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';

type LoginPayload = {
  username: string;
  password: string;
};

function isSafeRedirectTarget(value: string | null): value is string {
  return Boolean(value && value.startsWith('/') && !value.startsWith('//'));
}

export function LoginForm() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const [formData, setFormData] = useState<LoginPayload>({
    username: '',
    password: '',
  });
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [errorMessage, setErrorMessage] = useState('');
  const isRegistered = searchParams.get('registered') === '1';

  const redirectTarget = useMemo(() => {
    const nextParam = searchParams.get('next');
    if (!isSafeRedirectTarget(nextParam) || nextParam === '/login') {
      return '/';
    }
    return nextParam;
  }, [searchParams]);

  function handleUsernameChange(event: ChangeEvent<HTMLInputElement>) {
    setFormData((previous) => ({
      ...previous,
      username: event.target.value,
    }));
  }

  function handlePasswordChange(event: ChangeEvent<HTMLInputElement>) {
    setFormData((previous) => ({
      ...previous,
      password: event.target.value,
    }));
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setErrorMessage('');
    setIsSubmitting(true);

    try {
      const response = await fetch('/api/auth/login', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify(formData),
      });

      if (!response.ok) {
        const responseData = (await response.json().catch(() => null)) as {
          error?: string;
        } | null;
        setErrorMessage(responseData?.error ?? 'Authentication failed.');
        return;
      }

      router.replace(redirectTarget);
      router.refresh();
    } catch {
      setErrorMessage('Unexpected error. Please try again.');
    } finally {
      setIsSubmitting(false);
    }
  }

  return (
    <div className="mx-auto w-full max-w-sm space-y-8">
      <div className="space-y-2">
        <h1 className="text-3xl font-semibold tracking-tight">Login</h1>
        <p className="text-muted-foreground text-sm">Sign in to access your workspace.</p>
      </div>
      <form className="space-y-6" onSubmit={handleSubmit}>
        {isRegistered ? (
          <p className="text-sm font-medium text-green-600 dark:text-green-400">
            Account created successfully. You can sign in now.
          </p>
        ) : null}
        <div className="space-y-2">
          <Label htmlFor="username" className="text-sm font-medium">
            Username
          </Label>
          <Input
            id="username"
            name="username"
            autoComplete="username"
            required
            value={formData.username}
            onChange={handleUsernameChange}
          />
        </div>
        <div className="space-y-2">
          <Label htmlFor="password" className="text-sm font-medium">
            Password
          </Label>
          <Input
            id="password"
            name="password"
            type="password"
            autoComplete="current-password"
            required
            value={formData.password}
            onChange={handlePasswordChange}
          />
        </div>
        {errorMessage ? (
          <p className="text-destructive text-sm font-medium">{errorMessage}</p>
        ) : null}
        <Button className="w-full rounded-none" type="submit" disabled={isSubmitting} size="lg">
          {isSubmitting ? 'Signing in...' : 'Sign in'}
        </Button>
        <p className="text-muted-foreground mt-4 text-center text-sm">
          {"Don't have an account? "}
          <Link className="text-primary font-medium hover:underline" href="/signup">
            Create one
          </Link>
        </p>
      </form>
    </div>
  );
}
