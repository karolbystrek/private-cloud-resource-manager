'use client';

import Link from 'next/link';
import { LogoutButton } from '@/components/logout-button';
import ThemeToggle from '@/components/theme-toggle';
import { Button } from '@/components/ui/button';

type HeaderProps = {
  hasSession: boolean;
};

export function Header({ hasSession }: HeaderProps) {
  let navigation = null;

  if (hasSession) {
    navigation = (
      <>
        <Button asChild variant="ghost" size="sm">
          <Link href="/jobs">Jobs</Link>
        </Button>
        <Button asChild variant="ghost" size="sm">
          <Link href="/jobs/new">New Job</Link>
        </Button>
      </>
    );
  }

  return (
    <header className="bg-background/95 supports-backdrop-filter:bg-background/60 sticky top-0 z-50 w-full border-b backdrop-blur">
      <div className="flex h-14 w-full items-center px-4">
        <div className="flex flex-1 items-center justify-between space-x-2 md:justify-end">
          <nav className="flex items-center gap-2">
            {navigation}
            <ThemeToggle />
            {hasSession && <LogoutButton />}
          </nav>
        </div>
      </div>
    </header>
  );
}
