package org.jeecg.modules.system.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.annotation.SaMode;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.util.Arrays;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.jeecg.common.api.vo.Result;
import org.jeecg.common.constant.SecurityRoleConstants;
import org.jeecg.common.system.query.QueryGenerator;
import org.jeecg.common.util.I18nUtil;
import org.jeecg.common.util.oConvertUtils;
import org.jeecg.modules.system.entity.SysLog;
import org.jeecg.modules.system.entity.SysRole;
import org.jeecg.modules.system.service.ISysLogService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * System log table front-end controller @Author zhangweijian
 *
 * @since 2018-12-26*/
@RestController
@RequestMapping(value = "/sys/log", method = RequestMethod.POST)
@Slf4j
@SaCheckRole(value = {SecurityRoleConstants.ROLE_OPS_ADMIN, "admin"}, mode = SaMode.OR)
public class SysLogController {

  @Autowired private ISysLogService sysLogService;

  /** Clear all */
  private static final String ALL_ClEAR = "allclear";

  /**
   * @Function: Query log records
   *
   * @param syslog
   * @param pageNo
   * @param pageSize
   * @param req
   * @return
   */
  @RequestMapping(value = "/list", method = RequestMethod.POST)
  // @SaCheckPermission("system:log:list")
  public Result<IPage<SysLog>> queryPageList(
      SysLog syslog,
      @RequestParam(name = "pageNo", defaultValue = "1") Integer pageNo,
      @RequestParam(name = "pageSize", defaultValue = "10") Integer pageSize,
      HttpServletRequest req) {
    Result<IPage<SysLog>> result = new Result<IPage<SysLog>>();
    QueryWrapper<SysLog> queryWrapper =
        QueryGenerator.initQueryWrapper(syslog, req.getParameterMap());
    Page<SysLog> page = new Page<SysLog>(pageNo, pageSize);
    // Log keywords
    String keyWord = req.getParameter("keyWord");
    if (oConvertUtils.isNotEmpty(keyWord)) {
      queryWrapper.like("log_content", keyWord);
    }
    // TODO Filter logic handling
    // TODO begin/end logic handling
    // TODO Powerful feature: frontend sends field name string; backend returns only those fields
    // Assign create time / created by
    IPage<SysLog> pageList = sysLogService.page(page, queryWrapper);
    log.info("Query current page: " + pageList.getCurrent());
    log.info("Query current page count: " + pageList.getSize());
    log.info("Query result count: " + pageList.getRecords().size());
    log.info("Total records: " + pageList.getTotal());
    result.setSuccess(true);
    result.setResult(pageList);
    return result;
  }

  /**
   * @Function: Delete single log record
   *
   * @param id
   * @return
   */
  @RequestMapping(value = "/delete", method = RequestMethod.DELETE)
  // @SaCheckPermission("system:log:delete")
  public Result<SysLog> delete(@RequestParam(name = "id", required = true) String id) {
    Result<SysLog> result = new Result<SysLog>();
    SysLog sysLog = sysLogService.getById(id);
    if (sysLog == null) {
      return Result.error(404, I18nUtil.getMessage("sysLog.entityNotFound"));
    } else {
      boolean ok = sysLogService.removeById(id);
      if (ok) {
        result.success(I18nUtil.getMessage("sysLog.deleteSuccess"));
      }
    }
    return result;
  }

  /**
   * @Function: Batch delete or clear all log records
   *
   * @param ids
   * @return
   */
  @RequestMapping(value = "/deleteBatch", method = RequestMethod.DELETE)
  // @SaCheckPermission("system:log:deleteBatch")
  public Result<SysRole> deleteBatch(@RequestParam(name = "ids", required = true) String ids) {
    Result<SysRole> result = new Result<SysRole>();
    if (ids == null || "".equals(ids.trim())) {
      return Result.error(400, I18nUtil.getMessage("sysLog.paramNotRecognized"));
    } else {
      if (ALL_ClEAR.equals(ids)) {
        this.sysLogService.removeAll();
        result.success(I18nUtil.getMessage("sysLog.clearSuccess"));
      }
      this.sysLogService.removeByIds(Arrays.asList(ids.split(",")));
      result.success(I18nUtil.getMessage("sysLog.deleteSuccess"));
    }
    return result;
  }
}
