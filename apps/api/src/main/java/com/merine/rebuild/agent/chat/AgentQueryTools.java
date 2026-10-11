package com.merine.rebuild.agent.chat;

import com.merine.rebuild.agent.chat.dto.ChatErrorCode;
import com.merine.rebuild.agent.provider.dto.ProviderRequests.Vendor;
import com.merine.rebuild.agent.semantics.SemanticMetricLookup;
import com.merine.rebuild.agent.semantics.dto.SemanticCatalogView.Metric;
import com.merine.rebuild.auth.AuthenticatedAccount;
import com.merine.rebuild.common.ApiException;
import com.merine.rebuild.system.security.PermissionCodes;
import com.merine.rebuild.system.security.PermissionGuard;
import com.merine.rebuild.system.user.account.UserAccountLookup;
import com.merine.rebuild.task.TaskStatisticsLookup;
import com.merine.rebuild.task.TaskStatisticsLookup.CompletedCount;
import com.merine.rebuild.task.TaskStatisticsLookup.Period;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.DeserializationFeature;

/** 对话内的工具白名单、参数校验和身份边界；统计与数据授权留在任务模块。 */
@Component
final class AgentQueryTools {
    static final String NAME = "query_completed_task_count";
    private static final DateTimeFormatter DISPLAY_TIME = new DateTimeFormatterBuilder()
            .appendPattern("yyyy-MM-dd HH:mm:ss").appendFraction(ChronoField.NANO_OF_SECOND, 0, 6, true)
            .toFormatter(java.util.Locale.ROOT).withZone(ZoneId.of("Asia/Shanghai"));
    private final TaskStatisticsLookup statistics;
    private final UserAccountLookup accounts;
    private final PermissionGuard guard;
    private final ObjectMapper json;
    private final ChatProperties props;
    private final Metric metric;
    private final String instructions;

    AgentQueryTools(TaskStatisticsLookup statistics, SemanticMetricLookup definitions,
                    UserAccountLookup accounts, PermissionGuard guard, ObjectMapper json,
                    ChatProperties props, @Value("classpath:prompts/agent-tools.txt") Resource resource) {
        this.statistics = statistics;
        this.accounts = accounts;
        this.guard = guard;
        this.json = json;
        this.props = props;
        this.metric = definitions.requireConfirmed("task.completed-count", 3);
        try {
            instructions = resource.getContentAsString(java.nio.charset.StandardCharsets.UTF_8).strip();
            if (instructions.isEmpty()) throw new IllegalStateException("查询工具指引不能为空");
        } catch (IOException error) {
            throw new IllegalStateException("无法加载海防助手查询工具指引", error);
        }
    }

    record QueryKey(String name, Period period) {}
    record RequestedQuery(ProviderToolCall call, Period period) {
        QueryKey key() { return new QueryKey(call.name(), period); }
    }
    record ExecutedQuery(String protocolJson, String displayText) {}
    private record QueryResult(String status, String metricId, int revision, long count, String unit,
                               String unitCode, String unitName, String scope, Period period, Instant startAt,
                               Instant endAtExclusive, String timeZone, Instant queriedAt,
                               String dataSource, String queryId) {}

    static boolean supports(Vendor vendor) { return vendor == Vendor.DEEPSEEK || vendor == Vendor.QWEN; }

    boolean available(Authentication authentication, Vendor vendor) {
        return supports(vendor) && authentication != null && authentication.isAuthenticated()
                && authentication.getAuthorities().stream().anyMatch(a -> PermissionCodes.TASK_READ.equals(a.getAuthority()));
    }

    String instructions() { return instructions; }

    List<Map<String, Object>> definitions() {
        return List.of(Map.of("type", "function", "function", Map.of(
                "name", NAME,
                "description", "查询本单位发起的办结任务数，依据已确认的 task.completed-count revision 3。后台先读取服务器当前时间，再计算北京时间范围：本月从月初至查询时刻；上月为上一完整自然月；最近一个月从查询时刻倒推一个日历月。本单位、当前时间和时间边界均由后台确定，不接受模型或客户端计算的日期。不适用于全省、其他单位、任务明细、承办任务数、分支数或任意 SQL。期间不明确时先澄清。",
                "parameters", Map.of("type", "object", "properties", Map.of("period", Map.of(
                        "type", "string", "enum", List.of("THIS_MONTH", "LAST_MONTH", "ROLLING_MONTH"),
                        "description", "THIS_MONTH 为本月截至查询时刻；LAST_MONTH 为上个完整自然月；ROLLING_MONTH 为最近/近/过去一个月，从查询时刻往前倒推一个日历月。三者不能混用。")),
                        "required", List.of("period"), "additionalProperties", false))));
    }

    /** 先校验整批计划，防止一批里混入非法调用后仍执行了前面的查询。 */
    List<RequestedQuery> validate(List<ProviderToolCall> calls) {
        if (calls.isEmpty() || calls.size() > props.maxToolCalls()) {
            throw invalid("模型请求的查询次数超过上限");
        }
        HashSet<String> ids = new HashSet<>();
        return calls.stream().map(call -> {
            if (!NAME.equals(call.name()) || call.id() == null
                    || !call.id().matches("[A-Za-z0-9._:-]{1,128}") || !ids.add(call.id())) {
                throw invalid("模型请求了未开放或无效的查询工具");
            }
            try {
                if (call.arguments() == null || call.arguments().length() > props.maxToolArgumentChars()) {
                    throw invalid("查询参数超过上限");
                }
                var value = json.reader().with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS,
                        DeserializationFeature.FAIL_ON_READING_DUP_TREE_KEY).readTree(call.arguments());
                if (!value.isObject() || value.size() != 1 || !value.path("period").isTextual()) {
                    throw invalid("查询仅接受本月、上月或最近一个月，不能提交时间、单位、身份或 SQL");
                }
                return new RequestedQuery(call, Period.valueOf(value.path("period").asText()));
            } catch (ChatUpstreamException error) {
                throw error;
            } catch (RuntimeException error) {
                throw invalid("模型返回了无效的查询参数");
            }
        }).toList();
    }

    void requireCurrentSession(Authentication authentication, HttpSession session) {
        try {
            if (session == null || authentication == null
                    || !(authentication.getPrincipal() instanceof AuthenticatedAccount account)
                    || !(session.getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY)
                        instanceof SecurityContext context)
                    || context.getAuthentication() == null || !context.getAuthentication().isAuthenticated()
                    || !(context.getAuthentication().getPrincipal() instanceof AuthenticatedAccount current)
                    || current.userId() != account.userId() || current.authorizationVersion() != account.authorizationVersion()) {
                throw expired();
            }
            var state = accounts.findStateById(account.userId());
            if (state == null || !state.enabled() || state.authorizationVersion() != account.authorizationVersion()) {
                throw expired();
            }
            guard.require(authentication, PermissionCodes.CHAT_USE, "没有使用海防助手的权限");
            guard.require(authentication, PermissionCodes.TASK_READ, "没有查看任务的权限");
        } catch (IllegalStateException invalidSession) {
            throw expired();
        }
    }

    ExecutedQuery execute(Authentication authentication, HttpSession session, RequestedQuery query) {
        Period period = query.period();
        requireCurrentSession(authentication, session);
        CompletedCount result = statistics.completedCount(authentication, period);
        requireCurrentSession(authentication, session);
        String source = "任务处置 · 发起单位确认总体办结记录";
        QueryResult response = new QueryResult("OK", metric.id(), metric.revision(), result.count(),
                metric.unit(), result.unitCode(), result.unitName(), "当前登录用户所属单位发起的任务",
                period, result.period().startAt(), result.period().endAtExclusive(), "Asia/Shanghai",
                result.queriedAt(), source, result.queryId());
        String text = "\n\n### 后台查询结果\n\n**本单位发起的办结任务数：" + result.count() + " " + metric.unit()
                + "**\n\n- 时间范围：" + periodLabel(period)
                + "\n- 统计期间：" + DISPLAY_TIME.format(response.startAt()) + " 至 " + DISPLAY_TIME.format(response.endAtExclusive())
                + "（北京时间，包含起点、不包含终点）\n- 统计范围：" + markdownLabel(result.unitName())
                + "发起并确认总体办结的整项任务\n- 口径：" + metric.id() + "，版本 " + metric.revision()
                + "，已确认\n- 数据来源：" + source + "\n- 查询时间：" + DISPLAY_TIME.format(result.queriedAt())
                + "（服务器时间，北京时间）\n- 查询标识：" + result.queryId() + "\n\n";
        return new ExecutedQuery(json.writeValueAsString(response), text);
    }

    private static String periodLabel(Period period) {
        return switch (period) {
            case THIS_MONTH -> "本月截至查询时刻";
            case LAST_MONTH -> "上月完整自然月";
            case ROLLING_MONTH -> "最近一个月（查询时刻倒推一个日历月）";
        };
    }

    private static String markdownLabel(String value) {
        StringBuilder label = new StringBuilder();
        for (char character : value.toCharArray()) {
            if ("\\`*_{}[]<>!#|".indexOf(character) >= 0) label.append('\\');
            label.append(character == '\n' || character == '\r' ? ' ' : character);
        }
        return label.toString();
    }

    private static ChatUpstreamException invalid(String message) {
        return new ChatUpstreamException(ChatErrorCode.UPSTREAM_PROTOCOL_ERROR, message, false);
    }

    private static ApiException expired() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "登录身份已失效，请重新登录后查询");
    }
}
