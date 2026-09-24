import { Empty } from 'antd';
import { PageHeader } from '../../shared/ui/PageHeader';
import styles from './TaskHandlingPage.module.css';

/**
 * 任务处置：业务协同下的任务受理、派发与处置入口。
 *
 * 当前只有路由与空页面骨架：任务模型、接口与权限码都还没实现，
 * 因此这里不请求任何数据，也不做按钮级权限判断。
 */
export function TaskHandlingPage() {
  return (
    <div>
      <PageHeader demo={false} title="任务处置" description="业务协同下的任务受理、派发与处置。" />
      <div className={styles.page}>
        <div className={styles.placeholder}>
          <Empty
            image={Empty.PRESENTED_IMAGE_SIMPLE}
            description="页面骨架已就位，任务模型、接口与权限码待实现。"
          />
        </div>
      </div>
    </div>
  );
}
