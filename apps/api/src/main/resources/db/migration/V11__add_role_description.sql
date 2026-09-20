-- owner: system 模块（角色）
--
-- 目的：给角色补一段说明，供角色管理页与用户表单解释「这个角色能用什么功能」。
-- 边界：
--   * 仅用于展示，不参与任何授权判定；授权依据始终是角色编码与 sys_role_permission。
--   * 数据范围（单位/层级可见性）仍属后续独立设计，本表不预建对应字段。
-- 范围：只加一列，不改动已有角色数据；历史行的 description 保持 NULL。

ALTER TABLE sys_role
    ADD COLUMN description VARCHAR(200) NULL
        COMMENT '角色说明；仅用于展示，不参与授权判定' AFTER role_name;
