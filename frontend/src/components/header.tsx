'use client';

import {
  RiAddCircleLine,
  RiAdminLine,
  RiDashboardLine,
  RiHistoryLine,
  RiServerLine,
} from '@remixicon/react';
import Link from 'next/link';
import { usePathname } from 'next/navigation';
import { LogoutButton } from '@/components/logout-button';
import { Button } from '@/components/ui/button';
import type { UserRole } from '@/lib/user-role';

type HeaderProps = {
  hasSession: boolean;
  userRole: UserRole | null;
};

export function Header({ hasSession, userRole }: HeaderProps) {
  const pathname = usePathname();

  if (!hasSession) {
    return null;
  }

  const isDashboardRoute = pathname === '/';
  const isAdminRoute = pathname === '/admin' || pathname.startsWith('/admin/');
  const isJobHistoryRoute = pathname === '/jobs';
  const isNewJobRoute = pathname === '/jobs/new';
  const isNodesRoute = pathname === '/nodes' || pathname.startsWith('/nodes/');
  const showNodes = userRole === 'ADMIN';
  const showAdmin = userRole === 'ADMIN';

  return (
    <header className="bg-card sticky top-0 z-50 border-b">
      <div className="flex h-14 w-full items-center gap-3 px-4 sm:px-6">
        <Link href="/" className="font-heading shrink-0 text-sm font-semibold tracking-wide">
          Private Cloud Resource Manager
        </Link>
        <nav className="ml-auto flex min-w-0 items-center justify-end gap-1.5 overflow-x-auto py-1">
          <Button asChild variant={isDashboardRoute ? 'default' : 'outline'} size="default">
            <Link href="/">
              <RiDashboardLine size={14} />
              Dashboard
            </Link>
          </Button>
          {showAdmin ? (
            <Button asChild variant={isAdminRoute ? 'default' : 'outline'} size="default">
              <Link href="/admin">
                <RiAdminLine size={14} />
                Admin
              </Link>
            </Button>
          ) : null}
          {showNodes ? (
            <Button asChild variant={isNodesRoute ? 'default' : 'outline'} size="default">
              <Link href="/nodes">
                <RiServerLine size={14} />
                All Nodes
              </Link>
            </Button>
          ) : null}
          <Button asChild variant={isJobHistoryRoute ? 'default' : 'outline'} size="default">
            <Link href="/jobs">
              <RiHistoryLine size={14} />
              Job History
            </Link>
          </Button>
          <Button asChild variant={isNewJobRoute ? 'default' : 'outline'} size="default">
            <Link href="/jobs/new">
              <RiAddCircleLine size={14} />
              New Job
            </Link>
          </Button>
        </nav>
        <div className="flex shrink-0 items-center gap-1.5">
          <LogoutButton />
        </div>
      </div>
    </header>
  );
}
