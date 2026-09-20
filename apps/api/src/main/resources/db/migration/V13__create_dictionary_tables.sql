-- owner: system 模块（字典 / 菜单 / 权限）
--
-- 目的：建立业务字典结构（字典类型 + 字典项），并把「字典管理」登记进权限码与菜单树。
-- 边界：
--   * 内置枚举（状态、菜单类型、单位层级）由代码登记（DictionaryCatalog），不落表、在线只读；
--     本迁移只建业务字典的两张表，不预置任何业务字典数据。
--   * 字典类型与字典项只停用、不删除：业务数据里存的是值，删掉后历史行只剩代码。
--   * dict_code 与 item_value 是稳定标识，创建后不可修改；标签、排序、说明、状态可改。
-- 范围：只新增两张表、三个权限码与「系统管理 → 字典管理」的菜单节点；不改 V2/V11/V12。

CREATE TABLE sys_dict_type (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '字典类型主键，API 以 dict_code 对外',
    dict_code VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL
        COMMENT '字典编码，形如 vessel.type；区分大小写，全局唯一，创建后不可修改',
    dict_name VARCHAR(80) NOT NULL COMMENT '字典名称，用于展示；可修改',
    description VARCHAR(200) NULL COMMENT '字典说明；仅用于展示',
    status VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'ENABLED'
        COMMENT '类型状态：ENABLED 启用，DISABLED 停用；停用后使用侧不再返回该字典的选项',
    version INT NOT NULL DEFAULT 0 COMMENT '编辑版本；成功修改后加一，拒绝旧版本覆盖',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时刻，UTC',
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
        COMMENT '最近修改时刻，UTC，由修改 SQL 显式更新',
    PRIMARY KEY (id),
    CONSTRAINT uk_sys_dict_type_code UNIQUE (dict_code),
    CONSTRAINT ck_sys_dict_type_status CHECK (status IN ('ENABLED', 'DISABLED')),
    CONSTRAINT ck_sys_dict_type_version CHECK (version >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='system 模块：业务字典类型；内置枚举由代码登记，不写本表';

CREATE TABLE sys_dict_item (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '字典项主键，API 以 item_value 对外',
    dict_type_id BIGINT NOT NULL COMMENT '所属字典类型，引用 sys_dict_type(id)',
    item_value VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL
        COMMENT '字典项取值，在所属类型内唯一，创建后不可修改；业务数据存的就是它',
    item_label VARCHAR(80) NOT NULL COMMENT '字典项标签，用于展示；可修改',
    description VARCHAR(200) NULL COMMENT '字典项说明；仅用于展示',
    sort_order INT NOT NULL DEFAULT 0 COMMENT '同类型内排序，越小越靠前',
    status VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'ENABLED'
        COMMENT '字典项状态：ENABLED 启用，DISABLED 停用；停用后不再出现在选择项里，但标签仍可解析',
    version INT NOT NULL DEFAULT 0 COMMENT '编辑版本；成功修改后加一，拒绝旧版本覆盖',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时刻，UTC',
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
        COMMENT '最近修改时刻，UTC，由修改 SQL 显式更新',
    PRIMARY KEY (id),
    CONSTRAINT uk_sys_dict_item_value UNIQUE (dict_type_id, item_value),
    CONSTRAINT fk_sys_dict_item_type FOREIGN KEY (dict_type_id) REFERENCES sys_dict_type (id),
    CONSTRAINT ck_sys_dict_item_status CHECK (status IN ('ENABLED', 'DISABLED')),
    CONSTRAINT ck_sys_dict_item_version CHECK (version >= 0),
    CONSTRAINT ck_sys_dict_item_sort_order CHECK (sort_order >= 0),
    INDEX idx_sys_dict_item_type (dict_type_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='system 模块：业务字典项；只停用不删除，历史数据仍能解析标签';

-- 字典管理的权限码：读字典（管理页）与写（新建/编辑）分开；
-- 使用侧读字典（/api/dictionaries）登录即可，不占用权限码。
INSERT INTO sys_permission (permission_code, permission_name, description)
VALUES
    ('system:dict:read', '字典管理 · 查看', '查看字典类型与字典项，含内置枚举'),
    ('system:dict:create', '字典管理 · 新建', '新建业务字典类型与字典项'),
    ('system:dict:update', '字典管理 · 编辑', '修改字典名称、说明、排序与启停状态');

-- 引导菜单：字典管理页面（挂在系统管理下）
INSERT INTO sys_menu (parent_id, menu_type, menu_name, route_key, permission_id, sort_order, status,
                      description)
SELECT (SELECT id FROM sys_menu WHERE menu_name = '系统管理' AND menu_type = 'DIRECTORY'),
       'PAGE', '字典管理', 'system.dictionaries', p.id, 50, 'ENABLED',
       '内置枚举与业务字典维护'
  FROM sys_permission p WHERE p.permission_code = 'system:dict:read';

-- 引导菜单：字典管理的按钮节点
INSERT INTO sys_menu (parent_id, menu_type, menu_name, permission_id, sort_order, status, description)
SELECT m.id, 'BUTTON', seed.menu_name, p.id, seed.sort_order, 'ENABLED', p.description
  FROM (
      SELECT '新建字典' AS menu_name, 'system:dict:create' AS code, 10 AS sort_order
      UNION ALL SELECT '编辑字典', 'system:dict:update', 20
  ) AS seed
  JOIN sys_menu m ON m.route_key = 'system.dictionaries'
  JOIN sys_permission p ON p.permission_code = seed.code;
