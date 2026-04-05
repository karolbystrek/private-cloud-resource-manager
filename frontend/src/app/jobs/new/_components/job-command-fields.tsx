import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';

type JobCommandFieldsProps = {
  dockerImage: string;
  executionCommand: string;
  fieldErrors: Record<string, string[] | undefined>;
  onChange: (name: 'dockerImage' | 'executionCommand', value: string) => void;
};

export function JobCommandFields({
  dockerImage,
  executionCommand,
  fieldErrors,
  onChange,
}: JobCommandFieldsProps) {
  return (
    <div className="space-y-8">
      <div className="space-y-2">
        <Label htmlFor="dockerImage" className="text-sm font-medium">
          Docker Image
        </Label>
        <Input
          id="dockerImage"
          name="dockerImage"
          placeholder="nvidia/cuda:11.8.0-base-ubuntu22.04"
          required
          className="font-mono"
          value={dockerImage}
          onChange={(event) => onChange('dockerImage', event.target.value)}
        />
        {fieldErrors.dockerImage?.map((message) => (
          <p key={message} className="text-destructive text-sm font-medium">
            {message}
          </p>
        ))}
      </div>

      <div className="space-y-2">
        <Label htmlFor="executionCommand" className="text-sm font-medium">
          Execution Command
        </Label>
        <Input
          id="executionCommand"
          name="executionCommand"
          placeholder="python train_model.py --epochs 10"
          required
          className="font-mono"
          value={executionCommand}
          onChange={(event) => onChange('executionCommand', event.target.value)}
        />
        {fieldErrors.executionCommand?.map((message) => (
          <p key={message} className="text-destructive text-sm font-medium">
            {message}
          </p>
        ))}
      </div>
    </div>
  );
}
