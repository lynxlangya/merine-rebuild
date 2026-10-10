import { Table } from 'antd';
import type { MessagePart } from '@merine/api-contract';
import { NoticePart } from './SimpleParts';
import styles from './AgentParts.module.css';

const MAX_CELL_CHARS = 200;

function cell(value: unknown): string {
  if (value === null || value === undefined) return '—';
  const text = String(value);
  return text.length > MAX_CELL_CHARS ? `${text.slice(0, MAX_CELL_CHARS)}…` : text;
}

/** 表格部件：列定义来自后端，行与列必须等长；截断逐格标注而不是静默丢弃。 */
export function TablePart({ part }: { part: Extract<MessagePart, { type: 'TABLE' }> }) {
  const columns = (part.columns ?? []).filter(
    (column) => column && typeof column.title === 'string',
  );
  const rows = (part.rows ?? []).filter((row): row is string[] => Array.isArray(row));
  if (columns.length === 0) {
    return <NoticePart level="WARNING" text="表格缺少列定义，未渲染" />;
  }
  if (rows.some((row) => row.length !== columns.length)) {
    return <NoticePart level="WARNING" text="表格行列不一致，未渲染" />;
  }
  const dataSource = rows.map((row, index) => ({
    key: index,
    cells: row.map((value) => cell(value)),
  }));
  const header = columns.map((column, index) => ({
    key: column.key ?? String(index),
    title: column.unit ? `${column.title}（${column.unit}）` : column.title,
    dataIndex: ['cells', index],
    align: column.numeric ? ('right' as const) : ('left' as const),
  }));
  return (
    <div className={styles.tableWrap}>
      {part.title && <div className={styles.partTitle}>{part.title}</div>}
      <Table
        size="small"
        columns={header}
        dataSource={dataSource}
        pagination={false}
        scroll={{ x: 'max-content' }}
        onHeaderRow={() => ({ style: { whiteSpace: 'nowrap' } })}
      />
      {part.note && <div className={styles.partNote}>{part.note}</div>}
    </div>
  );
}
