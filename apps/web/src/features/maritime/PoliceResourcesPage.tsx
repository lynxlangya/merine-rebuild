import { Alert, Button, Empty, Input, Result, Space, Tabs } from 'antd';
import { useEffect } from 'react';
import { Link, useLocation, useSearchParams } from 'react-router';
import { errorText } from '../../shared/api-error';
import { hasPermission, PERMISSIONS } from '../../shared/permissions';
import { PageHeader } from '../../shared/ui/PageHeader';
import { TablePager } from '../../shared/ui/TablePager';
import { useAuth } from '../auth/public';
import { ArchiveDetail } from './components/ArchiveDetail';
import { ArchiveList } from './components/ArchiveList';
import { useArchiveMaintenance } from './components/ArchiveMaintenance';
import { WharfRelations } from './components/WharfRelations';
import { archives, positivePage, type ArchiveKind } from './model';
import { useArchives } from './queries';
import styles from './Maritime.module.css';

export function PoliceResourcesPage() {
  const [params, setParams] = useSearchParams();
  const location = useLocation();
  const currentUrl = location.pathname + location.search;
  const kind: ArchiveKind =
    params.get('type') === 'port-officers' ? 'port-officers' : 'police-stations';
  const id = params.get('id');
  const { state } = useAuth();
  const codes = state.status === 'authenticated' ? state.user.permissionCodes : [];
  const allowed = hasPermission(codes, PERMISSIONS.maritimePolicingRead);
  const stations = useArchives(
    'police-stations',
    {
      keyword: params.get('stationKeyword') || undefined,
      page: positivePage(params.get('stationPage')),
      pageSize: 10,
    },
    allowed && kind === 'police-stations',
  );
  useEffect(() => {
    if (kind === 'police-stations' && !id && stations.data?.items[0]) {
      const next = new URLSearchParams(params);
      next.set('id', stations.data.items[0].id);
      setParams(next, { replace: true });
    }
  }, [kind, id, stations.data, params, setParams]);
  const maintenance = useArchiveMaintenance((deletedKind, deletedId) => {
    if (id && kind === deletedKind && id === deletedId) {
      const next = new URLSearchParams(params);
      next.delete('id');
      next.delete('returnTo');
      setParams(next);
    }
  });
  if (!allowed) return <Result status="403" title="没有查看警务资源的权限" />;
  const selectedId = id || stations.data?.items[0]?.id;
  const detail = (detailKind: ArchiveKind, detailId: string) => (
    <ArchiveDetail
      key={`${detailKind}-${detailId}`}
      kind={detailKind}
      id={detailId}
      currentUrl={
        detailKind === 'police-stations' && !id
          ? currentUrl + (location.search ? '&' : '?') + 'id=' + detailId
          : currentUrl
      }
      returnTo={params.get('returnTo')}
      showBack={detailKind !== 'police-stations'}
      onEdit={
        hasPermission(codes, archives[detailKind].update)
          ? (row) => maintenance.edit(detailKind, row)
          : undefined
      }
      onDelete={
        hasPermission(codes, archives[detailKind].delete)
          ? (row) => maintenance.remove(detailKind, row)
          : undefined
      }
    >
      {(row) => (
        <>
          {detailKind === 'police-stations' && (
            <>
              <div className={styles.toolbar}>
                <h2>所属民警</h2>
                {hasPermission(codes, PERMISSIONS.maritimePortOfficerCreate) &&
                  row.status === 'ENABLED' && (
                    <Button
                      type="primary"
                      onClick={() =>
                        maintenance.create('port-officers', { policeStationId: row.id })
                      }
                    >
                      添加民警
                    </Button>
                  )}
              </div>
              <ArchiveList
                kind="port-officers"
                fixed={{ policeStationId: row.id }}
                pageKey="officerPage"
                compact
                title="所属民警档案"
                currentUrl={currentUrl}
                onEdit={(item) => maintenance.edit('port-officers', item)}
                onDelete={(item) => maintenance.remove('port-officers', item)}
              />
            </>
          )}
          <WharfRelations
            stationId={detailKind === 'police-stations' ? row.id : undefined}
            officerId={detailKind === 'port-officers' ? row.id : undefined}
            currentUrl={currentUrl}
          />
        </>
      )}
    </ArchiveDetail>
  );
  return (
    <div>
      <PageHeader
        title="警务资源"
        demo={false}
        description="按派出所查看所属民警与管辖码头，维护港区责任关系。"
        actions={
          <Space>
            {hasPermission(codes, PERMISSIONS.maritimePoliceStationCreate) && (
              <Button onClick={() => maintenance.create('police-stations')}>新建派出所</Button>
            )}
            {hasPermission(codes, PERMISSIONS.maritimePortOfficerCreate) && (
              <Button type="primary" onClick={() => maintenance.create('port-officers')}>
                添加民警
              </Button>
            )}
          </Space>
        }
      />
      <div className={styles.page}>
        <Tabs
          activeKey={kind}
          items={[
            { key: 'police-stations', label: '按派出所查看' },
            { key: 'port-officers', label: '全部民警' },
          ]}
          onChange={(type) => setParams({ type })}
        />
        {kind === 'port-officers' ? (
          id ? (
            detail(kind, id)
          ) : (
            <ArchiveList
              kind={kind}
              currentUrl={currentUrl}
              onEdit={(row) => maintenance.edit(kind, row)}
              onDelete={(row) => maintenance.remove(kind, row)}
            />
          )
        ) : (
          <div className={styles.policing}>
            <aside className={`${styles.panel} ${styles.stationSidebar}`} aria-label="派出所列表">
              <div className={styles.stationSidebarHead}>
                <h2>派出所</h2>
                <Input.Search
                  key={params.get('stationKeyword') || ''}
                  defaultValue={params.get('stationKeyword') || ''}
                  aria-label="搜索派出所"
                  placeholder="请输入派出所名称"
                  allowClear
                  onSearch={(keyword) => {
                    const next = new URLSearchParams(params);
                    if (keyword) next.set('stationKeyword', keyword);
                    else next.delete('stationKeyword');
                    for (const key of ['stationPage', 'id', 'officerPage', 'wharfPage', 'returnTo'])
                      next.delete(key);
                    setParams(next);
                  }}
                />
              </div>
              {stations.isError ? (
                <Alert
                  type="error"
                  title={errorText(stations.error)}
                  action={<Button onClick={() => void stations.refetch()}>重试</Button>}
                />
              ) : (
                <div className={styles.stations}>
                  {stations.data?.items.map((row) => {
                    const next = new URLSearchParams(params);
                    next.set('id', row.id);
                    for (const key of ['officerPage', 'wharfPage', 'returnTo']) next.delete(key);
                    return (
                      <Link
                        key={row.id}
                        to={location.pathname + '?' + next}
                        className={`${styles.stationLink} ${selectedId === row.id ? styles.activeStation : ''}`}
                        aria-current={selectedId === row.id ? 'true' : undefined}
                      >
                        {row.name}
                        <small>
                          {row.region}
                          {row.status === 'DISABLED' ? ' · 已停用' : ''}
                        </small>
                      </Link>
                    );
                  })}
                </div>
              )}
              <TablePager
                compact
                total={stations.data?.total ?? 0}
                page={stations.data?.page ?? 1}
                pageSize={10}
                itemCount={stations.data?.items.length ?? 0}
                onPageChange={(page) => {
                  const next = new URLSearchParams(params);
                  next.set('stationPage', String(page));
                  for (const key of ['id', 'officerPage', 'wharfPage', 'returnTo'])
                    next.delete(key);
                  setParams(next);
                }}
              />
            </aside>
            <section>
              {selectedId ? (
                detail('police-stations', selectedId)
              ) : (
                <Empty
                  description={stations.isPending ? '正在加载派出所' : '暂无符合条件的派出所'}
                />
              )}
            </section>
          </div>
        )}
      </div>
      {maintenance.overlays}
    </div>
  );
}
