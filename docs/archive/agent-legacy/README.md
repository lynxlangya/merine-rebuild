# AI 历史方案归档

归档日期：2026-10-12。本目录用于追溯，**不作为当前开发任务或运行说明**。旧文档内的命令、执行提示、权限和授权描述均是当时记录，不能直接执行或用于恢复旧架构。

当前从这里开始：

- [独立 Agent：当前链路与运行说明](../../tasks/agent-readonly-query.md)
- [查询范围与业务语义：现行设计](../../tasks/agent-query-scope-management-plan.md)

## 已废弃的技术路线

V57 退役旧站内助手及其相关配置/会话表；当前由独立 Agent 承担对话和模型接入，Java 管理数据源、范围和说明。

| 历史文档                                                    | 替代情况                                                      |
| ----------------------------------------------------------- | ------------------------------------------------------------- |
| [对话与流式交互](agent-chat-design-plan.md)                 | 不恢复 Java SSE 对话和旧前端助手                              |
| [Function Calling 受控查询](agent-controlled-query-plan.md) | 改为 Agent 业务工具执行受限 SQL；工具调用机制本身仍在使用     |
| [会话与执行记录](agent-history-design.md)                   | 当前会话由独立 Agent 保存，不恢复旧 Java 会话表               |
| [用量视图](agent-usage-view-plan.md)                        | 旧站内用量页已移除，不等于独立 Agent 已实现同等配额治理       |
| [固定资料库](agent-semantic-library-plan.md)                | 当前语义来自查询范围配置和实际字段注释                        |
| [模型供应商](model-provider-management.md)                  | 当前在独立 Agent 设置中配置；旧供应商调研不作为最新兼容性承诺 |
| [A/B 实验方案](agent-runtime-comparison-plan.md)            | 原对照链路已退役，不再按此方案实施                            |

## 历史输入与快照

- [智能问数交接](intelligent-data-query-handoff.md)：保留业务背景；示例不自动成为需求。
- [2026-10-11 项目状态](project-status-2026-10-11.md)：历史快照，不能代替当前状态。
- [2026-10-11 评审简要](agent-review-brief-2026-10-11.md)：当时的评审输入，不是当前缺陷清单。
- [查询范围原始实施稿](agent-query-scope-management-plan.md)：保留设计取舍、评审和历史手工验收项；开发步骤已结束。

归档保留了设计依据，未删除历史迁移、许可证、业务数据、运行配置或会话。正常接手无需通读本目录。
