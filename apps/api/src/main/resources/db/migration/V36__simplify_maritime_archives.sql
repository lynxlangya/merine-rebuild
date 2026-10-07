-- 精简涉海基础档案，保留业务字段与责任关系；历史迁移不改写。

UPDATE archive_port SET location=COALESCE(NULLIF(location, ''), '浙江沿海，主要港区分布于宁波北仑、梅山及舟山金塘一带'), purpose=COALESCE(NULLIF(purpose, ''), '集装箱、散杂货及大宗货物运输'), version=version+1, updated_at=UTC_TIMESTAMP(6) WHERE fixture_key='port.nbzs';

UPDATE archive_port SET location=COALESCE(NULLIF(location, ''), '温州市沿海，港区分布于瓯江口、状元岙及乐清湾一带'), purpose=COALESCE(NULLIF(purpose, ''), '集装箱、散杂货与沿海客货运输'), version=version+1, updated_at=UTC_TIMESTAMP(6) WHERE fixture_key='port.wz';

UPDATE archive_port SET location=COALESCE(NULLIF(location, ''), '嘉兴市平湖沿海，主要港区位于乍浦、独山港一带'), purpose=COALESCE(NULLIF(purpose, ''), '集装箱、散货及临港产业货物运输'), version=version+1, updated_at=UTC_TIMESTAMP(6) WHERE fixture_key='port.jx';

UPDATE archive_port SET location=COALESCE(NULLIF(location, ''), '台州市沿海，主要港区分布于海门、健跳及大麦屿一带'), purpose=COALESCE(NULLIF(purpose, ''), '散杂货、集装箱与沿海客货运输'), version=version+1, updated_at=UTC_TIMESTAMP(6) WHERE fixture_key='port.tz';

UPDATE archive_police_station SET location=COALESCE(NULLIF(location, ''), '宁波市北仑区梅山街道'), version=version+1, updated_at=UTC_TIMESTAMP(6) WHERE fixture_key='station.ms';

UPDATE archive_police_station SET location=COALESCE(NULLIF(location, ''), '舟山市普陀区沈家门街道'), version=version+1, updated_at=UTC_TIMESTAMP(6) WHERE fixture_key='station.sjm';

UPDATE archive_police_station SET location=COALESCE(NULLIF(location, ''), '舟山市定海区金塘镇'), version=version+1, updated_at=UTC_TIMESTAMP(6) WHERE fixture_key='station.jt';

UPDATE archive_police_station SET location=COALESCE(NULLIF(location, ''), '温州市洞头区北岙街道'), version=version+1, updated_at=UTC_TIMESTAMP(6) WHERE fixture_key='station.ba';

UPDATE archive_police_station SET location=COALESCE(NULLIF(location, ''), '台州市玉环市大麦屿街道'), version=version+1, updated_at=UTC_TIMESTAMP(6) WHERE fixture_key='station.dmy';

UPDATE archive_police_station SET location=COALESCE(NULLIF(location, ''), '嘉兴市平湖市乍浦镇'), version=version+1, updated_at=UTC_TIMESTAMP(6) WHERE fixture_key='station.zp';

UPDATE archive_port_officer SET name=CASE WHEN name='演示民警01' THEN '陈浩' ELSE name END, duty=CASE WHEN duty='港区责任民警（演示）' THEN '港区责任民警' ELSE duty END, version=version+1, updated_at=UTC_TIMESTAMP(6) WHERE fixture_key='officer.ms';

UPDATE archive_port_officer SET name=CASE WHEN name='演示民警02' THEN '周明远' ELSE name END, duty=CASE WHEN duty='港区责任民警（演示）' THEN '港区责任民警' ELSE duty END, version=version+1, updated_at=UTC_TIMESTAMP(6) WHERE fixture_key='officer.sjm';

UPDATE archive_port_officer SET name=CASE WHEN name='演示民警03' THEN '林嘉诚' ELSE name END, duty=CASE WHEN duty='港区责任民警（演示）' THEN '港区责任民警' ELSE duty END, version=version+1, updated_at=UTC_TIMESTAMP(6) WHERE fixture_key='officer.jt';

UPDATE archive_port_officer SET name=CASE WHEN name='演示民警04' THEN '吴志航' ELSE name END, duty=CASE WHEN duty='港区责任民警（演示）' THEN '港区责任民警' ELSE duty END, version=version+1, updated_at=UTC_TIMESTAMP(6) WHERE fixture_key='officer.ba';

UPDATE archive_port_officer SET name=CASE WHEN name='演示民警05' THEN '徐立新' ELSE name END, duty=CASE WHEN duty='港区责任民警（演示）' THEN '港区责任民警' ELSE duty END, version=version+1, updated_at=UTC_TIMESTAMP(6) WHERE fixture_key='officer.dmy';

UPDATE archive_port_officer SET name=CASE WHEN name='演示民警06' THEN '沈亦凡' ELSE name END, duty=CASE WHEN duty='港区责任民警（演示）' THEN '港区责任民警' ELSE duty END, version=version+1, updated_at=UTC_TIMESTAMP(6) WHERE fixture_key='officer.zp';

UPDATE archive_wharf SET location=COALESCE(NULLIF(location, ''), '宁波市北仑区梅山港区'), purpose=COALESCE(NULLIF(purpose, ''), '集装箱装卸、堆存与中转'), version=version+1, updated_at=UTC_TIMESTAMP(6) WHERE fixture_key='wharf.ms';

UPDATE archive_wharf SET location=COALESCE(NULLIF(location, ''), '宁波市北仑区穿山港区'), purpose=COALESCE(NULLIF(purpose, ''), '集装箱装卸与国际航线中转'), version=version+1, updated_at=UTC_TIMESTAMP(6) WHERE fixture_key='wharf.cs';

UPDATE archive_wharf SET location=COALESCE(NULLIF(location, ''), '舟山市金塘岛大浦口港区'), purpose=COALESCE(NULLIF(purpose, ''), '集装箱装卸、堆存与物流服务'), version=version+1, updated_at=UTC_TIMESTAMP(6) WHERE fixture_key='wharf.dpk';

UPDATE archive_wharf SET location=COALESCE(NULLIF(location, ''), '温州市洞头区状元岙港区'), purpose=COALESCE(NULLIF(purpose, ''), '集装箱及散杂货装卸'), version=version+1, updated_at=UTC_TIMESTAMP(6) WHERE fixture_key='wharf.zya';

UPDATE archive_wharf SET location=COALESCE(NULLIF(location, ''), '嘉兴市平湖市独山港区'), purpose=COALESCE(NULLIF(purpose, ''), '散杂货装卸与临港物流'), version=version+1, updated_at=UTC_TIMESTAMP(6) WHERE fixture_key='wharf.dsg';

UPDATE archive_wharf SET location=COALESCE(NULLIF(location, ''), '台州市玉环市大麦屿港区'), purpose=COALESCE(NULLIF(purpose, ''), '集装箱及散杂货装卸'), version=version+1, updated_at=UTC_TIMESTAMP(6) WHERE fixture_key='wharf.dmy';

UPDATE archive_anchorage SET location=COALESCE(NULLIF(location, ''), '舟山市定海区马峙水道附近水域'), purpose=COALESCE(NULLIF(purpose, ''), '船舶候泊与临时锚泊'), version=version+1, updated_at=UTC_TIMESTAMP(6) WHERE fixture_key='anchorage.mz1';

UPDATE archive_anchorage SET location=COALESCE(NULLIF(location, ''), '宁波舟山港虾峙门水道北侧水域'), purpose=COALESCE(NULLIF(purpose, ''), '进出港船舶候泊与待引'), version=version+1, updated_at=UTC_TIMESTAMP(6) WHERE fixture_key='anchorage.xzm';

UPDATE archive_anchorage SET location=COALESCE(NULLIF(location, ''), '舟山市交杯山附近水域'), purpose=COALESCE(NULLIF(purpose, ''), '船舶候泊与临时锚泊'), version=version+1, updated_at=UTC_TIMESTAMP(6) WHERE fixture_key='anchorage.jbs';

UPDATE archive_island SET location=COALESCE(NULLIF(location, ''), '舟山市岱山县中部，岱山本岛'), version=version+1, updated_at=UTC_TIMESTAMP(6) WHERE fixture_key='island.1';

UPDATE archive_island SET location=COALESCE(NULLIF(location, ''), '舟山市岱山县东北部，衢山镇'), version=version+1, updated_at=UTC_TIMESTAMP(6) WHERE fixture_key='island.2';

UPDATE archive_island SET location=COALESCE(NULLIF(location, ''), '舟山市岱山县南部，秀山乡'), version=version+1, updated_at=UTC_TIMESTAMP(6) WHERE fixture_key='island.3';

UPDATE archive_island SET location=COALESCE(NULLIF(location, ''), '舟山市岱山县东南部，长涂镇周边海域'), version=version+1, updated_at=UTC_TIMESTAMP(6) WHERE fixture_key='island.4';

UPDATE archive_island SET location=COALESCE(NULLIF(location, ''), '舟山市岱山县衢山岛周边海域'), version=version+1, updated_at=UTC_TIMESTAMP(6) WHERE fixture_key='island.5';

UPDATE archive_island SET location=COALESCE(NULLIF(location, ''), '舟山市岱山县衢山岛东北侧海域'), version=version+1, updated_at=UTC_TIMESTAMP(6) WHERE fixture_key='island.6';

ALTER TABLE archive_port DROP COLUMN source_url, DROP COLUMN source_date, DROP COLUMN checked_date;

ALTER TABLE archive_wharf DROP CHECK ck_archive_wharf_basis, DROP CHECK ck_archive_wharf_public, DROP COLUMN source_url, DROP COLUMN source_date, DROP COLUMN checked_date, DROP COLUMN jurisdiction_basis, DROP COLUMN jurisdiction_source_url;

ALTER TABLE archive_anchorage DROP COLUMN source_url, DROP COLUMN source_date, DROP COLUMN checked_date;

ALTER TABLE archive_island DROP COLUMN source_url, DROP COLUMN source_date, DROP COLUMN checked_date;

ALTER TABLE archive_police_station DROP COLUMN source_url, DROP COLUMN source_date, DROP COLUMN checked_date;

ALTER TABLE archive_port_officer DROP CHECK ck_archive_port_officer_mock, DROP COLUMN source_url, DROP COLUMN source_date, DROP COLUMN checked_date, DROP COLUMN is_mock;

UPDATE sys_dict_item i JOIN sys_dict_type t ON t.id=i.dict_type_id SET i.status='DISABLED', i.version=i.version+1, i.updated_at=UTC_TIMESTAMP(6) WHERE t.dict_code='maritime.wharf.jurisdiction';

UPDATE sys_dict_type SET status='DISABLED', version=version+1, updated_at=UTC_TIMESTAMP(6) WHERE dict_code='maritime.wharf.jurisdiction';
