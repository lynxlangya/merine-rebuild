# 智能体 / 智能问数方向评审简要（2026-10-11）

> **历史归档（2026-10-12）**：这是当时的讨论或状态快照，不代表当前实现；文中的待办、完成度、未提交状态和授权描述均不作为现行任务。 当前入口：[独立 Agent 运行说明](../../tasks/agent-readonly-query.md)；范围与语义：[现行设计](../../tasks/agent-query-scope-management-plan.md)。以下保留历史原文。

用途：交给外部评审（Claude Code）判断当前方案能否继续、是否存在逻辑漏洞或结构性风险。本文只描述**当前真实状态与证据**；设计稿、重建计划中的能力不等于已实现。仓库根 [AGENTS.md](../../../AGENTS.md) 是协作底线。

## 1. 业务定位

- 目标是"智能问数"：用自然语言查询本项目业务数据（任务处置、信息流转、涉海要素），回答统计类问题，并可做一般性问答。
- AI 入口是**独立 Agent 服务**（`apps/agent`，DeepSeek Harness 裁剪 fork，端口 3080）。站内助手（旧 `/agent` 页面、Java chat/provider/semantics、模型供应商、Function Calling）已于 2026-10-12 整体移除（迁移 `V57`）：主站顶部入口指向独立 Agent。
- 数据查询采用**受控只读视图**方案：模型只生成受限 SQL，Agent 重建后经只读账号执行；Java 不执行模型 SQL、不向 Agent 下发凭据、不暴露底表。
- 当前定位是**单操作员本地合成数据试验**，不是多用户生产系统。

## 2. 全链路结构

### 配置侧（管理员）

```text
数据源管理（连接/凭据/只读检查/启停）        [主站页面 + /api/agent/datasources]
  → 查询范围（选择允许视图 + 中文名/用途口径/字段说明）  [主站页面 + /api/agent/query-scopes]
  → 复检并启用
```

- 数据源配置存 `ai_data_source`（密码 AES-256-GCM 密文，主密钥在部署环境变量）。
- 白名单存 `ai_data_source_view`；说明覆盖值存 `ai_data_source_view_meta` / `ai_data_source_view_column_meta`。
- 视图定义由 Flyway 迁移维护（V54 任务汇总、V56 首批业务视图），应用运行账号只有 DML 权限，不做运行时 DDL。
- 语义修改只递增 `semantic_version`，不使连接检查失效；范围修改会使检查失效并强制停用，需复检启用。

### 运行侧（用户提问）

```text
浏览器 → Agent（系统提示 + 会话 + 工具）
  → get_business_time / describe_business_data（取目录、说明、catalogRevision）
  → 模型生成 SQL
  → Agent：重新向 Java 桥取授权（固定数据源、白名单、说明、两类版本的一致快照）
  → SQL 解析 + 白名单 AST 重建 → 只读连接执行
  → 结果（截断标识）进入模型上下文 → 回答 → 会话存储
```

- 桥：`GET /internal/agent/data-source`（`@Hidden`，64 位十六进制 Bearer，默认关闭，固定数据源、不接受请求指定 ID）。响应协议 `contractVersion: 2`，含 `viewMeta` 与 `semanticVersion`，加锁快照、>64 KiB 拒绝。
- Agent 工具：`get_business_time`、`describe_business_data`（目录 + `catalogRevision`）、`query_business_data(sql, catalogRevision)`。目录或说明变化后旧摘要拒绝执行，必须重新描述。

## 3. 已实现的安全边界

| 边界           | 实现                                                                                                                                                                     | 位置                                           |
| -------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------ | ---------------------------------------------- |
| 凭据不下发     | 密码只在 Java 库密文 + Agent 服务端内存按次持有；浏览器、模型上下文、日志、配置文件都没有密码                                                                            | `AgentSecretCipher`、`bridge.ts`               |
| 只允许真实视图 | 白名单候选必须同时命中"信息模式发现的 VIEW"和 SELECT 授权；保存、启用、每次签发授权都复检；Agent 读取字段前再按 `information_schema.VIEWS` 校验对象类型                  | `DataSourceAuthorizationEvaluator`、`index.ts` |
| 凭据不跨目标   | 修改主机/端口/数据库/账号必须重新输入密码；"测试连接"只在目标与身份完全一致时复用已保存密码                                                                              | `DataSourceService`、`DataSourceStore`         |
| SQL 重建       | 不执行模型原文；AST 白名单重建，单视图、无 `SELECT *`、无 JOIN/子查询/CTE/多语句/变量/锁，聚合与函数白名单，最多 20 列、LIMIT 201                                        | `packages/merine/query/src/sql.ts`             |
| 资源限制       | 5 秒 `max_execution_time`、15 秒总超时、最多 200 行/每格 2048 字符/合计 64 KiB、只读事务、单进程同时只允许一项查询                                                       | `index.ts`、`bridge.ts`                        |
| 目录一致性     | `semanticVersion` + `catalogRevision`；说明或字段变化后旧摘要被拒绝；桥下发的连接/范围/说明/版本来自同一加锁快照                                                         | 桥 + Agent                                     |
| 传输           | Agent 只监听回环或经 HTTPS 反代（`--public-url`/`--trusted-host`）；Java→数据库默认 `VERIFY_IDENTITY`（固定数字地址 + 证书 IP SAN），`VERIFY_CA`/`REQUIRED` 只能显式选择 | `DataSourceConnections`、反代文档              |
| 审计           | Java：数据源新增/编辑/启停/检查、范围变更、语义变更、探测；Agent：`DSH_HOME/query-audit.jsonl`（queryId、SQL 哈希、耗时，不写原文与数据）                                | `AuditTrail`、Agent                            |
| 能力收敛       | Agent 关闭遥测/反馈/日志上传/搜索抓取；关闭本机程序、终端、目录选择、文件系统、Office；品牌化为"海防助手"                                                                | `project/merine.patch.yml`、`834aabe`          |

## 4. 这两天做了什么（提交时间线）

| 提交      | 主题                                                       |
| --------- | ---------------------------------------------------------- |
| `b1cfd68` | 引入裁剪后的 Harness fork（`apps/agent`）                  |
| `00515f3` | `agent.sh` 把本地 Agent 纳入启停/打包                      |
| `5bcc1ec` | 品牌改为海防助手；收敛外传能力                             |
| `cd3d7c3` | api/web/agent 三产物打包                                   |
| `834aabe` | 内网收敛本机能力；业务语义替换；关闭插件管理器             |
| `ae501d0` | 数据源管理（V53）：连接、加密、只读检查、视图白名单        |
| `794d383` | 站内助手废弃；删除 Function Calling；主站入口重定向到 3080 |
| `54d8308` | Agent 支持 Mermaid；会话标题解析修复                       |
| `10690fc` | 生产反向代理文档；项目现状文档                             |
| `b61dc41` | 单操作员只读查询链路 + 服务间凭据桥（V54 任务汇总视图）    |
| `5456c8e` | 按要求恢复 Agent 插件管理入口（能力取舍已确认）            |
| `bbe633c` | 查询范围与语义管理（V55）+ 探测接口 + 桥协议 v2            |
| `943ad79` | Agent 目录接入语义版本与 `catalogRevision`                 |
| `cc63624` | 查询范围详情页；智能体页面统一为标准列表版式               |

### 未提交（两个并行工作者的改动）

- **本线（安全与首批业务视图）**：`V56` 首批业务视图与字段说明模板；Codex 审查 6 项修复；`scripts/seed-query-scope.mjs` + 模板；Agent 字段来源校验；探测结果绑定配置版本；文档更新。
- **Codex 线（页面重构）**：查询范围与数据源页面组件化（`ScopeMetadataEditor`、`ScopeRangeModal`、`DataSourceCard`、共享 `ResourceCollection`），`DESIGN.md` 调整，新增 `model.test.ts`。

## 5. 首批业务视图（V56）与说明配置

| 视图                                                                                            | 一行代表     | 用途                                         |
| ----------------------------------------------------------------------------------------------- | ------------ | -------------------------------------------- |
| `ai_task_summary`（V54 扩列）                                                                   | 一项任务     | 总体办结、期限、当前责任、分支统计           |
| `ai_task_branch`                                                                                | 一条责任分支 | 当前承办、期限、办结时间、结果代码           |
| `ai_intel_topic`                                                                                | 一条原始信息 | 信息条数与首次发布                           |
| `ai_intel_receipt`                                                                              | 一条接收记录 | 发送批次、送达、签收、反馈预聚合             |
| `ai_port` / `ai_anchorage` / `ai_island` / `ai_police_station` / `ai_port_officer` / `ai_wharf` | 一条档案     | 涉海要素；码头 LEFT JOIN 保留无关联/停用记录 |

- 说明以"用途与统计口径 + 字段中文说明"存入查询范围元数据，经桥下发，Agent 合并后给模型；歧义时模型应先澄清。
- 本地配置：固定数据源"本地任务统计-只读"，10 个视图、131 条字段说明、配置 v4、语义 v11，已复检启用。
- 可复用资产：`scripts/query-scope-seed.json`（模板）、`scripts/seed-query-scope.mjs`（只补缺失、幂等、失败整批停止、重新读取后重新合并）。

## 6. 本地验证证据

- Java 主代码 + 测试代码编译通过；Agent `tsc`；web `typecheck` + `build`；Prettier；`git diff --check`。
- 视图无重复行：10 个视图行数与底表一一相等（14/45/9/38/4/6/3/6/12/12）。
- 口径：总体办结视图与底表同为 7；`open_branch_count`/`overdue_branch_count`/`last_action_at_utc` 与底表逐行 0 处不一致。
- 指标区分：信息 9、发送批次 19、送达 38、签收 22。
- 桥：`contractVersion=2`、10 视图各带说明、响应 16.6 KB。
- Agent 真实路径（只读账号）复跑 7 条查询通过；`GROUP BY` 误判缺陷已修复。
- 安全修复实测：底表名 `sys_unit` → `VIEW_DENIED`；换目标复用密码 → 400；同目标免密 → PASSED；`inspect` 返回 `sourceVersion`。
- **未运行测试文件**；未做浏览器验收与模型问答（由用户执行）。

## 7. 已知缺口与风险（评审重点）

1. **身份与数据范围（P0）**：Agent 单操作员、进程令牌共享；没有用户身份桥、没有单位隔离。视图里有什么单位就能查什么单位，不能宣称"当前用户本单位"。
2. **指标确定性（P0）**：说明只是模型理解依据，不强制口径。漏加办结条件、选错时间字段仍可能形成合法 SQL；正式指标若要稳定一致，需要确定性指标执行器或校验层。
3. **北京时间按日/月统计（P1）**：SQL 白名单没有时区转换函数，直接对 UTC 字段 `DATE()` 会按 UTC 分日；目前靠说明与 `get_business_time` 约束，缺硬保障。
4. **结果治理（P1）**：查询结果与说明会进入模型上下文并落到 `DSH_HOME/sessions`；关闭遥测不等于结果不发给所选模型，也没有保留期限/脱敏策略。
5. **回归测试滞后（P1）**：数据源/查询范围回归仍按旧流程（连接保存写白名单、改目标可留空密码、无视图类型用例），与本轮行为不一致。
6. **TLS 生产演练（P1）**：`VERIFY_IDENTITY` 本机无法验证（开发容器自签证书，显式 `REQUIRED`）；上线前需用可信 CA + 证书 IP SAN 实跑。
7. **DNS 漂移（P2）**：密码复用比较主机名；同一主机名换解析目标时旧密码仍会被使用，需部署侧固定 DNS/hosts。
8. **单实例与会话共享（P2）**：Agent 是单进程、单操作员；多实例/多用户需要重新设计。
9. **审计整合（P2）**：Java 审计与 Agent `query-audit.jsonl` 未打通，审计页看不到 Agent 查询。
10. **遗留与运维（P2/P3）**：历史非视图白名单行没有清理入口（无法启用/查询）；`DataSourceViewQuery`（服务端内置参数化查询）目前只被测试引用；交付 `dist/` 未随最新改动重打。
11. **验证缺口（P2）**：本地合成数据没有"码头无关联或关联停用"的样例，该分支只有结构保证；视图/说明增长后的维护成本尚未验证。

## 8. 请评审的问题

1. "Java 管配置与凭据桥 + Agent 重建 SQL + 只读账号执行视图"这条架构是否成立、能否持续？有没有结构性逻辑漏洞？
2. 桥的信任模型（固定数据源、进程凭据、一致快照、协议版本）还有哪些绕过路径？
3. SQL 重建白名单（单视图、20 列、函数/操作符白名单、字符串转十六进制、LIMIT）是否存在可利用的注入或信息泄露面？
4. 单操作员试验是否可以接受？若要接多用户身份，最小且不破坏现有边界的方案是什么（网关代查 vs 每用户实例 vs 视图内单位过滤）？
5. 指标确定性要不要做？受控指标执行器与"说明 + 人工抽查"的取舍？
6. 北京时间按日/月统计怎么落（SQL 白名单加时区函数？视图输出北京时间日期列？还是在工具层限定区间）？
7. 查询结果进入模型上下文与会话存储，需要怎样的保留、脱敏或回收策略？
8. Agent 查询如何并入 Java 统一审计，才能既覆盖越权尝试又不把结果写入审计？
9. 现有 6 项安全修复（视图准入、密码复用、TLS、脚本核对顺序、并发合并、探测缓存）是否还有遗漏的同类问题？
10. 这套方案继续投入前，最应该先补的 P0/P1 是什么？

## 9. 复现与验证入口

```sh
./scripts/dev.sh status                 # 开发栈
./scripts/dev.sh agent status           # Agent（3080）
node scripts/seed-query-scope.mjs <数据源ID>          # 查询范围配置演练（不写）
node scripts/seed-query-scope.mjs <数据源ID> --apply  # 合并范围与说明、复检启用
```

- 关键文档：[只读查询运行说明](../../tasks/agent-readonly-query.md)、[查询范围设计](../../tasks/agent-query-scope-management-plan.md)、[项目现状](project-status-2026-10-11.md)、[生产反代配置](../../../apps/agent/project/deploy/nginx-reverse-proxy.md)。
- 关键代码：`apps/api/.../agent/datasource/`、`apps/agent/packages/merine/query/src/`、`apps/web/src/features/query-scopes/`。
