package com.merine.rebuild.intelligence;

import com.merine.rebuild.common.ApiResponse;
import com.merine.rebuild.common.PageResult;
import com.merine.rebuild.intelligence.dto.IntelligenceRequests.*;
import com.merine.rebuild.intelligence.dto.IntelligenceViews.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/intelligence-topics")
@Tag(name="信息流转",description="情报共享、签收和线索反馈")
public class IntelligenceController {
    private final IntelligenceService service;
    public IntelligenceController(IntelligenceService service) {this.service=service;}
    @GetMapping
    @Operation(summary="查询本单位收到或发出的情报，不扩大数据范围")
    public ApiResponse<PageResult<IntelligenceListItem>> list(Authentication auth,
        @RequestParam(defaultValue="received") String view,@RequestParam(defaultValue="all") String status,
        @RequestParam(required=false) String keyword,@RequestParam(defaultValue="1") int page,
        @RequestParam(defaultValue="20") int pageSize,HttpServletRequest request) {
        return ApiResponse.success(service.list(auth,view,status,keyword,page,pageSize),request);
    }
    @GetMapping("/{id}")
    @Operation(summary="查看情报及当前单位可见的流转历史")
    public ApiResponse<IntelligenceDetail> detail(Authentication auth,@PathVariable String id,HttpServletRequest request) {
        return ApiResponse.success(service.detail(auth,IntelligenceService.parseId(id)),request);
    }
    @GetMapping("/unit-options")
    public ApiResponse<List<IntelligenceUnitOption>> options(Authentication auth,@RequestParam String action,
        @RequestParam(required=false) String topicId,@RequestParam(required=false) String receiptId,HttpServletRequest request) {
        return ApiResponse.success(service.options(auth,action,topicId==null?null:IntelligenceService.parseId(topicId),receiptId==null?null:IntelligenceService.parseId(receiptId)),request);
    }
    @PostMapping
    public ApiResponse<IntelligenceDetail> create(Authentication auth,@RequestHeader("Idempotency-Key") String key,
        @Valid @RequestBody IntelligenceDraftRequest input,HttpServletRequest request) {
        return ApiResponse.success(service.create(auth,key,input),request);
    }
    @PutMapping("/{id}")
    public ApiResponse<IntelligenceDetail> update(Authentication auth,@PathVariable String id,@RequestHeader("Idempotency-Key") String key,
        @Valid @RequestBody IntelligenceDraftRequest input,HttpServletRequest request) {
        return ApiResponse.success(service.update(auth,IntelligenceService.parseId(id),key,input),request);
    }
    @PostMapping("/{id}/send")
    public ApiResponse<IntelligenceDetail> send(Authentication auth,@PathVariable String id,@RequestHeader("Idempotency-Key") String key,
        @Valid @RequestBody IntelligenceSendRequest input,HttpServletRequest request) {
        return ApiResponse.success(service.send(auth,IntelligenceService.parseId(id),null,key,input),request);
    }
    @PostMapping("/{id}/receipts/{receiptId}/forward")
    public ApiResponse<IntelligenceDetail> forward(Authentication auth,@PathVariable String id,@PathVariable String receiptId,
        @RequestHeader("Idempotency-Key") String key,@Valid @RequestBody IntelligenceSendRequest input,HttpServletRequest request) {
        return ApiResponse.success(service.send(auth,IntelligenceService.parseId(id),IntelligenceService.parseId(receiptId),key,input),request);
    }
    @PostMapping("/{id}/receipts/{receiptId}/sign")
    public ApiResponse<IntelligenceDetail> sign(Authentication auth,@PathVariable String id,@PathVariable String receiptId,
        @RequestHeader("Idempotency-Key") String key,HttpServletRequest request) {
        return ApiResponse.success(service.receiptAction(auth,IntelligenceService.parseId(id),IntelligenceService.parseId(receiptId),"sign",key,null),request);
    }
    @PostMapping("/{id}/receipts/{receiptId}/feedbacks")
    public ApiResponse<IntelligenceDetail> feedback(Authentication auth,@PathVariable String id,@PathVariable String receiptId,
        @RequestHeader("Idempotency-Key") String key,@Valid @RequestBody IntelligenceFeedbackRequest input,HttpServletRequest request) {
        return ApiResponse.success(service.receiptAction(auth,IntelligenceService.parseId(id),IntelligenceService.parseId(receiptId),"feedbacks",key,input),request);
    }
    @PostMapping("/{id}/supplements")
    public ApiResponse<IntelligenceDetail> supplement(Authentication auth,@PathVariable String id,
        @RequestHeader("Idempotency-Key") String key,@Valid @RequestBody IntelligenceSupplementRequest input,HttpServletRequest request) {
        return ApiResponse.success(service.supplement(auth,IntelligenceService.parseId(id),key,input),request);
    }
}
