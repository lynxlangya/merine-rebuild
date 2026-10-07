import { Alert, Button, Descriptions, Result, Space, Spin, Tag } from 'antd';
import { Link } from 'react-router';
import { errorText } from '../../../shared/api-error';
import { hasPermission } from '../../../shared/permissions';
import { DetailBackLink } from '../../../shared/ui/DetailBackLink';
import { PageHeader } from '../../../shared/ui/PageHeader';
import { useAuth } from '../../auth/public';
import { DICTIONARY_CODES, dictLabel, useDictionary } from '../../dictionaries/public';
import { archiveHref, archives, safeReturn, type ArchiveKind, type ArchiveRecord } from '../model';
import { useArchive } from '../queries';
import styles from '../Maritime.module.css';

export function ArchiveDetail({
  kind,
  id,
  currentUrl,
  returnTo,
  onEdit,
  onDelete,
  children,
}: {
  kind: ArchiveKind;
  id: string;
  currentUrl: string;
  returnTo: string | null;
  onEdit?: (row: ArchiveRecord) => void;
  onDelete?: (row: ArchiveRecord) => void;
  children?: (record: ArchiveRecord) => React.ReactNode;
}) {
  const { state } = useAuth();
  const codes = state.status === 'authenticated' ? state.user.permissionCodes : [];
  const allowed = hasPermission(codes, archives[kind].read);
  const result = useArchive(kind, id, allowed);
  const status = useDictionary(DICTIONARY_CODES.status);
  const inhabitation = useDictionary(DICTIONARY_CODES.islandInhabitation);
  if (!allowed) return <Result status="403" title="没有查看此类档案的权限" />;
  if (result.isPending) return <Spin />;
  if (result.isError)
    return (
      <Alert
        type="error"
        title="档案加载失败"
        description={errorText(result.error)}
        action={<Button onClick={() => void result.refetch()}>重试</Button>}
      />
    );
  const row: ArchiveRecord = result.data;
  function related(
    target: ArchiveKind,
    targetId?: string | null,
    name?: string | null,
    relationStatus?: string | null,
  ) {
    if (!targetId || !name) return '未提供';
    return (
      <Space wrap>
        {hasPermission(codes, archives[target].read) ? (
          <Link to={archiveHref(target, targetId, currentUrl)}>{name}</Link>
        ) : (
          <span>{name}</span>
        )}
        {relationStatus === 'DISABLED' && <Tag>已停用</Tag>}
      </Space>
    );
  }
  const items = [
    { key: 'region', label: '所在区域', children: row.region },
    {
      key: 'status',
      label: '状态',
      children: (
        <Tag color={row.status === 'ENABLED' ? 'green' : 'default'}>
          {dictLabel(status.data, row.status)}
        </Tag>
      ),
    },
    ...(kind !== 'port-officers'
      ? [{ key: 'location', label: '位置描述', children: row.location || '未提供' }]
      : []),
    ...(row.purpose !== undefined
      ? [{ key: 'purpose', label: '用途', children: row.purpose || '未提供' }]
      : []),
    ...(kind === 'islands'
      ? [
          {
            key: 'inhabitation',
            label: '居住类型',
            children: dictLabel(inhabitation.data, row.inhabitationType ?? ''),
          },
        ]
      : []),
    ...(kind === 'wharfs'
      ? [
          {
            key: 'port',
            label: '所属港口',
            children: row.portId
              ? related('ports', row.portId, row.portName, row.portStatus)
              : '独立码头',
          },
          {
            key: 'station',
            label: '管辖派出所',
            children: related(
              'police-stations',
              row.policeStationId,
              row.policeStationName,
              row.policeStationStatus,
            ),
          },
          {
            key: 'officer',
            label: '主要责任民警',
            children: related(
              'port-officers',
              row.responsibleOfficerId,
              row.responsibleOfficerName,
              row.responsibleOfficerStatus,
            ),
          },
        ]
      : []),
    ...(kind === 'port-officers'
      ? [
          {
            key: 'station',
            label: '所属派出所',
            children: related(
              'police-stations',
              row.policeStationId,
              row.policeStationName,
              row.policeStationStatus,
            ),
          },
          { key: 'duty', label: '职务', children: row.duty || '未提供' },
        ]
      : []),
  ];
  return (
    <div className={styles.detail}>
      <DetailBackLink to={safeReturn(returnTo, archives[kind].path + '?type=' + kind)}>
        {returnTo &&
        new URL(
          safeReturn(returnTo, archives[kind].path),
          'https://local.invalid',
        ).searchParams.has('id')
          ? '返回上一步'
          : `返回${archives[kind].label}列表`}
      </DetailBackLink>
      <PageHeader
        title={row.name}
        demo={false}
        description={`${archives[kind].label}档案 · ${row.region}`}
        actions={
          <Space>
            <Button onClick={() => void result.refetch()}>刷新</Button>
            {onEdit && <Button onClick={() => onEdit(row)}>编辑档案</Button>}
            {onDelete && (
              <Button danger onClick={() => onDelete(row)}>
                删除
              </Button>
            )}
          </Space>
        }
      />
      <div className={styles.panel}>
        <Descriptions column={2} items={items} />
      </div>
      {children?.(row)}
    </div>
  );
}
