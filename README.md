# Merine Rebuild

React + Java 的独立 monorepo。当前完成 M1.1 最小工程：React 页面通过 API 读写本地 MySQL，并生成共享接口类型。所有演示记录均为合成数据。

## 启动

需要 Docker Desktop（或带 Compose 的 Docker）正在运行，首次生成本地配置需要 `openssl`。整套开发服务在容器里运行，宿主机无需安装 JDK、Node 或 MySQL。

```bash
./scripts/dev.sh up
```

首次启动会下载镜像与依赖、生成独立随机密码到 `.env`、建立本项目的数据卷、执行 Flyway 迁移，然后启动 API 与前端。后续启动复用依赖缓存和数据，不重置密码或数据卷。

| 入口               | 地址                                  |
| ------------------ | ------------------------------------- |
| 工程工作台         | http://127.0.0.1:5173                 |
| API 状态与合成记录 | http://127.0.0.1:9002/api/bootstrap   |
| Swagger UI         | http://127.0.0.1:9002/api/docs        |
| OpenAPI            | http://127.0.0.1:9002/api/openapi     |
| 后端健康检查       | http://127.0.0.1:9002/actuator/health |
| MySQL 宿主端口     | `127.0.0.1:3307`                      |

页面支持亮色 / 暗黑切换。输入一条联调记录、写入、刷新；能再次查到这条记录，才证明页面 → API → MySQL 已贯通。

## 目录与技术

```text
apps/web/             React、TypeScript、Vite、Ant Design、Router、TanStack Query
apps/api/             Java 21、Spring Boot、MyBatis、Validation、Actuator、springdoc
  src/main/resources/db/migration/   Flyway SQL 迁移
packages/api-contract/ 后端 OpenAPI 快照与生成的 TypeScript 类型
infra/                Compose、开发 Dockerfile、数据库账号初始化
scripts/              启停、检查、类型生成和实际联调验证
```

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
docker compose --env-file .env -f infra/compose.yaml exec -T web pnpm contract:generate
docker compose --env-file .env -f infra/compose.yaml exec -T web pnpm smoke
```

- `check`：TypeScript、格式检查与 Maven 验证。
- `build`：前端生产构建与后端 jar 打包。目前验证开发环境，完整交付镜像另在后续 M1.5 实现。
- `contract:generate`：从本地后端导出 OpenAPI，更新 `packages/api-contract/openapi.json` 与 `src/schema.d.ts`。页面消费生成类型，生成文件不手改。
- `smoke`：通过 Vite 的 `/api` 代理验证 MySQL 读写、参数校验、请求编号和 404；每次追加一条 `SMOKE-` 合成记录，不清理或重置已有数据。

`smoke` 在容器内默认访问同容器的 Vite `5173`。`contract:generate` 通过容器服务名访问 API。初次下载依赖较慢时可查看对应服务日志；状态未知时先查看 `status`，不删卷重试。

## 本地数据与账号

- Compose 项目名为 `merine-rebuild-dev`，端口只发布到本机回环地址。当前仅服务本地开发，不包含登录或权限控制。
- Vite 开发服务器默认只监听回环地址；容器内由 Compose 注入 `VITE_DEV_HOST=0.0.0.0`，否则宿主机端口映射无法转发。在宿主机直接运行 `pnpm --filter @merine/web dev` 不会暴露到局域网。
- 数据库固定为 `merine_rebuild`，与旧项目的容器、账号、端口及数据卷分开。
- `merine_migrate` 负责该库的结构迁移；`merine_app` 仅有该库的查询与增删改权限。应用不以 root 或迁移账号运行。
- `.env` 由脚本生成，权限为 `600`，不会提交或进入镜像。已有数据卷时不能仅修改 `.env` 来轮换账号密码；数据库内的账号需要配套变更。
- MySQL 数据保存在命名卷中。`down` 不删除卷；`down -v`、清库或恢复数据必须是明确的独立操作。
- 源码挂载便于开发，Linux 的 `node_modules`、Maven 缓存和编译输出放在独立卷中，不混用宿主机依赖。

## 本轮边界

这是一条可运行的基础链路。当前未实现登录鉴权、用户管理、业务搜索表格、Neo4j、AI 调用或完整交付镜像，也没有连接旧数据库或外部业务服务。下一步在这个基础上完成登录与用户体系，再形成业务公共组件。

选型依据：[Spring Boot](https://docs.spring.io/spring-boot/system-requirements.html)、[MyBatis](https://mybatis.org/spring-boot-starter/mybatis-spring-boot-autoconfigure/)、[Vite](https://vite.dev/guide/)、[Ant Design](https://ant.design/docs/react/introduce/)、[Docker Compose](https://docs.docker.com/compose/)。依赖组合以本仓库的实际构建与联调结果为准。
