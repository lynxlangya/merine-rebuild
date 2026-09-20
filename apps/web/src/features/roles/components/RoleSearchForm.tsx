import { Button, Input } from 'antd';
import { useState } from 'react';
import { DICTIONARY_CODES, DictSelect, useDictionary } from '../../dictionaries/public';
import { EMPTY_ROLE_FILTERS, type RoleFilters, type RoleStatusFilter } from '../model';
import styles from './RoleSearchForm.module.css';

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
  const statusDictionary = useDictionary(DICTIONARY_CODES.status);

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
        <DictSelect
          id="role-status"
          className={styles.select}
          dictionary={statusDictionary.data}
          loading={statusDictionary.isPending}
          value={filters.status}
          includeAllLabel="全部状态"
          onChange={(value) =>
            setFilters((previous) => ({ ...previous, status: value as RoleStatusFilter }))
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
