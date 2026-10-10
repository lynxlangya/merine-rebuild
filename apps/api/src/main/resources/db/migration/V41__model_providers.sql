-- owner: agent.provider；供应商配置与密钥密文。此版本不发起模型调用。
CREATE TABLE ai_model_provider (
 id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '供应商配置 UUID；同时作为密钥认证附加数据',
 vendor VARCHAR(24) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '供应商模板代码；不是模型标识',
 icon VARCHAR(24) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '内置展示图标代码',
 name VARCHAR(80) NOT NULL COMMENT '配置名称，全局唯一，可区分同厂商不同账号或地域',
 remark VARCHAR(300) NOT NULL COMMENT '配置用途备注',
 website VARCHAR(500) NOT NULL COMMENT '官网或开放平台 HTTPS 链接',
 base_url VARCHAR(500) NOT NULL COMMENT 'OpenAI Chat Completions 兼容基础地址，不含接口路径',
 api_key_cipher TEXT NOT NULL COMMENT 'v1 AES-256-GCM 密文，含随机 nonce；主密钥在部署环境中',
 status VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'ENABLED' COMMENT '配置状态 ENABLED/DISABLED，不表示连通状态',
 version INT NOT NULL DEFAULT 0 COMMENT '并发编辑版本，更新后递增',
 created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间 UTC',
 updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '更新时间 UTC',
 PRIMARY KEY(id), UNIQUE KEY uk_ai_model_provider_name(name),
 INDEX idx_ai_model_provider_updated(updated_at,id),
 CONSTRAINT ck_ai_model_provider_status CHECK(status IN ('ENABLED','DISABLED')),
 CONSTRAINT ck_ai_model_provider_version CHECK(version >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='agent模块：模型供应商连接配置；仅后端保存凭据';

INSERT INTO sys_permission(permission_code,permission_name,description) VALUES
 ('agent:provider:read','模型供应商 · 查看','查看供应商配置，不返回密钥'),
 ('agent:provider:create','模型供应商 · 新增','新增供应商与密钥'),
 ('agent:provider:update','模型供应商 · 编辑','编辑配置、替换密钥与启停'),
 ('agent:provider:delete','模型供应商 · 删除','删除供应商配置和密钥');
INSERT INTO sys_menu(menu_type,menu_name,sort_order,status,description,icon_name)
 SELECT 'DIRECTORY','智能体管理',40,'ENABLED','模型供应商与智能体配置','RobotOutlined'
 WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_type='DIRECTORY' AND menu_name='智能体管理' AND parent_id IS NULL);
INSERT INTO sys_menu(parent_id,menu_type,menu_name,route_key,permission_id,sort_order,status,description,icon_name)
 SELECT m.id,'PAGE','模型供应商','agent.providers',p.id,10,'ENABLED','供应商连接配置与密钥维护','CloudServerOutlined'
 FROM sys_menu m JOIN sys_permission p ON p.permission_code='agent:provider:read'
 WHERE m.id=(SELECT MIN(id) FROM sys_menu WHERE menu_type='DIRECTORY' AND menu_name='智能体管理' AND parent_id IS NULL);
INSERT INTO sys_menu(parent_id,menu_type,menu_name,permission_id,sort_order,status,description)
 SELECT m.id,'BUTTON',s.name,p.id,s.sort_order,'ENABLED',p.description
 FROM (SELECT '新增供应商' name,'agent:provider:create' code,10 sort_order
 UNION ALL SELECT '编辑供应商','agent:provider:update',20
 UNION ALL SELECT '删除供应商','agent:provider:delete',30) s
 JOIN sys_permission p ON p.permission_code=s.code JOIN sys_menu m ON m.route_key='agent.providers';
