export type JobHistorySortDirection = 'asc' | 'desc';

export type JobStatus = 'PENDING' | 'RUNNING' | 'COMPLETED' | 'FAILED' | 'OOM_KILLED' | 'LEASE_EXPIRED' | 'STOPPED';

export type JobHistoryItem = {
  id: string;
  nodeId: string | null;
  status: JobStatus;
  dockerImage: string;
  executionCommand: string;
  reqCpuCores: number;
  reqRamGb: number;
  totalCostCredits: number;
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
  totalCostCredits: number;
  nodeId: string | null;
  createdAt: string;
  userId: string;
  username: string;
};
