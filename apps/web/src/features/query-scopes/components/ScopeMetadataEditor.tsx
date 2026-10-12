import { ReloadOutlined, SaveOutlined } from '@ant-design/icons';
import type { ScopeViewMeta } from '@merine/api-contract';
import { Button, Empty, Input, Table, Tag, Tooltip } from 'antd';
import { useState } from 'react';
import { type MetadataDraft, type ScopeColumnRow, effectiveDescription } from '../model';
import styles from '../QueryScopeDetailPage.module.css';

/** 单个视图的说明编辑区；字段元数据、权限与保存由页面管理。 */
export function ScopeMetadataEditor({
  view,
  meta,
  form,
  rows,
  inspected,
  canUpdate,
  canTest,
  busy,
  reading,
  saving,
  dirty,
  onChange,
  onRead,
  onSave,
  onReset,
}: {
  view: string;
  meta?: ScopeViewMeta;
  form: MetadataDraft;
  rows: ScopeColumnRow[];
  inspected: boolean;
  canUpdate: boolean;
  canTest: boolean;
  busy: boolean;
  reading: boolean;
  saving: boolean;
  dirty: boolean;
  onChange: (value: MetadataDraft) => void;
  onRead: () => void;
  onSave: () => void;
  onReset: () => void;
}) {
  const [search, setSearch] = useState('');
  const filtered = rows.filter((row) =>
    [row.name, row.comment, form.overrides[row.name]].some((text) =>
      text?.toLowerCase().includes(search.trim().toLowerCase()),
    ),
  );
  const columns = [
    {
      title: '字段',
      key: 'field',
      width: 210,
      render: (_value: unknown, row: ScopeColumnRow) => (
        <div className={styles.fieldIdentity}>
          <code>{row.name}</code>
          <span>
            {row.type ?? '类型未读取'}
            {row.nullable ? ' · 可空' : ''}
          </span>
        </div>
      ),
    },
    {
      title: '当前说明',
      key: 'effective',
      render: (_value: unknown, row: ScopeColumnRow) => {
        const effective = effectiveDescription({
          ...row,
          override: form.overrides[row.name] ?? '',
        });
        return (
          <div className={styles.effective}>
            <span>{effective.text}</span>
            {effective.source === '应用覆盖' && <Tag bordered={false}>自定义</Tag>}
          </div>
        );
      },
    },
    ...(canUpdate
      ? [
          {
            title: '自定义说明',
            key: 'override',
            width: 290,
            render: (_value: unknown, row: ScopeColumnRow) => (
              <Tooltip title={row.comment ? `默认：${row.comment}` : undefined}>
                <Input.TextArea
                  autoSize={{ minRows: 1, maxRows: 3 }}
                  maxLength={300}
                  aria-label={`${row.name}的自定义说明`}
                  value={form.overrides[row.name] ?? ''}
                  placeholder={row.comment ? '留空使用默认说明' : '填写字段说明'}
                  disabled={busy}
                  onChange={(event) =>
                    onChange({
                      ...form,
                      overrides: { ...form.overrides, [row.name]: event.target.value },
                    })
                  }
                />
              </Tooltip>
            ),
          },
        ]
      : []),
  ];

  return (
    <section className={styles.editor} aria-label="视图说明编辑">
      <header className={styles.editorHeader}>
        <div>
          <h2>{meta?.displayName?.trim() || view}</h2>
          <code className={styles.code}>{view}</code>
        </div>
        <div className={styles.editorActions}>
          {dirty && <span className={styles.unsaved}>未保存</span>}
          {dirty && canUpdate && (
            <Button type="text" disabled={busy} onClick={onReset}>
              还原
            </Button>
          )}
          {canUpdate && (
            <Button
              type="primary"
              icon={<SaveOutlined />}
              loading={saving}
              disabled={!dirty || busy}
              onClick={onSave}
            >
              保存说明
            </Button>
          )}
        </div>
      </header>
      <div className={styles.form}>
        <label className={styles.field}>
          <span>显示名称</span>
          <Input
            maxLength={80}
            value={form.displayName}
            placeholder={view}
            disabled={!canUpdate || busy}
            onChange={(event) => onChange({ ...form, displayName: event.target.value })}
          />
        </label>
        <label className={styles.field}>
          <span>用途与统计口径</span>
          <Input.TextArea
            autoSize={{ minRows: 3, maxRows: 8 }}
            maxLength={2000}
            showCount
            value={form.description}
            placeholder="统计对象、时间范围与计算口径"
            disabled={!canUpdate || busy}
            onChange={(event) => onChange({ ...form, description: event.target.value })}
          />
        </label>
      </div>
      <div className={styles.fieldToolbar}>
        <div className={styles.sectionTitle}>
          <h3>字段说明</h3>
          <span>{inspected ? `${rows.length} 个字段` : '已保存说明'}</span>
        </div>
        <Input.Search
          className={styles.fieldSearch}
          aria-label="搜索字段"
          placeholder="搜索字段或说明"
          allowClear
          value={search}
          onChange={(event) => setSearch(event.target.value)}
        />
        {canTest && (
          <Button
            size="small"
            icon={<ReloadOutlined />}
            loading={reading}
            disabled={busy}
            onClick={onRead}
          >
            {inspected ? '刷新字段' : '读取字段'}
          </Button>
        )}
      </div>
      {!inspected && (
        <p className={styles.minorHint}>
          {canTest
            ? '读取字段后可查看类型与数据库默认说明。'
            : '没有读取字段的权限，当前仅展示已保存说明。'}
        </p>
      )}
      {rows.length > 0 ? (
        <Table<ScopeColumnRow>
          className={styles.fieldTable}
          rowKey="name"
          size="small"
          tableLayout="fixed"
          scroll={{ x: canUpdate ? 850 : 560 }}
          pagination={false}
          columns={columns}
          dataSource={filtered}
          locale={{ emptyText: '没有匹配字段' }}
        />
      ) : (
        <div className={styles.empty}>
          <Empty
            image={Empty.PRESENTED_IMAGE_SIMPLE}
            description={inspected ? '没有字段信息' : '尚未读取字段'}
          />
        </div>
      )}
    </section>
  );
}
