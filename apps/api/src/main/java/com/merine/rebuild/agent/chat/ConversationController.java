package com.merine.rebuild.agent.chat;

import com.merine.rebuild.agent.chat.dto.ConversationMessageView;
import com.merine.rebuild.agent.chat.dto.ConversationRequests;
import com.merine.rebuild.agent.chat.dto.ConversationView;
import com.merine.rebuild.auth.AuthenticatedAccount;
import com.merine.rebuild.common.ApiResponse;
import com.merine.rebuild.common.PageResult;
import com.merine.rebuild.system.security.PermissionCodes;
import com.merine.rebuild.system.security.PermissionGuard;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 我的助手会话：列表、消息、重命名、删除。
 *
 * <p>可见性只有一条规则——会话归属用户本人，因此没有「查看他人会话」的参数；
 * 不属于本人的会话统一按 404 处理，不暴露存在性。权限与对话一致（{@code agent:chat:use}）。
 */
@RestController
@RequestMapping("/api/agent/conversations")
@Tag(name = "海防助手 · 会话", description = "只返回归属用户自己的会话；不返回密钥与执行正文以外的内容")
final class ConversationController {
    private final ChatService service;
    private final ChatHistoryService history;
    private final PermissionGuard guard;

    ConversationController(ChatService service, ChatHistoryService history, PermissionGuard guard) {
        this.service = service;
        this.history = history;
        this.guard = guard;
    }

    @GetMapping
    @Operation(summary = "列出我的助手会话（最近更新在前）")
    ApiResponse<PageResult<ConversationView>> list(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int pageSize,
            Authentication authentication, HttpServletRequest request) {
        AuthenticatedAccount account = require(authentication);
        return ApiResponse.success(history.list(account.userId(), page, pageSize), request);
    }

    @GetMapping("/{id}/messages")
    @Operation(summary = "读取我的某个会话的消息")
    ApiResponse<List<ConversationMessageView>> messages(
            @PathVariable String id, Authentication authentication, HttpServletRequest request) {
        AuthenticatedAccount account = require(authentication);
        return ApiResponse.success(history.messages(account.userId(), id), request);
    }

    @PatchMapping("/{id}")
    @Operation(summary = "重命名我的会话")
    ApiResponse<ConversationView> rename(
            @PathVariable String id, @Valid @RequestBody ConversationRequests.Rename input,
            Authentication authentication, HttpServletRequest request) {
        AuthenticatedAccount account = require(authentication);
        return ApiResponse.success(history.rename(account.userId(), id, input.title(), input.version()),
                request);
    }

    /** 该会话还有正在生成的回答时拒绝删除，避免回答写进已删除的会话。 */
    @DeleteMapping("/{id}")
    @Operation(summary = "删除我的会话")
    ApiResponse<Void> delete(@PathVariable String id, @RequestParam @Min(0) int version,
                             Authentication authentication, HttpServletRequest request) {
        AuthenticatedAccount account = require(authentication);
        service.requireNoActiveRun(account.userId(), id);
        history.delete(account.userId(), id, version);
        return ApiResponse.success(null, request);
    }

    private AuthenticatedAccount require(Authentication authentication) {
        AuthenticatedAccount account = ChatIdentity.require(authentication);
        guard.require(authentication, PermissionCodes.CHAT_USE, "没有使用海防助手的权限");
        return account;
    }
}
