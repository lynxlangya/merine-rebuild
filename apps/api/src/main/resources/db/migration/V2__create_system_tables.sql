-- owner: system 模块（单位 / 角色 / 用户）
--
-- 目的：建立登录与身份恢复所需的最小用户、单位与角色关系。
-- 范围边界：
--   * 功能权限、数据范围策略与完整的用户管理后台留到后续阶段，本迁移不建预留表。
--   * 单位层级（单位下的部门）本轮没有真实调用方，不建自引用列。
--   * 演示账号由 scripts/dev.sh seed 单独初始化，不写在迁移里：迁移不含任何口令，
--     也不会在每次启动时重置已有账号。

CREATE TABLE sys_unit (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '单位主键，API 以十进制字符串返回',
    unit_code VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL
        COMMENT '单位业务编码，区分大小写，全局唯一，停用后不复用',
    unit_name VARCHAR(80) NOT NULL COMMENT '单位显示名称，不参与关联，可修改',
    status VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'ENABLED'
        COMMENT '单位状态：ENABLED 启用，DISABLED 停用；停用后其用户不可登录',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时刻，UTC',
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
        COMMENT '最近修改时刻，UTC，由修改 SQL 显式更新',
    PRIMARY KEY (id),
    CONSTRAINT uk_sys_unit_code UNIQUE (unit_code),
    CONSTRAINT ck_sys_unit_status CHECK (status IN ('ENABLED', 'DISABLED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='system 模块：单位；本轮只承载用户归属，数据范围策略后续单独设计';

CREATE TABLE sys_role (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '角色主键，API 以十进制字符串返回',
    role_code VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL
        COMMENT '稳定角色编码，区分大小写，全局唯一，停用后不复用',
    role_name VARCHAR(80) NOT NULL COMMENT '角色显示名称，不参与关联，可修改',
    status VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'ENABLED'
        COMMENT '角色状态：ENABLED 启用，DISABLED 停用',
    version INT NOT NULL DEFAULT 0 COMMENT '并发编辑版本，每次成功修改加一',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时刻，UTC',
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
        COMMENT '最近修改时刻，UTC，由修改 SQL 显式更新',
    PRIMARY KEY (id),
    CONSTRAINT uk_sys_role_code UNIQUE (role_code),
    CONSTRAINT ck_sys_role_status CHECK (status IN ('ENABLED', 'DISABLED')),
    CONSTRAINT ck_sys_role_version CHECK (version >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='system 模块：角色定义；本轮只用于登录后展示与授权判定，功能权限表后续再加';

CREATE TABLE sys_user (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '用户主键，API 以十进制字符串返回',
    login_name VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL
        COMMENT '登录名，区分大小写且按大小写判重，全局唯一，停用后不复用',
    display_name VARCHAR(80) NOT NULL COMMENT '用户显示名称；本地环境使用合成名称，不填真实姓名',
    password_hash VARCHAR(100) CHARACTER SET ascii COLLATE ascii_bin NOT NULL
        COMMENT '口令单向哈希（BCrypt）；禁止明文或可逆保存，接口响应与日志均不得输出本列',
    unit_id BIGINT NOT NULL COMMENT '所属单位，引用 sys_unit(id)；用户的数据范围以此为起点',
    status VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'ENABLED'
        COMMENT '账号状态：ENABLED 启用，DISABLED 停用；停用后已有会话在下次请求即失效',
    authorization_version INT NOT NULL DEFAULT 0
        COMMENT '授权版本；角色或数据范围变化时加一，已有会话持旧版本即被判定失效',
    last_login_at DATETIME(6) NULL COMMENT '最近一次成功登录时刻，UTC；从未登录为 NULL',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时刻，UTC',
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
        COMMENT '最近修改时刻，UTC，由修改 SQL 显式更新',
    PRIMARY KEY (id),
    CONSTRAINT uk_sys_user_login_name UNIQUE (login_name),
    CONSTRAINT fk_sys_user_unit FOREIGN KEY (unit_id) REFERENCES sys_unit (id),
    CONSTRAINT ck_sys_user_status CHECK (status IN ('ENABLED', 'DISABLED')),
    CONSTRAINT ck_sys_user_authorization_version CHECK (authorization_version >= 0),
    INDEX idx_sys_user_unit (unit_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='system 模块：用户账号与登录凭据；权限与数据范围不存放在本表';

CREATE TABLE sys_user_role (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '授予关系主键，仅用于约束与审计',
    user_id BIGINT NOT NULL COMMENT '被授予的用户，引用 sys_user(id)',
    role_id BIGINT NOT NULL COMMENT '授予的角色，引用 sys_role(id)',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '授予时刻，UTC',
    PRIMARY KEY (id),
    CONSTRAINT uk_sys_user_role UNIQUE (user_id, role_id),
    CONSTRAINT fk_sys_user_role_user FOREIGN KEY (user_id) REFERENCES sys_user (id),
    CONSTRAINT fk_sys_user_role_role FOREIGN KEY (role_id) REFERENCES sys_role (id),
    INDEX idx_sys_user_role_role (role_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='system 模块：用户与角色的授予关系；一个用户可有多个角色';
