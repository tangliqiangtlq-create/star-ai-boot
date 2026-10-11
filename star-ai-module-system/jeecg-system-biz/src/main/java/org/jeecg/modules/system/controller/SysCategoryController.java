package org.jeecg.modules.system.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.annotation.SaMode;
import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jeecg.common.api.vo.Result;
import org.jeecg.common.config.TenantContext;
import org.jeecg.common.constant.CommonConstant;
import org.jeecg.common.constant.SecurityRoleConstants;
import org.jeecg.common.system.query.QueryGenerator;
import org.jeecg.common.system.vo.DictModel;
import org.jeecg.common.system.vo.LoginUser;
import org.jeecg.common.util.I18nUtil;
import org.jeecg.common.util.ImportExcelUtil;
import org.jeecg.common.util.JeecgJsonUtils;
import org.jeecg.common.util.ReflectHelper;
import org.jeecg.common.util.oConvertUtils;
import org.jeecg.config.mybatis.MybatisPlusSaasConfig;
import org.jeecg.modules.system.entity.SysCategory;
import org.jeecg.modules.system.model.TreeSelectModel;
import org.jeecg.modules.system.service.ISysCategoryService;
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

/**
 * @Description: category dictionary @Author: jeecg-boot @Date: 2019-05-29 @Version: V1.0
 */
@RestController
@RequestMapping("/sys/category")
@Slf4j
@SaCheckRole(value = {SecurityRoleConstants.ROLE_OPS_ADMIN, "admin"}, mode = SaMode.OR)
public class SysCategoryController {
  @Autowired private ISysCategoryService sysCategoryService;

  /** Category code 0 */
  private static final String CATEGORY_ROOT_CODE = "0";

  /**
 * Paginated list query
 *
 * @param sysCategory
 * @param pageNo
 * @param pageSize
 * @param req
 * @return*/
  @PostMapping(value = "/rootList")
  public Result<IPage<SysCategory>> queryPageList(
      SysCategory sysCategory,
      @RequestParam(name = "pageNo", defaultValue = "1") Integer pageNo,
      @RequestParam(name = "pageSize", defaultValue = "10") Integer pageSize,
      HttpServletRequest req) {
    if (oConvertUtils.isEmpty(sysCategory.getPid())) {
      sysCategory.setPid("0");
    }
    Result<IPage<SysCategory>> result = new Result<IPage<SysCategory>>();
    // ------------------------------------------------------------------------------------------------
    // Whether multi-tenant data isolation is enabled for system admin [SAAS mode]
    if (MybatisPlusSaasConfig.OPEN_SYSTEM_TENANT_CONTROL) {
      sysCategory.setTenantId(oConvertUtils.getInt(TenantContext.getTenant(), 0));
    }
    // ------------------------------------------------------------------------------------------------

    // --author:os_chengtgen---date:20190804 -----for: category dictionary page display error, issues:377--------start
    // --author:liusq---date:20211119 -----for: [vue3] category dictionary page query condition config--------start
    QueryWrapper<SysCategory> queryWrapper =
        QueryGenerator.initQueryWrapper(sysCategory, req.getParameterMap());
    String name = sysCategory.getName();
    String code = sysCategory.getCode();
    // QueryWrapper<SysCategory> queryWrapper = new QueryWrapper<SysCategory>();
    if (StringUtils.isBlank(name) && StringUtils.isBlank(code)) {
      queryWrapper.eq("pid", sysCategory.getPid());
    }
    // --author:liusq---date:20211119 -----for: category dictionary page query condition config--------end
    // --author:os_chengtgen---date:20190804 -----for: [vue3] category dictionary page display error, issues:377--------end

    Page<SysCategory> page = new Page<SysCategory>(pageNo, pageSize);
    IPage<SysCategory> pageList = sysCategoryService.page(page, queryWrapper);
    result.setSuccess(true);
    result.setResult(pageList);
    return result;
  }

  @PostMapping(value = "/childList")
  public Result<List<SysCategory>> queryPageList(SysCategory sysCategory, HttpServletRequest req) {
    // ------------------------------------------------------------------------------------------------
    // Whether multi-tenant data isolation is enabled for system admin [SAAS mode]
    if (MybatisPlusSaasConfig.OPEN_SYSTEM_TENANT_CONTROL) {
      sysCategory.setTenantId(oConvertUtils.getInt(TenantContext.getTenant(), 0));
    }
    // ------------------------------------------------------------------------------------------------
    Result<List<SysCategory>> result = new Result<List<SysCategory>>();
    QueryWrapper<SysCategory> queryWrapper =
        QueryGenerator.initQueryWrapper(sysCategory, req.getParameterMap());
    List<SysCategory> list = sysCategoryService.list(queryWrapper);
    result.setSuccess(true);
    result.setResult(list);
    return result;
  }

  /**
   * Add
   *
   * @param sysCategory
   * @return
   */
  @PostMapping(value = "/add")
  public Result<SysCategory> add(@RequestBody SysCategory sysCategory) {
    Result<SysCategory> result = new Result<SysCategory>();
    try {
      sysCategoryService.addSysCategory(sysCategory);
      result.success(I18nUtil.getMessage("sysCategory.addSuccess"));
    } catch (Exception e) {
      log.error("operation failed", e);
      result.error500(I18nUtil.getMessage("sysUser.operationFailed"));
    }
    return result;
  }

  /**
   * Edit
   *
   * @param sysCategory
   * @return
   */
  @PostMapping(
      value = "/edit",
      consumes = "application/json")
  public Result<SysCategory> edit(@RequestBody SysCategory sysCategory) {
    Result<SysCategory> result = new Result<SysCategory>();
    SysCategory sysCategoryEntity = sysCategoryService.getById(sysCategory.getId());
    if (sysCategoryEntity == null) {
      return Result.error(404, I18nUtil.getMessage("sysUser.entityNotFound"));
    } else {
      sysCategoryService.updateSysCategory(sysCategory);
      result.success(I18nUtil.getMessage("sysCategory.modifySuccess"));
    }
    return result;
  }

  /**
   * Delete by id
   *
   * @param id
   * @return
   */
  @DeleteMapping(value = "/delete")
  public Result<SysCategory> delete(@RequestParam(name = "id", required = true) String id) {
    Result<SysCategory> result = new Result<SysCategory>();
    SysCategory sysCategory = sysCategoryService.getById(id);
    if (sysCategory == null) {
      return Result.error(404, I18nUtil.getMessage("sysUser.entityNotFound"));
    } else {
      this.sysCategoryService.deleteSysCategory(id);
      result.success(I18nUtil.getMessage("sysCategory.deleteSuccess"));
    }

    return result;
  }

  /**
   * Batch delete
   *
   * @param ids
   * @return
   */
  @DeleteMapping(value = "/deleteBatch")
  public Result<SysCategory> deleteBatch(@RequestParam(name = "ids", required = true) String ids) {
    Result<SysCategory> result = new Result<SysCategory>();
    if (ids == null || "".equals(ids.trim())) {
      return Result.error(400, I18nUtil.getMessage("sysCategory.paramNotRecognized"));
    } else {
      this.sysCategoryService.deleteSysCategory(ids);
      result.success(I18nUtil.getMessage("sysCategory.deleteSuccess"));
    }
    return result;
  }

  /**
   * Query by id
   *
   * @param id
   * @return
   */
  @PostMapping(value = "/queryById")
  public Result<SysCategory> queryById(@RequestParam(name = "id", required = true) String id) {
    Result<SysCategory> result = new Result<SysCategory>();
    SysCategory sysCategory = sysCategoryService.getById(id);
    if (sysCategory == null) {
      return Result.error(404, I18nUtil.getMessage("sysUser.entityNotFound"));
    } else {
      result.setResult(sysCategory);
      result.setSuccess(true);
    }
    return result;
  }

  /**
   * Export excel
   *
   * @param request
   */
  @PostMapping(value = "/exportXls")
  public ModelAndView exportXls(HttpServletRequest request, SysCategory sysCategory) {
    // ------------------------------------------------------------------------------------------------
    // Whether multi-tenant data isolation is enabled for system admin [SAAS mode]
    if (MybatisPlusSaasConfig.OPEN_SYSTEM_TENANT_CONTROL) {
      sysCategory.setTenantId(oConvertUtils.getInt(TenantContext.getTenant(), 0));
    }
    // ------------------------------------------------------------------------------------------------

    // Step.1 Build query conditions and query data
    QueryWrapper<SysCategory> queryWrapper =
        QueryGenerator.initQueryWrapper(sysCategory, request.getParameterMap());
    List<SysCategory> pageList = sysCategoryService.list(queryWrapper);
    // Step.2 Export Excel via AutoPoi
    ModelAndView mv = new ModelAndView(new JeecgEntityExcelView());
    // Filter selected data
    String selections = request.getParameter("selections");
    if (oConvertUtils.isEmpty(selections)) {
      mv.addObject(NormalExcelConstants.DATA_LIST, pageList);
    } else {
      List<String> selectionList = Arrays.asList(selections.split(","));
      List<SysCategory> exportList =
          pageList.stream()
              .filter(item -> selectionList.contains(item.getId()))
              .collect(Collectors.toList());
      mv.addObject(NormalExcelConstants.DATA_LIST, exportList);
    }
    // Export file name
    mv.addObject(NormalExcelConstants.FILE_NAME, "Category dictionary list");
    mv.addObject(NormalExcelConstants.CLASS, SysCategory.class);
    LoginUser user = (LoginUser) StpUtil.getSession().get("loginUser");
    mv.addObject(
        NormalExcelConstants.PARAMS,
        new ExportParams("Category dictionary list data", "Exported by:" + user.getRealname(), "Export information"));
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
        List<SysCategory> listSysCategorys =
            ExcelImportUtil.importExcel(file.getInputStream(), SysCategory.class, params);
        // Sort by code length
        Collections.sort(listSysCategorys);
        log.info("Sorted list====>", listSysCategorys);
        for (int i = 0; i < listSysCategorys.size(); i++) {
          SysCategory sysCategoryExcel = listSysCategorys.get(i);
          String code = sysCategoryExcel.getCode();
          if (code.length() > 3) {
            String pCode = sysCategoryExcel.getCode().substring(0, code.length() - 3);
            log.info("pCode====>", pCode);
            String pId = sysCategoryService.queryIdByCode(pCode);
            log.info("pId====>", pId);
            if (StringUtils.isNotBlank(pId)) {
              sysCategoryExcel.setPid(pId);
            }
          } else {
            sysCategoryExcel.setPid("0");
          }
          try {
            sysCategoryService.save(sysCategoryExcel);
            successLines++;
          } catch (Exception e) {
            errorLines++;
            String message = e.getMessage().toLowerCase();
            int lineNumber = i + 1;
            // Determine error message by index name
            if (message.contains(CommonConstant.SQL_INDEX_UNIQ_CATEGORY_CODE)) {
              errorMessage.add("Row " + lineNumber + " row: category code already exists, import ignored.");
            } else {
              errorMessage.add("Row " + lineNumber + " row: unknown error, import ignored");
              log.error("operation failed", e);
            }
          }
        }
      } catch (Exception e) {
        errorMessage.add("Exception occurred: import failed");
        log.error("import excel failed", e);
        log.error("operation failed", e);
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

  /** Load a single record for display/edit backfill */
  @RequestMapping(value = "/loadOne", method = RequestMethod.POST)
  public Result<SysCategory> loadOne(
      @RequestParam(name = "field") String field, @RequestParam(name = "val") String val) {
    Result<SysCategory> result = new Result<SysCategory>();
    try {
      // update-begin-author:taoyan date:2022-5-6 for: issues/3663 SQL injection issue
      boolean isClassField = ReflectHelper.isClassField(field, SysCategory.class);
      if (!isClassField) {
        return Result.error(I18nUtil.getMessage("sysCategory.fieldInvalid"));
      }
      // update-end-author:taoyan date:2022-5-6 for: issues/3663 SQL injection issue
      QueryWrapper<SysCategory> query = new QueryWrapper<SysCategory>();
      query.eq(field, val);
      List<SysCategory> ls = this.sysCategoryService.list(query);
      if (ls == null || ls.size() == 0) {
        result.setMessage(I18nUtil.getMessage("sysCategory.queryNoResult"));
        result.setSuccess(false);
      } else if (ls.size() > 1) {
        result.setMessage(I18nUtil.getMessage("sysCategory.queryDataException", new Object[]{field, val}));
        result.setSuccess(false);
      } else {
        result.setSuccess(true);
        result.setResult(ls.get(0));
      }
    } catch (Exception e) {
      log.error("operation failed", e);
      result.setMessage(e.getMessage());
      result.setSuccess(false);
    }
    return result;
  }

  /** Load child data of a node */
  @RequestMapping(value = "/loadTreeChildren", method = RequestMethod.POST)
  public Result<List<TreeSelectModel>> loadTreeChildren(@RequestParam(name = "pid") String pid) {
    Result<List<TreeSelectModel>> result = new Result<List<TreeSelectModel>>();
    try {
      List<TreeSelectModel> ls = this.sysCategoryService.queryListByPid(pid);
      result.setResult(ls);
      result.setSuccess(true);
    } catch (Exception e) {
      log.error("operation failed", e);
      result.setMessage(e.getMessage());
      result.setSuccess(false);
    }
    return result;
  }

  /** Load level-1 nodes; if synchronous, load all data */
  @RequestMapping(value = "/loadTreeRoot", method = RequestMethod.POST)
  public Result<List<TreeSelectModel>> loadTreeRoot(
      @RequestParam(name = "async") Boolean async, @RequestParam(name = "pcode") String pcode) {
    Result<List<TreeSelectModel>> result = new Result<List<TreeSelectModel>>();
    try {
      List<TreeSelectModel> ls = this.sysCategoryService.queryListByCode(pcode);
      if (!async) {
        loadAllCategoryChildren(ls);
      }
      result.setResult(ls);
      result.setSuccess(true);
    } catch (Exception e) {
      log.error("operation failed", e);
      result.setMessage(e.getMessage());
      result.setSuccess(false);
    }
    return result;
  }

  /** Recursively fetch child nodes; used for synchronous loading */
  private void loadAllCategoryChildren(List<TreeSelectModel> ls) {
    for (TreeSelectModel tsm : ls) {
      List<TreeSelectModel> temp = this.sysCategoryService.queryListByPid(tsm.getKey());
      if (temp != null && temp.size() > 0) {
        tsm.setChildren(temp);
        loadAllCategoryChildren(temp);
      }
    }
  }

  /**
   * checkCode
   *
   * @param pid
   * @param code
   * @return
   */
  @PostMapping(value = "/checkCode")
  public Result<?> checkCode(
      @RequestParam(name = "pid", required = false) String pid,
      @RequestParam(name = "code", required = false) String code) {
    if (oConvertUtils.isEmpty(code)) {
      return Result.error(I18nUtil.getMessage("sysCategory.typeCodeEmpty"));
    }
    if (oConvertUtils.isEmpty(pid)) {
      return Result.ok();
    }
    SysCategory parent = this.sysCategoryService.getById(pid);
    if (code.startsWith(parent.getCode())) {
      return Result.ok();
    } else {
      return Result.error(I18nUtil.getMessage("sysCategory.codeNotMatchFormat", new Object[]{parent.getCode()}));
    }
  }

  /**
   * Category dict tree control load node
   *
   * @param pid
   * @param pcode
   * @param condition
   * @return
   */
  @RequestMapping(value = "/loadTreeData", method = RequestMethod.POST)
  public Result<List<TreeSelectModel>> loadDict(
      @RequestParam(name = "pid", required = false) String pid,
      @RequestParam(name = "pcode", required = false) String pcode,
      @RequestParam(name = "condition", required = false) String condition) {
    Result<List<TreeSelectModel>> result = new Result<List<TreeSelectModel>>();
    // if pid is provided, ignore pcode
    if (oConvertUtils.isEmpty(pid)) {
      if (oConvertUtils.isEmpty(pcode)) {
        result.setSuccess(false);
        result.setMessage(I18nUtil.getMessage("sysCategory.loadTreeParamErrorNull"));
        return result;
      } else {
        if (ISysCategoryService.ROOT_PID_VALUE.equals(pcode)) {
          pid = ISysCategoryService.ROOT_PID_VALUE;
        } else {
          pid = this.sysCategoryService.queryIdByCode(pcode);
        }
        if (oConvertUtils.isEmpty(pid)) {
          result.setSuccess(false);
          result.setMessage(I18nUtil.getMessage("sysCategory.loadTreeParamErrorCode"));
          return result;
        }
      }
    }
    Map<String, String> query = null;
    if (oConvertUtils.isNotEmpty(condition)) {
      try {
        query = JeecgJsonUtils.parseToStringMap(condition);
      } catch (IOException e) {
        log.warn("Rejected invalid category tree condition JSON", e);
        return result.error500("Invalid condition JSON");
      }
    }
    List<TreeSelectModel> ls = sysCategoryService.queryListByPid(pid, query);
    result.setSuccess(true);
    result.setResult(ls);
    return result;
  }

  /**
   * Category dictionary control data echo [form page]
   *
   * @param ids
   * @param delNotExist whether to remove missing keys (default true); if false, return the key itself when absent in DB
   * @return
   */
  @RequestMapping(value = "/loadDictItem", method = RequestMethod.POST)
  public Result<List<String>> loadDictItem(
      @RequestParam(name = "ids") String ids,
      @RequestParam(name = "delNotExist", required = false, defaultValue = "true")
          boolean delNotExist) {
    Result<List<String>> result = new Result<>();
    // Non-null check
    if (StringUtils.isBlank(ids)) {
      result.setSuccess(false);
      result.setMessage(I18nUtil.getMessage("sysCategory.idsEmpty"));
      return result;
    }
    // QueryData
    List<String> textList = sysCategoryService.loadDictItem(ids, delNotExist);
    result.setSuccess(true);
    result.setResult(textList);
    return result;
  }

  /**
   * [List page] Load category dict data for value replacement
   *
   * @param code
   * @return
   */
  @RequestMapping(value = "/loadAllData", method = RequestMethod.POST)
  public Result<List<DictModel>> loadAllData(
      @RequestParam(name = "code", required = true) String code) {
    Result<List<DictModel>> result = new Result<List<DictModel>>();
    LambdaQueryWrapper<SysCategory> query = new LambdaQueryWrapper<SysCategory>();
    if (oConvertUtils.isNotEmpty(code) && !CATEGORY_ROOT_CODE.equals(code)) {
      query.likeRight(SysCategory::getCode, code);
    }
    List<SysCategory> list = this.sysCategoryService.list(query);
    if (list == null || list.size() == 0) {
      result.setMessage(I18nUtil.getMessage("sysCategory.noDataParamError"));
      result.setSuccess(false);
      return result;
    }
    List<DictModel> rdList = new ArrayList<DictModel>();
    for (SysCategory c : list) {
      rdList.add(new DictModel(c.getId(), c.getName()));
    }
    result.setSuccess(true);
    result.setResult(rdList);
    return result;
  }

  /**
   * Batch query child nodes by parent ids
   *
   * @param parentIds
   * @return
   */
  @PostMapping("/getChildListBatch")
  public Result getChildListBatch(@RequestParam("parentIds") String parentIds) {
    try {
      QueryWrapper<SysCategory> queryWrapper = new QueryWrapper<>();
      List<String> parentIdList = Arrays.asList(parentIds.split(","));
      queryWrapper.in("pid", parentIdList);
      List<SysCategory> list = sysCategoryService.list(queryWrapper);
      IPage<SysCategory> pageList = new Page<>(1, 10, list.size());
      pageList.setRecords(list);
      return Result.OK(pageList);
    } catch (Exception e) {
      log.error("operation failed", e);
      return Result.error(I18nUtil.getMessage("sysCategory.batchQuerySubNodeFailed", new Object[]{e.getMessage()}));
    }
  }
}
