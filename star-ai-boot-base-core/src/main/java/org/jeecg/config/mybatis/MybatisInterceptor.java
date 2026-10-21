package org.jeecg.config.mybatis;

import cn.dev33.satoken.stp.StpUtil;
import java.lang.reflect.Field;
import java.util.Date;
import java.util.Properties;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.binding.MapperMethod.ParamMap;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.SqlCommandType;
import org.apache.ibatis.plugin.*;
import org.jeecg.common.config.TenantContext;
import org.jeecg.common.constant.TenantConstant;
import org.jeecg.common.system.vo.LoginUser;
import org.jeecg.common.util.ReflectionAccessUtil;
import org.jeecg.common.util.oConvertUtils;
import org.springframework.stereotype.Component;

/** MyBatis interceptor; auto-injects creator, create time, modifier, modify time @Author scott @Date 2019-01-19 */
@Slf4j
@Component
@Intercepts({
  @Signature(
      type = Executor.class,
      method = "update",
      args = {MappedStatement.class, Object.class})
})
public class MybatisInterceptor implements Interceptor {

  @Override
  public Object intercept(Invocation invocation) throws Throwable {
    MappedStatement mappedStatement = (MappedStatement) invocation.getArgs()[0];
    String sqlId = mappedStatement.getId();
    log.debug("------sqlId------" + sqlId);
    SqlCommandType sqlCommandType = mappedStatement.getSqlCommandType();
    Object parameter = invocation.getArgs()[1];
    log.debug("------sqlCommandType------" + sqlCommandType);

    if (parameter == null) {
      return invocation.proceed();
    }
    if (SqlCommandType.INSERT == sqlCommandType) {
      LoginUser sysUser = this.getLoginUser();
      Field[] fields = oConvertUtils.getAllFields(parameter);
      for (Field field : fields) {
        log.debug("------field.name------" + field.getName());
        try {
          if ("createBy".equals(field.getName()) || "createdBy".equals(field.getName())) {
            Object localCreateBy = ReflectionAccessUtil.getFieldValue(field, parameter);
            if ((localCreateBy == null || "".equals(localCreateBy)) && sysUser != null) {
              ReflectionAccessUtil.setFieldValue(field, parameter, sysUser.getId());
            }
          }
          if ("createTime".equals(field.getName()) || "createdTime".equals(field.getName())) {
            Object localCreateDate = ReflectionAccessUtil.getFieldValue(field, parameter);
            if (localCreateDate == null || "".equals(localCreateDate)) {
              ReflectionAccessUtil.setFieldValue(field, parameter, new Date());
            }
          }
          // INSERT must also populate update_* when column is NOT NULL without DEFAULT
          // (MySQL: Field 'update_time' doesn't have a default value).
          if ("updateBy".equals(field.getName()) || "updatedBy".equals(field.getName())) {
            Object localUpdateBy = ReflectionAccessUtil.getFieldValue(field, parameter);
            if ((localUpdateBy == null || "".equals(localUpdateBy)) && sysUser != null) {
              ReflectionAccessUtil.setFieldValue(field, parameter, sysUser.getId());
            }
          }
          if ("updateTime".equals(field.getName()) || "updatedTime".equals(field.getName())) {
            Object localUpdateDate = ReflectionAccessUtil.getFieldValue(field, parameter);
            if (localUpdateDate == null || "".equals(localUpdateDate)) {
              ReflectionAccessUtil.setFieldValue(field, parameter, new Date());
            }
          }
          if ("sysOrgCode".equals(field.getName())) {
            Object localSysOrgCode = ReflectionAccessUtil.getFieldValue(field, parameter);
            if ((localSysOrgCode == null || "".equals(localSysOrgCode)) && sysUser != null) {
              ReflectionAccessUtil.setFieldValue(field, parameter, sysUser.getOrgCode());
            }
          }
          if (MybatisPlusSaasConfig.OPEN_SYSTEM_TENANT_CONTROL
              && TenantConstant.TENANT_ID.equals(field.getName())) {
            Object localTenantId = ReflectionAccessUtil.getFieldValue(field, parameter);
            if (localTenantId == null) {
              String tenantId = TenantContext.getTenant();
              if (field.getType().equals(String.class)) {
                ReflectionAccessUtil.setFieldValue(field, parameter, tenantId);
              } else {
                ReflectionAccessUtil.setFieldValue(
                    field, parameter, oConvertUtils.getInt(tenantId, 0));
              }
            }
          }
        } catch (Exception ignored) {
        }
      }
    }
    if (SqlCommandType.UPDATE == sqlCommandType) {
      LoginUser sysUser = this.getLoginUser();
      Field[] fields;
      if (parameter instanceof ParamMap) {
        ParamMap<?> p = (ParamMap<?>) parameter;
        String et = "et";
        if (p.containsKey(et)) {
          parameter = p.get(et);
        } else {
          parameter = p.get("param1");
        }
        if (parameter == null) {
          return invocation.proceed();
        }
        fields = oConvertUtils.getAllFields(parameter);
      } else {
        fields = oConvertUtils.getAllFields(parameter);
      }

      for (Field field : fields) {
        log.debug("------field.name------" + field.getName());
        try {
          if (("updateBy".equals(field.getName()) || "updatedBy".equals(field.getName()))
              && sysUser != null) {
            ReflectionAccessUtil.setFieldValue(field, parameter, sysUser.getId());
          }
          if ("updateTime".equals(field.getName()) || "updatedTime".equals(field.getName())) {
            ReflectionAccessUtil.setFieldValue(field, parameter, new Date());
          }
        } catch (Exception e) {
          log.error("operation failed", e);
        }
      }
    }
    return invocation.proceed();
  }

  @Override
  public Object plugin(Object target) {
    return Plugin.wrap(target, this);
  }

  @Override
  public void setProperties(Properties properties) {}

  private LoginUser getLoginUser() {
    LoginUser sysUser = null;
    try {
      sysUser =
          StpUtil.isLogin()
              ? (LoginUser) StpUtil.getSession().get("loginUser")
              : null;
    } catch (Exception e) {
      sysUser = null;
    }
    return sysUser;
  }
}
