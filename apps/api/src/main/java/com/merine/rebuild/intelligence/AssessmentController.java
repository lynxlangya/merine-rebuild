package com.merine.rebuild.intelligence;

import com.merine.rebuild.common.ApiResponse;
import com.merine.rebuild.common.PageResult;
import com.merine.rebuild.intelligence.dto.AssessmentRequests.RecordAssessment;
import com.merine.rebuild.intelligence.dto.AssessmentViews.Assessment;
import com.merine.rebuild.intelligence.dto.AssessmentViews.AssessmentContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
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
@RequestMapping("/api/intelligence-topics/{id}")
public class AssessmentController {
    private final IntelligenceTaskAccess access;
    public AssessmentController(IntelligenceTaskAccess access) { this.access = access; }

    @GetMapping("/assessment-context")
    public ApiResponse<AssessmentContext> context(Authentication auth, @PathVariable String id, HttpServletRequest request) {
        return ApiResponse.success(access.context(auth, IntelligenceService.parseId(id)),request);
    }

    @GetMapping("/assessments")
    public ApiResponse<PageResult<Assessment>> list(Authentication auth, @PathVariable String id,
        @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int pageSize, HttpServletRequest request) {
        return ApiResponse.success(access.list(auth, IntelligenceService.parseId(id), page, pageSize), request);
    }

    @PostMapping("/assessments")
    public ApiResponse<Assessment> record(Authentication auth, @PathVariable String id, @RequestHeader("Idempotency-Key") String key,
        @Valid @RequestBody RecordAssessment input, HttpServletRequest request) {
        return ApiResponse.success(access.record(auth, IntelligenceService.parseId(id), key, input), request);
    }
}
