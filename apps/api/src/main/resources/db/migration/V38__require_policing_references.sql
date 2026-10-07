-- maritime：回填完成后收紧引用约束；未完成映射将明确失败，不删除原档案。
ALTER TABLE archive_police_station MODIFY COLUMN unit_id BIGINT NOT NULL COMMENT '所属支队或大队，引用 sys_unit(id)，由应用引用锁及删除保护维护';
ALTER TABLE archive_port_officer MODIFY COLUMN user_id BIGINT NOT NULL COMMENT '人员身份，引用 sys_user(id)，由引用锁保护，同一用户仅有一份成员档案',
    ADD UNIQUE KEY uk_archive_port_officer_user (user_id),
    DROP COLUMN name;
