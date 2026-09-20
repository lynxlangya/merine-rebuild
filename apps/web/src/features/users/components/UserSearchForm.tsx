import { Button, Input, Select } from 'antd';
import { useState } from 'react';
import { UnitTreeSelect, useUnitOptionsQuery } from '../../units/public';
import { useRoleOptionsQuery } from '../../roles/public';
import { DICTIONARY_CODES, DictSelect, useDictionary } from '../../dictionaries/public';
import { EMPTY_USER_FILTERS, type UserFilters, type UserStatusFilter } from '../model';
import styles from './UserSearchForm.module.css';

/**
 * 用户查询区：正在输入的条件与已提交的查询分开，只有点「查询」或回车才提交。
 * 「展开更多条件」只控制启用状态字段的显隐，已填的值保留并继续参与查询，
 * 收起时在按钮上标出生效的条件数，避免隐藏条件悄悄改变结果。
 */
export function UserSearchForm({
  onSearch,
  onReset,
}: {
  onSearch: (filters: UserFilters) => void;
  onReset: () => void;
}) {
  const units = useUnitOptionsQuery();
  const roles = useRoleOptionsQuery();
  const statusDictionary = useDictionary(DICTIONARY_CODES.status);
  const [filters, setFilters] = useState<UserFilters>(EMPTY_USER_FILTERS);
  const [expanded, setExpanded] = useState(false);

  const patch = (part: Partial<UserFilters>) => {
    setFilters((previous) => ({ ...previous, ...part }));
  };

  const optionsFailed = units.isError || roles.isError;
  const hiddenActive = !expanded && filters.status !== '' ? 1 : 0;
  const moreLabel = expanded
    ? '收起更多条件'
    : hiddenActive > 0
      ? `展开更多条件 · ${hiddenActive} 项生效`
      : '展开更多条件';

  return (
    <form
      className={styles.bar}
      data-expanded={expanded}
      role="search"
      onSubmit={(event) => {
        event.preventDefault();
        onSearch(filters);
      }}
    >
      <div className={styles.field}>
        <label className={styles.label} htmlFor="user-keyword">
          姓名 / 账号
        </label>
        <Input
          id="user-keyword"
          value={filters.keyword}
          placeholder="支持姓名或账号关键字"
          autoComplete="off"
          onChange={(event) => patch({ keyword: event.target.value })}
        />
      </div>

      <div className={styles.field}>
        <label className={styles.label} htmlFor="user-unit">
          所属单位
        </label>
        <UnitTreeSelect
          id="user-unit"
          className={styles.select}
          value={filters.unitCode}
          loading={units.isPending}
          allowClear
          placeholder="全部单位"
          onChange={(value) => patch({ unitCode: value ?? '' })}
        />
      </div>

      <div className={styles.field}>
        <label className={styles.label} htmlFor="user-role">
          角色
        </label>
        <Select
          id="user-role"
          className={styles.select}
          value={filters.roleCode}
          loading={roles.isPending}
          options={[
            { value: '', label: roles.isError ? '全部角色（选项加载失败）' : '全部角色' },
            ...(roles.data ?? []).map((role) => ({ value: role.code, label: role.name })),
          ]}
          onChange={(value: string) => patch({ roleCode: value })}
        />
      </div>

      <div className={`${styles.field} ${styles.more}`}>
        <label className={styles.label} htmlFor="user-status">
          启用状态
        </label>
        <DictSelect
          id="user-status"
          className={styles.select}
          dictionary={statusDictionary.data}
          loading={statusDictionary.isPending}
          value={filters.status}
          includeAllLabel="全部状态"
          onChange={(value) => patch({ status: value as UserStatusFilter })}
        />
      </div>

      <div className={styles.actions}>
        <Button type="primary" htmlType="submit">
          查询
        </Button>
        <Button
          onClick={() => {
            setFilters(EMPTY_USER_FILTERS);
            setExpanded(false);
            onReset();
          }}
        >
          重置
        </Button>
        <Button
          type="text"
          aria-expanded={expanded}
          onClick={() => setExpanded((previous) => !previous)}
        >
          {moreLabel}
        </Button>
      </div>

      {optionsFailed && (
        <p className={styles.optionError} role="alert">
          单位或角色选项加载失败，按单位/角色筛选会缺少可选项。
          <Button
            type="link"
            size="small"
            onClick={() => {
              if (units.isError) void units.refetch();
              if (roles.isError) void roles.refetch();
            }}
          >
            重新加载
          </Button>
        </p>
      )}
    </form>
  );
}
