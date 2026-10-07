import type { OfficerUserOption } from '@merine/api-contract';
import { useQuery } from '@tanstack/react-query';
import { Alert, Button, Select } from 'antd';
import { useState } from 'react';
import { errorText } from '../../../shared/api-error';
import { fetchOfficerUsers } from '../api';

/** 单位与重复关联限制由服务器执行；搜索、翻页保留请求隔离。 */
export function OfficerUserSelect({
  stationId,
  value,
  onChange,
  id,
}: {
  stationId?: string | null;
  value?: string;
  onChange?: (id?: string) => void;
  id?: string;
}) {
  const [keyword, setKeyword] = useState('');
  const [page, setPage] = useState(1);
  const [loaded, setLoaded] = useState<OfficerUserOption[]>([]);
  const result = useQuery({
    queryKey: ['maritime', 'officer-users', stationId, keyword, page],
    queryFn: ({ signal }) => fetchOfficerUsers(stationId!, keyword, page, signal),
    enabled: Boolean(stationId),
  });
  const items = [...loaded, ...(result.data?.items ?? [])];
  return (
    <Select
      id={id}
      value={value}
      onChange={onChange}
      disabled={stationId ? undefined : true}
      allowClear
      loading={result.isFetching}
      placeholder={stationId ? '请选择民警' : '请先选择所属派出所'}
      showSearch={{
        filterOption: false,
        onSearch: (text) => {
          setKeyword(text);
          setPage(1);
          setLoaded([]);
        },
      }}
      options={[
        ...new Map(
          items.map((item) => [
            item.id,
            { value: item.id, label: `${item.name} · ${item.loginName}` },
          ]),
        ).values(),
      ]}
      notFoundContent={
        !stationId
          ? '请先选择所属派出所'
          : result.isPending
            ? '正在加载'
            : result.isError
              ? '加载失败'
              : '暂无可添加用户，请先在用户管理维护同单位账号'
      }
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
              disabled={result.isFetching}
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
