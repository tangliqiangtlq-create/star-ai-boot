package org.jeecg.config;

import feign.Logger;
import java.util.Map;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile({"prod"})
public class H2FeignLevelStartupVerifier implements InitializingBean {

  @Autowired private ListableBeanFactory beanFactory;

  @Override
  public void afterPropertiesSet() {
    verifyLevels();
  }

  public void verifyLevels() {
    if (beanFactory == null) {
      return;
    }
    Map<String, Logger.Level> beans = beanFactory.getBeansOfType(Logger.Level.class, true, false);
    for (Map.Entry<String, Logger.Level> entry : beans.entrySet()) {
      Logger.Level level = entry.getValue();
      if (level == Logger.Level.FULL) {
        throw new BeanCreationException(
            "H2FeignLevelStartupVerifier: Feign Logger.Level.FULL detected in bean="
                + entry.getKey()
                + "; PROD profile requires BASIC or NONE; FULL leaks Authorization/Cookie/body into logs");
      }
    }
  }

  static boolean isFull(Logger.Level level) {
    return level == Logger.Level.FULL;
  }
}
