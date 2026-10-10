-- 操作审计：全系统写操作与登录事件的追加式流水（谁、何时、对什么、做了什么、结果）。
-- 只追加：没有更新与删除路径；不含业务内容（密码、密钥、情报正文、对话内容一律不进）。
-- 运行账号有 DML 权限，本表防的是误用与内部追责，不防数据库管理员。
CREATE TABLE sys_audit_log (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '追加顺序号，接口按十进制字符串返回',
  occurred_at DATETIME(6) NOT NULL COMMENT '操作发生时刻 UTC；列表按它倒序',
  actor_user_id BIGINT NULL COMMENT '操作者用户 id，引用 sys_user(id)；登录失败等无身份场景为 NULL',
  actor_login VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT '' COMMENT '登录名快照；登录失败时记录尝试的登录名',
  actor_name VARCHAR(80) NOT NULL DEFAULT '' COMMENT '姓名快照，账号改名或删除后仍可读',
  actor_unit_name VARCHAR(80) NOT NULL DEFAULT '' COMMENT '单位名快照：历史查询看的就是当时的单位名（会话快照里只有名称，没有单位 id）',
  module VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '模块码：system/agent/maritime/auth/bootstrap，登记在 AuditModules',
  action VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '动作码 <资源>:<动作>，如 user:toggle-status、auth:login',
  result VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '结果 SUCCEEDED/FAILED；写操作只产生 SUCCEEDED，登录失败为 FAILED',
  target_type VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT '' COMMENT '对象类型，如 USER/ROLE/PROVIDER/ISLAND；无对象时为空',
  target_id VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT '' COMMENT '对象标识（主键或编码），对象删除后仍保留',
  target_label VARCHAR(160) NOT NULL DEFAULT '' COMMENT '对象名称快照，删除后仍能读懂这条记录',
  summary VARCHAR(300) NOT NULL COMMENT '人写的摘要，白名单内容；禁止密码、密钥、业务正文',
  request_id VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT '' COMMENT '当时的请求 id，用于和服务器日志对齐',
  client_ip VARCHAR(45) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT '' COMMENT '客户端 IP；登录事件必填，写操作为空表示未采集',
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '写入时刻 UTC，与 occurred_at 的差即落库延迟',
  PRIMARY KEY (id),
  INDEX idx_sys_audit_log_time (occurred_at, id),
  INDEX idx_sys_audit_log_actor (actor_user_id, occurred_at, id),
  INDEX idx_sys_audit_log_target (target_type, target_id, occurred_at),
  INDEX idx_sys_audit_log_module (module, action, occurred_at),
  CONSTRAINT ck_sys_audit_log_result CHECK (result IN ('SUCCEEDED','FAILED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='system 模块：操作审计流水；只追加，不含业务内容';

-- 审计查看权限码：与其它系统管理页一致，一个页面节点一个读码；将来导出再加 :export
INSERT INTO sys_permission (permission_code, permission_name, description)
VALUES ('system:audit:read', '系统管理 · 审计日志 · 查看', '查看操作审计流水：操作者、动作、对象、结果与客户端 IP');

-- 引导菜单：审计日志页面（挂在系统管理目录下，排在字典管理之后）
INSERT INTO sys_menu (parent_id, menu_type, menu_name, route_key, permission_id, sort_order, status,
                      description)
SELECT (SELECT id FROM sys_menu WHERE menu_name = '系统管理' AND menu_type = 'DIRECTORY'),
       'PAGE', '审计日志', 'system.audit', p.id, 60, 'ENABLED',
       '操作审计流水：谁在什么时候对什么对象做了什么'
  FROM sys_permission p WHERE p.permission_code = 'system:audit:read';
