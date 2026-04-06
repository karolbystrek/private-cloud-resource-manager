export type JobHistorySortDirection = 'asc' | 'desc';

export type JobHistoryItem = {
  id: string;
  nodeId: string | null;
  status: string;
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
