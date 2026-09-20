-- owner: system 模块（权限 / 菜单 / 角色）
--
-- 目的：建立功能权限的最小完整模型——权限码字典（sys_permission）、菜单资源树（sys_menu）、
--       角色-权限授予关系（sys_role_permission），并写入引导菜单与内置管理员角色行。
-- 边界：
--   * 权限码与菜单节点由菜单管理在线维护；权限码创建后不可修改（它是判权用的 authority）。
--   * 菜单停用只影响导航展示与「是否可再分配」，不改变已授权功能，也不替代后端判权。
--   * 数据范围（能看哪些业务数据）与操作留痕不在本轮，本迁移不建相关表或字段。
--   * 引导数据只是「默认起步」：菜单管理允许自由增删改，删到没有入口时用恢复默认菜单补齐。
-- 说明：本文件在本地开发库与测试库重建后重放；已执行重建前版本的库按交付说明重建，不就地改写历史。

CREATE TABLE sys_permission (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '权限主键，API 以权限码对外，不暴露本列',
    permission_code VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL
        COMMENT '权限码，形如 system:user:create；区分大小写，全局唯一，创建后不可修改',
    permission_name VARCHAR(80) NOT NULL COMMENT '权限名称，用于展示；可修改',
    description VARCHAR(200) NULL COMMENT '权限说明；仅用于展示',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时刻，UTC',
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '最近修改时刻，UTC',
    PRIMARY KEY (id),
    CONSTRAINT uk_sys_permission_code UNIQUE (permission_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='system 模块：功能权限码字典；一个权限项对应一个菜单节点（页面/页签/按钮）';

CREATE TABLE sys_menu (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '菜单节点主键，API 以十进制字符串返回',
    parent_id BIGINT NULL COMMENT '上级节点，引用 sys_menu(id)；顶层为 NULL',
    menu_type VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL
        COMMENT '节点类型：DIRECTORY 目录，PAGE 页面，TAB 页签，BUTTON 按钮',
    menu_name VARCHAR(80) NOT NULL COMMENT '节点名称，用于导航与授权树展示',
    route_key VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL
        COMMENT '前端已注册的路由 key，仅页面节点有值，全局唯一',
    permission_id BIGINT NULL
        COMMENT '节点对应的权限码，引用 sys_permission(id)；目录节点为 NULL',
    sort_order INT NOT NULL DEFAULT 0 COMMENT '同级排序，越小越靠前',
    status VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'ENABLED'
        COMMENT '节点状态：ENABLED 启用，DISABLED 停用；停用只影响导航与可分配性',
    version INT NOT NULL DEFAULT 0 COMMENT '编辑版本；成功修改后加一，拒绝旧版本覆盖',
    description VARCHAR(200) NULL COMMENT '节点说明；仅用于展示',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时刻，UTC',
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
        COMMENT '最近修改时刻，UTC，由修改 SQL 显式更新',
    PRIMARY KEY (id),
    CONSTRAINT uk_sys_menu_route_key UNIQUE (route_key),
    CONSTRAINT fk_sys_menu_parent FOREIGN KEY (parent_id) REFERENCES sys_menu (id),
    CONSTRAINT fk_sys_menu_permission FOREIGN KEY (permission_id) REFERENCES sys_permission (id),
    CONSTRAINT ck_sys_menu_type CHECK (menu_type IN ('DIRECTORY', 'PAGE', 'TAB', 'BUTTON')),
    CONSTRAINT ck_sys_menu_status CHECK (status IN ('ENABLED', 'DISABLED')),
    CONSTRAINT ck_sys_menu_version CHECK (version >= 0),
    CONSTRAINT ck_sys_menu_sort_order CHECK (sort_order >= 0),
    CONSTRAINT ck_sys_menu_route_key CHECK (
        (menu_type = 'PAGE' AND route_key IS NOT NULL)
        OR (menu_type <> 'PAGE' AND route_key IS NULL)),
    CONSTRAINT ck_sys_menu_permission CHECK (
        (menu_type = 'DIRECTORY' AND permission_id IS NULL)
        OR (menu_type <> 'DIRECTORY' AND permission_id IS NOT NULL)),
    INDEX idx_sys_menu_parent (parent_id),
    INDEX idx_sys_menu_permission (permission_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='system 模块：菜单资源树；由权限码推导导航，隐藏菜单不替代后端判权';

CREATE TABLE sys_role_permission (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '授予关系主键，仅用于约束与审计',
    role_id BIGINT NOT NULL COMMENT '被授予的角色，引用 sys_role(id)',
    permission_id BIGINT NOT NULL COMMENT '授予的权限，引用 sys_permission(id)',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '授予时刻，UTC',
    PRIMARY KEY (id),
    CONSTRAINT uk_sys_role_permission UNIQUE (role_id, permission_id),
    CONSTRAINT fk_sys_role_permission_role FOREIGN KEY (role_id) REFERENCES sys_role (id),
    CONSTRAINT fk_sys_role_permission_permission FOREIGN KEY (permission_id)
        REFERENCES sys_permission (id),
    INDEX idx_sys_role_permission_permission (permission_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='system 模块：角色与功能权限的授予关系；内置角色的全部权限不写在本表';

-- 权限码字典：页面一个码，按钮一个码；代码里的常量（system/security/PermissionCodes）
-- 与本清单必须一致，一致性由 Java 回归测试核对。
INSERT INTO sys_permission (permission_code, permission_name, description)
VALUES
    ('system:user:read', '用户管理 · 查看', '查看用户列表与用户详情'),
    ('system:user:create', '用户管理 · 新建', '新建账号并设置初始密码'),
    ('system:user:update', '用户管理 · 编辑', '修改姓名、所属单位与角色'),
    ('system:user:toggle-status', '用户管理 · 启停', '启用或停用账号'),
    ('system:user:reset-password', '用户管理 · 重置密码', '为账号设置新密码'),
    ('system:role:read', '角色管理 · 查看', '查看角色列表、详情与成员'),
    ('system:role:create', '角色管理 · 新建', '新建角色'),
    ('system:role:update', '角色管理 · 编辑', '修改角色名称、说明与功能权限'),
    ('system:role:toggle-status', '角色管理 · 启停', '启用或停用角色'),
    ('system:role:delete', '角色管理 · 删除', '删除无成员的角色'),
    ('system:menu:read', '菜单管理 · 查看', '查看菜单树与已注册页面清单'),
    ('system:menu:create', '菜单管理 · 新增', '新增目录、页面、页签或按钮节点'),
    ('system:menu:update', '菜单管理 · 编辑', '修改节点名称、上级、排序、状态与说明'),
    ('system:menu:delete', '菜单管理 · 删除', '删除节点及其子树，并解除相关授权'),
    ('system:menu:restore', '菜单管理 · 恢复默认', '补齐缺失的引导菜单与权限码'),
    ('system:unit:read', '单位管理 · 查看', '查看组织树与单位选项'),
    ('system:unit:create', '单位管理 · 新增', '新增单位'),
    ('system:unit:update', '单位管理 · 编辑', '修改单位名称、上级与行政区划'),
    ('system:unit:delete', '单位管理 · 删除', '删除无下级且无用户的单位'),
    ('system:diagnostics:read', '工程诊断 · 查看', '查看探针记录与联调状态'),
    ('system:diagnostics:write', '工程诊断 · 写入', '写入探针记录');

-- 引导菜单：系统管理（目录）
INSERT INTO sys_menu (parent_id, menu_type, menu_name, sort_order, status, description)
VALUES (NULL, 'DIRECTORY', '系统管理', 10, 'ENABLED', '账号、角色、菜单与单位维护'),
       (NULL, 'DIRECTORY', '开发工具', 90, 'ENABLED', '本地联调与诊断入口');

-- 引导菜单：页面节点（必须绑定已注册的 route_key）
INSERT INTO sys_menu (parent_id, menu_type, menu_name, route_key, permission_id, sort_order, status,
                      description)
SELECT (SELECT id FROM sys_menu WHERE menu_name = '系统管理' AND menu_type = 'DIRECTORY'),
       'PAGE', '用户管理', 'system.users', p.id, 10, 'ENABLED', '账号、所属单位与角色维护'
  FROM sys_permission p WHERE p.permission_code = 'system:user:read';

INSERT INTO sys_menu (parent_id, menu_type, menu_name, route_key, permission_id, sort_order, status,
                      description)
SELECT (SELECT id FROM sys_menu WHERE menu_name = '系统管理' AND menu_type = 'DIRECTORY'),
       'PAGE', '角色管理', 'system.roles', p.id, 20, 'ENABLED', '角色定义与功能权限分配'
  FROM sys_permission p WHERE p.permission_code = 'system:role:read';

INSERT INTO sys_menu (parent_id, menu_type, menu_name, route_key, permission_id, sort_order, status,
                      description)
SELECT (SELECT id FROM sys_menu WHERE menu_name = '系统管理' AND menu_type = 'DIRECTORY'),
       'PAGE', '菜单管理', 'system.menus', p.id, 30, 'ENABLED', '菜单树与按钮权限维护'
  FROM sys_permission p WHERE p.permission_code = 'system:menu:read';

INSERT INTO sys_menu (parent_id, menu_type, menu_name, route_key, permission_id, sort_order, status,
                      description)
SELECT (SELECT id FROM sys_menu WHERE menu_name = '系统管理' AND menu_type = 'DIRECTORY'),
       'PAGE', '单位管理', 'system.units', p.id, 40, 'ENABLED', '三级组织树维护'
  FROM sys_permission p WHERE p.permission_code = 'system:unit:read';

INSERT INTO sys_menu (parent_id, menu_type, menu_name, route_key, permission_id, sort_order, status,
                      description)
SELECT (SELECT id FROM sys_menu WHERE menu_name = '开发工具' AND menu_type = 'DIRECTORY'),
       'PAGE', '工程诊断', 'dev.diagnostics', p.id, 10, 'ENABLED', '页面 → API → MySQL 的最小链路'
  FROM sys_permission p WHERE p.permission_code = 'system:diagnostics:read';

-- 引导菜单：按钮节点（挂在所属页面下）
INSERT INTO sys_menu (parent_id, menu_type, menu_name, permission_id, sort_order, status, description)
SELECT m.id, 'BUTTON', seed.menu_name, p.id, seed.sort_order, 'ENABLED', p.description
  FROM (
      SELECT 'system.users' AS route_key, '新建用户' AS menu_name, 'system:user:create' AS code, 10 AS sort_order
      UNION ALL SELECT 'system.users', '编辑用户', 'system:user:update', 20
      UNION ALL SELECT 'system.users', '启停账号', 'system:user:toggle-status', 30
      UNION ALL SELECT 'system.users', '重置密码', 'system:user:reset-password', 40
      UNION ALL SELECT 'system.roles', '新建角色', 'system:role:create', 10
      UNION ALL SELECT 'system.roles', '编辑角色', 'system:role:update', 20
      UNION ALL SELECT 'system.roles', '启停角色', 'system:role:toggle-status', 30
      UNION ALL SELECT 'system.roles', '删除角色', 'system:role:delete', 40
      UNION ALL SELECT 'system.menus', '新增菜单项', 'system:menu:create', 10
      UNION ALL SELECT 'system.menus', '编辑菜单项', 'system:menu:update', 20
      UNION ALL SELECT 'system.menus', '删除菜单项', 'system:menu:delete', 30
      UNION ALL SELECT 'system.menus', '恢复默认菜单', 'system:menu:restore', 40
      UNION ALL SELECT 'system.units', '新增单位', 'system:unit:create', 10
      UNION ALL SELECT 'system.units', '编辑单位', 'system:unit:update', 20
      UNION ALL SELECT 'system.units', '删除单位', 'system:unit:delete', 30
      UNION ALL SELECT 'dev.diagnostics', '写入探针记录', 'system:diagnostics:write', 10
  ) AS seed
  JOIN sys_menu m ON m.route_key = seed.route_key
  JOIN sys_permission p ON p.permission_code = seed.code;

-- 内置管理员角色行：已存在（例如另一个环境先建过）则保持原样，不覆盖名称与状态。
INSERT INTO sys_role (role_code, role_name, description)
SELECT 'SYSTEM_ADMIN', '系统管理员', '内置管理员角色：恒拥有全部功能权限，不可删除'
  FROM DUAL
 WHERE NOT EXISTS (SELECT 1
                     FROM (SELECT role_code FROM sys_role) AS existing_role
                    WHERE existing_role.role_code = 'SYSTEM_ADMIN');
