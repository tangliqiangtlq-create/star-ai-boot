package org.jeecg.config.freemarker;

import freemarker.core.TemplateClassResolver;
import freemarker.template.Configuration;
import freemarker.template.TemplateExceptionHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

@Component
public class E4SharedFreemarkerSandbox {

    @Bean(name = "sharedFreemarkerConfiguration")
    @Primary
    public Configuration sharedFreemarkerConfiguration() {
        Configuration cfg = new Configuration(Configuration.VERSION_2_3_31);
        cfg.setNewBuiltinClassResolver(TemplateClassResolver.ALLOWS_NOTHING_RESOLVER);
        cfg.setAPIBuiltinEnabled(false);
        // Try best-effort auto-escaping for SSTI protection; reflect to tolerate freemarker version variance
        try {
            java.lang.reflect.Method strat = Configuration.class.getMethod("setAutoEscapingStrategy", Class.forName("freemarker.core.AutoEscapingStrategy"));
            Class<?> k = Class.forName("freemarker.core.AutoEscapingStrategy");
            Object enabled = java.util.Arrays.stream(k.getEnumConstants()).filter(e -> "ENABLED_IF_DEFAULT_MARKUP_OUTPUT".equals(String.valueOf(e))).findFirst().orElse(null);
            if (enabled != null) strat.invoke(cfg, enabled);
        } catch (Throwable t0) {
            try {
                java.lang.reflect.Method auto = Configuration.class.getMethod("setAutoEscaping", boolean.class);
                auto.invoke(cfg, Boolean.TRUE);
            } catch (Throwable ignore) {
            }
        }
        cfg.setTemplateLoader(new E4SafeTemplateLookupStrategy(cfg));
        cfg.setDefaultEncoding("UTF-8");
        cfg.setClassicCompatible(true);
        cfg.setTemplateExceptionHandler(TemplateExceptionHandler.RETHROW_HANDLER);
        cfg.setLogTemplateExceptions(false);
        cfg.setWrapUncheckedExceptions(true);
        return cfg;
    }
}
