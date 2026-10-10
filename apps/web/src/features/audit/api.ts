import type {
  AuditLogEntry,
  AuditModuleOption,
  AuditPurgeResult,
  PageResultAuditLogEntry,
} from '@merine/api-contract';
import { request } from '../../shared/http';

export const auditKeys = {
  list: (query: AuditQuery) => ['system', 'audit-logs', query] as const,
  modules: ['system', 'audit-modules'] as const,
};

/** 审计查询条件：全部服务端执行；时间缺省由后端收敛为最近 7 天。 */
export interface AuditQuery {
  from?: string;
  to?: string;
  actor?: string;
  module?: string;
  action?: string;
  result?: string;
  targetType?: string;
  targetId?: string;
  page: number;
  pageSize: number;
}

export function fetchAuditLogs(query: AuditQuery, signal?: AbortSignal) {
  const params = new URLSearchParams({
    from: query.from ?? '',
    to: query.to ?? '',
    actor: query.actor ?? '',
    module: query.module ?? '',
    action: query.action ?? '',
    result: query.result ?? '',
    targetType: query.targetType ?? '',
    targetId: query.targetId ?? '',
    page: String(query.page),
    pageSize: String(query.pageSize),
  });
  return request<PageResultAuditLogEntry>(`/api/system/audit-logs?${params}`, { signal });
}

export function fetchAuditModules(signal?: AbortSignal) {
  return request<AuditModuleOption[]>('/api/system/audit-logs/modules', { signal });
}

/** 立即清理超过保留期的流水（服务端按保留期决定删什么，前端不改条件）。 */
export function purgeAuditLogs() {
  return request<AuditPurgeResult>('/api/system/audit-logs/purge', { method: 'POST' });
}

export type { AuditLogEntry };
