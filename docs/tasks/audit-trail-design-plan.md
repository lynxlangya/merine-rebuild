# 操作审计设计（全系统）

整理日期：2026-10-10。范围与边界已与用户确认（见 §1 决策记录）。本文是设计与实施依据，**未开工**；写完供评审。

## 1. 决策记录（2026-10-10，用户确认）

| #   | 决策         | 结论                                                                                  |
| --- | ------------ | ------------------------------------------------------------------------------------- |
| 1   | 覆盖范围     | **整个系统**：系统管理、模型连接、涉海要素档案、任务处置、信息流转、登录/登出、初始化 |
| 2   | 记录哪些操作 | 写操作**只记成功的**；**登录成功与失败都记**（失败是安全事件的核心）                  |
| 3   | 可见性       | **全量可见**（不限本单位），由权限码严格控制                                          |
| 4   | 记录粒度     | 动作 + 对象 + 一句人写的摘要，**不做自动字段级 diff**                                 |
| 5   | 保留期       | **保留 30 天，过期硬删除**；每日定时 + 手动入口，清理本身留痕（见 §13）。不做归档文件 |

## 2. 目标与不做什么

做：一张只追加的审计表 + 显式写入封装 + 权限码/菜单/页面 + 登录事件。

不做（明说，避免误解）：

- **不记业务内容**：情报正文、对话内容、密码、API 密钥、档案详情一律不进审计（负面清单见 §7）。
- **不记被拒绝的写操作**（403、参数校验失败）：量大会淹没有效信息；将来需要时另做「安全事件」通道。
- **不做前后值 diff**：摘要由代码写，人能读即可。
- **不做字段级导出、不做归档文件、不做防篡改存储**：审计防的是误用与内部追责，不防数据库管理员；要更强需要存储隔离，本期不做。删除只发生在保留期清理（§13），清理本身留痕。
- **不替代**：运行日志（排障）、任务/情报的业务流转表（流程本身）、`ai_chat_run`（助手用量）。

## 3. 数据模型

新表 `sys_audit_log`（system 模块，只追加；无物理外键；每列有注释）：

| 列                | 类型                                              | 注释要点                                                            |
| ----------------- | ------------------------------------------------- | ------------------------------------------------------------------- |
| `id`              | BIGINT AUTO_INCREMENT                             | 追加顺序号；接口按字符串返回（与 `sys_user` 一致）                  |
| `occurred_at`     | DATETIME(6) NOT NULL                              | 操作发生时刻 UTC，列表按它倒序                                      |
| `actor_user_id`   | BIGINT NULL                                       | 操作者；登录失败等无身份场景为 NULL                                 |
| `actor_login`     | VARCHAR(64) NOT NULL DEFAULT ''                   | 登录名快照（账号改名或删除后仍可读）；登录失败时是尝试的登录名      |
| `actor_name`      | VARCHAR(80) NOT NULL DEFAULT ''                   | 姓名快照                                                            |
| `actor_unit_id`   | BIGINT NULL                                       | 操作者所属单位 id（便于按单位筛）                                   |
| `actor_unit_name` | VARCHAR(80) NOT NULL DEFAULT ''                   | 单位名快照                                                          |
| `module`          | VARCHAR(32) NOT NULL                              | 模块码：system/agent/maritime/task/intelligence/auth/bootstrap      |
| `action`          | VARCHAR(64) NOT NULL                              | 动作码：`<资源>:<动作>`，见 §4                                      |
| `result`          | VARCHAR(16) NOT NULL                              | SUCCEEDED / FAILED（本期写操作只产生 SUCCEEDED，登录失败是 FAILED） |
| `target_type`     | VARCHAR(32) NOT NULL DEFAULT ''                   | 对象类型：USER/ROLE/MENU/UNIT/DICT_ITEM/PROVIDER/ISLAND/…           |
| `target_id`       | VARCHAR(64) NOT NULL DEFAULT ''                   | 对象标识（删除后仍在）                                              |
| `target_label`    | VARCHAR(160) NOT NULL DEFAULT ''                  | 对象名称快照（删除后仍能读懂这条账）                                |
| `summary`         | VARCHAR(300) NOT NULL                             | 人写的摘要，白名单内容，禁止敏感串                                  |
| `request_id`      | VARCHAR(64) NOT NULL DEFAULT ''                   | 当时的请求 id，用于和服务器日志对齐                                 |
| `client_ip`       | VARCHAR(45) NOT NULL DEFAULT ''                   | 客户端 IP（登录事件必填；见 §6）                                    |
| `created_at`      | DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) | 写入时刻 UTC                                                        |

索引与约束：

- `INDEX idx_sys_audit_log_time (occurred_at, id)` —— 列表主查询
- `INDEX idx_sys_audit_log_actor (actor_user_id, occurred_at, id)` —— 按人筛
- `INDEX idx_sys_audit_log_target (target_type, target_id, occurred_at)` —— 「这个对象的历史」
- `INDEX idx_sys_audit_log_module (module, action, occurred_at)` —— 按模块/动作筛
- `CONSTRAINT ck_sys_audit_log_result CHECK (result IN ('SUCCEEDED','FAILED'))`
- 无外键、无 `updated_at`、无删除路径（表只追加）。

## 4. 动作码规范

`<资源>:<动作>`，资源用小写单数、动作用固定动词表，便于筛选与统计：

- 生命周期：`create` / `update` / `delete` / `restore`
- 状态与凭据：`toggle-status` / `reset-password` / `rotate-key`（密钥更换）
- 关系：`assign-roles`（用户角色）/ `grant-permissions`（角色权限）
- 流程：`dispatch` / `accept` / `return` / `reassign` / `transfer` / `decide-transfer` / `withdraw-transfer` / `submit-result` / `close` / `recall` / `withdraw`
- 情报：`freeze-scope` / `send` / `sign` / `feedback` / `forward` / `supplement` / `correct` / `assess` / `create-task`
- 身份：`login` / `logout`
- 配置：`discover-models`（「获取模型列表」：用密钥出站，值得留痕）

模块清单登记在代码侧 `system/audit/AuditModules`（与 `PermissionModules` 同一约定），回归断言「库里出现过的模块都已登记」。

## 5. 埋点清单（逐模块）

「触发点」是服务方法；**在写成功之后、同一事务内**记一条。删除类先取名称再删，摘要里保留名称。

### 5.1 系统管理（module=system）

| 动作码                                                               | 触发点                                 | 对象      | 摘要示例                                        |
| -------------------------------------------------------------------- | -------------------------------------- | --------- | ----------------------------------------------- |
| `user:create`                                                        | `UserAdminService.create`              | USER      | 新建用户「张三」（demo.zhang，杭州支队）        |
| `user:update`                                                        | `UserAdminService.update`              | USER      | 修改用户资料                                    |
| `user:toggle-status`                                                 | `UserAdminService.changeStatus`        | USER      | 停用用户 / 启用用户                             |
| `user:reset-password`                                                | `UserAdminService.resetPassword`       | USER      | 重置密码（不记密码本身）                        |
| `user:assign-roles`                                                  | `UserAdminService` 授权路径            | USER      | 角色由「只读审计员」改为「用户管理员」          |
| `role:create` / `role:update` / `role:toggle-status` / `role:delete` | `RoleService` 对应方法                 | ROLE      | 删除角色「xxx」（删除前取名称）                 |
| `role:grant-permissions`                                             | `RoleService.update`（权限集合变化时） | ROLE      | 权限由 5 项改为 12 项（新增：用户管理 · 新增…） |
| `menu:create` / `menu:update` / `menu:delete` / `menu:restore`       | `MenuService`                          | MENU      | 删除菜单「xxx」及其 3 个子节点、2 个权限码      |
| `unit:create` / `unit:update` / `unit:delete`                        | `UnitService`                          | UNIT      |                                                 |
| `dict-type:create` / `dict-type:update`                              | `DictionaryService`                    | DICT_TYPE |                                                 |
| `dict-item:create` / `dict-item:update` / `dict-item:toggle-status`  | `DictionaryService`                    | DICT_ITEM | 停用字典项「启用」（取值 ENABLED 不变）         |

### 5.2 模型连接（module=agent）

| 动作码                                                    | 触发点                     | 对象     | 摘要示例                                               |
| --------------------------------------------------------- | -------------------------- | -------- | ------------------------------------------------------ |
| `provider:create` / `provider:update` / `provider:delete` | `ProviderService`          | PROVIDER | 修改连接「DeepSeek」：模型 6 → 5；更换密钥（不记内容） |
| `provider:discover-models`                                | `ProviderService.discover` | PROVIDER | 读取上游模型清单：返回 262 个模型（出站调用留痕）      |

### 5.3 涉海要素档案（module=maritime）

`MaritimeService` 的 18 个方法各一条：`island:create|update|delete`、`port:*`、`wharf:*`、`police-station:*`、`port-officer:*`、`anchorage:*`，对象类型对应 ISLAND/PORT/WHARF/POLICE_STATION/PORT_OFFICER/ANCHORAGE，摘要写名称。

### 5.4 任务处置与信息流转（不纳入）

按 §1 决策 7：`TaskService` 的流程动作（下发/承接/退回/重派/交接/审批/撤回/结果/办结）与
`IntelligenceService` 的情报动作（创建/冻结范围/发送/签收/反馈/继续共享/追加研判/源头更正）
**不写审计**——业务表本身就是留痕。审计页面不提供这两个模块的筛选项。

### 5.5 身份与初始化（module=auth / bootstrap）

| 动作码                   | 触发点                    | 摘要示例                                                |
| ------------------------ | ------------------------- | ------------------------------------------------------- |
| `auth:login`             | `AuthService.login` 成功  | 登录成功（IP）                                          |
| `auth:login`             | `AuthService.login` 失败  | 登录失败：账号或口令不正确（尝试登录名 + IP，不记口令） |
| `auth:logout`            | `AuthService.logout`      | 退出登录                                                |
| `bootstrap:create-admin` | `BootstrapService.create` | 初始化管理员账号                                        |

## 6. 写入方式与登录事件

- 写入封装：`system/audit/AuditTrail.record(AuditEvent)`，**与业务写同一事务**；写不进去就让业务一起回滚（本期接受这个取舍，管理操作频率低）。
- 取操作者：从当前 `AuthenticatedAccount` 取 id/登录名/姓名/单位（快照），不在审计里查库。
- IP 采集 `AuditClientIp`：优先 `X-Forwarded-For` 第一段（生产由 nginx 设置），回落 `request.getRemoteAddr()`；只记 IP，不记 UA。文档写明反代语义。
- 登录失败在**认证失败分支**记录，只在登录接口里做，避免密码校验路径散落。

## 7. 负面清单（硬约束）

**绝不写入审计**：密码与密码哈希、API 密钥（任何片段）、情报正文与附件、对话内容、密钥解密细节、完整请求体。

实现约束：`summary` 由代码写死模板 + 少量白名单字段（名称、数量、状态码）；回归用例在「重置密码 / 更换密钥 / 情报创建 / 对话」之后，直接在审计表里检索这些敏感串，必须为 0 条。

## 8. 权限、菜单与页面

- 权限码：`system:audit:read`（系统管理 · 审计日志 · 查看）；将来导出再加 `system:audit:export`。
- 菜单：`系统管理` 目录下新增 PAGE 节点「审计日志」，`route_key = system.audit`（同时在 `routeRegistry.tsx` 与后端 `RegisteredRoutes` 登记），排序排在字典管理之后。
- 接口：`GET /api/system/audit-logs?from&to&actor&module&action&result&targetType&targetId&page&pageSize`（服务端筛选 + 分页；时间缺省近 7 天，`to` 缺省现在）。
- 页面：只读列表（时间、操作者〈姓名·登录名·单位〉、模块、动作、对象、结果、IP），筛选条 + 分页 + 行详情（摘要 + requestId）；空态与加载失败提示与其它管理页一致。

## 9. 验收标准

1. **隔离 MySQL 回归**：
   - 停用用户 → 审计新增一条 `system/user:toggle-status`，带操作者快照、对象名称、结果；
   - 删除字典项/岛屿 → 审计里保留被删对象的名称；
   - 登录成功与失败各一条，失败记尝试登录名与 IP、不记口令；
   - 权限不足被拒（403）不产生审计记录；
   - 按 module/action/actor/时间/结果筛选与分页正确；无权限账号 403；
   - **审计写失败则业务回滚**：测试里临时给审计表加一条 `CHECK`，触发对应操作 → 接口失败且业务数据未变（测完删除约束）。
2. **负面清单回归**：见 §7。
3. **浏览器验收**：菜单出现→筛选→翻页→行详情；另一个无权限账号看不到菜单、直接敲 URL 403；明暗主题各一遍。
4. **文档**：本文档实施记录、`design-qa.md`、README 一句话、`docs/rules/backend.md` 把「审计」从缺口清单移到已实现（并写明边界）。

## 10. 实施步骤

| 步骤 | 内容                                                                                                                      | 验证                                                         |
| ---- | ------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------ |
| S1   | 迁移：`sys_audit_log` 表 + 权限码 + 菜单节点；后端 `AuditModules`/`AuditTrail`/`AuditClientIp`；前端路由注册 + 只读列表页 | 表结构回归（注释/索引/无外键）、菜单与权限树回归、页面可打开 |
| S2   | 系统管理埋点（用户/角色/菜单/单位/字典）+ 登录登出（成功与失败）                                                          | 回归：动作码、对象快照、删除保留名称、失败登录、403 不记     |
| S3   | 模型连接 + 涉海档案埋点                                                                                                   | 同上，含 `provider:discover-models`                          |
| S4   | 任务 + 情报埋点                                                                                                           | 同上；确认业务流转表与审计互不重复                           |
| S5   | 负面清单回归 + 回滚性回归 + 浏览器验收 + 文档                                                                             | 见 §9                                                        |

## 11. 风险与边界

- **表增长**：保留期 30 天，过期行由每日定时任务与手动入口按批硬删；量级是人工操作，单次批量上限 20 万条足够。
- **反代后的 IP**：`X-Forwarded-For` 只在部署侧可信时使用，文档写明；本地开发直连时是 `127.0.0.1`。
- **审计与业务表的关系**：任务/情报的流程状态仍在业务表；审计记的是「有人做了这个操作」，两者不互相替代。
- **不防 DBA**：见 §2；如后续要防篡改，需要独立存储与权限隔离，另行设计。
- **埋点数量**：约 45 个调用点（系统管理 21 + 模型连接 4 + 涉海档案 18 + 身份与初始化 4，去除重复计数），分散在服务层；本设计以「动作码 + 对象 + 摘要」固定模板控制一致性，按模块分批落地。
- **与业务表的关系（再强调）**：任务与信息流转的操作只在业务表留痕，审计不复制；若将来要求「一个页面看全系统操作」，正确做法是读业务表做只读聚合，而不是把事件抄进审计。

## 12. 实施记录

### S1 基础设施（2026-10-10，已完成）

- 迁移 **V50**：表 `sys_audit_log`（16 列全部注释、4 个索引、`result` CHECK、无外键无删除路径）+ 权限码 `system:audit:read` + 菜单节点「审计日志」（`system.audit`，系统管理目录第 6 位）；`PermissionCodes`、`MenuBootstrap`、`RegisteredRoutes` 三处同步。
- 后端 `system/audit`：`AuditTrail`（写入唯一入口，会话快照 + requestId + IP，写失败即业务回滚）、`AuditModules`（模块注册表，**不含任务与信息流转**）、`AuditLogService`/`AuditLogController`（只读列表 + 模块选项，服务端筛选，非法模块/结果/时间/分页 400，时间上限 90 天、缺省近 7 天）。
- 前端 `/system/audit`：时间范围/操作者/模块/结果筛选 + 表格 + 展开详情（对象 id、IP、requestId、摘要）+ 分页；路由 key `system.audit` 已注册。
- 回归 6 项：写入内容与 IP（`X-Forwarded-For` 首段）、登录失败无身份写法、敏感串扫描、筛选与参数校验、模块清单、401/403。

### S2 系统管理 + 登录（2026-10-10，已完成）

- 埋点：用户（新建/编辑/改角色/重置密码/启停）、角色（新建/编辑/改权限/启停/删除）、菜单（新建/编辑/删除/恢复默认）、单位（新建/编辑/删除）、字典（类型与项的增改/停用）、登录（口令登录成功、**失败**、开发免密登录）、退出、初始化探针。
- 回归 3 项 + dev 日志 1 项：写操作落库（动作码/对象/摘要/操作者）、删除与重置类**保留名称快照、不记密码**、登录成功/失败/退出（失败记尝试登录名与原因、不记口令）、免密登录也留痕。

### 过程中发现并修掉的问题

| 问题                                | 定位                                                                                  | 处理                                                                                                |
| ----------------------------------- | ------------------------------------------------------------------------------------- | --------------------------------------------------------------------------------------------------- |
| 会话里没有单位 id                   | `AuthenticatedAccount` 只有 `unitName`                                                | 审计只记**单位名快照**，去掉 `actor_unit_id` 列（迁移未执行前调整）                                 |
| **非 ASCII 登录名让登录失败变 500** | `actor_login` 是 ascii 列，中文登录名插入即报错；被既有用例（中文登录名返回 401）抓到 | `AuditTrail` 对所有 ascii 列做净化（可打印 ASCII 之外替换为 `?`），中文姓名/单位名/摘要仍走 utf8mb4 |
| 登录失败分支改写成 `return null`    | 替换 `throw` 时手误，会让错误口令"登录成功"                                           | 在提交前发现并改回 `throw`；失败留痕抽成 `auditFailedLogin(...)`                                    |
| 开发免密登录漏埋点                  | `loginForDevelopment` 直接 `return establishSession(...)`，与口令登录的结构不同       | 单独补上并加用例（该入口只在 dev profile 注册，用例放在 `DevLoginRegressionTest`）                  |
| 用例互相污染                        | 埋点是全局副作用：其它测试类的操作与登录都会写审计表                                  | 审计用例在 `@BeforeEach` 只清审计表（不碰夹具），断言按动作过滤                                     |

### 未做（S3 之后）

- 模型连接（新增/编辑/删除 + 「获取模型列表」）与涉海要素档案（18 个写点）埋点。
- 负面清单与回滚性的**专项**回归（当前已覆盖敏感串扫描与"审计写失败即业务回滚"的机制说明，尚未做 CHECK 注入式用例）。
- 浏览器验收的明暗主题各一遍、以及保留期策略（按决策 5 暂不做）。

### 修复：审计页无限刷新（2026-10-10）

页面把 `Date.now()` 现算的时间范围起点放进了 `queryKey`（毫秒级变化 → 每渲染都是新键）→ 无限重新取数。
处理：`rangeFromDays()` 秒/毫秒归零 + `useMemo` 固定；补单测与「查询键必须是稳定值」前端规则；验证静置 20 秒零额外请求。

### S3 模型连接 + 涉海档案（2026-10-10，已完成）

- 模型连接：`provider:create`（含供应商、模型数、地址）、`provider:update`（摘要说清**改了什么**：模型数变化、地址变化、更换密钥、状态或供应商变化）、`provider:delete`（名称与地址快照）、`provider:discover-models`（调用含密钥出站的那一步，记返回条数）。替换密钥只记「更换密钥」，**密钥与指纹都不进审计**。
- 涉海档案：六类资源 × 新增/修改/删除共 **18 个写点**，动作码 `<资源>:create|update|delete`，对象带类型码（ISLAND/PORT/…）与名称快照。摘要形如「删除海岛「黄泽山岛」」；档案正文（区域、位置、职务等字段值）不入审计。
- 回归 `AuditS3RegressionTest`：连接三类写 + 密钥负面清单；六类资源增改删 18 条断言 + 删除后名称快照仍在 + 正文不入表。

### S3 过程中发现并修掉的问题

| 问题                           | 定位                                                                                                                    | 处理                                                                        |
| ------------------------------ | ----------------------------------------------------------------------------------------------------------------------- | --------------------------------------------------------------------------- |
| **民警删除留痕会写成「null」** | `archive_port_officer` 没有 name 列（姓名在 `sys_user`），而 `lockPortOfficer` 的锁定读不查姓名，删除时 `row.name` 为空 | 在删除前同事务内 `findPortOfficer(id)` 取姓名快照再删；由 18 点用例当场暴露 |
| 「获取模型列表」摘要多一对引号 | 三元表达式拼接产生「「千问 AI 平台」，」                                                                                | 改成两种完整句式，实测（真实上游返回 194 个模型）后修正                     |
| 测试库留下悬空权限行           | 早先一次失败运行中途退出，`@AfterEach` 的原始 SQL 删角色没先删 `sys_role_permission`                                    | 用例清理改为先删关联再删角色；测试库按前缀清理并把「无悬空引用」恢复为绿    |

### 失败注入的做法（回滚回归）

运行账号只持有 DML 权限（有意的约束），测试里**无法**用 `ALTER TABLE ... ADD CHECK` 注入「审计写失败」。
因此 `AuditRollbackRegressionTest` 替换「审计写入」这一个协作者让它抛异常，断言的对象仍是隔离 MySQL 里的业务行：
接口 500、用户状态保持 `ENABLED`；撤掉注入后同一次操作正常完成并落痕。事务语义没有被替身掩盖。

### 仍未做

- 保留期清理的**归档与导出**（当前是硬删除，见 §13）。
- 事件通知、导出、跨模块检索。
- 浏览器验收目前覆盖功能路径，尚未专门过一遍暗色主题。

## 13. 保留期：保留 30 天，过期硬删除

### 决策

保留 **30 天**（`merine.audit.retention.days`，默认 30，可配置），过期**永久删除**，不做归档文件。
如果将来确实需要更长的证据链，做法应是「删除前导出 NDJSON 到挂载目录或对象存储」，而不是把表一直留着——
导出文件里带操作者姓名与 IP，保护级别要与数据库同等，所以那是另一个范围的工作。

### 规则

1. **边界**：只删 `occurred_at < now - 保留天数` 的行，正好等于边界的记录保留；窗口内的行任何情况下都不动。
2. **分批**：每批 500 条，单次最多 400 批（20 万条），避免超大事务长时间持锁。
3. **清理留痕**：真删到东西时写一条 `audit:purge`（模块 `system`，对象 `AUDIT_LOG`），
   摘要写明「保留几天、删除多少条、覆盖哪段时间、手动还是定时」；审计写失败则整个清理回滚。
   没有过期记录时不写记录，避免每天一条噪音。
4. **触发**：每日定时（`merine.audit.retention.cron`，默认本地 03:30，可用 `enabled` 关闭）与
   手动接口 `POST /api/system/audit-logs/purge`（权限码 `system:audit:read`）走同一个方法。
   测试 profile 显式关闭定时器，免得删掉正在断言的记录。
5. **页面**：审计页时间范围上限 30 天，页头写明保留期；「清理过期记录」按钮需要二次确认，
   并复述实际结果（保留几天、删了多少、覆盖区间）。

### 实施与验收（2026-10-10）

- 迁移 **V51**：把保留期口径写进表与 `occurred_at` 的注释（不加列、不加索引，清理走已有的 `idx_sys_audit_log_time`）。
- 后端：`AuditRetentionProperties`（含 `days >= 1` 校验）、`AuditRetentionService`（批量删除 + 留痕）、
  `AuditRetentionScheduler`（默认开启、测试关闭）、`POST /api/system/audit-logs/purge` 与契约类型 `AuditPurgeResult`。
- 回归 `AuditRetentionRegressionTest`（4 项）：只删窗口外的行（窗口内两条原样保留）、清理留痕的摘要内容、
  定时触发记系统动作（无操作者）、保留期参数校验、无权限 403 且不产生清理记录。
- 浏览器验收：页面「清理过期记录」→ 二次确认 → 提示「已永久删除 2 条超过 30 天的记录，覆盖 2026-08-26 08:39 至 08:40」，
  审计表新增一条 `清理过期流水`（摘要含条数与覆盖区间），被删的两条假记录归零、其余 22 条原样。
- 过程中修掉的问题：`MIN/MAX(DATETIME)` 经驱动回来的是 `LocalDateTime`（而非 Timestamp/Instant），
  首版摘要写成「覆盖 未知 至 未知」；已按四种可能类型归一，并把「覆盖 20…、不含未知」写进断言。
