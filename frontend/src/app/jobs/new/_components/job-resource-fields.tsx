import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';

type JobResourceFieldsProps = {
  reqCpuCores: string;
  reqRamGb: string;
  reqGpuCount: string;
  fieldErrors: Record<string, string[] | undefined>;
  onChange: (name: 'reqCpuCores' | 'reqRamGb' | 'reqGpuCount', value: string) => void;
};

export function JobResourceFields({
  reqCpuCores,
  reqRamGb,
  reqGpuCount,
  fieldErrors,
  onChange,
}: JobResourceFieldsProps) {
  return (
    <div className="grid gap-8 sm:grid-cols-3">
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
      <div className="space-y-2">
        <Label htmlFor="reqGpuCount" className="text-sm font-medium">
          GPU Count
        </Label>
        <Input
          id="reqGpuCount"
          name="reqGpuCount"
          type="number"
          min="0"
          required
          value={reqGpuCount}
          onChange={(event) => onChange('reqGpuCount', event.target.value)}
        />
        {fieldErrors.reqGpuCount?.map((message) => (
          <p key={message} className="text-destructive text-sm font-medium">
            {message}
          </p>
        ))}
      </div>
    </div>
  );
}
