package org.jeecg.config;

import java.io.IOException;

import jakarta.servlet.*;

import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.alibaba.druid.spring.boot3.autoconfigure.DruidDataSourceAutoConfigure;
import com.alibaba.druid.spring.boot3.autoconfigure.properties.DruidStatProperties;
import com.alibaba.druid.util.Utils;

/**
 * @Description: DruidConfig configuration class
 * @author: jeecg-boot
 */
@Configuration
@ConditionalOnClass(DruidStatProperties.class)
@AutoConfigureAfter(DruidDataSourceAutoConfigure.class)
public class DruidConfig {

    /**
     * Full path to common.js with ads, druid-1.1.14
     */
    private static final String FILE_PATH = "support/http/resources/js/common.js";
    /**
     * Original script statement that triggers ad injection.
     */
    private static final String ORIGIN_JS = "this.buildFooter();";
    /**
     * Replaced script
     */
    private static final String NEW_JS = "//this.buildFooter();";

    /**
     * Remove ads from the Druid monitoring page.
     *
     * @param properties DruidStatProperties property collection
     * @return {@link FilterRegistrationBean}
     */
    @Bean
    @ConditionalOnWebApplication
    @ConditionalOnBean(DruidStatProperties.class)
    @ConditionalOnProperty(name = "spring.datasource.druid.stat-view-servlet.enabled", havingValue = "true")
    public FilterRegistrationBean<RemoveAdFilter> removeDruidAdFilter(
            DruidStatProperties properties) throws IOException {
        // Get web monitor page params
        DruidStatProperties.StatViewServlet config = properties.getStatViewServlet();
        // Extract common.js config path
        String pattern = config.getUrlPattern() != null ? config.getUrlPattern() : "/druid/*";
        String commonJsPattern = pattern.replaceAll("\\*", "js/common.js");
        // Getcommon.js
        String text = Utils.readFromResource(FILE_PATH);
        // Disable this.buildFooter(); do not build ads
        final String newJs = text.replace(ORIGIN_JS, NEW_JS);
        FilterRegistrationBean<RemoveAdFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new RemoveAdFilter(newJs));
        registration.addUrlPatterns(commonJsPattern);
        return registration;
    }

    /**
     * Remove Druid ad filter.
     *
     * @author BBF
     */
    private class RemoveAdFilter implements Filter {

        private final String newJs;

        public RemoveAdFilter(String newJs) {
            this.newJs = newJs;
        }

        @Override
        public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
                throws IOException, ServletException {
            chain.doFilter(request, response);
            // Reset buffer; response headers are not reset
            response.resetBuffer();
            response.getWriter().write(newJs);
        }
    }
}
