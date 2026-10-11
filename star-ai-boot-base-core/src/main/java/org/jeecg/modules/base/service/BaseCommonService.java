package org.jeecg.modules.base.service;

import java.util.Map;
import org.jeecg.common.api.dto.LogDTO;
import org.jeecg.common.system.vo.LoginUser;

/**
 * commonAPI
 *
 * @author: jeecg-boot
 */
public interface BaseCommonService {

  /**
   * SaveLog
   *
   * @param logDTO
   */
  void addLog(LogDTO logDTO);

  /**
   * SaveLog
   *
   * @param logContent
   * @param logType
   * @param operateType
   * @param user
   */
  void addLog(String logContent, Integer logType, Integer operateType, LoginUser user);

  /**
   * SaveLog
   *
   * @param logContent
   * @param logType
   * @param operateType
   */
  void addLog(String logContent, Integer logType, Integer operateType);

  Map<String, String> lisCustomFieldValidation();
}
