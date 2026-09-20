-- owner: system 模块（用户）
-- 编辑版本用于检测过期表单；身份版本只负责让已有会话失效，两者独立。
-- 已有用户从版本 0 开始；不改动账号、密码或授予关系。
ALTER TABLE sys_user
    ADD COLUMN version INT NOT NULL DEFAULT 0
        COMMENT '编辑版本；基本信息、密码、角色或账号状态成功修改时加一，拒绝旧版本覆盖'
        AFTER authorization_version,
    ADD CONSTRAINT ck_sys_user_version CHECK (version >= 0),
    MODIFY COLUMN authorization_version INT NOT NULL DEFAULT 0
        COMMENT '身份与授权版本；角色、所属单位、密码或账号启停变化时加一，旧会话不可恢复使用';
