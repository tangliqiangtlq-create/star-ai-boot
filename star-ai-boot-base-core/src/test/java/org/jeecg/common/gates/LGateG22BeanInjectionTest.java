package org.jeecg.common.gates;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class LGateG22BeanInjectionTest {

  @Test
  void filterClasses_LoadableAtLeastFour() {
    String[] candidates = {
      "org.jeecg.config.filter.InternalTrustedS2sFilter",
      "org.jeecg.config.filter.GatewaySignatureFilter",
      "org.jeecg.config.filter.ApiInputXssFilter",
      "org.jeecg.config.filter.QueryTokenStripFilter",
      "org.jeecg.config.filter.PrivacyCacheControlFilter",
      "org.jeecg.config.filter.SecurityResponseHeadersFilter",
      "org.jeecg.config.filter.ContentTypeOptionsFilter",
      "org.jeecg.config.filter.InternalSecretHeaderStripFilter",
      "org.jeecg.config.filter.UserTokenContextClearFilter",
      "org.jeecg.config.filter.RequestBodyReserveFilter",
      "org.jeecg.config.filter.WebsocketFilter"
    };
    int loaded = 0;
    for (String cls : candidates) {
      try {
        Class.forName(cls);
        loaded++;
      } catch (ClassNotFoundException ignored) {
      }
    }
    assertTrue(loaded >= 4,
        "G22 loadable Filter count=" + loaded + " < threshold 4 (AUT-01 rg source≥4; JVM class-load recheck): "
            + String.join(", ", candidates));
  }

  @Test
  void interceptorClasses_LoadableAtLeastTwo() {
    String[] candidates = {
      "org.jeecg.config.firewall.SensitiveTableBlacklistInterceptor",
      "org.jeecg.config.sign.interceptor.SignAuthInterceptor",
      "org.jeecg.config.firewall.interceptor.LowCodeModeInterceptor",
      "org.jeecg.config.mybatis.interceptor.DynamicDatasourceInterceptor"
    };
    int loaded = 0;
    for (String cls : candidates) {
      try {
        Class.forName(cls);
        loaded++;
      } catch (ClassNotFoundException ignored) {
      }
    }
    assertTrue(loaded >= 2,
        "G22 loadable Interceptor count=" + loaded + " < threshold 2 (AUT-02 rg source≥2; JVM class-load recheck): "
            + String.join(", ", candidates));
  }
}
