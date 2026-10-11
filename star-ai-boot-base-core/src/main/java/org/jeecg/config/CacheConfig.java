package org.jeecg.config;

import org.springframework.cache.interceptor.KeyGenerator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CacheConfig {

  @Bean("customKeyGenerator")
  public KeyGenerator keyGenerator() {
    return (target, method, params) -> {
      StringBuilder key = new StringBuilder();
      for (Object param : params) {
        if (param == null) {
          continue;
        }
        if (param instanceof String) {
          key.append(".").append(((String) param).toLowerCase());
        } else {
          key.append(".").append(param);
        }
      }
      return key.toString();
    };
  }
}
