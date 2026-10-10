import { Bubble } from '@ant-design/x';
import { Button } from 'antd';
import type { MessagePart } from '@merine/api-contract';
import { messageText, type AgentMessage } from '../chatModel';
import { useAgentChatApi } from '../AgentChatContext';
import { ChartPart } from './ChartPart';
import { QuestionPart } from './QuestionPart';
import { NoticePart, StepsPart, UnknownPart } from './SimpleParts';
import { SourcesPart } from './SourcesPart';
import { TablePart } from './TablePart';
import { TextPart } from './TextPart';
import styles from './AgentParts.module.css';

/** 单条消息：用户文本走普通气泡，助手按部件注册表渲染。 */
export function AgentMessageItem({ message, last }: { message: AgentMessage; last: boolean }) {
  const { retry, busy } = useAgentChatApi();
  if (message.role === 'USER') {
    return (
      <Bubble
        placement="end"
        content={message}
        contentRender={() => <p className={styles.userText}>{messageText(message)}</p>}
      />
    );
  }
  const showRetry = Boolean(message.error?.retryable) && last && !busy;
  return (
    <Bubble
      placement="start"
      variant="borderless"
      content={message}
      contentRender={() => (
        <div className={styles.assistantBody}>
          {message.parts.length === 0 && message.status === 'STREAMING' && (
            <span className={styles.thinking}>正在生成…</span>
          )}
          {message.parts.map((rendered) =>
            rendered.kind === 'unknown' ? (
              <UnknownPart key={rendered.partId} rawType={rendered.rawType} />
            ) : (
              <PartView
                key={rendered.partId}
                part={rendered.part}
                streaming={rendered.status === 'STREAMING'}
              />
            ),
          )}
          {message.error && (
            <NoticePart
              level={message.status === 'ABORTED' ? 'INFO' : 'ERROR'}
              text={message.error.message}
            />
          )}
          {showRetry && (
            <div>
              <Button size="small" onClick={retry}>
                重试
              </Button>
            </div>
          )}
        </div>
      )}
    />
  );
}

function PartView({ part, streaming }: { part: MessagePart; streaming: boolean }) {
  switch (part.type) {
    case 'TEXT':
      return <TextPart text={part.text ?? ''} streaming={streaming} />;
    case 'TABLE':
      return <TablePart part={part} />;
    case 'CHART':
      return <ChartPart spec={part.spec} />;
    case 'SOURCES':
      return <SourcesPart items={part.items} />;
    case 'STEPS':
      return <StepsPart items={part.items} />;
    case 'QUESTION':
      return <QuestionPart part={part} />;
    case 'NOTICE':
      return <NoticePart level={part.level ?? 'INFO'} text={part.text ?? ''} code={part.code} />;
    default:
      return null;
  }
}
