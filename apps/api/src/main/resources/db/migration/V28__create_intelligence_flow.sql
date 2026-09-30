-- owner: intelligence；无物理外键，引用完整性由事务锁及巡检维护。

CREATE TABLE intel_topic (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键，API 返回十进制字符串',
    topic_no VARCHAR(40) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '稳定唯一情报编号',
    source_unit_id BIGINT NOT NULL COMMENT '操作或引用单位，引用 sys_unit(id)，用例锁定校验',
    source_user_id BIGINT NOT NULL COMMENT '操作用户，引用 sys_user(id)，授权锚点下校验',
    source_unit_name VARCHAR(100) NOT NULL COMMENT '操作时名称快照，不随更名变化',
    source_user_name VARCHAR(100) NOT NULL COMMENT '操作时名称快照，不随更名变化',
    title VARCHAR(160) NOT NULL COMMENT '标题，发布后不可修改',
    body TEXT NOT NULL COMMENT '正文，接口上限 4000 字符',
    status VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'DRAFT 草稿，PUBLISHED 已发出，无办结状态',
    version INT NOT NULL DEFAULT 0 COMMENT '草稿并发版本',
    draft_note VARCHAR(1000) NOT NULL DEFAULT '' COMMENT '草稿首次发送说明，发布后保留',
    created_at DATETIME(6) NOT NULL COMMENT '操作时刻，UTC',
    published_at DATETIME(6) NULL COMMENT '首次发送时刻，UTC，草稿为空',
    PRIMARY KEY (id),
    UNIQUE KEY uk_intel_topic_no (topic_no),
    INDEX idx_intel_topic_source (source_unit_id, id),
    INDEX idx_intel_topic_user (source_user_id),
    CONSTRAINT ck_intel_topic_status CHECK (status IN ('DRAFT','PUBLISHED')),
    CONSTRAINT ck_intel_topic_version CHECK (version >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='intelligence 模块：原始情报，发布后正文不覆盖';

CREATE TABLE intel_scope_unit (
    topic_id BIGINT NOT NULL COMMENT '所属情报，引用 intel_topic(id)，用例事务校验',
    unit_id BIGINT NOT NULL COMMENT '操作或引用单位，引用 sys_unit(id)，用例锁定校验',
    unit_name VARCHAR(100) NOT NULL COMMENT '操作时名称快照，不随更名变化',
    PRIMARY KEY (topic_id, unit_id),
    INDEX idx_intel_scope_unit_unit (unit_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='intelligence 模块：允许传播单位，发布后冻结';

CREATE TABLE intel_draft_target (
    topic_id BIGINT NOT NULL COMMENT '所属情报，引用 intel_topic(id)，用例事务校验',
    unit_id BIGINT NOT NULL COMMENT '操作或引用单位，引用 sys_unit(id)，用例锁定校验',
    unit_name VARCHAR(100) NOT NULL COMMENT '操作时名称快照，不随更名变化',
    PRIMARY KEY (topic_id, unit_id),
    INDEX idx_intel_draft_target_unit (unit_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='intelligence 模块：草稿首次目标单位';

CREATE TABLE intel_send (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键，API 返回十进制字符串',
    topic_id BIGINT NOT NULL COMMENT '所属情报，引用 intel_topic(id)，用例事务校验',
    parent_receipt_id BIGINT NULL COMMENT '继续共享的来源回执，引用 intel_receipt(id)，同情报；源头发送为空',
    from_unit_id BIGINT NOT NULL COMMENT '操作或引用单位，引用 sys_unit(id)，用例锁定校验',
    sender_user_id BIGINT NOT NULL COMMENT '操作用户，引用 sys_user(id)，授权锚点下校验',
    from_unit_name VARCHAR(100) NOT NULL COMMENT '操作时名称快照，不随更名变化',
    sender_name VARCHAR(100) NOT NULL COMMENT '操作时名称快照，不随更名变化',
    note VARCHAR(1000) NOT NULL COMMENT '本次发送附加说明',
    sent_at DATETIME(6) NOT NULL COMMENT '操作时刻，UTC',
    PRIMARY KEY (id),
    INDEX idx_intel_send_topic (topic_id, id),
    INDEX idx_intel_send_parent (parent_receipt_id),
    INDEX idx_intel_send_unit (from_unit_id),
    INDEX idx_intel_send_user (sender_user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='intelligence 模块：发送批次，转发不复制原情报';

CREATE TABLE intel_receipt (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键，API 返回十进制字符串',
    send_id BIGINT NOT NULL COMMENT '发送批次，引用 intel_send(id)',
    to_unit_id BIGINT NOT NULL COMMENT '操作或引用单位，引用 sys_unit(id)，用例锁定校验',
    to_unit_name VARCHAR(100) NOT NULL COMMENT '操作时名称快照，不随更名变化',
    signed_by_user_id BIGINT NULL COMMENT '签收人，引用 sys_user(id)，未签收为空',
    signed_by_name VARCHAR(100) NULL COMMENT '签收时姓名快照',
    signed_at DATETIME(6) NULL COMMENT '首次单位签收时刻，UTC',
    PRIMARY KEY (id),
    UNIQUE KEY uk_intel_receipt_send_unit (send_id, to_unit_id),
    INDEX idx_intel_receipt_unit (to_unit_id, send_id),
    INDEX idx_intel_receipt_signer (signed_by_user_id),
    CONSTRAINT ck_intel_receipt_sign CHECK ((signed_at IS NULL AND signed_by_user_id IS NULL AND signed_by_name IS NULL) OR (signed_at IS NOT NULL AND signed_by_user_id IS NOT NULL AND signed_by_name IS NOT NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='intelligence 模块：每次送达独立回执和单位签收';

CREATE TABLE intel_read (
    receipt_id BIGINT NOT NULL COMMENT '阅读回执，引用 intel_receipt(id)',
    user_id BIGINT NOT NULL COMMENT '操作用户，引用 sys_user(id)，授权锚点下校验',
    unit_id BIGINT NOT NULL COMMENT '操作或引用单位，引用 sys_unit(id)，用例锁定校验',
    user_name VARCHAR(100) NOT NULL COMMENT '操作时名称快照，不随更名变化',
    read_at DATETIME(6) NOT NULL COMMENT '操作时刻，UTC',
    PRIMARY KEY (receipt_id, user_id),
    INDEX idx_intel_read_user (user_id),
    INDEX idx_intel_read_unit (unit_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='intelligence 模块：个人首次阅读，不替代签收';

CREATE TABLE intel_feedback (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键，API 返回十进制字符串',
    receipt_id BIGINT NOT NULL COMMENT '反馈来源回执，引用 intel_receipt(id)',
    unit_id BIGINT NOT NULL COMMENT '操作或引用单位，引用 sys_unit(id)，用例锁定校验',
    user_id BIGINT NOT NULL COMMENT '操作用户，引用 sys_user(id)，授权锚点下校验',
    unit_name VARCHAR(100) NOT NULL COMMENT '操作时名称快照，不随更名变化',
    user_name VARCHAR(100) NOT NULL COMMENT '操作时名称快照，不随更名变化',
    body TEXT NOT NULL COMMENT '线索反馈，上限 4000 字符，无办理结果义务',
    created_at DATETIME(6) NOT NULL COMMENT '操作时刻，UTC',
    PRIMARY KEY (id),
    INDEX idx_intel_feedback_receipt (receipt_id, id),
    INDEX idx_intel_feedback_unit (unit_id),
    INDEX idx_intel_feedback_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='intelligence 模块：绑定接收记录的追加反馈';

CREATE TABLE intel_supplement (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键，API 返回十进制字符串',
    topic_id BIGINT NOT NULL COMMENT '所属情报，引用 intel_topic(id)，用例事务校验',
    kind VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'SUPPLEMENT 补充，CORRECTION 更正',
    unit_id BIGINT NOT NULL COMMENT '操作或引用单位，引用 sys_unit(id)，用例锁定校验',
    user_id BIGINT NOT NULL COMMENT '操作用户，引用 sys_user(id)，授权锚点下校验',
    unit_name VARCHAR(100) NOT NULL COMMENT '操作时名称快照，不随更名变化',
    user_name VARCHAR(100) NOT NULL COMMENT '操作时名称快照，不随更名变化',
    body TEXT NOT NULL COMMENT '追加说明，上限 4000 字符',
    created_at DATETIME(6) NOT NULL COMMENT '操作时刻，UTC',
    PRIMARY KEY (id),
    INDEX idx_intel_supplement_topic (topic_id, id),
    INDEX idx_intel_supplement_unit (unit_id),
    INDEX idx_intel_supplement_user (user_id),
    CONSTRAINT ck_intel_supplement_kind CHECK (kind IN ('SUPPLEMENT','CORRECTION'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='intelligence 模块：源头补充更正，不覆盖原文';

CREATE TABLE intel_command (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键，API 返回十进制字符串',
    actor_unit_id BIGINT NOT NULL COMMENT '操作或引用单位，引用 sys_unit(id)，用例锁定校验',
    action_code VARCHAR(96) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '动作及资源；阅读包含用户 ID',
    idempotency_key VARCHAR(80) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '客户端同意图稳定键',
    request_digest CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '规范化命令的 SHA256 摘要',
    topic_id BIGINT NULL COMMENT '成功结果，引用 intel_topic(id)，事务内占位暂空，提交前填入',
    created_at DATETIME(6) NOT NULL COMMENT '操作时刻，UTC',
    PRIMARY KEY (id),
    UNIQUE KEY uk_intel_command (actor_unit_id, action_code, idempotency_key),
    INDEX idx_intel_command_topic (topic_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='intelligence 模块：同单位同意图事务内持久幂等';

CREATE TABLE intel_number_counter (
    business_date DATE NOT NULL COMMENT 'UTC+8 的业务日期',
    last_number INT NOT NULL COMMENT '已使用的日流水，回滚不消耗',
    PRIMARY KEY (business_date),
    CONSTRAINT ck_intel_number CHECK (last_number BETWEEN 0 AND 999999)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='intelligence 模块：事务内按业务日期分配编号';

INSERT INTO sys_permission (permission_code, permission_name, description) VALUES
 ('intelligence:topic:read','信息流转 · 查看信息','按实际接收范围查看情报'),
 ('intelligence:topic:create','信息流转 · 新建情报','保存情报草稿'),
 ('intelligence:topic:update','信息流转 · 修改草稿','修改未发出情报'),
 ('intelligence:topic:send','信息流转 · 发送情报','源头单位发送情报'),
 ('intelligence:topic:sign','信息流转 · 单位签收','签收本单位一次接收记录'),
 ('intelligence:topic:read-receipt','信息流转 · 记录阅读','个人显式记录阅读'),
 ('intelligence:topic:feedback','信息流转 · 反馈线索','签收后追加线索反馈'),
 ('intelligence:topic:forward','信息流转 · 继续共享','签收后沿合法关系继续共享'),
 ('intelligence:topic:supplement','信息流转 · 补充更正','源头追加补充或更正说明');

UPDATE sys_menu m JOIN sys_permission p ON p.permission_code='intelligence:topic:read' SET m.permission_id=p.id WHERE m.route_key='collaboration.flows';
INSERT INTO sys_menu (parent_id,menu_type,menu_name,route_key,permission_id,sort_order,status,description) SELECT d.id,'PAGE','信息流转','collaboration.flows',p.id,20,'ENABLED','情报共享、签收与线索反馈' FROM sys_menu d JOIN sys_permission p ON p.permission_code='intelligence:topic:read' WHERE d.menu_name='业务协同' AND d.menu_type='DIRECTORY' AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE route_key='collaboration.flows');
INSERT INTO sys_menu (parent_id,menu_type,menu_name,permission_id,sort_order,status,description) SELECT m.id,'BUTTON',p.permission_name,p.id,seed.sort_order,'ENABLED',p.description FROM (SELECT 'intelligence:topic:create' AS code, 10 AS sort_order UNION ALL SELECT 'intelligence:topic:update' AS code, 20 AS sort_order UNION ALL SELECT 'intelligence:topic:send' AS code, 30 AS sort_order UNION ALL SELECT 'intelligence:topic:sign' AS code, 40 AS sort_order UNION ALL SELECT 'intelligence:topic:read-receipt' AS code, 50 AS sort_order UNION ALL SELECT 'intelligence:topic:feedback' AS code, 60 AS sort_order UNION ALL SELECT 'intelligence:topic:forward' AS code, 70 AS sort_order UNION ALL SELECT 'intelligence:topic:supplement' AS code, 80 AS sort_order) seed JOIN sys_menu m ON m.route_key='collaboration.flows' JOIN sys_permission p ON p.permission_code=seed.code WHERE NOT EXISTS (SELECT 1 FROM sys_menu e WHERE e.permission_id=p.id);
UPDATE sys_menu m JOIN sys_permission p ON p.id=m.permission_id SET m.menu_name=SUBSTRING_INDEX(p.permission_name, ' · ', -1) WHERE p.permission_code LIKE 'intelligence:topic:%' AND m.menu_type='BUTTON';
