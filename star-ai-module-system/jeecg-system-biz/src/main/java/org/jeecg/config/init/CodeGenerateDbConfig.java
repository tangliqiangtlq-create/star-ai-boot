package org.jeecg.config.init;

import com.alibaba.druid.filter.config.ConfigTools;
import jakarta.annotation.PostConstruct;
import org.apache.commons.lang3.StringUtils;
import org.jeecgframework.codegenerate.database.CodegenDatasourceConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

/**
 * @Description: code generator custom DB configuration
 * [With this class, online-mode DB connections use platform config; jeecg_database.properties is ignored;
 *  GUI-mode code generation still uses jeecg_database.properties]
 *  Note: for DM database, update ${spring.datasource.dynamic.datasource.master.url:} below
 * @author: scott
 * @date: February 18, 2021 16:30
 *
 * Important: if this class path or name changes, also update
 *  the annotation in org/jeecg/interceptor/OnlineRepairCodeGenerateDbConfig.java
 *  @ConditionalOnMissingClass("org.jeecg.config.init.CodeGenerateDbConfig")
 */
@Configuration
public class CodeGenerateDbConfig {
  private static final Logger log = LoggerFactory.getLogger(CodeGenerateDbConfig.class);

  @Value("${spring.datasource.dynamic.datasource.master.url:}")
  private String url;

  @Value("${spring.datasource.dynamic.datasource.master.username:}")
  private String username;

  @Value("${spring.datasource.dynamic.datasource.master.password:}")
  private String password;

  @Value("${spring.datasource.dynamic.datasource.master.driver-class-name:}")
  private String driverClassName;

  @Value("${spring.datasource.dynamic.datasource.master.druid.public-key:}")
  private String publicKey;

  @PostConstruct
  public void initCodeGenerateDbConfig() {
    if (StringUtils.isNotBlank(url)) {
      if (StringUtils.isNotBlank(publicKey)) {
        try {
          password = ConfigTools.decrypt(publicKey, password);
        } catch (Exception e) {
          log.error("operation failed", e);
          log.error(" Code generator database connection, database password decryption failed!");
        }
      }
      try {
        CodegenDatasourceConfig.initDbConfig(driverClassName, url, username, password);
        log.info(" Init CodeGenerate Config [ Get Db Config From application.yml ] ");
      } catch (NoClassDefFoundError e) {
        log.warn(" Skip CodeGenerate Db init, missing legacy dependency: {}");
      }
    }
  }
}
