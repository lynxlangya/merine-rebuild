# 任务处置：业务流程

[返回图解入口](README.md) · [模块架构](architecture.md) · [库表关系](database.md)

核对日期：2026-09-27。流程图中的动作都由 [TaskService](../../../apps/api/src/main/java/com/merine/rebuild/task/TaskService.java) 校验，前端按钮只呈现服务端返回的操作。

## 1. 从下发到办结

总队可以发给直属支队，支队也可以独立发起任务给直属大队；“发起单位”不固定等于总队。

```mermaid
flowchart TB
    Create["发起单位新建任务<br/>整单 OPEN"]
    Pending["为每个直属目标建立分支<br/>分支 OPEN / 当前段 PENDING_ACCEPT"]
    Choose{"接收单位承接前选择"}
    Active["承接任务<br/>当前段 IN_PROGRESS"]
    Progress["记录进展 / 向下下发 / 合法支队交接<br/>责任仍须最终答复"]
    Submit["提交正式结果<br/>无未决交接，所有子分支已结束"]
    Answered["新增 task_result<br/>分支及当前段 COMPLETED"]
    Return["选择退回原因并填写说明<br/>原段 RETURNED"]
    Back["同分支新增发送单位的接回段<br/>IN_PROGRESS / source_action = RETURN"]
    Resolve{"发送单位如何处理"}
    Reassign["重派给一个直属下级<br/>排除所有曾退回该分支的单位<br/>接回段 REASSIGNED，再新增待承接段"]
    Recall["说明原因撤回分支<br/>分支及接回段 RECALLED<br/>不产生正式结果"]
    AllDone{"整单还有 OPEN 分支？"}
    Wait["整单保持 OPEN<br/>其他分支继续办理"]
    Closing["整单 AWAITING_CLOSE<br/>全部分支已答复或撤回"]
    Close["发起单位填写总体结论并办结<br/>整单 COMPLETED"]

    Create --> Pending --> Choose
    Choose -->|"承接"| Active --> Progress --> Submit --> Answered
    Choose -->|"退回"| Return --> Back --> Resolve
    Resolve -->|"重新派发"| Reassign --> Choose
    Resolve -->|"撤回"| Recall
    Answered --> AllDone
    Recall --> AllDone
    AllDone -->|"有"| Wait
    AllDone -->|"没有"| Closing --> Close
```

图中的“办理”不是自动流水线：承办单位可以多次记录进展，也可以直接提交结果。向下下发与支队交接各有额外前置条件，见后两张图。

### 关键规则

- 退回只允许在待承接时，原因是“派错单位 / 不属本单位职责 / 要求不明确”，另附说明。
- 退回后的接回段可以记录进展，但只允许通过**重派或撤回**结束；不能用提交结果或普通下发绕过。
- 重新派发需要当前承办期限尚未过去；过期后返回 `DUE_PASSED`，仍可以说明原因撤回。
- `COMPLETED` 分支必须有一份正式结果；`RECALLED` 分支没有结果，分支 `completed_at` 保持空，撤回时间在承办段 `ended_at`。
- 整单“待办结”不写办结时间。显式办结才写总体结论、办结人、办结时间，且不能重开。
- 办结默认使用 `TASK_CREATE`，分支撤回使用 `TASK_DISPATCH`；还必须符合单位与当前责任条件。

正式结果有五种：`FULFILLED` 目标达成、`NOT_FOUND` 经核查未发现、`PARTIAL` 部分完成、`UNABLE_TO_VERIFY` 无法核实、`OUT_OF_JURISDICTION` 转出辖区。最后一种必须选启用的建议单位，不能选本单位；建议不会自动派发任务。

## 2. 向下下发：新增子分支，父责任保留

```mermaid
flowchart TB
    Parent["支队已经承接父分支<br/>当前段 IN_PROGRESS"]
    Gate{"无未决交接、非退回接回段<br/>当前期限未过且有合法直属下级？"}
    Block["阻止下发<br/>按钮展示对应原因"]
    Dispatch["选择直属大队并明确期限<br/>子期限必须在未来，且不晚于父段期限"]
    Children["每个目标新增一个子分支<br/>各自从 PENDING_ACCEPT 开始"]
    ChildWork["各大队承接并答复<br/>或退回后由支队重派 / 撤回"]
    ChildDone{"仍有 OPEN 子分支？"}
    ParentWait["支队可继续记录进展<br/>禁止父分支交结果或申请交接"]
    ParentResult["子分支全部答复或撤回<br/>支队可提交父分支汇总结果"]
    TaskCheck["再检查整单全部分支<br/>决定是否进入 AWAITING_CLOSE"]

    Parent --> Gate
    Gate -->|"否"| Block
    Gate -->|"是"| Dispatch --> Children --> ChildWork --> ChildDone
    ChildDone -->|"有"| ParentWait
    ParentWait --> ChildWork
    ChildDone -->|"没有"| ParentResult --> TaskCheck
```

父分支始终由支队负责汇总。子分支结束不会自动替父分支交结果。页面汇总只数当前视图最上层的分支；总队视图不把大队子分支再次计入根分支总数。

大队发现事项跨辖区时，提交如实结果与建议单位；支队按需创建关联 `source_result_id` 的**另一项任务**并给新期限，大队不能把当前分支直接平级转给其他大队。

## 3. 支队交接：同一分支更换承办单位

适用范围：总队直接下发的根分支，当前由支队办理；目标为同一总队下其他启用支队，且从未承办过该分支。没有未结束子分支才可申请。

```mermaid
sequenceDiagram
    participant A as 原承办支队 A
    participant S as TaskService / MySQL
    participant B as 目标支队 B
    participant H as 发起总队

    A->>S: 申请交接（原因、已做工作、依据、剩余事项）
    S->>S: 新增申请 AWAITING_TARGET
    Note over A,B: 尚未交接，A 仍负责；B 只有受限申请视图
    B->>S: 回应申请
    alt B 拒绝
        S->>S: TARGET_DECLINED
        S-->>A: A 继续办理，可重新申请
    else B 同意并说明所需办理时长
        S->>S: AWAITING_ISSUER
        Note over A,H: B 同意仍不转移责任，等待总队决定
        H->>S: 决定是否批准
        alt 总队拒绝
            S->>S: ISSUER_REJECTED
            S-->>A: A 继续办理
        else 总队批准并给出 B 的新期限
            S->>S: 校验新期限足够覆盖 B 要求的办理时长
            S->>S: 同事务结束 A 段为 TRANSFERRED
            S->>S: 新建 B 的 IN_PROGRESS / PEER_TRANSFER 段
            S->>S: 更新 current_assignment_id
            opt 新期限晚于整单当前期限
                S->>S: 要求延期理由，更新 current_due_at，记 EXTEND_DUE
            end
            S->>S: 申请变为 APPROVED，记 TRANSFER_APPROVE
            S-->>B: 正式负责本分支，无需再点承接
        end
    end
    Note over A,S: A 可在 AWAITING_TARGET 或 AWAITING_ISSUER 时撤回申请，变为 WITHDRAWN
```

### 责任、时间与可见范围

- 正式交接发生在**总队批准时**。旧段的期限和历史保留，新段 `accepted_at` 是批准时刻，承接人沿用 B 同意申请的用户。
- 办理时长从批准时起算；界面填小时或天，接口与数据库存分钟。批准期限必须满足该时长。
- 有未决交接时，A 可记录进展、撤回申请；不能新增子分支或提交正式结果。
- 延长整单期限只改 `current_due_at`，不改 `initial_due_at`，也不自动延长其他并行分支的期限。
- 其他分支的参与单位能看到整单延期事件及前后期限，看不到这条交接的原因和办理细节。
- 目标支队停用后，总队的批准和拒绝都会被单位校验阻止；现有恢复路径是由申请方撤回申请。

## 4. 从图走到验收

每做一步，都对照详情中的 `status`、分支 `currentAssignmentId`、`assignments`、`result`、`allowedActions` 和 `actions`。同一动作的页面禁用原因应与接口报错一致；旧任务保持原编号，旧的自动办结任务可以没有总体结论。

源码入口：[TaskController](../../../apps/api/src/main/java/com/merine/rebuild/task/TaskController.java)、[TaskService](../../../apps/api/src/main/java/com/merine/rebuild/task/TaskService.java)。自动回归入口：[TaskHandlingRegressionTest](../../../apps/api/src/test/java/com/merine/rebuild/task/TaskHandlingRegressionTest.java)。
