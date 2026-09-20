/**
 * 角色 feature 的显式出口。
 *
 * 用户管理只需要「角色选项」这一项能力：筛选下拉与表单勾选都走这里，
 * 因此两个 feature 之间不互相深导入内部文件。
 */
export { useRoleOptionsQuery, roleKeys } from './queries';
