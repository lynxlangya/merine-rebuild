import {
  EyeInvisibleOutlined,
  EyeTwoTone,
  LockOutlined,
  MoonOutlined,
  SunOutlined,
} from '@ant-design/icons';
import { useQuery } from '@tanstack/react-query';
import { Alert, Button, Checkbox, Form, Input, Segmented, Select, Spin } from 'antd';
import { useEffect, useState } from 'react';
import { Navigate, useNavigate, useSearchParams } from 'react-router';
import type { DevLoginAccountOption } from '@merine/api-contract';
import { ApiError } from '../../shared/http';
import { validatePasswordLength } from '../../shared/password';
import { useThemeMode } from '../../shared/theme/ThemeProvider';
import { useAuth } from './AuthProvider';
import { fetchDevLoginAccounts } from './api';
import { safeRedirectTarget } from './redirect';
import styles from './LoginPage.module.css';

interface LoginFormValues {
  loginName: string;
  password?: string;
  rememberMe: boolean;
}

/**
 * 本地开发预填的演示账号。只填账号不填密码——密码不进前端产物。
 * 生产构建里为空字符串，不会带出任何预置账号。
 */
const PREFILLED_LOGIN_NAME = import.meta.env.DEV ? 'demo.hq.admin' : '';

function devAccountLabel(account: DevLoginAccountOption): string {
  if (account.unitLevel === 1) return `总队-${account.displayName}`;
  if (account.unitLevel === 2) {
    return `支队-${account.unitName.replace('市公安局', '')}-${account.displayName}`;
  }
  if (account.unitLevel === 3) {
    const branchName = account.parentUnitName?.match(/^(.+?)市/)?.[1] ?? account.parentUnitName;
    return `大队-${branchName ?? '所属支队'}-${account.unitName}-${account.displayName}`;
  }
  return `${account.unitName}-${account.displayName}`;
}

interface LoginFailure {
  /** danger 用于凭证与账号问题；warning 用于服务不可用，两者动作不同。 */
  kind: 'danger' | 'warning';
  title: string;
  description: string;
  requestId?: string;
}

function describeFailure(error: unknown): LoginFailure {
  if (error instanceof ApiError) {
    if (error.code === 'INVALID_DEV_ACCOUNT') {
      return {
        kind: 'danger',
        title: '账号不可用',
        description: '请从列表中重新选择一个可用账号。',
        requestId: error.requestId,
      };
    }
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
  const { state, signIn, signInDev } = useAuth();
  const { mode, setMode } = useThemeMode();
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const [form] = Form.useForm<LoginFormValues>();
  const [failure, setFailure] = useState<LoginFailure | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const devAccounts = useQuery({
    queryKey: ['dev-login-accounts'],
    queryFn: ({ signal }) => fetchDevLoginAccounts(signal),
    enabled: import.meta.env.DEV,
    retry: false,
  });

  useEffect(() => {
    if (!devAccounts.data?.length) return;
    const selected = form.getFieldValue('loginName') as string | undefined;
    if (!devAccounts.data.some((account) => account.loginName === selected)) {
      form.setFieldValue('loginName', devAccounts.data[0].loginName);
    }
  }, [devAccounts.data, form]);

  const redirect = safeRedirectTarget(searchParams.get('redirect'));
  const showDevLogin = import.meta.env.DEV && devAccounts.isSuccess;
  const loadingDevAccounts = import.meta.env.DEV && devAccounts.isPending;
  const devAccountsError =
    import.meta.env.DEV &&
    devAccounts.isError &&
    !(devAccounts.error instanceof ApiError && [401, 404].includes(devAccounts.error.status));

  if (state.status === 'authenticated') {
    return <Navigate to={redirect} replace />;
  }

  const submit = async (values: LoginFormValues) => {
    setFailure(null);
    setSubmitting(true);
    try {
      if (showDevLogin) {
        await signInDev(values.loginName.trim(), values.rememberMe);
      } else {
        await signIn({
          loginName: values.loginName.trim(),
          password: values.password ?? '',
          rememberMe: values.rememberMe,
        });
      }
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
      if (!showDevLogin && described.kind === 'danger') form.setFieldValue('password', '');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className={styles.auth}>
      <a className={styles.skip} href="#loginForm">
        跳到登录表单
      </a>

      <aside className={styles.brand} aria-hidden="true" />

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
                {showDevLogin
                  ? '本地开发可直接选择账号进入工作台，便于检查不同单位和角色的页面。'
                  : '使用单位分配的账号登录。可用功能由账号当前的角色决定。忘记密码或账号异常，请联系单位系统管理员。'}
              </p>
            </div>

            {devAccountsError && (
              <Alert
                role="alert"
                type="warning"
                showIcon
                title="本地账号列表暂不可用"
                description="可以稍后重试，或使用账号密码登录。"
                action={
                  <Button size="small" onClick={() => void devAccounts.refetch()}>
                    重试
                  </Button>
                }
              />
            )}

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

            {loadingDevAccounts ? (
              <div className={styles.loading}>
                <Spin size="small" /> 正在加载本地账号…
              </div>
            ) : (
              <>
                {showDevLogin ? (
                  <>
                    <Form.Item
                      name="loginName"
                      label="选择账号"
                      rules={[{ required: true, message: '请选择账号' }]}
                    >
                      <Select
                        showSearch
                        filterOption={(input, option) => {
                          const query = input.trim().toLocaleLowerCase();
                          return [option?.label, option?.value].some((value) =>
                            String(value ?? '')
                              .toLocaleLowerCase()
                              .includes(query),
                          );
                        }}
                        placeholder="选择要使用的账号"
                        options={devAccounts.data?.map((account) => ({
                          value: account.loginName,
                          label: devAccountLabel(account),
                        }))}
                      />
                    </Form.Item>
                    {devAccounts.data?.length === 0 && (
                      <Alert type="info" showIcon title="当前没有可用账号" />
                    )}
                  </>
                ) : (
                  <>
                    <Form.Item
                      name="loginName"
                      label="账号"
                      extra={PREFILLED_LOGIN_NAME ? '本地开发已预填演示账号。' : undefined}
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
                        iconRender={(visible) =>
                          visible ? <EyeTwoTone /> : <EyeInvisibleOutlined />
                        }
                      />
                    </Form.Item>
                  </>
                )}

                <Form.Item name="rememberMe" valuePropName="checked" noStyle>
                  <Checkbox>
                    保持登录 <span className={styles.hint}>（仅限本单位授权设备）</span>
                  </Checkbox>
                </Form.Item>

                <Button
                  type="primary"
                  htmlType="submit"
                  size="large"
                  block
                  loading={submitting}
                  disabled={showDevLogin && devAccounts.data?.length === 0}
                >
                  {submitting ? '正在登录' : showDevLogin ? '以所选账号登录' : '登录'}
                </Button>

                <p className={styles.foot}>
                  {showDevLogin
                    ? '仅本地开发环境可直接选择账号；登录后仍按该账号的权限访问。'
                    : '登录行为将记入审计日志。请勿在非授权设备上保存凭证。'}
                </p>
              </>
            )}
          </Form>
        </div>
      </main>
    </div>
  );
}
