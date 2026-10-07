import type { components } from './generated/tasks';

/** 任务处置及情报关联任务：显式导出生成契约，字段定义仍来自后端。 */
export type TaskDetail = components['schemas']['TaskDetail'];
export type TaskBranch = components['schemas']['Branch'];
export type TaskTransfer = components['schemas']['Transfer'];
export type TaskListItem = components['schemas']['TaskListItem'];
export type PageResultTaskListItem = components['schemas']['PageResultTaskListItem'];
export type TaskUnitOption = components['schemas']['UnitOption'];
export type CreateTask = components['schemas']['Create'];
export type DispatchTask = components['schemas']['Dispatch'];
export type SubmitTaskResult = components['schemas']['SubmitResult'];
export type RequestTaskTransfer = components['schemas']['TransferRequest'];
export type RespondTaskTransfer = components['schemas']['TransferResponse'];
export type DecideTaskTransfer = components['schemas']['TransferDecision'];
export type TaskAllowedAction = components['schemas']['AllowedAction'];
export type CloseTask = components['schemas']['Close'];
export type RecallTask = components['schemas']['Recall'];
export type ReturnTask = components['schemas']['ReturnTask'];
export type IntelligenceTaskContext = components['schemas']['IntelligenceTaskContext'];
export type CreateIntelligenceTask = components['schemas']['CreateIntelligenceTask'];
export type LinkedIntelligenceTask = components['schemas']['LinkedIntelligenceTask'];
export type PageResultLinkedIntelligenceTask =
  components['schemas']['PageResultLinkedIntelligenceTask'];
export type TaskIntelligenceSource = components['schemas']['TaskIntelligenceSource'];
