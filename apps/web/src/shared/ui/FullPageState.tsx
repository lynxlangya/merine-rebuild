import { Button, Result, Spin } from 'antd';
import styles from './FullPageState.module.css';

/** 整页加载：身份恢复、首次进入等整屏等待场景共用。 */
export function FullPageLoading({ description = '正在加载' }: { description?: string }) {
  return (
    <div className={styles.wrap} role="status" aria-live="polite">
      <Spin size="large" />
      <p className={styles.text}>{description}</p>
    </div>
  );
}

/** 整页失败：保留可重试入口，不把服务故障显示成“未登录”。 */
export function FullPageError({
  title,
  description,
  onRetry,
}: {
  title: string;
  description: string;
  onRetry?: () => void;
}) {
  return (
    <div className={styles.wrap}>
      <Result
        status="warning"
        title={title}
        subTitle={description}
        extra={
          onRetry ? (
            <Button type="primary" onClick={onRetry}>
              重新加载
            </Button>
          ) : undefined
        }
      />
    </div>
  );
}
