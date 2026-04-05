export default async function JobDetailPage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = await params;

  return (
    <div className="flex flex-1 items-center justify-center p-6 font-mono">
      <div className="max-w-xl space-y-4 text-center">
        <h1 className="text-3xl font-bold tracking-tight">Job Started</h1>
        <p className="text-muted-foreground">
          Successfully captured job ID: <span className="text-primary font-mono">{id}</span>.
        </p>
        <p className="bg-muted/30 border p-4 text-sm">
          This is a placeholder page since the detailed job implementation is out-of-scope for the
          current submission requirement.
        </p>
      </div>
    </div>
  );
}
