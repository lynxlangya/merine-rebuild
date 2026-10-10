package com.merine.rebuild.agent.chat;

import com.merine.rebuild.agent.chat.dto.ChatEvent;
import com.merine.rebuild.agent.chat.dto.ChatRequest;
import com.merine.rebuild.agent.chat.dto.ChatRunRecordView;
import com.merine.rebuild.agent.chat.dto.ChatRunStats;
import com.merine.rebuild.agent.chat.dto.ChatRunView;
import com.merine.rebuild.agent.chat.dto.ChatRuntimeView;
import com.merine.rebuild.agent.chat.dto.ConversationMessageView;
import com.merine.rebuild.agent.chat.dto.ConversationRequests;
import com.merine.rebuild.agent.chat.dto.ConversationView;
import com.merine.rebuild.common.PageResult;
import com.merine.rebuild.auth.AuthenticatedAccount;
import com.merine.rebuild.common.ApiException;
import com.merine.rebuild.common.ApiResponse;
import com.merine.rebuild.system.security.PermissionCodes;
import com.merine.rebuild.system.security.PermissionGuard;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 对话端点：发起/续接执行、查询状态、停止、列出我的执行记录。
 *
 * <p>全部要求 {@code agent:chat:use}；执行归属按会话身份校验，跨用户一律按不存在处理。
 * 运行模式由 {@code merine.agent.chat.mode} 决定（PROVIDER 调真实模型、DEMO 用本地替身），
 * 一轮结束后由 {@link ChatHistoryService} 落库。会话本身的接口在 {@link ConversationController}。
 */
@RestController
@RequestMapping("/api/agent/chat")
@Tag(name = "海防助手 · 对话", description = "流式执行、停止与执行记录；一轮结束后落库")
final class ChatController {

    private final ChatService service;
    private final ChatHistoryService history;
    private final ChatProperties props;
    private final PermissionGuard guard;

    ChatController(ChatService service, ChatHistoryService history, ChatProperties props,
                   PermissionGuard guard) {
        this.service = service;
        this.history = history;
        this.props = props;
        this.guard = guard;
    }

    @GetMapping("/config")
    @Operation(summary = "读取对话运行模式，用于界面标注本地演示或真实模型")
    ApiResponse<ChatRuntimeView> config(HttpServletRequest request) {
        ChatEvent.ExecutionMode mode = props.mode() == ChatProperties.ChatMode.DEMO
                ? ChatEvent.ExecutionMode.LOCAL_STUB
                : ChatEvent.ExecutionMode.PROVIDER;
        return ApiResponse.success(new ChatRuntimeView(mode), request);
    }

    /**
     * 首次执行返回 SSE；同 key 正在进行返回 409；同 key 已结束返回原结果快照（JSON）；
     * 同 key 不同有效命令返回 409。响应形态见设计稿 §4.1。
     */
    @PostMapping(produces = {MediaType.TEXT_EVENT_STREAM_VALUE, MediaType.APPLICATION_JSON_VALUE})
    @Operation(summary = "发起或续接一次本地演示执行")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "SSE 事件流，或同 key 已完成时的快照 JSON",
            content = {
                    @Content(mediaType = MediaType.TEXT_EVENT_STREAM_VALUE,
                            schema = @Schema(implementation = ChatEvent.class)),
                    @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ChatRunView.class))
            })
    ResponseEntity<?> chat(@Valid @RequestBody ChatRequest request, Authentication authentication,
                           HttpServletRequest http) {
        AuthenticatedAccount account = ChatIdentity.require(authentication);
        guard.require(authentication, PermissionCodes.CHAT_USE, "没有使用海防助手的权限");
        ChatStart start = service.start(request, account);
        if (start instanceof ChatStart.Stream stream) {
            return ResponseEntity.ok().contentType(MediaType.TEXT_EVENT_STREAM).body(stream.emitter());
        }
        // 同 key 已完成：显式声明 JSON，否则会按 Accept 顺序协商成 text/event-stream。
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(ApiResponse.success(((ChatStart.Snapshot) start).view(), http));
    }

    /**
     * 我的会话列表：只返回本人会话，最近更新在前。
     * 会话与执行记录都只对归属用户可见，因此这里不接受任何「查看他人」的参数。
     */
    @GetMapping("/conversations")
    @Operation(summary = "列出我的助手会话")
    ApiResponse<PageResult<ConversationView>> conversations(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int pageSize,
            Authentication authentication, HttpServletRequest request) {
        AuthenticatedAccount account = ChatIdentity.require(authentication);
        guard.require(authentication, PermissionCodes.CHAT_USE, "没有使用海防助手的权限");
        return ApiResponse.success(history.list(account.userId(), page, pageSize), request);
    }

    @GetMapping("/conversations/{id}/messages")
    @Operation(summary = "读取我的某个会话的消息")
    ApiResponse<List<ConversationMessageView>> conversationMessages(
            @PathVariable String id, Authentication authentication, HttpServletRequest request) {
        AuthenticatedAccount account = ChatIdentity.require(authentication);
        guard.require(authentication, PermissionCodes.CHAT_USE, "没有使用海防助手的权限");
        return ApiResponse.success(history.messages(account.userId(), id), request);
    }

    @PatchMapping("/conversations/{id}")
    @Operation(summary = "重命名我的会话")
    ApiResponse<ConversationView> renameConversation(
            @PathVariable String id, @Valid @RequestBody ConversationRequests.Rename input,
            Authentication authentication, HttpServletRequest request) {
        AuthenticatedAccount account = ChatIdentity.require(authentication);
        guard.require(authentication, PermissionCodes.CHAT_USE, "没有使用海防助手的权限");
        return ApiResponse.success(history.rename(account.userId(), id, input.title(), input.version()),
                request);
    }

    @DeleteMapping("/conversations/{id}")
    @Operation(summary = "删除我的会话（进行中的轮次会先被拒绝）")
    ApiResponse<Void> deleteConversation(@PathVariable String id,
                                         @RequestParam @Min(0) int version,
                                         Authentication authentication, HttpServletRequest request) {
        AuthenticatedAccount account = ChatIdentity.require(authentication);
        guard.require(authentication, PermissionCodes.CHAT_USE, "没有使用海防助手的权限");
        service.requireNoActiveRun(account.userId(), id);
        history.delete(account.userId(), id, version);
        return ApiResponse.success(null, request);
    }

    /** 我的执行记录：用量、耗时与失败原因；筛选维度与用量聚合一致（`days` 可选，缺省不限时间）。 */
    @GetMapping("/runs")
    @Operation(summary = "列出我的助手执行记录")
    ApiResponse<PageResult<ChatRunRecordView>> runs(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int pageSize,
            @RequestParam(defaultValue = "") @Size(max = 16) String state,
            @RequestParam(defaultValue = "") @Size(max = 64) String providerId,
            @RequestParam(defaultValue = "") @Size(max = 120) String modelId,
            @RequestParam(required = false) @Min(1) @Max(90) Integer days,
            Authentication authentication, HttpServletRequest request) {
        AuthenticatedAccount account = ChatIdentity.require(authentication);
        guard.require(authentication, PermissionCodes.CHAT_USE, "没有使用海防助手的权限");
        if (!state.isBlank() && !RUN_STATES.contains(state)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PARAMETER",
                    "状态取值不在允许范围内");
        }
        return ApiResponse.success(
                history.runs(account.userId(), state.trim(), providerId, modelId, days, page,
                        pageSize),
                request);
    }

    /** 用量聚合：KPI、按天趋势与模型分布。`offsetMinutes` 是客户端本地偏移，用于按本地日期分桶。 */
    @GetMapping("/runs/stats")
    @Operation(summary = "我的助手用量聚合（KPI、按天趋势、模型分布）")
    ApiResponse<ChatRunStats> runStats(
            @RequestParam(defaultValue = "14") @Min(1) @Max(90) int days,
            @RequestParam(defaultValue = "") @Size(max = 64) String providerId,
            @RequestParam(defaultValue = "") @Size(max = 120) String modelId,
            @RequestParam(defaultValue = "0") @Min(-720) @Max(840) int offsetMinutes,
            Authentication authentication, HttpServletRequest request) {
        AuthenticatedAccount account = ChatIdentity.require(authentication);
        guard.require(authentication, PermissionCodes.CHAT_USE, "没有使用海防助手的权限");
        return ApiResponse.success(
                history.stats(account.userId(), days, providerId, modelId, offsetMinutes), request);
    }

    private static final java.util.Set<String> RUN_STATES =
            java.util.Set.of("SUCCEEDED", "AWAITING_INPUT", "FAILED", "ABORTED");

    @GetMapping("/requests/{idempotencyKey}")
    @Operation(summary = "按幂等键查询当前身份的原执行状态与受限快照")
    ApiResponse<ChatRunView> status(@PathVariable String idempotencyKey, Authentication authentication,
                                    HttpServletRequest http) {
        AuthenticatedAccount account = ChatIdentity.require(authentication);
        guard.require(authentication, PermissionCodes.CHAT_USE, "没有使用海防助手的权限");
        ChatRun run = service.findOwned(account.userId(), idempotencyKey);
        if (run == null) {
            throw notFound();
        }
        return ApiResponse.success(run.view(), http);
    }

    /**
     * 停止请求只设置标记，由执行线程在下一个检查点确认：先写 {@code CANCEL_REQUESTED}，
     * 再以 {@code MESSAGE_DONE(ABORTED)} 收尾。终态之后调用返回 200 当前快照。
     */
    @PostMapping("/requests/{idempotencyKey}/stop")
    @Operation(summary = "请求停止本次演示执行")
    ResponseEntity<ApiResponse<ChatRunView>> stop(@PathVariable String idempotencyKey,
                                                  Authentication authentication,
                                                  HttpServletRequest http) {
        AuthenticatedAccount account = ChatIdentity.require(authentication);
        guard.require(authentication, PermissionCodes.CHAT_USE, "没有使用海防助手的权限");
        ChatRun run = service.findOwned(account.userId(), idempotencyKey);
        if (run == null) {
            throw notFound();
        }
        if (run.state() == ChatRunView.ChatRunState.RUNNING) {
            run.requestCancel();
            return ResponseEntity.accepted().body(ApiResponse.success(run.view(), http));
        }
        return ResponseEntity.ok(ApiResponse.success(run.view(), http));
    }

    private static ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "CHAT_REQUEST_NOT_FOUND",
                "没有找到该执行，可能已过期或从未提交");
    }
}
