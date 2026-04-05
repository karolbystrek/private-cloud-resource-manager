export type NodeSummary = {
  id: string;
  nomadNodeId: string;
  hostname: string;
  ipAddress: string;
  status: string;
  totalCpuCores: number;
  totalRamMb: number;
  totalGpuCount: number;
  lastHeartbeat: string | null;
};

export type NodeDetails = {
  id: string;
  nomadNodeId: string;
  hostname: string;
  ipAddress: string;
  status: string;
  nomadStatus: string | null;
  nomadStatusDescription: string | null;
  schedulingEligibility: string | null;
  datacenter: string | null;
  nodePool: string | null;
  nodeClass: string | null;
  draining: boolean;
  nomadVersion: string | null;
  dockerVersion: string | null;
  nomadCreateIndex: number | null;
  nomadModifyIndex: number | null;
  totalCpuCores: number;
  totalRamMb: number;
  totalGpuCount: number;
  agentVersion: string | null;
  lastHeartbeat: string | null;
  createdAt: string | null;
};
