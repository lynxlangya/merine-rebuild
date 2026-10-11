-- owner: agent.datasource。
-- Agent 受控查询的数据源配置：连接参数、密码密文、启停状态，以及连接/只读授权检查结果。
-- 本轮只做配置与检查，不向 Agent 下发凭据、不执行用户或模型提交的 SQL。
-- 密码只存密文（AES-256-GCM，AAD 为 datasource:<id>），主密钥在部署环境，不在库内。
CREATE TABLE ai_data_source (
  id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL
    COMMENT '数据源 UUID；同时作为密码密文的认证附加数据前缀（datasource:<id>）',
  name VARCHAR(80) NOT NULL COMMENT '配置名称，全局唯一，用于区分环境或账号；不参与数据库连接',
  remark VARCHAR(300) NOT NULL DEFAULT '' COMMENT '用途备注，可空串',
  db_type VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'MYSQL'
    COMMENT '数据库类型；首版固定 MYSQL，连接串由服务端按该类型生成',
  host VARCHAR(253) CHARACTER SET ascii COLLATE ascii_bin NOT NULL
    COMMENT '主机名或 IP 字面量；不含端口、协议与账号信息，连接前校验 DNS 解析结果在部署允许范围内',
  port INT NOT NULL DEFAULT 3306 COMMENT 'TCP 端口；必须属于部署配置允许的端口集合',
  database_name VARCHAR(64) NOT NULL COMMENT '目标数据库名；账号至少要能访问该库，只勾选其中的视图',
  username VARCHAR(64) NOT NULL COMMENT '专用只读数据库账号名；有写权限或无法确认只读的账号不允许启用',
  password_cipher TEXT NOT NULL COMMENT 'v1 密文（随机 nonce + AES-256-GCM，AAD 为 datasource:<id>）；不回显、不写日志',
  status VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'DISABLED'
    COMMENT '启用状态 ENABLED/DISABLED；新建默认停用，启用前必须重新检查通过并已勾选视图',
  version INT NOT NULL DEFAULT 0 COMMENT '并发编辑版本；连接参数、密码或视图范围变化后递增并使旧检查结果失效',
  last_check_at DATETIME(6) NULL COMMENT '最近一次连接与授权检查时刻 UTC；NULL 表示尚未检查或结果已因配置修改清除',
  last_check_status VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL
    COMMENT '最近检查结论：PASSED/VIEW_DENIED/WRITE_PRIVILEGE/AUTHORIZATION_UNCONFIRMED/CONNECTION_FAILED；NULL 表示无有效结果',
  last_check_message VARCHAR(300) NOT NULL DEFAULT '' COMMENT '最近检查结论的脱敏说明；不包含密码或账号凭据',
  checked_version INT NULL COMMENT '产生该结论时的配置版本；与 version 不一致即为旧结果，不会被启用复用',
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时刻 UTC',
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
    COMMENT '最近修改时刻 UTC，由修改 SQL 显式更新',
  PRIMARY KEY (id),
  UNIQUE KEY uk_ai_data_source_name (name),
  INDEX idx_ai_data_source_updated (updated_at, id),
  CONSTRAINT ck_ai_data_source_db_type CHECK (db_type IN ('MYSQL')),
  CONSTRAINT ck_ai_data_source_port CHECK (port BETWEEN 1 AND 65535),
  CONSTRAINT ck_ai_data_source_status CHECK (status IN ('ENABLED', 'DISABLED')),
  CONSTRAINT ck_ai_data_source_check_status CHECK (last_check_status IS NULL OR last_check_status IN
    ('PASSED', 'CONNECTION_FAILED', 'WRITE_PRIVILEGE', 'AUTHORIZATION_UNCONFIRMED', 'VIEW_DENIED')),
  CONSTRAINT ck_ai_data_source_version CHECK (version >= 0),
  CONSTRAINT ck_ai_data_source_checked_version CHECK (checked_version IS NULL OR checked_version >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='agent 模块：Agent 受控查询的数据源连接配置；密码只存密文';

-- 允许查询的视图白名单：管理员从检查结果中勾选，本次保存整体替换。
-- 勾选只约束本平台，不会自动创建或修改数据库授权；实际可读性仍由数据库账号授权决定。
CREATE TABLE ai_data_source_view (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键，API 不直接暴露',
  data_source_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL
    COMMENT '所属数据源 ai_data_source(id)；应用层保证不悬空，删除数据源时同事务清理（本轮无删除入口）',
  view_name VARCHAR(64) NOT NULL COMMENT '允许查询的视图名（MySQL 标识符，最长 64 字符）',
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时刻 UTC',
  PRIMARY KEY (id),
  UNIQUE KEY uk_ai_data_source_view (data_source_id, view_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='agent 模块：数据源允许查询的视图白名单；不修改数据库授权';

INSERT INTO sys_permission(permission_code, permission_name, description) VALUES
 ('agent:datasource:read', '数据源管理 · 查看', '查看数据源配置与最近检查结果，不返回密码'),
 ('agent:datasource:create', '数据源管理 · 新增', '新增数据源配置，新建默认停用'),
 ('agent:datasource:update', '数据源管理 · 编辑', '编辑连接参数、密码与允许查询的视图'),
 ('agent:datasource:toggle-status', '数据源管理 · 启停', '启用或停用数据源；启用前必须重新检查通过'),
 ('agent:datasource:test', '数据源管理 · 测试连接', '测试连接并检查只读授权与可查询视图');

INSERT INTO sys_menu(menu_type, menu_name, sort_order, status, description, icon_name)
SELECT 'DIRECTORY', '智能体管理', 50, 'ENABLED', 'Agent 数据源等运行配置', 'RobotOutlined'
 WHERE NOT EXISTS (SELECT 1 FROM (SELECT id FROM sys_menu WHERE menu_type = 'DIRECTORY'
   AND menu_name = '智能体管理' AND parent_id IS NULL) AS existing);

INSERT INTO sys_menu(parent_id, menu_type, menu_name, route_key, permission_id, sort_order, status,
                     description, icon_name)
SELECT d.id, 'PAGE', '数据源管理', 'agent.datasources', p.id, 10, 'ENABLED',
       '受控查询数据源的连接配置、只读授权检查与视图白名单', 'DatabaseOutlined'
  FROM sys_menu d
  JOIN sys_permission p ON p.permission_code = 'agent:datasource:read'
 WHERE d.menu_type = 'DIRECTORY' AND d.menu_name = '智能体管理' AND d.parent_id IS NULL;

INSERT INTO sys_menu(parent_id, menu_type, menu_name, permission_id, sort_order, status, description)
SELECT m.id, 'BUTTON', s.name, p.id, s.sort_order, 'ENABLED', p.description
  FROM (SELECT '测试连接' name, 'agent:datasource:test' code, 10 sort_order
        UNION ALL SELECT '新增数据源', 'agent:datasource:create', 20
        UNION ALL SELECT '编辑数据源', 'agent:datasource:update', 30
        UNION ALL SELECT '启停数据源', 'agent:datasource:toggle-status', 40) s
  JOIN sys_permission p ON p.permission_code = s.code
  JOIN sys_menu m ON m.route_key = 'agent.datasources';
