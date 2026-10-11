package org.jeecg.config;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.util.StrUtil;
import feign.Client;
import feign.Feign;
import feign.Logger;
import feign.RequestInterceptor;
import feign.RequestTemplate;
import feign.RetryableException;
import feign.Retryer;
import feign.codec.Decoder;
import feign.codec.Encoder;
import feign.okhttp.OkHttpClient;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.SortedMap;
import java.util.concurrent.TimeUnit;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import okhttp3.ConnectionPool;
import okhttp3.Dispatcher;
import org.jeecg.common.constant.CommonConstant;
import org.jeecg.common.util.CompositeEncoder;
import org.jeecg.common.util.PathMatcherUtil;
import org.jeecg.config.sign.util.HttpUtils;
import org.jeecg.config.sign.util.SignUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.cloud.openfeign.FeignAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Scope;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * @Description: CustomFeignConfig
 *
 * @author: JeecgBoot
 */
@ConditionalOnClass(Feign.class)
@AutoConfigureBefore(FeignAutoConfiguration.class)
@Slf4j
@Configuration
public class CustomFeignConfig implements RequestInterceptor {

  @Value("${jeecg.signUrls:}")
  private String signUrls;

  @Value("${jeecg.internal.call.secret:}")
  private String internalCallSecret;

  /**
   * Feign client logging; default level NONE. Logger.Level details:
   * NONE: log nothing BASIC: log method, URL, status code, and elapsed time only
   * HEADERS: BASIC plus request/response headers FULL: log all request/response details including headers, body, metadata
   */
  @Bean
  @Primary
  Logger.Level feignLoggerLevel(
      @Value("${jeecg.feign.logger.level:BASIC}") String level) {
    try {
      return Logger.Level.valueOf(level.trim().toUpperCase());
    } catch (Exception ignore) {
      return Logger.Level.BASIC;
    }
  }

  /** Custom Decoder */
  @Bean
  @Primary
  @Scope("prototype")
  public Decoder feignDecoder() {
    log.info("CustomFeignConfig feignDecoder");
    return new MyResponseEntityDecoder();
  }

  /** Configure SpringFormEncoder to support multipart/form-data requests */
  @Bean
  @Primary
  public Encoder feignEncoder() {
    log.info("CustomFeignConfig feignEncoder");
    return new CompositeEncoder();
  }

  /** B10-3: OkHttp pool + LoadBalancer (must wrap or service names like jeecg-system fail DNS). */
  @Bean
  @Primary
  public Client feignClient(
      org.springframework.cloud.client.loadbalancer.LoadBalancerClient loadBalancerClient,
      org.springframework.cloud.loadbalancer.support.LoadBalancerClientFactory
          loadBalancerClientFactory,
      org.springframework.beans.factory.ObjectProvider<
              java.util.List<
                  org.springframework.cloud.openfeign.loadbalancer
                      .LoadBalancerFeignRequestTransformer>>
          transformers,
      @Value("${jeecg.feign.okhttp.connect-timeout-ms:4000}") int connectMs,
      @Value("${jeecg.feign.okhttp.read-timeout-ms:20000}") int readMs,
      @Value("${jeecg.feign.okhttp.max-idle-connections:40}") int maxIdle) {
    Dispatcher dispatcher = new Dispatcher();
    dispatcher.setMaxRequests(200);
    dispatcher.setMaxRequestsPerHost(40);
    ConnectionPool pool = new ConnectionPool(maxIdle, 5, TimeUnit.MINUTES);
    okhttp3.OkHttpClient ok =
        new okhttp3.OkHttpClient.Builder()
            .dispatcher(dispatcher)
            .connectionPool(pool)
            .connectTimeout(connectMs, TimeUnit.MILLISECONDS)
            .readTimeout(readMs, TimeUnit.MILLISECONDS)
            .writeTimeout(readMs, TimeUnit.MILLISECONDS)
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

  /** B10-3: retry only 5xx/408/425/429/network errors — never auth or business 4xx. */
  @Bean
  @Primary
  public Retryer feignRetryer(
      @Value("${jeecg.feign.retry.max-attempts:3}") int maxAttempts) {
    int attempts = Math.min(3, Math.max(1, maxAttempts));
    return new Retryer.Default(500L, 5000L, attempts) {
      @Override
      public void continueOrPropagate(RetryableException e) {
        int status = e.status();
        boolean retryable =
            status == 0
                || (status >= 500 && status <= 599)
                || status == 408
                || status == 425
                || status == 429;
        if (!retryable) {
          throw e;
        }
        super.continueOrPropagate(e);
      }
    };
  }

  @Override
  public void apply(RequestTemplate requestTemplate) {
    ServletRequestAttributes attributes = null;
    try {
      attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
    } catch (Throwable ex) {
      log.warn("Unable to access request context for Feign interceptor: {}");
    }

    if (null != attributes) {
      applyHttpRequestToken(requestTemplate, attributes);
    }
    // No elevated token path: S2S relies solely on X-Internal-Call-Secret (injected below).
    // Filter-side ATTR_TRUSTED_S2S_MODULE mark provides per-module S2S permission grants.
    if (attributes == null) {
      log.debug("S2S: no token; secret-only; path={}", requestTemplate.path());
    }

    // For special endpoints, sign verification — filter requests by URL [dict-table parameter signature verification]
    if (PathMatcherUtil.matches(
        StrUtil.isBlank(signUrls)
            ? Arrays.asList(PathMatcherUtil.SIGN_URL_LIST)
            : Arrays.asList(signUrls.split(",")),
        requestTemplate.path())) {
      try {
        log.info("============================ [begin] fegin api url ============================");
        log.info(requestTemplate.path());
        log.info(requestTemplate.method());
        String queryLine = requestTemplate.queryLine();
        String questionMark = "?";
        if (queryLine != null && queryLine.startsWith(questionMark)) {
          queryLine = queryLine.substring(1);
        }
        log.info(queryLine);
        if (requestTemplate.body() != null) {
          log.info(new String(requestTemplate.body(), StandardCharsets.UTF_8));
        }
        SortedMap<String, String> allParams =
            HttpUtils.getAllParams(
                requestTemplate.path(),
                queryLine,
                requestTemplate.body(),
                requestTemplate.method());
        String sign = SignUtil.getParamsSign(allParams);
        log.info(" Feign request params sign: {}", sign);
        log.info("============================ [end] fegin api url ============================");
        requestTemplate.header(CommonConstant.X_SIGN, sign);
        requestTemplate.header(
            CommonConstant.X_TIMESTAMP, String.valueOf(System.currentTimeMillis()));
      } catch (IOException e) {
        log.error("Feign request sign error", e);
      }
    }

    // A1-1: resolve from Environment at call time (Feign child context @Value can be stale/blank).
    String secret = resolveInternalCallSecret();
    if (StrUtil.isNotBlank(secret)) {
      requestTemplate.header("X-Internal-Call-Secret", secret);
    } else if (requestTemplate.path() != null
        && (requestTemplate.path().contains("/processengine/")
            || requestTemplate.path().contains("/processInstance/"))) {
      log.warn(
          "S2S Feign missing jeecg.internal.call.secret — processengine call will 401 path={}",
          requestTemplate.path());
    }
  }

  private String resolveInternalCallSecret() {
    if (StrUtil.isNotBlank(internalCallSecret)) {
      return internalCallSecret.trim();
    }
    try {
      if (org.jeecg.common.util.SpringContextUtils.getApplicationContext() == null) {
        return "";
      }
      String s =
          org.jeecg.common.util.SpringContextUtils.getApplicationContext()
              .getEnvironment()
              .getProperty("jeecg.internal.call.secret", "");
      return StrUtil.isBlank(s) ? "" : s.trim();
    } catch (Exception e) {
      return "";
    }
  }

  private void applyHttpRequestToken(
      RequestTemplate requestTemplate, ServletRequestAttributes attributes) {
    HttpServletRequest request;
    try {
      request = attributes.getRequest();
    } catch (Throwable ex) {
      log.warn("Unable to resolve HttpServletRequest in Feign interceptor: {}");
      return;
    }
    if (request == null) {
      return;
    }

    String cookie = request.getHeader("Cookie");
    String tokenName = safeTokenName();

    boolean tokenCookieExists = false;
    if (cookie != null) {
      String[] cookies = cookie.split("; ");
      for (String c : cookies) {
        if (c.startsWith(tokenName + "=")) {
          tokenCookieExists = true;
          break;
        }
      }
    }

    if (!tokenCookieExists) {
      String tokenValue = safeTokenValueFromSa();
      if (StrUtil.isBlank(tokenValue)) {
        if (cookie != null && !cookie.isEmpty()) {
          requestTemplate.header("Cookie", cookie);
        }
      } else {
        String newCookie = tokenName + "=" + tokenValue;
        if (cookie != null && !cookie.isEmpty()) {
          newCookie = cookie + "; " + newCookie;
        }
        requestTemplate.header("Cookie", newCookie);
      }
    } else {
      requestTemplate.header("Cookie", cookie);
    }

    requestTemplate.header("x_gateway_base_path", request.getHeader("x_gateway_base_path"));
    String origin = request.getHeader("Origin");
    if (StrUtil.isNotBlank(origin)) {
      requestTemplate.header("Origin", origin);
    }
    String referer = request.getHeader("Referer");
    if (StrUtil.isNotBlank(referer)) {
      requestTemplate.header("Referer", referer);
    }
  }

  private static String safeTokenName() {
    try {
      String name = StpUtil.getTokenName();
      return StrUtil.isNotBlank(name) ? name : "token";
    } catch (Throwable ex) {
      return "token";
    }
  }

  /** Never throw when Sa-Token request context is absent (schedulers / MQ). */
  private static String safeTokenValueFromSa() {
    try {
      return StpUtil.getTokenValue();
    } catch (Throwable ex) {
      return null;
    }
  }
}
