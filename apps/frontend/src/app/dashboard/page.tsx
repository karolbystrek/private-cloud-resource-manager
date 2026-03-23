"use client";

import { useAuth } from "@/context/AuthContext";

export default function DashboardPage() {
  const { user } = useAuth();

  return (
    <div className="space-y-8">
      {/* Page header */}
      <div>
        <h1 className="text-2xl font-bold tracking-tight text-foreground">
          Dashboard
        </h1>
        <p className="mt-1 text-sm text-muted">
          Welcome back, {user?.username}. Here&apos;s your cloud resource overview.
        </p>
      </div>

      {/* Stats cards */}
      <div className="grid gap-6 sm:grid-cols-2 lg:grid-cols-3">
        <StatCard
          icon="💰"
          label="CU Balance"
          value="—"
          subtitle="Compute Units available"
        />
        <StatCard
          icon="🖥️"
          label="Active Nodes"
          value="—"
          subtitle="Infrastructure machines"
        />
        <StatCard
          icon="🚀"
          label="Running Jobs"
          value="—"
          subtitle="Active workloads"
        />
      </div>

      {/* Placeholder content */}
      <div className="rounded-2xl border border-card-border bg-card p-8 text-center">
        <p className="text-lg font-medium text-muted">
          Dashboard widgets coming in FE-02
        </p>
        <p className="mt-2 text-sm text-muted">
          Wallet balance, node list, and job status will be connected to the
          Broker API.
        </p>
      </div>
    </div>
  );
}

function StatCard({
  icon,
  label,
  value,
  subtitle,
}: {
  icon: string;
  label: string;
  value: string;
  subtitle: string;
}) {
  return (
    <div className="rounded-2xl border border-card-border bg-card p-6 transition-colors hover:border-primary/30">
      <div className="flex items-center gap-3">
        <span className="text-2xl">{icon}</span>
        <p className="text-sm font-medium text-muted">{label}</p>
      </div>
      <p className="mt-3 text-3xl font-bold tracking-tight text-foreground">
        {value}
      </p>
      <p className="mt-1 text-xs text-muted">{subtitle}</p>
    </div>
  );
}
