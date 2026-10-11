# 三份交付产物

仓库根执行 `./scripts/package.sh`：先构建并运行现有 API 回归，再组装三份包。
`--skip-build` 仅用于已成功执行当前源码构建后的重新组装，不能替代构建验收。

| 产物                                | 内容                                        | 运行要求                         |
| ----------------------------------- | ------------------------------------------- | -------------------------------- |
| api/merine-api.tar.gz               | Spring Boot jar、同版本 Flyway 迁移         | Java 21、MySQL 8.4               |
| web/merine-web.tar.gz               | public 静态资源、nginx 配置参考             | nginx；无需 Node                 |
| agent/merine-agent-linux-x64.tar.gz | 编译后 JS、精简生产依赖、Web 资源、启动脚本 | Linux x86_64 glibc、Node 24.18.0 |

`SHA256SUMS` 提供三个压缩包的校验值。包中没有密钥、.env、开发数据库或 Agent 会话。
原有 dist 下的其他文件不会自动清理；上表为本打包入口管理的三份产物。

API：解压后先用独立迁移账号通过 Flyway 执行 migration 目录，再配置 DB_URL、DB_APP_USER、DB_APP_PASSWORD、模型密钥加密配置等运行环境，执行 `java -jar api/merine-api.jar`。禁止用应用运行账号执行迁移；无需启动 seed/dev 初始化流程。HTTPS 下设置安全 Cookie。完整环境约定参考仓库 infra/compose.prod.yaml，不复制开发环境凭据。

Web：将 web/public 内容发布到 nginx 静态目录。附带配置默认反代 `api:9002`，并引用 `/etc/nginx/security-headers.conf`；按实际部署地址配置，保持 /api 同源与 SSE 不缓冲。它仍是主系统 Web，不自动把旧 /agent 页面切换到独立 Agent。

Agent：见 agent/DEPLOY.md。默认单独监听 3080，不包含 Node 运行时。它目前仍是独立引擎，Java 身份桥及海防业务工具未接入；这三份包不是已经完成统一认证的一体化部署。

构建 Agent 的 Linux 镜像会下载已声明依赖及编译工具，仅作用于隔离的 Docker 构建阶段；开发工作区的依赖、源码和运行实例不改动。此打包流程不连接生产环境、不调用真实模型。
