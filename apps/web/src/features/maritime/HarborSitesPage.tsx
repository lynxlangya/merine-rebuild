import { Button, Result, Tabs } from 'antd';
import { useLocation, useSearchParams } from 'react-router';
import { hasPermission, PERMISSIONS } from '../../shared/permissions';
import { PageHeader } from '../../shared/ui/PageHeader';
import { useAuth } from '../auth/public';
import { ArchiveDetail } from './components/ArchiveDetail';
import { ArchiveList } from './components/ArchiveList';
import { useArchiveMaintenance } from './components/ArchiveMaintenance';
import { archives, safeReturn, type ArchiveKind } from './model';
import styles from './Maritime.module.css';

export function HarborSitesPage() {
  const [params, setParams] = useSearchParams();
  const location = useLocation();
  const currentUrl = location.pathname + location.search;
  const kind: ArchiveKind =
    params.get('type') === 'wharfs'
      ? 'wharfs'
      : params.get('type') === 'anchorages'
        ? 'anchorages'
        : 'ports';
  const id = params.get('id');
  const { state } = useAuth();
  const codes = state.status === 'authenticated' ? state.user.permissionCodes : [];
  const maintenance = useArchiveMaintenance((deletedKind, deletedId) => {
    if (id && kind === deletedKind && id === deletedId) {
      const back = safeReturn(params.get('returnTo'), location.pathname + '?type=' + kind);
      const url = new URL(back, 'https://local.invalid');
      setParams(url.searchParams);
    }
  });
  if (!hasPermission(codes, PERMISSIONS.maritimeHarborRead))
    return <Result status="403" title="没有查看港口与停泊点的权限" />;
  const edit = hasPermission(codes, archives[kind].update)
    ? (row: Parameters<typeof maintenance.edit>[1]) => maintenance.edit(kind, row)
    : undefined;
  const remove = hasPermission(codes, archives[kind].delete)
    ? (row: Parameters<typeof maintenance.remove>[1]) => maintenance.remove(kind, row)
    : undefined;
  return (
    <div>
      {id ? (
        <div className={styles.page}>
          <ArchiveDetail
            kind={kind}
            id={id}
            currentUrl={currentUrl}
            returnTo={params.get('returnTo')}
            onEdit={edit}
            onDelete={remove}
          >
            {(row) =>
              kind === 'ports' ? (
                <>
                  <div className={styles.toolbar}>
                    <h2>下属码头</h2>
                    {hasPermission(codes, PERMISSIONS.maritimeWharfCreate) &&
                      row.status === 'ENABLED' && (
                        <Button
                          type="primary"
                          onClick={() => maintenance.create('wharfs', { portId: row.id })}
                        >
                          新增下属码头
                        </Button>
                      )}
                  </div>
                  <ArchiveList
                    kind="wharfs"
                    fixed={{ portId: row.id }}
                    compact
                    pageKey="wharfPage"
                    currentUrl={currentUrl}
                    onEdit={(item) => maintenance.edit('wharfs', item)}
                    onDelete={(item) => maintenance.remove('wharfs', item)}
                    title="下属码头档案"
                  />
                </>
              ) : null
            }
          </ArchiveDetail>
        </div>
      ) : (
        <>
          <PageHeader
            title="港口与停泊点"
            demo={false}
            description="从港口到码头，查看停泊点与责任关系。"
            actions={
              hasPermission(codes, archives[kind].create) ? (
                <Button type="primary" onClick={() => maintenance.create(kind)}>
                  新建{archives[kind].label}
                </Button>
              ) : undefined
            }
          />
          <div className={styles.page}>
            <Tabs
              activeKey={kind}
              items={[
                { key: 'ports', label: '港口' },
                { key: 'wharfs', label: '码头' },
                { key: 'anchorages', label: '锚地' },
              ]}
              onChange={(type) => setParams({ type })}
            />
            <ArchiveList
              key={kind}
              kind={kind}
              currentUrl={currentUrl}
              onEdit={(row) => maintenance.edit(kind, row)}
              onDelete={(row) => maintenance.remove(kind, row)}
            />
          </div>
        </>
      )}
      {maintenance.overlays}
    </div>
  );
}
