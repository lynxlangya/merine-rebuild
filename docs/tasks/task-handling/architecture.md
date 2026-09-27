# 任务处置：模块架构

[返回图解入口](README.md) · [业务流程](business-flow.md) · [库表关系](database.md)

核对日期：2026-09-27。模块采用 React 页面 → Spring MVC Controller → Service → MyBatis → MySQL 的调用链；流程由任务用例中的显式代码实现。

## 1. 分层架构图

实线是运行时调用或数据传递；虚线是契约生成与类型依赖。

```mermaid
flowchart TB
    subgraph WEB[前端 apps/web]
        Page["TaskHandlingPage<br/>列表、查询、弹窗与提交"]
        Detail["TaskDetailView / BranchDrawer<br/>树表、结论与办理经过"]
        Model["model.ts / taskTime.ts<br/>展示转换、汇总与上海时间"]
        Cache["TanStack Query<br/>任务缓存 / 会话内字典缓存"]
        Api["tasks/api.ts / dictionaries/api.ts<br/>共用 shared/http.ts<br/>HTTP、Cookie、CSRF、任务幂等键"]
        Page --> Detail
        Detail --> Model
        Page --> Cache
        Page --> Api
        Cache --> Api
    end

    subgraph API[后端 apps/api]
        Security["Spring Security + AccountStateFilter<br/>会话、CSRF、账号及授权版本"]
        Controller["TaskController<br/>路由、ID 解析、请求校验"]
        Service["TaskService<br/>功能权限、参与范围、状态、期限、事务"]
        Guard["PermissionGuard<br/>检查会话中的权限码"]
        Dict["DictionaryLookup<br/>结果类型 / 退回原因可选性"]
        DictApi["GET /api/dictionaries<br/>字典标签与启用状态"]
        Mapper["TaskMapper + TaskMapper.xml<br/>任务 SQL 的唯一写入入口"]
        Views["TaskViews<br/>可见详情 + allowedActions + 禁用原因"]
        Controller --> Service
        Service --> Guard
        Service --> Dict
        Service --> Mapper
        Service --> Views
        DictApi --> Dict
    end

    Api -->|"同源 /api/tasks 与 /api/dictionaries"| Security
    Security --> Controller
    Security --> DictApi
    Views -->|"统一 JSON 响应，经查询缓存更新页面"| Page

    subgraph DB[本项目 MySQL]
        TaskDB[("8 张 task 表<br/>责任、结果、历史、幂等、编号")]
        SystemDB[("sys_unit / sys_user<br/>sys_dict_type / sys_dict_item")]
    end
    Mapper -->|"读写任务数据"| TaskDB
    Mapper -->|"只读单位事实 / 当前人员姓名"| SystemDB
    Dict -->|"读取字典"| SystemDB

    subgraph CONTRACT[开发时生成契约]
        DTO["TaskRequests / TaskViews<br/>Controller 接口声明"]
        OpenAPI["运行中的本地 API<br/>导出 OpenAPI"]
        Types["packages/api-contract<br/>openapi.json / schema.d.ts"]
        DTO -.-> OpenAPI
        OpenAPI -.->|"contract:generate"| Types
    end
    Types -.->|"TypeScript 类型"| Api
    Types -.->|"TypeScript 类型"| Model
```

任务与字典请求都经过 Spring Security。`index.ts` 维护类型别名；`openapi.json` 和 `schema.d.ts` 从新 API 生成，不手工维护第二份接口定义。

如果用 React 的经验理解 Java：Controller 类似“请求适配入口”，Service 是处理完整业务意图的函数集合，Mapper 负责执行明确的 SQL。`TaskViews` 类似传给页面的类型化数据，其中 `allowedActions` 直接告诉页面当前可以展示哪些操作。

### 权限和按钮分别负责什么

1. Security 确定请求是否有有效会话、写请求是否通过 CSRF 校验。
2. `TaskService` 用权限码确定“能做什么”，再用单位参与范围、当前责任与状态确定“能对哪条数据做”。
3. `detailFor` 过滤可见分支和历史，计算整单及分支的 `allowedActions`。
4. 前端映射按钮名称；`enabled=false` 保留按钮并显示 `reason`，没有下发的操作不显示。
5. 点击按钮后，写接口重新校验；页面缓存里的可用按钮不能代替服务端授权。

单位管理另通过 `TaskUnitUsageLookup` 查询任务引用，以阻止删除已被任务引用的单位或调整其上级。任务数据的写入仍归 `TaskMapper`。

## 2. 一次“提交处置结果”的时序

以下是 `submitResult` 的实际顺序。创建任务还会分配编号；办结接口自行锁整单，不能套用要求整单为 `OPEN` 的 `locked()`。

```mermaid
sequenceDiagram
    actor U as 当前承办用户
    participant P as TaskHandlingPage
    participant H as api.ts / shared/http.ts
    participant C as Security / TaskController
    participant S as TaskService
    participant D as Mapper / MySQL

    U->>P: 填结果类型、办理情况、结论、建议单位
    P->>H: 提交表单 + 当前意图的 Idempotency-Key
    H->>C: POST /api/tasks/{id}/branches/{branchId}/results
    C->>C: 会话、CSRF、请求 DTO 校验
    C->>S: submitResult(auth, id, branchId, key, input)
    Note over S,D: 进入 READ_COMMITTED 事务
    S->>S: 权限码、当前单位、规范化摘要
    S->>D: replay 查询已有幂等命令
    alt 同一键已成功且摘要一致
        D-->>S: 已关联的 task_id
        S->>D: 按当前参与范围读取详情
        S-->>P: 返回当前详情，不重复写结果
    else 新意图
        S->>D: 按主键顺序锁本单位及建议单位
        S->>D: reserve 预占幂等键
        Note over S,D: 若并发请求已完成，则在此重放；否则继续
        S->>S: 校验结果取值、字典启用与建议单位
        S->>D: 锁整单、分支、当前承办段
        S->>S: 校验当前责任、非退回段、无未决交接、无 OPEN 子分支
        S->>D: 新增 task_result
        S->>D: 承办段和分支变为 COMPLETED
        S->>D: 追加 RESULT 历史
        S->>D: 没有 OPEN 分支时整单转 AWAITING_CLOSE
        S->>D: 完成 task_command，读取最新详情
        Note over S,D: 成功提交；任何异常整体回滚
        S-->>P: 当前详情及 allowedActions
    end
    P->>P: 失效任务列表 / 详情缓存并重新查询
    P-->>U: 刷新状态、结果、按钮与历史
```

同一键换内容会返回 `IDEMPOTENCY_CONFLICT`。幂等记录关联业务对象，重放读取的是**当前可见详情**，不是保存的一份旧响应 JSON。失败回滚时，结果、状态、历史和幂等占位都不能留下半成品。

## 3. 代码对照入口

| 层次     | 入口                                                                                                                                                                                                                                                           | 建议先看                                                         |
| -------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ---------------------------------------------------------------- |
| 页面     | [TaskHandlingPage.tsx](../../../apps/web/src/features/tasks/TaskHandlingPage.tsx)                                                                                                                                                                              | 查询、`open`、`submit`、结果和办结弹窗                           |
| 展示     | [model.ts](../../../apps/web/src/features/tasks/model.ts)                                                                                                                                                                                                      | `branchRows`、`taskSummary`、`branchCommands`、`historyEntries`  |
| HTTP     | [api.ts](../../../apps/web/src/features/tasks/api.ts)、[shared/http.ts](../../../apps/web/src/shared/http.ts)                                                                                                                                                  | 路径、幂等键、CSRF、统一错误                                     |
| 接口     | [TaskController.java](../../../apps/api/src/main/java/com/merine/rebuild/task/TaskController.java)                                                                                                                                                             | 每个动作如何进入 Service                                         |
| 业务     | [TaskService.java](../../../apps/api/src/main/java/com/merine/rebuild/task/TaskService.java)                                                                                                                                                                   | `submitResult`、`close`、`recall`、`detailFor`、`allowedActions` |
| 数据     | [TaskMapper.xml](../../../apps/api/src/main/resources/mapper/task/TaskMapper.xml)                                                                                                                                                                              | 锁定读、条件更新、历史与幂等 SQL                                 |
| 保护边界 | [TaskUnitUsageLookup.java](../../../apps/api/src/main/java/com/merine/rebuild/task/TaskUnitUsageLookup.java)                                                                                                                                                   | 单位管理如何查询任务引用                                         |
| 回归证据 | [TaskHandlingRegressionTest.java](../../../apps/api/src/test/java/com/merine/rebuild/task/TaskHandlingRegressionTest.java)、[TaskIdempotencyConcurrencyTest.java](../../../apps/api/src/test/java/com/merine/rebuild/task/TaskIdempotencyConcurrencyTest.java) | 状态与权限、禁用原因一致性、并发与回滚                           |
