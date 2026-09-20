-- owner: system 模块（用户）
--
-- 目的：明确 sys_user.authorization_version 的语义。
-- 背景：用户管理接口开放后，除角色与所属单位变化外，管理员重置密码同样要让
--       该账号的已有会话失效；这一列就是「必须让旧会话失效」的统一计数器。
-- 范围：只改列注释，不改类型与数据。V2 已执行，按规则用新迁移同步注释。

ALTER TABLE sys_user
    MODIFY COLUMN authorization_version INT NOT NULL DEFAULT 0
        COMMENT '身份与授权版本；角色、所属单位或密码变化时加一，已有会话持旧版本即被判定失效';
