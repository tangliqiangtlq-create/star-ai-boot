package org.jeecg.config.mybatis;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.inner.InnerInterceptor;
import com.baomidou.mybatisplus.core.toolkit.PluginUtils;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;

import cn.hutool.core.util.ObjectUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import com.baomidou.mybatisplus.autoconfigure.ConfigurationCustomizer;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.DynamicTableNameInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.type.JdbcType;
import org.jeecg.common.constant.CommonConstant;
import org.jeecg.common.system.security.C4TenantLineHandler;
import org.jeecg.common.handler.JeecgJsonTypeHandler;
import org.jeecg.config.firewall.DangerousSqlStringInspector;
import org.jeecg.config.firewall.SensitiveTableBlacklistInterceptor;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Single data source config (effective when jeecg.datasource.open = false) @Author zhoujf */
@Slf4j
@Configuration
@MapperScan(value = {"org.jeecg.**.mapper*"})
public class MybatisPlusSaasConfig {

  /**
   * Whether tenant isolation is enabled for system module
   * Scope: user, role, department, my department, dictionary, category dictionary, multi-datasource, position, announcement
   *
   * <p>Implementation:
   * 1. User table tenant ID isolation via hard-coded logic
   * 2. Role, department, my department, dictionary, category dictionary, multi-datasource, position, announcement use hard-coded logic plus TENANT_TABLE config for safer isolation
   * 3. Menu and tenant tables are not tenant-isolated
   * 4. MybatisInterceptor auto-injects tenant ID on CRUD
   */
  public static final Boolean OPEN_SYSTEM_TENANT_CONTROL = false;

  /** Tables requiring multi-tenancy must add a tenant_id column */
  public static final List<String> TENANT_TABLE = new ArrayList<String>();

  static {
    // 1. Configure tables requiring tenant isolation here
    if (MybatisPlusSaasConfig.OPEN_SYSTEM_TENANT_CONTROL) {
      // a. System management tables
      // TENANT_TABLE.add("sys_role");
      // TENANT_TABLE.add("sys_user_role");
      TENANT_TABLE.add("sys_depart");
      TENANT_TABLE.add("sys_category");
      TENANT_TABLE.add("sys_data_source");
      TENANT_TABLE.add("sys_position");
      // TENANT_TABLE.add("sys_announcement");
    }

    // 2. Sample test
    // TENANT_TABLE.add("demo");
    // 3. Online tenant isolation test
    // TENANT_TABLE.add("ceapp_issue");
  }

  @Bean
  public MybatisPlusInterceptor mybatisPlusInterceptor() {
    int tenantLineHandlerOrder = 2;
    log.info("SEC-C4-ORDER tenant_line_handler_order=2");
    MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
    interceptor.addInnerInterceptor(new DangerousSqlStringInspector());
    interceptor.addInnerInterceptor(new TenantLineInnerInterceptor(new C4TenantLineHandler()));
    interceptor.addInnerInterceptor(new SensitiveTableBlacklistInterceptor());
    interceptor.addInnerInterceptor(dynamicTableNameInnerInterceptor());
    // RC-K8-c: page size hard cap at 500 — never throw, silently truncate.
    interceptor.addInnerInterceptor(
        new InnerInterceptor() {
          @Override
          public void beforeQuery(
              Executor executor,
              MappedStatement ms,
              Object parameter,
              RowBounds rowBounds,
              ResultHandler resultHandler,
              BoundSql boundSql) {
            IPage<?> page = null;
            if (parameter instanceof IPage<?>) {
              page = (IPage<?>) parameter;
            } else if (parameter instanceof java.util.Map<?, ?>) {
              java.util.Map<?, ?> m = (java.util.Map<?, ?>) parameter;
              for (Object v : m.values()) {
                if (v instanceof IPage<?>) {
                  page = (IPage<?>) v;
                  break;
                }
              }
            }
            if (page != null && page.getSize() > 500) {
              page.setSize(Math.min(page.getSize(), 500));
            }
          }
        });
    PaginationInnerInterceptor paginationInnerInterceptor = new PaginationInnerInterceptor();
    paginationInnerInterceptor.setMaxLimit(500L);
    interceptor.addInnerInterceptor(paginationInnerInterceptor);
    interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());
    return interceptor;
  }

  /**
   * Dynamic table name switch interceptor; adapts vue2/vue3 sharing one table with multiple names, e.g. sys_role_index → sys_role_index_v3 in vue3
   *
   * @return
   */
  private DynamicTableNameInnerInterceptor dynamicTableNameInnerInterceptor() {
    DynamicTableNameInnerInterceptor dynamicTableNameInnerInterceptor =
        new DynamicTableNameInnerInterceptor();
    dynamicTableNameInnerInterceptor.setTableNameHandler(
        (sql, tableName) -> {
          // Get table names requiring dynamic parsing
          String dynamicTableName = ThreadLocalDataHelper.get(CommonConstant.DYNAMIC_TABLE_NAME);
          // Apply dynamic table name logic only when dynamicTableName is non-empty; otherwise return original table name
          if (ObjectUtil.isNotEmpty(dynamicTableName) && dynamicTableName.equals(tableName)) {
            // Get version flag from front-end
            Object version = ThreadLocalDataHelper.get(CommonConstant.VERSION);
            if (ObjectUtil.isNotEmpty(version)) {
              // Table name rule (originalTableName + underscore + frontend version)
              return tableName + "_" + version;
            }
          }
          return tableName;
        });
    return dynamicTableNameInnerInterceptor;
  }

  @Bean
  public ConfigurationCustomizer mybatisConfigurationCustomizer() {
    return configuration -> {
      configuration
          .getTypeHandlerRegistry()
          .register(JSONObject.class, JdbcType.VARCHAR, JeecgJsonTypeHandler.class);
      configuration
          .getTypeHandlerRegistry()
          .register(JSONArray.class, JdbcType.VARCHAR, JeecgJsonTypeHandler.class);
    };
  }

  //    /**
  //     * Will be removed next version; configured now to avoid cache issues
  //     * @return
  //     */
  //    @Bean
  //    public ConfigurationCustomizer configurationCustomizer() {
  //        return configuration -> configuration.setUseDeprecatedExecutor(false);
  //    }
  //    /**
  //     * mybatis-plus SQL performance plugin [can be disabled in production]
  //     */
  //    @Bean
  //    public PerformanceInterceptor performanceInterceptor() {
  //        return new PerformanceInterceptor();
  //    }

}
