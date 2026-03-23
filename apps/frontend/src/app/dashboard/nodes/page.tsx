export default function NodesPage() {
  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold tracking-tight text-foreground">
          Nodes
        </h1>
        <p className="mt-1 text-sm text-muted">
          Available infrastructure machines in your cluster.
        </p>
      </div>
      <div className="rounded-2xl border border-card-border bg-card p-8 text-center">
        <p className="text-lg font-medium text-muted">
          Node listing coming in FE-02
        </p>
      </div>
    </div>
  );
}
