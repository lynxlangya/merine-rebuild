import { useQuery } from '@tanstack/react-query';
import { Alert, Button, Empty } from 'antd';
import { useState } from 'react';
import { Link } from 'react-router';
import { errorText } from '../../../shared/api-error';
import { hasPermission, PERMISSIONS } from '../../../shared/permissions';
import { TablePager } from '../../../shared/ui/TablePager';
import { useAuth } from '../../auth/public';
import { fetchUnitPoliceStations } from '../api';
import { archiveHref } from '../model';
import { maritimeKeys } from '../queries';
import { useArchiveMaintenance } from './ArchiveMaintenance';
import styles from '../Maritime.module.css';

/** 单位详情中的直属派出所摘要；完整档案和维护按警务权限显示。 */
export function UnitPoliceStations({ unitCode }: { unitCode: string }) {
  const [page, setPage] = useState(1);
  const { state } = useAuth();
  const codes = state.status === 'authenticated' ? state.user.permissionCodes : [];
  const result = useQuery({
    queryKey: [...maritimeKeys.all, 'unit-stations', unitCode, page],
    queryFn: ({ signal }) => fetchUnitPoliceStations(unitCode, page, signal),
  });
  const maintenance = useArchiveMaintenance();
  return (
    <div className={styles.unitStations}>
      <div className={styles.toolbar}>
        <h2>
          所属派出所 <span className={styles.muted}>({result.data?.total ?? 0})</span>
        </h2>
        {hasPermission(codes, PERMISSIONS.maritimePoliceStationCreate) && (
          <Button size="small" onClick={() => maintenance.create('police-stations', { unitCode })}>
            新增派出所
          </Button>
        )}
      </div>
      {result.isError ? (
        <Alert
          type="error"
          title={errorText(result.error)}
          action={<Button onClick={() => void result.refetch()}>重试</Button>}
        />
      ) : result.isPending ? (
        <p>正在加载派出所</p>
      ) : result.data.items.length === 0 ? (
        <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无所属派出所" />
      ) : (
        <div className={styles.stations}>
          {result.data.items.map((station) => (
            <div key={station.id} className={styles.stationLink}>
              {hasPermission(codes, PERMISSIONS.maritimePolicingRead) ? (
                <Link to={archiveHref('police-stations', station.id)}>{station.name}</Link>
              ) : (
                station.name
              )}
              {station.status === 'DISABLED' && <small>已停用</small>}
            </div>
          ))}
        </div>
      )}
      {(result.data?.total ?? 0) > 0 && (
        <TablePager
          total={result.data!.total}
          page={page}
          pageSize={10}
          itemCount={result.data!.items.length}
          onPageChange={setPage}
        />
      )}
      {maintenance.overlays}
    </div>
  );
}
