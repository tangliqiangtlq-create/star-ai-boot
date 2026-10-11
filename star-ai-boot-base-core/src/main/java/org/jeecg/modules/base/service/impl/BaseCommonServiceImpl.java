package org.jeecg.modules.base.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.jeecg.common.api.CommonAPI;
import org.jeecg.common.api.dto.LogDTO;
import org.jeecg.common.constant.enums.ClientTerminalTypeEnum;
import org.jeecg.common.system.vo.DictModel;
import org.jeecg.common.system.vo.LoginUser;
import org.jeecg.common.util.BrowserUtils;
import org.jeecg.common.util.IpUtils;
import org.jeecg.common.util.SpringContextUtils;
import org.jeecg.common.util.oConvertUtils;
import org.jeecg.modules.base.mapper.BaseCommonMapper;
import org.jeecg.modules.base.service.BaseCommonService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

/**
 * @Description: common implementation
 *
 * @author: jeecg-boot
 */
@Service
@Slf4j
public class BaseCommonServiceImpl implements BaseCommonService {

  @Resource private BaseCommonMapper baseCommonMapper;

  /**
   * CommonAPI resolves to {@code SysBaseApiImpl} in the system service (local write into
   * mpay_system.sys_log), and to an ISysBaseAPI Feign proxy in dcs/processengine (S2S call into
   * the system service). Either way, this method must NOT touch the local sys_log copy — under
   * the per-service sharding scheme, sys_log is owned by the system service only.
   */
  @Lazy @Autowired private CommonAPI commonAPI;

  @Override
  public void addLog(LogDTO logDTO) {
    if (oConvertUtils.isEmpty(logDTO.getId())) {
      logDTO.setId(String.valueOf(IdWorker.getId()));
    }
    if (logDTO.getCreateTime() == null) {
      logDTO.setCreateTime(new Date());
    }
    // Save log (exception handling to prevent oversized data storage failure from breaking
    // business) JT-238. Route through CommonAPI so the write goes to mpay_system.sys_log — in
    // system process this resolves to a local mapper write; in dcs/processengine it goes out
    // via Feign as an internal S2S call (CustomFeignConfig injects X-Internal-Call-Secret).
    try {
      commonAPI.addLog(logDTO);
    } catch (Exception e) {
      int len = logDTO.getLogContent() == null ? 0 : logDTO.getLogContent().length();
      log.warn("addLog via CommonAPI failed; LogContent length={}, err={}", len, e.getMessage());
    }
  }

  @Override
  public void addLog(String logContent, Integer logType, Integer operatetype, LoginUser user) {
    LogDTO sysLog = new LogDTO();
    sysLog.setId(String.valueOf(IdWorker.getId()));
    // Description on annotation; operation log content
    sysLog.setLogContent(logContent);
    sysLog.setLogType(logType);
    sysLog.setOperateType(operatetype);
    try {
      // Getrequest
      HttpServletRequest request = SpringContextUtils.getHttpServletRequest();
      // Set IP address
      sysLog.setIp(IpUtils.getIpAddr(request));

      try {
        // Set client
        if (BrowserUtils.isDesktop(request)) {
          sysLog.setClientType(ClientTerminalTypeEnum.PC.getKey());
        } else {
          sysLog.setClientType(ClientTerminalTypeEnum.APP.getKey());
        }
      } catch (Exception e) {
        // log.error("operation failed", e);
      }
    } catch (Exception e) {
      sysLog.setIp("127.0.0.1");
    }
    // Get logged-in user info
    if (user == null) {
      try {
        user =  (LoginUser) StpUtil.getSession().get("loginUser");
      } catch (Exception e) {
        // log.error("operation failed", e);
      }
    }
    if (user != null) {
      sysLog.setUserid(user.getUsername());
      sysLog.setUsername(user.getRealname());
    }
    sysLog.setCreateTime(new Date());
    // Save log (exception handling to prevent oversized data storage failure from breaking
    // business) JT-238. Route through CommonAPI so the write goes to mpay_system.sys_log — in
    // system process this resolves to a local mapper write; in dcs/processengine it goes out
    // via Feign as an internal S2S call (CustomFeignConfig injects X-Internal-Call-Secret).
    try {
      commonAPI.addLog(sysLog);
    } catch (Exception e) {
      int len = sysLog.getLogContent() == null ? 0 : sysLog.getLogContent().length();
      log.warn("addLog via CommonAPI failed; LogContent length={}, err={}", len, e.getMessage());
    }
  }

  @Override
  public void addLog(String logContent, Integer logType, Integer operateType) {
    addLog(logContent, logType, operateType, null);
  }

  @Override
  @Cacheable(cacheNames = "dcs_BaseCommonServiceImpl_lisCustomFieldValidation")
  public Map<String, String> lisCustomFieldValidation() {

    Map<String, String> custom_field_validation = new HashMap<>();

    // sys_dict / sys_dict_item are owned by the system shard (mpay_system). Go through
    // CommonAPI#queryDictItemsByCode: local dict read inside the system service, Feign S2S call in
    // dcs/processengine — no local schema mirror is required (SHARD-BAN T5-06/07, gate 1.14).
    List<DictModel> items = commonAPI.queryDictItemsByCode("custom_field_validation");

    if (items != null) {
      for (DictModel item : items) {
        if (item == null || item.getValue() == null || item.getText() == null) {
          continue;
        }
        custom_field_validation.put(item.getValue().toLowerCase(), item.getText());
      }
    }

    return custom_field_validation;
  }
}
