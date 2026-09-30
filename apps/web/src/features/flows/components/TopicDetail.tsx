import { Button, Collapse, Space, Tag, Tooltip } from 'antd';
import type {
  IntelligenceAction,
  IntelligenceDetail,
  IntelligenceReceipt,
} from '@merine/api-contract';
import { formatFlowTime, receiptPath } from '../model';
import { flowLabel, flowOperation } from '../actions';
import type { FlowOperation } from '../actions';
import styles from '../InformationFlowPage.module.css';

function Actions({
  actions,
  receipt,
  onAction,
  busy,
  published = false,
}: {
  actions: IntelligenceAction[];
  receipt?: IntelligenceReceipt;
  onAction: (action: FlowOperation) => void;
  busy: boolean;
  published?: boolean;
}) {
  const visible = actions.flatMap((action) => {
    const operation = flowOperation(action.code, receipt);
    return operation ? [{ action, operation }] : [];
  });
  if (visible.length === 0) return null;
  return (
    <div className={styles.actions}>
      <Space wrap>
        {visible.map(({ action, operation }) => (
          <Tooltip key={action.code} title={action.reason}>
            <span>
              <Button
                disabled={!action.enabled || busy}
                type={
                  action.enabled && (action.code === 'send' || action.code === 'sign')
                    ? 'primary'
                    : 'default'
                }
                title={action.reason ?? undefined}
                onClick={() => onAction(operation)}
              >
                {flowLabel(operation.code, published)}
              </Button>
            </span>
          </Tooltip>
        ))}
      </Space>
    </div>
  );
}
function ReceiptContent({
  receipt,
  all,
  onAction,
  busy,
}: {
  receipt: IntelligenceReceipt;
  all: IntelligenceReceipt[];
  onAction: (action: FlowOperation) => void;
  busy: boolean;
}) {
  return (
    <div className={styles.receiptContent}>
      {receipt.parentReceiptId && <p className={styles.meta}>{receiptPath(receipt, all)}</p>}
      <div className={styles.meta}>
        {receipt.senderName} · {formatFlowTime(receipt.sentAt)} ·{' '}
        {receipt.signedAt
          ? `${receipt.signedByName} 于 ${formatFlowTime(receipt.signedAt)} 签收`
          : '待单位签收'}
      </div>
      {receipt.note && <p className={styles.body}>{receipt.note}</p>}
      {receipt.mine && (
        <Actions
          actions={receipt.allowedActions}
          receipt={receipt}
          onAction={onAction}
          busy={busy}
        />
      )}
      {receipt.feedbacks.map((f) => (
        <div className={styles.feedback} key={f.id}>
          <div className={styles.meta}>
            {f.unitName} · {f.userName} · {formatFlowTime(f.createdAt)}
          </div>
          <p className={styles.body}>{f.body}</p>
        </div>
      ))}
    </div>
  );
}
export function TopicDetail({
  detail,
  onAction,
  busy,
}: {
  detail: IntelligenceDetail;
  onAction: (action: FlowOperation) => void;
  busy: boolean;
}) {
  const mine = detail.receipts.filter((r) => r.mine);
  const history = detail.receipts.filter((r) => !r.mine);
  return (
    <div className={styles.detail}>
      <section className={styles.panel}>
        <div className={styles.sectionHead}>
          <h2>原始情报</h2>
          <Space size={4}>
            {detail.sourceMine && <Tag>范围 {detail.scopeUnitCodes.length} 个单位</Tag>}
            <Tag color={detail.status === 'DRAFT' ? 'default' : 'blue'}>
              {detail.status === 'DRAFT' ? '草稿' : '已发出'}
            </Tag>
          </Space>
        </div>
        <div className={styles.meta}>
          {detail.topicNo} · {detail.sourceUnitName} · {detail.sourceUserName} · 创建于{' '}
          {formatFlowTime(detail.createdAt)}
          {detail.publishedAt ? ` · 发出于 ${formatFlowTime(detail.publishedAt)}` : ''}
        </div>
        <p className={styles.body}>{detail.body}</p>
        <Actions
          actions={detail.allowedActions}
          onAction={onAction}
          busy={busy}
          published={detail.status === 'PUBLISHED'}
        />
      </section>
      {detail.supplements.length > 0 && (
        <section className={styles.panel}>
          <h2>源头补充与更正</h2>
          {detail.supplements.map((s) => (
            <div className={styles.feedback} key={s.id}>
              <div className={styles.meta}>
                <Tag color={s.kind === 'CORRECTION' ? 'orange' : 'blue'}>
                  {s.kind === 'CORRECTION' ? '更正' : '补充'}
                </Tag>
                {s.unitName} · {s.userName} · {formatFlowTime(s.createdAt)}
              </div>
              <p className={styles.body}>{s.body}</p>
            </div>
          ))}
        </section>
      )}
      {mine.length > 0 && (
        <section className={styles.panel}>
          <h2>接收回执</h2>
          {mine.map((r, index) => (
            <article className={styles.receipt} key={r.id}>
              <div className={styles.sectionHead}>
                <h3>
                  第 {index + 1} 次送达 · {r.fromUnitName} → {r.toUnitName}
                </h3>
                <Tag color={r.signedAt ? 'green' : 'orange'}>
                  {r.signedAt ? '已签收' : '待签收'}
                </Tag>
              </div>
              <ReceiptContent receipt={r} all={detail.receipts} onAction={onAction} busy={busy} />
            </article>
          ))}
        </section>
      )}
      {history.length > 0 && (
        <section className={styles.panel}>
          <h2>发送历史与反馈</h2>
          <Collapse
            items={history.map((r) => ({
              key: r.id,
              label: (
                <Space wrap>
                  {r.fromUnitName} → {r.toUnitName}
                  <Tag>{r.signedAt ? '已签收' : '待签收'}</Tag>
                  <span className={styles.meta}>
                    {formatFlowTime(r.sentAt)}
                    {r.feedbacks.length ? ` · ${r.feedbacks.length} 条反馈` : ''}
                  </span>
                </Space>
              ),
              children: (
                <ReceiptContent receipt={r} all={detail.receipts} onAction={onAction} busy={busy} />
              ),
            }))}
          />
        </section>
      )}
    </div>
  );
}
