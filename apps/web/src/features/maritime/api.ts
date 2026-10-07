import type { ArchiveOption, PageResultPortView } from '@merine/api-contract';
import { request } from '../../shared/http';
import type { ArchiveKind, ViewByKind, WriteByKind } from './model';
export type ArchiveFilters = {
  keyword?: string;
  region?: string;
  status?: string;
  portId?: string;
  policeStationId?: string;
  responsibleOfficerId?: string;
  inhabitationType?: string;
  page?: number;
  pageSize?: number;
};
export type ArchivePage<T> = Omit<PageResultPortView, 'items'> & { items: T[] };
function query(filters: ArchiveFilters) {
  const search = new URLSearchParams();
  for (const [key, value] of Object.entries(filters))
    if (value !== undefined && value !== '') search.set(key, String(value));
  return search.toString();
}
export function fetchArchives<K extends ArchiveKind>(
  kind: K,
  filters: ArchiveFilters,
  signal?: AbortSignal,
) {
  return request<ArchivePage<ViewByKind[K]>>(`/api/maritime/${kind}?${query(filters)}`, { signal });
}
export function fetchArchive<K extends ArchiveKind>(kind: K, id: string, signal?: AbortSignal) {
  return request<ViewByKind[K]>(`/api/maritime/${kind}/${encodeURIComponent(id)}`, { signal });
}
export function saveArchive<K extends ArchiveKind>(kind: K, input: WriteByKind[K], id?: string) {
  return request<ViewByKind[K]>(`/api/maritime/${kind}${id ? '/' + encodeURIComponent(id) : ''}`, {
    method: id ? 'PUT' : 'POST',
    body: JSON.stringify(input),
  });
}
export function deleteArchive(kind: ArchiveKind, id: string, version: number) {
  return request<null>(`/api/maritime/${kind}/${encodeURIComponent(id)}?version=${version}`, {
    method: 'DELETE',
  });
}
export function fetchOptions(
  kind: 'ports' | 'police-stations' | 'port-officers',
  filters: ArchiveFilters,
  signal?: AbortSignal,
) {
  return request<ArchivePage<ArchiveOption>>(`/api/maritime/options/${kind}?${query(filters)}`, {
    signal,
  });
}
export function fetchWharfRelations(filters: ArchiveFilters, signal?: AbortSignal) {
  return request<ArchivePage<import('@merine/api-contract').WharfRelationView>>(
    `/api/maritime/options/wharf-relations?${query(filters)}`,
    { signal },
  );
}

export function fetchOfficerUsers(
  stationId: string,
  keyword: string,
  page: number,
  signal?: AbortSignal,
) {
  return request<ArchivePage<import('@merine/api-contract').OfficerUserOption>>(
    `/api/maritime/options/officer-users?${query({ policeStationId: stationId, keyword, page, pageSize: 50 })}`,
    { signal },
  );
}
export function fetchUnitPoliceStations(unitCode: string, page: number, signal?: AbortSignal) {
  return request<import('@merine/api-contract').PageResultUnitPoliceStation>(
    `/api/system/units/${encodeURIComponent(unitCode)}/police-stations?page=${page}&pageSize=10`,
    { signal },
  );
}
