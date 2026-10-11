package com.merine.rebuild.agent.datasource;

import com.merine.rebuild.common.ApiException;
import io.swagger.v3.oas.annotations.Hidden;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/** 单操作员服务间桥：固定一个数据源，不接受模型指定 ID，不执行 SQL。默认关闭。 */
@Hidden
@RestController
public class AgentDataSourceBridge {
    public static final String PATH = "/internal/agent/data-source";
    private final AgentConnectionGrantService service;
    private final String token;
    private final String sourceId;
    private final String scope;

    public AgentDataSourceBridge(AgentConnectionGrantService service,
                                 @Value("${MERINE_AGENT_BRIDGE_TOKEN:}") String token,
                                 @Value("${MERINE_AGENT_DATA_SOURCE_ID:}") String sourceId,
                                 @Value("${MERINE_AGENT_QUERY_SCOPE:单操作员固定视图范围；未绑定平台用户单位}") String scope) {
        this.service = service;
        this.token = token;
        this.sourceId = sourceId;
        this.scope = scope;
    }

    @GetMapping(PATH)
    public ResponseEntity<AgentConnectionGrantService.ConnectionGrant> grant(
            @RequestHeader(value = "Authorization", defaultValue = "") String authorization) {
        if (!token.matches("[a-fA-F0-9]{64}") || sourceId.isBlank()
                || authorization.length() > 80
                || !MessageDigest.isEqual(("Bearer " + token).getBytes(StandardCharsets.UTF_8),
                        authorization.getBytes(StandardCharsets.UTF_8))) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "AGENT_BRIDGE_DENIED", "服务凭据无效或连接桥未启用");
        }
        return ResponseEntity.ok().header("Cache-Control", "no-store").header("Pragma", "no-cache")
                .body(service.grant(sourceId, scope));
    }
}
