-- maritime：派出所归属支队/大队；民警身份引用用户。只回填固定样例，不按姓名匹配。
ALTER TABLE archive_police_station ADD COLUMN unit_id BIGINT NULL COMMENT '所属支队或大队，引用 sys_unit(id)，由应用引用锁及删除保护维护' AFTER name,
    ADD INDEX idx_archive_police_station_unit (unit_id, id);
ALTER TABLE archive_port_officer ADD COLUMN user_id BIGINT NULL COMMENT '人员身份，引用 sys_user(id)，一个用户对应一份派出所成员档案' AFTER id;
UPDATE archive_police_station s JOIN sys_unit u ON u.unit_code='330206' SET s.unit_id=u.id WHERE s.fixture_key='station.ms' AND s.unit_id IS NULL;
UPDATE archive_police_station s JOIN sys_unit u ON u.unit_code='330903' SET s.unit_id=u.id WHERE s.fixture_key='station.sjm' AND s.unit_id IS NULL;
UPDATE archive_police_station s JOIN sys_unit u ON u.unit_code='330902' SET s.unit_id=u.id WHERE s.fixture_key='station.jt' AND s.unit_id IS NULL;
UPDATE archive_police_station s JOIN sys_unit u ON u.unit_code='330305' SET s.unit_id=u.id WHERE s.fixture_key='station.ba' AND s.unit_id IS NULL;
UPDATE archive_police_station s JOIN sys_unit u ON u.unit_code='331021' SET s.unit_id=u.id WHERE s.fixture_key='station.dmy' AND s.unit_id IS NULL;
UPDATE archive_police_station s JOIN sys_unit u ON u.unit_code='330498' SET s.unit_id=u.id WHERE s.fixture_key='station.zp' AND s.unit_id IS NULL;
UPDATE archive_port_officer o JOIN sys_user u ON u.login_name='demo.330206' JOIN archive_police_station s ON s.id=o.police_station_id AND s.unit_id=u.unit_id SET o.user_id=u.id WHERE o.fixture_key='officer.ms' AND o.user_id IS NULL;
UPDATE archive_port_officer o JOIN sys_user u ON u.login_name='demo.330903' JOIN archive_police_station s ON s.id=o.police_station_id AND s.unit_id=u.unit_id SET o.user_id=u.id WHERE o.fixture_key='officer.sjm' AND o.user_id IS NULL;
UPDATE archive_port_officer o JOIN sys_user u ON u.login_name='demo.330902' JOIN archive_police_station s ON s.id=o.police_station_id AND s.unit_id=u.unit_id SET o.user_id=u.id WHERE o.fixture_key='officer.jt' AND o.user_id IS NULL;
UPDATE archive_port_officer o JOIN sys_user u ON u.login_name='demo.330305' JOIN archive_police_station s ON s.id=o.police_station_id AND s.unit_id=u.unit_id SET o.user_id=u.id WHERE o.fixture_key='officer.ba' AND o.user_id IS NULL;
UPDATE archive_port_officer o JOIN sys_user u ON u.login_name='demo.331021' JOIN archive_police_station s ON s.id=o.police_station_id AND s.unit_id=u.unit_id SET o.user_id=u.id WHERE o.fixture_key='officer.dmy' AND o.user_id IS NULL;
UPDATE archive_port_officer o JOIN sys_user u ON u.login_name='demo.330498' JOIN archive_police_station s ON s.id=o.police_station_id AND s.unit_id=u.unit_id SET o.user_id=u.id WHERE o.fixture_key='officer.zp' AND o.user_id IS NULL;

-- 非样例档案须显式完成映射后再执行下一条约束迁移；未知映射不补造。
