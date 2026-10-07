import { useQuery } from '@tanstack/react-query';
import { fetchArchive, fetchArchives, type ArchiveFilters } from './api';
import type { ArchiveKind } from './model';
export const maritimeKeys = { all: ['maritime'] as const };
export function useArchives<K extends ArchiveKind>(
  kind: K,
  filters: ArchiveFilters,
  enabled = true,
) {
  return useQuery({
    queryKey: [...maritimeKeys.all, kind, 'list', filters],
    queryFn: ({ signal }) => fetchArchives(kind, filters, signal),
    enabled,
  });
}
export function useArchive<K extends ArchiveKind>(kind: K, id: string | null, enabled = true) {
  return useQuery({
    queryKey: [...maritimeKeys.all, kind, 'detail', id],
    queryFn: ({ signal }) => fetchArchive(kind, id!, signal),
    enabled: enabled && Boolean(id),
  });
}
