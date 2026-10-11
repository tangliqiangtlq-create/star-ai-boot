package org.jeecg.modules.system.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.util.RandomUtil;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.plugins.pagination.PageDTO;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.jeecg.common.api.vo.Result;
import org.jeecg.common.aspect.annotation.AutoLog;
import org.jeecg.common.config.TenantContext;
import org.jeecg.common.constant.CommonConstant;
import org.jeecg.common.constant.SymbolConstant;
import org.jeecg.common.modules.redis.client.JeecgRedisClient;
import org.jeecg.common.system.query.QueryGenerator;
import org.jeecg.common.system.vo.LoginUser;
import org.jeecg.common.util.I18nUtil;
import org.jeecg.common.util.LocaleResolver;
import org.jeecg.common.util.oConvertUtils;
import org.jeecg.config.mybatis.MybatisPlusSaasConfig;
import org.jeecg.modules.base.service.BaseCommonService;
import org.jeecg.modules.system.entity.*;
import org.jeecg.modules.system.model.TreeModel;
import org.jeecg.modules.system.service.*;
import org.jeecg.modules.system.vo.SysUserRoleCountVo;
import org.jeecgframework.poi.excel.def.NormalExcelConstants;
import org.jeecgframework.poi.excel.entity.ExportParams;
import org.jeecgframework.poi.excel.entity.ImportParams;
import org.jeecgframework.poi.excel.view.JeecgEntityExcelView;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;
import org.springframework.web.servlet.ModelAndView;

/**
 * Role table front-end controller @Author scott
 *
 * @since 2018-12-19*/
@RestController
@RequestMapping("/sys/role")
@Slf4j
public class SysRoleController {
  @Autowired private ISysRoleService sysRoleService;

  @Autowired private ISysPermissionDataRuleService sysPermissionDataRuleService;

  @Autowired private ISysRolePermissionService sysRolePermissionService;

  @Autowired private ISysPermissionService sysPermissionService;

  @Autowired private ISysUserRoleService sysUserRoleService;
  @Autowired private BaseCommonService baseCommonService;
  @Autowired private JeecgRedisClient jeecgRedisClient;

  /**
   * Paginated list query [system roles, no tenant isolation]
   *
   * @param role
   * @param pageNo
   * @param pageSize
   * @param req
   * @return
   */
  @SaCheckPermission("system:role:list")
  @RequestMapping(value = "/list", method = RequestMethod.POST)
  @AutoLog("RoleList-Paginated list query")
  public Result<IPage<SysRole>> queryPageList(
      SysRole role,
      @RequestParam(name = "pageNo", defaultValue = "1") Integer pageNo,
      @RequestParam(name = "pageSize", defaultValue = "10") Integer pageSize,
      HttpServletRequest req) {
    Result<IPage<SysRole>> result = new Result<IPage<SysRole>>();
    // QueryWrapper<SysRole> queryWrapper = QueryGenerator.initQueryWrapper(role,
    // req.getParameterMap());
    // IPage<SysRole> pageList = sysRoleService.page(page, queryWrapper);
    Page<SysRole> page = new Page<SysRole>(pageNo, pageSize);
    // Switch to non-tenant-isolated method; still flawed (if tenant isolation on, other tenants' roles visible but edit errors)
    IPage<SysRole> pageList = sysRoleService.listAllSysRole(page, role);
    result.setSuccess(true);
    result.setResult(pageList);
    return result;
  }

  /**
   * Paginated list query [tenant roles, tenant isolated]
   *
   * @param role
   * @param pageNo
   * @param pageSize
   * @param req
   * @return
   */
  @RequestMapping(value = "/listByTenant", method = RequestMethod.POST)
  @AutoLog("RoleList-Paginated list query")
  public Result<IPage<SysRole>> listByTenant(
      SysRole role,
      @RequestParam(name = "pageNo", defaultValue = "1") Integer pageNo,
      @RequestParam(name = "pageSize", defaultValue = "10") Integer pageSize,
      HttpServletRequest req) {
    Result<IPage<SysRole>> result = new Result<IPage<SysRole>>();
    // This API must scope queries by tenant
    if (MybatisPlusSaasConfig.OPEN_SYSTEM_TENANT_CONTROL) {
      role.setTenantId(
          oConvertUtils.getInt(
              !"0".equals(TenantContext.getTenant()) ? TenantContext.getTenant() : "", -1));
    }

    QueryWrapper<SysRole> queryWrapper =
        QueryGenerator.initQueryWrapper(role, req.getParameterMap());
    Page<SysRole> page = new Page<SysRole>(pageNo, pageSize);
    IPage<SysRole> pageList = sysRoleService.page(page, queryWrapper);
    result.setSuccess(true);
    result.setResult(pageList);
    return result;
  }

  /**
   * Add
   *
   * @param role
   * @return
   */
  @RequestMapping(value = "/add", method = RequestMethod.POST)
  @SaCheckPermission("system:role:add")
  @AutoLog("RoleList-Add")
  public Result<SysRole> add(@RequestBody SysRole role) {
    Result<SysRole> result = new Result<SysRole>();
    try {
      // With multi-tenant isolation, role id auto-generated as 10 digits
      // update-begin---author:wangshuai---date:2024-05-23---for: [TV360X-42] Role code set on create does not match after save ---
      if (MybatisPlusSaasConfig.OPEN_SYSTEM_TENANT_CONTROL
          && oConvertUtils.isEmpty(role.getRoleCode())) {
        // update-end---author:wangshuai---date:2024-05-23---for: [TV360X-42] Role code set on create does not match after save ---
        role.setRoleCode(RandomUtil.randomString(10));
      }
      role.setCreateTime(new Date());
      sysRoleService.save(role);
      result.success(I18nUtil.getMessage("sysRole.addSuccess"));
    } catch (Exception e) {
      log.error("operation failed", e);
      result.error500(I18nUtil.getMessage("sysUser.operationFailed"));
    }
    return result;
  }

  /**
   * Edit
   *
   * @param role
   * @return
   */
  @SaCheckPermission("system:role:edit")
  @RequestMapping(
      value = "/edit",
      method = {RequestMethod.PUT, RequestMethod.POST})
  @AutoLog("RoleList-Edit")
  public Result<SysRole> edit(@RequestBody SysRole role) {
    Result<SysRole> result = new Result<SysRole>();
    SysRole sysrole = sysRoleService.getById(role.getId());
    if (sysrole == null) {
      result.error500(I18nUtil.getMessage("sysRole.roleNotFound"));
    } else {
      role.setUpdateTime(new Date());

      // ------------------------------------------------------------------
      // Under SaaS isolation, verify current tenant id belongs to current tenant
      if (MybatisPlusSaasConfig.OPEN_SYSTEM_TENANT_CONTROL) {
        // Get current user
        LoginUser sysUser = (LoginUser) StpUtil.getSession().get("loginUser");
        Integer tenantId = oConvertUtils.getInt(TenantContext.getTenant(), 0);
        String username = "admin";
        if (!tenantId.equals(role.getTenantId()) && !username.equals(sysUser.getUsername())) {
          baseCommonService.addLog(
              "Unauthorized, modifying role ID under another tenant: " + role.getId() + ", operator: " + sysUser.getUsername(),
              CommonConstant.LOG_TYPE_2,
              CommonConstant.OPERATE_TYPE_3);
          return Result.error(I18nUtil.getMessage("sysRole.modifyFailedNotInTenant"));
        }
      }
      // ------------------------------------------------------------------

      boolean ok = sysRoleService.updateById(role);
      if (ok) {
        result.success(I18nUtil.getMessage("sysRole.modifySuccess"));
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
  @SaCheckPermission("system:role:delete")
  @RequestMapping(value = "/delete", method = RequestMethod.DELETE)
  @AutoLog("RoleList-Delete by id")
  public Result<?> delete(@RequestParam(name = "id", required = true) String id) {
    // Under SaaS isolation, verify current tenant id belongs to current tenant
    if (MybatisPlusSaasConfig.OPEN_SYSTEM_TENANT_CONTROL) {
      // Get current user
      LoginUser sysUser = (LoginUser) StpUtil.getSession().get("loginUser");
      int tenantId = oConvertUtils.getInt(TenantContext.getTenant(), 0);
      Long getRoleCount = sysRoleService.getRoleCountByTenantId(id, tenantId);
      String username = "admin";
      if (getRoleCount == 0 && !username.equals(sysUser.getUsername())) {
        baseCommonService.addLog(
            "Unauthorized, deleting role ID under another tenant: " + id + ", operator: " + sysUser.getUsername(),
            CommonConstant.LOG_TYPE_2,
            CommonConstant.OPERATE_TYPE_4);
        return Result.error(I18nUtil.getMessage("sysRole.deleteFailedNotInTenant"));
      }
    }

    // update-begin---author:wangshuai---date:2024-01-16---for: [QQYUN-7974] Prevent deletion of admin role ---
    // Whether admin role exists
    sysRoleService.checkAdminRoleRejectDel(id);
    // update-end---author:wangshuai---date:2024-01-16---for: [QQYUN-7974] Prevent deletion of admin role ---

    sysRoleService.deleteRole(id);

    return Result.ok(I18nUtil.getMessage("sysRole.deleteSuccess"));
  }

  /**
   * Batch delete
   *
   * @param ids
   * @return
   */
  @SaCheckPermission("system:role:deleteBatch")
  @RequestMapping(value = "/deleteBatch", method = RequestMethod.DELETE)
  @AutoLog("RoleList-Batch delete")
  public Result<SysRole> deleteBatch(@RequestParam(name = "ids", required = true) String ids) {
    baseCommonService.addLog(
        "Delete role operation, role ids: " + ids, CommonConstant.LOG_TYPE_2, CommonConstant.OPERATE_TYPE_4);
    Result<SysRole> result = new Result<SysRole>();
    if (oConvertUtils.isEmpty(ids)) {
      result.error500(I18nUtil.getMessage("sysRole.roleNotSelected"));
    } else {
      // Under SaaS isolation, verify current tenant id belongs to current tenant
      if (MybatisPlusSaasConfig.OPEN_SYSTEM_TENANT_CONTROL) {
        int tenantId = oConvertUtils.getInt(TenantContext.getTenant(), 0);
        String[] roleIds = ids.split(SymbolConstant.COMMA);
        LoginUser sysUser = (LoginUser) StpUtil.getSession().get("loginUser");
        String username = "admin";
        for (String id : roleIds) {
          Long getRoleCount = sysRoleService.getRoleCountByTenantId(id, tenantId);
          // If role id is 0 (non-existent), delete the role
          if (getRoleCount == 0 && !username.equals(sysUser.getUsername())) {
            baseCommonService.addLog(
                "Unauthorized, deleting role ID under another tenant: " + id + ", operator: " + sysUser.getUsername(),
                CommonConstant.LOG_TYPE_2,
                CommonConstant.OPERATE_TYPE_4);
            return Result.error(I18nUtil.getMessage("sysRole.batchDeleteFailed"));
          }
        }
      }
      // Verify whether role is admin
      sysRoleService.checkAdminRoleRejectDel(ids);
      sysRoleService.deleteBatchRole(ids.split(","));
      result.success(I18nUtil.getMessage("sysRole.deleteSuccess"));
    }
    return result;
  }

  /**
   * Query by id
   *
   * @param id
   * @return
   */
  @RequestMapping(value = "/queryById", method = RequestMethod.POST)
  @AutoLog("RoleList-Query by id")
  public Result<SysRole> queryById(@RequestParam(name = "id", required = true) String id) {
    Result<SysRole> result = new Result<SysRole>();
    SysRole sysrole = sysRoleService.getById(id);
    if (sysrole == null) {
      return Result.error(404, I18nUtil.getMessage("sysUser.entityNotFound"));
    } else {
      result.setResult(sysrole);
      result.setSuccess(true);
    }
    return result;
  }

  /**
   * Query all roles (with tenant isolation)
   *
   * @return
   */
  @RequestMapping(value = "/queryall", method = RequestMethod.POST)
  @AutoLog("RoleList-QueryAllRole")
  public Result<List<SysRole>> queryall() {
    Result<List<SysRole>> result = new Result<>();
    LambdaQueryWrapper<SysRole> query = new LambdaQueryWrapper<SysRole>();
    // ------------------------------------------------------------------------------------------------
    // Whether multi-tenant data isolation is enabled for system admin [SAAS mode]
    if (MybatisPlusSaasConfig.OPEN_SYSTEM_TENANT_CONTROL) {
      query.eq(SysRole::getTenantId, oConvertUtils.getInt(TenantContext.getTenant(), 0));
    }
    // ------------------------------------------------------------------------------------------------
    List<SysRole> list = sysRoleService.list(query);
    if (list == null || list.size() <= 0) {
      result.error500(I18nUtil.getMessage("sysRole.roleInfoNotFound"));
    } else {
      result.setResult(list);
      result.setSuccess(true);
    }
    return result;
  }

  /**
   * Query all system roles (no tenant isolation)
   *
   * @return
   */
  @SaCheckPermission("system:role:queryallNoByTenant")
  @AutoLog("RoleList-QueryAllSystemRole")
  @RequestMapping(value = "/queryallNoByTenant", method = RequestMethod.POST)
  public Result<List<SysRole>> queryallNoByTenant() {
    Result<List<SysRole>> result = new Result<>();
    LambdaQueryWrapper<SysRole> query = new LambdaQueryWrapper<SysRole>();
    List<SysRole> list = sysRoleService.list(query);
    if (list == null || list.size() <= 0) {
      result.error500(I18nUtil.getMessage("sysRole.roleInfoNotFound"));
    } else {
      result.setResult(list);
      result.setSuccess(true);
    }
    return result;
  }

  /** Check role code uniqueness */
  @PostMapping(value = "/checkRoleCode")
  @AutoLog("Role list - Validate role code uniqueness")
  public Result<Boolean> checkUsername(String id, String roleCode) {
    Result<Boolean> result = new Result<>();
    // If this param is false, program throws exception
    result.setResult(true);
    log.info("--Verify role code uniqueness---id:" + id + "--roleCode:" + roleCode);
    try {
      SysRole role = null;
      if (oConvertUtils.isNotEmpty(id)) {
        role = sysRoleService.getById(id);
      }
      // SysRole newRole = sysRoleService.getOne(new
      // QueryWrapper<SysRole>().lambda().eq(SysRole::getRoleCode, roleCode));
      SysRole newRole = sysRoleService.getRoleNoTenant(roleCode);
      if (newRole != null) {
        // If roleCode query returns results, perform check.
        if (role == null) {
          // role empty => add mode => return false if roleCode exists
          result.setSuccess(false);
          result.setMessage(I18nUtil.getMessage("sysRole.roleCodeExists"));
          return result;
        } else if (!id.equals(newRole.getId())) {
          // Otherwise => edit mode => check whether both IDs match
          result.setSuccess(false);
          result.setMessage(I18nUtil.getMessage("sysRole.roleCodeExists"));
          return result;
        }
      }
    } catch (Exception e) {
      result.setSuccess(false);
      result.setResult(false);
      result.setMessage(e.getMessage());
      return result;
    }
    result.setSuccess(true);
    return result;
  }

  /**
   * Export excel
   *
   * @param request
   */
  @RequestMapping(value = "/exportXls", method = RequestMethod.POST)
  public ModelAndView exportXls(SysRole sysRole, HttpServletRequest request) {
    // ------------------------------------------------------------------------------------------------
    // Whether multi-tenant data isolation is enabled for system admin [SAAS mode]
    if (MybatisPlusSaasConfig.OPEN_SYSTEM_TENANT_CONTROL) {
      sysRole.setTenantId(oConvertUtils.getInt(TenantContext.getTenant(), 0));
    }
    // ------------------------------------------------------------------------------------------------

    // Step.1 Build query conditions
    QueryWrapper<SysRole> queryWrapper =
        QueryGenerator.initQueryWrapper(sysRole, request.getParameterMap());
    // Step.2 Export Excel via AutoPoi
    ModelAndView mv = new ModelAndView(new JeecgEntityExcelView());
    List<SysRole> pageList = sysRoleService.list(queryWrapper);
    // Export file name
    mv.addObject(NormalExcelConstants.FILE_NAME, "RoleList");
    mv.addObject(NormalExcelConstants.CLASS, SysRole.class);
    LoginUser user = (LoginUser) StpUtil.getSession().get("loginUser");
    mv.addObject(
        NormalExcelConstants.PARAMS,
        new ExportParams("RoleListData", "Exported by:" + user.getRealname(), "Export information"));
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
  public Result<?> importExcel(HttpServletRequest request, HttpServletResponse response) {
    MultipartHttpServletRequest multipartRequest = (MultipartHttpServletRequest) request;
    Map<String, MultipartFile> fileMap = multipartRequest.getFileMap();
    for (Map.Entry<String, MultipartFile> entity : fileMap.entrySet()) {
      // Get uploaded file object
      MultipartFile file = entity.getValue();
      ImportParams params = new ImportParams();
      params.setTitleRows(2);
      params.setHeadRows(1);
      params.setNeedSave(true);
      try {
        return sysRoleService.importExcelCheckRoleCode(file, params);
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
    return Result.error(I18nUtil.getMessage("sysRole.importFailed", new Object[]{""}));
  }

  /** Query data rule records */
  @RequestMapping(value = "/datarule/{permissionId}/{roleId}", method = RequestMethod.POST)
  @AutoLog("Query data rule data")
  public Result<?> loadDatarule(
      @PathVariable("permissionId") String permissionId, @PathVariable("roleId") String roleId) {
    List<SysPermissionDataRule> list =
        sysPermissionDataRuleService.getPermRuleListByPermId(permissionId);
    if (list == null || list.size() == 0) {
      return Result.error(I18nUtil.getMessage("sysRole.permissionConfigNotFound"));
    } else {
      Map<String, Object> map = new HashMap(5);
      map.put("datarule", list);
      LambdaQueryWrapper<SysRolePermission> query =
          new LambdaQueryWrapper<SysRolePermission>()
              .eq(SysRolePermission::getPermissionId, permissionId)
              .isNotNull(SysRolePermission::getDataRuleIds)
              .eq(SysRolePermission::getRoleId, roleId);
      SysRolePermission sysRolePermission = sysRolePermissionService.getOne(query);
      if (sysRolePermission == null) {
        // return Result.error("Role menu configuration not found");
      } else {
        String drChecked = sysRolePermission.getDataRuleIds();
        if (oConvertUtils.isNotEmpty(drChecked)) {
          map.put(
              "drChecked",
              drChecked.endsWith(",") ? drChecked.substring(0, drChecked.length() - 1) : drChecked);
        }
      }
      return Result.ok(map);
      // TODO Later button permission queries can also use this request; just add two more keys in the map
    }
  }

  /** Save data rules to role-menu association table */
  @PostMapping(value = "/datarule")
  @AutoLog("Save data rules to role-menu relation table")
  public Result<?> saveDatarule(@RequestBody JSONObject jsonObject) {
    try {
      String permissionId = jsonObject.getString("permissionId");
      String roleId = jsonObject.getString("roleId");
      String dataRuleIds = jsonObject.getString("dataRuleIds");
      log.info("Save data rules>>" + "MenuID:" + permissionId + "RoleID:" + roleId + "DataPermissionID:" + dataRuleIds);
      LambdaQueryWrapper<SysRolePermission> query =
          new LambdaQueryWrapper<SysRolePermission>()
              .eq(SysRolePermission::getPermissionId, permissionId)
              .eq(SysRolePermission::getRoleId, roleId);
      SysRolePermission sysRolePermission = sysRolePermissionService.getOne(query);
      if (sysRolePermission == null) {
        return Result.error(I18nUtil.getMessage("sysRole.saveMenuPermissionFirst"));
      } else {
        sysRolePermission.setDataRuleIds(dataRuleIds);
        this.sysRolePermissionService.updateById(sysRolePermission);
      }
    } catch (Exception e) {
      log.error("operation failed", e);
      return Result.error(I18nUtil.getMessage("sysRole.saveFailed"));
    }
    return Result.ok(I18nUtil.getMessage("sysRole.saveSuccess"));
  }

  /**
   * User role authorization: query menu permission tree
   *
   * @param request
   * @return
   */
  @RequestMapping(value = "/queryTreeList", method = RequestMethod.POST)
  @AutoLog("Query menu permission tree")
  public Result<Map<String, Object>> queryTreeList(HttpServletRequest request) {
    Result<Map<String, Object>> result = new Result<>();
    // All permission ids
    List<String> ids = new ArrayList<>();
    try {
      LambdaQueryWrapper<SysPermission> query = new LambdaQueryWrapper<SysPermission>();
      query.eq(SysPermission::getDelFlag, CommonConstant.DEL_FLAG_0);
      query.orderByAsc(SysPermission::getSortNo);
      List<SysPermission> list = sysPermissionService.list(query);
      for (SysPermission sysPer : list) {
        ids.add(sysPer.getId());
      }
      // Process multilingual name field by current locale
      String currentLang = LocaleResolver.getCurrLang();
      if (oConvertUtils.isEmpty(currentLang)) {
        currentLang = "zh_CN"; // Default to Chinese
      }
      for (SysPermission permission : list) {
        String localizedName = getLocalizedName(permission.getName(), currentLang);
        if (oConvertUtils.isNotEmpty(localizedName)) {
          permission.setName(localizedName);
        }
      }
      List<TreeModel> treeList = new ArrayList<>();
      getTreeModelList(treeList, list, null);
      Map<String, Object> resMap = new HashMap(5);
      // All tree node data
      resMap.put("treeList", treeList);
      // All tree ids
      resMap.put("ids", ids);
      result.setResult(resMap);
      result.setSuccess(true);
    } catch (Exception e) {
      log.error("operation failed", e);
    }
    return result;
  }

  private void getTreeModelList(
      List<TreeModel> treeList, List<SysPermission> metaList, TreeModel temp) {
    for (SysPermission permission : metaList) {
      String tempPid = permission.getParentId();
      TreeModel tree =
          new TreeModel(
              permission.getId(),
              tempPid,
              permission.getName(),
              permission.getRuleFlag(),
              permission.isLeaf());
      if (temp == null && oConvertUtils.isEmpty(tempPid)) {
        treeList.add(tree);
        if (!tree.getIsLeaf()) {
          getTreeModelList(treeList, metaList, tree);
        }
      } else if (temp != null && tempPid != null && tempPid.equals(temp.getKey())) {
        temp.getChildren().add(tree);
        if (!tree.getIsLeaf()) {
          getTreeModelList(treeList, metaList, tree);
        }
      }
    }
  }

  /**
   * Resolve multilingual name field by current locale
   *
   * @param nameJson multilingual JSON, e.g. {"zh_CN": "Menu Management", "en": "Menu Management"}
   * @param currentLang current locale, e.g. "zh_CN" or "en"
   * @return localized text; returns original value if parse fails
   */
  private String getLocalizedName(String nameJson, String currentLang) {
    if (oConvertUtils.isEmpty(nameJson)) {
      return nameJson;
    }
    try {
      JSONObject nameObj = JSONObject.parseObject(nameJson);
      if (nameObj != null) {
        if (nameObj.containsKey(currentLang)) {
          String localizedName = nameObj.getString(currentLang);
          if (oConvertUtils.isNotEmpty(localizedName)) {
            return localizedName;
          }
        }
        String fallbackLang = "zh_CN".equals(currentLang) ? "en" : "zh_CN";
        if (nameObj.containsKey(fallbackLang)) {
          String fallbackName = nameObj.getString(fallbackLang);
          if (oConvertUtils.isNotEmpty(fallbackName)) {
            return fallbackName;
          }
        }
        for (String key : nameObj.keySet()) {
          String value = nameObj.getString(key);
          if (oConvertUtils.isNotEmpty(value)) {
            return value;
          }
        }
      }
    } catch (Exception e) {
      log.debug("name field is not JSON format, returning raw value: {}", nameJson);
    }
    return nameJson;
  }

  /**
   * Paginated list of all roles (includes count per role)
   *
   * @return
   */
  @RequestMapping(value = "/queryPageRoleCount", method = RequestMethod.POST)
  @AutoLog("Paginated query of all roles (includes count per role)")
  public Result<IPage<SysUserRoleCountVo>> queryPageRoleCount(
      @RequestParam(name = "pageNo", defaultValue = "1") Integer pageNo,
      @RequestParam(name = "pageSize", defaultValue = "10") Integer pageSize) {
    Result<IPage<SysUserRoleCountVo>> result = new Result<>();
    LambdaQueryWrapper<SysRole> query = new LambdaQueryWrapper<SysRole>();
    // ------------------------------------------------------------------------------------------------
    // Whether multi-tenant data isolation is enabled for system admin [SAAS mode]
    if (MybatisPlusSaasConfig.OPEN_SYSTEM_TENANT_CONTROL) {
      query.eq(SysRole::getTenantId, oConvertUtils.getInt(TenantContext.getTenant(), 0));
    }
    // ------------------------------------------------------------------------------------------------
    Page<SysRole> page = new Page<>(pageNo, pageSize);
    IPage<SysRole> pageList = sysRoleService.page(page, query);
    List<SysRole> records = pageList.getRecords();
    IPage<SysUserRoleCountVo> sysRoleCountPage = new PageDTO<>();
    List<SysUserRoleCountVo> sysCountVoList = new ArrayList<>();
    // Iterate roles to get user count per role
    for (SysRole role : records) {
      LambdaQueryWrapper<SysUserRole> countQuery = new LambdaQueryWrapper<>();
      countQuery.eq(SysUserRole::getRoleId, role.getId());
      long count = sysUserRoleService.count(countQuery);
      SysUserRoleCountVo countVo = new SysUserRoleCountVo();
      BeanUtils.copyProperties(role, countVo);
      countVo.setCount(count);
      sysCountVoList.add(countVo);
    }
    sysRoleCountPage.setRecords(sysCountVoList);
    sysRoleCountPage.setTotal(pageList.getTotal());
    sysRoleCountPage.setSize(pageList.getSize());
    result.setSuccess(true);
    result.setResult(sysRoleCountPage);
    return result;
  }
}
