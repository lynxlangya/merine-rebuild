import { Button, Input, Select } from 'antd';
import { useState } from 'react';
import { EMPTY_ROLE_FILTERS, type RoleFilters, type RoleStatusFilter } from '../model';
import styles from './RoleSearchForm.module.css';

const STATUS_OPTIONS: { value: Exclude<RoleStatusFilter, ''>; label: string }[] = [
  { value: 'ENABLED', label: '启用' },
  { value: 'DISABLED', label: '已停用' },
];

/**
 * 角色查询区：正在输入的条件与已提交的查询分开，只有点「查询」或回车才提交。
 * 角色数量不多，条件只有关键字与状态两项，不额外做「展开更多」。
 */
export function RoleSearchForm({
  onSearch,
  onReset,
}: {
  onSearch: (filters: RoleFilters) => void;
  onReset: () => void;
}) {
  const [filters, setFilters] = useState<RoleFilters>(EMPTY_ROLE_FILTERS);

  return (
    <form
      className={styles.bar}
      role="search"
      onSubmit={(event) => {
        event.preventDefault();
        onSearch(filters);
      }}
    >
      <div className={styles.field}>
        <label className={styles.label} htmlFor="role-keyword">
          角色名称 / 编码
        </label>
        <Input
          id="role-keyword"
          value={filters.keyword}
          placeholder="支持名称或编码关键字"
          autoComplete="off"
          onChange={(event) =>
            setFilters((previous) => ({ ...previous, keyword: event.target.value }))
          }
        />
      </div>

      <div className={styles.field}>
        <label className={styles.label} htmlFor="role-status">
          状态
        </label>
        <Select
          id="role-status"
          className={styles.select}
          value={filters.status}
          options={[{ value: '', label: '全部状态' }, ...STATUS_OPTIONS]}
          onChange={(value: RoleStatusFilter) =>
            setFilters((previous) => ({ ...previous, status: value }))
          }
        />
      </div>

      <div className={styles.actions}>
        <Button type="primary" htmlType="submit">
          查询
        </Button>
        <Button
          onClick={() => {
            setFilters(EMPTY_ROLE_FILTERS);
            onReset();
          }}
        >
          重置
        </Button>
      </div>
    </form>
  );
}
