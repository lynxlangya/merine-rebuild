-- owner: task 模块。任务、责任分支、历次承办、支队交接、结果与过程历史。
-- 引用由任务用例的事务与锁定读维护；本迁移不做级联删除。

CREATE TABLE task_order (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '任务主键，API 以十进制字符串返回',
    task_no VARCHAR(40) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '全局唯一且不可复用的任务编号',
    issuer_unit_id BIGINT NOT NULL COMMENT '发起单位，引用 sys_unit(id)，任务用例锁定校验',
    issuer_unit_name_snapshot VARCHAR(80) NOT NULL COMMENT '创建时的发起单位名称快照，不随单位改名变化',
    issuer_user_id BIGINT NOT NULL COMMENT '发起人，引用 sys_user(id)，任务用例校验',
    title VARCHAR(160) NOT NULL COMMENT '任务标题，发送后不可修改',
    instruction TEXT NOT NULL COMMENT '任务要求，接口限制不超过 4000 字符，发送后不可修改',
    expected_result VARCHAR(1000) NOT NULL COMMENT '交付目标与判断口径，发送后不可修改',
    initial_due_at DATETIME(6) NOT NULL COMMENT '初次整单截止时刻，UTC，不可改写',
    current_due_at DATETIME(6) NOT NULL COMMENT '当前经发起单位批准的整单截止时刻，UTC',
    status VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'OPEN'
        COMMENT '整单状态：OPEN 办理中，COMPLETED 全部分支已有正式结果',
    completed_at DATETIME(6) NULL COMMENT '全部分支交付结果后的显式办结时刻，UTC；未办结为 NULL',
    version INT NOT NULL DEFAULT 0 COMMENT '整单并发版本，成功状态或期限变化时加一',
    source_result_id BIGINT NULL COMMENT '后续任务的来源结果，引用 task_result(id)，任务用例锁定校验',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时刻，UTC',
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '最近修改时刻，UTC，由写入 SQL 显式更新',
    PRIMARY KEY (id),
    CONSTRAINT uk_task_order_no UNIQUE (task_no),
    CONSTRAINT ck_task_order_status CHECK (status IN ('OPEN', 'COMPLETED')),
    CONSTRAINT ck_task_order_version CHECK (version >= 0),
    CONSTRAINT ck_task_order_due CHECK (current_due_at >= initial_due_at),
    INDEX idx_task_order_issuer (issuer_unit_id, created_at, id),
    INDEX idx_task_order_source_result (source_result_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='task 模块：一项任务及其原始和当前整单期限';

CREATE TABLE task_branch (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '责任分支主键，API 以十进制字符串返回',
    task_id BIGINT NOT NULL COMMENT '所属任务，引用 task_order(id)，任务用例锁定校验',
    parent_branch_id BIGINT NULL COMMENT '向下下发来源分支，引用 task_branch(id)；初次下发为 NULL',
    origin_from_unit_id BIGINT NOT NULL COMMENT '首次发送单位，引用 sys_unit(id)，任务用例锁定校验',
    origin_to_unit_id BIGINT NOT NULL COMMENT '首次接收单位，引用 sys_unit(id)，任务用例锁定校验',
    instruction_snapshot TEXT NOT NULL COMMENT '创建分支时的具体要求快照，接口限制不超过 4000 字符',
    expected_result_snapshot VARCHAR(1000) NOT NULL COMMENT '创建分支时的交付目标快照',
    current_assignment_id BIGINT NULL COMMENT '当前承办段，引用 task_assignment(id)，与分支一致由任务事务维护',
    status VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'OPEN'
        COMMENT '分支状态：OPEN 尚无正式结果，COMPLETED 已有唯一正式结果',
    completed_at DATETIME(6) NULL COMMENT '正式提交分支结果的时刻，UTC；未完成为 NULL',
    version INT NOT NULL DEFAULT 0 COMMENT '分支并发版本，当前责任或状态变化时加一',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '分支创建时刻，UTC',
    PRIMARY KEY (id),
    CONSTRAINT ck_task_branch_status CHECK (status IN ('OPEN', 'COMPLETED')),
    CONSTRAINT ck_task_branch_version CHECK (version >= 0),
    INDEX idx_task_branch_tree (task_id, parent_branch_id),
    INDEX idx_task_branch_parent (parent_branch_id),
    INDEX idx_task_branch_origin_from (origin_from_unit_id),
    INDEX idx_task_branch_origin_to (origin_to_unit_id),
    INDEX idx_task_branch_current_assignment (current_assignment_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='task 模块：一次向下下发产生的一份必须交付结果的责任义务';

CREATE TABLE task_assignment (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '承办段主键，API 以十进制字符串返回',
    branch_id BIGINT NOT NULL COMMENT '所属责任分支，引用 task_branch(id)，任务用例锁定校验',
    previous_assignment_id BIGINT NULL COMMENT '前一承办段，引用 task_assignment(id)；首段为 NULL',
    from_unit_id BIGINT NOT NULL COMMENT '本段发送单位，引用 sys_unit(id)，任务用例锁定校验',
    from_unit_name_snapshot VARCHAR(80) NOT NULL COMMENT '本段建立时发送单位名称快照',
    to_unit_id BIGINT NOT NULL COMMENT '本段负责单位，引用 sys_unit(id)，任务用例锁定校验',
    to_unit_name_snapshot VARCHAR(80) NOT NULL COMMENT '本段建立时负责单位名称快照',
    source_action VARCHAR(20) CHARACTER SET ascii COLLATE ascii_bin NOT NULL
        COMMENT '产生原因：DOWNWARD 首次下发，RETURN 退回，REASSIGN 重新派发，PEER_TRANSFER 支队交接',
    due_at DATETIME(6) NOT NULL COMMENT '本段责任截止时刻，UTC；交接后旧段不改写',
    status VARCHAR(20) CHARACTER SET ascii COLLATE ascii_bin NOT NULL
        COMMENT '承办状态：PENDING_ACCEPT 待承接，IN_PROGRESS 办理中，RETURNED 退回，TRANSFERRED 平级交接，REASSIGNED 重新派发，COMPLETED 结果已提交',
    accepted_by_user_id BIGINT NULL COMMENT '承接人，引用 sys_user(id)；尚未承接为 NULL',
    accepted_at DATETIME(6) NULL COMMENT '承接时刻，UTC；支队交接为总队批准时刻',
    ended_by_user_id BIGINT NULL COMMENT '结束本段的操作人，引用 sys_user(id)；活跃段为 NULL',
    ended_at DATETIME(6) NULL COMMENT '退回、转交、重新派发或提交结果时刻，UTC；活跃段为 NULL',
    end_reason VARCHAR(1000) NULL COMMENT '结束本段的原因说明；未结束或无补充原因时为 NULL',
    version INT NOT NULL DEFAULT 0 COMMENT '承办段并发版本，状态变更时加一',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '本段建立时刻，UTC',
    active_branch_id BIGINT GENERATED ALWAYS AS
        (CASE WHEN status IN ('PENDING_ACCEPT', 'IN_PROGRESS') THEN branch_id ELSE NULL END) STORED
        COMMENT '活跃段唯一性插槽；结束段为 NULL，可保留多条历史',
    PRIMARY KEY (id),
    CONSTRAINT uk_task_assignment_active UNIQUE (active_branch_id),
    CONSTRAINT ck_task_assignment_source CHECK (source_action IN ('DOWNWARD', 'RETURN', 'REASSIGN', 'PEER_TRANSFER')),
    CONSTRAINT ck_task_assignment_status CHECK (status IN ('PENDING_ACCEPT', 'IN_PROGRESS', 'RETURNED', 'TRANSFERRED', 'REASSIGNED', 'COMPLETED')),
    CONSTRAINT ck_task_assignment_version CHECK (version >= 0),
    INDEX idx_task_assignment_branch (branch_id, id),
    INDEX idx_task_assignment_inbox (to_unit_id, status, due_at, id),
    INDEX idx_task_assignment_from (from_unit_id),
    INDEX idx_task_assignment_previous (previous_assignment_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='task 模块：同一分支上的历次承办责任；一条分支最多一个活跃承办段';

CREATE TABLE task_transfer_request (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '支队交接申请主键，API 以十进制字符串返回',
    branch_id BIGINT NOT NULL COMMENT '申请所属分支，引用 task_branch(id)，任务用例锁定校验',
    from_assignment_id BIGINT NOT NULL COMMENT '发起交接时的承办段，引用 task_assignment(id)',
    target_unit_id BIGINT NOT NULL COMMENT '拟接收支队，引用 sys_unit(id)，任务用例锁定校验',
    reason VARCHAR(1000) NOT NULL COMMENT '申请平级交接的业务原因',
    work_done TEXT NOT NULL COMMENT '甲支队已完成的工作，接口限制不超过 4000 字符',
    evidence_summary TEXT NOT NULL COMMENT '辖区或事实依据摘要，接口限制不超过 4000 字符',
    remaining_work TEXT NOT NULL COMMENT '乙支队仍需完成的事项，接口限制不超过 4000 字符',
    requested_by_user_id BIGINT NOT NULL COMMENT '申请人，引用 sys_user(id)',
    requested_at DATETIME(6) NOT NULL COMMENT '交接申请时刻，UTC',
    status VARCHAR(20) CHARACTER SET ascii COLLATE ascii_bin NOT NULL
        COMMENT '交接状态：AWAITING_TARGET 待乙答复，AWAITING_ISSUER 待总队决定，APPROVED 已批准，TARGET_DECLINED 乙拒绝，ISSUER_REJECTED 总队拒绝，WITHDRAWN 甲撤回',
    target_responded_by_user_id BIGINT NULL COMMENT '乙支队答复人，引用 sys_user(id)；未答复为 NULL',
    target_responded_at DATETIME(6) NULL COMMENT '乙支队答复时刻，UTC；未答复为 NULL',
    target_response_reason VARCHAR(1000) NULL COMMENT '乙支队拒绝理由或同意说明',
    target_required_duration_minutes INT NULL COMMENT '乙从批准时起所需最短办理分钟数；同意时为正数，其他情况 NULL',
    approved_due_at DATETIME(6) NULL COMMENT '总队批准给乙的截止时刻，UTC；未批准为 NULL',
    issuer_decided_by_user_id BIGINT NULL COMMENT '总队决定人，引用 sys_user(id)；未决定为 NULL',
    issuer_decided_at DATETIME(6) NULL COMMENT '总队决定时刻，UTC；未决定为 NULL',
    issuer_decision_reason VARCHAR(1000) NULL COMMENT '总队拒绝或整单延期原因；无补充时为 NULL',
    version INT NOT NULL DEFAULT 0 COMMENT '交接申请并发版本，答复或决定时加一',
    active_branch_id BIGINT GENERATED ALWAYS AS
        (CASE WHEN status IN ('AWAITING_TARGET', 'AWAITING_ISSUER') THEN branch_id ELSE NULL END) STORED
        COMMENT '同分支未决申请唯一性插槽；已决申请为 NULL',
    PRIMARY KEY (id),
    CONSTRAINT uk_task_transfer_pending UNIQUE (active_branch_id),
    CONSTRAINT ck_task_transfer_status CHECK (status IN ('AWAITING_TARGET', 'AWAITING_ISSUER', 'APPROVED', 'TARGET_DECLINED', 'ISSUER_REJECTED', 'WITHDRAWN')),
    CONSTRAINT ck_task_transfer_duration CHECK (target_required_duration_minutes IS NULL OR target_required_duration_minutes > 0),
    CONSTRAINT ck_task_transfer_version CHECK (version >= 0),
    INDEX idx_task_transfer_branch (branch_id, id),
    INDEX idx_task_transfer_target (target_unit_id, status, requested_at),
    INDEX idx_task_transfer_assignment (from_assignment_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='task 模块：支队间责任交接申请、乙支队确认及总队决定';

CREATE TABLE task_result (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '正式结果主键，API 以十进制字符串返回',
    branch_id BIGINT NOT NULL COMMENT '结果所属分支，引用 task_branch(id)，每分支唯一',
    assignment_id BIGINT NOT NULL COMMENT '提交时当前承办段，引用 task_assignment(id)，任务用例校验',
    outcome_code VARCHAR(24) CHARACTER SET ascii COLLATE ascii_bin NOT NULL
        COMMENT '结果类型：FULFILLED 达成，PARTIAL 部分完成，OUT_OF_JURISDICTION 转出辖区，UNABLE_TO_VERIFY 无法核实',
    handling_detail TEXT NOT NULL COMMENT '实际办理经过，接口限制不超过 4000 字符',
    conclusion TEXT NOT NULL COMMENT '明确结论，接口限制不超过 4000 字符',
    suggested_unit_id BIGINT NULL COMMENT '建议后续办理单位，引用 sys_unit(id)；仅是线索，不自动派发',
    submitted_by_user_id BIGINT NOT NULL COMMENT '结果提交人，引用 sys_user(id)',
    submitted_at DATETIME(6) NOT NULL COMMENT '正式结果提交时刻，UTC',
    PRIMARY KEY (id),
    CONSTRAINT uk_task_result_branch UNIQUE (branch_id),
    CONSTRAINT ck_task_result_outcome CHECK (outcome_code IN ('FULFILLED', 'PARTIAL', 'OUT_OF_JURISDICTION', 'UNABLE_TO_VERIFY')),
    INDEX idx_task_result_assignment (assignment_id),
    INDEX idx_task_result_suggested_unit (suggested_unit_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='task 模块：每分支一份不可覆盖的正式处置结果';

CREATE TABLE task_action (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '追加式业务历史主键',
    task_id BIGINT NOT NULL COMMENT '所属任务，引用 task_order(id)，任务用例维护',
    branch_id BIGINT NULL COMMENT '关联分支，引用 task_branch(id)；整单动作可为 NULL',
    assignment_id BIGINT NULL COMMENT '关联承办段，引用 task_assignment(id)；无承办段时为 NULL',
    transfer_request_id BIGINT NULL COMMENT '关联交接申请，引用 task_transfer_request(id)；非交接动作时为 NULL',
    action_code VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '稳定动作编码，如 CREATE、ACCEPT、TRANSFER_APPROVE',
    actor_unit_id BIGINT NOT NULL COMMENT '操作单位，引用 sys_unit(id)',
    actor_user_id BIGINT NOT NULL COMMENT '操作人，引用 sys_user(id)',
    target_unit_id BIGINT NULL COMMENT '动作目标单位，引用 sys_unit(id)；无目标时为 NULL',
    old_due_at DATETIME(6) NULL COMMENT '被调整对象的原期限，UTC；非改期动作为 NULL',
    new_due_at DATETIME(6) NULL COMMENT '被调整对象的新期限，UTC；非改期动作为 NULL',
    note TEXT NULL COMMENT '动作说明，接口限制不超过 4000 字符；无说明时为 NULL',
    occurred_at DATETIME(6) NOT NULL COMMENT '业务动作发生时刻，UTC',
    PRIMARY KEY (id),
    INDEX idx_task_action_history (task_id, occurred_at, id),
    INDEX idx_task_action_branch (branch_id),
    INDEX idx_task_action_assignment (assignment_id),
    INDEX idx_task_action_transfer (transfer_request_id),
    INDEX idx_task_action_actor_unit (actor_unit_id),
    INDEX idx_task_action_target_unit (target_unit_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='task 模块：不可覆盖的任务、分支、交接及期限业务历史';

CREATE TABLE task_command (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '幂等命令主键',
    actor_unit_id BIGINT NOT NULL COMMENT '发起意图的单位，引用 sys_unit(id)',
    action_code VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '请求的写动作编码',
    idempotency_key VARCHAR(80) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '客户端同一写入意图的稳定键',
    request_digest CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '规范化有效命令字段的 SHA-256 十六进制摘要',
    task_id BIGINT NOT NULL COMMENT '成功命令对应任务，引用 task_order(id)',
    branch_id BIGINT NULL COMMENT '成功命令对应分支，引用 task_branch(id)；整单命令为 NULL',
    created_at DATETIME(6) NOT NULL COMMENT '命令成功提交时刻，UTC',
    PRIMARY KEY (id),
    CONSTRAINT uk_task_command_intent UNIQUE (actor_unit_id, action_code, idempotency_key),
    INDEX idx_task_command_task (task_id),
    INDEX idx_task_command_branch (branch_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='task 模块：发送与状态写入的持久幂等结果';

INSERT INTO sys_dict_type (dict_code, dict_name, description)
SELECT 'task.result.outcome', '任务处置结果', '正式结果的稳定取值与显示标签'
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_dict_type WHERE dict_code = 'task.result.outcome');

INSERT INTO sys_dict_item (dict_type_id, item_value, item_label, description, sort_order)
SELECT t.id, seed.item_value, seed.item_label, seed.description, seed.sort_order
  FROM (SELECT 'FULFILLED' AS item_value, '目标达成' AS item_label, '任务目标已经完成' AS description, 10 AS sort_order
        UNION ALL SELECT 'PARTIAL', '部分完成', '已有成果但仍需后续处置', 20
        UNION ALL SELECT 'OUT_OF_JURISDICTION', '转出辖区', '查明事项不在本单位辖区，附事实依据', 30
        UNION ALL SELECT 'UNABLE_TO_VERIFY', '无法核实', '有明确办理经过与无法核实原因', 40) seed
  JOIN sys_dict_type t ON t.dict_code = 'task.result.outcome'
 WHERE NOT EXISTS (SELECT 1 FROM sys_dict_item i WHERE i.dict_type_id = t.id AND i.item_value = seed.item_value);

INSERT INTO sys_permission (permission_code, permission_name, description)
VALUES ('task:read', '任务处置 · 查看', '按参与单位查看任务列表与详情'),
       ('task:create', '任务处置 · 创建', '向直属下级创建并下发任务'),
       ('task:accept', '任务处置 · 承接', '承接本单位当前待办分支'),
       ('task:progress', '任务处置 · 进展', '追加本单位办理进展'),
       ('task:dispatch', '任务处置 · 下发', '向直属下级新增独立责任分支'),
       ('task:return', '任务处置 · 退回', '承接前说明原因退回原发送单位'),
       ('task:submit-result', '任务处置 · 结果', '提交本单位分支正式处置结果'),
       ('task:transfer-request', '任务处置 · 申请交接', '支队向另一支队发起责任交接申请'),
       ('task:transfer-respond', '任务处置 · 回应交接', '目标支队回应交接申请'),
       ('task:transfer-decide', '任务处置 · 决定交接', '总队批准或拒绝支队交接并确定期限');

INSERT INTO sys_menu (parent_id, menu_type, menu_name, sort_order, status, description)
SELECT NULL, 'DIRECTORY', '业务协同', 30, 'ENABLED', '任务处置与业务协同'
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_name = '业务协同' AND menu_type = 'DIRECTORY');

INSERT INTO sys_menu (parent_id, menu_type, menu_name, route_key, permission_id, sort_order, status, description)
SELECT d.id, 'PAGE', '任务处置', 'collaboration.tasks', p.id, 10, 'ENABLED', '任务受理、下发与结果交付'
  FROM sys_menu d JOIN sys_permission p ON p.permission_code = 'task:read'
 WHERE d.menu_name = '业务协同' AND d.menu_type = 'DIRECTORY'
   AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE route_key = 'collaboration.tasks');

INSERT INTO sys_menu (parent_id, menu_type, menu_name, permission_id, sort_order, status, description)
SELECT m.id, 'BUTTON', seed.menu_name, p.id, seed.sort_order, 'ENABLED', p.description
  FROM (SELECT '新建任务' AS menu_name, 'task:create' AS code, 10 AS sort_order
        UNION ALL SELECT '承接任务', 'task:accept', 20
        UNION ALL SELECT '追加进展', 'task:progress', 30
        UNION ALL SELECT '向下下发', 'task:dispatch', 40
        UNION ALL SELECT '退回任务', 'task:return', 50
        UNION ALL SELECT '提交结果', 'task:submit-result', 60
        UNION ALL SELECT '申请支队交接', 'task:transfer-request', 70
        UNION ALL SELECT '回应支队交接', 'task:transfer-respond', 80
        UNION ALL SELECT '决定支队交接', 'task:transfer-decide', 90) seed
  JOIN sys_menu m ON m.route_key = 'collaboration.tasks'
  JOIN sys_permission p ON p.permission_code = seed.code
 WHERE NOT EXISTS (SELECT 1 FROM sys_menu existing WHERE existing.permission_id = p.id);
