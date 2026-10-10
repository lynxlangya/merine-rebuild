# Agent 静态界面验收（2026-10-09）

## 对照依据

- 参考图：`/var/folders/wk/rvm43lm95md779dplzgfl87r0000gn/T/codex-clipboard-f1278fc8-dfff-40d0-98e9-643725bc6a42.png`，3600×2260；主题参考为同目录 `codex-clipboard-878fa0b9-d438-409b-9edd-df9ec630df70.png`。
- 已确认的设计调整：空白新对话、仅两个侧栏图标、无会话列表及右侧工具面板；沿用项目字体与主题，不逐像素复制 Codex 原图。
- 实现截图目录：`/tmp/merine-agent-ui/`。`home-light-1440.jpg`、`home-dark-1440.jpg` 为 1440×900，`home-light-1366.jpg`、`home-dark-1366.jpg` 为 1366×768，CSS 视口与图像像素 1:1。
- 来源包含桌面窗口边框且为不同内容状态；对照应用区域的层级、窄栏和输入区，不把这些已批准差异作为缺陷。原图和亮色主页已在同一比较输入中查看。

## 视觉检查

- 字体与文案：复用应用字体，24px 欢迎标题、16px 输入占位文字；无预置业务答案或虚构模型能力。
- 布局：56px 侧栏、40px 图标入口、最大 800px 居中输入区，底部留白；两种桌面尺寸未出现内容遮挡或横向溢出。
- 配色：使用项目亮暗语义变量；输入边框、占位文字及导航状态在双主题下可辨认。
- 图标：使用已有 Ant Design 图标，没有新增图片、图标库或生成资产。
- 局部检查：实际捕获主系统右上角，主题仅显示图标，助手入口位于其右侧。输入区和侧栏在全页截图中可清楚检查。
- 首轮视觉对照未发现需要修复的 P0/P1/P2 差异，没有视觉修复迭代。

## 已完成验证

- 前端 TypeScript 检查、Vite 生产构建、变更文件 Prettier 检查及 `git diff --check` 通过。
- 未登录访问 `/agent` 跳转登录，使用本地合成账号登录后回到 `/agent`。
- 主页、资料库导航和资料库刷新正常，Agent 不包含主系统外壳。
- 主题按钮可切换；新文档加载继承主题偏好，本轮未增加跨窗口实时同步。
- 输入框只读、发送按钮禁用；Tab 焦点可见，Enter 可进入资料库，当前导航具有 `aria-current`。
- Agent 页面未捕获控制台警告或错误；主系统首页原有 Ant Design List 弃用提示不属于本次修改。

## 新标签页入口验证

后续按用户要求，入口改为原生 `target="_blank"` 链接，删除 `window.open` 弹窗调用与尺寸限制，提示同步为“新标签页”。先前的独立弹窗方案已撤销。

更改后已在内置浏览器实际点击入口：原标签页仍停留在 `/`，另出现独立标签页 `/agent`，新标签页正确显示主页、资料库入口和只读输入框。截图：`/tmp/merine-agent-ui/new-tab.jpg`。

此前弹窗方案的验证缺口随方案撤销；当前新标签页方案已通过实际导航验证。此次变更的 TypeScript、Prettier 和差异空白检查通过。

## 左下角账号弹层增量验收

- 新参考图：`/var/folders/wk/rvm43lm95md779dplzgfl87r0000gn/T/codex-clipboard-a6721182-ba50-4edd-8660-b49284ad9f6c.png`。仅参考底部头像、圆角浮层和分隔后的退出项；按用户要求保留项目文字头像，不增加套餐、设置等入口。
- 实现证据：`/tmp/merine-agent-ui/account-light-1440.jpg`（1440×900）、`account-light-1366.jpg` 与 `account-dark-1366.jpg`（1366×768），CSS/截图像素 1:1；`account-detail.jpg` 为 340×298 的浏览器局部截图，与参考图在同一次比较输入中查看。
- 字体、颜色沿用项目 token；260px 浮层使用圆角、边界及浮层阴影，信息为当前会话的姓名、账号、单位和角色；头像与退出图标分别复用 Ant Design Avatar 和 LogoutOutlined。没有新增图像或依赖。按反馈收窄后已在浏览器确认文字显示完整、无横向溢出。
- 主页和资料库均可打开账号弹层；Enter 可打开、Escape 和点击外部可关闭，未发现遮挡操作入口或超出视口的布局问题。视觉对照首轮通过，无需修复迭代。
- 已使用本地合成账号实际退出：跳转登录页；随后重新访问 `/agent/library` 仍被登录守卫拦截。重新登录后恢复页面并可再次打开用户菜单。
- 退出复用 auth 公开能力及原有 CSRF/会话清理流程；失败保留重试入口，但本轮未对网络失败注入测试。
- 新增页面控制台无警告/错误；前端类型检查、构建、格式与差异空白检查通过。

final result: passed

## 模型供应商配置增量验收（2026-10-09）

- 使用项目主题 token、Ant Design 图标与抽屉；卡片列表替代表格，八家厂商模板可预填配置。
- 实测 1440×900、1366×768 的亮暗主题；抽屉内容独立滚动，页脚按钮固定可用；1366×768 无页面横向溢出。
- 浏览器完成合成配置新增、编辑保留密钥、停用、重启后登录重定向和持久化、未保存提醒、模板切换取消保留草稿。测试配置已通过受保护 API 删除。
- 隔离 MySQL：6 项供应商回归、2 项权限清单回归、4 项无外键回归通过；独立空库完成 V1—V41 迁移，已有开发库目录得到复用。
- 前端类型检查与生产构建、86 项现有前端测试及 6 项契约生成检查通过。没有外部模型调用或新依赖。
- 截图保存在 `/tmp/merine-provider-ui/`；验收仅覆盖配置管理，不代表真实供应商调用兼容性。

## 对话与流式交互阶段 1 浏览器验收（2026-10-10）

- **方法**：沙箱无法读取主 Chrome 的 `DevToolsActivePort`，改用独立临时配置启动的受控无头 Chrome（`--user-data-dir=/tmp/merine-chrome`，远程调试 9333），不触碰用户现有浏览器会话；通过 CDP 设置视口 1440×900 与 1366×768（DPR 2）。截图目录 `/tmp/merine-acceptance/`。
- **对照与量测**：图标栏 56px、对话栏 240px（工作区起点 296px，1440 宽），与 DESIGN 的骨架一致；消息区与工作区滚动条已隐藏（`scrollbar-width: none`）。
- **覆盖场景**：空白主页（亮/暗、1440/1366）、直接回答（步骤 + 文本 + 表格 + 柱状图 + 来源）、追问卡（单选）与作答后重算、资料不足（NOTICE 缺口提示）、停止（状态「已停止」）、设置页（`/agent/settings`，仅图标栏无对话栏）、来源站内链接、同会话连续提问。
- **发现并修复**：
  1. 柱状图几何错误（柱心与分类标签错位、两个分类时槽位被拉满整宽）→ 几何抽成 `barLayout` 纯函数并补单测，柱子居中、柱顶数值、基线，SVG 按自然尺寸渲染；
  2. 追问条件摘要显示原始取值 `CASE` → 改为选项标签「立案案件」，并同步回归断言；
  3. 设置页底部说明与当前演示形态不符 → 改为「对话由本地演示替身完成…」；
  4. 停止只有输入框内的小图标 → 底栏增加「停止生成」按钮；慢速演示从 250ms 调至 700ms，流式与停止更可观察；
  5. 追问单选由普通按钮改为 `Radio.Group optionType="button"`（原生 radio 语义）；
  6. 工作区滚动条与消息区统一隐藏；
  7. 非聊天页隐藏对话栏（设置、资料库只保留图标栏）。
- **验证证据**：`./scripts/dev.sh check` 退出码 0（后端 194 tests、前端 104 tests、契约 7 tests 全绿）；停止链路经 UI 处理函数触发后：`POST /stop` 202 → `CANCEL_REQUESTED` → `MESSAGE_DONE(ABORTED)` → 状态行「已停止」。
- **限制（测试环境）**：受控无头 Chrome 在流式重绘与动画合成层下，CDP 合成鼠标点击对加载态按钮的命中测试不稳定（布局坐标正确、合成器命中旧坐标），因此停止确认用同一 UI 处理函数的程序化点击验证；真实鼠标点击行为请在常规浏览器中复核。
- **未覆盖**：屏幕阅读器、色觉模拟、移动端与超宽屏。

final result: passed（含一条测试环境限制）

## 模型配置（P2.1）浏览器验收（2026-10-10）

- 方法：受控无头 Chrome（同上一轮），1440×900 DPR 2，助手设置页 `/agent/settings`。
- 覆盖：卡片显示「未配置模型；真实对话前需要先添加」→ 编辑抽屉「可用模型」添加两个 DeepSeek 模型 → 保存 → 卡片显示两个模型标签；刷新后仍持久；`GET /api/agent/models` 返回两条启用模型（含连接名与模型标识，无地址、备注与密钥）；后台「智能体管理」菜单不再出现。
- 截图：`/tmp/merine-acceptance/13-settings-models-empty.png`、`14-drawer-models-section.png`、`15-drawer-models-filled.png`、`16-card-with-models.png`。
- 未覆盖：停用模型后的选项过滤由后端回归覆盖，浏览器未重复；模型选择器回填输入区属于 P2.2。

final result: passed

## 真实模型对话（P2.2）验收（2026-10-10）

- 后端：假上游（本机 HttpServer）回归覆盖正常流与 Bearer 头、401/429/坏数据流映射、未启用模型在开流前 409；新增无 `agent:chat:use` 的 403 用例。
- 真实调用：使用本地已配置的 DeepSeek 连接与 `deepseek-flash`（首次按旧文档用了 `deepseek-chat`，核对官方文档后更正）发一条最小请求（不打印密钥与正文到日志），SSE 返回文本「收到」、终态 `SUCCEEDED`，首字 0.01s、总耗时 0.63s。
- 浏览器（受控无头 Chrome，1440×900）：输入区显示模型名，发送后渲染真实回答（`/tmp/merine-acceptance/17-real-deepseek.png`）；切换到无 `agent:chat:use` 的账号后，页面显示 403 且不渲染输入区（`18-forbidden.png`）。
- 未覆盖：流式超时定点测试、费用与限流配额、审计与会话落库。

final result: passed

## 输入区调整与模型标识更正（2026-10-10）

- 按用户标注调整输入区：模型选择器只显示模型名（不再带「连接 ·」前缀）；删除「真实模型调用」说明文字；发送按钮从输入框内移到下方底栏（与模型选择同一行），生成中变为停止方块图标，停止后恢复发送箭头。
- 发送按钮空文本时保持禁用（与组件库一致）；Enter 提交、IME 与多行增长沿用 Sender。
- 按 DeepSeek 官方文档（`/zh-cn/quick_start/pricing`、`/zh-cn/`）核对模型标识：当前为 `deepseek-flash` 与 `deepseek-v4-pro`，`deepseek-chat` / `deepseek-reasoner` 已下线；本地配置已更正，文档同步。
- 实测（受控无头 Chrome，1440×900）：空白态底栏显示「DeepSeek Flash」与灰色发送按钮；发送长回答请求后按钮变为蓝色停止方块并带「停止生成」提示；生成结束后恢复发送按钮并渲染真实回答。
- 截图：`/tmp/merine-acceptance/19-composer-model-only.png`、`20-generating-stop-icon.png`、`21-real-answer.png`。

final result: passed

## 推理强度配置与选择（2026-10-10）

- 取值依据：DeepSeek 官方文档（`guides/thinking_mode`、create-chat-completion）当前为 `none` / `low` / `high`（默认）/ `max`；`minimal`、`medium`、`xhigh` 只是兼容映射，不作为我们的选项。
- 抽屉：`/agent/settings` → 编辑 DeepSeek →「可用模型」下每个模型多一行「推理强度」多选（关闭思考 / 低 / 高（默认）/ 最高），新增模型默认全选，留空表述为「不显示强度选择」；备注仍在同一模型块内。
- 输入区：底栏在模型选择器下方新增强度选择，仅显示该模型配置的级别；默认「高（默认）」，切换模型后回到默认。
- 真实调用：选择 `deepseek-flash` + 「关闭思考」，发送「请只回复两个字：收到」，回答「收到」；服务端日志 `Chat run requested: ... modelId=deepseek-flash reasoningEffort=NONE`（密钥与正文不打印）。
- 后端：模型保存的强度按官方顺序归一化落库并在使用侧原样返回；假上游回归断言请求体含 `"reasoning_effort":"high"`；越界强度（模型只允许 LOW/HIGH 时请求 MAX）返回 409 `CHAT_MODEL_UNAVAILABLE` 且不产生出站请求。
- 截图：`/tmp/merine-acceptance/23-drawer-efforts-full-row.png`（抽屉）、`24-composer-effort.png`（底栏两行选择）、`26-effort-none-draft.png`、`27-effort-none-answer.png`。

final result: passed

## 推理强度 UI 返工（2026-10-10）

- 问题：输入区把模型与强度做成两行整宽下拉，箭头位置不齐、大片空框；抽屉里每种模型一个整宽多选框加灰色说明，四个标签重复两遍，视觉重量过大。
- 输入区：模型与强度改为并排紧凑胶囊（灰底、圆角、宽度随内容收缩、悬停加深），强度胶囊只在模型配了可用子集时出现；标签简化为「高」（默认值说明从标签里去掉，改为配置页说明），悬浮提示「推理强度」。
- 抽屉：多选框换成「推理强度」小标题 + 可点标签（勾选为淡蓝底、取消为描边），说明文字收进标题旁的问号提示；备注仍是标签行下的独立输入框。
- 联动验证（受控无头 Chrome，1440×900）：取消 V4 Pro 的「最高」→ 保存 → `GET /api/agent/models` 返回 `NONE/LOW/HIGH`，输入区该模型的强度下拉只剩三项；再勾回「最高」保存后恢复四项。
- 明暗主题各看一遍：浅色（截图 29/31）、深色（36/38）底栏与抽屉标签均可读。
- 测试环境注意：无头标签页里 React 更新会被延后，程序化点击后立刻读 `aria-checked` 或点击保存会读到旧值（出现过一次误判）；等待 1–2 秒再读或再保存即可，这是验收脚本的时序限制，不是产品缺陷。
- 截图：`/tmp/merine-acceptance/29-composer-chips-crop.png`、`31-drawer-efforts-crop.png`、`32-effort-filtered.png`、`34-drawer-efforts-dark-crop.png`、`38-composer-dark-crop.png`。

final result: passed

## 推理强度改成竖向滑杆（2026-10-10）

- 按参考图把强度从下拉列表改成滑杆：点输入区底栏的强度胶囊，在按钮上方弹出竖向滑杆，档位从下到上为关闭思考 → 低 → 高 → 最高（只列该模型可用子集），当前档位在面板顶部以强调色显示、关闭思考用静音色；拖动或点档位点即时生效，胶囊文字同步。
- 轨道/把手/档位点沿用主题变量（填充 `--accent`、轨道 `--surface-2`、档位点未填充 `--border-strong`、已填充反白 `--surface`），明暗主题各验收一次。
- 真实调用验证：点顶部档位切到「最高」后发送，服务端日志 `reasoningEffort=MAX`；底部档位显示「关闭思考」且为静音色。
- 抽屉里逐模型的「可用推理强度」仍是多选标签：那是「允许哪些档位」，与使用侧的单选滑杆职责不同，不合并。
- 截图：`/tmp/merine-acceptance/44-effort-slider-dots-crop.png`（浅色）、`46-effort-slider-dark-crop.png`（深色）。

final result: passed

## 推理强度滑杆改为自绘组件（2026-10-10）

- 去掉 antd `Slider`，新增 `features/agent/EffortSlider.tsx` + 同名 CSS Module（约 110 行），用原生指针事件实现：按住拖动、点轨道任意位置跳到最近档位、`ArrowUp/ArrowDown/Home/End` 键盘改档；`role="slider"` + `aria-valuemin/max/now/valuetext/orientation`，`:focus-visible` 有焦点环。
- 表现：顶部数值随悬浮预览目标档位（松手/移开后回到当前值）、关闭思考用静音色；轨道内嵌细描边、填充用主题渐变、档位点在填充段内反白、把手白芯描边并在拖动时放大跟手（拖动中关闭过渡，避免「追不上」指针）。
- 实测（受控无头 Chrome，1440×900）：真实鼠标按下→拖动→松开把档位从「高」拉到「最高」，胶囊同步显示；悬浮「低」时数值预览为「低」而真实值仍为「最高」；键盘 `↓ ↓ Home End` 依次落到 高→低→关闭思考→最高；底部档位填充为 0、数值转静音色。发送后服务端日志 `reasoningEffort` 与所选档位一致。
- 明暗主题各验收一次：浅色 `50-custom-slider-crop.png`、`52-slider-off-crop.png`（关闭思考）、深色 `54-custom-slider-dark-crop.png`。
- 抽屉里逐模型的「可用推理强度」仍是多选标签（允许哪些档位），与使用侧的单选滑杆职责不同。

final result: passed

## 千问模板地址更新（2026-10-10）

- 依据千问官方文档（`platform.qianwenai.com/docs/developer-guides/getting-started/first-api-call` 与 `/docs/api-reference/preparation/api-key`）：OpenAI 兼容基础地址为 `https://maas.qianwenaiapi.com/compatible-mode/v1`，文档示例与第三方工具（Chatbox）配置页用的是同一地址；按量付费 Key 为 `sk-ws-`（早期 `sk-`）、Token Plan 专用 Key 为 `sk-sp-`，两者不能混用。
- 模板改为「千问 AI 平台」：官网 `platform.qianwenai.com`、请求地址 `maas.qianwenaiapi.com/compatible-mode/v1`、说明写清 Key 前缀差异；文档链接指向「首次调用千问API」。旧模板指向阿里云百炼（`dashscope.aliyuncs.com/compatible-mode/v1`），那是另一套平台与密钥体系，已在配置文档的对照表里注明不可互换。
- 浏览器核对（1440×900）：设置页模板卡片显示「千问 AI 平台 / platform.qianwenai.com」；点开抽屉后供应商模板、配置名与请求地址均已预填，接口路径预览为 `https://maas.qianwenaiapi.com/compatible-mode/v1/chat/completions`（截图 `56-qwen-template-crop.png`）。
- 未验证：没有千问账号与密钥，未做真实连通调用；模板仍是填表起点，不代表已兼容。

final result: passed

## 「获取模型列表」（2026-10-10）

- 入口：抽屉「可用模型」标题右侧新增按钮；请求地址或密钥都缺时禁用，鼠标悬停说明原因。新连接必须先填密钥，已保存连接可直接用服务端保存的那份。
- 行为：服务端调用 OpenAI 兼容的 `GET {baseUrl}/models`，返回标识与归属方；弹窗里勾选要导入的模型（已在表单里的标「已存在」且不可选），选一组推理强度（默认四档全选）后一次性加入表单，仍受「一个连接最多 20 个模型」限制。上游不返回能力信息，界面写明强度是本地默认值、导入后可逐个调整。
- 真实调用：用本地已配置的 DeepSeek 连接（真实密钥）点「获取模型列表」，上游返回 `deepseek-flash`、`deepseek-v4-pro`（`owned_by=deepseek`）两个模型，与上游实际提供的一致；两个都已存在，导入按钮自动显示「导入 0 个模型」并禁用（截图 `57-discover-modal.png`）。
- 导入链路：删掉 `deepseek-v4-pro` 后重新拉取 → 该模型变为可勾选 → 导入 1 个 → 表单出现新行且四档强度全选（截图 `58-imported-models.png`）→ 保存成功，`GET /api/agent/models` 返回两个模型；随后把显示名/备注改回原值，开发配置恢复原状。
- 安全核对：接口以 409 `MODEL_LIST_AUTH_FAILED` / 502 `MODEL_LIST_UNAVAILABLE` / 502 `MODEL_LIST_PROTOCOL_ERROR` / 429 `MODEL_LIST_RATE_LIMITED` 区分失败，字段错误落在 `apiKey` 或 `baseUrl`；两次真实拉取后按 `authorization|bearer|sk-` 检索 API 日志为 0 命中（密钥不出现在日志里）。
- 自动化：后端 5 个隔离回归（地址/密钥/目标校验、复用已保存密钥、权限拆分、结构解析与坏结构、密文损坏）；前端 3 个纯函数回归（跳过已有、强度归一化、容量上限）。出站成功路径没有自动化用例，原因见配置文档。
- 截图：`/tmp/merine-acceptance/63-models-section-wide.png`（入口）、`57-discover-modal.png`（真实拉取结果）、`58-imported-models.png`（导入后）。

final result: passed

## 推理强度随模型清单同步 + 上游 400 文案（2026-10-10）

- 背景：上一轮排查发现千问的 `reasoning_effort` 取值与 DeepSeek 不同（按系列 `low/medium/xhigh` 或 `low/high/max`），而界面只能发 DeepSeek 四档，`qwen3.7-flash + 最高` 被上游 400 拒绝。
- 后端：新增 `ReasoningEffortCatalog`（按供应商 + 模型标识收录官方取值与默认档位，文档未写的一律留空）；枚举扩到 `NONE/LOW/MEDIUM/HIGH/XHIGH/MAX`；`GET /models` 解析时给每个模型补上 `reasoningEfforts`；使用侧 `GET /api/agent/models` 增加 `defaultReasoningEffort`；上游 400/422/404 改为 `MODEL_REQUEST_REJECTED`。
- 前端：档位标签补「中」「极高」；「获取模型列表」弹窗去掉手工选一组强度的输入，改为逐模型显示将写入的官方档位（目录外显示「不设置」）；输入区默认档位改用服务端给 `defaultReasoningEffort`。
- 真实拉取核对（千问）：`qwen3.8-flash/max`（含 0902）→ 低/中/极高；`glm-5.3`、`kimi-k3` → 低/高/最高；`kimi/kimi-k3` → 最高；`deepseek-v4-pro` → 高/最高，`deepseek-v4-pro-0813`、`deepseek-v4-flash-0731`、`deepseek-v4.1-flash` → 低/高/最高；`qwen3.7-flash`、`qwen3.6-27b`、实时/翻译/向量类 → 不设置（文档未列出）。
- 配置同步：千问 6 个已配模型按目录改成 `['LOW','MEDIUM','XHIGH']`（qwen3.8 两项）、`['LOW','HIGH','MAX']`（glm-5.3、kimi-k3）、留空（qwen3.7-flash、qwen3.6-27b）；同步后逐个真实调用：qwen3.8-flash（不选=极高 5.8s、中 0.8s）、qwen3.8-max 低、glm-5.3 最高、kimi-k3 低、qwen3.7-flash 与 qwen3.6-27b 不发档位，全部 200 且返回「收到」；DeepSeek 关闭思考与最高各一次同样 200。
- 400 文案验证：临时把 qwen3.7-flash 设为 `MAX` 后调用，流内事件变为 `MODEL_REQUEST_REJECTED · 模型服务拒绝了这次请求，请检查模型标识与所选推理强度是否受支持`（旧文案是「无法处理的响应（HTTP 400）」），随后恢复为空档位（恢复时第一次 PUT 撞上乐观锁 409，重取版本后成功——顺带确认并发保护有效）。
- 截图：`/tmp/merine-acceptance/64-discover-with-efforts.png`（弹窗内逐模型显示官方档位）。
- 注意：浏览器仍需重新登录（后端重启会清空会话）。

final result: passed

## 抽屉档位按模型收窄（2026-10-10）

- 现象（用户反馈）：导入后每个模型都列全部六档，glm-5.3 出现「关闭思考/中/极高」等它并不支持的档位，qwen3.6-27b 也显示整排可勾选。
- 做法：新增 `POST /api/agent/providers/reasoning-efforts`（`vendor + modelIds` 批量查目录，读权限）；抽屉对每行按当前模型标识去抖查询，只渲染该模型的官方档位；目录没收录的模型不渲染档位并标注「官方未收录该模型的档位，将不发送强度参数」；尚未整理目录的供应商（`covered=false`）保留全部档位。保存时按目录收口，避免历史数据里留着不支持的档位。
- 实测（受控无头 Chrome，1440×900，千问连接）：glm-5.3 → 低/高/最高；qwen3.8-flash、qwen3.8-max → 低/中/极高；kimi-k3 → 低/高/最高；qwen3.7-flash、qwen3.6-27b → 无档位 + 说明文字（截图 `71-effort-per-model-crop.png`）。
- 期间两次数据事故与修复：排查中千问连接的模型先被清空一次（页面缓存还是旧列表，抽屉渲染不出模型行，此时保存就会写成空集合），补日志后又出现一次「只掉了一个模型」（qwen3.7-flash）——日志显示两次 `models=5` 的更新，时间落在后端重启窗口、间隔 15 秒，是客户端失败重试的特征，但触发保存的那一次点击无法从服务端日志区分（只记录到载荷）。两次都按目录恢复并逐个真实调用确认可用（qwen3.7-flash 恢复后实测 200）。
- 已补的防护：后端连接的创建/更新/删除各记一行 INFO（标识、供应商、模型数量，不含密钥）；前端在「保存会让既有模型全部消失」时先弹确认，模板切换的确认文案写明会清空模型列表。这些是排障线索与轻量护栏，统一操作审计仍在缺口清单里。
- 自动化：后端目录查询用例（按模型返回档位与默认值、未收录留空、`covered=false`、只读可查/缺权限 403）。

final result: passed

## 助手会话落库与执行记录（A1 + A2，2026-10-10）

- 目标：刷新不丢会话；每次执行留下可追溯记录（用量、耗时、失败原因）。会话只对归属用户可见。
- 后端：迁移 V46（`ai_conversation`、`ai_conversation_message`）与 V47（`ai_chat_run`）；`ChatHistoryService` 负责归属校验（非本人一律 404）与「提问 + 回答 + 执行记录」同事务写入；新建执行时确定会话（传 `conversationId` 校验归属，未传则按首条提问建会话），流收尾时落库，失败只记日志。接口：`/api/agent/conversations`（列表/消息/重命名/删除）与 `/api/agent/chat/runs`。
- 前端：协议新增 `STREAM_START.conversationId`；侧栏改读服务端会话（本地草稿 + 服务端列表），支持打开、重命名、删除；一轮结束刷新列表；加载历史时有明确状态。
- 验证：后端 215 tests（会话历史 6 项：结构约束、归属隔离、乐观锁、一次执行三处落库、重复落库被唯一键挡住、删除守卫；假上游新增用量与 `stream_options` 断言）全绿；前端 111；契约 7。
- 真实调用：DeepSeek `deepseek-flash` → 记录 `tokens=34/136`、1308ms；千问 `qwen3.8-flash` → `65/60`、1362ms；两条都在流式回答正常返回的同时写入执行记录。
- 浏览器（受控无头 Chrome，1440×900）：侧栏出现服务端会话；打开已存在会话恢复 4 条历史并追问成功（模型沿用历史作答）；刷新后历史仍在；重命名生效（服务端 `title`/`version` 变化）；删除会话后消息与执行记录一并清理（库里 2 会话 / 6 消息 / 3 执行记录）。截图 `73-history-restored.png`、`75-history-restored-crop.png`。
- 未覆盖：跨账号的浏览器验收（后端隔离用例已覆盖）、会话列表分页在数据量大时的表现、保留期与清理任务。

final result: passed

## 助手会话与执行记录：深度自查修复（2026-10-10）

- 范围：对照 `docs/tasks/agent-history-design.md` 复核本轮实现，检查计划一致性、并发与生命周期陷阱、UI/UX。
- 后端修复：被拒绝的作答不再留下空会话（会话确定放在作答受理之后）；`recordTurn` 与删除会话都先锁会话行并在锁内复核归属（回答不会再写进已删除的会话，`seq` 也在锁内取）；删除守卫覆盖「等待作答」；标题与错误文案截断避开代理对；补齐 5 组助手会话的悬空引用检查（顺带清掉测试库 34 条旧残留 + 98 条孤立消息）；`ChatService`/`ChatController`/`ChatProperties`/`ProviderChatClient` 的过时注释改回真实行为。
- 前端修复：切换服务端会话改走 `selectSession`（会请求停止正在生成的旧执行，实测服务端记录 `ABORTED`）；打开会话先建占位（加载期间显示会话本身与「正在加载会话历史…」，并禁用发送，避免漏上下文）；`新聊天` 复用空草稿（连点三次仍然只有一条草稿）；非生成中的会话在打开时后台核对服务端，能在别处新增轮次后拉回；会话列表加载失败给出提示；失败轮次的提示文案不再指向尚不存在的页面。
- 验证：`./scripts/dev.sh check` exit 0；后端用例含新增的助手会话引用检查；浏览器复验「切会话停旧流」「连点新聊天只留一条草稿」「打开会话恢复历史」。

final result: passed

## 会话模型回填与用量视图（2026-10-10）

- 回填：打开历史会话时按其最近一次使用的连接、模型与档位回填输入区；该模型已不可用（连接停用、模型删除）时忽略回填，用默认选择。实现上先按会话记录算出目标，再交给输入区显示；切换会话会清掉本次访问的临时选择，避免把上一个会话的模型带过去。
- 浏览器实测：新建会话显示默认「DeepSeek Flash / 高」；用千问 `qwen3.8-flash`（默认档位极高）发一条后再点「新聊天」，输入区回到默认；重新打开那条会话，输入区恢复为「qwen3.8-flash / 极高」。
- 用量视图：图标栏在「资料库」下方新增「用量」（`/agent/usage`）。页面显示当前账号自己的执行记录：概要（总数、本页 token 输入/输出、未完成条数、无用量条数）+ 表格（时间、连接与模型、状态、耗时、token、字符、失败原因）+ 状态筛选 + 分页。接口 `GET /api/agent/chat/runs` 支持 `state` 参数（非法值 400）。
- 实测：8 条记录（含 1 条「已停止」、1 条无用量显示「—」）；筛「失败」0 条、筛「已停止」1 条，切换筛选回到第一页。截图 `/tmp/merine-acceptance/77-usage-view.png`、`78-usage-view-crop.png`。
- 过程中修掉的两个测试问题：结构回归的助手会话引用检查抓到测试库残留（对话流用例为管理员账号落库后没清理，13 条悬空会话，已在对话测试基座补清理）；清理与仍在收尾的执行线程抢锁会死锁，改成退让重试而不是抢锁。
- 未覆盖：跨用户统计与聚合、从记录跳到会话、导出。

final result: passed

## 用量看板 v2：图表与筛选（2026-10-10）

- 结构：筛选行（近 7/14/30 天、全部模型/单个模型）+ 5 张指标卡（请求数、平均延迟、成功率、token 输入/输出、字符输入→输出）+ 趋势卡（口径可切 Token/次数/字符/时长）+ 状态分布 + 按模型分布 + 执行明细。图表自绘 SVG，无新增依赖。
- 后端：新增 `runs/stats` 聚合（按本地日期分桶，`offsetMinutes` 参与 `CONVERT_TZ`）；`runs` 明细补 `providerId/modelId` 筛选；参数越界统一 400。
- 实测（受控无头 Chrome，1440×900）：KPI 与库内数据一致（8 条 / 平均 3.1s / 成功率 87.5% / token 525→3.6k）；趋势图悬浮显示当天次数、token、字符与耗时；口径切到「次数」后大数字与曲线同步；状态分布 成功 7 条 88%、已停止 1 条 13%；模型分布 DeepSeek 3.9k、千问 224；把「模型」筛成 `qwen3.8-flash` 后 KPI 变 2 条、明细 2 行、分布只剩一格。
- 自查修掉的两个问题：明细表未接模型筛选（接口只认 `state`，表现为 KPI 收窄而明细仍全量）；`@Min/@Max` 越界返回 500（补 400 处理与回归用例）。
- 语义修正：模型分布的「失败」只计 `state='FAILED'`，不再把「已停止」算失败。
- 截图：`79-usage-dashboard.png`（初版）、`81-usage-dashboard-light-crop.png`（浅色指标卡）、`83-usage-dashboard-dark-crop.png`（深色）、`84-usage-final.png`（筛选与口径切换后）。
- 未覆盖：按小时粒度、分布直方图、导出、记录跳会话。

final result: passed

## 用量看板布局调整（2026-10-10）

- 按用户标注把「按模型分布」移到「状态分布」下面：左列趋势图跨两行，右列自上而下为「状态分布 → 按模型分布」，执行明细整行；趋势卡下方补了区间小结（日均口径值、活跃天数、峰值日），把原本的空白变成有用信息。
- 实测（受控无头 Chrome，1440×900）：卡片位置为 趋势(左, 81px) / 状态分布(右, 965px, 209px) / 按模型分布(右, 965px, 441px) / 明细(整行, 681px)；区间小结显示「日均 Token 292 · 活跃天数 1 / 14 · 峰值日 10-10 · 4.1k」。
- 前端新增 `trendSummary` 纯函数与单测（日均按区间天数、峰值只取有记录的天）；前端 118 tests 全绿。
- 截图：`/tmp/merine-acceptance/85-usage-layout.png`。

final result: passed

## 用量页 UI / UX 重设计（2026-10-10）

- 范围：用户授权优化并重构现有 `/agent/usage`；原截图是问题参照，不是像素复刻目标。后端、契约、依赖和业务数据均未调整。
- source visual truth：用户提供的页面截图；同一路由重设计前截图 `/tmp/merine-acceptance/85-usage-layout.png`，1440×900 CSS px、2×密度、2880×1800 像素。
- implementation screenshot：`/Users/langya/.codex/visualizations/2026/10/10/01a12425-f9de-7662-853b-b1cb5f4a451a/usage-light-2x.png`，同为 1440×900 CSS px、2×密度、2880×1800 像素。两图在同一比较输入中打开，无浏览器工具栏；状态均为浅色、近 14 天、全部模型、Token、全部状态，8 条已有执行。
- 全景比较：将五张等权卡片改为统一指标带，Token 作为主指标；趋势和状态/模型分布紧凑并排，明细提前进入首屏。稀疏折线改为按天输入/输出堆叠柱，避免一个活跃日撑满巨大曲线。以上为用户授权的设计变化。
- 局部检查：同一等密度对照中的标题、筛选、状态百分比、模型占比和表格前两行均可读；逐项查看输入/输出、时间两行与状态文字，不需要额外裁剪。
- 必查表面：字体沿用项目字体与 Ant Design，标题/指标/辅助说明三级层次清楚；间距统一、图表固定 164px 高、桌面无横向溢出；颜色使用主题变量，成功绿/失败红/停止中性，成功率不再使用警告色；无图片资产新增或替换，保留既有图标，SVG 只用于数据图表；文案明确当前账号、未返回用量及全部时间明细。
- 迭代修复：早期检查发现趋势区仍偏高，已缩短图高和卡片间距；断网时 Query 处于暂停但缺少提示，已补自动恢复说明；状态百分比统一一位小数，修正 88% + 13% 的误读；最终等密度截图确认上述改动，无待修 P0/P1/P2。
- 真实浏览器：1440×900 和 1366×768、亮/暗主题；千问模型筛选后概览与明细均为 2 次，失败空结果及清除筛选可用；30 天日期轴、字符口径说明、时长单位、图表悬浮及键盘聚焦均核对。概览周期不影响全时间明细，页面明确标注。
- 恢复路径：仅在受控浏览器模拟请求失败和离线，确认保留已加载结果、显示重试/暂停说明，恢复连接后重新加载。未发起外部 AI 调用。最终浏览器 console 无 error/warn。
- 自动检查：前端构建通过、120 项前端测试通过；最终微调后类型检查、9 项用量测试、Prettier 和差异检查通过。
- 实际缺口：已有记录不足一页，未用大量数据验证翻页；未新增跨账号浏览器验收或完整无障碍审计。模型分布仍受既有 API 最多返回 8 个模型的限制，页面说明按执行次数选取、占比按全部模型计算。

final result: passed

## 供应商官方标识替换（2026-10-10）

- 背景：内置 AntD 图形作供应商标识不够准确，改为各家官方图标；资源下载到 `apps/web/public/vendor-icons/`（含来源清单 `ICONS.md`），不新增依赖。
- 资源：DeepSeek 与千问用用户提供的官方 SVG；智谱 90×90 PNG、Kimi 48/32/16 ICO、豆包 120×120 PNG、百度千帆「大模型服务」SVG、腾讯云 32 ICO、MiniMax 192×192 PNG；CUSTOM 用自绘中性占位 SVG。
- 前端：`ProviderIcon/ProviderGlyph` 改为按 vendor 取官方文件（`<img>`，白底徽牌衬底，暗色主题下深色标识仍可见）；配置卡、快速添加模板卡、抽屉抬头与助手模型选择器的触发器/选项都换成官方标识；抽屉移除「图标」自选下拉，`icon` 字段改为隐藏提交（契约与库表不变）。
- 验证：10 个 `<img>` 全部加载成功（naturalWidth>0，含 8 家模板与 2 个已配置连接）；配置卡截图 `94-quick-add-crop.png`（DeepSeek 蓝鲸、千问蓝花标识清晰）；暗色主题 `97-vendor-icons-dark.png`；抽屉确认无图标选择器且抬头为官方标识；输入区触发器显示 deepseek.svg。
- 回归：隐藏 `icon` 字段仍随表单提交——用界面保存一次（备注改为校验值再改回），服务端 `icon=SPARK`、版本递增、备注还原成功。
- 前端 124 tests、后端 215 tests、契约 7/7、`./scripts/dev.sh check` exit 0。

final result: passed

## SSE 客户端的错误响应修复（2026-10-10）

- 问题（审计遗留项）：只声明 `Accept: text/event-stream` 的客户端遇到业务错误时，错误响应要经过内容协商，协商失败让整个响应变成 500；我们前端声明的是 `text/event-stream, application/json`，所以不受影响，但脚本与第三方集成会踩到。
- 处理：统一异常处理里的所有错误响应改经同一个出口——SSE-only 声明时直接写出 JSON 错误体（状态码与 `Content-Type: application/json` 不变），其余请求照旧走 ResponseEntity；同时把「查询参数约束校验」从两个控制器的局部处理收敛到全局（`HandlerMethodValidationException` → 400 `VALIDATION_ERROR`，字段名进 `fieldErrors`）。
- 回归：新增用例「SSE-only 客户端在模型停用时拿到 409 `CHAT_MODEL_UNAVAILABLE` + application/json，且没有出站请求」；参数越界（状态取值、页码、时区偏移）仍分别返回 `INVALID_PARAMETER` 与 `VALIDATION_ERROR`。
- 验证：后端 216 tests、前端 124、契约 7/7，`./scripts/dev.sh check` exit 0。

final result: passed

## Enter 发送失效修复（2026-10-10）

- 现象（用户反馈）：在输入区按 Enter 不发送，点底栏发送按钮正常。
- 根因：发送按钮画在 Sender 的 `footer` 里，因此把组件库的 `suffix`（默认动作按钮）关掉了；而 `@ant-design/x` 的 Sender 把「可提交」状态 `submitDisabled` 交给默认动作按钮（`ActionButton`）更新，关掉之后该状态一直停在初始的 `true`，内建的 Enter 提交处理被静默跳过（代码位置：`es/sender/Sender.js` 的 `triggerSend` 与 `es/sender/components/TextArea.js` 的 `onInternalKeyDown`）。
- 处理：由输入区自己接管 Enter——`onKeyDown` 里判断「Enter 且非 Shift 且非输入法组合中」，`preventDefault()` 后直接调用 `submit()`，并返回 `false` 告诉 Sender 这次按键已处理（避免它再走一遍内建逻辑）；README 提到的「Enter 发送 · Shift + Enter 换行」提示与实际行为一致。
- 实测（受控无头 Chrome，真实键盘事件）：空闲时按 Enter → 用户消息上屏、草稿清空、开始生成；Shift + Enter → 草稿变为 `第一行\n`、不发送；生成中按 Enter → 消息数不变、草稿保留（不会重发）；点击发送按钮行为不变。
- 代码注释写明了这条组件库约束，避免以后有人「顺手把 footer 换回 suffix」再踩一次。
- 前端 124 tests、后端 216 tests、契约 7/7，`./scripts/dev.sh check` exit 0。

final result: passed

## 删除 icon 字段与模型连接权限门禁（2026-10-10）

- 迁移 V48 删除 `ai_model_provider.icon`：标识改由 `vendor` 决定并渲染官方图标后，配置里的自选图标码不再有意义。后端同步移除 `Icon` 枚举与 `ProviderView`/`ModelOption`/持久化行的 `icon` 字段，契约重新生成（生成文件里已无 `icon`），前端模板与表单去掉该字段。
- 权限门禁（与后台管理页一致）：账户菜单的「设置」只在持有 `agent:provider:read` 时出现；直接访问 `/agent/settings` 且无该权限时显示 403 结果页（不再渲染空列表）；新增/编辑/删除按钮继续由 `create`/`update`/`delete` 控制，后端逐个判定。
- 全员可用：连接配置好后，任何登录用户都能在对话里使用（使用侧模型列表登录即可读），与单位、角色无关；文档里写清了「使用不设门槛、管理按权限码」的边界。
- 实测（受控无头 Chrome）：把业务用户角色临时授予 `agent:chat:use` 后登录业务账号——账户菜单只有「退出登录」（无「设置」）；访问 `/agent/settings` 显示「没有管理模型连接的权限」结果页；回到对话页输入区可用、模型触发器显示「DeepSeek Flash」与 deepseek.svg（说明连接对普通用户可用）。验证后已撤销临时授权。
- 后端 216 tests、前端 124 tests、契约 7/7、`./scripts/dev.sh check` exit 0（迁移已应用到开发库与测试库）。

## 「未挂菜单的权限码」在角色管理里可授权（2026-10-10）

- 问题（用户反馈）：`agent:chat:use` 与 `agent:provider:*` 在菜单管理、角色管理里都找不到，只能改库授权。
- 根因：V43 下线后台菜单后这些权限码不再挂在任何菜单节点上，而 `GET /api/system/roles/permission-tree` 直接返回菜单树——没有菜单节点的权限码在勾选树里就不存在。后端其实一直接受这些码（`requireKnownPermissions` 校验的是整张 `sys_permission`），缺的是界面入口。
- 处理：权限树接口改为「菜单树 + 一个合成分组」——`其他权限（未挂在菜单上）`，列出所有没有菜单节点的权限码（名称 + 编码）。分组只是父节点、没有权限码，不会产生新的可授权项；前端树是通用的（有 `permissionCode` 即成勾选项），无需改结构。菜单侧新增只读查询 `findBoundPermissionCodes()`，角色服务据此求差集。
- 浏览器实测（受控无头 Chrome）：角色管理 → 编辑角色 → 权限树展开该分组，看到 7 个勾选项：`agent:chat:use`、`agent:provider:create/delete/read/update`，以及顺带暴露的 `collaboration:flow:read`、`collaboration:task:read`（这两个同样是只有权限码、没有菜单节点的码）。截图 `/tmp/merine-acceptance/98-role-permission-group.png`。
- 回归：新增用例断言权限树包含该分组及其 5 个 agent 权限码，且分组自身没有权限码；角色管理 15 个用例全绿。
- 文档：`model-provider-management.md` 写明「使用不设门槛、管理按权限码、权限码在角色管理的其他权限分组里授予」；`agent-chat-design-plan.md` 同步 `agent:chat:use` 的授予位置。

final result: passed

## 清理无引用的遗留权限码（2026-10-10）

- 现象（用户提问）：`collaboration:flow:read`、`collaboration:task:read` 为什么出现在「其他权限（未挂在菜单上）」分组里。
- 查明：这两个码是旧骨架页的读权限，正式页面在 V22 已改绑 `task:handling:read`（任务处置）与 `intelligence:topic:read`（信息流转）；此后它们在菜单、Java 判定（`grep` 无命中）与前端都没有任何引用，只剩 1 个角色还挂着其中一个授权——属于死码，出现在授权界面只会误导管理员。
- 处理：迁移 V49 先删授予关系再删权限码；新分组据此只剩有意不挂菜单的 `agent:chat:use` 与 `agent:provider:*`。
- 验证：迁移后「未挂菜单的权限码」查询只剩上述 5 个；角色管理权限树的分组不再出现这两个码；回归用例新增断言（树里不得出现这两个码），角色管理 15 个用例全绿。
- 顺带说明：这个「其他权限」分组不止解决授权入口问题，也成了无菜单权限码的清点面——以后再出死码会立刻被看到。

final result: passed

## 非菜单权限按模块分组（架构修正，2026-10-10）

- 问题（用户指出）：「其他权限（未挂在菜单上）」是个跨模块的垃圾桶分组，把无关模块的权限码混在一起，既看不出归属，也会让死码藏在大桶里；后续再有非菜单权限会继续往里堆。
- 设计（先定规则再实现）：
  1. 权限码按**模块命名空间**组织，`<模块>:<资源>:<动作>`，第一段即模块；
  2. 模块清单是**代码侧注册表** `system/permission/PermissionModules`（显示名 + 勾选树顺序），与 `RegisteredIcons`/`RegisteredRoutes` 同类——结构性清单放代码，业务字典才放库；
  3. 角色管理的权限勾选树 = 菜单树 + **每个有非菜单权限码的模块一个分组**（节点 id `module:<模块>`），不做跨模块合并；未登记模块显示为「未登记模块（前缀）」，漏登记一眼可见；
  4. 护栏：回归断言「库里出现的每个权限前缀都已登记」与「树里不存在跨模块的『其他权限』分组」，并保留「不得出现已清理死码」的断言。
- 规则落点：`docs/rules/backend.md` §2「认证与授权」新增该约定（命名空间、两种呈现方式、禁止合并、新增模块必须登记）。
- 实测：权限树顶层为 业务协同 / 涉海要素 / 系统管理 / 开发工具 / **智能体**；`module:agent` 组内是 `agent:chat:use`、`agent:provider:create/delete/read/update`，组内按权限码稳定排序；界面里已无「其他权限」字样。截图 `/tmp/merine-acceptance/99-module-group.png`。
- 角色管理回归 16 个用例全绿（新增「按模块分组」与「模块前缀都登记」两个用例）。

## 变更代码扫描（2026-10-10）

- 扫到并修复：用量页时间范围与明细口径不一致（见 `agent-usage-view-plan.md` §9）；`usage.ts` 里 v1 遗留的两个死导出。
- 复核确认无问题的点：① 统一异常处理的 SSE 出口（只声明 `text/event-stream` 时直接写 JSON，状态码与格式不变；其余请求走 ResponseEntity）；② 参数校验收敛到全局后，两个控制器不再各自处理（provider/role 用例仍绿）；③ 权限模块分组：`TreeMap` 排序稳定、分组无权限码、未登记前缀显式暴露且由回归拦住；④ icon 列删除后后端已无任何 SQL/字段引用，供应商保存往返（不带 icon）返回 200，前端契约同步无残留；⑤ 供应商官方图标在构建产物（`dist/vendor-icons`）中存在，暗色主题下白底徽牌可辨；⑥ Enter 提交（含 IME 与生成中不重发）、聊天历史落库加锁顺序、用量采集（`usage` 缺失记未知不估算）此前已单独验收。
- 残留风险（记录在案，未改）：上游不支持 `stream_options` 时会 400，目前只有 DeepSeek 与千问在册（都支持），如需接其它供应商要按连接关闭 `merine.agent.chat.request-usage`；`public/vendor-icons/ICONS.md` 会随静态站点公开（只有来源清单，无敏感信息）；V49 直接删了死码的授予关系但未递增角色授权版本（死码不参与任何判定，旧会话持有它也无效）。
