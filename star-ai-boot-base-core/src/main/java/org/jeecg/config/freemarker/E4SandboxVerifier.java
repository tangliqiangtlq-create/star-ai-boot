package org.jeecg.config.freemarker;

import freemarker.core.TemplateClassResolver;
import freemarker.template.Configuration;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class E4SandboxVerifier implements InitializingBean, ApplicationContextAware {

    private ApplicationContext applicationContext;

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        Map<String, Configuration> configs = applicationContext.getBeansOfType(Configuration.class);
        if (configs == null || configs.isEmpty()) {
            throw new BeanInitializationException("E4SandboxVerifier: No Configuration beans found, expected at least sharedFreemarkerConfiguration");
        }
        for (Map.Entry<String, Configuration> entry : configs.entrySet()) {
            String beanName = entry.getKey();
            Configuration cfg = entry.getValue();
            // Defense in depth: harden again in case FreeMarkerConfigurer built config after BPP
            E4FreemarkerConfigurationHardener.applySandbox(cfg);
            verifyFingerprint(beanName, cfg);
        }
    }

    private void verifyFingerprint(String beanName, Configuration cfg) {
        TemplateClassResolver resolver = cfg.getNewBuiltinClassResolver();
        if (resolver != TemplateClassResolver.ALLOWS_NOTHING_RESOLVER) {
            throw new BeanInitializationException(
                "E4SandboxVerifier FAIL: bean=[" + beanName + "] TemplateClassResolver != ALLOWS_NOTHING_RESOLVER"
            );
        }
        if (cfg.isAPIBuiltinEnabled()) {
            throw new BeanInitializationException(
                "E4SandboxVerifier FAIL: bean=[" + beanName + "] APIBuiltinEnabled should be false"
            );
        }
        if (!(cfg.getTemplateLoader() instanceof E4SafeTemplateLookupStrategy)) {
            throw new BeanInitializationException(
                "E4SandboxVerifier FAIL: bean=[" + beanName + "] TemplateLoader is not E4SafeTemplateLookupStrategy"
            );
        }
    }

    public static class BeanInitializationException extends RuntimeException {
        public BeanInitializationException(String msg) {
            super(msg);
        }
    }
}
