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
export type ResetPassword = components['schemas']['ResetPassword'];

/** 单位管理：用户管理的所属单位选择与组织树共用同一套契约。 */
export type UnitSummary = components['schemas']['UnitSummary'];
export type UnitTreeNode = components['schemas']['UnitTreeNode'];
export type UnitView = components['schemas']['UnitView'];
export type CreateUnit = components['schemas']['CreateUnit'];
export type UpdateUnit = components['schemas']['UpdateUnit'];

/** 角色：用户管理的筛选与表单选择。 */
export type RoleSummary = components['schemas']['RoleSummary'];

/** 角色管理：列表、详情、成员与写请求。 */
export type RoleListItem = components['schemas']['RoleListItem'];
export type RoleDetail = components['schemas']['RoleDetail'];
export type RoleMember = components['schemas']['RoleMember'];
export type PageResultRoleListItem = components['schemas']['PageResultRoleListItem'];
export type PageResultRoleMember = components['schemas']['PageResultRoleMember'];
export type CreateRole = components['schemas']['CreateRole'];
export type UpdateRole = components['schemas']['UpdateRole'];
export type ChangeRoleStatus = components['schemas']['ChangeRoleStatus'];

/** 菜单与导航：菜单资源树同时是角色授权用的权限勾选树。 */
export type MenuNode = components['schemas']['MenuNode'];
export type NavigationNode = components['schemas']['NavigationNode'];
export type RouteKeyOption = components['schemas']['RouteKeyOption'];
export type CreateMenu = components['schemas']['CreateMenu'];
export type UpdateMenu = components['schemas']['UpdateMenu'];
export type MenuDeleteImpact = components['schemas']['MenuDeleteImpact'];
export type RestoreMenusResult = components['schemas']['RestoreMenusResult'];
