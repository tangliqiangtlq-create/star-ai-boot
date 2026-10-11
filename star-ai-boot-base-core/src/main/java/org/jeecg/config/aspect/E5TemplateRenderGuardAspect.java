package org.jeecg.config.aspect;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.jeecg.common.annotation.RenderRequiresApproved;
import org.jeecg.common.exception.E5RejectException;
import org.springframework.stereotype.Component;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

@Aspect
@Component
@Slf4j
public class E5TemplateRenderGuardAspect {

    private static final String STATUS_APPROVED = "APPROVED";

    @Around("@annotation(org.jeecg.common.annotation.RenderRequiresApproved) || @within(org.jeecg.common.annotation.RenderRequiresApproved)")
    public Object aroundRender(ProceedingJoinPoint pjp) throws Throwable {
        Object[] args = pjp.getArgs();
        MethodSignature sig = (MethodSignature) pjp.getSignature();
        Method method = sig.getMethod();
        RenderRequiresApproved ann = method.getAnnotation(RenderRequiresApproved.class);
        if (ann == null && pjp.getTarget() != null) {
            ann = pjp.getTarget().getClass().getAnnotation(RenderRequiresApproved.class);
        }

        for (Object arg : args) {
            checkTemplateContentStatus(arg);
        }

        Object result = pjp.proceed();
        checkTemplateContentStatus(result);
        return result;
    }

    public static void checkTemplateContentStatus(Object templateEntity) {
        if (templateEntity == null) {
            return;
        }
        try {
            Field f = templateEntity.getClass().getDeclaredField("status");
            f.setAccessible(true);
            Object status = f.get(templateEntity);
            if (status == null || !STATUS_APPROVED.equalsIgnoreCase(String.valueOf(status))) {
                String templateCode = "";
                try {
                    Field tc = templateEntity.getClass().getDeclaredField("templateCode");
                    tc.setAccessible(true);
                    Object v = tc.get(templateEntity);
                    if (v != null) templateCode = String.valueOf(v);
                } catch (Exception ignored) {
                }
                throw new E5RejectException(
                    "E5TemplateRenderGuard: template status not APPROVED, templateCode=" + templateCode
                    + ", status=" + status
                );
            }
        } catch (E5RejectException e) {
            throw e;
        } catch (Exception ignored) {
        }
    }
}
