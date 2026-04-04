'use client';

import { ChangeEvent, FormEvent, useState } from 'react';
import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';

type SignupPayload = {
  username: string;
  email: string;
  password: string;
};

export function SignupForm() {
  const router = useRouter();
  const [formData, setFormData] = useState<SignupPayload>({
    username: '',
    email: '',
    password: '',
  });
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [errorMessage, setErrorMessage] = useState('');

  function handleUsernameChange(event: ChangeEvent<HTMLInputElement>) {
    setFormData((previous) => ({
      ...previous,
      username: event.target.value,
    }));
  }

  function handleEmailChange(event: ChangeEvent<HTMLInputElement>) {
    setFormData((previous) => ({
      ...previous,
      email: event.target.value,
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
      const response = await fetch('/api/auth/register', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify(formData),
      });

      if (!response.ok) {
        setErrorMessage('Registration failed. Please try again.');
        return;
      }

      router.replace('/login?registered=1');
      router.refresh();
    } catch {
      setErrorMessage('Registration failed. Please try again.');
    } finally {
      setIsSubmitting(false);
    }
  }

  return (
    <div className="mx-auto w-full max-w-sm space-y-8">
      <div className="space-y-2">
        <h1 className="text-3xl font-semibold tracking-tight">Sign up</h1>
        <p className="text-muted-foreground text-sm">Create your account to continue.</p>
      </div>
      <form className="space-y-6" onSubmit={handleSubmit}>
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
          <Label htmlFor="email" className="text-sm font-medium">
            Email
          </Label>
          <Input
            id="email"
            name="email"
            type="email"
            autoComplete="email"
            required
            value={formData.email}
            onChange={handleEmailChange}
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
            autoComplete="new-password"
            required
            value={formData.password}
            onChange={handlePasswordChange}
          />
        </div>
        {errorMessage ? (
          <p className="text-destructive text-sm font-medium">{errorMessage}</p>
        ) : null}
        <Button className="w-full rounded-none" type="submit" disabled={isSubmitting} size="lg">
          {isSubmitting ? 'Creating account...' : 'Create account'}
        </Button>
        <p className="text-muted-foreground mt-4 text-center text-sm">
          {'Already have an account? '}
          <Link className="text-primary font-medium hover:underline" href="/login">
            Sign in
          </Link>
        </p>
      </form>
    </div>
  );
}
