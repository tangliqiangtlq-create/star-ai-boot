package org.jeecg.config;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

@Order(Ordered.HIGHEST_PRECEDENCE)
public class H2FeignHeaderStrippingInterceptor implements RequestInterceptor {

  private static final Logger log = LoggerFactory.getLogger(H2FeignHeaderStrippingInterceptor.class);

  public static final List<String> STRIP_HEADERS =
      Collections.unmodifiableList(
          Arrays.asList(
              "Authorization",
              "Cookie",
              "Set-Cookie",
              "X-OTP",
              "X-MFA",
              "X-Gateway-Secret"));

  private final Set<String> stripLower;

  public H2FeignHeaderStrippingInterceptor() {
    stripLower = new HashSet<>();
    for (String h : STRIP_HEADERS) {
      stripLower.add(h.toLowerCase(Locale.ROOT));
    }
  }

  @Override
  public void apply(RequestTemplate template) {
    if (template == null) {
      return;
    }
    Map<String, Collection<String>> headers = template.headers();
    if (headers == null || headers.isEmpty()) {
      return;
    }
    Set<String> toRemove = new HashSet<>();
    for (String headerName : headers.keySet()) {
      if (headerName == null) {
        continue;
      }
      if (stripLower.contains(headerName.toLowerCase(Locale.ROOT))) {
        toRemove.add(headerName);
      }
    }
    for (String headerName : toRemove) {
      template.removeHeader(headerName);
      if (log.isDebugEnabled()) {
        log.debug("H2FeignHeaderStripping: stripped header={}", headerName);
      }
    }
  }

  public static List<String> getStripHeaders() {
    return STRIP_HEADERS;
  }
}
