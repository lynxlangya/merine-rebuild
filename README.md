# Merine Rebuild

React + Java 的独立 monorepo。M1.1 打通了 React 页面 → API → 本地 MySQL 的最小链路；当前在此基础上完成了真实登录闭环、前端公共架构与基于设计稿的基础页面。

> 当前进度：登录闭环与系统管理四个模块（**用户管理**、**角色管理**、**菜单管理**、**单位管理**）的前后端都已接真实后端与本地 MySQL，并已实现**菜单/页面/按钮级功能权限**：菜单树由数据库维护（页面绑定前端已注册的路由），登录后按权限下发导航，接口按按钮级权限码判定。应用保留首页与五个页面（含工程诊断）；**数据范围（能看哪些业务数据）仍未实现**，详见[本轮边界](#本轮边界)。设计稿只作样式参考；账号与业务样例为合成数据，单位树来自已授权组织参考数据，不含联系人信息。

## 启动

需要 Docker Desktop（或带 Compose 的 Docker）正在运行，首次生成本地配置需要 `openssl`。整套开发服务在容器里运行，宿主机无需安装 JDK、Node 或 MySQL。

```bash
./scripts/dev.sh up
```

首次启动会下载镜像与依赖、生成独立随机密码到 `.env`、建立本项目的数据卷、执行 Flyway 迁移，然后启动 API 与前端。后续启动复用依赖缓存和数据，不重置密码或数据卷。

启动后还没有账号，需要先初始化一个本地演示账号（密码只从交互输入读取，不回显、不进 shell 历史）：

```bash
./scripts/dev.sh seed
# 默认挂到 ORG_001 浙江省公安厅海防总队 / 系统管理员；账号姓名仍使用本地合成值
SEED_ROLE_NAME='系统管理员' SEED_DISPLAY_NAME='陈知远' ./scripts/dev.sh seed
```

密码下限 6 位，上限为 72 个 UTF-8 字节（中文通常占 3 字节）；不足 12 位会打出警告——这类密码只适用于本地合成数据。

| 入口                 | 地址                                            |
| -------------------- | ----------------------------------------------- |
| 登录页               | http://127.0.0.1:5173/login                     |
| 应用首页（登录后）   | http://127.0.0.1:5173/                          |
| 用户管理（登录后）   | http://127.0.0.1:5173/system/users              |
| 角色管理（登录后）   | http://127.0.0.1:5173/system/roles              |
| 菜单管理（登录后）   | http://127.0.0.1:5173/system/menus              |
| 单位管理（登录后）   | http://127.0.0.1:5173/system/units              |
| 工程诊断（联调探针） | http://127.0.0.1:5173/dev/diagnostics           |
| 业务接口（需登录）   | http://127.0.0.1:9002/api/bootstrap             |
| 后端就绪探针         | http://127.0.0.1:9002/actuator/health/readiness |
| Swagger UI（需登录） | http://127.0.0.1:9002/api/docs                  |
| OpenAPI（需登录）    | http://127.0.0.1:9002/api/openapi               |
| MySQL 宿主端口       | `127.0.0.1:3307`                                |

除登录所需的 `/api/auth/csrf`、`POST /api/auth/session` 与两个健康探针外，所有接口都要求已登录，**包括接口文档**（`merine.security.public-api-docs` 默认 `false`）。未登录访问受保护接口返回 JSON 格式的 401，不会重定向到页面。本地想匿名看文档，把该属性显式改成 `true`。

应用支持亮色 / 暗黑切换，偏好存在本地，首屏不闪主题；1440×900 与 1366×768 为基准桌面尺寸，窄屏自动折叠导航。

端到端联调从「工程诊断」进入：输入一条记录、写入、刷新；能再次查到这条记录，才证明页面 → API → MySQL 已贯通。这条链路用的是真实后端与 MySQL，不是演示数据，且同样要求登录。

## 目录与技术

```text
apps/web/             React、TypeScript、Vite、Ant Design、Router、TanStack Query
  src/app/            路由、外壳、Provider、主题 token 与 Ant Design 映射
  src/features/       按用例拆分：页面、api/queries/model、内部 components，跨 feature 走 public.ts
  src/app/routeRegistry.tsx  路由 key → 页面组件与图标的前端注册表
  src/shared/         统一请求、主题状态与经复用验证的展示组件
apps/api/             Java 21、Spring Boot、MyBatis、Validation、Actuator、springdoc
  src/main/java/com/merine/rebuild/   auth、system/{user,role,unit,permission,security} 等业务能力及 common
  src/main/resources/mapper/        按业务/用例归属组织的 MyBatis XML
  src/main/resources/db/migration/   Flyway SQL 迁移
packages/api-contract/ 后端 OpenAPI 快照与生成的 TypeScript 类型
infra/                Compose、开发 Dockerfile、数据库账号初始化
scripts/              启停、检查、类型生成和实际联调验证
```

前端依赖方向为 `app → features → shared`。色彩、间距、圆角等语义 token 只在 `apps/web/src/app/theme/tokens.css` 维护一处；Ant Design 通过 `ConfigProvider` 映射到同一组 token，共享主题状态位于 `shared/theme`。

后端按业务组织，复杂用例再分 `dto` / `persistence`；小模块保持平铺。文件位置、命名、公开能力及分层时机统一见[目录与模块规则](docs/rules/structure.md)。一条典型链路是 `features/units/UnitListPage → queries/api → system.unit.UnitController → UnitService → UnitMapper → mapper/system/unit/UnitMapper.xml`；用户表单通过 `features/units/public.ts` 复用单位选择能力、通过 `features/roles/public.ts` 复用角色选项。

前端依赖由根目录 pnpm workspace 管理，后端由 `apps/api/pom.xml` 与 Maven Wrapper 管理。仓库已提交到 `main` 并推送到 `origin`（`git@github.com:lynxlangya/merine-rebuild.git`）。

当前锁定 React 19.3.0、Ant Design 6.6.4、Vite 8.3.0、TypeScript 5.9.3、Node 24.18.0、pnpm 11.10.0、Java 21、Spring Boot 4.1.1、MyBatis Starter 4.1.0 和 springdoc 3.1.1。前端传递依赖通过 `pnpm-lock.yaml` 固定；镜像通过版本与摘要固定。

## 日常命令

以下命令在仓库根目录执行，停止服务会保留数据卷。

```bash
./scripts/dev.sh status
./scripts/dev.sh logs api
./scripts/dev.sh down
./scripts/dev.sh up
```

已有 Node / pnpm 时，也可以使用 `pnpm dev`、`pnpm stack:status`、`pnpm stack:logs` 和 `pnpm stack:down`。本机工具版本记录在 `mise.toml`，容器不依赖本机工具版本。

直接使用 Compose 的等价入口如下。常规启动优先使用脚本，它会先确认数据库就绪并运行迁移。

```bash
docker compose --project-name merine-rebuild-dev --env-file .env -f infra/compose.yaml ps
docker compose --project-name merine-rebuild-dev --env-file .env -f infra/compose.yaml logs --tail 100 api
```

前端修改会由 Vite 热更新。后端本轮采用明确的编译重启：修改 Java 后执行以下命令，启动时会重新编译当前源码。后续再按实际需要增加自动重载。

```bash
docker compose --env-file .env -f infra/compose.yaml restart api
```

前端依赖变更后更新锁文件并重启 web；新增 SQL 迁移后执行 `./scripts/dev.sh migrate`，已执行的迁移文件不再修改。

## 检查与接口契约

服务启动后运行：

```bash
./scripts/dev.sh check
./scripts/dev.sh build
./scripts/dev.sh test-db
# 接口文档与冒烟都要先登录；账号密码只经环境变量传入
docker compose --env-file .env -f infra/compose.yaml exec -T \
  -e API_LOGIN_NAME=admin -e API_PASSWORD='<seed 时设置的密码>' \
  web pnpm contract:generate
docker compose --env-file .env -f infra/compose.yaml exec -T \
  -e API_LOGIN_NAME=admin -e API_PASSWORD='<seed 时设置的密码>' \
  web pnpm smoke
```

- `check`：建好测试库并跑 TypeScript、Node 内置前端回归测试、格式检查与 Maven 验证（含认证、用户管理与单位管理回归测试）。
- `build`：前端生产构建与后端 jar 打包。目前验证开发环境，完整交付镜像另在后续 M1.5 实现。
- `test-db`：准备隔离的测试库 `merine_rebuild_test` 并应用同一套迁移；重复执行是幂等的，不动开发库。
- `contract:generate`：从当前源码启动的本地后端导出 OpenAPI，更新 `packages/api-contract/openapi.json` 与 `src/schema.d.ts`。页面消费生成类型，生成文件不手改。接口文档默认需要登录；当前文档访问不额外要求系统管理角色。
- `smoke`：经 Vite 的 `/api` 代理走一遍「CSRF → 登录（含错误密码与未登录）→ MySQL 读写 → 参数校验 → 退出后会话失效」；每次追加一条 `SMOKE-` 合成记录，不清理或重置已有数据。密码经环境变量传入，不写在脚本里。

`smoke` 在容器内默认访问同容器的 Vite `5173`。`contract:generate` 通过容器服务名访问 API。初次下载依赖较慢时可查看对应服务日志；状态未知时先查看 `status`，不删卷重试。

## 本地数据与账号

- Compose 项目名为 `merine-rebuild-dev`，端口只发布到本机回环地址。仅服务本地开发，当前只有登录与身份，**没有功能权限与数据范围模型**。
- 会话保存在服务端内存，Cookie 只带会话标识（HttpOnly、SameSite=Lax）；单实例重启后需要重新登录。勾选「保持登录」会把空闲超时延长到 7 天。
- 退出、账号停用、所属单位停用与授权版本变化都会让已有会话在下次请求时失效。
- 演示账号用 `./scripts/dev.sh seed` 初始化：密码只以 BCrypt 哈希落库，日志、迁移与仓库里都没有明文；重复执行不会重置已有账号。登录页在开发构建下会预填账号名（不预填密码），生产构建不预填任何内容。
- Vite 开发服务器默认只监听回环地址；容器内由 Compose 注入 `VITE_DEV_HOST=0.0.0.0`，否则宿主机端口映射无法转发。在宿主机直接运行 `pnpm --filter @merine/web dev` 不会暴露到局域网。
- 开发库固定为 `merine_rebuild`，与旧项目的容器、账号、端口及数据卷分开；测试库 `merine_rebuild_test` 在同一个 MySQL 实例内但库名与账号独立，破坏性测试只作用于它。
- `merine_migrate` 负责两个库的结构迁移；`merine_app` / `merine_app_test` 只有各自库的查询与增删改权限。应用不以 root 或迁移账号运行。
- `.env` 由脚本生成，权限为 `600`，不会提交或进入镜像。已有数据卷时不能仅修改 `.env` 来轮换账号密码；数据库内的账号需要配套变更。
- MySQL 数据保存在命名卷中。`down` 不删除卷；`down -v`、清库或恢复数据必须是明确的独立操作。
- 源码挂载便于开发，Linux 的 `node_modules`、Maven 缓存和编译输出放在独立卷中，不混用宿主机依赖。

## 本轮边界

应用有六个页面，全部接真实后端：

- **首页** `/`：当前身份与可用入口。身份来自服务端会话，不摆没有数据支撑的指标卡。
- **用户管理** `/system/users`：账号查询、新建、编辑、启用停用（单个与批量）。
- **角色管理** `/system/roles`：角色查询、新增、编辑（名称、说明、功能权限）、启用停用与删除；成员清单只读，可跳到用户管理按角色筛选。
- **菜单管理** `/system/menus`：维护目录、页面、页签与按钮；页面只能绑定前端已注册的路由 key，节点自带权限码，删除会连同子树与角色授权一起处理。
- **单位管理** `/system/units`：三级组织树、直属下级与用户、单位新增/编辑/删除；删除只允许无下级且无用户的空单位。
- **工程诊断** `/dev/diagnostics`：页面 → API → MySQL 的最小读写链路与探针记录。

**已经接真实的**：登录、退出、当前身份与 CSRF（Spring Security Session + HttpOnly Cookie，账号落 `sys_user`，密码 BCrypt 哈希）；用户、角色、单位与功能权限的查询与写入；单位三级组织树与 50 行已授权组织参考数据；路由守卫、身份恢复、会话失效处理。

**授权模型（已实现的部分）**：用户 → 角色 → 菜单节点上的权限码（页面 `:read`，按钮 `:create`/`:update`/`:toggle-status`/`:delete`/`:reset-password`/`:restore`，工程诊断另有读与写两个码）。登录时把**内置管理员角色**（`merine.security.system-admin-role-codes`，默认 `SYSTEM_ADMIN`）展开为全部权限码，其余角色按 `sys_role_permission` 取权限；接口按按钮级权限码判定，`/api/me/menus` 按同一份权限下发导航，前端按钮按同一份权限码显示。角色状态或权限集合变化会让持有者旧会话失效。系统始终保留至少一个可用管理账号：停用账号、调整用户角色、停用角色、改角色权限、删角色或删菜单节点都会做覆盖校验，为 0 时 409 拒绝。

**菜单与导航**：菜单树由迁移写入引导数据（系统管理 / 开发工具两个目录、五个页面与十六个按钮），之后在菜单管理里自由增删改；页面只能引用前端注册表（`apps/web/src/app/routeRegistry.tsx`）与后端清单（`RegisteredRoutes`）里已有的 route key，新增页面仍要前端发版。菜单停用只影响导航展示与可分配性，**隐藏菜单不等于禁止访问**——接口始终按权限码判定。删到没有入口时用页面上的「恢复默认菜单」补齐；连菜单管理入口都被删掉时用命令行兜底：`./scripts/dev.sh menus`（只补缺失项，不删除也不覆盖）。

**尚未实现**：数据范围模型（账号能看哪些业务数据）。当前权限码只决定「能用哪些功能」，不代表能看哪些业务数据；分级/跨单位的可见范围、可管理单位限制与操作留痕都还没有实现。

**尚未实现的其它部分**：图谱查询、任务调度、AI 平台接入、情报流转、Neo4j、完整交付镜像；用户列表导出。也没有连接旧数据库或外部业务服务。

`docs/sys-design` 下的设计稿**只是样式与交互参考**，不是本轮要实现的模块清单。

选型依据：[Spring Boot](https://docs.spring.io/spring-boot/system-requirements.html)、[MyBatis](https://mybatis.org/spring-boot-starter/mybatis-spring-boot-autoconfigure/)、[Vite](https://vite.dev/guide/)、[Ant Design](https://ant.design/docs/react/introduce/)、[Docker Compose](https://docs.docker.com/compose/)。依赖组合以本仓库的实际构建与联调结果为准。
