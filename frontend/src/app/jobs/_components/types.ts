export type JobHistorySortDirection = 'asc' | 'desc';

export type JobStatus =
  | 'SUBMITTED'
  | 'QUEUED'
  | 'DISPATCHING'
  | 'SCHEDULING'
  | 'RUNNING'
  | 'FINALIZING'
  | 'SUCCEEDED'
  | 'FAILED'
  | 'CANCELED'
  | 'TIMED_OUT'
  | 'INFRA_FAILED';

export type GpuRequirement = {
  enabled: boolean;
  count: number;
  vendor: 'nvidia' | null;
  minMemoryGb: number | null;
  model: string | null;
};

export type GpuOption = {
  nodeId: string;
  nodeHostname: string;
  vendor: 'nvidia';
  model: string;
  maxMemoryGb: number | null;
  count: number;
};

export type JobHistoryItem = {
  id: string;
  status: JobStatus;
  dockerImage: string;
  executionCommand: string;
  reqCpuCores: number;
  reqRamGb: number;
  gpuRequirement: GpuRequirement;
  quotaBreakdown: QuotaBreakdown;
  totalConsumedMinutes: number;
  createdAt: string;
};

export type JobsPageResponse = {
  jobs: JobHistoryItem[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  hasNext: boolean;
  hasPrevious: boolean;
  sort: JobHistorySortDirection;
};

export type JobDetails = {
  id: string;
  status: JobStatus;
  dockerImage: string;
  executionCommand: string;
  reqCpuCores: number;
  reqRamGb: number;
  gpuRequirement: GpuRequirement;
  quotaBreakdown: QuotaBreakdown;
  totalConsumedMinutes: number;
  createdAt: string;
  updatedAt: string;
  userId: string;
  userEmail: string;
};

export type QuotaBreakdown = {
  cpuUnits: number;
  ramUnits: number;
  gpuUnits: number;
  totalUnits: number;
};
