import { useEffect, useRef, useState } from 'react';
import { errorText } from './api-error';
import { submissionIntent, submissionUncertain, type SubmissionIntent } from './idempotentIntent';

/** 写入意图与请求键一起保留；未知结果只能重放原请求，不自动重试。 */
export function useIdempotentSubmit<T, R>(
  send: (input: T, key: string) => Promise<R>,
  confirmed: (result: R) => void,
  rejected?: (error: unknown) => void,
) {
  const [busy, setBusy] = useState(false),
    [uncertain, setUncertain] = useState(false),
    [failure, setFailure] = useState<string>();
  const pending = useRef<SubmissionIntent<T> | null>(null),
    running = useRef(false),
    mounted = useRef(true);
  useEffect(() => {
    mounted.current = true;
    return () => {
      mounted.current = false;
    };
  }, []);
  const run = async (intent: SubmissionIntent<T>) => {
    if (running.current) return;
    running.current = true;
    pending.current = intent;
    setBusy(true);
    setFailure(undefined);
    let result: R;
    try {
      result = await send(intent.input, intent.key);
    } catch (error) {
      intent.uncertain = submissionUncertain(error, intent.uncertain);
      if (mounted.current) {
        setUncertain(intent.uncertain);
        setFailure(intent.uncertain ? '提交结果待确认，请重试原提交。' : errorText(error));
        rejected?.(error);
      }
      return;
    } finally {
      running.current = false;
      if (mounted.current) setBusy(false);
    }
    if (!mounted.current) return;
    pending.current = null;
    setUncertain(false);
    confirmed(result);
  };
  return {
    busy,
    uncertain,
    failure,
    submit: async (input: T) => {
      if (running.current) return;
      try {
        await run(submissionIntent(pending.current, input));
      } catch (error) {
        setFailure(error instanceof Error ? error.message : '请核对输入');
      }
    },
    retry: async () => {
      if (pending.current) await run(pending.current);
    },
  };
}
