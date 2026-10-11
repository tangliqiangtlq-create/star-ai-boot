package org.jeecg.modules.quartz.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.annotation.SaMode;
import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;

import org.jeecg.common.api.vo.Result;
import org.jeecg.common.constant.CommonConstant;
import org.jeecg.common.constant.SecurityRoleConstants;
import org.jeecg.common.constant.SymbolConstant;
import org.jeecg.common.system.query.QueryGenerator;
import org.jeecg.common.system.vo.LoginUser;
import org.jeecg.common.util.I18nUtil;
import org.jeecg.common.util.ImportExcelUtil;
import org.jeecg.modules.quartz.entity.QuartzJob;
import org.jeecg.modules.quartz.service.IQuartzJobService;
import org.jeecgframework.poi.excel.ExcelImportUtil;
import org.jeecgframework.poi.excel.def.NormalExcelConstants;
import org.jeecgframework.poi.excel.entity.ExportParams;
import org.jeecgframework.poi.excel.entity.ImportParams;
import org.jeecgframework.poi.excel.view.JeecgEntityExcelView;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;
import org.springframework.web.servlet.ModelAndView;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * @Description: online scheduled task management @Author: jeecg-boot @Date: 2019-01-02 @Version:V1.0
 */
@RestController
@RequestMapping("/sys/quartzJob")
@Slf4j
@Api(tags = "Scheduled task API")
public class QuartzJobController {
  @Autowired private IQuartzJobService quartzJobService;
  @Autowired private Scheduler scheduler;

  /**
   * Paginated list query
   *
   * @param quartzJob
   * @param pageNo
   * @param pageSize
   * @param req
   * @return
   */
  @RequestMapping(value = "/list", method = RequestMethod.POST)
  public Result<?> queryPageList(
      QuartzJob quartzJob,
      @RequestParam(name = "pageNo", defaultValue = "1") Integer pageNo,
      @RequestParam(name = "pageSize", defaultValue = "10") Integer pageSize,
      HttpServletRequest req) {
    QueryWrapper<QuartzJob> queryWrapper =
        QueryGenerator.initQueryWrapper(quartzJob, req.getParameterMap());
    Page<QuartzJob> page = new Page<QuartzJob>(pageNo, pageSize);
    IPage<QuartzJob> pageList = quartzJobService.page(page, queryWrapper);
    return Result.ok(pageList);
  }

  /**
   * Add scheduled job
   *
   * @param quartzJob
   * @return
   */
  @SaCheckRole(value = {"admin", SecurityRoleConstants.ROLE_OPS_ADMIN}, mode = SaMode.OR)
  @SaCheckPermission("system:quartzJob:add")
  @RequestMapping(value = "/add", method = RequestMethod.POST)
  public Result<?> add(@RequestBody QuartzJob quartzJob) {
    quartzJobService.saveAndScheduleJob(quartzJob);
    return Result.ok(I18nUtil.getMessage("quartzJob.createSuccess"));
  }

  /**
   * Update scheduled job
   *
   * @param quartzJob
   * @return
   */
  @SaCheckRole(value = {"admin", SecurityRoleConstants.ROLE_OPS_ADMIN}, mode = SaMode.OR)
  @SaCheckPermission("system:quartzJob:edit")
  @RequestMapping(
      value = "/edit",
      method = {RequestMethod.PUT, RequestMethod.POST})
  public Result<?> eidt(@RequestBody QuartzJob quartzJob) {
    try {
      quartzJobService.editAndScheduleJob(quartzJob);
    } catch (SchedulerException e) {
      log.error("operation failed", e);
      return Result.error(I18nUtil.getMessage("quartzJob.updateFailed"));
    }
    return Result.ok(I18nUtil.getMessage("quartzJob.updateSuccess"));
  }

  /**
   * Delete by id
   *
   * @param id
   * @return
   */
  @SaCheckRole(value = {"admin", SecurityRoleConstants.ROLE_OPS_ADMIN}, mode = SaMode.OR)
  @SaCheckPermission("system:quartzJob:delete")
  @RequestMapping(value = "/delete", method = RequestMethod.DELETE)
  public Result<?> delete(@RequestParam(name = "id", required = true) String id) {
    QuartzJob quartzJob = quartzJobService.getById(id);
    if (quartzJob == null) {
      return Result.error(I18nUtil.getMessage("sysUser.entityNotFound"));
    }
    quartzJobService.deleteAndStopJob(quartzJob);
    return Result.ok(I18nUtil.getMessage("common.success.delete"));
  }

  /**
   * Batch delete
   *
   * @param ids
   * @return
   */
  @SaCheckRole(value = {"admin", SecurityRoleConstants.ROLE_OPS_ADMIN}, mode = SaMode.OR)
  @SaCheckPermission("system:quartzJob:deleteBatch")
  @RequestMapping(value = "/deleteBatch", method = RequestMethod.DELETE)
  public Result<?> deleteBatch(@RequestParam(name = "ids", required = true) String ids) {
    if (ids == null || "".equals(ids.trim())) {
      return Result.error(I18nUtil.getMessage("common.invalid.param_unrecognized"));
    }
    for (String id : Arrays.asList(ids.split(SymbolConstant.COMMA))) {
      QuartzJob job = quartzJobService.getById(id);
      quartzJobService.deleteAndStopJob(job);
    }
    return Result.ok(I18nUtil.getMessage("quartzJob.deleteSuccess"));
  }

  /**
   * Pause scheduled job
   *
   * @param id
   * @return
   */
  @SaCheckRole(value = {"admin", SecurityRoleConstants.ROLE_OPS_ADMIN}, mode = SaMode.OR)
  @SaCheckPermission("system:quartzJob:pause")
  @PostMapping(value = "/pause")
  @ApiOperation(value = "Stop scheduled job")
  public Result<Object> pauseJob(@RequestParam(name = "id") String id) {
    QuartzJob job = quartzJobService.getById(id);
    if (job == null) {
      return Result.error(I18nUtil.getMessage("quartzJob.notFound"));
    }
    quartzJobService.pause(job);
    return Result.ok(I18nUtil.getMessage("quartzJob.stopSuccess"));
  }

  /**
   * Start scheduled job
   *
   * @param id
   * @return
   */
  @SaCheckRole(value = {"admin", SecurityRoleConstants.ROLE_OPS_ADMIN}, mode = SaMode.OR)
  @SaCheckPermission("system:quartzJob:resume")
  @PostMapping(value = "/resume")
  @ApiOperation(value = "Start scheduled task")
  public Result<Object> resumeJob(@RequestParam(name = "id") String id) {
    QuartzJob job = quartzJobService.getById(id);
    if (job == null) {
      return Result.error(I18nUtil.getMessage("quartzJob.notFound"));
    }
    quartzJobService.resumeJob(job);
    // scheduler.resumeJob(JobKey.jobKey(job.getJobClassName().trim()));
    return Result.ok(I18nUtil.getMessage("quartzJob.startSuccess"));
  }

  /**
   * Query by id
   *
   * @param id
   * @return
   */
  @PostMapping(value = "/queryById")
  public Result<?> queryById(@RequestParam(name = "id", required = true) String id) {
    QuartzJob quartzJob = quartzJobService.getById(id);
    return Result.ok(quartzJob);
  }

  /**
   * Export excel
   *
   * @param request
   * @param quartzJob
   */
  @PostMapping(value = "/exportXls")
  public ModelAndView exportXls(HttpServletRequest request, QuartzJob quartzJob) {
    // Step.1 Build query conditions
    QueryWrapper<QuartzJob> queryWrapper =
        QueryGenerator.initQueryWrapper(quartzJob, request.getParameterMap());
    // Step.2 AutoPoi Export Excel
    ModelAndView mv = new ModelAndView(new JeecgEntityExcelView());
    List<QuartzJob> pageList = quartzJobService.list(queryWrapper);
    // Export file name
    mv.addObject(NormalExcelConstants.FILE_NAME, "Scheduled task list");
    mv.addObject(NormalExcelConstants.CLASS, QuartzJob.class);
    // Get current logged-in user
    // update-begin---author:wangshuai ---date:20211227  for: [JTC-116] Exporter name was hardcoded ------------
    LoginUser user = (LoginUser) StpUtil.getSession().get("loginUser");
    mv.addObject(
        NormalExcelConstants.PARAMS,
        new ExportParams("Scheduled task list data", "Exported by:" + user.getRealname(), "Export information"));
    // update-end---author:wangshuai ---date:20211227  for: [JTC-116] Exporter name was hardcoded ------------
    mv.addObject(NormalExcelConstants.DATA_LIST, pageList);
    return mv;
  }

  /**
   * Import data via Excel
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
    // ErrorInfo
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
        List<QuartzJob> listQuartzJobs =
            ExcelImportUtil.importExcel(file.getInputStream(), QuartzJob.class, params);
        // add-begin-author:taoyan date:20210909 for: Imported scheduled jobs are not started or scheduled automatically; must manually click start to add them to the scheduler #2986
        for (QuartzJob job : listQuartzJobs) {
          job.setStatus(CommonConstant.STATUS_DISABLE);
        }
        List<String> list =
            ImportExcelUtil.importDateSave(
                listQuartzJobs,
                IQuartzJobService.class,
                errorMessage,
                CommonConstant.SQL_INDEX_UNIQ_JOB_CLASS_NAME);
        // add-end-author:taoyan date:20210909 for: Imported scheduled jobs are not started or scheduled automatically; must manually click start to add them to the scheduler #2986
        errorLines += list.size();
        successLines += (listQuartzJobs.size() - errorLines);
      } catch (Exception e) {
        log.error("operation failed", e);
        return Result.error(I18nUtil.getMessage("common.error.import_failed"));
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
   * Execute immediately
   *
   * @param id
   * @return
   */
  @SaCheckRole(value = {"admin", SecurityRoleConstants.ROLE_OPS_ADMIN}, mode = SaMode.OR)
  @SaCheckPermission("system:quartzJob:execute")
  @PostMapping("/execute")
  public Result<?> execute(@RequestParam(name = "id", required = true) String id) {
    QuartzJob quartzJob = quartzJobService.getById(id);
    if (quartzJob == null) {
      return Result.error(I18nUtil.getMessage("sysUser.entityNotFound"));
    }
    try {
      quartzJobService.execute(quartzJob);
    } catch (Exception e) {
      // log.error("operation failed", e);
      log.info("Scheduled task immediate execution failed>>" + "[redacted]");
      return Result.error(I18nUtil.getMessage("quartzJob.executeFailed"));
    }
    return Result.ok(I18nUtil.getMessage("quartzJob.executeSuccess"));
  }
}
