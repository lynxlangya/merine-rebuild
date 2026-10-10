# 操作审计设计（全系统）

整理日期：2026-10-10。范围与边界已与用户确认（见 §1 决策记录）。本文是设计与实施依据，**未开工**；写完供评审。

## 1. 决策记录（2026-10-10，用户确认）

| #   | 决策         | 结论                                                                                  |
| --- | ------------ | ------------------------------------------------------------------------------------- |
| 1   | 覆盖范围     | **整个系统**：系统管理、模型连接、涉海要素档案、任务处置、信息流转、登录/登出、初始化 |
| 2   | 记录哪些操作 | 写操作**只记成功的**；**登录成功与失败都记**（失败是安全事件的核心）                  |
| 3   | 可见性       | **全量可见**（不限本单位），由权限码严格控制                                          |
| 4   | 记录粒度     | 动作 + 对象 + 一句人写的摘要，**不做自动字段级 diff**                                 |
| 5   | 保留期       | 第一期**不清理、不提供删除接口**（只追加）；保留策略第二期定                          |

## 2. 目标与不做什么

做：一张只追加的审计表 + 显式写入封装 + 权限码/菜单/页面 + 登录事件。

不做（明说，避免误解）：

- **不记业务内容**：情报正文、对话内容、密码、API 密钥、档案详情一律不进审计（负面清单见 §7）。
- **不记被拒绝的写操作**（403、参数校验失败）：量大会淹没有效信息；将来需要时另做「安全事件」通道。
- **不做前后值 diff**：摘要由代码写，人能读即可。
- **不做字段级导出、不做保留期清理、不做防篡改存储**：运行账号有 DML 权限，审计防的是误用与内部追责，不防数据库管理员；要更强需要存储隔离，本期不做。
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

### 5.4 任务处置（module=task）

`TaskService` 的流程动作各一条：`task:dispatch`、`task:accept`、`task:return`、`task:reassign`、`task:transfer`、`task:decide-transfer`、`task:withdraw-transfer`、`task:submit-result`、`task:close`、`task:recall`、`task:withdraw`；对象 TASK，摘要写「任务名称/编号 + 动作 + 承接单位」。

### 5.5 信息流转（module=intelligence）

`IntelligenceService` 与研判入口：`intel:create`、`intel:update`、`intel:freeze-scope`、`intel:send`、`intel:sign`、`intel:feedback`、`intel:forward`、`intel:supplement`、`intel:correct`、`intel:assess`、`intel:create-task`；对象 INTEL_TOPIC，摘要写主题标题与动作，**不写正文**。

### 5.6 身份与初始化（module=auth / bootstrap）

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

- **表增长**：登录与写操作量都不大（人工操作级），先不清理；第二期按数据量定归档策略。
- **反代后的 IP**：`X-Forwarded-For` 只在部署侧可信时使用，文档写明；本地开发直连时是 `127.0.0.1`。
- **审计与业务表的关系**：任务/情报的流程状态仍在业务表；审计记的是「有人做了这个操作」，两者不互相替代。
- **不防 DBA**：见 §2；如后续要防篡改，需要独立存储与权限隔离，另行设计。
- **埋点数量**：约 60–70 个调用点，分散在 6 个模块的服务层；本设计以「动作码 + 对象 + 摘要」固定模板控制一致性，评审后按模块分批落地。
