package org.jeecg.common.aspect;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.NotPermissionException;
import cn.dev33.satoken.stp.StpUtil;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterThrowing;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.jeecg.common.api.CommonAPI;
import org.jeecg.common.api.dto.DenyAuditDTO;
import org.jeecg.common.security.H7ExceptionRedactor;
import org.jeecg.common.util.IpUtils;
import org.jeecg.common.util.SpringContextUtils;
import org.jeecg.common.util.oConvertUtils;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Date;

@Aspect
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 50)
@Slf4j
public class H8DenyCallAuditAspect implements ApplicationContextAware {

    private static final String REASON_NOT_LOGIN = "H8_DENIED_NOTLOGIN";
    private static final String REASON_NOT_PERMISSION = "H8_DENIED_NOPERMISSION";
    private static final String REASON_ACCESS_DENIED = "H8_DENIED_ACCESS";

    private ApplicationContext applicationContext;
    private volatile CommonAPI commonApi;

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    @PostConstruct
    void markLoaded() {
        log.info("H8DenyCallAuditAspect loaded: AfterThrowing for NotLogin/NotPermission/AccessDenied");
    }

    @Pointcut("@annotation(org.jeecg.common.aspect.annotation.H8LoginAudit) || " +
            "@annotation(org.jeecg.common.aspect.annotation.AutoLog) || " +
            "within(org.jeecg.modules.system.controller..*)")
    public void auditPointCut() {
    }

    @AfterThrowing(pointcut = "auditPointCut()", throwing = "ex")
    public void afterThrowing(JoinPoint joinPoint, Throwable ex) {
        try {
            String reasonCode = resolveReason(ex);
            if (reasonCode == null) {
                return;
            }
            DenyAuditDTO audit = buildAuditRecord(joinPoint, ex, reasonCode);
            persistAsync(audit);
        } catch (Throwable safety) {
            log.warn("H8 deny audit record failed (swallowed)", safety);
        }
    }

    static String resolveReason(Throwable ex) {
        if (ex == null) return null;
        Throwable t = ex;
        for (int depth = 0; t != null && depth < 5; depth++) {
            if (t instanceof NotLoginException) {
                return REASON_NOT_LOGIN;
            }
            if (t instanceof NotPermissionException) {
                return REASON_NOT_PERMISSION;
            }
            String cn = t.getClass().getName();
            if (cn != null && (cn.contains("AccessDeniedException") || cn.contains("DeniedException"))) {
                return REASON_ACCESS_DENIED;
            }
            Throwable next = t.getCause();
            if (next == t) break;
            t = next;
        }
        return null;
    }

    private DenyAuditDTO buildAuditRecord(JoinPoint joinPoint, Throwable ex, String reasonCode) {
        DenyAuditDTO audit = new DenyAuditDTO();
        audit.setReasonCode(reasonCode);
        String redactedDetail = H7ExceptionRedactor.redactSummary(ex);
        audit.setReasonDetailRedacted(redactedDetail);
        audit.setCreateTime(new Date());

        try {
            String userId = null;
            if (StpUtil.isLogin()) {
                userId = StpUtil.getLoginIdAsString();
            }
            audit.setUserIdOpt(userId);
        } catch (Throwable ignore) {
        }

        try {
            HttpServletRequest request = SpringContextUtils.getHttpServletRequestQuietly();
            if (request != null) {
                audit.setRequestIp(IpUtils.getIpAddr(request));
                audit.setHttpMethod(request.getMethod());
                audit.setRequestUri(request.getRequestURI());
                String trace = request.getHeader("X-Trace-Id");
                if (oConvertUtils.isEmpty(trace)) {
                    trace = request.getHeader("trace-id");
                }
                audit.setTraceId(trace);
                Object contentType = request.getContentType();
                if (contentType != null && contentType.toString().toLowerCase().contains("json")) {
                    try {
                        Object[] args = joinPoint.getArgs();
                        if (args != null && args.length > 0) {
                            StringBuilder sb = new StringBuilder();
                            for (Object a : args) {
                                if (a == null) continue;
                                if (a instanceof jakarta.servlet.ServletRequest || a instanceof jakarta.servlet.ServletResponse) continue;
                                String cn = a.getClass().getName();
                                if (cn != null && (cn.contains("HttpServletRequest") || cn.contains("HttpServletResponse"))) continue;
                                sb.append(a.getClass().getSimpleName()).append(";");
                            }
                            if (sb.length() > 0) {
                                audit.setRequestBodyRedacted(H7ExceptionRedactor.redactMessageBody(sb.toString()));
                            }
                        }
                    } catch (Throwable ignore) {
                    }
                }
            }
        } catch (Throwable ignore) {
        }

        try {
            String tenantStr = org.springframework.util.StringUtils.hasText(System.getProperty("jeecg.tenant.id"))
                    ? System.getProperty("jeecg.tenant.id") : null;
            if (tenantStr != null) {
                audit.setTenantId(Integer.parseInt(tenantStr));
            } else {
                audit.setTenantId(0);
            }
        } catch (Throwable ignore) {
            audit.setTenantId(0);
        }

        return audit;
    }

    private void persistAsync(DenyAuditDTO audit) {
        CommonAPI api = ensureCommonApi();
        if (api == null) {
            log.warn("H8 deny audit: CommonAPI unavailable; reasonCode={}", audit.getReasonCode());
            return;
        }
        try {
            // sys_deny_audit is owned by the system shard. In the system service this resolves to
            // the local batch-100 async pipeline; in dcs/processengine it goes out through Feign
            // (SHARD-BAN T5-06/07, gate 1.14) — no local mirror of the table is required.
            api.recordDenyAudit(audit);
        } catch (Throwable t) {
            log.warn("H8 deny audit persist failed: reasonCode={}", audit.getReasonCode(), t);
        }
    }

    private CommonAPI ensureCommonApi() {
        CommonAPI api = commonApi;
        if (api != null) return api;
        synchronized (this) {
            api = commonApi;
            if (api != null) return api;
            if (applicationContext != null) {
                try {
                    api = applicationContext.getBean(CommonAPI.class);
                    commonApi = api;
                } catch (Throwable ignore) {
                }
            }
            return api;
        }
    }
}
