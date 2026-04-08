'use client';

import {
  RiAddCircleLine,
  RiAdminLine,
  RiArrowDownSLine,
  RiDashboardLine,
  RiHistoryLine,
  RiPlayList2Line,
  RiServerLine,
} from '@remixicon/react';
import { Fragment } from 'react';
import Link from 'next/link';
import { usePathname } from 'next/navigation';
import { LogoutButton } from '@/components/logout-button';
import ThemeToggle from '@/components/theme-toggle';
import {
  Breadcrumb,
  BreadcrumbItem,
  BreadcrumbLink,
  BreadcrumbList,
  BreadcrumbPage,
  BreadcrumbSeparator,
} from '@/components/ui/breadcrumb';
import { Button } from '@/components/ui/button';
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu';
import type { UserRole } from '@/lib/user-role';

type HeaderProps = {
  hasSession: boolean;
  userRole: UserRole | null;
};

function formatSegment(segment: string) {
  return segment
    .split('-')
    .filter(Boolean)
    .map((word) => word[0]?.toUpperCase() + word.slice(1))
    .join(' ');
}

export function Header({ hasSession, userRole }: HeaderProps) {
  const pathname = usePathname();

  if (!hasSession) {
    return null;
  }

  const segments = pathname.split('/').filter(Boolean);
  const isDashboardRoute = pathname === '/';
  const isAdminRoute = pathname === '/admin' || pathname.startsWith('/admin/');
  const isJobsRoute = pathname === '/jobs' || pathname.startsWith('/jobs/');
  const isNodesRoute = pathname === '/nodes' || pathname.startsWith('/nodes/');
  const showNodes = userRole === 'ADMIN';
  const showAdmin = userRole === 'ADMIN';

  return (
    <header className="bg-card sticky top-0 z-50 border-b">
      <div className="flex h-14 w-full items-center justify-between gap-3 px-4 sm:px-6">
        <div className="flex min-w-0 flex-1 items-center gap-3">
          <Link href="/" className="font-heading shrink-0 text-sm font-semibold tracking-wide">
            Private Cloud Resource Manager
          </Link>
          <nav className="flex min-w-0 items-center gap-1.5 overflow-x-auto py-1">
            <Button asChild variant={isDashboardRoute ? 'default' : 'outline'} size="sm">
              <Link href="/">
                <RiDashboardLine size={14} />
                Dashboard
              </Link>
            </Button>
            {showAdmin ? (
              <Button asChild variant={isAdminRoute ? 'default' : 'outline'} size="sm">
                <Link href="/admin">
                  <RiAdminLine size={14} />
                  Admin
                </Link>
              </Button>
            ) : null}
            <DropdownMenu>
              <DropdownMenuTrigger asChild>
                <Button variant={isJobsRoute ? 'default' : 'outline'} size="sm">
                  <RiPlayList2Line size={14} />
                  Jobs
                  <RiArrowDownSLine size={14} />
                </Button>
              </DropdownMenuTrigger>
              <DropdownMenuContent align="start" className="min-w-[11rem]">
                <DropdownMenuItem asChild>
                  <Link href="/jobs">
                    <RiHistoryLine size={14} />
                    Job History
                  </Link>
                </DropdownMenuItem>
                <DropdownMenuItem asChild>
                  <Link href="/jobs/new">
                    <RiAddCircleLine size={14} />
                    New Job
                  </Link>
                </DropdownMenuItem>
              </DropdownMenuContent>
            </DropdownMenu>
            {showNodes ? (
              <DropdownMenu>
                <DropdownMenuTrigger asChild>
                  <Button variant={isNodesRoute ? 'default' : 'outline'} size="sm">
                    <RiServerLine size={14} />
                    Nodes
                    <RiArrowDownSLine size={14} />
                  </Button>
                </DropdownMenuTrigger>
                <DropdownMenuContent align="start" className="min-w-[11rem]">
                  <DropdownMenuItem asChild>
                    <Link href="/nodes">
                      <RiServerLine size={14} />
                      All Nodes
                    </Link>
                  </DropdownMenuItem>
                </DropdownMenuContent>
              </DropdownMenu>
            ) : null}
          </nav>
        </div>
        <div className="flex shrink-0 items-center gap-1.5">
          <ThemeToggle />
          <LogoutButton />
        </div>
      </div>
      <div className="bg-muted/40 border-t">
        <div className="flex h-8 w-full items-center px-4 sm:px-6">
          <Breadcrumb>
            <BreadcrumbList>
              {segments.length === 0 ? (
                <BreadcrumbItem>
                  <BreadcrumbPage>Dashboard</BreadcrumbPage>
                </BreadcrumbItem>
              ) : (
                <BreadcrumbItem>
                  <BreadcrumbLink href="/">Dashboard</BreadcrumbLink>
                </BreadcrumbItem>
              )}
              {segments.map((segment, index) => {
                const href = `/${segments.slice(0, index + 1).join('/')}`;
                const isLast = index === segments.length - 1;
                const label = formatSegment(decodeURIComponent(segment));

                return (
                  <Fragment key={href}>
                    <BreadcrumbSeparator />
                    <BreadcrumbItem>
                      {isLast ? (
                        <BreadcrumbPage>{label}</BreadcrumbPage>
                      ) : (
                        <BreadcrumbLink href={href}>{label}</BreadcrumbLink>
                      )}
                    </BreadcrumbItem>
                  </Fragment>
                );
              })}
            </BreadcrumbList>
          </Breadcrumb>
        </div>
      </div>
    </header>
  );
}
