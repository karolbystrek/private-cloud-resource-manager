import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';

type JobResourceFieldsProps = {
  reqCpuCores: string;
  reqRamGb: string;
  reqGpu: boolean;
  gpuAvailable: boolean;
  fieldErrors: Record<string, string[] | undefined>;
  onChange: (name: 'reqCpuCores' | 'reqRamGb' | 'reqGpu', value: string | boolean) => void;
};

export function JobResourceFields({
  reqCpuCores,
  reqRamGb,
  reqGpu,
  gpuAvailable,
  fieldErrors,
  onChange,
}: JobResourceFieldsProps) {
  return (
    <div className="space-y-8">
      <div className="space-y-3">
        <Label className="text-sm font-semibold tracking-wide uppercase text-muted-foreground">
          Compute Acceleration
        </Label>
        <div className="grid gap-4 sm:grid-cols-2">
          <button
            type="button"
            onClick={() => onChange('reqGpu', false)}
            className={`flex flex-col text-left p-4 border transition-all duration-300 relative overflow-hidden group ${
              !reqGpu
                ? 'border-primary bg-primary/5 ring-1 ring-primary/30'
                : 'border-border hover:border-muted-foreground/30 bg-card hover:bg-muted/10'
            }`}
          >
            <span className="font-semibold text-sm tracking-tight">Standard CPU Mode</span>
            <span className="text-muted-foreground text-xs mt-1.5 leading-relaxed">
              Executes the container workload using standard CPU cores. Recommended for general execution.
            </span>
            <span className="absolute bottom-0 right-0 w-16 h-16 bg-primary/5 rounded-full translate-x-8 translate-y-8 group-hover:scale-110 transition-transform duration-300" />
          </button>

          <button
            type="button"
            disabled={!gpuAvailable}
            onClick={() => onChange('reqGpu', true)}
            className={`flex flex-col text-left p-4 border transition-all duration-300 relative overflow-hidden group ${
              !gpuAvailable
                ? 'opacity-40 cursor-not-allowed border-border bg-muted/20'
                : reqGpu
                ? 'border-primary bg-primary/5 ring-1 ring-primary/30'
                : 'border-border hover:border-muted-foreground/30 bg-card hover:bg-muted/10'
            }`}
          >
            <span className="font-semibold text-sm tracking-tight flex items-center justify-between">
              NVIDIA GPU Mode
              {gpuAvailable && (
                <span className="bg-emerald-500/10 text-emerald-500 text-[10px] font-mono font-bold uppercase tracking-wider px-2 py-0.5 border border-emerald-500/20 rounded-sm animate-pulse">
                  Detected
                </span>
              )}
            </span>
            <span className="text-muted-foreground text-xs mt-1.5 leading-relaxed">
              {!gpuAvailable
                ? 'NVIDIA GeForce accelerator is not available on active cluster nodes.'
                : 'Accelerate container using dedicated GPU capabilities. Ideal for parallel processing, AI, or ML.'}
            </span>
            {gpuAvailable && (
              <span className="absolute bottom-0 right-0 w-16 h-16 bg-emerald-500/5 rounded-full translate-x-8 translate-y-8 group-hover:scale-110 transition-transform duration-300" />
            )}
          </button>
        </div>
      </div>

      <div className="grid gap-8 sm:grid-cols-2">
        <div className="space-y-2">
          <Label htmlFor="reqCpuCores" className="text-sm font-medium">
            CPU Cores
          </Label>
          <Input
            id="reqCpuCores"
            name="reqCpuCores"
            type="number"
            min="1"
            required
            value={reqCpuCores}
            onChange={(event) => onChange('reqCpuCores', event.target.value)}
          />
          {fieldErrors.reqCpuCores?.map((message) => (
            <p key={message} className="text-destructive text-sm font-medium">
              {message}
            </p>
          ))}
        </div>
        <div className="space-y-2">
          <Label htmlFor="reqRamGb" className="text-sm font-medium">
            RAM (GB)
          </Label>
          <Input
            id="reqRamGb"
            name="reqRamGb"
            type="number"
            min="1"
            required
            value={reqRamGb}
            onChange={(event) => onChange('reqRamGb', event.target.value)}
          />
          {fieldErrors.reqRamGb?.map((message) => (
            <p key={message} className="text-destructive text-sm font-medium">
              {message}
            </p>
          ))}
        </div>
      </div>
    </div>
  );
}
