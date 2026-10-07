import type {
  AnchorageView,
  IslandView,
  PoliceStationView,
  PortOfficerView,
  PortView,
  WharfView,
  WriteAnchorage,
  WriteIsland,
  WritePoliceStation,
  WritePort,
  WritePortOfficer,
  WriteWharf,
} from '@merine/api-contract';
import { PERMISSIONS } from '../../shared/permissions';

export type ArchiveKind =
  'ports' | 'wharfs' | 'anchorages' | 'islands' | 'police-stations' | 'port-officers';
export type ViewByKind = {
  ports: PortView;
  wharfs: WharfView;
  anchorages: AnchorageView;
  islands: IslandView;
  'police-stations': PoliceStationView;
  'port-officers': PortOfficerView;
};
export type WriteByKind = {
  ports: WritePort;
  wharfs: WriteWharf;
  anchorages: WriteAnchorage;
  islands: WriteIsland;
  'police-stations': WritePoliceStation;
  'port-officers': WritePortOfficer;
};
/** 仅供共享档案列表/表单展示；字段契约仍来自各后端 DTO。 */
export type ArchiveRecord = Pick<
  PortView,
  'id' | 'name' | 'region' | 'status' | 'version' | 'createdAt' | 'updatedAt' | 'fixtureKey'
> &
  Partial<WharfView & IslandView & Omit<PortOfficerView, 'policeStationId'> & PoliceStationView>;
export const archives = {
  ports: {
    label: '港口',
    read: PERMISSIONS.maritimeHarborRead,
    create: PERMISSIONS.maritimePortCreate,
    update: PERMISSIONS.maritimePortUpdate,
    delete: PERMISSIONS.maritimePortDelete,
    path: '/maritime/harbor-sites',
  },
  wharfs: {
    label: '码头',
    read: PERMISSIONS.maritimeHarborRead,
    create: PERMISSIONS.maritimeWharfCreate,
    update: PERMISSIONS.maritimeWharfUpdate,
    delete: PERMISSIONS.maritimeWharfDelete,
    path: '/maritime/harbor-sites',
  },
  anchorages: {
    label: '锚地',
    read: PERMISSIONS.maritimeHarborRead,
    create: PERMISSIONS.maritimeAnchorageCreate,
    update: PERMISSIONS.maritimeAnchorageUpdate,
    delete: PERMISSIONS.maritimeAnchorageDelete,
    path: '/maritime/harbor-sites',
  },
  islands: {
    label: '海岛',
    read: PERMISSIONS.maritimeIslandRead,
    create: PERMISSIONS.maritimeIslandCreate,
    update: PERMISSIONS.maritimeIslandUpdate,
    delete: PERMISSIONS.maritimeIslandDelete,
    path: '/maritime/islands',
  },
  'police-stations': {
    label: '派出所',
    read: PERMISSIONS.maritimePolicingRead,
    create: PERMISSIONS.maritimePoliceStationCreate,
    update: PERMISSIONS.maritimePoliceStationUpdate,
    delete: PERMISSIONS.maritimePoliceStationDelete,
    path: '/maritime/police-resources',
  },
  'port-officers': {
    label: '民警',
    read: PERMISSIONS.maritimePolicingRead,
    create: PERMISSIONS.maritimePortOfficerCreate,
    update: PERMISSIONS.maritimePortOfficerUpdate,
    delete: PERMISSIONS.maritimePortOfficerDelete,
    path: '/maritime/police-resources',
  },
} as const;

export function archiveHref(kind: ArchiveKind, id: string, returnTo?: string) {
  const query = new URLSearchParams({ type: kind, id });
  if (returnTo) query.set('returnTo', returnTo);
  return `${archives[kind].path}?${query}`;
}
export function safeReturn(value: string | null, fallback: string) {
  if (!value) return fallback;
  try {
    const parsed = new URL(value, 'https://local.invalid');
    return parsed.origin === 'https://local.invalid' && parsed.pathname.startsWith('/maritime/')
      ? parsed.pathname + parsed.search
      : fallback;
  } catch {
    return fallback;
  }
}
export function positivePage(value: string | null) {
  const page = Number(value);
  return Number.isInteger(page) && page > 0 && page <= 1000000 ? page : 1;
}
/** 表单只发送当前档案允许维护的字段，fixtureKey、时间和关联名称不进入写请求。 */
export function writeInput(
  kind: ArchiveKind,
  values: Partial<ArchiveRecord>,
  version?: number,
): WriteByKind[ArchiveKind] {
  const common = {
    version,
    name: values.name?.trim() ?? '',
    status: values.status ?? 'ENABLED',
  };
  if (kind === 'port-officers')
    return {
      version,
      status: values.status ?? 'ENABLED',
      userId: values.userId ?? '',
      policeStationId: values.policeStationId ?? '',
      duty: values.duty || null,
    };
  const located = {
    ...common,
    region: values.region?.trim() ?? '',
    location: values.location || null,
  };
  if (kind === 'wharfs')
    return {
      ...located,
      purpose: values.purpose || null,
      portId: values.portId || null,
      policeStationId: values.policeStationId || null,
      responsibleOfficerId: values.responsibleOfficerId || null,
    };
  if (kind === 'islands')
    return { ...located, inhabitationType: values.inhabitationType ?? 'INHABITED' };
  if (kind === 'police-stations') return { ...located, unitCode: values.unitCode ?? '' };
  return { ...located, purpose: values.purpose || null };
}
