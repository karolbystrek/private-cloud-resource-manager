export default async function JobDetailPage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = await params;

  return (
    <div className="flex flex-1 items-center justify-center p-6 font-mono">
      <div className="max-w-xl space-y-4 text-center">
        <h1 className="text-3xl font-bold tracking-tight">Job Details</h1>
        <p className="text-muted-foreground">
          Job ID:
          {' '}
          <span className="text-primary font-mono">{id}</span>
          .
        </p>
        <p className="bg-muted/30 border p-4 text-sm">
          This page is a placeholder.
          {' '}
          Detailed job information for `/jobs/&lt;job-id&gt;` will be added in a follow-up task.
        </p>
      </div>
    </div>
  );
}
