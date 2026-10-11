package org.jeecg.config;

import cn.dev33.satoken.stp.StpLogic;
import org.jeecg.config.shiro.StpLogicJwtForRs256;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CustomSaTokenConfig {

  @Bean
  public StpLogic getStpLogicJwt(JeecgBaseConfig jeecgBaseConfig) {

    return new StpLogicJwtForRs256(jeecgBaseConfig.getApiSubdomainAddress());
  }
}
