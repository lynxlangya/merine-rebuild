# 目录与模块规则

按业务归属组织、按需要增加层级。以下带未来业务名的目录是放置约定，不要求现在建空目录或脚手架。

## 仓库职责

| 位置                    | 放什么                                   | 不放什么                                |
| ----------------------- | ---------------------------------------- | --------------------------------------- |
| `apps/web`              | 浏览器应用、页面、交互                   | SQL、服务端密钥、后端授权规则的替代实现 |
| `apps/api`              | Java 用例、接口、安全、持久化、外部适配  | 手工复制的前端构建产物                  |
| `packages/api-contract` | OpenAPI 快照、生成类型、少量显式类型导出 | 第二份手写接口模型、业务逻辑            |
| `infra`                 | Compose、Dockerfile、基础设施初始化      | 业务表迁移、真实数据、凭据              |
| `scripts`               | 简短的运行、生成、检查编排               | 从业务代码搬来的业务算法                |
| `docs/rules`            | 当前有效的开发约定                       | 每次任务的流水账                        |
| `docs/sys-design`       | 原始设计与交互参考                       | 直接参与应用打包的产品源码              |
| `docs/archive`          | 历史方案与设计输入                       | 当前完成状态或可执行入口的替代品        |

只有一个根 Git 仓库。构建产物、缓存、日志、上传数据和 `.env` 不进源码；`packages/api-contract` 的 OpenAPI 快照和生成类型按项目约定纳入版本管理。示例配置只放占位值。前端制品在构建阶段进入后端打包目录，未来有第二个前端应用真实复用时再评估 `packages/ui`。

## 前端结构与依赖

```text
apps/web/src/
  app/                     路由、布局、Provider、应用启动与主题装配
  features/
    auth/                  登录与身份恢复
    users/                 用户用例；roles、orgs 同级按需增加
      UserListPage.tsx
      UserForm.tsx
      api.ts
      queries.ts           请求状态复杂到需要分离时再建
      UserListPage.module.css
    graph/                 后续图谱用例
    ai/                    后续会话、证据与复核
    data-tasks/             任务展示与操作
    intel/                 情报发送、接收和反馈
  shared/
    http.ts                现有统一请求入口
    ui/                    经实际复用验证的展示组件
    hooks/                 与业务无关的 hooks
    lib/                   按用途命名的小工具，不设万能 utils.ts
```

- 依赖方向为 `app → features → shared`，各层可使用 `@merine/api-contract`。`shared` 不导入 `features` 或 `app`。
- 页面组件负责组装；请求函数在本 feature 的 `api.ts`；样式、测试、小组件就近放。复杂 feature 再细分 `components`、`hooks` 等，不为每页建立完整目录树。
- 不深层导入另一 feature 的页面、私有状态或内部请求逻辑。实际跨模块能力由所属 feature 提供小型公开出口，或在 app 层组合；禁止循环依赖，不为消除循环把业务整体搬进 shared。
- 组件/页面与文件用 `PascalCase`；hook 用 `useXxx.ts`；普通函数/变量 `camelCase`，常量按语义命名；业务目录和文档使用英文 `kebab-case`。公共出口显式导出，不建层层 `export *`。
- 前端按用户用例拆分，不强求与后端包一一对应；任务中心可以展示多个模块的任务，不能因此获得这些模块的表写权限。

## 后端结构与归属

根包沿用 `com.merine.rebuild`。小模块允许直接放 Controller、Service、Mapper 和类型；增长后按业务子域细分，不把全项目所有 Controller 或 Service 各自堆在一个总目录。

```text
apps/api/src/main/java/com/merine/rebuild/
  common/                  响应、异常、请求标识等无业务归属的技术能力
  bootstrap/               现有工程联调；不作为业务架构模板继续扩展
  system/
    user/                  UserController、UserService、UserMapper、请求/响应/持久化类型
    role/                  角色用例
    org/                   单位用例
    api/                   有真实调用者后提供的模块公开契约
  archive/                 档案、来源记录、资料导入及其任务
  graph/                   关系投影、同步任务、图谱查询
  ai/                      会话、生成、证据、复核及所选平台适配
  intel/                   草稿、发送快照、回执、反馈、办结

apps/api/src/main/resources/
  mapper/<module>/         与 Mapper 对应的 XML
  db/migration/            全项目统一编号的 Flyway 迁移
apps/api/src/test/java/    与受测代码包路径对应的测试
```

- Java 包使用小写，类型 `PascalCase`，方法/变量 `camelCase`，常量 `UPPER_SNAKE_CASE`。名称说清业务，如 `SendIntelRequest`、`UserSummary`，避免泛化的 `DataInfo`、`Manager`。
- 模块内通过 Controller → Service → Mapper 组织职责；简单 DTO 可用 record，不强制为同形数据复制多套 VO/BO/DO，也不强制 `Service + ServiceImpl` 成对出现。对外响应不得暴露密码哈希、内部持久化字段。
- 跨模块默认调用 owner 明确公开的查询或命令能力，使用公开请求/结果类型；不导入对方 Mapper、持久化对象或内部 Service。公开能力可以包含查询和写命令，不限于 `QueryService`，也不默认引入事件总线。
- **一张表只有一个写入 owner**。例如 archive 拥有档案和来源，graph 维护派生关系，AI 经 intel 的公开命令创建草稿，不直接更新情报表。迁移文件也应说明 owner。
- 跨模块列表默认用批量查询接口组合，避免逐行调用。确有同库只读 JOIN 的需要，通过所属模块明确暴露的只读查询契约实现：写清参与表、授权条件、结果归属并有集成测试；不得借此绕过数据权限或反向写表。常规模块内 JOIN 无此限制。
- `common` 只收纳不依赖业务模块的技术能力。`User`、单位范围策略、情报状态留在 owner；确需共享时暴露最小契约，而不是搬整个业务模型。最小身份上下文只携带必要身份标识，不复制用户/角色实体。
- 不为未来拆微服务预建多 Maven 模块、DDD 全套分层、CQRS 总线或通用任务引擎。模块边界先通过代码与 SQL 审查维护；ArchUnit 等工具若有实际价值，仍须先申请新依赖。

目录承担导航与边界，不代替业务设计。移动文件时同步引用、包名和测试；仅为整齐而改变可工作的模块不属于普通功能任务的默认范围。
