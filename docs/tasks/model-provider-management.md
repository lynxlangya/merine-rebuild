# 模型供应商配置首版

调研与实现日期：2026-10-09，模型配置与推理强度补充于 2026-10-10，千问改用千问 AI 平台地址亦于 2026-10-10。管理供应商连接与其下的模型清单；真实模型调用已在阶段 2.2 接入（OpenAI 兼容流式，按连接 + 模型标识取用）。供应商模板是填表起点，不代表所有模型、接口或功能已通过兼容性验证。

## 调研结论

共同配置是 API Key、Base URL 和模型 ID。前两者属于供应商连接，模型 ID 属于连接下的模型清单（`ai_model_provider_model`）：一个连接可挂多个模型，界面按「连接 + 启用模型」提供选择。同一供应商可以建立多份连接，以区分账号、地域与用途。首版统一记录 OpenAI Chat Completions 兼容基础地址；不把截图里的 Anthropic 地址作为通用地址。

| 供应商          | 国内通用 Base URL 预设                             | 需要注意                                                                                                            | 官方依据                                                                                        |
| --------------- | -------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------- | ----------------------------------------------------------------------------------------------- |
| DeepSeek        | `https://api.deepseek.com`                         | 同时有 Anthropic 格式地址，两者不可混用                                                                             | [首次调用](https://api-docs.deepseek.com/)                                                      |
| 千问 AI 平台    | `https://maas.qianwenaiapi.com/compatible-mode/v1` | 与阿里云百炼是两套平台与密钥体系，地址不可互换；按量付费 Key 为 `sk-ws-`、Token Plan 专用 Key 为 `sk-sp-`，不要混用 | [首次调用](https://platform.qianwenai.com/docs/developer-guides/getting-started/first-api-call) |
| 智谱 GLM        | `https://open.bigmodel.cn/api/paas/v4`             | 通用对话接口；编程套餐另配地址                                                                                      | [对话补全](https://docs.bigmodel.cn/api-reference/模型-api/对话补全)                            |
| Kimi / 月之暗面 | `https://api.moonshot.cn/v1`                       | 开放平台支持多种协议；模型间思考参数不同                                                                            | [快速开始](https://platform.kimi.com/docs/get-api-key)                                          |
| 豆包 / 火山方舟 | `https://ark.cn-beijing.volces.com/api/v3`         | 还需已开通的模型或推理接入点；向量接口不能据此假定兼容                                                              | [兼容接口](https://docs.volcengine.com/docs/ark/compatible-with-openai-sdk?lang=zh)             |
| 文心 / 百度千帆 | `https://qianfan.baidubce.com/v2`                  | 使用通用 API Key，区别旧版 AK/SK 与编程套餐                                                                         | [通用配置](https://cloud.baidu.com/doc/qianfan/s/Jmovazfdw)                                     |
| 腾讯混元        | `https://api.hunyuan.cloud.tencent.com/v1`         | 此为存量服务地址；官方正在迁移至 TokenHub，新账号按控制台配置                                                       | [OpenAI 兼容接口](https://cloud.tencent.com/document/product/1729/111007)                       |
| MiniMax         | `https://api.minimax.cn/v1`                        | 参数受具体模型约束，部分模型不能关闭思考                                                                            | [OpenAI 格式](https://platform.minimax.cn/docs/api-reference/text-openai-api)                   |

后续值得尝试的能力：流式输出、结构化 JSON、工具调用、思考强度、图像输入、联网搜索和上下文缓存。这些不能当成供应商级通用开关。例如 MiniMax 部分模型强制思考，Kimi 不同模型的推理配置不同；请求参数的名称和取值要在选定模型后验证。优先做“选择一个模型 → 本地替身验证 → 经授权的真实调用”，不提前添加一组保存后不生效的开关。

## 页面与数据

- 入口：助手左下角用户菜单 →「设置」（`/agent/settings`）。后台「智能体管理 → 模型供应商」菜单已在 V43 下线（权限码 `agent:provider:*` 保留），页面实现与后台共用同一个 `features/model-providers`。
- 上方是已配置卡片，支持名称搜索和分页；下方为八家供应商快捷模板，另有自定义入口。卡片显示配置名、供应商、备注、地址、启停状态与密钥已保存标识，不展示伪造的连通状态。
- 抽屉包含供应商模板，以及必填的名称、备注、官网、请求地址、API Key；编辑时密钥留空保留原值。供应商标识一律使用各家官方图标（`apps/web/public/vendor-icons/`，来源与格式见同目录 `ICONS.md`，指示性使用、商标归各自公司），界面按 `vendor` 取图标，不再有图标自选；原来的 `icon` 列已由迁移 V48 删除（配置里存自选图标码在新方案下没有意义）。
- **连接配好之后全体登录用户可用**：使用侧 `GET /api/agent/models` 登录即可读，对话里的模型选择器对任何有 `agent:chat:use` 的用户都展示已启用连接下的启用模型，与单位、角色无关。**管理动作按权限码控制**，与后台其它管理页一致：`agent:provider:read` 进设置页（无权限时账户菜单不显示「设置」，直接访问页面看到 403 结果页）、`create`/`update`/`delete` 各自控制新增、编辑（含「获取模型列表」）、删除按钮，后端接口同样逐个判定。
- 这些权限码**故意不挂在菜单节点上**（助手有自己的外壳，不进后台菜单），角色管理里按**模块**分组呈现：`GET /api/system/roles/permission-tree` 在菜单树之外，为每个「有非菜单权限码」的模块补一个分组（节点 id `module:<模块>`，组名取模块显示名）。智能体模块对应「智能体」分组，内含 `agent:chat:use` 与 `agent:provider:*` 共 5 个权限码，勾选后与菜单权限一样生效。模块清单登记在 `system/permission/PermissionModules`，回归会断言库里不存在未登记前缀；**不合并成跨模块的「其他权限」**——归属要一眼可见，死码也不能藏在大桶里（V49 就是借这个清点面发现并删除了旧骨架页遗留的 `collaboration:task:read` / `collaboration:flow:read`）。
- 「可用模型」在同一个抽屉里维护：模型标识（调用时原样发送，取值以供应商官方文档为准，深寻当前为 `deepseek-flash` / `deepseek-v4-pro`）、显示名（空白时服务端回填为标识）、备注、可用推理强度、启停与展示顺序（按提交顺序）；同连接下模型标识唯一，删除连接连带删除其模型。
- 同一厂商可建立多份配置，名称全局唯一；新增、编辑（含启停）、删除各自有权限码。删除移除本配置、模型与密钥；助手只按已启用连接与模型展示选项、不持久化引用。
- 后端入口 `agent.provider.ProviderController` → `ProviderService` → `ProviderMapper` → `ai_model_provider` / `ai_model_provider_model`；版本条件和行锁防止并发覆盖，模型集合在同一事务里按模型标识对账增删改，错误保留表单输入。
- 「可用推理强度」是逐模型的可点标签勾选（`Tag.CheckableTagGroup`，取值为两家官方的并集 `NONE/LOW/MEDIUM/HIGH/XHIGH/MAX`，取值以供应商官方文档为准，DeepSeek 当前为 `none`/`low`/`high`/`max`，默认 `high`），落库为逗号分隔列 `reasoning_efforts`，保存时归一到官方顺序；留空表示该模型不显示强度选择、也不发送 `reasoning_effort`。不同模型的思考参数并不通用（部分模型强制思考或另有开关），所以卡片上不显示、也不做供应商级开关。
- **推理强度目录**（`ReasoningEffortCatalog`）按供应商与模型标识收录官方文档写明的取值，拉取模型清单时一起带回：DeepSeek 全部模型 `none/low/high/max`；千问 Qwen3.8 系列 `low/medium/xhigh`（默认 xhigh）、glm-5.3 与 kimi-k3（阿里云直供）`low/high/max`（默认 max）、千问上的 DeepSeek 直供 `high/max`（带日期版本与 4.1-flash 为 `low/high/max`）、月之暗面直供 `kimi/kimi-k3` 仅 `max`；文档没写取值的一律留空（不显示档位、也不发送该参数）。官方默认档位随使用侧选项一起返回（`defaultReasoningEffort`），输入区据此选中。
- 「获取模型列表」（`POST /api/agent/providers/models/discover`）按当前抽屉里的请求地址与密钥读上游 `GET {baseUrl}/models`：表单里刚输入的密钥优先，留空且连接已保存时用服务端保存的那份，两者都没有则拒绝且不出站。返回的模型标识生成候选列表，管理员勾选要导入的模型、选一组推理强度（默认四档全选）后一次性加进表单；表单里已有的标识标「已存在」且不可选，一个连接仍限 20 个模型。权限按场景区分：新连接要 `agent:provider:create`，已保存连接要 `agent:provider:update`。
- 上游接口只返回「有哪些模型」，不返回能力（是否支持思考、哪些推理强度），所以强度来自上面的官方目录而不是探测结果：导入时逐模型填入该模型的官方取值，弹窗里直接显示每个模型会写入的档位，界面不再要求管理员自己选一组。目录之外的模型显示「不设置」。
- 档位展示与目录同源：抽屉里每个模型只列官方目录里的取值（`POST /api/agent/providers/reasoning-efforts` 按 `vendor + modelIds` 批量查询，读权限即可）。目录覆盖的供应商（DeepSeek、千问）里，没收录的模型不给档位并标注「官方未收录该模型的档位，将不发送强度参数」；尚未整理目录的供应商（`covered=false`）保留全部档位由管理员自行勾选。保存时按目录收口，历史数据里不支持的档位会被去掉。
- 出站约束：仅 HTTPS，禁止跟随重定向，连接 5s / 请求 15s 超时，拒绝 `localhost` 与本机、内网、链路本地、多播、ULA 的 IP 字面量目标（域名不解析，自建网关用域名是合法用法）。失败按状态映射：401/403 → `MODEL_LIST_AUTH_FAILED`（字段错误落在 apiKey）、404 → `MODEL_LIST_UNAVAILABLE`、429 → `MODEL_LIST_RATE_LIMITED`、5xx 与网络超时 → `MODEL_LIST_UNAVAILABLE`、结构无法识别 → `MODEL_LIST_PROTOCOL_ERROR`；密钥与响应原文都不进日志、不回前端。
- 使用侧入口 `agent.provider.ModelOptionController` → `ProviderService.modelOptions()`：`GET /api/agent/models` 读取「启用连接下的启用模型」（登录即可，不占 `agent:provider:read`），返回模型标识、显示名、所属连接名、可用推理强度与图标；地址、备注与密钥不进入该响应。
- API 只返回公开配置，密钥不进入查询或保存响应。API Key 仅在当前表单输入期间存在浏览器内存中，关闭即清空，不保存 localStorage。
- URL 限 HTTPS，拒绝内嵌账号、查询串、片段和明显错误的完整对话路径。当前无出站请求；未来加入连通测试或推理前，必须另外实现目标网络校验、重定向限制和超时，不能将当前 URL 格式校验视为 SSRF 防护。

## 密钥保管与运行

使用 JDK 自带 AES-256-GCM，随机 nonce，并将配置 UUID 作为认证附加数据；数据库只存带版本前缀的密文。`MODEL_PROVIDER_ENCRYPTION_KEY` 为 64 位十六进制主密钥，位于被 Git 忽略的本地 `.env`，由 `dev.sh setup` / `up` / `migrate` 在缺失时生成，现有值不会覆盖。Compose 只将它传给后端；生产演练同样从环境注入，不打入镜像。缺失或无效时密钥写入返回 503，不退回明文。

首次升级使用 `./scripts/dev.sh up`；新增迁移需先执行 `./scripts/dev.sh migrate`。若已有 API 容器是在添加环境变量之前创建，须通过 Compose `up -d --no-deps --force-recreate api` 应用新环境，仅 restart 不刷新容器环境。

数据库备份与主密钥需要分别安全保管。不要在已有密文时直接重新生成主密钥；首版不提供在线密钥轮换、密钥找回或供应商连通测试。正式部署的密钥托管与轮换需要单独设计。

## 验证

- 隔离 MySQL 回归覆盖：密文可解密但 API 不回显、保留/替换密钥、修改地址必须重新输入密钥、无权限及只读角色、CSRF、非法输入、并发编辑只允许一个成功、旧版本删除拒绝、密文篡改/错配拒绝、字段注释和无物理外键。
- 使用侧模型选项另有无权限角色与未登录用例：登录即可读、只返回「启用连接 + 启用模型」、响应不含地址、备注与密钥。
- 模型回归覆盖：创建带模型、空白显示名回填、顺序与 sortOrder、替换集合（保留的更新/新的插入/缺的删除）、同连接重复标识拒绝、删除连接连带删除模型、推理强度归一化与落库。
- 模型清单回归覆盖：非 HTTPS 与 `/chat/completions` 地址、缺密钥、非法密钥字符、连接不存在、本机与内网目标、403 权限（新建要新增权限、已保存要编辑权限）、OpenAI 兼容结构的解析与坏结构拒绝，以及推理强度目录的逐系列映射（含「文档没写就留空」与实时/翻译形态不纳入）。
- 目录查询回归：按模型返回档位与默认值、未收录模型返回空集、未整理目录的供应商 `covered=false`、只读角色可查而缺权限角色 403。
- 写操作留痕：连接的创建/更新/删除各记一行 INFO 日志（标识、供应商、模型数量），用于回溯「模型被清空」这类问题——只是排障线索，不能替代统一操作审计（仍在缺口清单里）。前端在保存会让既有模型全部消失时先弹确认，模板切换的确认文案也写明会清空模型列表。**出站调用本身没有自动化用例**：隔离环境里没有可信的 TLS 目标，地址校验与目标策略又都拒绝本机地址；真实连通性用带密钥的 DeepSeek 连接人工验证（见 design-qa），不依赖外部服务的测试底线保持不变。
- 菜单权限清单与迁移、恢复默认菜单清单一致性测试通过；V43 下线后台菜单后 `agent:provider:*` 权限码保留，目录/页面/按钮节点被清理，只读角色接口可用但导航不再出现该页面。
- 浏览器使用合成配置验证新增、编辑、启停、重启后持久化、登录重定向和表单草稿提醒；未发送任何外部模型请求。
- 当前阶段完成的是配置管理，不能据此声称八家供应商的真实模型调用已经兼容。

## 后续

助手对话与流式交互（协议、富内容部件、模型追问、模型标识方案）见[海防助手对话与流式交互设计（评审稿）](agent-chat-design-plan.md)；其中「模型标识放哪里」会反向影响本模块的配置结构。
