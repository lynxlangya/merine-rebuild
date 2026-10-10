package com.merine.rebuild.system.audit;

import com.merine.rebuild.auth.AuthenticatedAccount;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import com.merine.rebuild.system.audit.persistence.AuditLogMapper;
import com.merine.rebuild.system.audit.persistence.AuditLogRow;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 审计写入的唯一入口。
 *
 * <p>调用点放在**业务写入成功之后、同一事务内**：写不进审计就让业务一起回滚，
 * 不会出现「改了但没记住」。因此这里不吞异常、不做异步。
 *
 * <p>操作者信息（登录名、姓名、单位）取登录时下发的会话快照，运行期不查库；
 * 登录失败等没有身份的场景传 null，只用 {@link #recordAnonymous} 记尝试的登录名与 IP。
 */
@Component
public class AuditTrail {

    private static final Logger log = LoggerFactory.getLogger(AuditTrail.class);

    private final AuditLogMapper mapper;

    AuditTrail(AuditLogMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * 记录一次当前会话发起的操作：操作者从安全上下文取，请求上下文取 requestId 与 IP。
     *
     * <p>业务服务用这个入口，不必把 HttpServletRequest 一路传进服务层。
     * 取不到身份时按「未知身份」记录并打警告——系统写操作都应该有身份，缺了就是埋点接错了。
     */
    public void recordCurrent(AuditEvent event) {
        AuthenticatedAccount actor = currentActor();
        if (actor == null) {
            log.warn("Audit event without authenticated actor: module={} action={}", event.module(),
                    event.action());
        }
        record(actor, event, currentRequest());
    }

    private static AuthenticatedAccount currentActor() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.getPrincipal() instanceof AuthenticatedAccount account
                ? account
                : null;
    }

    private static HttpServletRequest currentRequest() {
        return RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes
                ? attributes.getRequest()
                : null;
    }

    /** 记录一次已成功的操作；`request` 用于取请求 id 与客户端 IP（可为 null）。 */
    public void record(AuthenticatedAccount actor, AuditEvent event, HttpServletRequest request) {
        mapper.insert(row(actor, event, request));
    }

    /** 无身份的失败事件（目前只有登录失败）：没有 actor_user_id，用尝试的登录名留痕。 */
    public void recordAnonymous(String actorLogin, AuditEvent event, HttpServletRequest request) {
        AuditLogRow base = row(null, event, request);
        mapper.insert(new AuditLogRow(base.id(), base.occurredAt(), null,
                safeAscii(actorLogin, 64),
                "", "", base.module(), base.action(), base.result(), base.targetType(),
                base.targetId(), base.targetLabel(), base.summary(), base.requestId(), base.clientIp()));
    }

    private static AuditLogRow row(AuthenticatedAccount actor, AuditEvent event,
                                   HttpServletRequest request) {
        return new AuditLogRow(0L, Instant.now(),
                actor == null ? null : actor.userId(),
                actor == null ? "" : safeAscii(actor.loginName(), 64),
                actor == null ? "" : safe(actor.displayName(), 80),
                actor == null ? "" : safe(actor.unitName(), 80),
                safeAscii(event.module(), 32), safeAscii(event.action(), 64), event.result(),
                safeAscii(event.targetType(), 32), safeAscii(event.targetId(), 64),
                safe(event.targetLabel(), 160), safe(event.summary(), 300),
                requestId(request), AuditClientIp.of(request));
    }

    /** 请求 id 由日志过滤器写入请求属性；缺失时留空，不影响审计本身。 */
    private static String requestId(HttpServletRequest request) {
        if (request == null) return "";
        Object value = request.getAttribute("requestId");
        return value == null ? "" : safeAscii(value.toString(), 64);
    }

    /**
     * ASCII 列的净化：登录名来自用户输入，可能是中文或带控制字符；
     * 直接写进 ascii 列会让「登录失败」本身变成 500。可打印 ASCII 之外的字符统一替换为 `?`。
     * 中文姓名、单位名与摘要走 utf8mb4 列，不受这里影响。
     */
    private static String safeAscii(String value, int max) {
        String trimmed = safe(value, max);
        StringBuilder builder = new StringBuilder(trimmed.length());
        for (int index = 0; index < trimmed.length(); index += 1) {
            char current = trimmed.charAt(index);
            builder.append(current >= 32 && current < 127 ? current : '?');
        }
        return builder.length() <= max ? builder.toString() : builder.substring(0, max);
    }

    private static String safe(String value, int max) {
        if (value == null) return "";
        String trimmed = value.trim();
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max);
    }
}
