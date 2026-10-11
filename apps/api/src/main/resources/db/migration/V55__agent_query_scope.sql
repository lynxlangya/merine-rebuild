-- owner: agent.datasource。
-- 查询范围与语义管理：视图/字段说明覆盖值与独立语义版本。
-- 执行白名单仍在 ai_data_source_view；说明编辑不使连接检查失效，也不改变启停状态。
-- 说明默认取目标库的列注释，应用内只保存覆盖值；空串表示取消覆盖。
ALTER TABLE ai_data_source
    ADD COLUMN semantic_version INT NOT NULL DEFAULT 0
        COMMENT '说明（语义）版本；仅视图/字段说明覆盖值变化时递增，不使连接检查结果失效',
    ADD CONSTRAINT ck_ai_data_source_semantic_version CHECK (semantic_version >= 0);

-- 视图级覆盖值：显示名与用途/指标说明。默认显示名取视图名、说明取列注释或留空。
CREATE TABLE ai_data_source_view_meta (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键，API 不直接暴露',
  data_source_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL
    COMMENT '所属数据源 ai_data_source(id)；应用层保证不悬空，删除数据源时同事务清理',
  view_name VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL
    COMMENT '视图名；数据库对象不存在时保留覆盖值但不下发模型',
  display_name VARCHAR(80) NOT NULL DEFAULT '' COMMENT '显示名；空表示沿用视图名',
  description VARCHAR(2000) NOT NULL DEFAULT ''
    COMMENT '用途、统计对象、条件、时间与去重口径；空表示未填写，不回退视图注释',
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
    COMMENT '最近修改时刻 UTC，由写入 SQL 显式更新',
  PRIMARY KEY (id),
  UNIQUE KEY uk_ai_data_source_view_meta (data_source_id, view_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='agent 模块：视图显示名与用途/指标说明覆盖值；执行白名单在 ai_data_source_view';

-- 字段级覆盖值：仅保存说明。默认取目标库 information_schema 的列注释，空串表示回退默认。
CREATE TABLE ai_data_source_view_column_meta (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键，API 不直接暴露',
  data_source_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL
    COMMENT '所属数据源 ai_data_source(id)；应用层保证不悬空',
  view_name VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '所属视图名',
  column_name VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL
    COMMENT '字段名；实际字段不存在时保留覆盖值但不下发模型',
  description VARCHAR(300) NOT NULL DEFAULT '' COMMENT '字段说明覆盖值；空表示回退数据库列注释',
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
    COMMENT '最近修改时刻 UTC，由写入 SQL 显式更新',
  PRIMARY KEY (id),
  UNIQUE KEY uk_ai_data_source_view_column_meta (data_source_id, view_name, column_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='agent 模块：字段说明覆盖值；默认取目标库列注释';

INSERT INTO sys_permission(permission_code, permission_name, description) VALUES
 ('agent:scope:read', '查询范围 · 查看', '查看查询范围、视图与字段说明；列表不连接目标库'),
 ('agent:scope:update', '查询范围 · 维护', '修改允许查询的视图范围与视图/字段说明');

INSERT INTO sys_menu(parent_id, menu_type, menu_name, route_key, permission_id, sort_order, status,
                     description, icon_name)
SELECT d.id, 'PAGE', '查询范围', 'agent.query-scopes', p.id, 20, 'ENABLED',
       '维护允许查询的视图范围与视图/字段说明', 'TableOutlined'
  FROM sys_menu d
  JOIN sys_permission p ON p.permission_code = 'agent:scope:read'
 WHERE d.menu_type = 'DIRECTORY' AND d.menu_name = '智能体管理' AND d.parent_id IS NULL
   AND NOT EXISTS (SELECT 1 FROM (SELECT id FROM sys_menu WHERE route_key = 'agent.query-scopes') AS existing);

INSERT INTO sys_menu(parent_id, menu_type, menu_name, permission_id, sort_order, status, description)
SELECT m.id, 'BUTTON', '维护查询范围', p.id, 20, 'ENABLED', p.description
  FROM sys_permission p
  JOIN sys_menu m ON m.route_key = 'agent.query-scopes'
 WHERE p.permission_code = 'agent:scope:update';
