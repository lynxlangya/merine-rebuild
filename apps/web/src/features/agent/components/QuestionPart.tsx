import { useState } from 'react';
import { Button, Checkbox, Input, Radio, Space } from 'antd';
import type { MessagePart } from '@merine/api-contract';
import { useAgentChatApi } from '../AgentChatContext';
import styles from './AgentParts.module.css';

type Question = Extract<MessagePart, { type: 'QUESTION' }>;

/**
 * 追问卡：单选、多选、文本输入、可选「其他」与合法跳过。
 * 服务端接受（STREAM_START）之前显示为「已提交，等待确认」，失败时保留用户输入可再次作答。
 */
export function QuestionPart({ part }: { part: Question }) {
  const { answer, session } = useAgentChatApi();
  const [values, setValues] = useState<string[]>([]);
  const [freeText, setFreeText] = useState('');
  const [error, setError] = useState<string>();
  const question = session?.run?.question;
  const submitted = question?.questionId === part.questionId && question.values !== undefined;
  const options = (part.options ?? []).filter(
    (option): option is { value: string; label?: string; hint?: string } =>
      Boolean(option && typeof option.value === 'string'),
  );
  const labelOf = (value: string) =>
    options.find((option) => option.value === value)?.label ?? value;
  const summary = (question?.values ?? []).map(labelOf).join('、');

  if (submitted) {
    return (
      <div className={styles.questionAnswered}>
        <span>{question?.answered ? '已作答' : '已提交，等待服务端确认…'}</span>
        <strong>{summary || question?.freeText || (question?.skipped ? '跳过该问题' : '—')}</strong>
      </div>
    );
  }

  const submit = (input: { values: string[]; freeText?: string; skipped?: boolean }) => {
    setError(undefined);
    const text = (input.freeText ?? '').trim();
    const chosen = input.values.map(labelOf);
    const label = [...chosen, text].filter(Boolean).join('、');
    answer({
      questionId: part.questionId,
      values: input.values,
      freeText: text || undefined,
      skipped: input.skipped,
      label: label || undefined,
    });
  };

  const validateAndSubmit = () => {
    const max = part.maxLength ?? 500;
    if (part.mode === 'TEXT') {
      if (!freeText.trim()) {
        setError('请输入内容');
        return;
      }
      if (freeText.length > max) {
        setError(`最多 ${max} 个字符`);
        return;
      }
      submit({ values: [], freeText });
      return;
    }
    const min = part.minSelections ?? 1;
    const limit = part.maxSelections ?? options.length;
    if (values.length < min || values.length > limit) {
      setError(`请选择 ${min}–${limit} 项`);
      return;
    }
    if (freeText.length > max) {
      setError(`补充说明最多 ${max} 个字符`);
      return;
    }
    submit({ values, freeText });
  };

  return (
    <div className={styles.question}>
      <p className={styles.questionPrompt}>{part.prompt}</p>
      {part.mode === 'SINGLE' && (
        <Radio.Group
          aria-label={part.prompt}
          optionType="button"
          buttonStyle="solid"
          value={values[0]}
          options={options.map((option) => ({
            value: option.value,
            label: option.label ?? option.value,
          }))}
          onChange={(event) => submit({ values: [String(event.target.value)], freeText })}
        />
      )}
      {part.mode === 'MULTIPLE' && (
        <Checkbox.Group
          value={values}
          onChange={(next) => setValues(next as string[])}
          options={options.map((option) => ({
            value: option.value,
            label: option.label ?? option.value,
          }))}
        />
      )}
      {part.mode === 'TEXT' && (
        <Input.TextArea
          rows={2}
          maxLength={part.maxLength ?? 500}
          placeholder={part.placeholder ?? '请输入内容'}
          value={freeText}
          onChange={(event) => setFreeText(event.target.value)}
        />
      )}
      {part.mode !== 'TEXT' && part.allowOther && (
        <Input
          size="small"
          maxLength={part.maxLength ?? 200}
          placeholder={part.placeholder ?? '其他补充（可选）'}
          value={freeText}
          onChange={(event) => setFreeText(event.target.value)}
        />
      )}
      {part.mode !== 'SINGLE' && (
        <div className={styles.questionActions}>
          <Button type="primary" size="small" onClick={validateAndSubmit}>
            提交
          </Button>
          {!part.required && (
            <Button size="small" onClick={() => submit({ values: [], skipped: true })}>
              跳过
            </Button>
          )}
        </div>
      )}
      {part.mode === 'SINGLE' && !part.required && (
        <Space>
          <Button size="small" onClick={() => submit({ values: [], skipped: true })}>
            跳过
          </Button>
        </Space>
      )}
      {error && <span className={styles.questionError}>{error}</span>}
    </div>
  );
}
