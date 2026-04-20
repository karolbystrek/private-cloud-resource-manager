import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Skeleton } from '@/components/ui/skeleton';
import { RiFlashlightLine } from '@remixicon/react';

function StatCardSkeleton() {
  return (
    <Card>
      <CardHeader className="pb-2">
        <CardTitle>
          <Skeleton className="h-4 w-28" />
        </CardTitle>
      </CardHeader>
      <CardContent className="flex items-center justify-between">
        <Skeleton className="h-8 w-14" />
        <Skeleton className="h-5 w-5" />
      </CardContent>
    </Card>
  );
}

export function HomeDashboardSkeleton() {
  return (
    <div className="bg-background/50 min-h-[calc(100vh-5.5rem)] w-full py-8">
      <div className="container mx-auto max-w-6xl space-y-6 px-4 md:px-6">
        <div>
          <h1 className="text-3xl font-semibold tracking-tight">Dashboard</h1>
          <p className="text-muted-foreground text-sm">Overview of your recent workload and shortcuts.</p>
        </div>

        <section className="grid gap-3 sm:grid-cols-2 xl:grid-cols-5">
          <StatCardSkeleton />
          <StatCardSkeleton />
          <StatCardSkeleton />
          <StatCardSkeleton />
        </section>

        <section className="grid gap-4 lg:grid-cols-3">
          <Card className="lg:col-span-2">
            <CardHeader >
              <CardTitle>Recent Jobs</CardTitle>
              <CardDescription>Latest submitted jobs with status and resources.</CardDescription>
            </CardHeader>
            <CardContent className="space-y-3">
              <Skeleton className="h-16 w-full" />
              <Skeleton className="h-16 w-full" />
              <Skeleton className="h-16 w-full" />
              <Skeleton className="h-16 w-full" />
              <Skeleton className="h-16 w-full" />
            </CardContent>
          </Card>

          <Card className="h-full">
          <CardHeader>
              <CardTitle className="inline-flex items-center gap-2">
                <RiFlashlightLine size={16} />
                Quota
              </CardTitle>
              <CardDescription>Monthly prepaid runtime budget.</CardDescription>
            </CardHeader>
            <CardContent className="space-y-3">
              <div className="rounded-none border bg-muted/40 p-3 space-y-2">
                <Skeleton className="h-3 w-40" />
                <Skeleton className="h-8 w-32" />
                <Skeleton className="h-3 w-56 max-w-full" />
              </div>

              <div className="space-y-2">
                <div className="flex items-center justify-between">
                  <Skeleton className="h-3 w-16" />
                  <Skeleton className="h-3 w-36" />
                </div>
                <Skeleton className="h-2.5 w-full" />
              </div>

              <Skeleton className="h-8 w-full" />
              <Skeleton className="h-8 w-full" />
              <Skeleton className="h-9 w-full" />
              <Skeleton className="h-9 w-full" />
            </CardContent>
          </Card>
        </section>
      </div>
    </div>
  );
}
