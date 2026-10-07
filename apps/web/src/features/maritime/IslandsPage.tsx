import { Button, Result, Tabs } from 'antd';
import { useLocation, useSearchParams } from 'react-router';
import { hasPermission, PERMISSIONS } from '../../shared/permissions';
import { PageHeader } from '../../shared/ui/PageHeader';
import { useAuth } from '../auth/public';
import { DICTIONARY_CODES, toDictOptions, useDictionary } from '../dictionaries/public';
import { ArchiveDetail } from './components/ArchiveDetail';
import { ArchiveList } from './components/ArchiveList';
import { useArchiveMaintenance } from './components/ArchiveMaintenance';
import { safeReturn } from './model';
import styles from './Maritime.module.css';

export function IslandsPage() {
  const [params, setParams] = useSearchParams();
  const location = useLocation();
  const currentUrl = location.pathname + location.search;
  const id = params.get('id');
  const { state } = useAuth();
  const codes = state.status === 'authenticated' ? state.user.permissionCodes : [];
  const dictionary = useDictionary(DICTIONARY_CODES.islandInhabitation);
  const maintenance = useArchiveMaintenance((deletedKind, deletedId) => {
    if (id && deletedKind === 'islands' && id === deletedId)
      setParams(
        new URL(safeReturn(params.get('returnTo'), location.pathname), 'https://local.invalid')
          .searchParams,
      );
  });
  if (!hasPermission(codes, PERMISSIONS.maritimeIslandRead))
    return <Result status="403" title="没有查看海岛的权限" />;
  return (
    <div>
      {id ? (
        <div className={styles.page}>
          <ArchiveDetail
            kind="islands"
            id={id}
            currentUrl={currentUrl}
            returnTo={params.get('returnTo')}
            onEdit={
              hasPermission(codes, PERMISSIONS.maritimeIslandUpdate)
                ? (row) => maintenance.edit('islands', row)
                : undefined
            }
            onDelete={
              hasPermission(codes, PERMISSIONS.maritimeIslandDelete)
                ? (row) => maintenance.remove('islands', row)
                : undefined
            }
          />
        </div>
      ) : (
        <>
          <PageHeader
            title="海岛"
            demo={false}
            description="统一维护有人岛与无人岛的基础档案。"
            actions={
              hasPermission(codes, PERMISSIONS.maritimeIslandCreate) ? (
                <Button
                  type="primary"
                  onClick={() =>
                    maintenance.create('islands', {
                      inhabitationType: params.get('inhabitationType') || 'INHABITED',
                    })
                  }
                >
                  新建海岛
                </Button>
              ) : undefined
            }
          />
          <div className={styles.page}>
            <Tabs
              activeKey={params.get('inhabitationType') || 'ALL'}
              items={[
                { key: 'ALL', label: '全部海岛' },
                ...toDictOptions(dictionary.data).map(({ value, label }) => ({
                  key: value,
                  label,
                })),
              ]}
              onChange={(key) => {
                const value = key === 'ALL' ? '' : key;
                const next = new URLSearchParams(params);
                if (value) next.set('inhabitationType', value);
                else next.delete('inhabitationType');
                next.delete('page');
                setParams(next);
              }}
            />
            <ArchiveList
              kind="islands"
              currentUrl={currentUrl}
              onEdit={(row) => maintenance.edit('islands', row)}
              onDelete={(row) => maintenance.remove('islands', row)}
            />
          </div>
        </>
      )}
      {maintenance.overlays}
    </div>
  );
}
