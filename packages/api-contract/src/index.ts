import type { components } from './schema';

export type BootstrapStatus = components['schemas']['BootstrapStatus'];
export type ProbeRecord = components['schemas']['ProbeRecord'];
export type CreateProbe = components['schemas']['CreateProbe'];

/** 认证：接口声明的契约以此为准，前端不再手写平行模型。 */
export type AuthUser = components['schemas']['AuthUserResponse'];
export type LoginRequest = components['schemas']['LoginRequest'];
export type FieldError = components['schemas']['FieldError'];

/** 用户管理。 */
export type UserSummary = components['schemas']['UserSummary'];
export type PageResultUserSummary = components['schemas']['PageResultUserSummary'];
export type CreateUser = components['schemas']['CreateUser'];
export type UpdateUser = components['schemas']['UpdateUser'];
export type ChangeStatus = components['schemas']['ChangeStatus'];

/** 单位管理：用户管理的所属单位选择与组织树共用同一套契约。 */
export type UnitSummary = components['schemas']['UnitSummary'];
export type UnitTreeNode = components['schemas']['UnitTreeNode'];
export type UnitView = components['schemas']['UnitView'];
export type CreateUnit = components['schemas']['CreateUnit'];
export type UpdateUnit = components['schemas']['UpdateUnit'];

/** 角色：用户管理的筛选与表单选择。 */
export type RoleSummary = components['schemas']['RoleSummary'];
