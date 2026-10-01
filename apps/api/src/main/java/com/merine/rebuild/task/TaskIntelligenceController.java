package com.merine.rebuild.task;

import com.merine.rebuild.common.ApiResponse;
import com.merine.rebuild.common.PageResult;
import com.merine.rebuild.task.dto.TaskIntelligenceRequests.CreateIntelligenceTask;
import com.merine.rebuild.task.dto.TaskIntelligenceViews.IntelligenceTaskContext;
import com.merine.rebuild.task.dto.TaskIntelligenceViews.LinkedIntelligenceTask;
import com.merine.rebuild.task.dto.TaskIntelligenceViews.TaskIntelligenceSource;
import com.merine.rebuild.task.dto.TaskViews;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TaskIntelligenceController {
    private final TaskIntelligenceService service;

    public TaskIntelligenceController(TaskIntelligenceService service) { this.service = service; }

    @GetMapping("/api/intelligence-topics/{id}/task-context")
    public ApiResponse<IntelligenceTaskContext> context(Authentication auth, @PathVariable String id,
                                                        HttpServletRequest request) {
        return ApiResponse.success(service.context(auth, TaskService.parseId(id)), request);
    }

    @PostMapping("/api/intelligence-topics/{id}/tasks")
    public ApiResponse<TaskViews.TaskDetail> create(Authentication auth, @PathVariable String id,
            @RequestHeader("Idempotency-Key") String key, @Valid @RequestBody CreateIntelligenceTask input,
            HttpServletRequest request) {
        return ApiResponse.success(service.create(auth, TaskService.parseId(id), key, input), request);
    }

    @GetMapping("/api/intelligence-topics/{id}/tasks")
    public ApiResponse<PageResult<LinkedIntelligenceTask>> list(Authentication auth, @PathVariable String id,
            @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int pageSize,
            HttpServletRequest request) {
        return ApiResponse.success(service.list(auth, TaskService.parseId(id), page, pageSize), request);
    }

    @GetMapping("/api/tasks/{id}/intelligence-source")
    public ApiResponse<TaskIntelligenceSource> source(Authentication auth, @PathVariable String id,
                                                      HttpServletRequest request) {
        return ApiResponse.success(service.source(auth, TaskService.parseId(id)), request);
    }

}
