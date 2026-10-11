package org.jeecg.modules.system.passwordreset.helper;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Password-reset metrics helper (P3-7).
 *
 * <p>Metrics:
 * <ul>
 *   <li>pwd_reset_create_total{source,status}: create counter (status=ok|fail)</li>
 *   <li>pwd_reset_meta_total{status}: meta counter (status=ok|fail)</li>
 *   <li>pwd_reset_submit_total{result,failStage}: submit counter (result=success|fail; failStage=RSA_DECRYPT/.../null)</li>
 *   <li>pwd_reset_revoke_total{type,result}: revoke counter (type=token|byUsername, result=ok|fail)</li>
 * </ul>
 *
 * <p>Note: without MeterRegistry/actuator this component is a no-op.</p>
 */
@Slf4j
@Component
public class PasswordResetMetrics {

    private final MeterRegistry registry;
    private final ConcurrentMap<String, Counter> counterCache = new ConcurrentHashMap<>();

    public PasswordResetMetrics(org.springframework.beans.factory.ObjectProvider<MeterRegistry> provider) {
        MeterRegistry r = null;
        try {
            r = provider.getIfAvailable();
        } catch (Exception ignore) {}
        this.registry = r;
    }

    public void countCreate(String source, boolean ok) {
        inc("pwd_reset_create_total",
                "source", nvl(source),
                "status", ok ? "ok" : "fail");
    }

    public void countMeta(boolean ok) {
        inc("pwd_reset_meta_total",
                "status", ok ? "ok" : "fail");
    }

    public void countSubmit(boolean success, String failStage) {
        inc("pwd_reset_submit_total",
                "result", success ? "success" : "fail",
                "failStage", success ? "" : nvl(failStage));
    }

    public void countRevoke(String type, boolean ok) {
        inc("pwd_reset_revoke_total",
                "type", nvl(type),
                "result", ok ? "ok" : "fail");
    }

    private void inc(String name, String... tagKV) {
        if (registry == null) return;
        try {
            String key = key(name, tagKV);
            Counter c = counterCache.computeIfAbsent(key, k -> {
                Counter.Builder builder = Counter.builder(name);
                for (int i = 0; i + 1 < tagKV.length; i += 2) {
                    builder.tag(tagKV[i], tagKV[i + 1]);
                }
                return builder.register(registry);
            });
            c.increment();
        } catch (Exception e) {
            log.debug("[PasswordResetMetrics] count failed: {}");
        }
    }

    private static String key(String name, String... kv) {
        StringBuilder sb = new StringBuilder(name);
        for (String s : kv) sb.append('|').append(s);
        return sb.toString();
    }

    private static String nvl(String s) { return s == null ? "" : s; }
}
