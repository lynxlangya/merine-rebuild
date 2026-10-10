import { ArrowUpOutlined, ExperimentOutlined } from '@ant-design/icons';
import { Sender } from '@ant-design/x';
import { useQuery } from '@tanstack/react-query';
import { Alert, Button, Tag, Tooltip } from 'antd';
import { useState, type KeyboardEvent } from 'react';
import { errorText } from '../../shared/api-error';
import { defaultReasoningEffort, useModelOptionsQuery } from '../model-providers/public';
import { chatKeys, fetchChatRuntime } from './api';
import { useAgentChatApi } from './AgentChatContext';
import { ModelPicker } from './components/ModelPicker';
import { resolveComposerSelection, type ComposerChoice } from './modelSelection';
import styles from './AgentComposer.module.css';

/**
 * Sender 负责输入、多行与 IME；发送动作由底栏按钮与 Enter 触发，选择器只选模型与强度。
 *
 * Enter 提交由本组件接管：发送按钮画在 footer 里，`suffix` 因此关闭，
 * 而组件库把「提交可用」的内部状态交给默认动作按钮更新，内建 Enter 处理会一直处于不可提交状态。
 */
export function AgentComposer() {
  const { session, busy, question, loadingConversation, conversationError, send, stop, setDraft } =
    useAgentChatApi();
  const runtime = useQuery({
    queryKey: chatKeys.runtime,
    queryFn: ({ signal }) => fetchChatRuntime(signal),
    staleTime: 30 * 60 * 1000,
  });
  const models = useModelOptionsQuery();
  // Home 按会话 id 挂载：临时选择不会带到另一个会话，历史选择仍由 session.model 回填。
  const [choice, setChoice] = useState<ComposerChoice>();
  const [welcomeDraft, setWelcomeDraft] = useState('');
  const options = models.data ?? [];
  const { model: selected, effort } = resolveComposerSelection(
    options,
    session?.model,
    choice,
    defaultReasoningEffort,
  );
  const providerMode = runtime.data?.executionMode === 'PROVIDER';
  const blockedReason = question
    ? '请先回答上面的问题'
    : loadingConversation
      ? '正在加载会话历史…'
      : conversationError
        ? '会话历史加载失败，请先重试'
        : runtime.isPending
          ? '正在加载助手状态…'
          : runtime.isError || !runtime.data
            ? '助手状态加载失败，请重试'
            : providerMode && models.isPending
              ? '正在加载可用模型…'
              : providerMode && models.isError
                ? '模型加载失败，请重试'
                : providerMode && !selected
                  ? '还没有可用模型，请到设置中添加'
                  : '';
  const blocked = Boolean(blockedReason);
  const stopping = session?.run?.status === 'CANCEL_REQUESTED';
  const draft = session ? session.draft : welcomeDraft;
  const change = (value: string) => {
    if (session) setDraft(session.id, value);
    else setWelcomeDraft(value);
  };
  const submit = () => {
    if (blocked || busy || !draft.trim()) return;
    setWelcomeDraft('');
    send(
      draft,
      providerMode && selected
        ? {
            providerId: selected.providerId,
            modelId: selected.modelId,
            reasoningEffort: effort,
          }
        : undefined,
    );
  };
  /**
   * Enter 发送、Shift + Enter 换行；输入法组合中的 Enter（确认候选词）不当作发送。
   * 返回 false 告诉 Sender 这次按键已经处理，避免它再走一遍内建逻辑。
   */
  const onKeyDown = (event: KeyboardEvent<Element>) => {
    if (event.key !== 'Enter' || event.shiftKey || event.nativeEvent.isComposing) return;
    event.preventDefault();
    submit();
    return false;
  };

  const error = runtime.isError
    ? runtime.error
    : providerMode && models.isError
      ? models.error
      : undefined;
  return (
    <section className={styles.composer} aria-label="对话输入区">
      {error && (
        <Alert
          className={styles.error}
          type="warning"
          showIcon
          title={errorText(error)}
          action={
            <Button
              size="small"
              onClick={() => {
                if (runtime.isError) void runtime.refetch();
                if (models.isError) void models.refetch();
              }}
            >
              重试
            </Button>
          }
        />
      )}
      <Sender
        className={styles.sender}
        classNames={{
          content: styles.inputContent,
          input: styles.input,
          footer: styles.senderFooter,
        }}
        value={draft}
        onChange={change}
        onSubmit={submit}
        onKeyDown={onKeyDown}
        onCancel={stop}
        loading={busy}
        disabled={blocked && !busy}
        placeholder={blockedReason || '输入你的问题，或描述需要梳理的内容…'}
        autoSize={{ minRows: 2, maxRows: 8 }}
        allowSpeech={false}
        suffix={false}
        footer={
          <div className={styles.footer}>
            <div className={styles.controls}>
              {providerMode ? (
                selected ? (
                  <ModelPicker
                    options={options}
                    selected={selected}
                    effort={effort}
                    disabled={busy || loadingConversation || models.isError}
                    onModelChange={(modelOptionId) => setChoice({ modelOptionId })}
                    onEffortChange={(reasoningEffort) =>
                      setChoice({ modelOptionId: selected.id, reasoningEffort })
                    }
                    onResetEffort={() => setChoice({ modelOptionId: selected.id })}
                  />
                ) : (
                  <span className={styles.hint}>
                    {models.isPending
                      ? '模型加载中…'
                      : models.isError
                        ? '模型加载失败'
                        : '未配置可用模型'}
                  </span>
                )
              ) : runtime.data?.executionMode === 'LOCAL_STUB' ? (
                <Tag icon={<ExperimentOutlined />} bordered={false} className={styles.modeTag}>
                  本地演示
                </Tag>
              ) : (
                <span className={styles.hint}>
                  {runtime.isError ? '助手连接不可用' : '正在连接助手'}
                </span>
              )}
            </div>
            <div className={styles.actions}>
              <span className={styles.shortcut}>
                {busy ? (stopping ? '正在停止…' : '正在生成…') : 'Enter 发送 · Shift + Enter 换行'}
              </span>
              {busy ? (
                <Tooltip title={stopping ? '正在停止…' : '停止生成'}>
                  <Button
                    type="primary"
                    shape="circle"
                    className={styles.action}
                    aria-label={stopping ? '正在停止生成' : '停止生成'}
                    disabled={stopping}
                    onClick={stop}
                  >
                    <span className={styles.stopSquare} aria-hidden="true" />
                  </Button>
                </Tooltip>
              ) : (
                <Tooltip title={blockedReason || '发送'}>
                  <span className={styles.actionWrap}>
                    <Button
                      type="primary"
                      shape="circle"
                      className={styles.action}
                      aria-label="发送"
                      icon={<ArrowUpOutlined />}
                      disabled={blocked || !draft.trim()}
                      onClick={submit}
                    />
                  </span>
                </Tooltip>
              )}
            </div>
          </div>
        }
      />
      <p className={styles.footnote}>
        {runtime.data?.executionMode === 'LOCAL_STUB'
          ? '当前为本地脚本演示，不调用外部模型。'
          : 'AI 回答仅供参考，重要信息请核对。'}
      </p>
    </section>
  );
}
