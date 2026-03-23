"use client";

import { useAuth } from "@/context/AuthContext";
import { useRouter } from "next/navigation";
import { useState, useEffect, type FormEvent } from "react";

export default function LoginPage() {
  const { login, user, isLoading } = useAuth();
  const router = useRouter();
  const [username, setUsername] = useState("");
  const [error, setError] = useState("");

  // If already logged in, redirect to dashboard
  useEffect(() => {
    if (!isLoading && user) {
      router.replace("/dashboard");
    }
  }, [user, isLoading, router]);

  const handleSubmit = (e: FormEvent) => {
    e.preventDefault();
    setError("");

    const trimmed = username.trim();
    if (!trimmed) {
      setError("Please enter a username.");
      return;
    }
    if (trimmed.length < 2) {
      setError("Username must be at least 2 characters.");
      return;
    }

    login(trimmed);
    router.push("/dashboard");
  };

  if (isLoading) {
    return (
      <div className="flex min-h-screen items-center justify-center bg-background">
        <div className="h-8 w-8 animate-spin rounded-full border-2 border-muted border-t-primary" />
      </div>
    );
  }

  return (
    <div className="flex min-h-screen items-center justify-center bg-background px-4">
      {/* Glow effect */}
      <div className="pointer-events-none absolute inset-0 overflow-hidden">
        <div className="absolute left-1/2 top-1/3 h-[500px] w-[500px] -translate-x-1/2 -translate-y-1/2 rounded-full bg-primary/5 blur-[120px]" />
      </div>

      <div className="relative w-full max-w-md">
        {/* Header */}
        <div className="mb-8 text-center">
          <div className="mx-auto mb-4 flex h-14 w-14 items-center justify-center rounded-2xl bg-primary/10 text-2xl font-bold text-primary">
            P
          </div>
          <h1 className="text-2xl font-bold tracking-tight text-foreground">
            Private Cloud Resource Manager
          </h1>
          <p className="mt-2 text-sm text-muted">
            Sign in to manage your compute resources
          </p>
        </div>

        {/* Card */}
        <div className="rounded-2xl border border-card-border bg-card p-8 shadow-xl shadow-black/20">
          <form onSubmit={handleSubmit} className="space-y-5">
            <div>
              <label
                htmlFor="username"
                className="mb-2 block text-sm font-medium text-foreground"
              >
                Username
              </label>
              <input
                id="username"
                type="text"
                value={username}
                onChange={(e) => setUsername(e.target.value)}
                placeholder="e.g. student, admin, researcher"
                autoComplete="username"
                autoFocus
                className="w-full rounded-lg border border-input-border bg-input-bg px-4 py-3 text-sm text-foreground placeholder-muted outline-none transition-colors focus:border-input-focus focus:ring-2 focus:ring-primary-glow"
              />
              {error && (
                <p className="mt-2 text-sm text-danger">{error}</p>
              )}
            </div>

            <button
              type="submit"
              className="w-full rounded-lg bg-primary px-4 py-3 text-sm font-semibold text-white transition-colors hover:bg-primary-hover focus:outline-none focus:ring-2 focus:ring-primary-glow"
            >
              Sign in
            </button>
          </form>

          <div className="mt-6 rounded-lg border border-card-border bg-background/50 p-4">
            <p className="mb-2 text-xs font-medium uppercase tracking-wider text-muted">
              Mock accounts
            </p>
            <div className="space-y-1 text-sm text-muted">
              <p>
                <span className="font-mono text-accent">admin</span> — full
                access
              </p>
              <p>
                <span className="font-mono text-accent">student</span> — basic
                user
              </p>
              <p>
                <span className="font-mono text-accent">researcher</span> —
                research user
              </p>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
