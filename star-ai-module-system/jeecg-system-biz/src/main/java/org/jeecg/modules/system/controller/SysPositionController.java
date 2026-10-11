package org.jeecg.modules.system.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.annotation.SaMode;
import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.util.RandomUtil;
import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.jeecg.common.api.vo.Result;
import org.jeecg.common.aspect.annotation.AutoLog;
import org.jeecg.common.config.TenantContext;
import org.jeecg.common.constant.CommonConstant;
import org.jeecg.common.constant.SecurityRoleConstants;
import org.jeecg.common.system.query.QueryGenerator;
import org.jeecg.common.system.vo.LoginUser;
import org.jeecg.common.util.I18nUtil;
import org.jeecg.common.util.ImportExcelUtil;
import org.jeecg.common.util.oConvertUtils;
import org.jeecg.config.mybatis.MybatisPlusSaasConfig;
import org.jeecg.modules.system.entity.SysPosition;
import org.jeecg.modules.system.entity.SysUser;
import org.jeecg.modules.system.service.ISysPositionService;
import org.jeecg.modules.system.service.ISysUserPositionService;
import org.jeecg.modules.system.service.ISysUserService;
import org.jeecgframework.poi.excel.ExcelImportUtil;
import org.jeecgframework.poi.excel.def.NormalExcelConstants;
import org.jeecgframework.poi.excel.entity.ExportParams;
import org.jeecgframework.poi.excel.entity.ImportParams;
import org.jeecgframework.poi.excel.view.JeecgEntityExcelView;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;
import org.springframework.web.servlet.ModelAndView;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @Description: Position table @Author: jeecg-boot @Date: 2019-09-19 @Version: V1.0*/
@Slf4j
@Api(tags = "Job title table")
@RestController
@RequestMapping("/sys/position")
@SaCheckRole(value = {SecurityRoleConstants.ROLE_OPS_ADMIN, "admin"}, mode = SaMode.OR)
public class SysPositionController {

  @Autowired private ISysPositionService sysPositionService;

  @Autowired private ISysUserPositionService userPositionService;

  @Autowired private ISysUserService userService;

  /**
 * Paginated list query
 *
 * @param sysPosition
 * @param pageNo
 * @param pageSize
 * @param req
 * @return*/
  @AutoLog(value = "Job title table - Paginated list query")
  @ApiOperation(value = "Job title table - Paginated list query", notes = "Job title table - Paginated list query")
  @PostMapping(value = "/list")
  public Result<IPage<SysPosition>> queryPageList(
      SysPosition sysPosition,
      @RequestParam(name = "pageNo", defaultValue = "1") Integer pageNo,
      @RequestParam(name = "pageSize", defaultValue = "10") Integer pageSize,
      HttpServletRequest req) {
    Result<IPage<SysPosition>> result = new Result<IPage<SysPosition>>();
    // ------------------------------------------------------------------------------------------------
    // Whether multi-tenant data isolation is enabled for system admin [SAAS mode]
    if (MybatisPlusSaasConfig.OPEN_SYSTEM_TENANT_CONTROL) {
      sysPosition.setTenantId(oConvertUtils.getInt(TenantContext.getTenant(), 0));
    }
    // ------------------------------------------------------------------------------------------------
    QueryWrapper<SysPosition> queryWrapper =
        QueryGenerator.initQueryWrapper(sysPosition, req.getParameterMap());
    Page<SysPosition> page = new Page<SysPosition>(pageNo, pageSize);
    IPage<SysPosition> pageList = sysPositionService.page(page, queryWrapper);
    result.setSuccess(true);
    result.setResult(pageList);
    return result;
  }

  /**
   * Add
   *
   * @param sysPosition
   * @return
   */
  @AutoLog(value = "Job title table - Add")
  @ApiOperation(value = "Job title table - Add", notes = "Job title table - Add")
  @PostMapping(value = "/add")
  public Result<SysPosition> add(@RequestBody SysPosition sysPosition) {
    Result<SysPosition> result = new Result<SysPosition>();
    try {
      // update-begin---author:wangshuai ---date:20230313
      // for: [QQYUN-4558] vue3 position feature adjustment: remove code and level, can hide first ------------
      // Code is empty; no multi-tenant isolation check needed
      if (oConvertUtils.isEmpty(sysPosition.getCode())) {
        // Generate 10-digit position code
        sysPosition.setCode(RandomUtil.randomString(10));
      }
      // update-end---author:wangshuai ---date:20230313
      // for: [QQYUN-4558] vue3 position feature adjustment: remove code and level, can hide first -------------
      sysPositionService.save(sysPosition);
      result.success(I18nUtil.getMessage("sysPosition.addSuccess"));
    } catch (Exception e) {
      log.error("operation failed", e);
      result.error500(I18nUtil.getMessage("sysUser.operationFailed"));
    }
    return result;
  }

  /**
   * Edit
   *
   * @param sysPosition
   * @return
   */
  @AutoLog(value = "Job title table - Edit")
  @ApiOperation(value = "Job title table - Edit", notes = "Job title table - Edit")
  @RequestMapping(
      value = "/edit",
      method = {RequestMethod.PUT, RequestMethod.POST})
  public Result<SysPosition> edit(@RequestBody SysPosition sysPosition) {
    Result<SysPosition> result = new Result<SysPosition>();
    SysPosition sysPositionEntity = sysPositionService.getById(sysPosition.getId());
    if (sysPositionEntity == null) {
      return Result.error(404, I18nUtil.getMessage("sysUser.entityNotFound"));
    } else {
      boolean ok = sysPositionService.updateById(sysPosition);
      // TODO What does returning false mean?
      if (ok) {
        result.success(I18nUtil.getMessage("sysPosition.modifySuccess"));
      }
    }

    return result;
  }

  /**
   * Delete by id
   *
   * @param id
   * @return
   */
  @AutoLog(value = "Job title table - Delete by id")
  @ApiOperation(value = "Job title table - Delete by id", notes = "Job title table - Delete by id")
  @DeleteMapping(value = "/delete")
  public Result<?> delete(@RequestParam(name = "id", required = true) String id) {
    try {
      sysPositionService.removeById(id);
      // Delete user-position relation table
      userPositionService.removeByPositionId(id);
    } catch (Exception e) {
      log.error("Delete failed");
      return Result.error(I18nUtil.getMessage("sysPosition.deleteFailed"));
    }
    return Result.ok(I18nUtil.getMessage("sysPosition.deleteSuccess"));
  }

  /**
   * Batch delete
   *
   * @param ids
   * @return
   */
  @AutoLog(value = "Job title table - Batch delete")
  @ApiOperation(value = "Job title table - Batch delete", notes = "Job title table - Batch delete")
  @DeleteMapping(value = "/deleteBatch")
  public Result<SysPosition> deleteBatch(@RequestParam(name = "ids", required = true) String ids) {
    Result<SysPosition> result = new Result<SysPosition>();
    if (ids == null || "".equals(ids.trim())) {
      return Result.error(400, I18nUtil.getMessage("sysPosition.paramNotRecognized"));
    } else {
      this.sysPositionService.removeByIds(Arrays.asList(ids.split(",")));
      result.success(I18nUtil.getMessage("sysPosition.deleteSuccess"));
    }
    return result;
  }

  /**
   * Query by id
   *
   * @param id
   * @return
   */
  @AutoLog(value = "Job title table - Query by id")
  @ApiOperation(value = "Job title table - Query by id", notes = "Job title table - Query by id")
  @PostMapping(value = "/queryById")
  public Result<SysPosition> queryById(@RequestParam(name = "id", required = true) String id) {
    Result<SysPosition> result = new Result<SysPosition>();
    SysPosition sysPosition = sysPositionService.getById(id);
    if (sysPosition == null) {
      return Result.error(404, I18nUtil.getMessage("sysUser.entityNotFound"));
    } else {
      result.setResult(sysPosition);
      result.setSuccess(true);
    }
    return result;
  }

  /**
   * Export excel
   *
   * @param request
   * @param response
   */
  @PostMapping(value = "/exportXls")
  public ModelAndView exportXls(HttpServletRequest request, HttpServletResponse response) {
    // Step.1 Build query conditions
    QueryWrapper<SysPosition> queryWrapper = null;
    try {
      String paramsStr = request.getParameter("paramsStr");
      if (oConvertUtils.isNotEmpty(paramsStr)) {
        String deString = URLDecoder.decode(paramsStr, "UTF-8");
        SysPosition sysPosition = JSON.parseObject(deString, SysPosition.class);
        // ------------------------------------------------------------------------------------------------
        // Whether multi-tenant data isolation is enabled for system admin [SAAS mode]
        if (MybatisPlusSaasConfig.OPEN_SYSTEM_TENANT_CONTROL) {
          sysPosition.setTenantId(oConvertUtils.getInt(TenantContext.getTenant(), 0));
        }
        // ------------------------------------------------------------------------------------------------
        queryWrapper = QueryGenerator.initQueryWrapper(sysPosition, request.getParameterMap());
      }
    } catch (UnsupportedEncodingException e) {
      log.error("operation failed", e);
    }

    // Step.2 Export Excel via AutoPoi
    ModelAndView mv = new ModelAndView(new JeecgEntityExcelView());
    List<SysPosition> pageList = sysPositionService.list(queryWrapper);
    LoginUser user = (LoginUser) StpUtil.getSession().get("loginUser");
    // Export file name
    mv.addObject(NormalExcelConstants.FILE_NAME, "Job title table list");
    mv.addObject(NormalExcelConstants.CLASS, SysPosition.class);
    mv.addObject(
        NormalExcelConstants.PARAMS,
        new ExportParams("Job title table list data", "Exported by:" + user.getRealname(), "Export information"));
    mv.addObject(NormalExcelConstants.DATA_LIST, pageList);
    return mv;
  }

  /**
   * Import data through excel
   *
   * @param request
   * @param response
   * @return
   */
  @RequestMapping(value = "/importExcel", method = RequestMethod.POST)
  public Result<?> importExcel(HttpServletRequest request, HttpServletResponse response)
      throws IOException {
    MultipartHttpServletRequest multipartRequest = (MultipartHttpServletRequest) request;
    Map<String, MultipartFile> fileMap = multipartRequest.getFileMap();
    // Error messages
    List<String> errorMessage = new ArrayList<>();
    int successLines = 0, errorLines = 0;
    for (Map.Entry<String, MultipartFile> entity : fileMap.entrySet()) {
      // Get uploaded file object
      MultipartFile file = entity.getValue();
      ImportParams params = new ImportParams();
      params.setTitleRows(2);
      params.setHeadRows(1);
      params.setNeedSave(true);
      try {
        List<Object> listSysPositions =
            ExcelImportUtil.importExcel(file.getInputStream(), SysPosition.class, params);
        List<String> list =
            ImportExcelUtil.importDateSave(
                listSysPositions,
                ISysPositionService.class,
                errorMessage,
                CommonConstant.SQL_INDEX_UNIQ_CODE);
        errorLines += list.size();
        successLines += (listSysPositions.size() - errorLines);
      } catch (Exception e) {
        log.error("operation failed", e);
        return Result.error(I18nUtil.getMessage("sysRole.importFailed", new Object[]{e.getMessage()}));
      } finally {
        try {
          file.getInputStream().close();
        } catch (IOException e) {
          log.error("operation failed", e);
        }
      }
    }
    return ImportExcelUtil.imporReturnRes(errorLines, successLines, errorMessage);
  }

  /**
 * Query by code
 *
 * @param code
 * @return*/
  @AutoLog(value = "Job title table - Query by code")
  @ApiOperation(value = "Job title table - Query by code", notes = "Job title table - Query by code")
  @PostMapping(value = "/queryByCode")
  public Result<SysPosition> queryByCode(
      @RequestParam(name = "code", required = true) String code) {
    Result<SysPosition> result = new Result<SysPosition>();
    QueryWrapper<SysPosition> queryWrapper = new QueryWrapper<SysPosition>();
    queryWrapper.eq("code", code);
    SysPosition sysPosition = sysPositionService.getOne(queryWrapper);
    if (sysPosition == null) {
      return Result.error(404, I18nUtil.getMessage("sysUser.entityNotFound"));
    } else {
      result.setResult(sysPosition);
      result.setSuccess(true);
    }
    return result;
  }

  /**
   * Query by multiple IDs
   *
   * @param ids
   * @return
   */
  @AutoLog(value = "Job title table - Query by multiple")
  @ApiOperation(value = "Job title table - Query by multiple ids", notes = "Job title table - Query by multiple ids")
  @PostMapping(value = "/queryByIds")
  public Result<List<SysPosition>> queryByIds(@RequestParam(name = "ids") String ids) {
    Result<List<SysPosition>> result = new Result<>();
    QueryWrapper<SysPosition> queryWrapper = new QueryWrapper<>();
    queryWrapper.in(true, "id", ids.split(","));
    List<SysPosition> list = sysPositionService.list(queryWrapper);
    if (list == null) {
      return Result.error(404, I18nUtil.getMessage("sysUser.entityNotFound"));
    } else {
      result.setResult(list);
      result.setSuccess(true);
    }
    return result;
  }

  /**
   * Get a list of job users
   *
   * @param pageNo
   * @param pageSize
   * @param positionId
   * @return
   */
  @PostMapping("/getPositionUserList")
  public Result<IPage<SysUser>> getPositionUserList(
      @RequestParam(name = "pageNo", defaultValue = "1") Integer pageNo,
      @RequestParam(name = "pageSize", defaultValue = "10") Integer pageSize,
      @RequestParam(name = "positionId") String positionId) {

    Page<SysUser> page = new Page<>(pageNo, pageSize);
    IPage<SysUser> pageList = userPositionService.getPositionUserList(page, positionId);
    List<String> userIds =
        pageList.getRecords().stream().map(SysUser::getId).collect(Collectors.toList());
    if (null != userIds && userIds.size() > 0) {
      Map<String, String> useDepNames = userService.getDepNamesByUserIds(userIds);
      pageList
          .getRecords()
          .forEach(
              item -> {
                item.setOrgCodeTxt(useDepNames.get(item.getId()));
              });
    }
    return Result.ok(pageList);
  }

  /**
   * Add members to user-position relation table
   *
   * @param userIds
   * @param positionId
   * @return
   */
  @PostMapping("/savePositionUser")
  public Result<String> saveUserPosition(
      @RequestParam(name = "userIds") String userIds,
      @RequestParam(name = "positionId") String positionId) {
    userPositionService.saveUserPosition(userIds, positionId);
    return Result.ok(I18nUtil.getMessage("sysPosition.addSuccess"));
  }

  /**
   * Remove members from job list
   *
   * @param userIds
   * @param positionId
   * @return
   */
  @DeleteMapping("/removePositionUser")
  public Result<String> removeUserPosition(
      @RequestParam(name = "userIds") String userIds,
      @RequestParam(name = "positionId") String positionId) {
    userPositionService.removePositionUser(userIds, positionId);
    return Result.OK(I18nUtil.getMessage("sysPosition.removeMemberSuccess"));
  }
}
