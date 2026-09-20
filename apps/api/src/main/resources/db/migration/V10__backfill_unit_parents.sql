-- owner: system 模块（单位）
--
-- 目的：修复 V8 的单条 INSERT ... SELECT 在 MySQL 中看不到同批新增父行的问题。
-- 行为：按单位编码把 parent_id 回填为真实上级；V8 已写入的层级和行政区划不变。
-- 影响：全新数据库按 V8 → V10 连续执行后得到完整三级树；已执行 V8 的本地开发库
--       也会被这条迁移修正。旧项目和其他数据库不受影响。

CREATE TEMPORARY TABLE tmp_sys_unit_parent (
    unit_code VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL
        COMMENT '单位编码',
    parent_code VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL
        COMMENT '上级单位编码；一级单位为 NULL',
    PRIMARY KEY (unit_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO tmp_sys_unit_parent (unit_code, parent_code)
VALUES
    ('ORG_001', NULL),
    ('ORG_002', 'ORG_001'),
    ('ORG_003', 'ORG_001'),
    ('ORG_004', 'ORG_001'),
    ('ORG_005', 'ORG_001'),
    ('ORG_007', 'ORG_001'),
    ('ORG_010', 'ORG_001'),
    ('ORG_011', 'ORG_001'),
    ('330114', 'ORG_002'),
    ('330203', 'ORG_003'),
    ('330205', 'ORG_003'),
    ('330206', 'ORG_003'),
    ('330211', 'ORG_003'),
    ('330212', 'ORG_003'),
    ('330225', 'ORG_003'),
    ('330226', 'ORG_003'),
    ('330281', 'ORG_003'),
    ('330282', 'ORG_003'),
    ('330283', 'ORG_003'),
    ('330302', 'ORG_004'),
    ('330303', 'ORG_004'),
    ('330305', 'ORG_004'),
    ('330324', 'ORG_004'),
    ('330326', 'ORG_004'),
    ('330327', 'ORG_004'),
    ('330381', 'ORG_004'),
    ('330382', 'ORG_004'),
    ('330383', 'ORG_004'),
    ('330394', 'ORG_004'),
    ('330424', 'ORG_005'),
    ('330481', 'ORG_005'),
    ('330482', 'ORG_005'),
    ('330498', 'ORG_005'),
    ('330602', 'ORG_007'),
    ('330603', 'ORG_007'),
    ('330604', 'ORG_007'),
    ('330902', 'ORG_010'),
    ('330903', 'ORG_010'),
    ('330905', 'ORG_010'),
    ('330921', 'ORG_010'),
    ('330922', 'ORG_010'),
    ('330998', 'ORG_010'),
    ('331002', 'ORG_011'),
    ('331003', 'ORG_011'),
    ('331004', 'ORG_011'),
    ('331021', 'ORG_011'),
    ('331022', 'ORG_011'),
    ('331081', 'ORG_011'),
    ('331082', 'ORG_011'),
    ('331099', 'ORG_011');

UPDATE sys_unit child
  JOIN tmp_sys_unit_parent t ON t.unit_code = child.unit_code
  LEFT JOIN sys_unit parent ON parent.unit_code = t.parent_code
   SET child.parent_id = parent.id,
       child.updated_at = CURRENT_TIMESTAMP(6)
 WHERE NOT (child.parent_id <=> parent.id);

DROP TEMPORARY TABLE tmp_sys_unit_parent;
