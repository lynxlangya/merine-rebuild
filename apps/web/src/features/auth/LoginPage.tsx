import {
  EyeInvisibleOutlined,
  EyeTwoTone,
  LockOutlined,
  MoonOutlined,
  SunOutlined,
} from '@ant-design/icons';
import { Alert, Button, Checkbox, Form, Input, Segmented, Tooltip } from 'antd';
import { useState } from 'react';
import { Navigate, useNavigate, useSearchParams } from 'react-router';
import { ApiError } from '../../shared/http';
import { validatePasswordLength } from '../../shared/password';
import { useThemeMode } from '../../app/theme/ThemeProvider';
import { useAuth } from './AuthProvider';
import { safeRedirectTarget } from './redirect';
import styles from './LoginPage.module.css';

interface LoginFormValues {
  loginName: string;
  password: string;
  rememberMe: boolean;
}

/**
 * 本地开发预填的演示账号。只填账号不填密码——密码不进前端产物。
 * 生产构建里为空字符串，不会带出任何预置账号。
 */
const PREFILLED_LOGIN_NAME = import.meta.env.DEV ? 'admin' : '';

interface LoginFailure {
  /** danger 用于凭证与账号问题；warning 用于服务不可用，两者动作不同。 */
  kind: 'danger' | 'warning';
  title: string;
  description: string;
  requestId?: string;
}

function describeFailure(error: unknown): LoginFailure {
  if (error instanceof ApiError) {
    if (error.code === 'ACCOUNT_DISABLED') {
      return {
        kind: 'danger',
        title: '该账号已停用',
        description: '请联系单位系统管理员处理。停用期间该账号此前的登录状态也已失效。',
        requestId: error.requestId,
      };
    }
    if (error.status === 401 || error.code === 'INVALID_CREDENTIALS') {
      return {
        kind: 'danger',
        title: '账号或密码不正确',
        description: '请确认账号与密码后重试。密码区分大小写。',
        requestId: error.requestId,
      };
    }
    if (error.status === 429) {
      return {
        kind: 'danger',
        title: '登录尝试过于频繁',
        description: '请稍后再试，或联系单位系统管理员。',
        requestId: error.requestId,
      };
    }
    if (error.status === 403) {
      return {
        kind: 'danger',
        title: '当前账号不能登录本系统',
        description: '请联系单位系统管理员确认账号状态与角色。',
        requestId: error.requestId,
      };
    }
    return {
      kind: 'warning',
      title: '登录服务暂时不可用',
      description: `${error.message}。请稍后重试。`,
      requestId: error.requestId,
    };
  }
  return {
    kind: 'warning',
    title: '登录服务暂时不可用',
    description: '无法连接服务。请检查本地开发环境是否已启动，稍后重试。',
  };
}

export function LoginPage() {
  const { state, signIn } = useAuth();
  const { mode, setMode } = useThemeMode();
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const [form] = Form.useForm<LoginFormValues>();
  const [failure, setFailure] = useState<LoginFailure | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const redirect = safeRedirectTarget(searchParams.get('redirect'));

  if (state.status === 'authenticated') {
    return <Navigate to={redirect} replace />;
  }

  const submit = async (values: LoginFormValues) => {
    setFailure(null);
    setSubmitting(true);
    try {
      await signIn({
        loginName: values.loginName.trim(),
        password: values.password,
        rememberMe: values.rememberMe,
      });
      void navigate(redirect, { replace: true });
    } catch (error) {
      const described = describeFailure(error);
      setFailure(described);
      if (error instanceof ApiError && error.fieldErrors.length > 0) {
        // 只回填表单确实拥有的字段，未知字段名不能直接塞进 setFields
        const known: (keyof LoginFormValues)[] = ['loginName', 'password', 'rememberMe'];
        const fields = error.fieldErrors
          .filter((item) => known.includes(item.field as keyof LoginFormValues))
          .map((item) => ({ name: item.field as keyof LoginFormValues, errors: [item.message] }));
        if (fields.length > 0) form.setFields(fields);
      }
      // 凭证错误后清空密码，账号保留，便于直接改正
      if (described.kind === 'danger') form.setFieldValue('password', '');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className={styles.auth}>
      <a className={styles.skip} href="#loginForm">
        跳到登录表单
      </a>

      <aside className={styles.brand} aria-hidden="true">
        <img className={styles.coast} src="/login-harbor.jpg" alt="" fetchPriority="high" />
      </aside>

      <main className={styles.main}>
        <div className={styles.topbar}>
          <Segmented
            aria-label="主题模式"
            value={mode}
            onChange={(value) => setMode(value as 'light' | 'dark')}
            options={[
              { value: 'light', icon: <SunOutlined />, label: '亮色' },
              { value: 'dark', icon: <MoonOutlined />, label: '暗黑' },
            ]}
          />
        </div>

        <div className={styles.center}>
          <header className={styles.brandHead}>
            <img className={styles.brandMark} src="/brand-mark.svg" alt="" />
            <h1 className={styles.brandName}>海防研判工作台</h1>
          </header>
          <Form<LoginFormValues>
            id="loginForm"
            form={form}
            className={styles.card}
            layout="vertical"
            requiredMark
            initialValues={{
              loginName: PREFILLED_LOGIN_NAME,
              password: '',
              rememberMe: true,
            }}
            onFinish={(values) => void submit(values)}
            disabled={submitting}
            aria-busy={submitting}
          >
            <div>
              <h2 className={styles.title}>登录</h2>
              <p className={styles.desc}>
                使用单位分配的账号登录。登录后按当前角色与数据范围加载可见内容，不显示其他单位的会话与资料。
              </p>
            </div>

            {failure && (
              <Alert
                role="alert"
                type={failure.kind === 'danger' ? 'error' : 'warning'}
                showIcon
                title={failure.title}
                description={
                  <>
                    {failure.description}
                    {failure.requestId && (
                      <span className={`${styles.requestId} mono`}>
                        请求编号：{failure.requestId}
                      </span>
                    )}
                  </>
                }
              />
            )}

            <Form.Item
              name="loginName"
              label="账号"
              extra={
                PREFILLED_LOGIN_NAME
                  ? '本地开发已预填演示账号；密码由 ./scripts/dev.sh seed 设定。'
                  : undefined
              }
              rules={[
                { required: true, whitespace: true, message: '请输入账号' },
                { max: 64, message: '账号最多 64 个字符' },
              ]}
            >
              <Input
                autoComplete="username"
                spellCheck={false}
                autoCapitalize="off"
                placeholder="请输入账号"
                prefix={<LockOutlined />}
              />
            </Form.Item>

            <Form.Item
              name="password"
              label="密码"
              rules={[
                { required: true, message: '请输入密码' },
                { validator: (_rule, value?: string) => validatePasswordLength(value) },
              ]}
            >
              <Input.Password
                autoComplete="current-password"
                placeholder="请输入密码"
                iconRender={(visible) => (visible ? <EyeTwoTone /> : <EyeInvisibleOutlined />)}
              />
            </Form.Item>

            <div className={styles.row}>
              <Form.Item name="rememberMe" valuePropName="checked" noStyle>
                <Checkbox>
                  保持登录 <span className={styles.hint}>（仅限本单位授权设备）</span>
                </Checkbox>
              </Form.Item>
              <Tooltip title="本页不提供自助重置。忘记密码请联系系统管理员在「用户管理」中重置，密码不通过登录页修改">
                <Button type="link" disabled>
                  忘记密码？
                </Button>
              </Tooltip>
            </div>

            <Button type="primary" htmlType="submit" size="large" block loading={submitting}>
              {submitting ? '正在登录' : '登录'}
            </Button>

            <p className={styles.foot}>登录行为将记入审计日志。请勿在非授权设备上保存凭证。</p>
          </Form>
        </div>
      </main>
    </div>
  );
}
