-- owner: task。结果类型的取值集合仍由代码与 CHECK 固定，字典只维护显示与可选性。
ALTER TABLE task_result DROP CHECK ck_task_result_outcome;
ALTER TABLE task_result ADD CONSTRAINT ck_task_result_outcome CHECK
    (outcome_code IN ('FULFILLED', 'NOT_FOUND', 'PARTIAL', 'OUT_OF_JURISDICTION', 'UNABLE_TO_VERIFY'));
ALTER TABLE task_result MODIFY outcome_code VARCHAR(24) CHARACTER SET ascii COLLATE ascii_bin NOT NULL
    COMMENT '结果类型：FULFILLED 目标达成，NOT_FOUND 经核查未发现，PARTIAL 部分完成，OUT_OF_JURISDICTION 转出辖区，UNABLE_TO_VERIFY 无法核实';
