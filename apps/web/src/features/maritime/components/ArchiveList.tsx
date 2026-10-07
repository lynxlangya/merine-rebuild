import { Button, Form, Input, Select, Space } from 'antd';
import { useEffect } from 'react';
import { useSearchParams } from 'react-router';
import { hasPermission } from '../../../shared/permissions';
import { useAuth } from '../../auth/public';
import { DICTIONARY_CODES, toDictOptions, useDictionary } from '../../dictionaries/public';
import type { ArchiveFilters } from '../api';
import { archives, positivePage, type ArchiveKind, type ArchiveRecord } from '../model';
import { useArchives } from '../queries';
import styles from '../Maritime.module.css';
import { ArchiveSelect } from './ArchiveSelect';
import { ArchiveTable } from './ArchiveTable';

export function ArchiveList({
  kind,
  currentUrl,
  fixed = {},
  pageKey = 'page',
  title,
  compact = false,
  onEdit,
  onDelete,
}: {
  kind: ArchiveKind;
  currentUrl: string;
  fixed?: ArchiveFilters;
  pageKey?: string;
  title?: string;
  compact?: boolean;
  onEdit: (row: ArchiveRecord) => void;
  onDelete: (row: ArchiveRecord) => void;
}) {
  const [params, setParams] = useSearchParams();
  const [form] = Form.useForm();
  const { state } = useAuth();
  const codes = state.status === 'authenticated' ? state.user.permissionCodes : [];
  const status = useDictionary(DICTIONARY_CODES.status);
  const filters: ArchiveFilters = compact
    ? { ...fixed, page: positivePage(params.get(pageKey)) }
    : {
        keyword: params.get('keyword') || undefined,
        region: params.get('region') || undefined,
        status: params.get('status') || undefined,
        portId: kind === 'wharfs' ? params.get('portId') || undefined : undefined,
        policeStationId: ['wharfs', 'port-officers'].includes(kind)
          ? params.get('policeStationId') || undefined
          : undefined,
        responsibleOfficerId:
          kind === 'wharfs' ? params.get('responsibleOfficerId') || undefined : undefined,
        inhabitationType:
          kind === 'islands' ? params.get('inhabitationType') || undefined : undefined,
        ...fixed,
        page: positivePage(params.get(pageKey)),
      };
  useEffect(() => {
    if (!compact)
      form.setFieldsValue({
        keyword: params.get('keyword') || '',
        region: params.get('region') || '',
        status: params.get('status') || undefined,
        portId: params.get('portId') || undefined,
        policeStationId: params.get('policeStationId') || undefined,
        responsibleOfficerId: params.get('responsibleOfficerId') || undefined,
      });
  }, [form, params, compact]);
  const query = useArchives(kind, filters, hasPermission(codes, archives[kind].read));
  function submit(values: Record<string, string | undefined>) {
    const next = new URLSearchParams(params);
    for (const key of [
      'keyword',
      'region',
      'status',
      'portId',
      'policeStationId',
      'responsibleOfficerId',
    ]) {
      if (values[key]) next.set(key, values[key]!);
      else next.delete(key);
    }
    next.delete(pageKey);
    setParams(next);
  }
  return (
    <div className={styles.list}>
      {!compact && (
        <div className={styles.panel}>
          <Form
            name="maritimeFilters"
            form={form}
            initialValues={filters}
            className={styles.filters}
            layout="vertical"
            onFinish={submit}
          >
            <Form.Item name="keyword" label="名称关键词">
              <Input allowClear placeholder="请输入" maxLength={120} />
            </Form.Item>
            <Form.Item name="region" label="所在区域">
              <Input allowClear placeholder="请输入" maxLength={120} />
            </Form.Item>
            <Form.Item name="status" label="状态">
              <Select allowClear placeholder="请选择" options={toDictOptions(status.data)} />
            </Form.Item>
            {kind === 'wharfs' && (
              <Form.Item name="portId" label="所属港口">
                <ArchiveSelect kind="ports" />
              </Form.Item>
            )}
            {['wharfs', 'port-officers'].includes(kind) && (
              <Form.Item name="policeStationId" label="派出所">
                <ArchiveSelect kind="police-stations" />
              </Form.Item>
            )}
            {kind === 'wharfs' && (
              <Form.Item name="responsibleOfficerId" label="责任民警">
                <ArchiveSelect kind="port-officers" />
              </Form.Item>
            )}
            <Form.Item>
              <Space>
                <Button type="primary" htmlType="submit">
                  查询
                </Button>
                <Button
                  onClick={() => {
                    form.resetFields();
                    submit({});
                  }}
                >
                  重置
                </Button>
              </Space>
            </Form.Item>
          </Form>
        </div>
      )}
      <ArchiveTable
        kind={kind}
        data={query.data}
        loading={query.isFetching}
        error={query.error}
        refetch={() => void query.refetch()}
        onPage={(page) => {
          const next = new URLSearchParams(params);
          next.set(pageKey, String(page));
          setParams(next);
        }}
        title={title}
        returnTo={currentUrl}
        onEdit={hasPermission(codes, archives[kind].update) ? onEdit : undefined}
        onDelete={hasPermission(codes, archives[kind].delete) ? onDelete : undefined}
      />
    </div>
  );
}
