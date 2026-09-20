import type { BootstrapStatus, CreateProbe, ProbeRecord } from '@merine/api-contract';
import { request } from '../../shared/http';

export const diagnosticsQueryKey = ['diagnostics', 'bootstrap'] as const;

export function getDiagnostics(signal: AbortSignal) {
  return request<BootstrapStatus>('/api/bootstrap', { signal });
}

export function createProbe(input: CreateProbe) {
  return request<ProbeRecord>('/api/bootstrap/probes', {
    method: 'POST',
    body: JSON.stringify(input),
  });
}
