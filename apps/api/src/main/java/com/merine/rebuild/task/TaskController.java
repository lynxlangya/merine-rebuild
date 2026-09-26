package com.merine.rebuild.task;

import com.merine.rebuild.common.ApiResponse;
import com.merine.rebuild.common.PageResult;
import com.merine.rebuild.task.dto.TaskRequests;
import com.merine.rebuild.task.dto.TaskViews;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tasks")
@Tag(name = "任务处置", description = "直属下发、承办结果与支队交接")
public class TaskController {
    private final TaskService service;

    public TaskController(TaskService service) { this.service = service; }

    @GetMapping
    @Operation(summary = "按当前单位权限与参与范围查询任务")
    public ApiResponse<PageResult<TaskViews.TaskListItem>> list(Authentication auth,
            @RequestParam(defaultValue = "all") String tab,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize, HttpServletRequest request) {
        return ApiResponse.success(service.list(auth, tab, page, pageSize), request);
    }

    @GetMapping("/{id}")
    @Operation(summary = "查询任务详情和可见分支")
    public ApiResponse<TaskViews.TaskDetail> detail(Authentication auth, @PathVariable String id,
            HttpServletRequest request) {
        return ApiResponse.success(service.detail(auth, TaskService.parseId(id)), request);
    }

    @GetMapping("/target-units")
    @Operation(summary = "查询当前动作可选的直属下级或同级支队")
    public ApiResponse<List<TaskViews.UnitOption>> targets(Authentication auth,
            @RequestParam String action, @RequestParam(required = false) String taskId,
            @RequestParam(required = false) String branchId, HttpServletRequest request) {
        return ApiResponse.success(service.targets(auth, action,
                taskId == null ? null : TaskService.parseId(taskId),
                branchId == null ? null : TaskService.parseId(branchId)), request);
    }

    @PostMapping
    @Operation(summary = "创建任务并向多个直属下级下发")
    public ApiResponse<TaskViews.TaskDetail> create(Authentication auth,
            @RequestHeader("Idempotency-Key") String key, @Valid @RequestBody TaskRequests.Create input,
            HttpServletRequest request) {
        return ApiResponse.success(service.create(auth, key, input), request);
    }

    @PostMapping("/{id}/branches/{branchId}/accept")
    public ApiResponse<TaskViews.TaskDetail> accept(Authentication auth, @PathVariable String id,
            @PathVariable String branchId, @RequestHeader("Idempotency-Key") String key,
            HttpServletRequest request) {
        return ApiResponse.success(service.accept(auth, TaskService.parseId(id), TaskService.parseId(branchId), key), request);
    }

    @PostMapping("/{id}/branches/{branchId}/progress")
    public ApiResponse<TaskViews.TaskDetail> progress(Authentication auth, @PathVariable String id,
            @PathVariable String branchId, @RequestHeader("Idempotency-Key") String key,
            @Valid @RequestBody TaskRequests.Progress input, HttpServletRequest request) {
        return ApiResponse.success(service.progress(auth, TaskService.parseId(id), TaskService.parseId(branchId), key, input), request);
    }

    @PostMapping("/{id}/branches/{branchId}/return")
    public ApiResponse<TaskViews.TaskDetail> returnTask(Authentication auth, @PathVariable String id,
            @PathVariable String branchId, @RequestHeader("Idempotency-Key") String key,
            @Valid @RequestBody TaskRequests.ReturnTask input, HttpServletRequest request) {
        return ApiResponse.success(service.returnTask(auth, TaskService.parseId(id), TaskService.parseId(branchId), key, input), request);
    }

    @PostMapping("/{id}/branches/{branchId}/dispatch")
    public ApiResponse<TaskViews.TaskDetail> dispatch(Authentication auth, @PathVariable String id,
            @PathVariable String branchId, @RequestHeader("Idempotency-Key") String key,
            @Valid @RequestBody TaskRequests.Dispatch input, HttpServletRequest request) {
        return ApiResponse.success(service.dispatch(auth, TaskService.parseId(id), TaskService.parseId(branchId), key, input, false), request);
    }

    @PostMapping("/{id}/branches/{branchId}/reassign")
    public ApiResponse<TaskViews.TaskDetail> reassign(Authentication auth, @PathVariable String id,
            @PathVariable String branchId, @RequestHeader("Idempotency-Key") String key,
            @Valid @RequestBody TaskRequests.Dispatch input, HttpServletRequest request) {
        return ApiResponse.success(service.dispatch(auth, TaskService.parseId(id), TaskService.parseId(branchId), key, input, true), request);
    }

    @PostMapping("/{id}/branches/{branchId}/results")
    public ApiResponse<TaskViews.TaskDetail> result(Authentication auth, @PathVariable String id,
            @PathVariable String branchId, @RequestHeader("Idempotency-Key") String key,
            @Valid @RequestBody TaskRequests.SubmitResult input, HttpServletRequest request) {
        return ApiResponse.success(service.submitResult(auth, TaskService.parseId(id), TaskService.parseId(branchId), key, input), request);
    }

    @PostMapping("/{id}/branches/{branchId}/transfer-requests")
    public ApiResponse<TaskViews.TaskDetail> transfer(Authentication auth, @PathVariable String id,
            @PathVariable String branchId, @RequestHeader("Idempotency-Key") String key,
            @Valid @RequestBody TaskRequests.TransferRequest input, HttpServletRequest request) {
        return ApiResponse.success(service.requestTransfer(auth, TaskService.parseId(id), TaskService.parseId(branchId), key, input), request);
    }

    @PostMapping("/{id}/branches/{branchId}/transfer-requests/{transferId}/respond")
    public ApiResponse<TaskViews.TaskDetail> respond(Authentication auth, @PathVariable String id,
            @PathVariable String branchId, @PathVariable String transferId,
            @RequestHeader("Idempotency-Key") String key,
            @Valid @RequestBody TaskRequests.TransferResponse input, HttpServletRequest request) {
        return ApiResponse.success(service.respondTransfer(auth, TaskService.parseId(id), TaskService.parseId(branchId),
                TaskService.parseId(transferId), key, input), request);
    }

    @PostMapping("/{id}/branches/{branchId}/transfer-requests/{transferId}/decide")
    public ApiResponse<TaskViews.TaskDetail> decide(Authentication auth, @PathVariable String id,
            @PathVariable String branchId, @PathVariable String transferId,
            @RequestHeader("Idempotency-Key") String key,
            @Valid @RequestBody TaskRequests.TransferDecision input, HttpServletRequest request) {
        return ApiResponse.success(service.decideTransfer(auth, TaskService.parseId(id), TaskService.parseId(branchId),
                TaskService.parseId(transferId), key, input), request);
    }

    @PostMapping("/{id}/branches/{branchId}/transfer-requests/{transferId}/withdraw")
    public ApiResponse<TaskViews.TaskDetail> withdraw(Authentication auth, @PathVariable String id,
            @PathVariable String branchId, @PathVariable String transferId,
            @RequestHeader("Idempotency-Key") String key, HttpServletRequest request) {
        return ApiResponse.success(service.withdrawTransfer(auth, TaskService.parseId(id), TaskService.parseId(branchId),
                TaskService.parseId(transferId), key), request);
    }
}
