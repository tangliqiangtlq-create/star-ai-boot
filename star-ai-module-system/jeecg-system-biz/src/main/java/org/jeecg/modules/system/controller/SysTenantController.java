package org.jeecg.modules.system.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.annotation.SaMode;
import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.util.*;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.jeecg.common.api.vo.Result;
import org.jeecg.common.aspect.annotation.PermissionData;
import org.jeecg.common.config.TenantContext;
import org.jeecg.common.constant.CommonConstant;
import org.jeecg.common.constant.SecurityRoleConstants;
import org.jeecg.common.constant.SymbolConstant;
import org.jeecg.common.system.query.QueryGenerator;
import org.jeecg.common.system.vo.LoginUser;
import org.jeecg.common.util.I18nUtil;
import org.jeecg.common.util.PasswordUtil;
import org.jeecg.common.util.oConvertUtils;
import org.jeecg.config.mybatis.MybatisPlusSaasConfig;
import org.jeecg.modules.base.service.BaseCommonService;
import org.jeecg.modules.system.entity.*;
import org.jeecg.modules.system.service.ISysDepartService;
import org.jeecg.modules.system.service.ISysTenantPackService;
import org.jeecg.modules.system.service.ISysTenantService;
import org.jeecg.modules.system.service.ISysUserService;
import org.jeecg.modules.system.service.ISysUserTenantService;
import org.jeecg.modules.system.vo.SysUserTenantVo;
import org.jeecg.modules.system.vo.tenant.TenantDepartAuthInfo;
import org.jeecg.modules.system.vo.tenant.TenantPackModel;
import org.jeecg.modules.system.vo.tenant.TenantPackUser;
import org.jeecg.modules.system.vo.tenant.TenantPackUserCount;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * TenantConfigInfo
 *
 * @author: jeecg-boot
 */
@Slf4j
@RestController
@RequestMapping("/sys/tenant")
@SaCheckRole(value = {SecurityRoleConstants.ROLE_OPS_ADMIN, "admin"}, mode = SaMode.OR)
public class SysTenantController {

  @Autowired private ISysTenantService sysTenantService;

  @Autowired private ISysUserService sysUserService;

  @Autowired private ISysUserTenantService relationService;

  @Autowired private ISysTenantPackService sysTenantPackService;

  @Autowired private BaseCommonService baseCommonService;

  @Autowired private ISysDepartService sysDepartService;

  /**
 * Get list data
 *
 * @param sysTenant
 * @param pageNo
 * @param pageSize
 * @param req
 * @return*/
  @SaCheckPermission("system:tenant:list")
  @PermissionData(pageComponent = "system/TenantList")
  @RequestMapping(value = "/list", method = RequestMethod.POST)
  public Result<IPage<SysTenant>> queryPageList(
      SysTenant sysTenant,
      @RequestParam(name = "pageNo", defaultValue = "1") Integer pageNo,
      @RequestParam(name = "pageSize", defaultValue = "10") Integer pageSize,
      HttpServletRequest req) {
    Result<IPage<SysTenant>> result = new Result<IPage<SysTenant>>();
    // ---author:zhangyafei---date:20210916-----for: add date range query to tenant management---
    Date beginDate = null;
    Date endDate = null;
    if (oConvertUtils.isNotEmpty(sysTenant)) {
      beginDate = sysTenant.getBeginDate();
      endDate = sysTenant.getEndDate();
      sysTenant.setBeginDate(null);
      sysTenant.setEndDate(null);
    }
    // ---author:zhangyafei---date:20210916-----for: add date range query to tenant management---
    QueryWrapper<SysTenant> queryWrapper =
        QueryGenerator.initQueryWrapper(sysTenant, req.getParameterMap());
    // ---author:zhangyafei---date:20210916-----for: add date range query to tenant management---
    if (oConvertUtils.isNotEmpty(sysTenant)) {
      queryWrapper.ge(oConvertUtils.isNotEmpty(beginDate), "begin_date", beginDate);
      queryWrapper.le(oConvertUtils.isNotEmpty(endDate), "end_date", endDate);
    }
    // ---author:zhangyafei---date:20210916-----for: add date range query to tenant management---
    Page<SysTenant> page = new Page<SysTenant>(pageNo, pageSize);
    IPage<SysTenant> pageList = sysTenantService.page(page, queryWrapper);
    result.setSuccess(true);
    result.setResult(pageList);
    return result;
  }

  /**
   * Get list of deleted tenants
   *
   * @param sysTenant
   * @param pageNo
   * @param pageSize
   * @param req
   * @return
   */
  @PostMapping("/recycleBinPageList")
  @SaCheckPermission("system:tenant:recycleBinPageList")
  public Result<IPage<SysTenant>> recycleBinPageList(
      SysTenant sysTenant,
      @RequestParam(name = "pageNo", defaultValue = "1") Integer pageNo,
      @RequestParam(name = "pageSize", defaultValue = "10") Integer pageSize,
      HttpServletRequest req) {
    Result<IPage<SysTenant>> result = new Result<IPage<SysTenant>>();
    Page<SysTenant> page = new Page<SysTenant>(pageNo, pageSize);
    IPage<SysTenant> pageList = sysTenantService.getRecycleBinPageList(page, sysTenant);
    result.setSuccess(true);
    result.setResult(pageList);
    return result;
  }

  /**
   * Add
   *
   * @param
   * @return
   */
  @SaCheckPermission("system:tenant:add")
  @RequestMapping(value = "/add", method = RequestMethod.POST)
  public Result<SysTenant> add(@RequestBody SysTenant sysTenant) {
    Result<SysTenant> result = new Result();
    if (sysTenantService.getById(sysTenant.getId()) != null) {
      return result.error500(I18nUtil.getMessage("sysTenant.idExists"));
    }
    try {
      sysTenantService.saveTenant(sysTenant);
      // Add default product pack
      sysTenantPackService.addTenantDefaultPack(sysTenant.getId());
      result.success(I18nUtil.getMessage("sysTenant.addSuccess"));
    } catch (Exception e) {
      log.error("operation failed", e);
      result.error500(I18nUtil.getMessage("sysUser.operationFailed"));
    }
    return result;
  }

  /**
   * Edit
   *
   * @param
   * @return
   */
  @SaCheckPermission("system:tenant:edit")
  @RequestMapping(
      value = "/edit",
      method = {RequestMethod.PUT, RequestMethod.POST})
  public Result<SysTenant> edit(@RequestBody SysTenant tenant) {
    Result<SysTenant> result = new Result();
    SysTenant sysTenant = sysTenantService.getById(tenant.getId());
    if (sysTenant == null) {
      return Result.error(404, I18nUtil.getMessage("sysUser.entityNotFound"));
    }
    if (oConvertUtils.isEmpty(sysTenant.getHouseNumber())) {
      tenant.setHouseNumber(RandomUtil.randomStringUpper(6));
    }
    boolean ok = sysTenantService.updateById(tenant);
    if (ok) {
      result.success(I18nUtil.getMessage("sysTenant.modifySuccess"));
    }
    return result;
  }

  /**
   * Delete by id
   *
   * @param id
   * @return
   */
  @SaCheckPermission("system:tenant:delete")
  @RequestMapping(
      value = "/delete",
      method = {RequestMethod.DELETE, RequestMethod.POST})
  public Result<?> delete(@RequestParam(name = "id", required = true) String id) {
    // ------------------------------------------------------------------
    // Under SaaS isolation, verify current tenant id belongs to current tenant
    if (MybatisPlusSaasConfig.OPEN_SYSTEM_TENANT_CONTROL) {
      // Get current user
      LoginUser sysUser = (LoginUser) StpUtil.getSession().get("loginUser");
      SysTenant sysTenant = sysTenantService.getById(id);

      String username = "admin";
      String createdBy = sysUser.getUsername();
      if (!sysTenant.getCreateBy().equals(createdBy) && !username.equals(createdBy)) {
        baseCommonService.addLog(
            "Unauthorized, cannot delete tenant not created by self, tenant ID: " + id + ", operator: " + sysUser.getUsername(),
            CommonConstant.LOG_TYPE_2,
            CommonConstant.OPERATE_TYPE_3);
        return Result.error(I18nUtil.getMessage("sysTenant.deleteFailedNotCreator"));
      }
    }
    // ------------------------------------------------------------------

    sysTenantService.removeTenantById(id);
    return Result.ok(I18nUtil.getMessage("sysTenant.deleteSuccess"));
  }

  /**
   * Batch delete
   *
   * @param ids
   * @return
   */
  @SaCheckPermission("system:tenant:deleteBatch")
  @RequestMapping(value = "/deleteBatch", method = RequestMethod.DELETE)
  public Result<?> deleteBatch(@RequestParam(name = "ids", required = true) String ids) {
    Result<?> result = new Result<>();
    if (oConvertUtils.isEmpty(ids)) {
      result.error500(I18nUtil.getMessage("sysTenant.tenantNotSelected"));
    } else {
      String[] ls = ids.split(",");
      // Filter out already referenced tenants
      List<Integer> idList = new ArrayList<>();
      for (String id : ls) {
        // ------------------------------------------------------------------
        // Under SaaS isolation, verify current tenant id belongs to current tenant
        if (MybatisPlusSaasConfig.OPEN_SYSTEM_TENANT_CONTROL) {
          // Get current user
          LoginUser sysUser = (LoginUser) StpUtil.getSession().get("loginUser");
          SysTenant sysTenant = sysTenantService.getById(id);

          String username = "admin";
          String createdBy = sysUser.getUsername();
          if (!sysTenant.getCreateBy().equals(createdBy) && !username.equals(createdBy)) {
            baseCommonService.addLog(
                "Unauthorized, cannot delete tenant not created by self, tenant ID: " + id + ", operator: " + sysUser.getUsername(),
                CommonConstant.LOG_TYPE_2,
                CommonConstant.OPERATE_TYPE_3);
            return Result.error(I18nUtil.getMessage("sysTenant.deleteFailedNotCreator"));
          }
        }
        // ------------------------------------------------------------------

        idList.add(Integer.parseInt(id));
      }
      // update-begin---author:wangshuai ---date:20230710
      // for: [QQYUN-5723] 3. Tenant deletion deletes directly without deleting intermediate tables ------------
      sysTenantService.removeByIds(idList);
      result.success(I18nUtil.getMessage("sysTenant.deleteSuccess"));
      // update-end---author:wangshuai ---date:20220523
      // for: [QQYUN-5723] 3. Tenant deletion deletes directly without deleting intermediate tables ------------
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
  public Result<SysTenant> queryById(@RequestParam(name = "id", required = true) String id) {
    Result<SysTenant> result = new Result<SysTenant>();
    if (oConvertUtils.isEmpty(id)) {
      result.error500(I18nUtil.getMessage("sysTenant.paramEmpty"));
    }
    // ------------------------------------------------------------------------------------------------
    // GetloginuserInfo
    LoginUser sysUser = (LoginUser) StpUtil.getSession().get("loginUser");
    // Enable multi-tenant isolation for system module [SAAS mode]; admin has privilege to manage all tenants
    if (MybatisPlusSaasConfig.OPEN_SYSTEM_TENANT_CONTROL
        && !"admin".equals(sysUser.getUsername())) {
      Integer loginSessionTenant = oConvertUtils.getInt(TenantContext.getTenant());
      if (loginSessionTenant != null && !loginSessionTenant.equals(Integer.valueOf(id))) {
        result.error500(I18nUtil.getMessage("sysTenant.noPermissionAccessOtherTenant"));
        return result;
      }
    }
    // ------------------------------------------------------------------------------------------------
    SysTenant sysTenant = sysTenantService.getById(id);
    if (sysTenant == null) {
      return Result.error(404, I18nUtil.getMessage("sysUser.entityNotFound"));
    } else {
      result.setResult(sysTenant);
      result.setSuccess(true);
    }
    return result;
  }

  /**
   * Query valid tenant data
   *
   * @return
   */
  @SaCheckPermission("system:tenant:queryList")
  @PostMapping(value = "/queryList")
  public Result<List<SysTenant>> queryList(
      @RequestParam(name = "ids", required = false) String ids) {
    Result<List<SysTenant>> result = new Result<List<SysTenant>>();
    LambdaQueryWrapper<SysTenant> query = new LambdaQueryWrapper<>();
    query.eq(SysTenant::getStatus, 1);
    if (oConvertUtils.isNotEmpty(ids)) {
      query.in(SysTenant::getId, ids.split(","));
    }
    // This query ignores time conditions
    List<SysTenant> ls = sysTenantService.list(query);
    result.setSuccess(true);
    result.setResult(ls);
    return result;
  }

  /**
   * Tenant product pack paginated list query
   *
   * @param sysTenantPack
   * @param pageNo
   * @param pageSize
   * @param req
   * @return
   */
  @PostMapping(value = "/packList")
  @SaCheckPermission("system:tenant:packList")
  public Result<IPage<SysTenantPack>> queryPackPageList(
      SysTenantPack sysTenantPack,
      @RequestParam(name = "pageNo", defaultValue = "1") Integer pageNo,
      @RequestParam(name = "pageSize", defaultValue = "10") Integer pageSize,
      HttpServletRequest req) {
    QueryWrapper<SysTenantPack> queryWrapper =
        QueryGenerator.initQueryWrapper(sysTenantPack, req.getParameterMap());
    Page<SysTenantPack> page = new Page<SysTenantPack>(pageNo, pageSize);
    IPage<SysTenantPack> pageList = sysTenantPackService.page(page, queryWrapper);
    List<SysTenantPack> records = pageList.getRecords();
    if (null != records && records.size() > 0) {
      pageList.setRecords(sysTenantPackService.setPermissions(records));
    }
    return Result.OK(pageList);
  }

  /**
   * Create tenant product pack
   *
   * @param sysTenantPack
   * @return
   */
  @PostMapping(value = "/addPackPermission")
  @SaCheckPermission("system:tenant:add:pack")
  public Result<String> addPackPermission(@RequestBody SysTenantPack sysTenantPack) {
    sysTenantPackService.addPackPermission(sysTenantPack);
    return Result.ok(I18nUtil.getMessage("sysTenant.createPackSuccess"));
  }

  /**
   * Create tenant product pack
   *
   * @param sysTenantPack
   * @return
   */
  @PutMapping(value = "/editPackPermission")
  @SaCheckPermission("system:tenant:edit:pack")
  public Result<String> editPackPermission(@RequestBody SysTenantPack sysTenantPack) {
    sysTenantPackService.editPackPermission(sysTenantPack);
    return Result.ok(I18nUtil.getMessage("sysTenant.modifyPackSuccess"));
  }

  /**
   * Batch deleteUserMenu
   *
   * @param ids
   * @return
   */
  @DeleteMapping("/deleteTenantPack")
  @SaCheckPermission("system:tenant:delete:pack")
  public Result<String> deleteTenantPack(@RequestParam(value = "ids") String ids) {
    sysTenantPackService.deleteTenantPack(ids);
    return Result.ok(I18nUtil.getMessage("sysTenant.deletePackSuccess"));
  }

  // ===========[Low-code app, frontend-only APIs ——
  // Restrict to maintain/view only own tenants] ==========================================================
  /**
   * Query all valid tenants for current user [low-code app dedicated API]
   *
   * @return
   */
  @PostMapping(value = "/getCurrentUserTenant")
  public Result<Map<String, Object>> getCurrentUserTenant() {
    Result<Map<String, Object>> result = new Result<Map<String, Object>>();
    try {
      LoginUser sysUser = (LoginUser) StpUtil.getSession().get("loginUser");
      // update-begin---author:wangshuai ---date:20221223 for：[QQYUN-3371]tenant logic refactor to relation table------------
      List<Integer> tenantIdList = relationService.getTenantIdsByUserId(sysUser.getId());
      Map<String, Object> map = new HashMap(5);
      if (null != tenantIdList && tenantIdList.size() > 0) {
        // update-end---author:wangshuai ---date:20221223 for：[QQYUN-3371]tenant logic refactor to relation table------------
        // This method queries only active tenants; 0 results means all tenants are invalid.
        List<SysTenant> tenantList = sysTenantService.queryEffectiveTenant(tenantIdList);
        map.put("list", tenantList);
      }
      result.setSuccess(true);
      result.setResult(map);
    } catch (Exception e) {
      log.error("operation failed", e);
      result.error500(I18nUtil.getMessage("sysTenant.queryFailed"));
    }
    return result;
  }

  /**
   * Invite user [low-code app dedicated API]
   *
   * @param ids
   * @param phone
   * @return
   */
  @PutMapping("/invitationUserJoin")
  @SaCheckPermission("system:tenant:invitation:user")
  public Result<String> invitationUserJoin(
      @RequestParam("ids") String ids, @RequestParam("phone") String phone) {
    sysTenantService.invitationUserJoin(ids, phone);
    return Result.ok(I18nUtil.getMessage("sysTenant.inviteUserSuccess"));
  }

  /**
   * Get user list [low-code app API]
   *
   * @param user
   * @param pageNo
   * @param pageSize
   * @param req
   * @return
   */
  @PostMapping(value = "/getTenantUserList")
  @SaCheckPermission("system:tenant:user:list")
  public Result<IPage<SysUser>> getTenantUserList(
      SysUser user,
      @RequestParam(name = "pageNo", defaultValue = "1") Integer pageNo,
      @RequestParam(name = "pageSize", defaultValue = "10") Integer pageSize,
      @RequestParam(name = "userTenantId") String userTenantId,
      HttpServletRequest req) {
    Result<IPage<SysUser>> result = new Result<>();
    Page<SysUser> page = new Page<>(pageNo, pageSize);
    Page<SysUser> pageList =
        relationService.getPageUserList(page, Integer.valueOf(userTenantId), user);
    result.setSuccess(true);
    result.setResult(pageList);
    return result;
  }

  /**
   * Remove user from tenant [low-code app API]
   *
   * @param userIds
   * @param tenantId
   * @return
   */
  @PutMapping("/leaveTenant")
  @SaCheckPermission("system:tenant:leave")
  public Result<String> leaveTenant(
      @RequestParam("userIds") String userIds, @RequestParam("tenantId") String tenantId) {
    Result<String> result = new Result<>();
    // Whether multi-tenant data isolation is enabled for system admin [SAAS mode]
    LoginUser sysUser = (LoginUser) StpUtil.getSession().get("loginUser");
    if (MybatisPlusSaasConfig.OPEN_SYSTEM_TENANT_CONTROL
        && !"admin".equals(sysUser.getUsername())) {
      Integer loginSessionTenant = oConvertUtils.getInt(TenantContext.getTenant());
      if (loginSessionTenant != null && !loginSessionTenant.equals(Integer.valueOf(tenantId))) {
        result.error500(I18nUtil.getMessage("sysTenant.noPermissionAccessOtherTenant"));
        return result;
      }
    }
    sysTenantService.leaveTenant(userIds, tenantId);
    return Result.ok(I18nUtil.getMessage("sysTenant.leaveSuccess"));
  }

  /**
   * Create tenant and save user to junction table [low-code app dedicated API]
   *
   * @param sysTenant
   */
  @PostMapping("/saveTenantJoinUser")
  public Result<Integer> saveTenantJoinUser(@RequestBody SysTenant sysTenant) {
    Result<Integer> result = new Result<>();
    LoginUser sysUser = (LoginUser) StpUtil.getSession().get("loginUser");
    Integer tenantId = sysTenantService.saveTenantJoinUser(sysTenant, sysUser.getId());
    result.setSuccess(true);
    result.setMessage(I18nUtil.getMessage("sysTenant.createSuccess"));
    result.setResult(tenantId);
    return result;
  }

  /**
   * Join tenant by door number [low-code app dedicated API]
   *
   * @param sysTenant
   */
  @PostMapping("/joinTenantByHouseNumber")
  public Result<Integer> joinTenantByHouseNumber(@RequestBody SysTenant sysTenant) {
    LoginUser sysUser = (LoginUser) StpUtil.getSession().get("loginUser");
    Integer tenantId = sysTenantService.joinTenantByHouseNumber(sysTenant, sysUser.getId());
    Result<Integer> result = new Result<>();
    if (tenantId != 0) {
      result.setMessage(I18nUtil.getMessage("sysTenant.applyJoinSuccess"));
      result.setSuccess(true);
      result.setResult(tenantId);
      return result;
    } else {
      result.setMessage(I18nUtil.getMessage("sysTenant.houseNumberNotExist"));
      result.setSuccess(false);
      return result;
    }
  }

  // update-begin---author:wangshuai ---date:20230107  for: [QQYUN-3725] Apply to join tenant: add API for pending review status ------------
  /**
   * Paginated tenant user data (vue3 user-tenant page) [low-code app dedicated API]
   *
   * @param pageNo
   * @param pageSize
   * @param userTenantStatus
   * @param type
   * @param req
   * @return
   */
  @PostMapping("/getUserTenantPageList")
  // @SaCheckPermission("system:tenant:tenantPageList")
  public Result<IPage<SysUserTenantVo>> getUserTenantPageList(
      @RequestParam(name = "pageNo", defaultValue = "1") Integer pageNo,
      @RequestParam(name = "pageSize", defaultValue = "10") Integer pageSize,
      @RequestParam(name = "userTenantStatus") String userTenantStatus,
      @RequestParam(name = "type", required = false) String type,
      SysUser user,
      HttpServletRequest req) {
    Page<SysUserTenantVo> page = new Page<SysUserTenantVo>(pageNo, pageSize);
    LoginUser sysUser = (LoginUser) StpUtil.getSession().get("loginUser");
    String tenantId = oConvertUtils.getString(TenantContext.getTenant(), "0");
    IPage<SysUserTenantVo> list =
        relationService.getUserTenantPageList(
            page,
            Arrays.asList(userTenantStatus.split(SymbolConstant.COMMA)),
            user,
            Integer.valueOf(tenantId));
    return Result.ok(list);
  }

  /**
   * Get tenant list by user id [low-code app dedicated API]
   *
   * @param userTenantStatus relation table status
   * @return
   */
  @PostMapping("/getTenantListByUserId")
  // @SaCheckPermission("system:tenant:getTenantListByUserId")
  public Result<List<SysUserTenantVo>> getTenantListByUserId(
      @RequestParam(name = "userTenantStatus", required = false) String userTenantStatus) {
    LoginUser sysUser = (LoginUser) StpUtil.getSession().get("loginUser");
    List<String> list = null;
    if (oConvertUtils.isNotEmpty(userTenantStatus)) {
      list = Arrays.asList(userTenantStatus.split(SymbolConstant.COMMA));
    }
    // Tenant status, user id, tenant-user relation status
    List<SysUserTenantVo> sysTenant = relationService.getTenantListByUserId(sysUser.getId(), list);
    return Result.ok(sysTenant);
  }

  /** Update user-tenant relationship status [low-code app dedicated API] */
  @PutMapping("/updateUserTenantStatus")
  // @SaCheckPermission("system:tenant:updateUserTenantStatus")
  public Result<String> updateUserTenantStatus(@RequestBody SysUserTenant userTenant) {
    String tenantId = TenantContext.getTenant();
    if (oConvertUtils.isEmpty(tenantId)) {
      return Result.error(I18nUtil.getMessage("sysTenant.tenantInfoNotFound"));
    }
    relationService.updateUserTenantStatus(
        userTenant.getUserId(), tenantId, userTenant.getStatus());
    return Result.ok(I18nUtil.getMessage("sysTenant.updateUserTenantStatusSuccess"));
  }

  /**
   * Deregister tenant [low-code app dedicated API]
   *
   * @param sysTenant
   * @return
   */
  @PutMapping("/cancelTenant")
  // @SaCheckPermission("system:tenant:cancelTenant")
  public Result<String> cancelTenant(@RequestBody SysTenant sysTenant, HttpServletRequest request) {
    LoginUser sysUser = (LoginUser) StpUtil.getSession().get("loginUser");
    SysTenant tenant = sysTenantService.getById(sysTenant.getId());
    if (null == tenant) {
      return Result.error(I18nUtil.getMessage("sysTenant.tenantInfoNotFound"));
    }
    if (!sysUser.getUsername().equals(tenant.getCreateBy())) {
      return Result.error(403, I18nUtil.getMessage("sysTenant.noPermissionCancelOnlyOwn"));
    }
    // P4: explicitly select password/salt — @TableField(select=false) on SysUser.password
    // means getById() no longer fetches the password column.
    SysUser userById = sysUserService.getOne(
        new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SysUser>()
            .select(SysUser::getId, SysUser::getUsername, SysUser::getPassword, SysUser::getSalt)
            .eq(SysUser::getId, sysUser.getId()));
    String loginPassword = request.getParameter("loginPassword");
    if (!org.jeecg.common.util.CredentialGuard.isPresentLoginPassword(loginPassword)
        || !org.jeecg.common.util.CredentialGuard.hasStoredPasswordHash(userById.getPassword())
        || !org.jeecg.common.util.crypto.SmCryptoUtil.checkPassword(
            loginPassword, userById.getPassword())) {
      return Result.error(I18nUtil.getMessage("sysTenant.passwordIncorrect"));
    }
    sysTenantService.removeById(sysTenant.getId());
    return Result.ok(I18nUtil.getMessage("sysTenant.cancelSuccess"));
  }

  // update-end---author:wangshuai ---date:20230107  for: [QQYUN-3725] Apply to join tenant: add API for pending review status ------------

  /**
   * User cancels tenant application [low-code app dedicated API]
   *
   * @param tenantId
   * @return
   */
  @PutMapping("/cancelApplyTenant")
  public Result<String> cancelApplyTenant(@RequestParam("tenantId") String tenantId) {
    LoginUser sysUser = (LoginUser) StpUtil.getSession().get("loginUser");
    sysTenantService.leaveTenant(sysUser.getId(), tenantId);
    return Result.ok(I18nUtil.getMessage("sysTenant.cancelApplySuccess"));
  }

  // ===========[Low-code app, frontend-only APIs ——
  // Restrict to maintain/view only own tenants] ==========================================================

  /**
   * Permanently delete tenants
   *
   * @param ids
   * @return
   */
  @DeleteMapping("/deleteLogicDeleted")
  @SaCheckPermission("system:tenant:deleteTenantLogic")
  public Result<String> deleteTenantLogic(@RequestParam("ids") String ids) {
    sysTenantService.deleteTenantLogic(ids);
    return Result.ok(I18nUtil.getMessage("sysTenant.deletePermanentlySuccess"));
  }

  /**
   * Restore deleted tenant
   *
   * @param ids
   * @return
   */
  @PutMapping("/revertTenantLogic")
  @SaCheckPermission("system:tenant:revertTenantLogic")
  public Result<String> revertTenantLogic(@RequestParam("ids") String ids) {
    sysTenantService.revertTenantLogic(ids);
    return Result.ok(I18nUtil.getMessage("sysTenant.restoreSuccess"));
  }

  /**
   * Leave tenant [low-code app API]
   *
   * @param sysTenant
   * @param request
   * @return
   */
  @DeleteMapping("/exitUserTenant")
  public Result<String> exitUserTenant(
      @RequestBody SysTenant sysTenant, HttpServletRequest request) {
    LoginUser sysUser = (LoginUser) StpUtil.getSession().get("loginUser");
    // Verify whether user already exists
    Integer count = relationService.userTenantIzExist(sysUser.getId(), sysTenant.getId());
    if (count == 0) {
      return Result.error(I18nUtil.getMessage("sysTenant.userNotInTenant"));
    }
    // Verify password
    String loginPassword = request.getParameter("loginPassword");
    // P4: explicitly select password/salt — @TableField(select=false) on SysUser.password
    // means getById() no longer fetches the password column.
    SysUser userById = sysUserService.getOne(
        new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SysUser>()
            .select(SysUser::getId, SysUser::getUsername, SysUser::getPassword, SysUser::getSalt)
            .eq(SysUser::getId, sysUser.getId()));
    if (!org.jeecg.common.util.CredentialGuard.isPresentLoginPassword(loginPassword)
        || !org.jeecg.common.util.CredentialGuard.hasStoredPasswordHash(userById.getPassword())
        || !org.jeecg.common.util.crypto.SmCryptoUtil.checkPassword(
            loginPassword, userById.getPassword())) {
      return Result.error(I18nUtil.getMessage("sysTenant.passwordIncorrect"));
    }
    // Logout
    sysTenantService.exitUserTenant(
        sysUser.getId(), sysUser.getUsername(), String.valueOf(sysTenant.getId()));
    return Result.ok(I18nUtil.getMessage("sysTenant.leaveTenantSuccess"));
  }

  /**
   * Change tenant owner [low-code app dedicated API]
   *
   * @param userId
   * @return
   */
  @PostMapping("/changeOwenUserTenant")
  public Result<String> changeOwenUserTenant(
      @RequestParam("userId") String userId, @RequestParam("tenantId") String tenantId) {
    sysTenantService.changeOwenUserTenant(userId, tenantId);
    return Result.ok(I18nUtil.getMessage("sysTenant.leaveTenantSuccess"));
  }

  /**
   * Invite user to tenant by phone match [low-code app dedicated API]
   *
   * @param phone
   * @param departId
   * @return
   */
  @PostMapping("/invitationUser")
  public Result<String> invitationUser(
      @RequestParam(name = "phone") String phone,
      @RequestParam(name = "departId", defaultValue = "") String departId) {
    return sysTenantService.invitationUser(phone, departId);
  }

  /**
   * Get count of 3 default admin users in tenant product pack
   *
   * @param tenantId
   * @return
   */
  @RequestMapping(value = "/loadAdminPackCount", method = RequestMethod.POST)
  public Result<List<TenantPackUserCount>> loadAdminPackCount(
      @RequestParam("tenantId") Integer tenantId) {
    List<TenantPackUserCount> list = sysTenantService.queryTenantPackUserCount(tenantId);
    return Result.ok(list);
  }

  /**
   * Query tenant product pack info
   *
   * @param packModel
   * @return
   */
  @RequestMapping(value = "/getTenantPackInfo", method = RequestMethod.POST)
  public Result<TenantPackModel> getTenantPackInfo(TenantPackModel packModel) {
    TenantPackModel tenantPackModel = sysTenantService.queryTenantPack(packModel);
    return Result.ok(tenantPackModel);
  }

  /**
   * Add user-product pack relation
   *
   * @param sysTenantPackUser
   * @return
   */
  @PostMapping("/addTenantPackUser")
  public Result<?> addTenantPackUser(@RequestBody SysTenantPackUser sysTenantPackUser) {
    sysTenantService.addBatchTenantPackUser(sysTenantPackUser);
    return Result.ok(I18nUtil.getMessage("sysTenant.operationSuccess"));
  }

  /**
   * Remove user from product pack
   *
   * @param sysTenantPackUser
   * @return
   */
  @PutMapping("/deleteTenantPackUser")
  public Result<?> deleteTenantPackUser(@RequestBody SysTenantPackUser sysTenantPackUser) {
    sysTenantService.deleteTenantPackUser(sysTenantPackUser);
    return Result.ok(I18nUtil.getMessage("sysTenant.operationSuccess"));
  }

  /**
   * Update application status
   *
   * @param sysTenant
   * @return
   */
  @PutMapping("/updateApplyStatus")
  public Result<?> updateApplyStatus(@RequestBody SysTenant sysTenant) {
    SysTenant entity = this.sysTenantService.getById(sysTenant.getId());
    if (entity == null) {
      return Result.error(I18nUtil.getMessage("sysTenant.tenantNotExist"));
    }
    entity.setApplyStatus(sysTenant.getApplyStatus());
    sysTenantService.updateById(entity);
    return Result.ok("");
  }

  /**
   * Get product pack membership application list
   *
   * @param tenantId
   * @return
   */
  @RequestMapping(value = "/getTenantPackApplyUsers", method = RequestMethod.POST)
  public Result<?> getTenantPackApplyUsers(@RequestParam("tenantId") Integer tenantId) {
    List<TenantPackUser> list = sysTenantService.getTenantPackApplyUsers(tenantId);
    return Result.ok(list);
  }

  /**
   * Individual apply to become administrator
   *
   * @param sysTenantPackUser
   * @return
   */
  @PostMapping("/doApplyTenantPackUser")
  public Result<?> doApplyTenantPackUser(@RequestBody SysTenantPackUser sysTenantPackUser) {
    sysTenantService.doApplyTenantPackUser(sysTenantPackUser);
    return Result.ok(I18nUtil.getMessage("sysTenant.applySuccess"));
  }

  /**
   * Approve application to become administrator
   *
   * @param sysTenantPackUser
   * @return
   */
  @PutMapping("/passApply")
  public Result<?> passApply(@RequestBody SysTenantPackUser sysTenantPackUser) {
    sysTenantService.passApply(sysTenantPackUser);
    return Result.ok(I18nUtil.getMessage("sysTenant.operationSuccess"));
  }

  /**
   * Reject application to become administrator
   *
   * @param sysTenantPackUser
   * @return
   */
  @PutMapping("/deleteApply")
  public Result<?> deleteApply(@RequestBody SysTenantPackUser sysTenantPackUser) {
    sysTenantService.deleteApply(sysTenantPackUser);
    return Result.ok("");
  }

  /**
   * Check whether you have applied for a super administrator
   *
   * @return
   */
  @RequestMapping(value = "/getApplySuperAdminCount", method = RequestMethod.POST)
  public Result<Long> getApplySuperAdminCount() {
    Long count = sysTenantService.getApplySuperAdminCount();
    return Result.ok(count);
  }

  /**
   * Enter app org page: query tenant info and whether current user has admin permission
   *
   * @param id
   * @return
   */
  @RequestMapping(value = "/queryTenantAuthInfo", method = RequestMethod.POST)
  public Result<TenantDepartAuthInfo> queryTenantAuthInfo(
      @RequestParam(name = "id", required = true) String id) {
    TenantDepartAuthInfo info = sysTenantService.getTenantDepartAuthInfo(Integer.parseInt(id));
    return Result.ok(info);
  }

  /**
   * Get paginated user list under product pack
   *
   * @param tenantId
   * @param packId
   * @param status
   * @param pageNo
   * @param pageSize
   * @return
   */
  @RequestMapping(value = "/queryTenantPackUserList", method = RequestMethod.POST)
  public Result<IPage<TenantPackUser>> queryTenantPackUserList(
      @RequestParam("tenantId") String tenantId,
      @RequestParam("packId") String packId,
      @RequestParam("status") Integer status,
      @RequestParam(name = "pageNo", defaultValue = "1") Integer pageNo,
      @RequestParam(name = "pageSize", defaultValue = "10") Integer pageSize) {
    Page<TenantPackUser> page = new Page<>(pageNo, pageSize);
    IPage<TenantPackUser> pageList =
        sysTenantService.queryTenantPackUserList(tenantId, packId, status, page);
    return Result.ok(pageList);
  }

  /**
   * Get tenant list by user id（Pagination）
   *
   * @param sysUserTenantVo
   * @return
   */
  @RequestMapping(value = "/getTenantPageListByUserId", method = RequestMethod.POST)
  public Result<IPage<SysTenant>> getTenantPageListByUserId(
      SysUserTenantVo sysUserTenantVo,
      @RequestParam(name = "pageNo", defaultValue = "1") Integer pageNo,
      @RequestParam(name = "pageSize", defaultValue = "10") Integer pageSize) {
    LoginUser sysUser = (LoginUser) StpUtil.getSession().get("loginUser");
    List<String> list = null;
    String userTenantStatus = sysUserTenantVo.getUserTenantStatus();
    if (oConvertUtils.isNotEmpty(userTenantStatus)) {
      list = Arrays.asList(userTenantStatus.split(SymbolConstant.COMMA));
    }
    Page<SysTenant> page = new Page<>(pageNo, pageSize);
    IPage<SysTenant> pageList =
        relationService.getTenantPageListByUserId(page, sysUser.getId(), list, sysUserTenantVo);
    return Result.ok(pageList);
  }

  /** Approve or reject tenant join */
  @PutMapping("/agreeOrRefuseJoinTenant")
  public Result<String> agreeOrRefuseJoinTenant(
      @RequestParam("tenantId") Integer tenantId, @RequestParam("status") String status) {
    // Whether multi-tenant data isolation is enabled for system admin [SAAS mode]
    LoginUser sysUser = (LoginUser) StpUtil.getSession().get("loginUser");
    String userId = sysUser.getId();
    SysTenant tenant = sysTenantService.getById(tenantId);
    if (null == tenant) {
      return Result.error(I18nUtil.getMessage("sysTenant.organizationNotExist"));
    }
    SysUserTenant sysUserTenant = relationService.getUserTenantByTenantId(userId, tenantId);
    if (null == sysUserTenant) {
      return Result.error(I18nUtil.getMessage("sysTenant.userNotInOrganization"));
    }
    String content = "";
    SysUser user = new SysUser();
    user.setUsername(sysUserTenant.getCreateBy());
    String realname = oConvertUtils.getString(sysUser.getRealname(), sysUser.getUsername());
    // Successfully added
    if (CommonConstant.USER_TENANT_NORMAL.equals(status)) {
      // UpdateTenantStatus
      relationService.agreeJoinTenant(userId, tenantId);
      content = content + realname + "Agreed to your invitation to join " + tenant.getName() + " 's invitation";
      sysTenantService.sendMsgForAgreeAndRefuseJoin(user, content);
      return Result.OK(I18nUtil.getMessage("sysTenant.agreeInvitation"));
    } else if (CommonConstant.USER_TENANT_REFUSE.equals(status)) {
      // Delete relation table directly
      relationService.refuseJoinTenant(userId, tenantId);
      content = content + realname + "Rejected your invitation to join " + tenant.getName() + " 's invitation";
      sysTenantService.sendMsgForAgreeAndRefuseJoin(user, content);
      return Result.OK(I18nUtil.getMessage("sysTenant.refuseInvitation"));
    }
    return Result.error(I18nUtil.getMessage("sysTenant.typeMismatch"));
  }

  /**
   * Query all valid tenants for current user [knowledge base dedicated API]
   *
   * @return
   */
  @RequestMapping(value = "/getCurrentUserTenantForFile", method = RequestMethod.POST)
  public Result<Map<String, Object>> getCurrentUserTenantForFile() {
    Result<Map<String, Object>> result = new Result<Map<String, Object>>();
    try {
      LoginUser sysUser = (LoginUser) StpUtil.getSession().get("loginUser");
      List<SysTenant> tenantList = sysTenantService.getTenantListByUserId(sysUser.getId());
      Map<String, Object> map = new HashMap<>(5);
      // When SaaS tenant isolation is enabled and tenant data is non-empty, return tenant info
      if (MybatisPlusSaasConfig.OPEN_SYSTEM_TENANT_CONTROL
          && CollectionUtil.isNotEmpty(tenantList)) {
        map.put("list", tenantList);
      }
      result.setSuccess(true);
      result.setResult(map);
    } catch (Exception e) {
      log.error("operation failed", e);
      result.error500(I18nUtil.getMessage("sysTenant.queryFailed"));
    }
    return result;
  }
}
