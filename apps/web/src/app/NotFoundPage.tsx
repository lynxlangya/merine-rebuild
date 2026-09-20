import { Button, Result } from 'antd';
import { useNavigate } from 'react-router';
import { defaultEntryPath } from '../features/auth/redirect';

export function NotFoundPage() {
  const navigate = useNavigate();
  return (
    <div style={{ padding: 'var(--sp-6)' }}>
      <Result
        status="404"
        title="页面不存在"
        subTitle="地址可能已变更，或该入口尚未开放。"
        extra={
          <Button type="primary" onClick={() => void navigate(defaultEntryPath, { replace: true })}>
            返回首页
          </Button>
        }
      />
    </div>
  );
}
