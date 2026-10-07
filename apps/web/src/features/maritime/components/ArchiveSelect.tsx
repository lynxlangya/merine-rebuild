import type { ArchiveOption } from '@merine/api-contract';
import { useQuery } from '@tanstack/react-query';
import { Alert, Button, Select } from 'antd';
import { useState } from 'react';
import { errorText } from '../../../shared/api-error';
import { fetchOptions } from '../api';

/** 可搜索、可翻页的真实候选项；既有停用关联以不可新增的保留选项显示。 */
export function ArchiveSelect({
  kind,
  value,
  onChange,
  stationId,
  current,
  disabled,
  id,
}: {
  kind: 'ports' | 'police-stations' | 'port-officers';
  value?: string | null;
  onChange?: (id?: string) => void;
  stationId?: string | null;
  current?: { id: string; name: string; status: string | null };
  disabled?: boolean;
  id?: string;
}) {
  const [keyword, setKeyword] = useState('');
  const [page, setPage] = useState(1);
  const [loaded, setLoaded] = useState<ArchiveOption[]>([]);
  const result = useQuery({
    queryKey: ['maritime', 'options', kind, keyword, stationId, page],
    queryFn: ({ signal }) =>
      fetchOptions(
        kind,
        {
          keyword,
          policeStationId: kind === 'port-officers' ? stationId || undefined : undefined,
          page,
          pageSize: 50,
        },
        signal,
      ),
    enabled: !disabled,
  });
  const items = [...loaded, ...(result.data?.items ?? [])];
  const unique = new Map(items.map((item) => [item.id, { value: item.id, label: item.name }]));
  if (current && current.id === value && !unique.has(current.id))
    unique.set(current.id, {
      value: current.id,
      label: current.name + (current.status === 'DISABLED' ? ' · 已停用，保留关联' : ''),
    });
  return (
    <Select
      id={id}
      showSearch={{
        filterOption: false,
        onSearch: (text) => {
          setKeyword(text);
          setPage(1);
          setLoaded([]);
        },
      }}
      allowClear
      placeholder={
        kind === 'port-officers' && disabled && !stationId ? '请先选择管辖派出所' : '请选择'
      }
      value={value || undefined}
      onChange={onChange}
      disabled={disabled}
      loading={result.isFetching}
      options={[...unique.values()]}
      popupRender={(menu) => (
        <>
          {menu}
          {result.isError && (
            <Alert
              type="error"
              title={errorText(result.error)}
              action={<Button onClick={() => void result.refetch()}>重试</Button>}
            />
          )}
          {(result.data?.total ?? 0) > page * 50 && (
            <Button
              type="text"
              block
              onClick={() => {
                setLoaded(items);
                setPage(page + 1);
              }}
            >
              加载更多
            </Button>
          )}
        </>
      )}
    />
  );
}
