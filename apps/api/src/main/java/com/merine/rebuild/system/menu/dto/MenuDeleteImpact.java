package com.merine.rebuild.system.menu.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 删除菜单节点的影响（既是预览结果，也是删除后的回执）。
 * 前端在确认弹窗里必须写出这两个数字，避免「以为只删一个按钮，实际删掉整棵子树」。
 */
public record MenuDeleteImpact(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "将删除/已删除的节点数（含子树）")
        int deletedNodes,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "将失去授权/已失去授权的角色数")
        int affectedRoles) {
}
