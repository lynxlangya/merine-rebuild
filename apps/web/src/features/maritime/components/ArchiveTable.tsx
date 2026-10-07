import { Alert, Button, Empty, Space, Table, Tag } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { Link } from 'react-router';
import { errorText } from '../../../shared/api-error';
import { RecordTitleLink } from '../../../shared/ui/RecordTitleLink';
import { TablePager } from '../../../shared/ui/TablePager';
import recordStyles from '../../../shared/ui/RecordTable.module.css';
import { DICTIONARY_CODES, dictLabel, useDictionary } from '../../dictionaries/public';
import type { ArchivePage } from '../api';
import { archiveHref, type ArchiveKind, type ArchiveRecord } from '../model';
import styles from '../Maritime.module.css';

export function ArchiveTable({
  kind,
  data,
  loading,
  error,
  refetch,
  onPage,
  title,
  returnTo,
  onEdit,
  onDelete,
}: {
  kind: ArchiveKind;
  data?: ArchivePage<ArchiveRecord>;
  loading: boolean;
  error: unknown;
  refetch: () => void;
  onPage: (page: number) => void;
  title?: string;
  returnTo: string;
  onEdit?: (row: ArchiveRecord) => void;
  onDelete?: (row: ArchiveRecord) => void;
}) {
  const status = useDictionary(DICTIONARY_CODES.status);
  const inhabitation = useDictionary(DICTIONARY_CODES.islandInhabitation);
  const columns: ColumnsType<ArchiveRecord> = [
    {
      title: kind === 'port-officers' ? '姓名' : '名称',
      key: 'name',
      width: 270,
      render: (_, row) => (
        <Space direction="vertical" size={4}>
          <RecordTitleLink to={archiveHref(kind, row.id, returnTo)}>{row.name}</RecordTitleLink>
        </Space>
      ),
    },
    { title: '所在区域', dataIndex: 'region', key: 'region', width: 180 },
  ];
  if (kind === 'wharfs')
    columns.push({
      title: '所属港口 / 责任关系',
      key: 'relations',
      width: 290,
      render: (_, row) => (
        <Space direction="vertical" size={4}>
          <span>
            {row.portName ? (
              <Link to={archiveHref('ports', row.portId!, returnTo)}>{row.portName}</Link>
            ) : (
              '独立码头'
            )}
          </span>
          <span className={styles.muted}>
            {row.policeStationName || '未指定派出所'}
            {row.responsibleOfficerName ? ' · ' + row.responsibleOfficerName : ''}
          </span>
        </Space>
      ),
    });
  else if (kind === 'islands')
    columns.push({
      title: '居住类型',
      key: 'type',
      width: 150,
      render: (_, row) => dictLabel(inhabitation.data, row.inhabitationType ?? ''),
    });
  else if (kind === 'port-officers')
    columns.push({
      title: '所属派出所 / 职务',
      key: 'station',
      width: 260,
      render: (_, row) => (
        <Space direction="vertical" size={3}>
          <Link to={archiveHref('police-stations', row.policeStationId!, returnTo)}>
            {row.policeStationName}
          </Link>
          <span className={styles.muted}>{row.duty || '未提供职务'}</span>
        </Space>
      ),
    });
  else
    columns.push({
      title: '位置 / 用途',
      key: 'location',
      width: 280,
      render: (_, row) => (
        <Space direction="vertical" size={3}>
          <span>{row.location || '未提供位置'}</span>
          {row.purpose && <span className={styles.muted}>{row.purpose}</span>}
        </Space>
      ),
    });
  columns.push({
    title: '状态',
    key: 'status',
    width: 90,
    render: (_, row) => (
      <Tag color={row.status === 'ENABLED' ? 'green' : 'default'}>
        {dictLabel(status.data, row.status)}
      </Tag>
    ),
  });
  if (onEdit || onDelete)
    columns.push({
      title: '操作',
      key: 'actions',
      align: 'right',
      width: 130,
      render: (_, row) => (
        <Space size={0}>
          {onEdit && (
            <Button type="text" size="small" onClick={() => onEdit(row)}>
              编辑
            </Button>
          )}
          {onDelete && (
            <Button type="text" size="small" danger onClick={() => onDelete(row)}>
              删除
            </Button>
          )}
        </Space>
      ),
    });
  return (
    <div className={recordStyles.frame}>
      <div className={recordStyles.toolbar}>
        <h2>{title ?? '档案列表'}</h2>
        <Button size="small" onClick={refetch} loading={loading}>
          刷新
        </Button>
      </div>
      {Boolean(error) && (
        <Alert
          type="error"
          showIcon
          title={data ? '刷新失败，当前显示上次结果' : '档案加载失败'}
          description={errorText(error)}
          action={<Button onClick={refetch}>重试</Button>}
        />
      )}
      {!error || data ? (
        <>
          <Table
            size="small"
            rowKey="id"
            columns={columns}
            dataSource={data?.items ?? []}
            loading={loading}
            pagination={false}
            scroll={{ x: 850 }}
            locale={{
              emptyText: (
                <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无符合条件的档案" />
              ),
            }}
          />
          <TablePager
            total={data?.total ?? 0}
            page={data?.page ?? 1}
            pageSize={data?.pageSize ?? 20}
            itemCount={data?.items.length ?? 0}
            onPageChange={onPage}
          />
        </>
      ) : null}
    </div>
  );
}
