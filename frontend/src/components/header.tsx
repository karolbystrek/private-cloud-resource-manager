'use client';

import { Fragment } from 'react';
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

type HeaderProps = {
  hasSession: boolean;
};

function formatSegment(segment: string) {
  return segment
    .split('-')
    .filter(Boolean)
    .map((word) => word[0]?.toUpperCase() + word.slice(1))
    .join(' ');
}

export function Header({ hasSession }: HeaderProps) {
  const pathname = usePathname();

  if (!hasSession) {
    return null;
  }

  const segments = pathname.split('/').filter(Boolean);

  return (
    <header className="bg-card sticky top-0 z-50 border-b">
      <div className="flex h-14 w-full items-center justify-between gap-6 px-4 sm:px-6">
        <div className="flex items-center gap-4">
          <Breadcrumb className="hidden sm:block">
            <BreadcrumbList>
              <BreadcrumbItem>
                <BreadcrumbLink href="/">Home</BreadcrumbLink>
              </BreadcrumbItem>
              {segments.map((segment, index) => {
                const href = `/${segments.slice(0, index + 1).join('/')}`;
                const isLast = index === segments.length - 1;
                const label = formatSegment(segment);

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
        <div className="flex items-center gap-1.5">
          <ThemeToggle />
          <LogoutButton />
        </div>
      </div>
    </header>
  );
}
