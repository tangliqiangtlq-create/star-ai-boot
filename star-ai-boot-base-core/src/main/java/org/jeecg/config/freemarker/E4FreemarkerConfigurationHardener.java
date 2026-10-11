package org.jeecg.config.freemarker;

import freemarker.core.TemplateClassResolver;
import freemarker.template.Configuration;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.PriorityOrdered;
import org.springframework.stereotype.Component;

/**
 * E4: harden EVERY Freemarker {@link Configuration} bean (including Spring Boot
 * auto-config {@code freeMarkerConfiguration}) to ALLOWS_NOTHING + APIBuiltinEnabled(false)
 * + {@link E4SafeTemplateLookupStrategy}.
 */
@Component
public class E4FreemarkerConfigurationHardener implements BeanPostProcessor, PriorityOrdered {

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 20;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        if (bean instanceof Configuration) {
            applySandbox((Configuration) bean);
            return bean;
        }
        // Spring FreeMarkerConfigurer — configuration may be built lazily; try getConfiguration()
        if (bean != null && bean.getClass().getName().contains("FreeMarkerConfigurer")) {
            try {
                java.lang.reflect.Method m = bean.getClass().getMethod("getConfiguration");
                Object cfg = m.invoke(bean);
                if (cfg instanceof Configuration) {
                    applySandbox((Configuration) cfg);
                }
            } catch (Throwable ignore) {
                // configurer not ready yet; E4SandboxVerifier will still fail-fast if unset
            }
        }
        return bean;
    }

    static void applySandbox(Configuration cfg) {
        if (cfg == null) {
            return;
        }
        cfg.setNewBuiltinClassResolver(TemplateClassResolver.ALLOWS_NOTHING_RESOLVER);
        cfg.setAPIBuiltinEnabled(false);
        freemarker.cache.TemplateLoader existing = cfg.getTemplateLoader();
        if (!(existing instanceof E4SafeTemplateLookupStrategy)) {
            cfg.setTemplateLoader(new E4SafeTemplateLookupStrategy(cfg, existing));
        }
    }
}
