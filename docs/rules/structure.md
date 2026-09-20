# 目录、命名与模块边界

本项目采用业务模块优先的模块化单体。目录先说明“这段代码属于哪个业务”，再按真实职责增加层级；不按文件数量强制分包，不预建空目录或整套 Controller/Service/Repository 模板。

## 1. 仓库职责

| 位置                    | 放什么                                  | 不放什么                                |
| ----------------------- | --------------------------------------- | --------------------------------------- |
| `apps/web`              | 浏览器应用、页面、交互                  | SQL、服务端密钥、后端授权规则的替代实现 |
| `apps/api`              | Java 用例、接口、安全、持久化、外部适配 | 手工复制的前端构建产物                  |
| `packages/api-contract` | OpenAPI 快照、生成类型、显式类型导出    | 第二份手写接口模型、业务逻辑            |
| `infra`                 | Compose、Dockerfile、基础设施初始化     | 业务表迁移、真实数据、凭据              |
| `scripts`               | 运行、生成、检查编排                    | 从业务代码搬来的业务算法                |
| `docs/rules`            | 当前有效的开发约定                      | 每次任务的流水账                        |
| `docs/sys-design`       | 原始设计与交互参考                      | 直接参与应用打包的产品源码              |
| `docs/archive`          | 历史方案与设计输入                      | 当前完成状态或可执行入口的替代品        |

只有一个根 Git 仓库。构建产物、缓存、日志、上传数据和 `.env` 不进源码；OpenAPI 快照和生成类型纳入版本管理。当前没有完整交付镜像，前端制品如何进入交付包在交付阶段实现。出现第二个前端应用且有真实复用时，再评估 `packages/ui`。

## 2. 前端：用例就近，装配向下依赖

```text
apps/web/src/
  app/                       路由、布局、Provider 装配、应用主题配置
    theme/                   语义 tokens、全局样式、Ant Design 映射
  features/
    auth/                    登录、身份恢复、路由守卫
      public.ts              其他 feature 所需的 useAuth
    users/                   用户管理
      UserListPage.tsx        页面及同名 CSS Module
      api.ts                 HTTP 请求与生成契约绑定
      queries.ts             查询 key、TanStack Query hooks
      model.ts               筛选模型、纯转换与业务错误分类
      components/            表单、搜索、表格等内部组件
    units/                   单位管理；与 users 使用相同职责划分
      public.ts              单位选择器、选项查询和缓存 key
    home/                    当前身份与业务入口
    diagnostics/             工程联调
  shared/
    http.ts                  统一请求、CSRF 与会话失效通知
    api-error.ts             通用 API 错误分类
    theme/ThemeProvider.tsx   无业务依赖的主题状态与 hook
    ui/                      经实际复用验证的展示组件
```

- 依赖方向为 `app → features → shared`；上层也可直接用 shared，各层可使用 `@merine/api-contract`。feature 不导入 app；shared 不导入 feature 或 app。`main.tsx` 只负责启动装配。
- app 可以导入 feature 的页面、Provider、守卫来装配应用。**feature 之间只通过对方 `public.ts` 的显式出口调用**，不能深导入组件、请求、状态或模型。没有实际跨 feature 使用时不用建立 public 文件；同一 feature 内直接导入所属文件，避免绕自身出口形成循环。
- 页面负责组装；页面内部组件及同名 CSS Module 就近放 `components/`。小 feature 可以保持平铺，独立组件开始分工或复用时再分目录；不按“3 个组件”或行数机械触发。`AuthProvider`、`RequireAuth` 等能力文件可以留在 feature 根目录，不为目录齐整嵌套一层。
- `api.ts` 负责 HTTP；`queries.ts` 管理服务端缓存；`model.ts` 放纯转换、筛选模型、表单错误映射等。只在存在相应职责时建文件；某个模型以后变大，按具体业务概念继续拆分，不新增万能 `utils.ts`。通用错误工具从 shared 直接导入，不借 api 文件层层转导出。
- 共享主题状态在 `shared/theme`，应用 tokens 与 Ant Design 映射在 `app/theme`；由 app 安装 Provider。这样登录页也可切换主题，又不反向依赖应用装配。
- HTTP 模型使用生成类型；纯展示模型可在本 feature 定义。查询/表单状态按[前端规则](frontend.md)管理，组件移动不改变状态生命周期。
- 前端按用户用例拆分，不强求与 Java 包或数据库表一一对应。当前角色选项仅服务用户表单，可留在 users；出现独立角色用例时再建立对应 feature。

## 3. 后端：业务归属先于技术层

根包为 `com.merine.rebuild`，启动类位于根包，以覆盖组件与 Mapper 扫描。当前结构如下；未来的档案、图谱、AI、情报按实际业务新增模块，不先建立占位包。

```text
com.merine.rebuild/
  MerineApplication.java
  common/                    响应、异常、请求标识；不依赖业务模块
  bootstrap/                 工程联调，不作为正式业务模块的扩展模板
  auth/                      登录、会话、安全过滤链
  system/                    系统管理的分组，不是所有类均可互访的边界
    security/                SystemAdminGuard，共用系统管理门禁
    unit/                    单位用例：Controller、Service、Lookup
      dto/                   请求与对外结果
      persistence/           UnitMapper、UnitRow
    user/                    sys_user、sys_user_role 的业务归属
      account/               认证所需的公开账号查询、登录时间登记、凭据约束
        persistence/         登录查询的 Mapper、SQL 聚合投射
      admin/                 用户管理 Controller、Service
        dto/                 管理接口请求、响应
        persistence/         管理 SQL、查询条件、结果投射
      usage/                 对单位用例公开的直属用户数查询
      support/               用户模块内部共享的纯函数
    role/                    角色选项查询；当前规模小，保持平铺
    seed/                    仅 seed profile 执行的本地账号初始化

apps/api/src/main/resources/
  mapper/system/unit/UnitMapper.xml
  mapper/system/user/account/UserAccountMapper.xml
  mapper/system/user/admin/UserAdminMapper.xml
  db/migration/              全项目统一编号的 Flyway 迁移

apps/api/src/test/java/com/merine/rebuild/
  support/                   无业务 fixture 的 MockMvcRegressionSupport
  auth/                      认证回归与账号 fixture
  system/security/           可复用的系统管理员 fixture
  system/user/admin/         用户管理回归与专属 fixture
  system/unit/               单位管理回归与专属 fixture
  common/                    异常与日志回归
```

### 分层与公开能力

- Controller 处理 HTTP 协议和入口校验；Service 编排用例、事务和业务规则；Lookup 提供有明确消费者的查询契约；Mapper 负责 SQL。内部简单查询可直接使用现有 Service，不为每张表再造 Lookup 或无职责转发层。
- 小模块可以平铺。像单位维护、用户管理这样需要区分公开 DTO 与数据库投射的用例，拆出 `dto` / `persistence`。`account`、`admin`、`usage` 是 user 内的不同能力，不是各自拥有一套用户表的独立业务模块；其他模块不照抄这套子包名称。
- Java 的 `public` 不等于项目允许跨模块使用。当前公开能力是 `user.account` 的账号查询、登录时间登记及相关结果/凭据约束，`user.usage.UserUnitUsageLookup` 的直属用户计数，`unit.UnitLookup` 和 `UnitSummary`，以及 `role.RoleLookup` 和 `RoleSummary`。系统管理用例共用 `SystemAdminGuard`。新增跨模块消费者时，先确定所需的最小公开能力。
- 跨模块不导入 Mapper、数据库投射或内部 Service。公开请求/结果不得依赖持久化类型；SQL 聚合列在 owner 内转换。例如 `UserAccountRow` 留在 persistence，`UserAccountLookup` 转成含角色列表的 `UserAccount` 后才交给 auth。
- 同形、无敏感字段且语义一致的简单查询可以直接映射到公开 record，例如 `RoleSummary`、`UserAccountState`；不为形式统一复制 VO/BO/DO。含密码哈希的账号结果仅用于服务端认证，不进入会话、HTTP 响应或日志。
- 依赖检查要看具体能力：当前 `user.admin → unit.UnitLookup`，`unit.UnitService → user.usage`，两条查询能力都不反向调用管理 Service。禁止形成类/Bean 的循环依赖或业务用例相互回调；目录名称本身不能证明边界有效。
- `common` / 模块内 `support` 不反向依赖其调用方；不要为 Javadoc 链接引入业务实现 import。公共技术层不收纳用户实体、单位范围或情报状态。

### SQL 与数据归属

- **一张业务表只有一个业务写入 owner，不等于一个表只能有一个 Mapper。** 同一 owner 内可以按用例拆 Mapper：账号查询、登录时间登记、管理操作与统计各有职责；不能让不同入口各自复制同一套写入规则。普通用例优先复用已有 Mapper，不无依据拆分或合并。
- 当前 user 拥有 `sys_user`、`sys_user_role`，unit 拥有 `sys_unit`，role 拥有 `sys_role`。seed 是本地初始化入口，只补缺失数据，不供在线业务调用；它的事务与幂等约束独立明确。它不是新增业务跨表写入的模板。
- XML 文件名与 Mapper 接口同名；`namespace` 必须等于 Mapper **完整类名**。资源目录按业务/用例路径对应，**省略根包和技术子包 `persistence`**：`system.user.account.persistence.UserAccountMapper` 对应 `mapper/system/user/account/UserAccountMapper.xml`。由 `mybatis.mapper-locations` 显式扫描，不依赖同包自动发现。
- 简单固定 SQL 可以用注解，动态筛选、多表关联和复杂映射优先 XML。同一方法不同时在注解和 XML 定义 SQL。复杂注解 SQL 在受影响时迁移，不为凑目录一致性改动全部历史代码。
- 跨模块读取优先批量公开查询，避免逐行调用。确需同库只读 JOIN 时，由查询 owner 公开契约，并说明参与表、授权条件和结果归属，有真实 MySQL 验证；不能反向写表或绕过数据权限。当前账号和用户列表关联单位、角色属于这种查询。

## 4. 命名：对象与职责可辨认

| 对象               | 约定与例子                                                                                                          |
| ------------------ | ------------------------------------------------------------------------------------------------------------------- |
| Java 包            | 小写业务名，单数名词：`system.unit`、`system.user`；包名不用连字符                                                  |
| 前端 feature / URL | feature 用英文 kebab-case，资源集合用复数：`features/units`、`/api/system/units`                                    |
| React 组件与样式   | `UserFormDrawer.tsx` / `UserFormDrawer.module.css`；页面以 `Page` 结尾                                              |
| hook / 普通文件    | 独立 hook 为 `useXxx.ts`；其他按职责，如 `queries.ts`、`api-error.ts`                                               |
| Java 类型          | 对象 + 职责：`UnitController`、`UnitService`、`UnitLookup`、`UnitMapper`；同一职责链保持同一业务前缀                |
| 请求 / 结果        | 名称说清语义，如 `CreateUnit`、`UnitSummary`、`UnitTreeNode`；紧密相关的少量请求允许放 `UnitRequests` 的嵌套 record |
| 持久化模型         | `UserAccountRow` 表示 SQL 投射，`UserQuery` 表示 SQL 条件；不把 Row 当 HTTP 响应                                    |
| 测试               | Java `*Test`（现有业务回归为 `*RegressionTest`），共用基座 `*Support`；前端 `*.test.ts(x)` 就近放置                 |

`Admin` 在 user 中区分管理用例与认证账号能力，保留 `UserAdminService`；unit 当前只有一套维护用例，用 `UnitService` 即可。不要只为字面一致批量添加或删除后缀。不使用 `Manager`、`DataInfo`、`BaseService` 等泛化名称，也不默认配对 `Service + ServiceImpl`。

Java 方法/变量和 TypeScript 普通函数/变量用 camelCase，Java 常量用 UPPER_SNAKE_CASE；TypeScript 不可变常量按其语义命名。重命名请求/响应类型时核对 OpenAPI schema 名，内部文件调整不应意外改变客户端契约。

## 5. 新功能与结构调整的验收

1. 先找已有业务 owner 和完整入口链路，再决定文件位置；只有确有职责分离或复用才建新层。不为未来微服务预建多 Maven 模块、DDD 全套分层或通用事件/任务总线。
2. 新建跨模块调用时说明消费者、公开请求/结果和权限边界；菜单/目录/`public.ts` 不能代替后端授权。规则还未实现的部分按[后端规则](backend.md)和[规则索引](README.md)明确标注。
3. 移动 Java、测试或 Mapper XML 时，同步 package、import、namespace、resultType、注释和文档链接；清理本项目的编译输出后重新构建，避免旧 class/XML 留在 target 造成假通过。不得删除数据库卷来处理构建问题。
4. 测试包镜像被测能力；只共享协议和隔离校验，业务 fixture 各有 owner。结构调整复用现有回归，检查测试被实际发现且数量未因迁移丢失；检查强度见[测试规则](testing.md)。
5. 核对前端依赖、构建和受影响交互；用当前代码验证 Spring/MyBatis 映射及代表性 HTTP 结果。接口形状有变化才重新生成契约，生成文件不手改。结构调整与功能变化分别说明。

依据：[Spring Boot 代码布局](https://docs.spring.io/spring-boot/reference/using/structuring-your-code.html)并不规定唯一目录结构，示例按业务组织；[MyBatis Mapper](https://mybatis.org/mybatis-3/getting-started.html)通过 namespace 关联 SQL 与接口。这里的子包、public 出口和命名是结合当前规模的项目约定，不引入 Spring Modulith、ArchUnit 或新的构建插件。
