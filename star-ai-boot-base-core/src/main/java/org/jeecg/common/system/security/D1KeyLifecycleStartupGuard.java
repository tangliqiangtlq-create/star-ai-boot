package org.jeecg.common.system.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanInitializationException;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.ReflectionUtils;
import org.springframework.util.StringUtils;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

@Slf4j
@Component
public class D1KeyLifecycleStartupGuard implements SmartInitializingSingleton, ApplicationContextAware {

    private ApplicationContext ctx;
    @Override public void setApplicationContext(ApplicationContext c) { this.ctx = c; }

    private static final List<String> L4_MANDATORY_KEYS = List.of(
        "GATEWAY_SECRET",
        "JEECG_DB_AES_KEY",
        "MFA_MASTER_SECRET",
        "SM2_JWT_PRIVATE_KEY",
        "RESET_SECRET",
        "XXL_JOB_ACCESS_TOKEN",
        "NACOS_AUTH_TOKEN",
        "OTP_PEPPER"
    );

    private static final Pattern BAD_VALUE =
        Pattern.compile("(?i)^(CHANGE_ME.*|LOCAL_DEV.*|DEFAULT.*|SAMPLE.*|XXXX.*|PUT[_-]?YOUR[_-]?HERE.*|)$");

    private static final String RAMP_OVERRIDE_REASON = "ENV_PLAYBOOK_REASON";
    private static final String RAMP_OVERRIDE_APPROVER = "ENV_PLAYBOOK_APPROVER";

    @Override
    public void afterSingletonsInstantiated() {
        Set<String> missingMandatory = new TreeSet<>();
        Set<String> injectedByBadDefault = new TreeSet<>();

        org.springframework.core.env.Environment env = ctx.getEnvironment();

        boolean rampOverride = hasRampD1Override(env);

        for (String k : L4_MANDATORY_KEYS) {
            String raw = env.getProperty(k);
            if (raw == null || BAD_VALUE.matcher(raw).matches()) {
                if (!rampOverride) {
                    missingMandatory.add(k);
                }
            }
        }

        String[] beanNames = ctx.getBeanDefinitionNames();
        for (String bn : beanNames) {
            Object bean;
            try { bean = ctx.getBean(bn); } catch (BeansException ignore) { continue; }
            Class<?> c = bean.getClass();
            ReflectionUtils.doWithFields(c, f -> {
                Value v = AnnotatedElementUtils.findMergedAnnotation(f, Value.class);
                if (v == null) return;
                try {
                    ReflectionUtils.makeAccessible(f);
                    Object val = f.get(bean);
                    String s = val == null ? "" : String.valueOf(val);
                    if (!looksLikeSecret(v.value())) return;
                    // Optional @Value("${x:}") empty defaults are OK; only refuse CHANGE_ME/LOCAL_DEV stubs.
                    if (!StringUtils.hasText(s)) {
                        String expr = v.value();
                        if (expr != null && expr.contains(":") && expr.trim().endsWith("}")) {
                            return;
                        }
                    }
                    if (BAD_VALUE.matcher(s).matches() && StringUtils.hasText(s)) {
                        injectedByBadDefault.add(c.getName() + "#" + f.getName() + " <= " + v.value());
                    }
                } catch (IllegalAccessException ignore) {}
            });
            for (Method m : c.getDeclaredMethods()) {
                Value v = AnnotatedElementUtils.findMergedAnnotation(m, Value.class);
                if (v == null) continue;
                Arrays.stream(m.getParameters()).forEach(p -> {
                    Value pv = p.getAnnotation(Value.class);
                    if (pv != null && looksLikeSecret(pv.value())) {
                        injectedByBadDefault.add(c.getName() + "#" + m.getName() + " param:" + p.getName());
                    }
                });
            }
        }

        if (!missingMandatory.isEmpty() || !injectedByBadDefault.isEmpty()) {
            StringBuilder sb = new StringBuilder(512);
            sb.append("D1 CRITICAL KEY CHECK FAILED (refuse to start):\n");
            if (!missingMandatory.isEmpty()) sb.append("  missing env keys: ").append(missingMandatory).append("\n");
            if (!injectedByBadDefault.isEmpty()) sb.append("  @Value injected with placeholder (see D1 scan):\n    - ")
                .append(String.join("\n    - ", injectedByBadDefault)).append("\n");
            sb.append("  fix: set them via .env (NOT yml / classpath) then restart. See D1 of 00-D remediation plan v1.0 section 2.");
            log.error("\n========== D1 KEY GUARD BLOCK STARTUP ==========\n{}\n=================================================", sb);
            throw new BeanInitializationException(sb.toString());
        }

        log.info("D1 L4 MANDATORY KEYS (8/8) OK: all injected via env, no placeholder detected.");
    }

    private boolean hasRampD1Override(org.springframework.core.env.Environment env) {
        String reason = env.getProperty(RAMP_OVERRIDE_REASON);
        String approver = env.getProperty(RAMP_OVERRIDE_APPROVER);
        if (reason != null && reason.startsWith("https://")
            && approver != null && approver.contains("@")) {
            log.warn("[D1-RAMP-OVERRIDE] Phase1 fail-fast overridden by ENV_PLAYBOOK_REASON/APPROVER. "
                + "Reason={}, Approver={}", reason, approver);
            return true;
        }
        return false;
    }

    private boolean looksLikeSecret(String placeholderExpr) {
        if (placeholderExpr == null) return false;
        String lower = placeholderExpr.toLowerCase();
        return lower.contains("secret") || lower.contains("key") || lower.contains("token")
            || lower.contains("pepper") || lower.contains("password") || lower.contains("private");
    }
}
