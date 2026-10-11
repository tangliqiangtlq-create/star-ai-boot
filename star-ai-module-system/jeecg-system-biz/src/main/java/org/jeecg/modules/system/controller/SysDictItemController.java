package org.jeecg.modules.system.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.annotation.SaMode;
import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import java.util.Arrays;
import java.util.Date;
import java.util.Set;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jeecg.common.api.vo.Result;
import org.jeecg.common.constant.CacheConstant;
import org.jeecg.common.constant.SecurityRoleConstants;
import org.jeecg.common.system.query.QueryGenerator;
import org.jeecg.common.util.I18nUtil;
import org.jeecg.modules.system.entity.SysDict;
import org.jeecg.modules.system.entity.SysDictItem;
import org.jeecg.modules.system.service.ISysDictItemService;
import org.jeecg.modules.system.service.ISysDictService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * front-end controller @Author zhangweijian
 *
 * @since 2018-12-28*/
@Api(tags = "DataDictionary")
@RestController
@RequestMapping(value = "/sys/dictItem", method = RequestMethod.POST)
@Slf4j
// C1 role matrix: operations_admin is granted all four system:dict:item:* codes
// (V1_99_023 / sys_role_permission seed), yet the previous {admin, super_admin} gate
// denied it even with the permission held — the UserLabel (dictCode=user_label) ops page
// could not edit its dict items. Align the role gate with the rest of the admin controllers
// (SysCategoryController/SysPositionController/... all use ROLE_OPS_ADMIN + admin).
//
// C8 is preserved and NOT weakened: assertAdminRoleWriteAllowed()/ById() below still
// force the super_admin role for the sys_config dict item whose key is
// "admin_role" — on add, edit, delete and deleteBatch. Re-check with
// ramp_C_priv_esc_gates_v1.1.sh C8-1.
//
// The same guard additionally covers the dict entries that steer a runtime security
// decision (the step-up amount threshold, and the sys_config key that routes ops
// alerts). It now inspects the stored row as well as the request body, so renaming a
// protected key cannot sidestep it — widening the role gate must not open an
// edit-away route around C8.
@SaCheckRole(
    value = {"admin", SecurityRoleConstants.ROLE_OPS_ADMIN, SecurityRoleConstants.ROLE_SUPER_ADMIN},
    mode = SaMode.OR)
public class SysDictItemController {

  @Autowired private ISysDictItemService sysDictItemService;
  @Autowired private ISysDictService sysDictService;

  /**
   * Dict codes whose items steer a runtime security decision, so writing them requires the
   * super_admin role — these configure the controls themselves rather than business labels.
   *
   * <ul>
   *   <li>{@code transaction_amount_threshold} — read by
   *       {@code AmountBasedSecondAuthResolver#getThresholdForAccountType} as the amount above which
   *       step-up (payment password / 2FA) is demanded. Raising it drops step-up for large
   *       withdrawals.
   * </ul>
   */
  private static final Set<String> SUPER_ADMIN_ONLY_DICT_CODES =
      Set.of("transaction_amount_threshold");

  /**
   * {@code sys_config} keys whose value steers a runtime security decision.
   *
   * <ul>
   *   <li>{@code admin_role} — the admin role set resolved by {@code RoleConfigUtil} (C8);
   *   <li>{@code ops_notify_email} — destination of ops alerts, resolved by
   *       {@code OpsNotifyEmailUtil#resolve}; repointing it silences those alerts.
   * </ul>
   */
  private static final Set<String> SUPER_ADMIN_ONLY_SYS_CONFIG_KEYS =
      Set.of("admin_role", "ops_notify_email");

  /**
   * C8 / security-config guard for the dict write paths.
   *
   * <p>Both the incoming body and the stored row are evaluated, because checking only the incoming
   * value leaves an "edit-away" bypass: renaming the protected key (or moving the item to another
   * dict) makes the new value look harmless while the stored configuration is still rewritten. An
   * absent {@code sys_config.admin_role} also makes {@code RoleConfigUtil} fall back to its wider
   * default role set, so renaming the key away would not merely skip a check — it would widen the
   * admin set. {@code add} passes the body, {@code delete}/{@code deleteBatch} the stored row, and
   * {@code edit} both.
   */
  /** Which protection, if any, a dict write hits. */
  enum ProtectedWrite {
    NONE,
    /** {@code sys_config} key {@code admin_role} — the admin role set (C8). */
    ADMIN_ROLE_KEY,
    /** A dict / {@code sys_config} key that steers a runtime security decision. */
    SECURITY_CONFIG
  }

  /**
   * Pure decision for a dict write, given the resolved dict code and item value. Extracted so it can
   * be unit-tested without a Sa-Token context or a Spring container.
   *
   * @param dictCode resolved {@code sys_dict.dict_code}, may be {@code null}
   * @param itemValue the dict item value, may be {@code null}
   */
  static ProtectedWrite classifyProtectedDictWrite(String dictCode, String itemValue) {
    if (StringUtils.isBlank(dictCode)) {
      return ProtectedWrite.NONE;
    }
    boolean sysConfig = "sys_config".equalsIgnoreCase(dictCode);
    if (sysConfig && "admin_role".equalsIgnoreCase(itemValue)) {
      return ProtectedWrite.ADMIN_ROLE_KEY;
    }
    for (String code : SUPER_ADMIN_ONLY_DICT_CODES) {
      if (code.equalsIgnoreCase(dictCode)) {
        return ProtectedWrite.SECURITY_CONFIG;
      }
    }
    if (!sysConfig || itemValue == null) {
      return ProtectedWrite.NONE;
    }
    String key = itemValue.trim();
    for (String protectedKey : SUPER_ADMIN_ONLY_SYS_CONFIG_KEYS) {
      if (protectedKey.equalsIgnoreCase(key)) {
        return ProtectedWrite.SECURITY_CONFIG;
      }
    }
    return ProtectedWrite.NONE;
  }

  private ProtectedWrite classify(SysDictItem item) {
    return item == null
        ? ProtectedWrite.NONE
        : classifyProtectedDictWrite(resolveDictCode(item.getDictId()), item.getItemValue());
  }

  private void assertSensitiveDictWriteAllowed(SysDictItem incoming, SysDictItem existing) {
    if (incoming == null && existing == null) {
      return;
    }
    // super_admin owns security configuration; nothing below constrains it.
    if (StpUtil.hasRole(SecurityRoleConstants.ROLE_SUPER_ADMIN)) {
      return;
    }
    ProtectedWrite incomingKind = classify(incoming);
    ProtectedWrite existingKind = classify(existing);
    if (incomingKind == ProtectedWrite.ADMIN_ROLE_KEY
        || existingKind == ProtectedWrite.ADMIN_ROLE_KEY) {
      log.error(
          "SEC-C8-PRIV-ESC attempt to write admin_role sys_config dict item without super_admin, uid={}",
          StpUtil.getLoginIdDefaultNull());
      throw new org.jeecg.common.exception.JeecgBootException(
          "C8 edit admin_role dict key requires super_admin role.", 403);
    }
    if (incomingKind == ProtectedWrite.SECURITY_CONFIG
        || existingKind == ProtectedWrite.SECURITY_CONFIG) {
      SysDictItem probe = incoming != null ? incoming : existing;
      log.error(
          "SEC-C8-PRIV-ESC attempt to write security-steering dict without super_admin, uid={} dictId={} itemValue={}",
          StpUtil.getLoginIdDefaultNull(),
          probe.getDictId(),
          probe.getItemValue());
      throw new org.jeecg.common.exception.JeecgBootException(
          "Editing this dictionary steers a security control; super_admin role required.", 403);
    }
  }

  private String resolveDictCode(String dictId) {
    if (StringUtils.isBlank(dictId)) {
      return null;
    }
    SysDict dict = sysDictService.getById(dictId);
    return dict == null ? null : dict.getDictCode();
  }

  /** Kept as the C8 anchor name; delegates to the pair-aware guard. */
  private void assertAdminRoleWriteAllowed(SysDictItem item) {
    assertSensitiveDictWriteAllowed(item, null);
  }

  private void assertAdminRoleWriteAllowedById(String dictItemId) {
    if (StringUtils.isBlank(dictItemId)) {
      return;
    }
    assertSensitiveDictWriteAllowed(null, sysDictItemService.getById(dictItemId));
  }

  /**
   * @Function: Query dict data
   *
   * @param sysDictItem
   * @param pageNo
   * @param pageSize
   * @param req
   * @return
   */
  @RequestMapping(value = "/list", method = RequestMethod.POST)
  public Result<IPage<SysDictItem>> queryPageList(
      SysDictItem sysDictItem,
      @RequestParam(name = "pageNo", defaultValue = "1") Integer pageNo,
      @RequestParam(name = "pageSize", defaultValue = "10") Integer pageSize,
      HttpServletRequest req) {
    Result<IPage<SysDictItem>> result = new Result<IPage<SysDictItem>>();
    QueryWrapper<SysDictItem> queryWrapper =
        QueryGenerator.initQueryWrapper(sysDictItem, req.getParameterMap());
    queryWrapper.orderByAsc("sort_order");
    Page<SysDictItem> page = new Page<SysDictItem>(pageNo, pageSize);
    IPage<SysDictItem> pageList = sysDictItemService.page(page, queryWrapper);
    result.setSuccess(true);
    result.setResult(pageList);
    return result;
  }

  /**
   * @Function: Add
   *
   * @return
   */
  @SaCheckPermission("system:dict:item:add")
  @RequestMapping(value = "/add", method = RequestMethod.POST)
  @CacheEvict(
      value = {CacheConstant.SYS_DICT_CACHE, CacheConstant.SYS_ENABLE_DICT_CACHE},
      allEntries = true)
  public Result<SysDictItem> add(@RequestBody SysDictItem sysDictItem) {
    Result<SysDictItem> result = new Result<SysDictItem>();
    try {
      assertAdminRoleWriteAllowed(sysDictItem);
      sysDictItem.setCreateTime(new Date());
      sysDictItemService.save(sysDictItem);
      result.success(I18nUtil.getMessage("sysDictItem.saveSuccess"));
    } catch (org.jeecg.common.exception.JeecgBootException jbe) {
      return Result.error(jbe.getErrCode(), jbe.getMessage());
    } catch (Exception e) {
      log.error("operation failed", e);
      result.error500(I18nUtil.getMessage("sysUser.operationFailed"));
    }
    return result;
  }

  /**
   * @Function: Edit
   *
   * @param sysDictItem
   * @return
   */
  @SaCheckPermission("system:dict:item:edit")
  @RequestMapping(
      value = "/edit",
      method = {RequestMethod.PUT, RequestMethod.POST})
  @CacheEvict(
      value = {CacheConstant.SYS_DICT_CACHE, CacheConstant.SYS_ENABLE_DICT_CACHE},
      allEntries = true)
  public Result<SysDictItem> edit(@RequestBody SysDictItem sysDictItem) {
    Result<SysDictItem> result = new Result<SysDictItem>();
    try {
      assertAdminRoleWriteAllowed(sysDictItem);
      SysDictItem sysdict = sysDictItemService.getById(sysDictItem.getId());
      if (sysdict == null) {
        return Result.error(404, I18nUtil.getMessage("sysUser.entityNotFound"));
      }
      // Also evaluate the stored row: renaming/moving a protected key must stay blocked even
      // though the incoming value no longer looks protected ("edit-away" bypass of C8).
      assertSensitiveDictWriteAllowed(null, sysdict);
      sysDictItem.setUpdateTime(new Date());
      boolean ok = sysDictItemService.updateById(sysDictItem);
      // TODO What does returning false mean?
      if (ok) {
        result.success(I18nUtil.getMessage("sysDictItem.editSuccess"));
      }
    } catch (org.jeecg.common.exception.JeecgBootException jbe) {
      return Result.error(jbe.getErrCode(), jbe.getMessage());
    }
    return result;
  }

  /**
   * @Function: Delete dict data
   *
   * @param id
   * @return
   */
  @SaCheckPermission("system:dict:item:delete")
  @RequestMapping(value = "/delete", method = RequestMethod.DELETE)
  @CacheEvict(
      value = {CacheConstant.SYS_DICT_CACHE, CacheConstant.SYS_ENABLE_DICT_CACHE},
      allEntries = true)
  public Result<SysDictItem> delete(@RequestParam(name = "id", required = true) String id) {
    Result<SysDictItem> result = new Result<SysDictItem>();
    try {
      assertAdminRoleWriteAllowedById(id);
      SysDictItem joinSystem = sysDictItemService.getById(id);
      if (joinSystem == null) {
        return Result.error(404, I18nUtil.getMessage("sysUser.entityNotFound"));
      } else {
        boolean ok = sysDictItemService.removeById(id);
        if (ok) {
          result.success(I18nUtil.getMessage("sysDictItem.deleteSuccess"));
        }
      }
    } catch (org.jeecg.common.exception.JeecgBootException jbe) {
      return Result.error(jbe.getErrCode(), jbe.getMessage());
    }
    return result;
  }

  /**
   * @Function: Batch delete dict data
   *
   * @param ids
   * @return
   */
  @SaCheckPermission("system:dict:item:deleteBatch")
  @RequestMapping(value = "/deleteBatch", method = RequestMethod.DELETE)
  @CacheEvict(
      value = {CacheConstant.SYS_DICT_CACHE, CacheConstant.SYS_ENABLE_DICT_CACHE},
      allEntries = true)
  public Result<SysDictItem> deleteBatch(@RequestParam(name = "ids", required = true) String ids) {
    Result<SysDictItem> result = new Result<SysDictItem>();
    if (ids == null || "".equals(ids.trim())) {
      return Result.error(400, I18nUtil.getMessage("sysDictItem.paramNotRecognized"));
    } else {
      try {
        for (String id : ids.split(",")) {
          if (id != null && !id.trim().isEmpty()) {
            assertAdminRoleWriteAllowedById(id.trim());
          }
        }
        this.sysDictItemService.removeByIds(Arrays.asList(ids.split(",")));
        result.success(I18nUtil.getMessage("sysDictItem.deleteSuccess"));
      } catch (org.jeecg.common.exception.JeecgBootException jbe) {
        return Result.error(jbe.getErrCode(), jbe.getMessage());
      }
    }
    return result;
  }

  /**
   * Duplicate dict value check
   *
   * @param sysDictItem
   * @param request
   * @return
   */
  @RequestMapping(value = "/dictItemCheck", method = RequestMethod.POST)
  @ApiOperation("Dictionary duplicate validation API")
  public Result<Object> doDictItemCheck(SysDictItem sysDictItem, HttpServletRequest request) {
    Long num = Long.valueOf(0);
    LambdaQueryWrapper<SysDictItem> queryWrapper = new LambdaQueryWrapper<SysDictItem>();
    queryWrapper.eq(SysDictItem::getItemValue, sysDictItem.getItemValue());
    queryWrapper.eq(SysDictItem::getDictId, sysDictItem.getDictId());
    if (StringUtils.isNotBlank(sysDictItem.getId())) {
      // Edit page check
      queryWrapper.ne(SysDictItem::getId, sysDictItem.getId());
    }
    num = sysDictItemService.count(queryWrapper);
    if (num == 0) {
      // Value available
      return Result.ok(I18nUtil.getMessage("sysDictItem.valueAvailable"));
    } else {
      // Value unavailable
      log.info("This value is unavailable, already exists in the system!");
      return Result.error(I18nUtil.getMessage("sysDictItem.valueNotAvailable"));
    }
  }
}
