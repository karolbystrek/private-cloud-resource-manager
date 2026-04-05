import { RiAddLine, RiDeleteBinLine } from '@remixicon/react';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import type { EnvVarRow } from './env-vars';

type EnvVarsEditorProps = {
  rows: EnvVarRow[];
  onAdd: () => void;
  onRemove: (id: string) => void;
  onChange: (id: string, field: 'key' | 'value', value: string) => void;
  errorMessages: string[];
};

export function EnvVarsEditor({
  rows,
  onAdd,
  onRemove,
  onChange,
  errorMessages,
}: EnvVarsEditorProps) {
  return (
    <div className="space-y-3">
      <div className="flex items-center justify-between">
        <Label className="text-sm font-medium">Environment Variables</Label>
        <Button
          type="button"
          variant="ghost"
          size="icon"
          aria-label="Add environment variable"
          onClick={onAdd}
        >
          <RiAddLine />
        </Button>
      </div>

      {rows.map((row) => (
        <div key={row.id} className="grid items-center gap-2 sm:grid-cols-[1fr_1fr_auto]">
          <Input
            placeholder="KEY"
            className="font-mono"
            value={row.key}
            onChange={(event) => onChange(row.id, 'key', event.target.value)}
          />
          <Input
            placeholder="value"
            className="font-mono"
            value={row.value}
            onChange={(event) => onChange(row.id, 'value', event.target.value)}
          />
          <Button
            type="button"
            variant="destructive"
            size="icon"
            aria-label="Remove environment variable"
            onClick={() => onRemove(row.id)}
          >
            <RiDeleteBinLine />
          </Button>
        </div>
      ))}

      {errorMessages.map((message) => (
        <p key={message} className="text-destructive text-sm font-medium">
          {message}
        </p>
      ))}
    </div>
  );
}
