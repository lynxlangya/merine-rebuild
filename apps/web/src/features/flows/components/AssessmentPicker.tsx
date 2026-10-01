import { useEffect, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Alert, Button, Select, Space } from 'antd';
import type { Assessment } from '@merine/api-contract';
import { assessmentKeys, fetchAssessments } from '../assessmentApi';
import { formatFlowTime } from '../model';

/** 只分页本单位研判；翻页时仍保留已选记录的标签。 */
export function AssessmentPicker({
  topicId,
  userId,
  value,
  onChange,
  onPick,
}: {
  topicId: string;
  userId?: string;
  value?: string;
  onChange?: (value: string) => void;
  onPick?: (record: Assessment) => void;
}) {
  const [page, setPage] = useState(1),
    [selected, setSelected] = useState<Assessment>();
  const query = useQuery({
    queryKey: assessmentKeys.list(userId, topicId, page),
    queryFn: ({ signal }) => fetchAssessments(topicId, page, signal),
  });
  useEffect(() => {
    if (!value && query.data?.items[0]) {
      const a = query.data.items[0];
      setSelected(a);
      onChange?.(a.id);
      onPick?.(a);
    }
  }, [value, query.data, onChange, onPick]);
  useEffect(() => {
    const current = query.data?.items.find((a) => a.id === value);
    if (current) setSelected(current);
  }, [value, query.data]);
  const options = [...(query.data?.items ?? [])];
  if (selected && !options.some((a) => a.id === selected.id)) options.unshift(selected);
  return (
    <Space orientation="vertical" style={{ width: '100%' }}>
      {query.error && (
        <Alert
          type="error"
          title="研判加载失败"
          action={<Button onClick={() => void query.refetch()}>重试</Button>}
        />
      )}
      <Select
        value={value}
        loading={query.isFetching}
        style={{ width: '100%' }}
        placeholder="选择本单位研判"
        options={options.map((a) => ({
          value: a.id,
          label: `${formatFlowTime(a.createdAt)} · ${a.userName}`,
        }))}
        onChange={(id) => {
          const a = options.find((a) => a.id === id);
          if (a) {
            setSelected(a);
            onChange?.(id);
            onPick?.(a);
          }
        }}
      />
      {(query.data?.total ?? 0) > 20 && (
        <Space>
          <Button
            size="small"
            disabled={page === 1 || query.isFetching}
            onClick={() => setPage(page - 1)}
          >
            更新的研判
          </Button>
          <span>第 {page} 页</span>
          <Button
            size="small"
            disabled={page * 20 >= (query.data?.total ?? 0) || query.isFetching}
            onClick={() => setPage(page + 1)}
          >
            更早的研判
          </Button>
        </Space>
      )}
      {query.data?.total === 0 && <span>暂无本单位研判，请先记录研判。</span>}
    </Space>
  );
}
