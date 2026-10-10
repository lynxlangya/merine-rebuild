-- owner: agent.provider；供应商连接下的可选模型。一个连接可以挂多个模型，调用时按 model_id 原样发送。
-- 引用完整性由应用层保证：写路径先锁 ai_model_provider 行，再校验/维护本表；删除供应商时同事务删除模型。
CREATE TABLE ai_model_provider_model (
  id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '模型配置 UUID',
  provider_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '所属供应商配置 ai_model_provider.id；应用层在同一事务内先锁供应商行',
  model_id VARCHAR(120) NOT NULL COMMENT '供应商模型标识，调用时原样作为请求的 model 字段',
  display_name VARCHAR(80) NOT NULL COMMENT '界面显示名；保存为空白时由服务端回填为 model_id',
  remark VARCHAR(300) NOT NULL COMMENT '用途备注，可为空字符串',
  status VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'ENABLED' COMMENT '模型状态 ENABLED/DISABLED，不表示已连通',
  sort_order INT NOT NULL DEFAULT 0 COMMENT '同一连接内的展示顺序，越小越靠前',
  version INT NOT NULL DEFAULT 0 COMMENT '并发编辑版本，更新后递增',
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间 UTC',
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '更新时间 UTC',
  PRIMARY KEY(id),
  UNIQUE KEY uk_ai_provider_model(provider_id, model_id),
  INDEX idx_ai_provider_model_order(provider_id, sort_order, id),
  CONSTRAINT ck_ai_provider_model_status CHECK(status IN ('ENABLED','DISABLED')),
  CONSTRAINT ck_ai_provider_model_version CHECK(version >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='agent模块：供应商连接下的可选模型；只保存配置，不发起调用';
