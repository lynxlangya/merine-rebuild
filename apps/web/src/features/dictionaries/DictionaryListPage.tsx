import { PlusOutlined, ReloadOutlined } from '@ant-design/icons';
import type { DictionaryItemView, DictionaryListItem, DictionaryView } from '@merine/api-contract';
import { Button, Empty, Result, Skeleton, Table, Tag } from 'antd';
import type { TableColumnsType } from 'antd';
import { useQueryClient } from '@tanstack/react-query';
import { useEffect, useRef, useState } from 'react';
import { errorText, isForbiddenError } from '../../shared/api-error';
import { PERMISSIONS, hasPermission } from '../../shared/permissions';
import { PageHeader } from '../../shared/ui/PageHeader';
import { useAuth } from '../auth/public';
import { DictTag, DICTIONARY_CODES, orderedItems, useDictionary } from './public';
import { DictionaryItemDrawer } from './components/DictionaryItemDrawer';
import { DictionaryTypeDrawer } from './components/DictionaryTypeDrawer';
import { dictionaryKeys, useDictionaryAdminListQuery, useDictionaryDetailQuery } from './queries';
import styles from './DictionaryListPage.module.css';

/**
 * 字典管理：左侧字典类型，右侧字典项，全部在线维护。
 *
 * 取值（dict_code / item_value）是稳定标识，创建后不可修改；字典类型与字典项都**只停用、不删除**：
 * 字典是全局参考数据，误删会让标签与选择项立刻退化，停用已经够表达"不再可选"，
 * 历史数据也仍能解析出标签。
 */
export function DictionaryListPage() {
  const queryClient = useQueryClient();
  const { state } = useAuth();
  const me = state.status === 'authenticated' ? state.user : null;
  const [permissionLost, setPermissionLost] = useState(false);
  const [selectedCode, setSelectedCode] = useState<string | null>(null);
  const [typeDrawer, setTypeDrawer] = useState<{
    open: boolean;
    target: DictionaryListItem | null;
  }>({
    open: false,
    target: null,
  });
  const [itemDrawer, setItemDrawer] = useState<{
    open: boolean;
    target: DictionaryItemView | null;
  }>({
    open: false,
    target: null,
  });
  const triggerRef = useRef<HTMLElement | null>(null);

  const canRead = !permissionLost && hasPermission(me?.permissionCodes, PERMISSIONS.dictRead);
  const canCreate = hasPermission(me?.permissionCodes, PERMISSIONS.dictCreate);
  const canUpdate = hasPermission(me?.permissionCodes, PERMISSIONS.dictUpdate);

  const list = useDictionaryAdminListQuery(canRead);
  const rows = list.data ?? [];
  const detail = useDictionaryDetailQuery(canRead ? selectedCode : null);
  const statusDictionary = useDictionary(DICTIONARY_CODES.status).data;

  const forbidden = permissionLost || isForbiddenError(list.error) || !canRead;

  // 首次加载或选中项消失时，自动落到第一条
  useEffect(() => {
    if (rows.length === 0) return;
    if (!selectedCode || !rows.some((row) => row.code === selectedCode)) {
      setSelectedCode(rows[0].code);
    }
  }, [rows, selectedCode]);

  const refreshAll = async () => {
    await queryClient.invalidateQueries({ queryKey: dictionaryKeys.adminList });
    await queryClient.invalidateQueries({ queryKey: dictionaryKeys.all });
    if (selectedCode) {
      await queryClient.invalidateQueries({ queryKey: dictionaryKeys.adminDetail(selectedCode) });
    }
  };

  const openTypeDrawer = (target: DictionaryListItem | null, trigger: HTMLElement) => {
    triggerRef.current = trigger;
    setTypeDrawer({ open: true, target });
  };

  const openItemDrawer = (target: DictionaryItemView | null, trigger: HTMLElement) => {
    triggerRef.current = trigger;
    setItemDrawer({ open: true, target });
  };

  const handleDrawerClosed = () => {
    triggerRef.current?.focus({ preventScroll: true });
    triggerRef.current = null;
  };

  const typeColumns: TableColumnsType<DictionaryListItem> = [
    {
      title: '字典',
      dataIndex: 'name',
      render: (value: string, row) => (
        <span className={styles.typeCell}>
          <span className={styles.typeName}>{value}</span>
          <span className={styles.mono}>{row.code}</span>
        </span>
      ),
    },
    { title: '项数', dataIndex: 'itemCount', width: 64, align: 'right' },
    {
      title: '状态',
      dataIndex: 'status',
      width: 84,
      render: (value: string) => <DictTag dictionary={statusDictionary} value={value} />,
    },
  ];

  const itemColumns: TableColumnsType<DictionaryItemView> = [
    {
      title: '取值',
      dataIndex: 'value',
      width: 168,
      render: (value: string) => <span className={styles.mono}>{value}</span>,
    },
    { title: '标签', dataIndex: 'label', width: 160 },
    {
      title: '说明',
      dataIndex: 'description',
      render: (value: string | null) =>
        value ? (
          <span className={styles.muted}>{value}</span>
        ) : (
          <span className={styles.muted}>—</span>
        ),
    },
    { title: '排序', dataIndex: 'sortOrder', width: 72, align: 'right' },
    {
      title: '状态',
      dataIndex: 'status',
      width: 88,
      render: (value: string) => <DictTag dictionary={statusDictionary} value={value} />,
    },
    ...(canUpdate
      ? [
          {
            title: '操作',
            key: 'actions',
            align: 'right' as const,
            width: 88,
            render: (_value: unknown, item: DictionaryItemView) => (
              <Button
                type="text"
                size="small"
                onClick={(event) => openItemDrawer(item, event.currentTarget)}
              >
                编辑
              </Button>
            ),
          },
        ]
      : []),
  ];

  return (
    <div>
      <PageHeader
        demo={false}
        title="字典管理"
        description="字典类型与字典项都在这里维护，状态、菜单类型、单位层级三本枚举同样可以改；使用侧登录后一次加载并缓存。"
        actions={
          <>
            <Button
              size="middle"
              icon={<ReloadOutlined />}
              loading={list.isFetching || detail.isFetching}
              onClick={() => void refreshAll()}
            >
              刷新
            </Button>
            {canCreate && (
              <Button
                type="primary"
                icon={<PlusOutlined />}
                onClick={(event) => openTypeDrawer(null, event.currentTarget)}
              >
                新建字典
              </Button>
            )}
          </>
        }
      />

      <div className={styles.page}>
        {forbidden ? (
          <div className={styles.stateCard}>
            <Result
              status="403"
              title="没有管理字典的权限"
              subTitle="字典管理需要「字典管理 · 查看」权限。这里不显示任何字典数据，也不会因此退出登录。"
            />
          </div>
        ) : list.isError && rows.length === 0 ? (
          <div className={styles.stateCard}>
            <Result
              status="warning"
              title="字典列表加载失败"
              subTitle={errorText(list.error)}
              extra={
                <Button type="primary" onClick={() => void list.refetch()}>
                  重新加载
                </Button>
              }
            />
          </div>
        ) : (
          <div className={styles.layout}>
            <section className={styles.card} aria-label="字典类型">
              <div className={styles.cardHead}>
                <b>字典类型</b>
                <Tag className={styles.countTag}>{list.isPending ? '—' : rows.length}</Tag>
              </div>
              {list.isPending ? (
                <div className={styles.skeleton}>
                  <Skeleton active paragraph={{ rows: 6 }} />
                </div>
              ) : (
                <Table<DictionaryListItem>
                  rowKey="code"
                  size="small"
                  showHeader={false}
                  pagination={false}
                  dataSource={rows}
                  columns={typeColumns}
                  rowSelection={{
                    type: 'radio',
                    selectedRowKeys: selectedCode ? [selectedCode] : [],
                    onChange: (keys) => setSelectedCode(String(keys[0] ?? '')),
                  }}
                  onRow={(row) => ({ onClick: () => setSelectedCode(row.code) })}
                  locale={{
                    emptyText: (
                      <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="还没有字典类型" />
                    ),
                  }}
                />
              )}
            </section>

            <section className={styles.card} aria-label="字典项">
              {detail.isPending && selectedCode ? (
                <div className={styles.skeleton}>
                  <Skeleton active paragraph={{ rows: 8 }} />
                </div>
              ) : detail.data ? (
                <DictionaryDetail
                  dictionary={detail.data}
                  statusDictionary={statusDictionary}
                  canCreate={canCreate}
                  canUpdate={canUpdate}
                  onEdit={(trigger) => {
                    const row = rows.find((item) => item.code === detail.data?.code);
                    if (row) openTypeDrawer(row, trigger);
                  }}
                  onAddItem={(trigger) => openItemDrawer(null, trigger)}
                  columns={itemColumns}
                />
              ) : (
                <Empty description="从左侧选择一个字典" />
              )}
            </section>
          </div>
        )}

        <div className={styles.notice}>
          取值（如 <span className={styles.mono}>ENABLED</span>、
          <span className={styles.mono}>PAGE</span>
          ）是业务判断与历史数据里保存的东西，创建后不可改；能改的是标签、说明、排序与状态。
          字典类型与字典项都<b>只停用、不删除</b>
          ——停用后不再出现在选择项里，历史数据仍能解析出标签，
          业务判定始终按取值比较。这里的改动会让本次会话的字典缓存立刻失效重取，使用侧不需要重新登录。
        </div>
      </div>

      <DictionaryTypeDrawer
        open={typeDrawer.open}
        target={typeDrawer.target}
        onClose={() => setTypeDrawer((previous) => ({ ...previous, open: false }))}
        onClosed={handleDrawerClosed}
        onSaved={(saved) => {
          setTypeDrawer((previous) => ({ ...previous, open: false }));
          setSelectedCode(saved.code);
        }}
        onForbidden={() => setPermissionLost(true)}
      />

      <DictionaryItemDrawer
        open={itemDrawer.open}
        dictionaryCode={selectedCode ?? ''}
        target={itemDrawer.target}
        onClose={() => setItemDrawer((previous) => ({ ...previous, open: false }))}
        onClosed={handleDrawerClosed}
        onSaved={() => {
          setItemDrawer((previous) => ({ ...previous, open: false }));
        }}
        onForbidden={() => setPermissionLost(true)}
      />
    </div>
  );
}

/** 右侧详情：类型信息 + 字典项表格。 */
function DictionaryDetail({
  dictionary,
  statusDictionary,
  canCreate,
  canUpdate,
  onEdit,
  onAddItem,
  columns,
}: {
  dictionary: DictionaryView;
  statusDictionary: Parameters<typeof DictTag>[0]['dictionary'];
  canCreate: boolean;
  canUpdate: boolean;
  onEdit: (trigger: HTMLElement) => void;
  onAddItem: (trigger: HTMLElement) => void;
  columns: TableColumnsType<DictionaryItemView>;
}) {
  const items = orderedItems(dictionary);
  return (
    <>
      <div className={styles.detailHead}>
        <div className={styles.detailIdentity}>
          <div className={styles.detailTitle}>
            <h2 className={styles.detailName}>{dictionary.name}</h2>
            <DictTag dictionary={statusDictionary} value={dictionary.status} />
          </div>
          <span className={styles.mono}>{dictionary.code}</span>
          {dictionary.description && <p className={styles.detailDesc}>{dictionary.description}</p>}
        </div>
        <div className={styles.detailActions}>
          {canUpdate && (
            <Button size="small" onClick={(event) => onEdit(event.currentTarget)}>
              编辑字典
            </Button>
          )}
          {canCreate && (
            <Button
              size="small"
              type="primary"
              icon={<PlusOutlined />}
              onClick={(event) => onAddItem(event.currentTarget)}
            >
              新增字典项
            </Button>
          )}
        </div>
      </div>

      <div className={styles.tableWrap}>
        <Table<DictionaryItemView>
          rowKey="value"
          size="small"
          tableLayout="fixed"
          pagination={false}
          dataSource={items}
          columns={columns}
          locale={{
            emptyText: (
              <Empty
                image={Empty.PRESENTED_IMAGE_SIMPLE}
                description={
                  dictionary.status === 'DISABLED'
                    ? '该字典已停用，使用侧拿不到它的选项'
                    : '还没有字典项'
                }
              />
            ),
          }}
        />
      </div>
    </>
  );
}
