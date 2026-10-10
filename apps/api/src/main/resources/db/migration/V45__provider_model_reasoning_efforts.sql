-- 每个模型可用的推理强度级别：取供应商官方支持值的子集，供助手输入区展示。
-- 列内为逗号分隔的 NONE/LOW/HIGH/MAX，空字符串表示不配置（不展示强度选择，也不向上游发送该参数）。
ALTER TABLE ai_model_provider_model
  ADD COLUMN reasoning_efforts VARCHAR(40) NOT NULL DEFAULT ''
    COMMENT '可用推理强度，逗号分隔的 NONE/LOW/HIGH/MAX；空表示不启用强度选择'
    AFTER remark;
