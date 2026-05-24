'use client';

import { useState } from 'react';
import { RiAddLine, RiArrowLeftLine, RiDeleteBinLine, RiSave3Line } from '@remixicon/react';
import Link from 'next/link';
import type { GpuWeightTier, ResourceWeightPolicy } from './page';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { formatLocalDateTime } from '@/lib/date-time';

type ResourceWeightsFormProps = {
  initialPolicy: ResourceWeightPolicy | null;
};

const fallbackPolicy: ResourceWeightPolicy = {
  cpuCoreWeight: 1,
  ramGbPerUnit: 4,
  ramUnitWeight: 1,
  gpuWeightTiers: [
    { minMemoryGb: 0, weight: 16 },
    { minMemoryGb: 16, weight: 24 },
    { minMemoryGb: 24, weight: 32 },
    { minMemoryGb: 40, weight: 48 },
  ],
  updatedAt: new Date().toISOString(),
};

function sortedTiers(tiers: GpuWeightTier[]): GpuWeightTier[] {
  return [...tiers].sort((a, b) => a.minMemoryGb - b.minMemoryGb);
}

function toPositiveNumber(value: string): number {
  return Math.max(1, Number.parseInt(value, 10) || 1);
}

function toNonNegativeNumber(value: string): number {
  return Math.max(0, Number.parseInt(value, 10) || 0);
}

function nextThreshold(tiers: GpuWeightTier[]): number {
  return Math.max(...tiers.map((tier) => tier.minMemoryGb), 0) + 8;
}

export function ResourceWeightsForm({ initialPolicy }: ResourceWeightsFormProps) {
  const [policy, setPolicy] = useState<ResourceWeightPolicy>(initialPolicy ?? fallbackPolicy);
  const [errorMessage, setErrorMessage] = useState(initialPolicy ? '' : 'Resource weights are currently unavailable.');
  const [successMessage, setSuccessMessage] = useState('');
  const [isSaving, setIsSaving] = useState(false);

  function updateTier(index: number, patch: Partial<GpuWeightTier>) {
    setPolicy((current) => ({
      ...current,
      gpuWeightTiers: current.gpuWeightTiers.map((tier, tierIndex) => (
        tierIndex === index ? { ...tier, ...patch } : tier
      )),
    }));
  }

  function addTier() {
    setPolicy((current) => ({
      ...current,
      gpuWeightTiers: sortedTiers([
        ...current.gpuWeightTiers,
        { minMemoryGb: nextThreshold(current.gpuWeightTiers), weight: 16 },
      ]),
    }));
  }

  function removeTier(index: number) {
    setPolicy((current) => ({
      ...current,
      gpuWeightTiers: current.gpuWeightTiers.filter((_, tierIndex) => tierIndex !== index),
    }));
  }

  async function savePolicy() {
    setIsSaving(true);
    setErrorMessage('');
    setSuccessMessage('');

    const payload = {
      ...policy,
      gpuWeightTiers: sortedTiers(policy.gpuWeightTiers),
    };

    try {
      const response = await fetch('/api/admin/resource-weights', {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload),
      });
      const body = await response.json().catch(() => null);

      if (!response.ok) {
        setErrorMessage(body?.error ?? 'Failed to update resource weights.');
        return;
      }

      setPolicy(body as ResourceWeightPolicy);
      setSuccessMessage('Resource weights saved.');
    } catch {
      setErrorMessage('Unexpected error. Please try again.');
    } finally {
      setIsSaving(false);
    }
  }

  return (
    <section className="space-y-6">
      <div className="flex flex-col gap-4 md:flex-row md:items-center md:justify-between">
        <div className="space-y-1">
          <Link href="/admin" className="text-muted-foreground hover:text-foreground inline-flex items-center gap-1 text-sm">
            <RiArrowLeftLine aria-hidden="true" size={16} />
            Back to admin
          </Link>
          <h1 className="text-3xl font-semibold tracking-tight">Resource Weights</h1>
        </div>
        <Button onClick={savePolicy} disabled={isSaving}>
          <RiSave3Line aria-hidden="true" size={16} />
          {isSaving ? 'Saving...' : 'Save'}
        </Button>
      </div>

      {errorMessage ? <p className="text-destructive text-sm">{errorMessage}</p> : null}
      {successMessage ? <p className="text-sm text-emerald-600">{successMessage}</p> : null}

      <Card>
        <CardHeader>
          <CardTitle>CPU and RAM</CardTitle>
        </CardHeader>
        <CardContent className="grid gap-4 md:grid-cols-3">
          <div className="space-y-2">
            <Label htmlFor="cpuCoreWeight">CPU core weight</Label>
            <Input
              id="cpuCoreWeight"
              type="number"
              min={1}
              value={policy.cpuCoreWeight}
              onChange={(event) => setPolicy((current) => ({ ...current, cpuCoreWeight: toPositiveNumber(event.target.value) }))}
            />
          </div>
          <div className="space-y-2">
            <Label htmlFor="ramGbPerUnit">RAM GB per unit</Label>
            <Input
              id="ramGbPerUnit"
              type="number"
              min={1}
              value={policy.ramGbPerUnit}
              onChange={(event) => setPolicy((current) => ({ ...current, ramGbPerUnit: toPositiveNumber(event.target.value) }))}
            />
          </div>
          <div className="space-y-2">
            <Label htmlFor="ramUnitWeight">RAM unit weight</Label>
            <Input
              id="ramUnitWeight"
              type="number"
              min={1}
              value={policy.ramUnitWeight}
              onChange={(event) => setPolicy((current) => ({ ...current, ramUnitWeight: toPositiveNumber(event.target.value) }))}
            />
          </div>
        </CardContent>
      </Card>

      <Card>
        <CardHeader className="flex flex-row items-center justify-between">
          <CardTitle>GPU Tiers</CardTitle>
          <Button type="button" variant="outline" size="sm" onClick={addTier}>
            <RiAddLine aria-hidden="true" size={14} />
            Add
          </Button>
        </CardHeader>
        <CardContent className="space-y-3">
          {policy.gpuWeightTiers.map((tier, index) => (
            <div key={`${tier.minMemoryGb}-${index}`} className="grid gap-3 md:grid-cols-[1fr_1fr_auto] md:items-end">
              <div className="space-y-2">
                <Label htmlFor={`gpu-threshold-${index}`}>Minimum VRAM GB</Label>
                <Input
                  id={`gpu-threshold-${index}`}
                  type="number"
                  min={0}
                  value={tier.minMemoryGb}
                  onChange={(event) => updateTier(index, { minMemoryGb: toNonNegativeNumber(event.target.value) })}
                />
              </div>
              <div className="space-y-2">
                <Label htmlFor={`gpu-weight-${index}`}>Weight</Label>
                <Input
                  id={`gpu-weight-${index}`}
                  type="number"
                  min={1}
                  value={tier.weight}
                  onChange={(event) => updateTier(index, { weight: toPositiveNumber(event.target.value) })}
                />
              </div>
              <Button
                type="button"
                variant="outline"
                size="icon"
                onClick={() => removeTier(index)}
                disabled={policy.gpuWeightTiers.length <= 1 || tier.minMemoryGb === 0}
                aria-label="Remove GPU tier"
              >
                <RiDeleteBinLine aria-hidden="true" size={15} />
              </Button>
            </div>
          ))}
          <p className="text-muted-foreground text-xs">Last updated {formatLocalDateTime(policy.updatedAt)}</p>
        </CardContent>
      </Card>
    </section>
  );
}
