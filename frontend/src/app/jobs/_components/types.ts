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

export type JobHistoryItem = {
  id: string;
  status: JobStatus;
  dockerImage: string;
  executionCommand: string;
  reqCpuCores: number;
  reqRamGb: number;
  reqGpu: boolean;
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
  reqGpu: boolean;
  totalConsumedMinutes: number;
  createdAt: string;
  updatedAt: string;
  userId: string;
  userEmail: string;
};
