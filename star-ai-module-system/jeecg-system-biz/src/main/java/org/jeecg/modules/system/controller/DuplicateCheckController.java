package org.jeecg.modules.system.controller;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jeecg.common.api.vo.Result;
import org.jeecg.common.util.I18nUtil;
import org.jeecg.modules.system.model.DuplicateCheckVo;
import org.jeecg.modules.system.service.ISysDictService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;

/**
 * @Title: DuplicateCheckAction @Description: Duplicate check utility @Author Zhang Daihao @Date 2019-03-25 @Version V1.0
 */
@Slf4j
@RestController
@RequestMapping(value = "/sys/duplicate", method = RequestMethod.POST)
@Api(tags = "Duplicate check")
public class DuplicateCheckController {

  @Autowired ISysDictService sysDictService;

  /**
   * Check whether data exists in the system
   *
   * @return
   */
  @RequestMapping(value = "/check", method = RequestMethod.POST)
  @ApiOperation("Duplicate check API")
  public Result<String> doDuplicateCheck(
      DuplicateCheckVo duplicateCheckVo, HttpServletRequest request) {
    log.debug("----duplicate check------：" + duplicateCheckVo.toString());

    // 1. If value is empty, return immediately
    if (StringUtils.isEmpty(duplicateCheckVo.getFieldVal())) {
      Result rs = new Result();
      rs.setCode(500);
      rs.setSuccess(true);
      rs.setMessage(I18nUtil.getMessage("duplicateCheck.dataEmpty"));
      return rs;
    }

    // 2.ReturnResult
    if (sysDictService.duplicateCheckData(duplicateCheckVo)) {
      // Value available
      return Result.ok(I18nUtil.getMessage("duplicateCheck.valueAvailable"));
    } else {
      // Value unavailable
      log.info("This value is unavailable, already exists in the system!");
      return Result.error(I18nUtil.getMessage("duplicateCheck.valueNotAvailable"));
    }
  }
}
