import { Empty } from 'antd';
import { PageHeader } from '../../shared/ui/PageHeader';
import styles from './InformationFlowPage.module.css';

/**
 * 信息流转：业务协同下的信息上报、转办与流转留痕入口。
 *
 * 当前只有路由与空页面骨架：流转模型、接口与权限码都还没实现，
 * 因此这里不请求任何数据，也不做按钮级权限判断。
 */
export function InformationFlowPage() {
  return (
    <div>
      <PageHeader
        demo={false}
        title="信息流转"
        description="业务协同下的信息上报、转办与流转留痕。"
      />
      <div className={styles.page}>
        <div className={styles.placeholder}>
          <Empty
            image={Empty.PRESENTED_IMAGE_SIMPLE}
            description="页面骨架已就位，流转模型、接口与权限码待实现。"
          />
        </div>
      </div>
    </div>
  );
}
