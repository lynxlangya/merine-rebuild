package com.merine.rebuild.agent.datasource.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 连接与只读授权检查结论。只有 PASSED 允许作为启用依据，且启用时还会重新检查一次。
 *
 * <ul>
 *   <li>{@code PASSED}：连接成功，未检测到写权限，且勾选视图都有 SELECT（无勾选时只验证只读）。</li>
 *   <li>{@code CONNECTION_FAILED}：地址/密码/网络/超时等导致无法完成检查。</li>
 *   <li>{@code WRITE_PRIVILEGE}：授权元数据中存在写权限或可转授权限。</li>
 *   <li>{@code AUTHORIZATION_UNCONFIRMED}：授权信息读取不完整（如账号持有角色、元数据查询失败），无法确认只读。</li>
 *   <li>{@code VIEW_DENIED}：勾选的视图不存在或当前账号没有该视图的 SELECT 授权。</li>
 * </ul>
 */
@Schema(name = "DataSourceCheckOutcome")
public enum DataSourceCheckOutcome {
    PASSED,
    CONNECTION_FAILED,
    WRITE_PRIVILEGE,
    AUTHORIZATION_UNCONFIRMED,
    VIEW_DENIED
}
