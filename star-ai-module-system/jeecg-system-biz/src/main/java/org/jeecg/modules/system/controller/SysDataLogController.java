package org.jeecg.modules.system.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.annotation.SaMode;
import java.util.ArrayList;
import java.util.List;

import jakarta.servlet.http.HttpServletRequest;

import org.jeecg.common.api.vo.Result;
import org.jeecg.common.constant.CommonConstant;
import org.jeecg.common.constant.SecurityRoleConstants;
import org.jeecg.common.system.query.QueryGenerator;
import org.jeecg.common.util.I18nUtil;
import org.jeecg.common.util.oConvertUtils;
import org.jeecg.modules.system.entity.SysDataLog;
import org.jeecg.modules.system.service.ISysDataLogService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import lombok.extern.slf4j.Slf4j;

/**
 * @Description: SystemDataLog
 *
 * @author: jeecg-boot
 */
@RestController
@RequestMapping(value = "/sys/dataLog", method = RequestMethod.POST)
@Slf4j
@SaCheckRole(value = {SecurityRoleConstants.ROLE_OPS_ADMIN, "admin"}, mode = SaMode.OR)
public class SysDataLogController {
  @Autowired private ISysDataLogService service;

  @RequestMapping(value = "/list", method = RequestMethod.POST)
  public Result<IPage<SysDataLog>> queryPageList(
      SysDataLog dataLog,
      @RequestParam(name = "pageNo", defaultValue = "1") Integer pageNo,
      @RequestParam(name = "pageSize", defaultValue = "10") Integer pageSize,
      HttpServletRequest req) {
    Result<IPage<SysDataLog>> result = new Result<IPage<SysDataLog>>();
    dataLog.setType(CommonConstant.DATA_LOG_TYPE_JSON);
    QueryWrapper<SysDataLog> queryWrapper =
        QueryGenerator.initQueryWrapper(dataLog, req.getParameterMap());
    Page<SysDataLog> page = new Page<SysDataLog>(pageNo, pageSize);
    IPage<SysDataLog> pageList = service.page(page, queryWrapper);
    log.info("Query current page: " + pageList.getCurrent());
    log.info("Query current page count: " + pageList.getSize());
    log.info("Query result count: " + pageList.getRecords().size());
    log.info("Total records: " + pageList.getTotal());
    result.setSuccess(true);
    result.setResult(pageList);
    return result;
  }

  /**
 * Query comparison data
 *
 * @param req
 * @return*/
  @RequestMapping(value = "/queryCompareList", method = RequestMethod.POST)
  public Result<List<SysDataLog>> queryCompareList(HttpServletRequest req) {
    Result<List<SysDataLog>> result = new Result<>();
    String dataId1 = req.getParameter("dataId1");
    String dataId2 = req.getParameter("dataId2");
    List<String> idList = new ArrayList<String>();
    idList.add(dataId1);
    idList.add(dataId2);
    try {
      List<SysDataLog> list = (List<SysDataLog>) service.listByIds(idList);
      result.setResult(list);
      result.setSuccess(true);
    } catch (Exception e) {
      log.error("operation failed", e);
    }
    return result;
  }

  /**
 * Query version info
 *
 * @param req
 * @return*/
  @RequestMapping(value = "/queryDataVerList", method = RequestMethod.POST)
  public Result<List<SysDataLog>> queryDataVerList(HttpServletRequest req) {
    Result<List<SysDataLog>> result = new Result<>();
    String dataTable = req.getParameter("dataTable");
    String dataId = req.getParameter("dataId");
    QueryWrapper<SysDataLog> queryWrapper = new QueryWrapper<SysDataLog>();
    queryWrapper.eq("data_table", dataTable);
    queryWrapper.eq("data_id", dataId);
    // update-begin-author:taoyan date:2022-7-26 for: AddQueryCondition-type
    String type = req.getParameter("type");
    if (oConvertUtils.isNotEmpty(type)) {
      queryWrapper.eq("type", type);
    }
    // Sort by time descending
    queryWrapper.orderByDesc("create_time");
    // update-end-author:taoyan date:2022-7-26 for:AddQueryCondition-type

    List<SysDataLog> list = service.list(queryWrapper);
    if (list == null || list.size() <= 0) {
      return Result.error(404, I18nUtil.getMessage("sysDataLog.versionNotFound"));
    } else {
      result.setResult(list);
      result.setSuccess(true);
    }
    return result;
  }
}
