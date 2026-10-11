package org.jeecg.common.util.log;

import ch.qos.logback.classic.LoggerContext;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.BeanInitializationException;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile({"prod", "staging", "uat"})
public class H1TurboCoverageVerifier implements InitializingBean {

  public static final List<String> REQUIRED_CONTEXT_NAMES =
      new ArrayList<>(
          java.util.Arrays.asList(
              "jeecg-system-cloud", "jeecg-cloud-gateway", "jeecg-cloud-dcs", "jeecg-cloud-xxljob"));

  private final String[] expectedContexts;

  public H1TurboCoverageVerifier() {
    this(REQUIRED_CONTEXT_NAMES.toArray(new String[0]));
  }

  H1TurboCoverageVerifier(String... expectedContexts) {
    this.expectedContexts = expectedContexts;
  }

  @Override
  public void afterPropertiesSet() {
    verifyCurrentContext();
  }

  public void verifyCurrentContext() {
    if (!(LoggerFactory.getILoggerFactory() instanceof LoggerContext context)) {
      throw new BeanInitializationException(
          "H1TurboCoverageVerifier: LoggerContext not available; H1 TurboFilter cannot be verified");
    }
    boolean hasH1 =
        context.getTurboFilterList().stream()
            .anyMatch(f -> f instanceof H1SensitiveTurboFilter);
    if (!hasH1) {
      throw new BeanInitializationException(
          "H1TurboCoverageVerifier: H1SensitiveTurboFilter NOT injected into LoggerContext name="
              + context.getName()
              + "; H1LogbackTurboConfigurator must run at EnvironmentPreparedEvent phase");
    }
  }

  static boolean hasH1InContext() {
    if (!(LoggerFactory.getILoggerFactory() instanceof LoggerContext context)) {
      return false;
    }
    return context.getTurboFilterList().stream()
        .anyMatch(f -> f instanceof H1SensitiveTurboFilter);
  }
}
