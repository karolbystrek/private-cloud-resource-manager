'use client';

import { ChangeEvent, FormEvent, useMemo, useState } from 'react';
import Link from 'next/link';
import { useRouter, useSearchParams } from 'next/navigation';

import { Button } from '@/components/ui/button';
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from '@/components/ui/card';
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
    setFormData(previous => ({
      ...previous,
      username: event.target.value,
    }));
  }

  function handlePasswordChange(event: ChangeEvent<HTMLInputElement>) {
    setFormData(previous => ({
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
    <Card className="w-full max-w-sm">
      <CardHeader>
        <CardTitle>Login</CardTitle>
        <CardDescription>Sign in to access your workspace.</CardDescription>
      </CardHeader>
      <CardContent>
        <form className="space-y-4" onSubmit={handleSubmit}>
          {isRegistered ? (
            <p className="text-xs text-green-600 dark:text-green-400">
              Account created successfully. You can sign in now.
            </p>
          ) : null}
          <div className="space-y-2">
            <Label htmlFor="username">Username</Label>
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
            <Label htmlFor="password">Password</Label>
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
            <p className="text-destructive text-xs">{errorMessage}</p>
          ) : null}
          <Button className="w-full" type="submit" disabled={isSubmitting}>
            {isSubmitting ? 'Signing in...' : 'Sign in'}
          </Button>
          <p className="text-muted-foreground text-center text-xs">
            {"Don't have an account? "}
            <Link className="text-primary hover:underline" href="/signup">
              Create one
            </Link>
          </p>
        </form>
      </CardContent>
    </Card>
  );
}
