import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import type { GpuOption } from '@/app/jobs/_components/types';

type JobResourceFieldsProps = {
  reqCpuCores: string;
  reqRamGb: string;
  gpuEnabled: boolean;
  gpuOptions: GpuOption[];
  selectedGpuOptionKey: string;
  gpuCount: string;
  gpuMinMemoryGb: string;
  gpuModel: string;
  fieldErrors: Record<string, string[] | undefined>;
  onChange: (
    name: 'reqCpuCores' | 'reqRamGb' | 'gpuEnabled' | 'gpuCount' | 'gpuMinMemoryGb' | 'gpuModel',
    value: string | boolean,
  ) => void;
  onGpuOptionChange: (optionKey: string) => void;
};

export function JobResourceFields({
  reqCpuCores,
  reqRamGb,
  gpuEnabled,
  gpuOptions,
  selectedGpuOptionKey,
  gpuCount,
  gpuMinMemoryGb,
  gpuModel,
  fieldErrors,
  onChange,
  onGpuOptionChange,
}: JobResourceFieldsProps) {
  const selectedOption = gpuOptions.find((option) => gpuOptionKey(option) === selectedGpuOptionKey);

  return (
    <div className="space-y-6">
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

      <div className="space-y-4 border-t pt-6">
        <label className="flex items-center gap-3 text-sm font-medium" htmlFor="gpuEnabled">
          <input
            id="gpuEnabled"
            name="gpuEnabled"
            type="checkbox"
            className="border-input h-4 w-4"
            checked={gpuEnabled}
            onChange={(event) => onChange('gpuEnabled', event.target.checked)}
          />
          Require NVIDIA GPU
        </label>
        {fieldErrors.gpuRequirement?.map((message) => (
          <p key={message} className="text-destructive text-sm font-medium">
            {message}
          </p>
        ))}
        {fieldErrors['gpuRequirement.vendor']?.map((message) => (
          <p key={message} className="text-destructive text-sm font-medium">
            {message}
          </p>
        ))}

        {gpuEnabled ? (
          <div className="grid gap-4 sm:grid-cols-3">
            <div className="space-y-2 sm:col-span-3">
              <Label htmlFor="gpuModel" className="text-sm font-medium">
                GPU Model
              </Label>
              <select
                id="gpuModel"
                name="gpuModel"
                required
                className="border-input bg-background h-9 w-full rounded-none border px-3 text-sm"
                value={selectedGpuOptionKey}
                onChange={(event) => onGpuOptionChange(event.target.value)}
              >
                <option value="">Select a GPU from cluster inventory</option>
                {gpuOptions.map((option) => (
                  <option key={gpuOptionKey(option)} value={gpuOptionKey(option)}>
                    {option.nodeHostname}
                    {' / '}
                    {option.model}
                    {' / '}
                    {option.count}
                    {' available'}
                    {option.maxMemoryGb ? ` / ${option.maxMemoryGb} GB VRAM` : ''}
                  </option>
                ))}
              </select>
              {gpuOptions.length === 0 ? (
                <p className="text-muted-foreground text-sm">
                  No eligible NVIDIA GPUs are currently available.
                </p>
              ) : null}
              {fieldErrors['gpuRequirement.model']?.map((message) => (
                <p key={message} className="text-destructive text-sm font-medium">
                  {message}
                </p>
              ))}
            </div>
            <div className="space-y-2">
              <Label htmlFor="gpuCount" className="text-sm font-medium">
                GPU Count
              </Label>
              <Input
                id="gpuCount"
                name="gpuCount"
                type="number"
                min="1"
                max={selectedOption?.count}
                required
                value={gpuCount}
                onChange={(event) => onChange('gpuCount', event.target.value)}
              />
              {fieldErrors['gpuRequirement.count']?.map((message) => (
                <p key={message} className="text-destructive text-sm font-medium">
                  {message}
                </p>
              ))}
            </div>
            <div className="space-y-2">
              <Label htmlFor="gpuMinMemoryGb" className="text-sm font-medium">
                Min VRAM (GB)
              </Label>
              <Input
                id="gpuMinMemoryGb"
                name="gpuMinMemoryGb"
                type="number"
                min="1"
                value={gpuMinMemoryGb}
                onChange={(event) => onChange('gpuMinMemoryGb', event.target.value)}
              />
              {fieldErrors['gpuRequirement.minMemoryGb']?.map((message) => (
                <p key={message} className="text-destructive text-sm font-medium">
                  {message}
                </p>
              ))}
            </div>
            <div className="space-y-2">
              <Label htmlFor="gpuModel" className="text-sm font-medium">
                Selected Model
              </Label>
              <Input
                id="gpuModelSelected"
                name="gpuModelSelected"
                maxLength={120}
                value={gpuModel}
                readOnly
              />
            </div>
          </div>
        ) : null}
      </div>
    </div>
  );
}

function gpuOptionKey(option: GpuOption): string {
  return `${option.nodeId}:${option.model}`;
}
