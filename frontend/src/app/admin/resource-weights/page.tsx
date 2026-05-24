import type { Metadata } from 'next';
import { redirect } from 'next/navigation';
import { ResourceWeightsForm } from './resource-weights-form';
import { brokerFetch } from '@/lib/server-auth';
import { isUserRole } from '@/lib/user-role';

export const metadata: Metadata = {
  title: 'Resource Weights - Private Cloud Resource Manager',
};

export type GpuWeightTier = {
  minMemoryGb: number;
  weight: number;
};

export type ResourceWeightPolicy = {
  cpuCoreWeight: number;
  ramGbPerUnit: number;
  ramUnitWeight: number;
  gpuWeightTiers: GpuWeightTier[];
  updatedAt: string;
};

type QuotaMeResponse = {
  role: string;
};

async function fetchCurrentUserRole(): Promise<string | null> {
  const response = await brokerFetch('/api/quota/me', {
    cache: 'no-store',
  }, '/admin/resource-weights');

  if (!response.ok) {
    return null;
  }

  const quota = (await response.json()) as QuotaMeResponse;
  return isUserRole(quota.role) ? quota.role : null;
}

async function fetchResourceWeights(): Promise<ResourceWeightPolicy | null> {
  const response = await brokerFetch('/admin/quota/resource-weights', {
    cache: 'no-store',
  }, '/admin/resource-weights');

  if (response.status === 403) {
    redirect('/');
  }

  if (!response.ok) {
    return null;
  }

  return (await response.json()) as ResourceWeightPolicy;
}

export default async function AdminResourceWeightsPage() {
  const userRole = await fetchCurrentUserRole();
  if (userRole !== 'ADMIN') {
    redirect('/');
  }

  const policy = await fetchResourceWeights();

  return (
    <div className="bg-background/50 min-h-[calc(100vh-5.5rem)] w-full py-8">
      <div className="container mx-auto max-w-4xl px-4 md:px-6">
        <ResourceWeightsForm initialPolicy={policy} />
      </div>
    </div>
  );
}
