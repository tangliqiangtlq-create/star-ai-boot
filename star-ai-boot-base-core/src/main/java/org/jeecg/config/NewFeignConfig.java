package org.jeecg.config;

import feign.Client;
import feign.Logger;
import feign.RequestInterceptor;
import feign.RequestTemplate;
import feign.RetryableException;
import feign.Retryer;
import feign.okhttp.OkHttpClient;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.SortedMap;
import java.util.concurrent.TimeUnit;
import okhttp3.ConnectionPool;
import okhttp3.Dispatcher;
import org.jeecg.common.config.TenantContext;
import org.jeecg.common.config.mqtoken.UserTokenContext;
import org.jeecg.common.util.PathMatcherUtil;
import org.jeecg.config.sign.util.HttpUtils;
import org.jeecg.config.sign.util.SignUtil;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/** Unified Feign configuration compatible with Spring Boot 3/jakarta. */
@Configuration
public class NewFeignConfig {

  private static final org.slf4j.Logger log = LoggerFactory.getLogger(NewFeignConfig.class);
  public static final String X_ACCESS_TOKEN = "X-Access-Token";
  public static final String X_SIGN = "X-Sign";
  public static final String X_TIMESTAMP = "X-TIMESTAMP";
  public static final String TENANT_ID = "X-Tenant-Id";

  @Value("${jeecg.feign.okhttp.connect-timeout-ms:4000}")
  private int connectTimeoutMs;

  @Value("${jeecg.feign.okhttp.read-timeout-ms:20000}")
  private int readTimeoutMs;

  @Value("${jeecg.feign.okhttp.max-idle-connections:40}")
  private int maxIdle;

  @Value("${jeecg.feign.retry.max-attempts:3}")
  private int retryMaxAttempts;

  @Bean
  public RequestInterceptor requestInterceptor() {
    return this::applyHeaders;
  }

  @Bean
  @Order(Ordered.HIGHEST_PRECEDENCE)
  public H2FeignHeaderStrippingInterceptor h2FeignHeaderStrippingInterceptor() {
    return new H2FeignHeaderStrippingInterceptor();
  }

  /**
   * Runs last: force Cookie replace when {@link UserTokenContext} is set so earlier interceptors
   * cannot leave an end-user Cookie (nacos {@code isReadHeader=false} → Cookie is Sa-Token's only
   * carrier). Without this, DCS→processengine add keeps the browser session → A5-1 403.
   */
  @Bean
  @Order(Ordered.LOWEST_PRECEDENCE)
  public RequestInterceptor userTokenContextElevationLastInterceptor() {
    return template -> {
      String elevated = UserTokenContext.getToken();
      if (!StringUtils.hasText(elevated)) {
        return;
      }
      applyElevatedAuth(template, elevated);
      if (template.path() != null && template.path().contains("processInstance")) {
        log.warn(
            "Feign FINAL elevation Cookie replaced path={} tokenLen={}",
            template.path(),
            elevated.length());
      }
    };
  }

  @Bean
  Logger.Level feignLoggerLevel(
      @Value("${jeecg.feign.logger.level:BASIC}") String level) {
    try {
      return Logger.Level.valueOf(level.trim().toUpperCase());
    } catch (Exception ignore) {
      return Logger.Level.BASIC;
    }
  }

  /**
   * B10-3: OkHttp-backed Feign client with pooled connections. Must wrap with {@link
   * org.springframework.cloud.openfeign.loadbalancer.FeignBlockingLoadBalancerClient} — a raw
   * OkHttpClient bean bypasses Nacos/LoadBalancer and yields {@code UnknownHostException:
   * jeecg-system}.
   */
  @Bean
  public Client feignClient(
      org.springframework.cloud.client.loadbalancer.LoadBalancerClient loadBalancerClient,
      org.springframework.cloud.loadbalancer.support.LoadBalancerClientFactory
          loadBalancerClientFactory,
      org.springframework.beans.factory.ObjectProvider<
              java.util.List<
                  org.springframework.cloud.openfeign.loadbalancer
                      .LoadBalancerFeignRequestTransformer>>
          transformers) {
    Dispatcher dispatcher = new Dispatcher();
    dispatcher.setMaxRequests(200);
    dispatcher.setMaxRequestsPerHost(40);
    ConnectionPool pool = new ConnectionPool(maxIdle, 5, TimeUnit.MINUTES);
    okhttp3.OkHttpClient ok =
        new okhttp3.OkHttpClient.Builder()
            .dispatcher(dispatcher)
            .connectionPool(pool)
            .connectTimeout(connectTimeoutMs, TimeUnit.MILLISECONDS)
            .readTimeout(readTimeoutMs, TimeUnit.MILLISECONDS)
            .writeTimeout(readTimeoutMs, TimeUnit.MILLISECONDS)
            .retryOnConnectionFailure(true)
            .build();
    Client delegate = new OkHttpClient(ok);
    java.util.List<
            org.springframework.cloud.openfeign.loadbalancer.LoadBalancerFeignRequestTransformer>
        transformerList =
            transformers.getIfAvailable(java.util.Collections::emptyList);
    return new org.springframework.cloud.openfeign.loadbalancer.FeignBlockingLoadBalancerClient(
        delegate, loadBalancerClient, loadBalancerClientFactory, transformerList);
  }

  /**
   * B10-3: exponential-backoff Retryer for 5xx/429/network errors. Period starts at 500 ms and
   * doubles up to 5000 ms, up to {@code retryMaxAttempts} attempts (hard cap at 3 to avoid
   * retry storms). Skips 401/403/400-style auth or business errors (auth refresh is handled
   * separately by AuthAwareRetryer).
   */
  @Bean
  public Retryer feignRetryer() {
    int attempts = Math.min(3, Math.max(1, retryMaxAttempts));
    return new Retryer.Default(500L, 5000L, attempts) {
      @Override
      public void continueOrPropagate(RetryableException e) {
        // Feign 13: RetryableException.status() returns primitive int; absent status → 0
        int status = e.status();
        boolean retryable =
            status == 0
                || (status >= 500 && status <= 599)
                || status == 408 /* Request Timeout */
                || status == 425 /* Too Early */
                || status == 429 /* Too Many Requests */;
        if (!retryable) {
          throw e;
        }
        super.continueOrPropagate(e);
      }
    };
  }

  private void applyHeaders(RequestTemplate template) {
    ServletRequestAttributes attrs = null;
    try {
      attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
    } catch (Throwable ex) {
      log.warn("Resolve request attributes failed in NewFeignConfig: {}");
    }

    // UserTokenContext (MQ/Async propagated token) MUST win over inbound HTTP session token.
    // Otherwise Feign keeps the end-user session and @SaCheckPermission(add) → 403.
    // nacos sa-token.isReadHeader=false → Cookie is the carrier Sa-Token actually reads.
    String elevated = UserTokenContext.getToken();
    if (StringUtils.hasText(elevated)) {
      applyElevatedAuth(template, elevated);
      if (attrs != null) {
        try {
          HttpServletRequest request = attrs.getRequest();
          String tenantId = request.getHeader(TENANT_ID);
          if (!StringUtils.hasText(tenantId)) {
            tenantId = request.getParameter(TENANT_ID);
          }
          if (!StringUtils.hasText(tenantId)) {
            tenantId = org.jeecg.common.config.TenantContext.getTenant();
          }
          if (StringUtils.hasText(tenantId)) {
            template.removeHeader(TENANT_ID);
            template.header(TENANT_ID, tenantId);
          }
          forwardHeader(template, request, "Origin");
          forwardHeader(template, request, "Referer");
          forwardHeader(template, request, "x_gateway_base_path");
        } catch (Throwable ex) {
          log.warn("Forward gateway headers after elevation failed: {}");
        }
      }
      signIfNeeded(template);
      return;
    }

    if (attrs != null) {
      try {
        HttpServletRequest request = attrs.getRequest();
        // Forward Cookie — Sa-Token isReadHeader=false; processengine→system permission Feign
        // must carry the inbound session Cookie or queryUserAuths falls back to empty/null.
        String cookie = request.getHeader("Cookie");
        if (StringUtils.hasText(cookie)) {
          template.removeHeader("Cookie");
          template.header("Cookie", cookie);
        }

        String token = request.getHeader(X_ACCESS_TOKEN);
        if (StringUtils.hasText(token)) {
          applyTokenHeader(template, token);
        }

        String tenantId = request.getHeader(TENANT_ID);
        if (!StringUtils.hasText(tenantId)) {
          tenantId = request.getParameter(TENANT_ID);
        }
        if (!StringUtils.hasText(tenantId)) {
          tenantId = org.jeecg.common.config.TenantContext.getTenant();
        }
        if (StringUtils.hasText(tenantId)) {
          template.removeHeader(TENANT_ID);
          template.header(TENANT_ID, tenantId);
        }

        // Forward browser access domain for downstream @FilePreview absolute preview URL
        forwardHeader(template, request, "Origin");
        forwardHeader(template, request, "Referer");
        forwardHeader(template, request, "x_gateway_base_path");
      } catch (Throwable ex) {
        log.warn("Read servlet request for Feign header failed: {}");
        fallbackHeaders(template);
      }
    } else {
      fallbackHeaders(template);
    }

    signIfNeeded(template);
  }

  /**
   * Replace Cookie + X-Access-Token with elevated token. Feign {@code header()} appends; leftover
   * end-user Cookie would win Sa-Token resolution when {@code isReadHeader=false}.
   */
  private static void applyElevatedAuth(RequestTemplate template, String elevatedToken) {
    template.removeHeader("Cookie");
    template.removeHeader(X_ACCESS_TOKEN);
    String tokenName = "token";
    try {
      String name = cn.dev33.satoken.stp.StpUtil.getTokenName();
      if (StringUtils.hasText(name)) {
        tokenName = name.trim();
      }
    } catch (Throwable ignore) {
    }
    template.removeHeader(tokenName);
    template.header("Cookie", tokenName + "=" + elevatedToken);
    template.header(X_ACCESS_TOKEN, elevatedToken);
    template.header(tokenName, elevatedToken);
  }

  /** Replace (not append) so a later/earlier interceptor cannot leave a stale end-user token. */
  private static void applyTokenHeader(RequestTemplate template, String token) {
    template.removeHeader(X_ACCESS_TOKEN);
    template.header(X_ACCESS_TOKEN, token);
  }

  private void forwardHeader(RequestTemplate template, HttpServletRequest request, String name) {
    String value = request.getHeader(name);
    if (StringUtils.hasText(value)) {
      template.header(name, value);
    }
  }

  private void fallbackHeaders(RequestTemplate template) {
    String token = UserTokenContext.getToken();
    if (StringUtils.hasText(token)) {
      applyElevatedAuth(template, token);
    }
    String tenantId = TenantContext.getTenant();
    if (StringUtils.hasText(tenantId)) {
      template.removeHeader(TENANT_ID);
      template.header(TENANT_ID, tenantId);
    }
  }

  private void signIfNeeded(RequestTemplate template) {
    List<String> signUrls = Arrays.asList(PathMatcherUtil.SIGN_URL_LIST);
    if (!PathMatcherUtil.matches(signUrls, template.path())) {
      return;
    }
    try {
      String queryLine = template.queryLine();
      if (queryLine != null && queryLine.startsWith("?")) {
        queryLine = queryLine.substring(1);
      }
      SortedMap<String, String> allParams =
          HttpUtils.getAllParams(template.path(), queryLine, template.body(), template.method());
      String sign = SignUtil.getParamsSign(allParams);
      template.header(X_SIGN, sign);
      template.header(X_TIMESTAMP, String.valueOf(System.currentTimeMillis()));
      if (template.body() != null) {
        log.debug("Feign sign body: {}", new String(template.body(), StandardCharsets.UTF_8));
      }
    } catch (IOException e) {
      log.error("Feign request sign error", e);
    }
  }
}
