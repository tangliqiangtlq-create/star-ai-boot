package org.jeecg.config.sentinel;

import com.alibaba.csp.sentinel.adapter.spring.webmvc_v6x.callback.BlockExceptionHandler;
import com.alibaba.csp.sentinel.adapter.spring.webmvc_v6x.callback.RequestOriginParser;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.commons.lang3.StringUtils;
import org.jeecg.common.enums.SentinelErrorInfoEnum;
import org.jeecg.common.util.IpUtils;
import org.jeecg.config.JeecgCloudCondition;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;

/**
 * Sentinel WebMVC callbacks under Spring Boot 3 / Jakarta.
 *
 * <p>{@code CustomSentinelExceptionHandler} / {@code DefaultRequestOriginParser} in
 * {@code jeecg-boot-starter-cloud} still use {@code javax.servlet} and legacy
 * {@code sentinel-spring-webmvc-adapter}; parsing {@code @Configuration} on Boot 3 triggers
 * {@code ClassNotFoundException: javax.servlet.http.HttpServletRequest}. This provides a v6x replacement.
 */
@Configuration
@Conditional(JeecgCloudCondition.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass(BlockExceptionHandler.class)
public class SentinelWebMvcJakartaConfiguration {

  @Bean
  @ConditionalOnMissingBean(BlockExceptionHandler.class)
  public BlockExceptionHandler customSentinelBlockExceptionHandler() {
    return SentinelWebMvcJakartaConfiguration::handleBlock;
  }

  @Bean
  @ConditionalOnMissingBean(RequestOriginParser.class)
  public RequestOriginParser customSentinelRequestOriginParser() {
    return request -> {
      String origin = request.getParameter("origin");
      if (StringUtils.isNotEmpty(origin)) {
        return origin;
      }
      return IpUtils.getIpAddr(request);
    };
  }

  private static void handleBlock(
      HttpServletRequest request,
      HttpServletResponse response,
      String resourceName,
      BlockException ex)
      throws Exception {
    SentinelErrorInfoEnum info = SentinelErrorInfoEnum.getErrorByException(ex);
    String msg = info != null ? info.getError() : "Unknown rate limit fallback";
    response.setStatus(HttpServletResponse.SC_OK);
    response.setCharacterEncoding("utf-8");
    response.setContentType("application/json;charset=utf-8");
    response.getWriter().write("{\"code\":500,\"message\":\"" + escapeJson(msg) + "\"}");
  }

  private static String escapeJson(String value) {
    if (value == null) {
      return "";
    }
    return value
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "\\r")
        .replace("\t", "\\t");
  }
}
