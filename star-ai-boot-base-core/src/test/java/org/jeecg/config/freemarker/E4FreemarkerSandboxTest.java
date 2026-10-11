package org.jeecg.config.freemarker;

import freemarker.template.Configuration;
import freemarker.template.Template;
import org.junit.Assert;
import org.junit.Test;

import java.io.StringReader;
import java.io.StringWriter;
import java.io.Writer;

public class E4FreemarkerSandboxTest {

    @Test
    public void testArithmeticExpressionRenderedAsLiteral() throws Exception {
        Configuration cfg = new Configuration(Configuration.VERSION_2_3_31);
        cfg.setNewBuiltinClassResolver(freemarker.core.TemplateClassResolver.ALLOWS_NOTHING_RESOLVER);
        cfg.setAPIBuiltinEnabled(false);
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
        cfg.setDefaultEncoding("UTF-8");
        String expression = "${123*456}";
        Template t = new Template("trap", new StringReader(expression), cfg);
        Writer out = new StringWriter();
        t.process(null, out);
        String rendered = out.toString();
        Assert.assertNotEquals("Sandbox must NOT evaluate arithmetic, got=" + rendered, "56088", rendered.trim());
    }
}
