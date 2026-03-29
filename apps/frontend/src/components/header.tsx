'use client';

import { LogoutButton } from '@/components/logout-button';
import ThemeToggle from '@/components/theme-toggle';

type HeaderProps = {
  hasSession: boolean;
};

export function Header({ hasSession }: HeaderProps) {
  return (
    <header className="bg-background/95 supports-backdrop-filter:bg-background/60 sticky top-0 z-50 w-full border-b backdrop-blur">
      <div className="flex h-14 w-full items-center px-4">
        <div className="flex flex-1 items-center justify-between space-x-2 md:justify-end">
          <nav className="flex items-center gap-2">
            <ThemeToggle />
            {hasSession ? <LogoutButton /> : null}
          </nav>
        </div>
      </div>
    </header>
  );
}
