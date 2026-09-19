# Merine Rebuild 开发入口

独立 monorepo，当前为 M1.1 最小运行工程。使用方式和当前范围见 `README.md`。

- 前端在 `apps/web`，Java API 在 `apps/api`，共享接口类型在 `packages/api-contract`，容器配置在 `infra`。
- 默认用 `./scripts/dev.sh up` 启动 Docker 开发环境；用 `status`、`logs` 查看实际状态，用 `down` 停止并保留数据卷。
- 只使用本项目本地容器与合成数据。不读取同级生产 dump，不连接旧库、生产或外部 AI 服务；不输出或提交 `.env`、密码及凭据。
- 先检查 Git 状态，保护已有改动。提交、推送、删卷、清库与重灌需要对应的明确授权。
- 新项目 Java 使用容器内 JDK 21；不要修改旧项目 JDK 或配置。Node / pnpm 版本以本仓库配置为准。
- 表结构通过新的 Flyway 迁移修改；运行账号只持有 DML 权限，迁移使用独立账号。已执行的迁移不改写。
- 接口变更后生成 OpenAPI 类型；不要手改 `packages/api-contract/src/schema.d.ts`。错误响应保留请求编号，日志不记录密码或完整敏感请求。
- 根据变更运行必要的检查：`./scripts/dev.sh check`、`./scripts/dev.sh build`、真实接口联调或浏览器验证。文档只核对内容与差异。
- 没有实际验证时，不用“编译通过”代替容器就绪、数据库读写或页面验收。
- 本轮没有登录和业务权限；新增业务前先完成认证授权，不把基础联调接口当成业务权限方案。
