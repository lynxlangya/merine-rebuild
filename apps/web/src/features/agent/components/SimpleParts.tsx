import { CloseCircleOutlined, CheckCircleOutlined, LoadingOutlined } from '@ant-design/icons';
import { Alert } from 'antd';
import type { MessagePart } from '@merine/api-contract';
import styles from './AgentParts.module.css';

/** 可观察步骤：只展示服务端给出的动作摘要，不出现模型推理内容。 */
export function StepsPart({ items }: { items: Extract<MessagePart, { type: 'STEPS' }>['items'] }) {
  const steps = (items ?? []).filter((item) => item && typeof item.text === 'string');
  if (steps.length === 0) return null;
  return (
    <ol className={styles.steps}>
      {steps.map((step, index) => (
        <li className={styles.step} key={`${step.text}-${index}`}>
          {step.status === 'RUNNING' ? (
            <LoadingOutlined />
          ) : step.status === 'FAILED' ? (
            <CloseCircleOutlined className={styles.stepFailed} />
          ) : (
            <CheckCircleOutlined className={styles.stepDone} />
          )}
          <span>{step.text}</span>
        </li>
      ))}
    </ol>
  );
}

export function NoticePart({
  level,
  text,
  code,
}: {
  level: 'INFO' | 'WARNING' | 'ERROR';
  text: string;
  code?: string;
}) {
  return (
    <Alert
      className={styles.notice}
      type={level === 'ERROR' ? 'error' : level === 'WARNING' ? 'warning' : 'info'}
      showIcon
      message={text}
      description={code ? `代码：${code}` : undefined}
    />
  );
}

export function UnknownPart({ rawType }: { rawType: string }) {
  return (
    <NoticePart level="INFO" text={`收到当前版本无法展示的内容（${rawType}），请升级前端版本。`} />
  );
}
