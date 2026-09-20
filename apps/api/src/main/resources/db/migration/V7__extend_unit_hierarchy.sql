-- owner: system 模块（单位）
--
-- 目的：把只承载用户归属的扁平单位表扩展为三级组织树。
-- 范围：只增加结构列、约束与索引；不修改已有单位名称、编码和状态，
--       真实单位数据由 V8 单独导入。
-- 层级语义：1 总队，2 支队，3 大队；顶级单位的 parent_id 为 NULL。
-- MySQL 不允许 CHECK 引用自增列，因此“上级不能是自己”由 UnitAdminService 校验，
-- 自引用外键只保证上级必须存在。

ALTER TABLE sys_unit
    ADD COLUMN parent_id BIGINT NULL
        COMMENT '上级单位，引用 sys_unit(id)；一级单位为 NULL' AFTER unit_name,
    ADD COLUMN unit_level TINYINT NOT NULL DEFAULT 1
        COMMENT '单位层级：1 总队，2 支队，3 大队' AFTER parent_id,
    ADD COLUMN area_code VARCHAR(12) CHARACTER SET ascii COLLATE ascii_bin NULL
        COMMENT '行政区划代码；来自组织参考数据，不参与单位关联' AFTER unit_level,
    ADD COLUMN version INT NOT NULL DEFAULT 0
        COMMENT '编辑版本；成功修改名称、上级或行政区划时加一，拒绝旧版本覆盖' AFTER status,
    ADD CONSTRAINT ck_sys_unit_level CHECK (unit_level BETWEEN 1 AND 3),
    ADD CONSTRAINT ck_sys_unit_version CHECK (version >= 0),
    ADD CONSTRAINT fk_sys_unit_parent FOREIGN KEY (parent_id) REFERENCES sys_unit (id),
    ADD INDEX idx_sys_unit_parent (parent_id);
