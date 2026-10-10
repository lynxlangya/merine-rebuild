-- 助手会话与消息：只对归属用户可见；引用完整性由应用层保证（无物理外键）。
CREATE TABLE ai_conversation (
  id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '会话 UUID，服务端生成',
  user_id BIGINT NOT NULL COMMENT '归属用户；会话只对该用户可见',
  title VARCHAR(80) NOT NULL COMMENT '会话标题，默认取首条提问前缀，可重命名',
  last_provider_id VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT '' COMMENT '最近一次使用的供应商配置标识；连接删除后仍保留',
  last_provider_name VARCHAR(80) NOT NULL DEFAULT '' COMMENT '最近一次使用时的连接名称快照',
  last_model_id VARCHAR(120) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT '' COMMENT '最近一次使用的模型标识',
  last_reasoning_effort VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT '' COMMENT '最近一次使用的推理强度档位；空表示未使用档位参数',
  message_count INT NOT NULL DEFAULT 0 COMMENT '消息条数（用户与助手之和），列表展示用',
  version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本，重命名与删除按版本判定',
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间 UTC',
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '最近更新时间 UTC，列表按它倒序',
  PRIMARY KEY (id),
  INDEX idx_ai_conversation_owner (user_id, updated_at, id),
  CONSTRAINT ck_ai_conversation_version CHECK (version >= 0),
  CONSTRAINT ck_ai_conversation_message_count CHECK (message_count >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='agent模块：助手会话；仅归属用户可见';

CREATE TABLE ai_conversation_message (
  id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '消息 UUID；助手消息沿用流协议的 messageId',
  conversation_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '所属会话，引用 ai_conversation.id',
  run_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '所属执行，引用 ai_chat_run.id；一次执行的提问与回答共用',
  seq INT NOT NULL COMMENT '会话内顺序，从 1 递增；提问在前、回答紧随其后',
  role VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '消息角色 USER/ASSISTANT',
  text MEDIUMTEXT NOT NULL COMMENT '消息正文；失败轮次的助手消息可以是空串，原因看执行记录',
  status VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '终态 SUCCEEDED/AWAITING_INPUT/FAILED/ABORTED；用户消息固定 SUCCEEDED',
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '写入时间 UTC',
  PRIMARY KEY (id),
  UNIQUE KEY uk_ai_conversation_message_run (conversation_id, run_id, role),
  INDEX idx_ai_conversation_message_seq (conversation_id, seq),
  CONSTRAINT ck_ai_conversation_message_role CHECK (role IN ('USER','ASSISTANT')),
  CONSTRAINT ck_ai_conversation_message_status CHECK (status IN ('SUCCEEDED','AWAITING_INPUT','FAILED','ABORTED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='agent模块：助手会话消息；按 seq 顺序读取';
