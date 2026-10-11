package org.jeecg.config;

import org.jeecg.filter.GlobalAccessTokenFilter;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import reactor.core.publisher.Mono;

/**
 * @author scott
 * @date 2020/5/26
 * Route rate-limit configuration
 */
@Configuration
public class RateLimiterConfiguration {
    /**
     * IP rate limit (uses HostName from exchange request info)
     */
    @Bean
    @Primary
    public KeyResolver ipKeyResolver() {
        return exchange -> Mono.just(exchange.getRequest().getRemoteAddress().getAddress().getHostAddress());
    }

    /**
     * User rate limiting (via exchange object can get request info, get current user TOKEN)
     */
    @Bean
    public KeyResolver userKeyResolver() {
        // Rate limit this way requires X-Access-Token in request header
        return exchange -> Mono.just(exchange.getRequest().getHeaders().getFirst(GlobalAccessTokenFilter.X_ACCESS_TOKEN));
    }

    /**
     * API rate limiting (uses request URI as the rate-limit key)
     */
    @Bean
    public KeyResolver apiKeyResolver() {
        return exchange -> Mono.just(exchange.getRequest().getPath().value());
    }

}
