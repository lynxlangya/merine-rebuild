-- 审计保留期（默认 30 天）落地后，把口径写进表与列注释：DBA 看表结构就能知道行会被硬删。
-- 不加列、不加索引：清理按 occurred_at < 边界 走已有的 idx_sys_audit_log_time。
ALTER TABLE sys_audit_log COMMENT = '操作审计流水：只追加、不可改；保留最近 30 天（merine.audit.retention.days），过期行由应用定时或手动硬删，清理本身也记一条 audit:purge';

ALTER TABLE sys_audit_log MODIFY COLUMN occurred_at DATETIME(6) NOT NULL COMMENT '操作发生时刻 UTC；保留期按这一列判过期（严格早于边界才删），列表也按它倒序';
