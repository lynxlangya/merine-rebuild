-- owner: system 模块（用户）
--
-- 目的：统一产品文案术语，把 sys_user.password_hash 的列注释由「口令」改为「密码」。
-- 范围：只改列注释，不改类型、字符集、可空性、约束与数据。
-- 说明：V2 已执行，按 docs/rules/database.md 不可改写，因此用新迁移同步注释。
--       MODIFY COLUMN 需要重写完整定义，否则会丢掉后面的属性。

ALTER TABLE sys_user
    MODIFY COLUMN password_hash VARCHAR(100) CHARACTER SET ascii COLLATE ascii_bin NOT NULL
        COMMENT '密码单向哈希（BCrypt）；禁止明文或可逆保存，接口响应与日志均不得输出本列';
