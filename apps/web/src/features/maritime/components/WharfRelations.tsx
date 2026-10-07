import { useQuery } from '@tanstack/react-query';
import { Alert, Button, Empty, Space, Table } from 'antd';
import { Link, useSearchParams } from 'react-router';
import { errorText } from '../../../shared/api-error';
import { hasPermission, PERMISSIONS } from '../../../shared/permissions';
import { TablePager } from '../../../shared/ui/TablePager';
import { useAuth } from '../../auth/public';
import { fetchWharfRelations, type ArchiveFilters } from '../api';
import { archiveHref, positivePage } from '../model';
import styles from '../Maritime.module.css';

export function WharfRelations({
  stationId,
  officerId,
  currentUrl,
}: {
  stationId?: string;
  officerId?: string;
  currentUrl: string;
}) {
  const [params, setParams] = useSearchParams();
  const { state } = useAuth();
  const codes = state.status === 'authenticated' ? state.user.permissionCodes : [];
  const filters: ArchiveFilters = {
    policeStationId: stationId,
    responsibleOfficerId: officerId,
    page: positivePage(params.get('wharfPage')),
  };
  const query = useQuery({
    queryKey: ['maritime', 'wharfRelations', filters],
    queryFn: ({ signal }) => fetchWharfRelations(filters, signal),
  });
  return (
    <div className={styles.panel}>
      <div className={styles.toolbar}>
        <h2>{officerId ? '负责码头' : '管辖码头'}</h2>
        <Button onClick={() => void query.refetch()}>刷新</Button>
      </div>
      {query.isError ? (
        <Alert
          type="error"
          title="责任关系加载失败"
          description={errorText(query.error)}
          action={<Button onClick={() => void query.refetch()}>重试</Button>}
        />
      ) : (
        <>
          <Table
            rowKey="id"
            size="small"
            pagination={false}
            dataSource={query.data?.items ?? []}
            loading={query.isFetching}
            columns={[
              {
                title: '码头',
                key: 'name',
                render: (_, row) =>
                  hasPermission(codes, PERMISSIONS.maritimeHarborRead) ? (
                    <Link to={archiveHref('wharfs', row.id, currentUrl)}>{row.name}</Link>
                  ) : (
                    row.name
                  ),
              },
              {
                title: '责任民警',
                key: 'officer',
                render: (_, row) =>
                  row.responsibleOfficerId ? (
                    <Space wrap>
                      {officerId === row.responsibleOfficerId ? (
                        <span>{row.responsibleOfficerName}</span>
                      ) : (
                        <Link
                          to={archiveHref('port-officers', row.responsibleOfficerId, currentUrl)}
                        >
                          {row.responsibleOfficerName}
                        </Link>
                      )}
                    </Space>
                  ) : (
                    '未指定'
                  ),
              },
            ]}
            locale={{
              emptyText: <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无关联码头" />,
            }}
          />
          <TablePager
            total={query.data?.total ?? 0}
            page={query.data?.page ?? 1}
            pageSize={20}
            itemCount={query.data?.items.length ?? 0}
            onPageChange={(page) => {
              const next = new URLSearchParams(params);
              next.set('wharfPage', String(page));
              setParams(next);
            }}
          />
        </>
      )}
    </div>
  );
}
