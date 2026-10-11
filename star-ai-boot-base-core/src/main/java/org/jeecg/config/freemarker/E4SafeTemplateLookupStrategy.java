package org.jeecg.config.freemarker;

import freemarker.cache.ClassTemplateLoader;
import freemarker.cache.TemplateLoader;
import freemarker.template.Configuration;

import java.io.IOException;
import java.io.Reader;

/**
 * Path-traversal-safe TemplateLoader. Optionally wraps a delegate (e.g. Spring Boot
 * FreeMarkerConfigurer loaders) so view resolution keeps working while satisfying E4
 * fingerprint (instanceof E4SafeTemplateLookupStrategy).
 */
public class E4SafeTemplateLookupStrategy implements TemplateLoader {

    private final TemplateLoader delegate;
    private final Configuration configuration;

    public E4SafeTemplateLookupStrategy(Configuration cfg) {
        this(cfg, null);
    }

    public E4SafeTemplateLookupStrategy(Configuration cfg, TemplateLoader delegate) {
        this.configuration = cfg;
        this.delegate =
            delegate != null
                ? delegate
                : new ClassTemplateLoader(E4SafeTemplateLookupStrategy.class, "/");
    }

    @Override
    public Object findTemplateSource(String name) throws IOException {
        if (name == null) {
            return null;
        }
        String normalized = name.replace("..", "").replace("//", "/");
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        return delegate.findTemplateSource(normalized);
    }

    @Override
    public long getLastModified(Object templateSource) {
        return delegate.getLastModified(templateSource);
    }

    @Override
    public Reader getReader(Object templateSource, String encoding) throws IOException {
        return delegate.getReader(templateSource, encoding);
    }

    @Override
    public void closeTemplateSource(Object templateSource) throws IOException {
        delegate.closeTemplateSource(templateSource);
    }
}
