import type { MenuNode, RoleListItem } from '@merine/api-contract';
import { ApiError } from '../../shared/http.ts';

/** 每页条数：与后端默认值一致，后端上限 100。 */
export const ROLE_PAGE_SIZE = 20;

/** 角色状态筛选：'' 表示全部，其余取值与后端 ENABLED / DISABLED 一致。 */
export type RoleStatusFilter = '' | 'ENABLED' | 'DISABLED';

/** 查询条件（不含分页），'' 表示不限。 */
export interface RoleFilters {
  keyword: string;
  status: RoleStatusFilter;
}

/** 服务端分页查询，page 从 1 开始。 */
export interface RoleListQuery extends RoleFilters {
  page: number;
  pageSize: number;
}

export const EMPTY_ROLE_FILTERS: RoleFilters = { keyword: '', status: '' };

/** 条件变化一律回到第一页，避免把旧页码带到新结果上。 */
export function toRoleListQuery(filters: RoleFilters, page = 1): RoleListQuery {
  return { ...filters, page, pageSize: ROLE_PAGE_SIZE };
}

export function isRoleEnabled(status: RoleListItem['status']): boolean {
  return status === 'ENABLED';
}

export function isRoleNotFoundError(error: unknown): boolean {
  return error instanceof ApiError && (error.status === 404 || error.code === 'ROLE_NOT_FOUND');
}

export function isPageOutOfRangeError(error: unknown): boolean {
  return error instanceof ApiError && error.code === 'PAGE_OUT_OF_RANGE';
}

/** 角色还有成员在用时后端会拒绝删除；页面据此刷新列表而不是把它当普通失败。 */
export function isRoleInUseError(error: unknown): boolean {
  return error instanceof ApiError && error.code === 'ROLE_IN_USE';
}

/** 内置管理员角色的保护：不可删除、权限只读。 */
export function isBuiltinProtectedError(error: unknown): boolean {
  return error instanceof ApiError && error.code === 'ROLE_BUILTIN_PROTECTED';
}

/**
 * 删除按钮能不能用。返回原因文案而不是布尔值：界面必须写出「为什么不能删」，
 * 而不是禁用后只留一个没有理由的按钮，或者允许点下去再失败。
 * （权限导致的不可用是另一回事：那种按钮直接不渲染。）
 */
export function deleteBlockedReason(
  role: Pick<RoleListItem, 'builtin' | 'userCount'>,
): string | null {
  if (role.builtin) return '内置管理员角色不能删除';
  if (role.userCount > 0) return `还有 ${role.userCount} 个账号在用，不能删除`;
  return null;
}

/** 当前登录人是否持有这个角色：保存前提示「你自己也需要重新登录」。 */
export function holdsRole(myRoleCodes: readonly string[] | undefined, roleCode: string): boolean {
  return myRoleCodes?.includes(roleCode) ?? false;
}

/* ============================== 权限勾选树 ============================== */

/**
 * 勾选树节点：目录没有权限码（key 用菜单 id），页面/页签/按钮各带一个权限码。
 * 纯数据结构，渲染与交互留在组件里。
 */
export interface PermissionNode {
  key: string;
  name: string;
  code: string | null;
  status: string;
  children?: PermissionNode[];
}

export function toPermissionNodes(nodes: readonly MenuNode[]): PermissionNode[] {
  return nodes.map((node) => ({
    key: node.permissionCode ?? `menu:${node.id}`,
    name: node.name,
    code: node.permissionCode ?? null,
    status: node.status,
    children: node.children.length > 0 ? toPermissionNodes(node.children) : undefined,
  }));
}

/** 节点子树里的全部权限码（含节点自身）。勾选目录或页面时用它一次性选中整棵子树。 */
export function collectPermissionCodes(node: PermissionNode): string[] {
  const codes = node.code ? [node.code] : [];
  for (const child of node.children ?? []) codes.push(...collectPermissionCodes(child));
  return codes;
}

/**
 * 由「已选权限码集合」推导每个节点的勾选状态：全选 → checked，部分选中 → halfChecked。
 *
 * 这样「只给页面查看权限」会显示成父行半选，而不是被父子联动误显示成整页全选——
 * 回显与保存的集合必须一致，否则管理员会按错误的显示做决定。
 */
export function deriveCheckState(
  nodes: readonly PermissionNode[],
  selected: ReadonlySet<string>,
): { checked: string[]; halfChecked: string[] } {
  const checked: string[] = [];
  const halfChecked: string[] = [];
  const visit = (node: PermissionNode) => {
    const codes = collectPermissionCodes(node);
    const chosen = codes.filter((code) => selected.has(code)).length;
    if (codes.length > 0) {
      if (chosen === codes.length) checked.push(node.key);
      else if (chosen > 0) halfChecked.push(node.key);
    }
    node.children?.forEach(visit);
  };
  nodes.forEach(visit);
  return { checked, halfChecked };
}

/* ============================== 字段错误 ============================== */

/** 抽屉表单字段名，与后端请求字段一致。 */
export type RoleFormField = 'code' | 'name' | 'description' | 'permissionCodes';

const FORM_FIELDS: readonly string[] = ['code', 'name', 'description', 'permissionCodes'];

/**
 * 把后端 fieldErrors 映射到表单字段。
 * 字段名可能是 permissionCodes[0] 这类带下标的形式，去掉下标后再匹配；
 * 认不出的字段返回给调用方展示为整体提示，不静默丢掉。
 */
export function toRoleFormFieldErrors(fieldErrors: readonly { field: string; message: string }[]): {
  fields: Partial<Record<RoleFormField, string>>;
  rest: string[];
} {
  const fields: Partial<Record<RoleFormField, string>> = {};
  const rest: string[] = [];
  for (const { field, message } of fieldErrors) {
    const name = field.replace(/\[\d+\]$/, '');
    if (FORM_FIELDS.includes(name)) {
      fields[name as RoleFormField] ??= message;
    } else {
      rest.push(message);
    }
  }
  return { fields, rest };
}
