package com.merine.rebuild.common;

import io.swagger.v3.oas.annotations.Operation;
import org.springdoc.core.customizers.GlobalOperationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

/** 同时作用于全量和分组文档，避免新增 Controller 引起接口编号变化。 */
@Configuration
public class OpenApiConfig {
    @Bean
    GlobalOperationCustomizer stableOperationIds() {
        return (operation, handler) -> {
            Operation annotation = handler.getMethodAnnotation(Operation.class);
            if (annotation != null && !annotation.operationId().isBlank()) {
                operation.setOperationId(annotation.operationId());
            } else {
                String controller = handler.getBeanType().getSimpleName().replaceFirst("Controller$", "");
                operation.setOperationId(StringUtils.uncapitalize(controller)
                        + StringUtils.capitalize(handler.getMethod().getName()));
            }
            return operation;
        };
    }
}
