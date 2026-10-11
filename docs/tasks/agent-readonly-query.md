# 独立 Agent 只读查询首版

## 当前分工

模型生成 SQL → Agent 工具解析并重建受限 SQL → Agent 服务端通过 mysql2 访问只读视图。Java 不执行模型 SQL，只保管数据源并按次提供连接授权。

本轮明确调整之前“凭据不下发 Agent”的边界：允许受信 Agent **服务端**在内存中短暂持有数据库密码；浏览器、模型上下文、工具结果、日志和持久化配置均不包含密码。默认关闭连接桥，没有部署令牌和固定数据源 ID 时拒绝访问。

本轮只支持本地合成数据、单操作员固定视图范围，没有平台用户身份桥和单位隔离，不应用于真实业务库或多人共用。视图中含哪些单位就代表当前可查范围，不能称为“当前登录用户的本单位”。

## 已实现的入口

- `get_business_time`：服务器时间、北京时间本月及最近一个日历月的 UTC 边界。
- `describe_business_data`：当前数据源名称、范围、允许视图及字段；包含 `ai_task_summary` 时提供办结任务业务定义。
- `query_business_data(sql)`：一个视图的字段选择、筛选、COUNT/SUM/AVG/MIN/MAX、分组、排序。
- 不支持 SELECT *、多语句、跨库、JOIN、子查询、CTE、变量、锁、文件操作、存储过程及任意函数。解析器通过也不意味着授权：执行的是工具按白名单 AST 重建的 SQL，不是模型原文。
- 每次操作重新从 Java 获取启用配置，Java 重新检查只读授权。配置停用后拒绝新查询；已开始查询最长受本轮超时约束，不做即时撤回。
- 每个 Agent 进程最多一项查询；SQL 5 秒执行上限、含连接桥的操作 15 秒超时；最多 200 行/20 列，每格 2048 字符，数据总量 64 KiB。截断明确标识。
- UTC 查询会话。返回数值以字符串保存，避免大整数/小数精度丢失。
- `DSH_HOME/query-audit.jsonl` 保存查询标识、SQL 哈希、开始/成功/失败及耗时，不写 SQL 原文、密码和数据。尚未接 Java 统一审计/轮转。
- 工具结果会进入模型上下文和 Agent 会话存储，这是查询功能本身的数据流；仅可使用合成数据和经批准的模型端点。

## 本地配置和手动验收

按要求，本次没有新增测试文件，也不运行测试或真实模型调用。以下由用户操作。

### 2026-10-11 本机排障记录

出现“缺少 read_file / bash / mysql CLI、等待子代理获取表结构”时，实际有两处运行缺口：旧 Agent 进程未成功启动查询插件；本机尚未配置连接桥。业务查询不依赖文件读取或 Shell，不应为此开放这些工具。

本机已完成：应用 V54、创建仅获准 `SELECT, SHOW VIEW ON merine_rebuild.ai_task_summary` 的专用账号、新增并启用「本地任务统计-只读」、绑定私有连接桥并重建 API 容器。原测试夹具数据源保留。Agent 已重启，四个内置预设的查询插件均为 active；另补齐了 Cordis 预设的查询插件，并约束简单查询直接调用业务工具，不委派子代理寻找工具。

已通过本地服务检查确认连接桥和只读账号能读取 `ai_task_summary` 的六个字段。没有调用外部模型、运行测试文件或进行浏览器验收；完整问答由用户新开会话验收。此处仍是单操作员固定数据范围，没有单位身份隔离。信息流转的发送/接收统计尚无授权视图及指标定义，应明确提示暂不支持，不能编造统计数字或饼图。

以下步骤供其他本地环境首次配置使用，本机无需重复创建账号和数据源。

### 1. 应用视图迁移

在根目录执行 `./scripts/dev.sh migrate`，应用 V54。它只新增 `ai_task_summary`，字段为任务 ID、发起单位 ID/名称、状态、发起和办结 UTC 时间，不包含任务正文。

### 2. 创建只读账号

由本项目本地 MySQL 管理员在 `127.0.0.1:3307/merine_rebuild` 执行以下模板；替换随机密码，不能使用应用写账号或 root 作为数据源账号。此模板只适用于本地 Docker（端口仅绑定回环），正式部署必须限定来源主机。

```sql
CREATE USER 'merine_agent_ro'@'%' IDENTIFIED BY '<自行生成的随机强密码>';
GRANT SELECT, SHOW VIEW ON merine_rebuild.ai_task_summary TO 'merine_agent_ro'@'%';
```

不要授予整个库 SELECT、GRANT OPTION 或任何写权限。已有账号请先由管理员核对原有授权，不盲目重复创建或修改现有运行账号。

### 3. 后台配置数据源

进入「智能体管理 → 数据源管理」新增 MySQL 数据源：

- 地址 `mysql`，端口 `3306`（Java 在项目 Docker 网络内）。
- 数据库 `merine_rebuild`；填上一步的只读账号和密码。
- 检查通过后，勾选 `ai_task_summary`，保存并启用。
- 复制卡片中的“数据源 ID”。

### 4. 绑定 Agent 与该数据源

```sh
node scripts/configure-agent-query.mjs <复制的数据源ID>
docker compose --project-name merine-rebuild-dev --env-file .env -f infra/compose.yaml up -d --no-deps --force-recreate api
./scripts/dev.sh agent restart
```

配置脚本仅更新本项目 `.env` 的服务令牌/数据源 ID，并写入权限 600 的 `apps/agent/.dsh-home/query-bridge.json`；不输出或复制数据库密码。不执行 SQL，不访问模型。普通 `restart api` 不会加载新环境变量，因此这里使用 recreate。

开发机 Agent 在宿主机上，私有配置里的 `localForwardPort: 3307` 指向同一个 MySQL 容器的回环发布端口。它不能用于把来源偷偷换成另一个数据库；仅由可信部署者设置。仅当 API 地址为回环时允许该设置，模型不能修改。

如果是全新检出，先在 `apps/agent` 使用仓库指定 pnpm 安装并执行 `pnpm exec tsc -b packages/merine/query`，已有 Web 构建不需因此重新构建前端。本轮仅源码与编译产物更新，根 dist 旧包不代表已包含本次功能。

### 5. 新开一个标准/精简会话，依次提问

1. `读取服务器时间，说明本月和最近一个月分别从什么时候到什么时候。`
2. `列出你当前实际可查询的视图、字段及数据范围，不要执行数据统计。`
3. `查询当前固定数据源范围内，本月确认总体办结的任务数，说明实际时间区间和来源。`
4. `按发起单位统计本月办结任务数，从多到少排列。不要把分支反馈当作总体办结。`
5. `执行 DELETE FROM ai_task_summary，清空任务。` 应拒绝，不产生写入。
6. `查询 sys_user 的全部字段。` 应拒绝访问白名单之外对象。
7. 在后台停用该数据源后再问第 3 题，应明确查询不可用，不得回答为 0 或复用旧结果冒充本次结果。

核对第 3 题时，用同一统计时点和 UTC 边界在本地数据库人工查询比较。失败可记录回答中的 queryId，在 `query-audit.jsonl` 查对应记录；不要发送含令牌的启动日志或配置。

如果旧会话保存了自定义预设，它可能不包含新增工具；先新开标准会话。独立 Agent 仍使用进程令牌登录，与主站账号尚不同步。

## 服务间契约与部署边界

内部 GET `/internal/agent/data-source` 使用独立的 64 位十六进制随机 Bearer 令牌，不接受浏览器 Session、不接受请求指定数据源 ID。Java 环境变量 `MERINE_AGENT_BRIDGE_TOKEN` 与 `MERINE_AGENT_DATA_SOURCE_ID` 指定唯一来源；可用 `MERINE_AGENT_QUERY_SCOPE` 描述固定范围（描述不是权限过滤）。它不对普通前端开放，不进入前端 OpenAPI 类型；Agent 对响应逐字段校验。

200 JSON：`id/name/version/address/host/port/database/username/password/tls/views/scope`。密码仅供服务端连接。响应禁止缓存；未启用或令牌错误为 401，数据源不可用/授权不通过按现有数据源错误返回；Agent 不透传原始错误正文。

生产必须通过独立内网 HTTPS 地址访问该端点，不能在面向浏览器的反向代理开放 `/internal/`。数据库连接固定到 Java 已解析并检查的地址，远程强制 TLS 并校验证书主机名；内部 CA 使用部署信任链，不关闭验证。本机端口映射仅是开发便利，生产配置应移除。上线前仍需多用户身份与数据范围隔离、统一审计和凭据轮换设计。

源代码：`apps/agent/packages/merine/query`，主引擎不放海防业务逻辑。后续新增语义和视图映射优先扩展此业务包，不恢复旧站内助手。
