# 数据库设计与开发规则

适用于本项目拥有的 MySQL 表、SQL 和 Flyway 迁移。只使用独立本地库与合成数据；开发库不兼作会清理数据的测试库，旧库和生产 dump 不属于开发输入。

## 1. 建模与归属

- 每张表先说明：保存什么事实、唯一写入模块、主键/业务唯一性、关联、数据范围、关键状态和主要查询。简单表可直接由迁移注释与用例说明承载，不强制另建数据字典。
- 默认按第三范式组织，字段语义原子、关系清楚；不保存逗号分隔的 ID 列表，不以通用 `key/value` 或 JSON 大字段替代已经明确的关系模型。
- 快照、来源原文、派生统计等有真实业务必要时允许冗余，并明确产生时点、版本、更新责任与真源。发送快照不可被主档案更新覆盖，来源记录和反馈追加历史不得静默改写。
- 表的 owner 按[模块规则](structure.md)确定；名称前缀辅助识别，不能代替权限或数据库隔离。图谱投影不是档案真源，任务展示页面不拥有所有任务的写入口。
- 审计字段按用途选择：可变业务记录有创建/修改时间，需要追责时记录操作者；并发编辑有版本字段；不可变快照只有实际需要的字段。禁止给所有表机械追加删除、租户、排序、备注、扩展 JSON 等预留字段。

## 2. 命名

| 对象           | 约定                                                                       | 示例                                                 |
| -------------- | -------------------------------------------------------------------------- | ---------------------------------------------------- |
| 表             | 小写英文 `snake_case`，单数业务名，模块前缀；不加无意义 `t_`/`tb_`         | `sys_user`、`archive_source_record`、`intel_receipt` |
| 模块前缀       | `system → sys_`，其余用 `archive_`、`graph_`、`ai_`、`intel_`              | 新模块确定 owner 后再定前缀                          |
| 主键/引用      | `id`、`<关联对象>_id`；所有关联类型一致                                    | `user_id`、`receiving_unit_id`                       |
| 编码/名称      | 业务编码 `_code`，业务编号 `_no`，显示名 `_name`                           | `role_code`、`intel_no`、`unit_name`                 |
| 时间/日期      | 时刻 `_at`，纯日期 `_date`，时长标明单位                                   | `sent_at`、`birth_date`、`duration_ms`               |
| 状态/布尔/数量 | 多状态用 `<语义>_status` 或单一 `status`；布尔 `is_`/`has_`；计数 `_count` | `send_status`、`is_enabled`、`failed_count`          |
| 索引/约束      | `idx_<表>_<用途>`、`uk_<表>_<用途>`、`fk_<表>_<引用>`、`ck_<表>_<规则>`    | `uk_sys_user_login_name`                             |
| 关联表         | owner 前缀 + 两端业务名；关系对有唯一约束                                  | `sys_user_role`，唯一 `(user_id, role_id)`           |

使用团队能理解的完整词，避免拼音、临时缩写、保留字和数字后缀。标识符控制在 MySQL 的 64 字符限制内；索引长名按业务用途简化。`bootstrap_probe` 是已有工程探针，不为统一前缀重命名。

## 3. 类型、空值与编码

以下是本项目新业务表的默认选择，不是对已有 bootstrap 表的迁移命令。

| 数据      | 默认选择与边界                                                                                                                                               |
| --------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| 技术主键  | 有符号 `BIGINT AUTO_INCREMENT`，Java `Long`，API 转十进制字符串；适合当前单库、无需分布式发号。已有 bootstrap 的 UUID 保留；外部业务标识另列存储，不替代主键 |
| 字符      | `VARCHAR(n)`，长度来自业务上限，不统一 255；备注/正文超过明确上限再考虑 TEXT；编号、证件样式数据使用字符串，不丢前导零                                       |
| 时刻      | 新业务表默认 `DATETIME(6)`，按 UTC 写入/读取；Java 用 Instant 表示真实时刻，验证 JDBC/MyBatis 往返。纯日期 `DATE`/LocalDate，不强行转 UTC                    |
| 数量/金额 | 范围合适的整数、`DECIMAL(p,s)`/BigDecimal；单位和舍入明确；金额不用 FLOAT/DOUBLE                                                                             |
| 布尔      | BOOLEAN/TINYINT 配 `CHECK (... IN (0,1))`，不让任意整数混入；多状态不用多个互相矛盾的布尔列                                                                  |
| 状态/枚举 | 有长度上限的稳定字符串 code；Java 显式 code 映射，必要时 CHECK 或字典外键；禁止 ordinal、显示中文或随重命名变化的 `name()` 作为持久契约                      |
| JSON      | 仅用于边界明确的快照/扩展结构；定义字段、版本、大小与校验；需要关系约束、常用筛选/关联的字段独立建列                                                         |

- 表默认 InnoDB、utf8mb4，文本默认沿用 `utf8mb4_0900_ai_ci`；登录名、外部标识、业务唯一编码逐项定义大小写/重音/空白等价规则，必要时使用区分大小写的 collation。ASCII code 可用 `ascii_bin`；FK 字符列类型与字符集/collation 必须兼容，不能只让名字相同。
- 必填 `NOT NULL`，合法的“未知/尚未发生”用 NULL，注释说明含义；不以空串、0、`-1` 或零日期伪装未知。默认值仅表达真实默认语义，不能让漏传关键字段静默成功。
- `DATETIME` 不自带时区转换，数据库会话、JDBC 连接、写入和读取必须保持 UTC；当前 JDBC 配置不能靠数据库容器的 `TZ` 替代。显示统一走[接口时间约定](backend.md)。已有 `TIMESTAMP(6)` 不为统一外观改写历史迁移；其范围/转换差异见 [MySQL 时间类型](https://dev.mysql.com/doc/refman/8.4/en/datetime.html)。
- `updated_at` 默认由业务更新 SQL 显式设置，使修改语义可审查；不能只靠时间判断并发版本。`version` 用条件递增，只有需要并发控制的可变记录才添加。
- MyBatis 的枚举/JSON 非默认映射按实际字段实现、注册并测读写；不预建万能 TypeHandler。DB NULL、JSON null、未知 code 的处理必须明确，不能悄悄变成空对象或第一个枚举。

## 4. 注释是建表必填项

**每张业务表有表 COMMENT，每个字段有非空且有信息量的 COMMENT，包括主键、引用、状态、审计和技术字段。** Flyway 自身管理表等第三方内部结构不自行修改。

- 表注释说明业务事实与模块归属。字段注释至少说明业务含义；涉及单位、时区、枚举值、NULL、来源或特殊规则时一并注明。
- 引用字段写出目标表/字段及角色；即使不建物理外键，也必须明确关联含义。JSON 字段注明结构版本或就近结构说明的位置。
- “状态”“时间”“备注”或照抄英文列名不算充分注释。状态新增 code、字段语义改变时通过新迁移同步注释、约束、Java 映射和 API。

下例只示范字段、注释和约束形式，不是完整角色模型，不直接执行或作为本轮迁移：

```sql
CREATE TABLE sys_role (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '角色主键，API 以十进制字符串返回',
    role_code VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL
        COMMENT '稳定角色编码，区分大小写，全局唯一，停用后不复用',
    role_name VARCHAR(80) NOT NULL COMMENT '角色显示名称，不作为关联键',
    status VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'ENABLED'
        COMMENT '角色状态：ENABLED 启用，DISABLED 停用',
    version INT NOT NULL DEFAULT 0 COMMENT '并发编辑版本，每次成功修改加一',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时刻，UTC',
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
        COMMENT '最近修改时刻，UTC，由修改 SQL 显式更新',
    PRIMARY KEY (id),
    CONSTRAINT uk_sys_role_code UNIQUE (role_code),
    CONSTRAINT ck_sys_role_status CHECK (status IN ('ENABLED', 'DISABLED')),
    CONSTRAINT ck_sys_role_version CHECK (version >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='system 模块：角色定义；操作权限通过角色权限关系维护';
```

命名、COMMENT、默认值与约束语法以 [MySQL 8.4 CREATE TABLE](https://dev.mysql.com/doc/refman/8.4/en/create-table.html) 为准。

## 5. 约束、索引与查询

- 业务唯一性必须落实唯一约束，先明确作用域（全局、单位、来源系统等），不只做 Service 查重；并发冲突转换为清楚的业务结果。状态迁移、幂等和行数语义见[后端规则](backend.md)。
- 同库、生命周期明确且需要引用完整性的关系默认使用外键；优先 RESTRICT/NO ACTION，明确需要时才级联。跨模块可以约定外键，不意味着可以跨模块写表；外部来源标识或不可变历史引用若不建 FK，须说明生命周期及如何防止悬空。引用目标使用主键/唯一键，关联列有合适索引。依据：[MySQL 外键](https://dev.mysql.com/doc/refman/8.4/en/create-table-foreign-keys.html)。
- 能由 NOT NULL、UNIQUE、FK、CHECK 保证的不变量在数据库约束；涉及多行状态或跨表规则仍由受事务保护的用例维护，CHECK 不代替授权。
- 不默认软删所有表。有历史/审计意义的对象优先停用或保留事件；确需软删时定义所有读路径、唯一键复用和历史关联语义。`UNIQUE(code, deleted_at)` 不能保证仅一条未删除记录，NULL 参与唯一性需专门设计与测试。
- “办结”保存显式办结状态/时间/操作者，不从签收或反馈推断；状态与相关时间在同一事务更新。多单位回执各自有事实，不把主单与分支状态混成一个可任意修改的字符串。
- 索引由实际 WHERE/JOIN/ORDER BY 设计，考虑联合索引前导列、范围与排序；不每列建索引，不给已有唯一索引再建等价普通索引。核心查询用代表性合成数据与 EXPLAIN 核对，数据少时不宣称已证明大规模性能。
- 列表在数据库分页，避免 SELECT *、全表拉到 Java 再过滤、逐行查询和无界 IN/批量写入；关联查询要核对一对多重复及 count 口径。性能优化先有慢查询或执行计划证据，不预建分库分表、keyset 分页或缓存体系。
- 导入按来源唯一性识别重复，业务上允许更新才 upsert，明确可覆盖字段与来源优先级；不要根据 upsert 返回行数直接统计成功/新增/修改条数。

## 6. Flyway 与变更验收

1. 迁移统一放 `apps/api/src/main/resources/db/migration`，文件名 `V<递增整数>__<snake_case描述>.sql`，如 `V2__create_system_tables.sql`；创建前检查已有及并行新增编号。注释写明目的与 owner。
2. 已执行迁移保持不可变。修字段、补注释、修约束均追加新迁移，不编辑 V1、不删除历史、不用自动建表或应用启动代码改结构。
3. 迁移由独立迁移账号执行，应用仅用 DML 账号；沿用 `dev.sh up` 的先迁移后启动流程。初始化脚本只管库/账号等基础设施，业务 schema 不在两处维护。
4. 对已有数据加 NOT NULL/唯一键前先检查、回填和校验；不拿 `IF NOT EXISTS` 或 `INSERT IGNORE` 掩盖不一致。需要兼容过渡时按“扩展 → 回填 → 约束/切换 → 清理”分阶段；简单新表无需套完整流程。
5. MySQL 多条 DDL 迁移不能靠一个事务全部回滚。失败先核对真实结构和 Flyway 历史，再给出修复迁移或恢复办法；不要直接 `repair` 掩盖错误，更不能清库重灌。依据：[MySQL 隐式提交](https://dev.mysql.com/doc/refman/8.4/en/implicit-commit.html)。删列、清数据等破坏性操作需对应授权。
6. schema 变化在隔离 MySQL 验证空库迁移、上一版本合成数据升级、默认值/约束与 Mapper 读写。通过 INFORMATION_SCHEMA/SHOW CREATE TABLE 核对实际字段、COMMENT、索引列顺序和约束，不能只核对 SQL 文件文本。

纯规则或 SQL 示例编辑不启动数据库。真正实施迁移时按[测试规则](testing.md)提供上述运行证据；缺环境就列缺口，不用编译成功替代。
