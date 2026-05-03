export type JobHistorySortDirection = 'asc' | 'desc';

export type JobStatus =
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
  runId: string | null;
  nodeId: string | null;
  status: JobStatus;
  dockerImage: string;
  executionCommand: string;
  reqCpuCores: number;
  reqRamGb: number;
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
  runId: string | null;
  status: JobStatus;
  dockerImage: string;
  executionCommand: string;
  reqCpuCores: number;
  reqRamGb: number;
  totalConsumedMinutes: number;
  nodeId: string | null;
  createdAt: string;
  userId: string;
  username: string;
};
