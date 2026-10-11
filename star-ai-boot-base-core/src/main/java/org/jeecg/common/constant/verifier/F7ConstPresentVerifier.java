package org.jeecg.common.constant.verifier;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Set;

@Slf4j
@Component
@ConditionalOnProperty(prefix = "dcs.f7", name = "enabled", havingValue = "true", matchIfMissing = true)
public class F7ConstPresentVerifier implements InitializingBean {

    private static final Set<String> REQUIRED = Set.of(
            "ROLE_SUPER_ADMIN",
            "ROLE_OPS_ADMIN",
            "ROLE_FILE_ADMIN",
            "ROLE_SEC_ADMIN",
            "ROLE_LEDGER_ADMIN",
            "ROLE_TEMPLATE_APPROVER"
    );

    @Override
    public void afterPropertiesSet() throws Exception {
        Class<?> clazz;
        try {
            clazz = Class.forName("org.jeecg.common.constant.SecurityRoleConstants");
        } catch (ClassNotFoundException cnfe) {
            throw new IllegalStateException("[F7-VERIFY-FAIL] SecurityRoleConstants class missing from classpath", cnfe);
        }

        int modifiers = clazz.getModifiers();
        if (!Modifier.isFinal(modifiers)) {
            throw new IllegalStateException("[F7-VERIFY-FAIL] SecurityRoleConstants is NOT declared final");
        }

        Field[] fields = clazz.getDeclaredFields();
        int found = 0;
        for (Field f : fields) {
            if (!REQUIRED.contains(f.getName())) continue;
            int fm = f.getModifiers();
            if (!Modifier.isPublic(fm) || !Modifier.isStatic(fm) || !Modifier.isFinal(fm)) {
                throw new IllegalStateException("[F7-VERIFY-FAIL] " + f.getName()
                        + " missing public static final modifier (mod=" + fm + ")");
            }
            f.setAccessible(true);
            Object val = f.get(null);
            if (!(val instanceof String) || ((String) val).trim().isEmpty()) {
                throw new IllegalStateException("[F7-VERIFY-FAIL] " + f.getName()
                        + " value is null/blank (val=" + val + ")");
            }
            found++;
        }

        if (found != REQUIRED.size()) {
            throw new IllegalStateException("[F7-VERIFY-FAIL] required constant missing. expected="
                    + REQUIRED.size() + " actual=" + found
                    + " missing=" + REQUIRED.stream()
                    .filter(r -> {
                        try { clazz.getDeclaredField(r); return false; }
                        catch (NoSuchFieldException e) { return true; }
                    }).toList());
        }

        java.lang.reflect.Constructor<?>[] ctors = clazz.getDeclaredConstructors();
        for (java.lang.reflect.Constructor<?> c : ctors) {
            if (!Modifier.isPrivate(c.getModifiers())) {
                throw new IllegalStateException("[F7-VERIFY-FAIL] SecurityRoleConstants constructor must be private (got: " + c + ")");
            }
        }

        log.info("[F7ConstPresentVerifier] PASS: SecurityRoleConstants 6/6 present + final class + private constructor");
    }
}
